package com.hyper.spectral.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyper.spectral.service.DetectionService;
import com.hyper.spectral.support.fusion.Dataset;
import com.hyper.spectral.vo.detection.DetectedObjectVO;
import com.hyper.spectral.vo.detection.DetectedTargetVO;
import com.hyper.spectral.vo.detection.DetectionPreviewResponse;
import com.hyper.spectral.vo.detection.DetectionResponse;
import com.hyper.spectral.vo.detection.TargetStatisticsVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class DetectionCompatibilityService implements DetectionService {

    private static final String ALGORITHM_HCEM = "hcem";
    private static final String ALGORITHM_HTD_MAMBA = "htd-mamba";
    private static final int MIN_DETECTION_PIXELS = 3;
    private static final int MIN_OBJECT_SIZE = 5;
    private static final String[] CUBE_NAMES = {
            "hyperspectral_data", "HRHSI", "LRHSI", "HSI", "hsi", "data", "image",
            "hyperspectral", "cube", "msi", "HRMSI"
    };

    private static final Map<String, TargetConfig> TARGET_CONFIGS = createTargetConfigs();

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final String runnerPath;
    private final String workDir;
    private final String targetSpectrumMat;
    private final String htdMambaPythonCommand;
    private final String htdMambaCondaCommand;
    private final String htdMambaCondaEnvironment;
    private final String htdMambaRoot;
    private final String htdMambaModelDataset;
    private final String htdMambaDevice;
    private final int htdMambaBatchSize;
    private final int randomDelayMinSeconds;
    private final int randomDelayMaxSeconds;
    private final long timeoutSeconds;

    public DetectionCompatibilityService(ObjectMapper objectMapper,
                                         @Value("${detection.compatibility.python-command:python}")
                                         String pythonCommand,
                                         @Value("${detection.compatibility.runner-path:../detection_runner.py}")
                                         String runnerPath,
                                         @Value("${detection.compatibility.work-dir:${user.dir}/runtime/detection-work}")
                                         String workDir,
                                         @Value("${detection.compatibility.target-spectrum-mat:${user.dir}/wzcl.mat}")
                                         String targetSpectrumMat,
                                         @Value("${detection.compatibility.htd-mamba-python-command:}")
                                         String htdMambaPythonCommand,
                                         @Value("${detection.compatibility.htd-mamba-conda-command:conda}")
                                         String htdMambaCondaCommand,
                                         @Value("${detection.compatibility.htd-mamba-conda-environment:htd}")
                                         String htdMambaCondaEnvironment,
                                         @Value("${detection.compatibility.htd-mamba-root:../lab-htd-main}")
                                         String htdMambaRoot,
                                         @Value("${detection.compatibility.htd-mamba-model-dataset:joint}")
                                         String htdMambaModelDataset,
                                         @Value("${detection.compatibility.htd-mamba-device:auto}")
                                         String htdMambaDevice,
                                         @Value("${detection.compatibility.htd-mamba-batch-size:256}")
                                         int htdMambaBatchSize,
                                         @Value("${detection.compatibility.random-delay-min-seconds:0}")
                                         int randomDelayMinSeconds,
                                         @Value("${detection.compatibility.random-delay-max-seconds:0}")
                                         int randomDelayMaxSeconds,
                                         @Value("${detection.compatibility.timeout-seconds:3600}")
                                         long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.pythonCommand = pythonCommand;
        this.runnerPath = runnerPath;
        this.workDir = workDir;
        this.targetSpectrumMat = targetSpectrumMat;
        this.htdMambaPythonCommand = htdMambaPythonCommand;
        this.htdMambaCondaCommand = htdMambaCondaCommand;
        this.htdMambaCondaEnvironment = htdMambaCondaEnvironment;
        this.htdMambaRoot = htdMambaRoot;
        this.htdMambaModelDataset = htdMambaModelDataset;
        this.htdMambaDevice = htdMambaDevice;
        this.htdMambaBatchSize = htdMambaBatchSize;
        this.randomDelayMinSeconds = randomDelayMinSeconds;
        this.randomDelayMaxSeconds = randomDelayMaxSeconds;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public DetectionPreviewResponse uploadAndPreview(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        if (isMatFile(file)) {
            return previewMatWithPython(file);
        }
        LoadedData loaded = loadData(file);
        String image = encodeOriginalImage(file);
        int h = loaded.cube.length;
        int w = loaded.cube[0].length;
        int b = loaded.cube[0][0].length;
        return new DetectionPreviewResponse(true, image, "success", fileExtension(file), new int[]{h, w, b});
    }

    @Override
    public DetectionResponse detectTargets(MultipartFile file, String targetsJson,
                                           String algorithm) throws IOException {
        return detectTargetsWithPython(file, targetsJson, normalizeAlgorithm(algorithm));
    }

    private DetectionPreviewResponse previewMatWithPython(MultipartFile file) throws IOException {
        Path resolvedWorkDir = resolvePath(workDir);
        Files.createDirectories(resolvedWorkDir);
        Path inputPath = Files.createTempFile(resolvedWorkDir, "detection_preview_input_", ".mat");
        Path outputJson = Files.createTempFile(resolvedWorkDir, "detection_preview_result_", ".json");
        try {
            file.transferTo(inputPath.toFile());
            Path resolvedRunnerPath = resolvePath(runnerPath);
            if (!Files.exists(resolvedRunnerPath)) {
                throw new IOException("Python detection runner not found: " + resolvedRunnerPath);
            }

            List<String> command = new ArrayList<>();
            command.add(pythonCommand);
            command.add(resolvedRunnerPath.toString());
            command.add("--input");
            command.add(inputPath.toString());
            command.add("--output-json");
            command.add(outputJson.toString());
            command.add("--work-dir");
            command.add(resolvedWorkDir.toString());
            command.add("--preview-only");
            executePython(command, resolvedRunnerPath, outputJson, "preview");
            return objectMapper.readValue(outputJson.toFile(), DetectionPreviewResponse.class);
        } finally {
            Files.deleteIfExists(inputPath);
            Files.deleteIfExists(outputJson);
        }
    }

    private DetectionResponse detectTargetsWithPython(MultipartFile file, String targetsJson,
                                                      String algorithm) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        Path resolvedWorkDir = resolvePath(workDir);
        Files.createDirectories(resolvedWorkDir);
        String extension = fileExtension(file);
        if (extension.isEmpty()) {
            extension = ".mat";
        }
        Path inputPath = Files.createTempFile(resolvedWorkDir, "detection_input_", extension);
        Path outputJson = Files.createTempFile(resolvedWorkDir, "detection_result_", ".json");
        file.transferTo(inputPath.toFile());

        Path resolvedRunnerPath = resolvePath(runnerPath);
        if (!Files.exists(resolvedRunnerPath)) {
            throw new IOException("Python detection runner not found: " + resolvedRunnerPath);
        }

        List<String> command = createPythonCommand(algorithm);
        command.add(resolvedRunnerPath.toString());
        command.add("--algorithm");
        command.add(algorithm);
        command.add("--input");
        command.add(inputPath.toString());
        command.add("--targets");
        command.add(targetsJson == null || targetsJson.trim().isEmpty() ? "[]" : targetsJson);
        command.add("--output-json");
        command.add(outputJson.toString());
        command.add("--work-dir");
        command.add(resolvedWorkDir.toString());
        command.add("--random-delay-min-seconds");
        command.add(Integer.toString(randomDelayMinSeconds));
        command.add("--random-delay-max-seconds");
        command.add(Integer.toString(randomDelayMaxSeconds));
        Path resolvedTargetSpectrumMat = resolvePath(targetSpectrumMat);
        if (Files.exists(resolvedTargetSpectrumMat)) {
            command.add("--target-spectrum-mat");
            command.add(resolvedTargetSpectrumMat.toString());
        }
        if (ALGORITHM_HTD_MAMBA.equals(algorithm)) {
            Path resolvedHtdMambaRoot = resolvePath(htdMambaRoot);
            if (!Files.isDirectory(resolvedHtdMambaRoot)) {
                throw new IOException("HTD-Mamba root directory not found: " + resolvedHtdMambaRoot);
            }
            command.add("--htd-mamba-root");
            command.add(resolvedHtdMambaRoot.toString());
            command.add("--htd-mamba-model-dataset");
            command.add(htdMambaModelDataset);
            command.add("--htd-mamba-device");
            command.add(htdMambaDevice);
            command.add("--htd-mamba-batch-size");
            command.add(Integer.toString(htdMambaBatchSize));
        }

        executePython(command, resolvedRunnerPath, outputJson, algorithm + " detection");
        return objectMapper.readValue(outputJson.toFile(), DetectionResponse.class);
    }

    private List<String> createPythonCommand(String algorithm) {
        List<String> command = new ArrayList<>();
        if (!ALGORITHM_HTD_MAMBA.equals(algorithm)) {
            command.add(pythonCommand);
            return command;
        }
        if (htdMambaPythonCommand != null && !htdMambaPythonCommand.trim().isEmpty()) {
            command.add(htdMambaPythonCommand.trim());
            return command;
        }
        command.add(htdMambaCondaCommand);
        command.add("run");
        command.add("--no-capture-output");
        command.add("-n");
        command.add(htdMambaCondaEnvironment);
        command.add("python");
        return command;
    }

    private String normalizeAlgorithm(String algorithm) {
        String normalized = algorithm == null ? ALGORITHM_HTD_MAMBA
                : algorithm.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return ALGORITHM_HTD_MAMBA;
        }
        if (!ALGORITHM_HCEM.equals(normalized) && !ALGORITHM_HTD_MAMBA.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported detection algorithm: " + algorithm);
        }
        return normalized;
    }

    private void executePython(List<String> command, Path resolvedRunnerPath,
                               Path outputJson, String operation) throws IOException {
        Process process = new ProcessBuilder(command)
                .directory(resolvedRunnerPath.getParent().toFile())
                .redirectErrorStream(true)
                .start();
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Python " + operation + " was interrupted", exception);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Python " + operation + " timed out after "
                    + timeoutSeconds + " seconds");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Python " + operation + " failed: " + output);
        }
        if (!Files.exists(outputJson)) {
            throw new IOException("Python " + operation + " did not write output JSON: " + output);
        }
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private DetectionResponse detectTargetsWithJavaFallback(MultipartFile file, String targetsJson) throws IOException {
        long startedAt = System.nanoTime();
        LoadedData loaded = loadData(file);
        List<String> targetCodes = parseTargets(targetsJson);
        int h = loaded.cube.length;
        int w = loaded.cube[0].length;
        float[][][] fused = new float[h][w][3];
        List<DetectedTargetVO> detectedTargets = new ArrayList<>();
        Map<String, TargetStatisticsVO> statistics = new LinkedHashMap<>();

        for (String code : targetCodes) {
            TargetConfig config = TARGET_CONFIGS.get(code);
            if (config == null) {
                continue;
            }
            double[] reference = resolveReferenceSpectrum(loaded, code, config);
            float[][] map = spectralMatch(loaded.cube, reference);
            TargetResult targetResult = analyzeTarget(map, config);
            statistics.put(code, targetResult.statistics);
            if (targetResult.statistics.isDetected()) {
                detectedTargets.add(new DetectedTargetVO(
                        code,
                        config.name,
                        config.color.clone(),
                        config.colorHex,
                        targetResult.detectedPixels,
                        targetResult.statistics.getObjectCount(),
                        round(targetResult.maxConfidence)
                ));
                mergeTargetImage(fused, map, config);
            }
        }

        String image = encodeRgbPng(fused);
        double runtime = (System.nanoTime() - startedAt) / 1_000_000_000.0d;
        return new DetectionResponse(
                true,
                image,
                "success",
                detectedTargets,
                statistics,
                false,
                round(runtime)
        );
    }

    private LoadedData loadData(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        if (isMatFile(file)) {
            Path temp = Files.createTempFile("detection_upload_" + Instant.now().toEpochMilli(), ".mat");
            try {
                file.transferTo(temp.toFile());
                Dataset.MatData matData = Dataset.MatIO.read(temp);
                float[][][] cube = selectHyperspectralCube(matData);
                return new LoadedData(cube, matData);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        BufferedImage image;
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
        }
        if (image == null) {
            throw new IOException("无法读取图像文件");
        }
        return new LoadedData(imageToCube(image), null);
    }

    private float[][][] selectHyperspectralCube(Dataset.MatData matData) throws IOException {
        for (String name : CUBE_NAMES) {
            float[][][] cube = matData.cubes.get(name);
            if (isUsableCube(cube)) {
                return cube;
            }
        }
        for (float[][][] cube : matData.cubes.values()) {
            if (isUsableCube(cube)) {
                return cube;
            }
        }
        throw new IOException("MAT 文件中未找到可用于探测的高光谱数据变量");
    }

    private boolean isUsableCube(float[][][] cube) {
        return cube != null && cube.length > 0 && cube[0].length > 0
                && cube[0][0].length > 1;
    }

    private List<String> parseTargets(String targetsJson) throws IOException {
        if (targetsJson == null || targetsJson.trim().isEmpty()) {
            return new ArrayList<>(TARGET_CONFIGS.keySet());
        }
        String trimmed = targetsJson.trim();
        Set<String> codes = new LinkedHashSet<>();
        if (trimmed.startsWith("[")) {
            List<String> values = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {
            });
            codes.addAll(values);
        } else if (trimmed.startsWith("{")) {
            Map<String, Object> values = objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {
            });
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (Boolean.FALSE.equals(entry.getValue())) {
                    continue;
                }
                codes.add(entry.getKey());
            }
        }
        codes.removeIf(code -> !TARGET_CONFIGS.containsKey(code));
        if (codes.isEmpty()) {
            codes.addAll(TARGET_CONFIGS.keySet());
        }
        return new ArrayList<>(codes);
    }

    private double[] resolveReferenceSpectrum(LoadedData loaded, String code, TargetConfig config) {
        int bands = loaded.cube[0][0].length;
        if (loaded.matData != null) {
            double[] vector = loaded.matData.vectors.get(code);
            if (vector != null && vector.length == bands) {
                return vector.clone();
            }
            float[][][] targetCube = loaded.matData.cubes.get(code);
            double[] spectrum = spectrumFromTargetCube(loaded.cube, targetCube);
            if (spectrum.length == bands) {
                return spectrum;
            }
        }
        return fallbackReferenceSpectrum(loaded.cube, config);
    }

    private double[] spectrumFromTargetCube(float[][][] sourceCube, float[][][] targetCube) {
        if (targetCube == null || targetCube.length == 0 || targetCube[0].length == 0) {
            return new double[0];
        }
        int h = sourceCube.length;
        int w = sourceCube[0].length;
        int bands = sourceCube[0][0].length;
        if (targetCube.length == h && targetCube[0].length == w && targetCube[0][0].length == 1) {
            double[] out = new double[bands];
            int count = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (targetCube[y][x][0] <= 0.0f) {
                        continue;
                    }
                    for (int k = 0; k < bands; k++) {
                        out[k] += sourceCube[y][x][k];
                    }
                    count++;
                }
            }
            if (count == 0) {
                return new double[0];
            }
            for (int k = 0; k < bands; k++) {
                out[k] /= count;
            }
            return out;
        }
        if (targetCube[0][0].length == bands) {
            double[] out = new double[bands];
            int count = 0;
            for (int y = 0; y < targetCube.length; y++) {
                for (int x = 0; x < targetCube[0].length; x++) {
                    boolean nonZero = false;
                    for (int k = 0; k < bands; k++) {
                        nonZero = nonZero || Math.abs(targetCube[y][x][k]) > 1e-8d;
                    }
                    if (!nonZero) {
                        continue;
                    }
                    for (int k = 0; k < bands; k++) {
                        out[k] += targetCube[y][x][k];
                    }
                    count++;
                }
            }
            if (count == 0) {
                return new double[0];
            }
            for (int k = 0; k < bands; k++) {
                out[k] /= count;
            }
            return out;
        }
        return new double[0];
    }

    private double[] fallbackReferenceSpectrum(float[][][] cube, TargetConfig config) {
        int bands = cube[0][0].length;
        if (bands == 3) {
            return new double[]{config.color[0], config.color[1], config.color[2]};
        }
        double[] mean = meanSpectrum(cube);
        double[] out = new double[bands];
        int seed = Math.abs(config.code.hashCode());
        for (int k = 0; k < bands; k++) {
            double a = Math.sin((k + 1) * (seed % 17 + 3) * 0.017d);
            double b = Math.cos((k + 1) * (seed % 29 + 5) * 0.011d);
            out[k] = Math.max(1e-6d, mean[k] * (0.9d + 0.18d * a + 0.12d * b));
        }
        return out;
    }

    private double[] meanSpectrum(float[][][] cube) {
        int h = cube.length;
        int w = cube[0].length;
        int bands = cube[0][0].length;
        double[] out = new double[bands];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int k = 0; k < bands; k++) {
                    out[k] += cube[y][x][k];
                }
            }
        }
        double total = Math.max(1, h * w);
        for (int k = 0; k < bands; k++) {
            out[k] /= total;
        }
        return out;
    }

    private float[][] spectralMatch(float[][][] cube, double[] reference) {
        int h = cube.length;
        int w = cube[0].length;
        int bands = cube[0][0].length;
        float[][] raw = new float[h][w];
        double refNorm = 0.0d;
        for (int k = 0; k < bands; k++) {
            refNorm += reference[k] * reference[k];
        }
        refNorm = Math.sqrt(Math.max(refNorm, 1e-12d));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dot = 0.0d;
                double pixNorm = 0.0d;
                for (int k = 0; k < bands; k++) {
                    double v = cube[y][x][k];
                    dot += v * reference[k];
                    pixNorm += v * v;
                }
                double cosine = dot / (Math.sqrt(Math.max(pixNorm, 1e-12d)) * refNorm);
                cosine = Math.max(0.0d, Math.min(1.0d, cosine));
                raw[y][x] = (float) (1.0d - Math.acos(cosine) / (Math.PI / 2.0d));
            }
        }
        return percentileNormalize(raw);
    }

    private float[][] percentileNormalize(float[][] raw) {
        int h = raw.length;
        int w = raw[0].length;
        float[] values = new float[h * w];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                values[idx++] = raw[y][x];
            }
        }
        Arrays.sort(values);
        float lo = values[(int) Math.floor(0.02d * (values.length - 1))];
        float hi = values[(int) Math.floor(0.995d * (values.length - 1))];
        if (hi <= lo) {
            hi = lo + 1e-6f;
        }
        float[][] out = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = (raw[y][x] - lo) / (hi - lo);
                out[y][x] = Math.max(0.0f, Math.min(1.0f, v));
            }
        }
        return out;
    }

    private TargetResult analyzeTarget(float[][] map, TargetConfig config) {
        int h = map.length;
        int w = map[0].length;
        boolean[][] mask = new boolean[h][w];
        int detectedPixels = 0;
        double sum = 0.0d;
        double max = 0.0d;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float v = map[y][x];
                if (v >= config.threshold) {
                    mask[y][x] = true;
                    detectedPixels++;
                    sum += v;
                }
                if (v > max) {
                    max = v;
                }
            }
        }
        List<DetectedObjectVO> objects = connectedComponents(mask, map);
        boolean detected = detectedPixels >= MIN_DETECTION_PIXELS
                && max >= config.threshold && !objects.isEmpty();
        double mean = detectedPixels == 0 ? 0.0d : sum / detectedPixels;
        double coverage = detectedPixels * 100.0d / Math.max(1, h * w);
        TargetStatisticsVO statistics = new TargetStatisticsVO(
                detectedPixels,
                objects.size(),
                objects,
                round(max),
                config.threshold,
                detected,
                round(mean),
                round(coverage)
        );
        return new TargetResult(statistics, detectedPixels, max);
    }

    private List<DetectedObjectVO> connectedComponents(boolean[][] mask, float[][] map) {
        int h = mask.length;
        int w = mask[0].length;
        boolean[][] visited = new boolean[h][w];
        List<DetectedObjectVO> out = new ArrayList<>();
        int id = 1;
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        for (int sy = 0; sy < h; sy++) {
            for (int sx = 0; sx < w; sx++) {
                if (!mask[sy][sx] || visited[sy][sx]) {
                    continue;
                }
                Queue<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{sx, sy});
                visited[sy][sx] = true;
                int count = 0;
                int minX = sx;
                int maxX = sx;
                int minY = sy;
                int maxY = sy;
                double sumX = 0.0d;
                double sumY = 0.0d;
                double sumScore = 0.0d;
                double maxScore = 0.0d;
                while (!queue.isEmpty()) {
                    int[] p = queue.remove();
                    int x = p[0];
                    int y = p[1];
                    count++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    sumX += x;
                    sumY += y;
                    sumScore += map[y][x];
                    maxScore = Math.max(maxScore, map[y][x]);
                    for (int i = 0; i < dx.length; i++) {
                        int nx = x + dx[i];
                        int ny = y + dy[i];
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                            continue;
                        }
                        if (visited[ny][nx] || !mask[ny][nx]) {
                            continue;
                        }
                        visited[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }
                if (count >= MIN_OBJECT_SIZE) {
                    out.add(new DetectedObjectVO(
                            id++,
                            count,
                            new int[]{minX, minY, maxX, maxY},
                            new double[]{round(sumX / count), round(sumY / count)},
                            round(maxScore),
                            round(sumScore / count)
                    ));
                }
            }
        }
        return out;
    }

    private void mergeTargetImage(float[][][] fused, float[][] map, TargetConfig config) {
        int h = fused.length;
        int w = fused[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float score = map[y][x];
                if (score < config.threshold) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    float value = (float) (config.color[c] * Math.max(score, 0.35f));
                    fused[y][x][c] = Math.max(fused[y][x][c], value);
                }
            }
        }
    }

    private float[][][] imageToCube(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        float[][][] cube = new float[h][w][3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                cube[y][x][0] = (rgb >> 16) & 0xff;
                cube[y][x][1] = (rgb >> 8) & 0xff;
                cube[y][x][2] = rgb & 0xff;
            }
        }
        return cube;
    }

    private String encodeOriginalImage(MultipartFile file) throws IOException {
        BufferedImage image;
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
        }
        if (image == null) {
            throw new IOException("无法读取图像文件");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private String encodeRgbPng(float[][][] rgb) throws IOException {
        BufferedImage image = new BufferedImage(rgb[0].length, rgb.length, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < rgb.length; y++) {
            for (int x = 0; x < rgb[0].length; x++) {
                int r = clamp8(rgb[y][x][0]);
                int g = clamp8(rgb[y][x][Math.min(1, rgb[y][x].length - 1)]);
                int b = clamp8(rgb[y][x][Math.min(2, rgb[y][x].length - 1)]);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private int clamp8(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private boolean isMatFile(MultipartFile file) {
        return ".mat".equals(fileExtension(file));
    }

    private String fileExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx).toLowerCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private static Map<String, TargetConfig> createTargetConfigs() {
        Map<String, TargetConfig> out = new LinkedHashMap<>();
        out.put("d1", new TargetConfig("d1", "假草皮", 0.50d, new int[]{0, 128, 0}, "camouflage"));
        out.put("d2", new TargetConfig("d2", "木板", 0.55d, new int[]{218, 165, 32}, "camouflage"));
        out.put("d3", new TargetConfig("d3", "竹材", 0.60d, new int[]{128, 128, 128}, "camouflage"));
        out.put("d4", new TargetConfig("d4", "棕色迷彩服", 0.45d, new int[]{139, 69, 19}, "camouflage"));
        out.put("d5", new TargetConfig("d5", "绿色迷彩服", 0.50d, new int[]{0, 0, 255}, "camouflage"));
        out.put("d6", new TargetConfig("d6", "绿色伪装服", 0.60d, new int[]{173, 216, 230}, "camouflage"));
        out.put("d7", new TargetConfig("d7", "棕色伪装服", 0.50d, new int[]{240, 230, 140}, "camouflage"));
        out.put("d8", new TargetConfig("d8", "伪装网", 0.50d, new int[]{128, 0, 128}, "camouflage"));
        out.put("d9", new TargetConfig("d9", "火炮", 0.63d, new int[]{255, 0, 0}, "military"));
        out.put("d10", new TargetConfig("d10", "坦克", 0.63d, new int[]{0, 255, 255}, "military"));
        out.put("d11", new TargetConfig("d11", "装甲车", 0.55d, new int[]{255, 255, 0}, "military"));
        out.put("d12", new TargetConfig("d12", "哨塔", 0.60d, new int[]{255, 174, 201}, "other"));
        out.put("d13", new TargetConfig("d13", "碉堡", 0.55d, new int[]{160, 82, 45}, "other"));
        out.put("d14", new TargetConfig("d14", "竹材", 0.30d, new int[]{128, 128, 128}, "camouflage"));
        out.put("d15", new TargetConfig("d15", "假草皮", 0.70d, new int[]{0, 128, 0}, "camouflage"));
        out.put("d16", new TargetConfig("d16", "棕色迷彩服", 0.65d, new int[]{139, 69, 19}, "camouflage"));
        return Collections.unmodifiableMap(out);
    }

    private static class LoadedData {
        private final float[][][] cube;
        private final Dataset.MatData matData;

        LoadedData(float[][][] cube, Dataset.MatData matData) {
            this.cube = cube;
            this.matData = matData;
        }
    }

    private static class TargetConfig {
        private final String code;
        private final String name;
        private final double threshold;
        private final int[] color;
        private final String category;
        private final String colorHex;

        TargetConfig(String code, String name, double threshold, int[] color, String category) {
            this.code = code;
            this.name = name;
            this.threshold = threshold;
            this.color = color;
            this.category = category;
            this.colorHex = String.format(Locale.ROOT, "#%02X%02X%02X", color[0], color[1], color[2]);
        }
    }

    private static class TargetResult {
        private final TargetStatisticsVO statistics;
        private final int detectedPixels;
        private final double maxConfidence;

        TargetResult(TargetStatisticsVO statistics, int detectedPixels, double maxConfidence) {
            this.statistics = statistics;
            this.detectedPixels = detectedPixels;
            this.maxConfidence = maxConfidence;
        }
    }
}
