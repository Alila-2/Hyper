package com.hyper.spectral.support.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hyper.spectral.config.fusion.HysureFusionProperties;
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
 * Runs HySure in an isolated Python process and exchanges results through JSON.
 */
@Component
public class HysurePythonAdapter {

    private final ObjectMapper objectMapper;
    private final HysureFusionProperties properties;
    private final Object spectrumWorkerLock = new Object();
    private final ExecutorService spectrumReadExecutor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "hysure-spectrum-reader");
        thread.setDaemon(true);
        return thread;
    });
    private SpectrumWorker spectrumWorker;

    public HysurePythonAdapter(ObjectMapper objectMapper, HysureFusionProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public HysureFusionResult fuse(Path inputFile, Path outputDirectory) throws IOException {
        stopSpectrumWorker();
        Files.createDirectories(outputDirectory);
        Path resultJson = outputDirectory.resolve("backend_result.json");
        Path logFile = outputDirectory.resolve("hysure.log");
        List<String> command = createBaseCommand();
        command.add("fuse");
        command.add("--input");
        command.add(inputFile.toAbsolutePath().normalize().toString());
        command.add("--output-dir");
        command.add(outputDirectory.toAbsolutePath().normalize().toString());
        command.add("--subspace");
        command.add(Integer.toString(properties.getSubspaceDimension()));
        command.add("--iterations");
        command.add(Integer.toString(properties.getIterations()));
        command.add("--seed");
        command.add(Integer.toString(properties.getRandomSeed()));
        command.add("--blur-support");
        command.add(Integer.toString(properties.getBlurSupport()));
        command.add("--block-rows");
        command.add(Integer.toString(properties.getOutputBlockRows()));
        command.add("--result-json");
        command.add(resultJson.toString());
        execute(command, resultJson, logFile, "fusion");
        HysureFusionResult result = objectMapper.readValue(resultJson.toFile(), HysureFusionResult.class);
        validateFusionResult(result, outputDirectory);
        return result;
    }

    public List<Double> readSpectrum(Path fusionFile, int x, int y) throws IOException {
        Path normalizedFile = fusionFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedFile)) {
            throw new IOException("HySure fusion result does not exist: " + normalizedFile);
        }
        synchronized (spectrumWorkerLock) {
            IOException lastFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                SpectrumWorker worker = ensureSpectrumWorker(normalizedFile);
                HysureSpectrumResult result;
                try {
                    result = worker.query(x, y);
                } catch (IOException exception) {
                    lastFailure = exception;
                    stopSpectrumWorkerLocked();
                    continue;
                }
                if (!"success".equals(result.status) || result.spectrum == null) {
                    throw new IOException("HySure spectrum query failed: "
                            + (StringUtils.hasText(result.message) ? result.message : "empty response"));
                }
                return result.spectrum;
            }
            throw new IOException("HySure spectrum worker failed after restart", lastFailure);
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

    private SpectrumWorker ensureSpectrumWorker(Path fusionFile) throws IOException {
        if (spectrumWorker != null
                && spectrumWorker.fusionFile.equals(fusionFile)
                && spectrumWorker.process.isAlive()) {
            return spectrumWorker;
        }
        stopSpectrumWorkerLocked();
        Path workDirectory = fusionFile.getParent();
        if (workDirectory == null) {
            throw new IOException("HySure result has no parent directory: " + fusionFile);
        }
        List<String> command = createBaseCommand();
        command.add("spectrum-server");
        command.add("--fusion-file");
        command.add(fusionFile.toString());
        Path logFile = workDirectory.resolve("spectrum-worker.log");
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start();
        spectrumWorker = new SpectrumWorker(fusionFile, process, logFile);
        return spectrumWorker;
    }

    private void stopSpectrumWorker() {
        synchronized (spectrumWorkerLock) {
            stopSpectrumWorkerLocked();
        }
    }

    private void stopSpectrumWorkerLocked() {
        if (spectrumWorker == null) {
            return;
        }
        spectrumWorker.stop();
        spectrumWorker = null;
    }

    private String readWorkerResponse(BufferedReader reader, Path logFile) throws IOException {
        Future<String> response = spectrumReadExecutor.submit(reader::readLine);
        try {
            String line = response.get(
                    Math.max(100L, properties.getSpectrumTimeoutMillis()),
                    TimeUnit.MILLISECONDS
            );
            if (line == null) {
                String log = Files.isRegularFile(logFile)
                        ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
                throw new IOException("HySure spectrum worker exited without a response: " + log);
            }
            return line;
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("HySure spectrum query was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Unable to read HySure spectrum response", exception.getCause());
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new IOException("HySure spectrum query timed out after "
                    + properties.getSpectrumTimeoutMillis() + " ms", exception);
        }
    }

    private List<String> createBaseCommand() throws IOException {
        Path runner = resolveRunnerPath();
        List<String> command = new ArrayList<>();
        if (StringUtils.hasText(properties.getPythonCommand())) {
            command.add(properties.getPythonCommand().trim());
        } else {
            if (!StringUtils.hasText(properties.getCondaEnvironment())) {
                throw new IOException("fusion.hysure.conda-environment is not configured");
            }
            command.add(properties.getCondaCommand());
            command.add("run");
            command.add("--no-capture-output");
            command.add("-n");
            command.add(properties.getCondaEnvironment());
            command.add("python");
        }
        command.add(runner.toString());
        return command;
    }

    private Path resolveRunnerPath() throws IOException {
        if (!StringUtils.hasText(properties.getRunnerPath())) {
            throw new IOException("fusion.hysure.runner-path is not configured");
        }
        Path configured = Paths.get(properties.getRunnerPath());
        Path runner = configured.isAbsolute()
                ? configured.normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
        if (!Files.isRegularFile(runner)) {
            throw new IOException("HySure runner not found: " + runner.toAbsolutePath());
        }
        return runner.toAbsolutePath();
    }

    private void execute(List<String> command, Path resultJson, Path logFile,
                         String operation) throws IOException {
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
            throw new IOException("HySure " + operation + " was interrupted", exception);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("HySure " + operation + " timed out after "
                    + properties.getTimeoutSeconds() + " seconds. Log: " + logFile);
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(resultJson)) {
            String output = Files.isRegularFile(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            String structuredError = readStructuredError(resultJson);
            throw new IOException("HySure " + operation + " failed: "
                    + (StringUtils.hasText(structuredError) ? structuredError : output));
        }
    }

    private String readStructuredError(Path resultJson) {
        if (!Files.isRegularFile(resultJson)) {
            return "";
        }
        try {
            HysureErrorResult result = objectMapper.readValue(resultJson.toFile(), HysureErrorResult.class);
            return result.message;
        } catch (IOException ignored) {
            return "";
        }
    }

    private void validateFusionResult(HysureFusionResult result, Path outputDirectory) throws IOException {
        if (!"success".equals(result.status) || result.width <= 0
                || result.height <= 0 || result.bands <= 0) {
            throw new IOException("HySure returned invalid fusion metadata");
        }
        Path outputRoot = outputDirectory.toAbsolutePath().normalize();
        result.fusionPath = validateOutputPath(result.fusionFile, outputRoot, "fusion file");
        result.previewPath = validateOutputPath(result.previewFile, outputRoot, "preview file");
    }

    private Path validateOutputPath(String rawPath, Path outputRoot, String description)
            throws IOException {
        if (!StringUtils.hasText(rawPath)) {
            throw new IOException("HySure did not return a " + description);
        }
        Path path = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(outputRoot) || !Files.isRegularFile(path)) {
            throw new IOException("Invalid HySure " + description + ": " + path);
        }
        return path;
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
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
        }

        private HysureSpectrumResult query(int x, int y) throws IOException {
            if (!process.isAlive()) {
                throw new IOException("HySure spectrum worker is not running. Log: " + logFile);
            }
            Map<String, Integer> request = new HashMap<>();
            request.put("x", x);
            request.put("y", y);
            writer.write(objectMapper.writeValueAsString(request));
            writer.newLine();
            writer.flush();
            String response = readWorkerResponse(reader, logFile);
            return objectMapper.readValue(response, HysureSpectrumResult.class);
        }

        private void stop() {
            try {
                writer.write("{\"command\":\"shutdown\"}");
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
                // The worker may already have exited.
            }
            try {
                writer.close();
            } catch (IOException ignored) {
                // Best effort shutdown.
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
    public static class HysureFusionResult {
        public String status;
        public String fusionFile;
        public String previewFile;
        public int width;
        public int height;
        public int bands;
        public String dtype;
        public double elapsedSeconds;
        public long fusionFileSizeBytes;
        public long previewFileSizeBytes;
        private Path fusionPath;
        private Path previewPath;

        public Path getFusionPath() {
            return fusionPath;
        }

        public Path getPreviewPath() {
            return previewPath;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HysureSpectrumResult {
        public String status;
        public String message;
        public List<Double> spectrum;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HysureErrorResult {
        public String status;
        public String message;
    }
}
