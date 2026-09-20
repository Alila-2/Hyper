package com.hyper.spectral.support.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hyper.spectral.config.fusion.MiaeFusionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs the MIAE fusion pipeline and keeps one memory-mapped spectrum worker alive.
 */
@Component
public class MiaePythonAdapter {

    private final ObjectMapper objectMapper;
    private final MiaeFusionProperties properties;
    private final Object spectrumWorkerLock = new Object();
    private final ExecutorService spectrumReadExecutor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "miae-spectrum-reader");
        thread.setDaemon(true);
        return thread;
    });
    private SpectrumWorker spectrumWorker;

    public MiaePythonAdapter(ObjectMapper objectMapper, MiaeFusionProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public MiaeFusionResult fuse(Path inputFile, Path outputDirectory) throws IOException {
        stopSpectrumWorker();
        Files.createDirectories(outputDirectory);
        Path resultJson = outputDirectory.resolve("backend_result.json");
        Path logFile = outputDirectory.resolve("miae.log");
        List<String> command = createBaseCommand();
        command.add("fuse");
        addPathArgument(command, "--input", inputFile);
        addPathArgument(command, "--output-dir", outputDirectory);
        addArgument(command, "--device", properties.getDevice());
        addArgument(command, "--seed", properties.getRandomSeed());
        addArgument(command, "--blind-iters", properties.getBlindIterations());
        addArgument(command, "--fusion-iters", properties.getFusionIterations());
        addArgument(command, "--batch-size", properties.getBatchSize());
        addArgument(command, "--patch-size", properties.getPatchSize());
        addArgument(command, "--endmembers", properties.getEndmembers());
        addArgument(command, "--stages", properties.getStages());
        addArgument(command, "--tile-size", properties.getTileSize());
        addArgument(command, "--halo-lr", properties.getHaloLowResolution());
        addArgument(command, "--log-every", properties.getLogEvery());
        if (properties.isAmpInference()) {
            command.add("--amp-inference");
        }
        addPathArgument(command, "--result-json", resultJson);
        execute(command, resultJson, logFile);
        MiaeFusionResult result = objectMapper.readValue(resultJson.toFile(), MiaeFusionResult.class);
        validateFusionResult(result, outputDirectory);
        return result;
    }

    public List<Double> readSpectrum(Path fusionFile, int x, int y) throws IOException {
        Path normalizedFile = fusionFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedFile)) {
            throw new IOException("MIAE fusion result does not exist: " + normalizedFile);
        }
        synchronized (spectrumWorkerLock) {
            IOException lastFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                SpectrumWorker worker = ensureSpectrumWorker(normalizedFile);
                try {
                    MiaeSpectrumResult result = worker.query(x, y);
                    if (!"success".equals(result.status) || result.spectrum == null) {
                        throw new IOException("MIAE spectrum query failed: "
                                + (StringUtils.hasText(result.message) ? result.message : "empty response"));
                    }
                    return result.spectrum;
                } catch (IOException exception) {
                    lastFailure = exception;
                    stopSpectrumWorkerLocked();
                }
            }
            throw new IOException("MIAE spectrum worker failed after restart", lastFailure);
        }
    }

    @PreDestroy
    public void close() {
        stopSpectrumWorker();
        spectrumReadExecutor.shutdownNow();
    }

    long currentSpectrumWorkerPid() {
        synchronized (spectrumWorkerLock) {
            return spectrumWorker == null ? -1L : spectrumWorker.process.pid();
        }
    }

    private List<String> createBaseCommand() throws IOException {
        List<String> command = new ArrayList<>();
        if (StringUtils.hasText(properties.getPythonCommand())) {
            command.add(properties.getPythonCommand().trim());
        } else {
            if (!StringUtils.hasText(properties.getCondaEnvironment())) {
                throw new IOException("fusion.miae.conda-environment is not configured");
            }
            command.add(properties.getCondaCommand());
            command.add("run");
            command.add("--no-capture-output");
            command.add("-n");
            command.add(properties.getCondaEnvironment());
            command.add("python");
        }
        command.add(resolveRunnerPath().toString());
        return command;
    }

    private Path resolveRunnerPath() throws IOException {
        if (!StringUtils.hasText(properties.getRunnerPath())) {
            throw new IOException("fusion.miae.runner-path is not configured");
        }
        Path configured = Paths.get(properties.getRunnerPath());
        Path runner = configured.isAbsolute()
                ? configured.normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
        if (!Files.isRegularFile(runner)) {
            throw new IOException("MIAE runner not found: " + runner.toAbsolutePath());
        }
        return runner.toAbsolutePath();
    }

    private void execute(List<String> command, Path resultJson, Path logFile) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        boolean finished;
        try {
            finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("MIAE fusion was interrupted", exception);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("MIAE fusion timed out after " + properties.getTimeoutSeconds()
                    + " seconds. Log: " + logFile);
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(resultJson)) {
            String output = Files.isRegularFile(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8) : "";
            String structuredError = readStructuredError(resultJson);
            throw new IOException("MIAE fusion failed: "
                    + (StringUtils.hasText(structuredError) ? structuredError : "unknown error")
                    + ". Log: " + output);
        }
    }

    private String readStructuredError(Path resultJson) {
        if (!Files.isRegularFile(resultJson)) {
            return "";
        }
        try {
            return objectMapper.readValue(resultJson.toFile(), MiaeErrorResult.class).message;
        } catch (IOException ignored) {
            return "";
        }
    }

    private void validateFusionResult(MiaeFusionResult result, Path outputDirectory) throws IOException {
        if (!"success".equals(result.status) || result.width <= 0
                || result.height <= 0 || result.bands <= 0) {
            throw new IOException("MIAE returned invalid fusion metadata");
        }
        Path outputRoot = outputDirectory.toAbsolutePath().normalize();
        result.fusionPath = validateOutputPath(result.fusionFile, outputRoot, "fusion data");
        result.matPath = validateOutputPath(result.matFile, outputRoot, "MAT result");
        result.previewPath = validateOutputPath(result.previewFile, outputRoot, "preview file");
    }

    private Path validateOutputPath(String rawPath, Path outputRoot, String description)
            throws IOException {
        if (!StringUtils.hasText(rawPath)) {
            throw new IOException("MIAE did not return a " + description);
        }
        Path path = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(outputRoot) || !Files.isRegularFile(path)) {
            throw new IOException("Invalid MIAE " + description + ": " + path);
        }
        return path;
    }

    private SpectrumWorker ensureSpectrumWorker(Path fusionFile) throws IOException {
        if (spectrumWorker != null && spectrumWorker.fusionFile.equals(fusionFile)
                && spectrumWorker.process.isAlive()) {
            return spectrumWorker;
        }
        stopSpectrumWorkerLocked();
        Path workDirectory = fusionFile.getParent();
        List<String> command = createBaseCommand();
        command.add("spectrum-server");
        addPathArgument(command, "--fusion-file", fusionFile);
        Path logFile = workDirectory.resolve("spectrum-worker.log");
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start();
        spectrumWorker = new SpectrumWorker(fusionFile, process, logFile);
        return spectrumWorker;
    }

    private String readWorkerResponse(BufferedReader reader, Path logFile) throws IOException {
        Future<String> response = spectrumReadExecutor.submit(reader::readLine);
        try {
            String line = response.get(Math.max(100L, properties.getSpectrumTimeoutMillis()),
                    TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new IOException("MIAE spectrum worker exited without a response. Log: " + logFile);
            }
            return line;
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("MIAE spectrum query was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Unable to read MIAE spectrum response", exception.getCause());
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new IOException("MIAE spectrum query timed out", exception);
        }
    }

    private void stopSpectrumWorker() {
        synchronized (spectrumWorkerLock) {
            stopSpectrumWorkerLocked();
        }
    }

    private void stopSpectrumWorkerLocked() {
        if (spectrumWorker != null) {
            spectrumWorker.stop();
            spectrumWorker = null;
        }
    }

    private void addPathArgument(List<String> command, String name, Path value) {
        command.add(name);
        command.add(value.toAbsolutePath().normalize().toString());
    }

    private void addArgument(List<String> command, String name, Object value) {
        command.add(name);
        command.add(String.valueOf(value));
    }

    private final class SpectrumWorker {
        private final Path fusionFile;
        private final Process process;
        private final Path logFile;
        private final BufferedWriter writer;
        private final BufferedReader reader;

        private SpectrumWorker(Path fusionFile, Process process, Path logFile) {
            this.fusionFile = fusionFile;
            this.process = process;
            this.logFile = logFile;
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        private MiaeSpectrumResult query(int x, int y) throws IOException {
            if (!process.isAlive()) {
                throw new IOException("MIAE spectrum worker is not running. Log: " + logFile);
            }
            Map<String, Integer> request = new HashMap<>();
            request.put("x", x);
            request.put("y", y);
            writer.write(objectMapper.writeValueAsString(request));
            writer.newLine();
            writer.flush();
            return objectMapper.readValue(readWorkerResponse(reader, logFile), MiaeSpectrumResult.class);
        }

        private void stop() {
            try {
                writer.write("{\"command\":\"shutdown\"}");
                writer.newLine();
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
                // The worker may already have exited.
            }
            if (process.isAlive()) {
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroy();
                    }
                    if (process.isAlive() && !process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException exception) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            try {
                reader.close();
            } catch (IOException ignored) {
                // Best effort shutdown.
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MiaeFusionResult {
        public String status;
        public String fusionFile;
        public String matFile;
        public String previewFile;
        public int width;
        public int height;
        public int bands;
        public String dtype;
        public double elapsedSeconds;
        public long fusionFileSizeBytes;
        public long previewFileSizeBytes;
        private Path fusionPath;
        private Path matPath;
        private Path previewPath;

        public Path getFusionPath() {
            return fusionPath;
        }

        public Path getMatPath() {
            return matPath;
        }

        public Path getPreviewPath() {
            return previewPath;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MiaeSpectrumResult {
        public String status;
        public String message;
        public List<Double> spectrum;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MiaeErrorResult {
        public String message;
    }
}
