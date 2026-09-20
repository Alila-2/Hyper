package com.hyper.spectral.service.impl;

import com.hyper.spectral.config.fusion.FusionCompatibilityProperties;
import com.hyper.spectral.support.adapter.HysurePythonAdapter;
import com.hyper.spectral.support.adapter.HysurePythonAdapter.HysureFusionResult;
import com.hyper.spectral.support.adapter.MiaePythonAdapter;
import com.hyper.spectral.support.adapter.MiaePythonAdapter.MiaeFusionResult;
import com.hyper.spectral.support.fusion.CnmfFusion;
import com.hyper.spectral.support.fusion.Dataset;
import com.hyper.spectral.support.fusion.Registration;
import com.hyper.spectral.vo.fusion.FusionCompatibilityInfoResponse;
import com.hyper.spectral.vo.fusion.FusionDataInfoResponse;
import com.hyper.spectral.vo.fusion.FusionSpectrumResponse;
import com.hyper.spectral.vo.fusion.PngByMatResponse;
import com.hyper.spectral.vo.fusion.PngInfoResponse;
import com.hyper.spectral.vo.fusion.StartFusionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class FusionCompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(FusionCompatibilityService.class);
    private static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("0.0");
    private static final String[] LOW_HSI_VARIABLES = {
            Dataset.LRHSI, "hyperspectral_data", "HSI", "hsi", "data", "image",
            "cube", "hyperspectral"
    };

    private final FusionCompatibilityProperties properties;
    private final MiaePythonAdapter miaePythonAdapter;
    private final HysurePythonAdapter hysurePythonAdapter;
    private volatile FusionSession currentSession;

    public FusionCompatibilityService(FusionCompatibilityProperties properties,
                                      MiaePythonAdapter miaePythonAdapter,
                                      HysurePythonAdapter hysurePythonAdapter) {
        this.properties = properties;
        this.miaePythonAdapter = miaePythonAdapter;
        this.hysurePythonAdapter = hysurePythonAdapter;
    }

    public PngByMatResponse getPngByMat(MultipartFile file) throws IOException {
        String originalFilename = resolveOriginalFilename(file, "matched_scene.mat");
        Optional<Path> matchedBmp = findMatchingBmp(originalFilename);

        BufferedImage image = matchedBmp.flatMap(this::readImageQuietly).orElseGet(() -> createFallbackMatchedImage(originalFilename));
        byte[] encodedPng = encodePng(image);
        String responseFilename = matchedBmp.map(path -> path.getFileName().toString())
                .orElse(stripExtension(originalFilename) + ".bmp");
        String format = matchedBmp.map(path -> fileExtension(path.getFileName().toString())).orElse("bmp");
        String fileSize = FILE_SIZE_FORMAT.format(encodedPng.length / 1024.0d) + " KB";

        PngInfoResponse pngInfo = new PngInfoResponse(
                image.getWidth() + "\u00d7" + image.getHeight(),
                format,
                fileSize,
                image.getColorModel().hasAlpha() ? "RGBA" : "RGB",
                responseFilename
        );
        return new PngByMatResponse(
                Base64.getEncoder().encodeToString(encodedPng),
                pngInfo,
                responseFilename
        );
    }

    public StartFusionResponse startFusion(String baseUrl, MultipartFile file1, MultipartFile file2) throws IOException {
        String filename = resolveOriginalFilename(file1, "");
        if (!StringUtils.hasText(filename)) {
            throw new IOException("未选择 HDR、IMG 或 MAT 文件");
        }

        String extension = fileExtension(filename);
        if (isRawDatasetExtension(extension)) {
            return startFusionFromRawDataset(baseUrl, filename, file2);
        }
        if (!"mat".equals(extension)) {
            throw new IOException("不支持的融合输入格式: " + extension + "，请选择 .hdr、.img 或 .mat 文件");
        }

        long startAt = System.currentTimeMillis();

        Path tempDir = Files.createTempDirectory("fusion_");
        try {
            Path matPath = tempDir.resolve("input.mat");
            file1.transferTo(matPath.toFile());

            if (miaePythonAdapter.isEnabled() || hysurePythonAdapter.isEnabled()) {
                Path fusionInput = preparePythonFusionInput(matPath, file2, tempDir);
                return miaePythonAdapter.isEnabled()
                        ? doMiaeFusion(baseUrl, fusionInput, startAt)
                        : doHysureFusion(baseUrl, fusionInput, startAt);
            }

            Dataset.ImagePair pair;
            try {
                pair = Dataset.fromMat(matPath);
            } catch (Exception exception) {
                log.info("Uploaded MAT is not a prepared LRHSI/HRMSI pair: {}", exception.getMessage());
                pair = null;
            }

            if (pair != null) {
                try {
                    return doRealFusion(baseUrl, pair, startAt);
                } catch (RuntimeException e) {
                    throw new IOException("真实 CNMF 融合失败: " + e.getMessage(), e);
                }
            }

            if (file2 == null || file2.isEmpty()) {
                throw new IOException("MAT文件不含LRHSI/HRMSI且未提供模态二图像，无法执行融合");
            }
            return doMockFusion(baseUrl, file1, file2, startAt);
        } finally {
            try {
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    private Path preparePythonFusionInput(Path matPath, MultipartFile imageFile, Path tempDir)
            throws IOException {
        try {
            Dataset.fromMat(matPath);
            return matPath;
        } catch (Exception exception) {
            if (imageFile == null || imageFile.isEmpty()) {
                log.info("Java could not inspect uploaded MAT; Python fusion will validate it: {}",
                        exception.getMessage());
                return matPath;
            }
            log.info("Preparing LRHSI/HRMSI pair from separate uploads: {}",
                    exception.getMessage());
        }

        Dataset.MatData data;
        try {
            data = Dataset.MatIO.read(matPath);
        } catch (Exception exception) {
            throw new IOException("Unable to read uploaded hyperspectral MAT: "
                    + exception.getMessage(), exception);
        }
        float[][][] lowHsi = findLowHsi(data);
        BufferedImage image = readMultipartImage(imageFile)
                .orElseThrow(() -> new IOException("Unable to read uploaded HRMSI image"));
        int targetHeight = lowHsi.length * 2;
        int targetWidth = lowHsi[0].length * 2;
        float[][][] highMsi = resizeRgbImage(image, targetWidth, targetHeight);
        double[] wavelengths = data.vectors.getOrDefault(Dataset.WAVELENGTHS, new double[0]);
        Dataset.ImagePair pair = new Dataset.ImagePair(lowHsi, highMsi, wavelengths, null);
        Path pairedMat = tempDir.resolve("fusion_input.mat");
        Dataset.MatIO.write(
                pairedMat, Dataset.MatData.fromImagePair(pair), Dataset.MatVersion.V5);
        return pairedMat;
    }

    private float[][][] findLowHsi(Dataset.MatData data) throws IOException {
        for (String variable : LOW_HSI_VARIABLES) {
            float[][][] cube = data.cubes.get(variable);
            if (isHyperspectralCube(cube)) {
                return cube;
            }
        }
        for (float[][][] cube : data.cubes.values()) {
            if (isHyperspectralCube(cube)) {
                return cube;
            }
        }
        throw new IOException("Uploaded MAT does not contain a usable hyperspectral cube");
    }

    private boolean isHyperspectralCube(float[][][] cube) {
        return cube != null && cube.length > 0 && cube[0].length > 0
                && cube[0][0].length > 3;
    }

    private float[][][] resizeRgbImage(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        float[][][] cube = new float[targetHeight][targetWidth][3];
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int rgb = resized.getRGB(x, y);
                cube[y][x][0] = (rgb >> 16) & 0xff;
                cube[y][x][1] = (rgb >> 8) & 0xff;
                cube[y][x][2] = rgb & 0xff;
            }
        }
        return cube;
    }

    private StartFusionResponse startFusionFromRawDataset(String baseUrl, String selectedName,
                                                          MultipartFile file2) throws IOException {
        long startAt = System.currentTimeMillis();
        Path rootDir = resolveSearchRoot();
        Path selectedPath = resolveDatasetFile(rootDir, selectedName);
        Path datasetDirectory = selectedPath.getParent();
        String stem = stripExtension(selectedPath.getFileName().toString());
        Path hdrPath = resolveHeader(datasetDirectory, stem);
        Path imgPath = resolveEnviImage(datasetDirectory, hdrPath.getFileName().toString());
        Path bmpPath = resolveBmpImage(datasetDirectory, hdrPath.getFileName().toString(), file2);

        log.info("Raw fusion pipeline: selected={}, HDR={}, IMG={}, BMP={}",
                selectedPath, hdrPath, imgPath, bmpPath);
        loadOpenCv();
        int targetLrSize = properties.getRegistrationLrSize();
        Dataset.ImagePair pair = targetLrSize > 0
                ? Dataset.fromRaw(hdrPath, imgPath, bmpPath, targetLrSize)
                : Dataset.fromRaw(hdrPath, imgPath, bmpPath);
        log.info("Loaded Dataset input: {}", pair.summary());

        Path pipelineDirectory = createPipelineDirectory(stem);
        Path preprocessedMat = pipelineDirectory.resolve("preprocessed.mat");
        Dataset.MatIO.write(preprocessedMat, Dataset.MatData.fromImagePair(pair), Dataset.MatVersion.V5);
        log.info("Dataset MAT written: {}", preprocessedMat);

        Dataset.ImagePair registeredPair;
        Registration.RegisteredPair registration = null;
        try {
            registration = Registration.register(pair, targetLrSize);
            registeredPair = new Dataset.ImagePair(
                    registration.lrhsi, registration.hrmsi, pair.wavelengths, pair.sourceHeader);
            log.info("Registration: {} matches, {} inliers, RMSE={} LR pixels, {}",
                    registration.goodMatches, registration.inliers, registration.inlierRmse,
                    registration.summary());
        } catch (Exception e) {
            if (!properties.isRegistrationFallbackEnabled()) {
                throw new IOException("图像配准失败，已停止融合: " + e.getMessage(), e);
            }
            registeredPair = alignBySize(pair, targetLrSize);
            log.warn("Feature registration failed; explicitly enabled size fallback was used: {}", e.getMessage());
        }

        Path registeredMat = pipelineDirectory.resolve("registered.mat");
        Dataset.MatData registeredData = Dataset.MatData.fromImagePair(registeredPair);
        if (registration != null) {
            registeredData.vectors.put("affine_low", flatten(registration.affineLow));
            registeredData.vectors.put("affine_high", flatten(registration.affineHigh));
            registeredData.vectors.put("crop_xywh_low", registration.cropXywhLow());
            registeredData.vectors.put("registration_good_matches",
                    new double[]{registration.goodMatches});
            registeredData.vectors.put("registration_inliers", new double[]{registration.inliers});
            registeredData.vectors.put("registration_inlier_rmse",
                    new double[]{registration.inlierRmse});
        } else {
            registeredData.vectors.put("registration_fallback_used", new double[]{1.0d});
        }
        Dataset.MatIO.write(registeredMat, registeredData, Dataset.MatVersion.V5);
        log.info("Registered MAT written: {}", registeredMat);

        String fusionAlgorithm = miaePythonAdapter.isEnabled()
                ? "MIAE" : (hysurePythonAdapter.isEnabled() ? "HySure" : "CNMF");
        log.info("Starting {} fusion on {}x{}x{} + {}x{}x{} ...",
                fusionAlgorithm,
                registeredPair.lrhsi.length, registeredPair.lrhsi[0].length,
                registeredPair.lrhsi[0][0].length,
                registeredPair.hrmsi.length, registeredPair.hrmsi[0].length,
                registeredPair.hrmsi[0][0].length);

        StartFusionResponse response;
        if (miaePythonAdapter.isEnabled()) {
            response = doMiaeFusion(baseUrl, registeredMat, startAt);
        } else if (hysurePythonAdapter.isEnabled()) {
            response = doHysureFusion(baseUrl, registeredMat, startAt);
        } else {
            response = doRealFusion(baseUrl, registeredPair, startAt);
        }
        if (!properties.isKeepIntermediateMat()) {
            deleteDirectoryQuietly(pipelineDirectory);
        }
        return response;
    }

    private boolean isRawDatasetExtension(String extension) {
        return "hdr".equals(extension)
                || "img".equals(extension)
                || "raw".equals(extension)
                || "dat".equals(extension)
                || "bin".equals(extension);
    }

    private Path resolveDatasetFile(Path rootDir, String selectedName) throws IOException {
        String dateDir = extractDateDirectory(selectedName);
        if (StringUtils.hasText(dateDir)) {
            Path direct = rootDir.resolve(dateDir).resolve(selectedName);
            if (Files.isRegularFile(direct)) {
                return direct;
            }
        }
        Path rootCandidate = rootDir.resolve(selectedName);
        if (Files.isRegularFile(rootCandidate)) {
            return rootCandidate;
        }
        try (Stream<Path> stream = Files.walk(rootDir)) {
            Optional<Path> matched = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(selectedName))
                    .findFirst();
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        throw new IOException("服务器数据目录中找不到所选原始文件: " + selectedName);
    }

    private Path resolveHeader(Path datasetDirectory, String stem) throws IOException {
        Path header = datasetDirectory.resolve(stem + ".hdr");
        if (Files.isRegularFile(header)) {
            return header;
        }
        try (Stream<Path> stream = Files.list(datasetDirectory)) {
            Optional<Path> matched = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(stem + ".hdr"))
                    .findFirst();
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        throw new IOException("找不到原始数据对应的 HDR 文件: " + stem + ".hdr");
    }

    private void loadOpenCv() throws IOException {
        try {
            Dataset.loadOpenCv(null);
        } catch (RuntimeException | LinkageError exception) {
            throw new IOException("OpenCV 本地库加载失败: " + exception.getMessage(), exception);
        }
    }

    private Dataset.ImagePair alignBySize(Dataset.ImagePair pair, int configuredLrSize) {
        int targetLrSize = configuredLrSize > 0
                ? Math.max(8, configuredLrSize)
                : Math.min(pair.lrhsi.length, pair.lrhsi[0].length);
        int targetHrSize = targetLrSize * 2;
        float[][][] lrhsi = configuredLrSize > 0
                ? Registration.resizeCube(pair.lrhsi, targetLrSize, targetLrSize)
                : centerCropCube(pair.lrhsi, targetLrSize, targetLrSize);
        float[][][] hrmsi = configuredLrSize > 0
                ? Registration.resizeCube(pair.hrmsi, targetHrSize, targetHrSize)
                : resizeCorrespondingHrCrop(pair, targetLrSize, targetHrSize);
        return new Dataset.ImagePair(lrhsi, hrmsi, pair.wavelengths, pair.sourceHeader);
    }

    private float[][][] resizeCorrespondingHrCrop(Dataset.ImagePair pair, int targetLrSize, int targetHrSize) {
        int hLr = pair.lrhsi.length;
        int wLr = pair.lrhsi[0].length;
        int hHr = pair.hrmsi.length;
        int wHr = pair.hrmsi[0].length;
        int lrX0 = (wLr - targetLrSize) / 2;
        int lrY0 = (hLr - targetLrSize) / 2;
        double sx = (double) wHr / wLr;
        double sy = (double) hHr / hLr;
        int hrX0 = clamp((int) Math.round(lrX0 * sx), 0, wHr - 1);
        int hrY0 = clamp((int) Math.round(lrY0 * sy), 0, hHr - 1);
        int hrX1 = clamp((int) Math.round((lrX0 + targetLrSize) * sx), hrX0 + 1, wHr);
        int hrY1 = clamp((int) Math.round((lrY0 + targetLrSize) * sy), hrY0 + 1, hHr);
        float[][][] crop = cropCube(pair.hrmsi, hrY0, hrX0, hrY1 - hrY0, hrX1 - hrX0);
        return Registration.resizeCube(crop, targetHrSize, targetHrSize);
    }

    private float[][][] centerCropCube(float[][][] src, int targetH, int targetW) {
        int y0 = Math.max(0, (src.length - targetH) / 2);
        int x0 = Math.max(0, (src[0].length - targetW) / 2);
        int h = Math.min(targetH, src.length - y0);
        int w = Math.min(targetW, src[0].length - x0);
        return cropCube(src, y0, x0, h, w);
    }

    private float[][][] cropCube(float[][][] src, int y0, int x0, int h, int w) {
        int bands = src[0][0].length;
        float[][][] out = new float[h][w][bands];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                System.arraycopy(src[y0 + y][x0 + x], 0, out[y][x], 0, bands);
            }
        }
        return out;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double[] flatten(double[][] matrix) {
        double[] flattened = new double[matrix.length * matrix[0].length];
        int index = 0;
        for (double[] row : matrix) {
            for (double value : row) {
                flattened[index++] = value;
            }
        }
        return flattened;
    }

    private Path createPipelineDirectory(String stem) throws IOException {
        Path directory = ensureResultDirectory()
                .resolve("fusion-work")
                .resolve(stem + "_" + Instant.now().toEpochMilli());
        Files.createDirectories(directory);
        return directory;
    }

    private Path resolveSearchRoot() throws IOException {
        String raw = properties.getSearchRootDir();
        if (!StringUtils.hasText(raw)) {
            throw new IOException("未配置 fusion.compatibility.search-root-dir");
        }
        Path root = Paths.get(raw);
        if (!Files.isDirectory(root)) {
            throw new IOException("融合数据根目录不存在: " + raw);
        }
        return root;
    }

    private String extractDateDirectory(String hdrName) {
        if (!StringUtils.hasText(hdrName) || !hdrName.startsWith("D_") || hdrName.length() < 10) {
            return "";
        }
        return hdrName.substring(2, 10);
    }

    private Path resolveEnviImage(Path workDir, String hdrName) throws IOException {
        String stem = stripExtension(hdrName);
        String[] candidates = {"", ".img", ".raw", ".dat", ".bin"};
        for (String ext : candidates) {
            Path candidate = ext.isEmpty() ? workDir.resolve(stem) : workDir.resolve(stem + ext);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("找不到ENVI数据文件: " + stem + "（已搜索 .img/.raw/.dat/.bin）");
    }

    private Path resolveBmpImage(Path workDir, String hdrName, MultipartFile fallbackFile) throws IOException {
        String stem = stripExtension(hdrName);
        Path bmpPath = workDir.resolve(stem + ".bmp");
        if (Files.exists(bmpPath)) {
            return bmpPath;
        }
        Path colorPath = workDir.resolve("color.bmp");
        if (Files.exists(colorPath)) {
            return colorPath;
        }
        if (fallbackFile != null && !fallbackFile.isEmpty()) {
            String extension = fileExtension(resolveOriginalFilename(fallbackFile, "image.bmp"));
            Path tempBmp = Files.createTempFile("fusion_bmp_", "." + (extension.isEmpty() ? "bmp" : extension));
            fallbackFile.transferTo(tempBmp.toFile());
            return tempBmp;
        }
        throw new IOException("找不到同名 BMP 或 color.bmp: " + workDir.toAbsolutePath());
    }

    private StartFusionResponse doRealFusion(String baseUrl, Dataset.ImagePair pair, long startAt) throws IOException {
        float[][][] hrhsi = CnmfFusion.cnmfFusion(pair.lrhsi, pair.hrmsi, 0, false);

        int h = hrhsi.length;
        int w = hrhsi[0].length;
        int bands = hrhsi[0][0].length;
        log.info("CNMF fusion completed: {}x{}x{} in {}ms",
                w, h, bands, System.currentTimeMillis() - startAt);

        Path resultDir = ensureResultDirectory();
        String resultName = "fusion_" + Instant.now().toEpochMilli() + ".png";
        Path resultPath = resultDir.resolve(resultName);

        int[] rgbBands = findRgbBands(pair.wavelengths);
        float[][][] rgbImage = Dataset.trueColor(
                hrhsi, rgbBands[0], rgbBands[1], rgbBands[2], null);
        Dataset.saveRgbPng(resultPath, rgbImage);

        Dataset.MatData outData = new Dataset.MatData();
        outData.cubes.put("HRHSI", hrhsi);
        if (pair.wavelengths.length > 0) {
            outData.vectors.put(Dataset.WAVELENGTHS, pair.wavelengths);
        }
        Path matOut = resultDir.resolve(stripExtension(resultName) + ".mat");
        Dataset.MatIO.write(matOut, outData, Dataset.MatVersion.V5);

        currentSession = FusionSession.fromReal(hrhsi, resultPath, pair.wavelengths);

        long elapsedMs = System.currentTimeMillis() - startAt;
        FusionCompatibilityInfoResponse fusionInfo = new FusionCompatibilityInfoResponse(
                w + "\u00d7" + h, bands,
                String.format(Locale.ROOT, "%.2f\u79d2", elapsedMs / 1000.0d),
                FILE_SIZE_FORMAT.format(Files.size(resultPath) / 1024.0d) + " KB",
                "RGB", "PNG", w, h);
        String imageUrl = baseUrl + "/static/fusion_results/" + resultName;
        return new StartFusionResponse(imageUrl, fusionInfo);
    }

    private StartFusionResponse doHysureFusion(String baseUrl, Path inputMat, long startAt)
            throws IOException {
        Path resultRoot = ensureResultDirectory().toAbsolutePath().normalize();
        Path outputDirectory = Files.createTempDirectory(resultRoot, "hysure_");
        HysureFusionResult result = hysurePythonAdapter.fuse(inputMat, outputDirectory);
        Path previewPath = result.getPreviewPath();
        Path fusionPath = result.getFusionPath();
        currentSession = FusionSession.fromHysure(
                result.width, result.height, result.bands, previewPath, fusionPath);

        long elapsedMs = System.currentTimeMillis() - startAt;
        FusionCompatibilityInfoResponse fusionInfo = new FusionCompatibilityInfoResponse(
                result.width + "\u00d7" + result.height,
                result.bands,
                String.format(Locale.ROOT, "%.2f\u79d2", elapsedMs / 1000.0d),
                FILE_SIZE_FORMAT.format(result.previewFileSizeBytes / 1024.0d) + " KB",
                "RGB",
                "PNG",
                result.width,
                result.height
        );
        String relativePreview = resultRoot.relativize(previewPath)
                .toString().replace(File.separatorChar, '/');
        String imageUrl = baseUrl + "/static/fusion_results/" + relativePreview;
        log.info("HySure fusion completed: {}x{}x{} in {}ms, result={}",
                result.width, result.height, result.bands, elapsedMs, fusionPath);
        return new StartFusionResponse(imageUrl, fusionInfo);
    }

    private StartFusionResponse doMiaeFusion(String baseUrl, Path inputMat, long startAt)
            throws IOException {
        Path resultRoot = ensureResultDirectory().toAbsolutePath().normalize();
        Path outputDirectory = Files.createTempDirectory(resultRoot, "miae_");
        MiaeFusionResult result = miaePythonAdapter.fuse(inputMat, outputDirectory);
        Path previewPath = result.getPreviewPath();
        Path fusionPath = result.getFusionPath();
        currentSession = FusionSession.fromPython(
                result.width, result.height, result.bands, previewPath, fusionPath,
                PythonFusionEngine.MIAE);

        long elapsedMs = System.currentTimeMillis() - startAt;
        FusionCompatibilityInfoResponse fusionInfo = new FusionCompatibilityInfoResponse(
                result.width + "\u00d7" + result.height,
                result.bands,
                String.format(Locale.ROOT, "%.2f\u79d2", elapsedMs / 1000.0d),
                FILE_SIZE_FORMAT.format(result.fusionFileSizeBytes / 1024.0d) + " KB",
                "RGB",
                "PNG",
                result.width,
                result.height
        );
        String relativePreview = resultRoot.relativize(previewPath)
                .toString().replace(File.separatorChar, '/');
        String imageUrl = baseUrl + "/static/fusion_results/" + relativePreview;
        log.info("MIAE fusion completed: {}x{}x{} in {}ms, MAT result={}",
                result.width, result.height, result.bands, elapsedMs, result.getMatPath());
        return new StartFusionResponse(imageUrl, fusionInfo);
    }

    private StartFusionResponse doMockFusion(String baseUrl, MultipartFile file1, MultipartFile file2, long startAt) throws IOException {
        String matName = resolveOriginalFilename(file1, "LRHSI.mat");
        String imageName = resolveOriginalFilename(file2, "HRMSI.png");
        int totalBands = inferBandCount(matName);

        BufferedImage sourceImage = readMultipartImage(file2).orElseGet(() -> createFallbackMatchedImage(imageName));
        int fusionWidth = Math.max(properties.getSampleFusionWidth(), 256);
        int fusionHeight = Math.max(properties.getSampleFusionHeight(), 256);
        int seed = computeSeed(matName + "|" + imageName + "|" + totalBands);
        BufferedImage fusionImage = createFusionImage(sourceImage, fusionWidth, fusionHeight, seed);

        Path resultDirectory = ensureResultDirectory();
        String resultName = "fusion_" + Instant.now().toEpochMilli() + ".png";
        Path resultPath = resultDirectory.resolve(resultName);
        ImageIO.write(fusionImage, "png", resultPath.toFile());

        long elapsedMs = System.currentTimeMillis() - startAt;
        currentSession = FusionSession.fromMock(fusionWidth, fusionHeight, totalBands, seed, resultPath);

        FusionCompatibilityInfoResponse fusionInfo = new FusionCompatibilityInfoResponse(
                fusionWidth + "\u00d7" + fusionHeight,
                totalBands,
                String.format(Locale.ROOT, "%.2f\u79d2", elapsedMs / 1000.0d),
                FILE_SIZE_FORMAT.format(Files.size(resultPath) / 1024.0d) + " KB",
                fusionImage.getColorModel().hasAlpha() ? "RGBA" : "RGB",
                "PNG",
                fusionWidth,
                fusionHeight
        );
        String imageUrl = baseUrl + "/static/fusion_results/" + resultName;
        return new StartFusionResponse(imageUrl, fusionInfo);
    }

    public FusionDataInfoResponse getFusionInfo() {
        FusionSession session = requireSession();
        List<Integer> shape = new ArrayList<>();
        shape.add(session.height);
        shape.add(session.width);
        shape.add(session.bands);
        return new FusionDataInfoResponse(session.width, session.height, session.bands,
                session.real ? "float32" : "float64", shape);
    }

    public FusionSpectrumResponse getFusionSpectrum(int x, int y) throws IOException {
        FusionSession session = requireSession();
        if (x < 0 || x >= session.width || y < 0 || y >= session.height) {
            throw new IllegalArgumentException(
                    String.format("\u5750\u6807\u8d85\u51fa\u8303\u56f4\uff1ax in [0,%d], y in [0,%d]",
                            session.width - 1, session.height - 1));
        }

        List<Double> spectrum;
        if (session.fusionDataPath != null) {
            spectrum = session.pythonFusionEngine == PythonFusionEngine.MIAE
                    ? miaePythonAdapter.readSpectrum(session.fusionDataPath, x, y)
                    : hysurePythonAdapter.readSpectrum(session.fusionDataPath, x, y);
        } else if (session.real && session.hrhsi != null) {
            float[][][] cube = session.hrhsi;
            int bands = cube[0][0].length;
            spectrum = new ArrayList<>(bands);
            for (int b = 0; b < bands; b++) {
                spectrum.add((double) Math.round(cube[y][x][b] * 10000.0) / 10000.0);
            }
        } else {
            spectrum = IntStream.range(0, session.bands)
                    .mapToObj(bandIndex -> computeSpectrumValue(session, x, y, bandIndex))
                    .collect(Collectors.toList());
        }
        return new FusionSpectrumResponse(spectrum);
    }

    private FusionSession requireSession() {
        FusionSession session = currentSession;
        if (session == null) {
            throw new IllegalStateException("\u672a\u52a0\u8f7d\u878d\u5408\u6570\u636e\uff0c\u8bf7\u5148\u5b8c\u6210\u56fe\u50cf\u878d\u5408");
        }
        return session;
    }

    private int[] findRgbBands(double[] wavelengths) {
        if (wavelengths == null || wavelengths.length == 0) {
            return new int[]{60, 38, 16};
        }
        return new int[]{
                nearestBand(wavelengths, 640),
                nearestBand(wavelengths, 550),
                nearestBand(wavelengths, 460)
        };
    }

    private int nearestBand(double[] wavelengths, double target) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < wavelengths.length; i++) {
            double d = Math.abs(wavelengths[i] - target);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private Optional<Path> findMatchingBmp(String matFilename) throws IOException {
        if (!StringUtils.hasText(properties.getSearchRootDir())) {
            return Optional.empty();
        }
        Path root = Paths.get(properties.getSearchRootDir());
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }

        String expectedName = stripExtension(matFilename) + ".bmp";
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(expectedName))
                    .max(Comparator.comparingLong(this::safeLastModifiedTime));
        }
    }

    private Optional<BufferedImage> readImageQuietly(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return Optional.ofNullable(ImageIO.read(inputStream));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> readMultipartImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream inputStream = file.getInputStream()) {
            return Optional.ofNullable(ImageIO.read(inputStream));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private BufferedImage createFallbackMatchedImage(String fileName) {
        int width = Math.max(properties.getSampleFusionWidth(), 256);
        int height = Math.max(properties.getSampleFusionHeight(), 256);
        int seed = computeSeed(fileName);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(48, 82, 130), width, height, new Color(166, 205, 255)));
        graphics.fillRect(0, 0, width, height);
        for (int y = 0; y < height; y += 12) {
            int alpha = 55 + ((y + seed) % 120);
            graphics.setColor(new Color(255, 255, 255, Math.min(alpha, 180)));
            graphics.drawLine(0, y, width, y + ((seed + y) % 18));
        }
        graphics.setColor(new Color(255, 255, 255, 180));
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.drawRoundRect(18, 18, width - 36, height - 36, 20, 20);
        graphics.setFont(new Font("Arial", Font.BOLD, 18));
        graphics.drawString("MATCHED BMP", 18, 34);
        graphics.dispose();
        return image;
    }

    private BufferedImage createFusionImage(BufferedImage sourceImage, int width, int height, int seed) {
        BufferedImage fusionImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = fusionImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int sourceWidth = Math.max(sourceImage.getWidth(), 1);
        int sourceHeight = Math.max(sourceImage.getHeight(), 1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sourceX = x * sourceWidth / width;
                int sourceY = y * sourceHeight / height;
                Color base = new Color(sourceImage.getRGB(sourceX, sourceY));
                float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
                float hue = (hsb[0] + ((seed + x * 3 + y * 5) % 100) / 500.0f) % 1.0f;
                float saturation = Math.min(0.98f, 0.45f + hsb[1] * 0.55f);
                float brightness = Math.min(0.98f, 0.35f + hsb[2] * 0.60f);
                fusionImage.setRGB(x, y, Color.HSBtoRGB(hue, saturation, brightness));
            }
        }

        graphics.setColor(new Color(255, 255, 255, 155));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(width / 6, height / 5, width / 4, height / 4);
        graphics.drawRect(width / 2, height / 3, width / 5, height / 5);
        graphics.setFont(new Font("Arial", Font.BOLD, 18));
        graphics.drawString("MOCK FUSION", 20, 30);
        graphics.dispose();
        return fusionImage;
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private Path ensureResultDirectory() throws IOException {
        Path resultDirectory = Paths.get(properties.getResultDir());
        Files.createDirectories(resultDirectory);
        return resultDirectory;
    }

    private String resolveOriginalFilename(MultipartFile file, String fallback) {
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename().trim();
        }
        return fallback;
    }

    private int inferBandCount(String matFilename) {
        if (StringUtils.hasText(matFilename) && matFilename.contains("320")) {
            return 320;
        }
        return properties.getSampleTotalBands();
    }

    private double computeSpectrumValue(FusionSession session, int x, int y, int bandIndex) {
        double normalizedX = (double) x / Math.max(session.width - 1, 1);
        double normalizedY = (double) y / Math.max(session.height - 1, 1);
        double phase = session.seed * 0.019 + bandIndex * 0.047;
        double peakOne = Math.exp(-Math.pow((bandIndex - 88.0) / 30.0, 2));
        double peakTwo = Math.exp(-Math.pow((bandIndex - 210.0) / 36.0, 2));
        double wave = 0.36
                + 0.19 * Math.sin(normalizedX * Math.PI * 2.4 + phase)
                + 0.16 * Math.cos(normalizedY * Math.PI * 2.8 + phase * 0.65)
                + 0.15 * peakOne
                + 0.11 * peakTwo;
        double adjusted = Math.max(0.0, Math.min(1.0, wave));
        return Math.round(adjusted * 10000.0) / 10000.0;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String fileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
    }

    private long safeLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException exception) {
            log.warn("Failed to delete fusion work directory {}: {}", directory, exception.getMessage());
        }
    }

    private int computeSeed(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(rawValue.getBytes(StandardCharsets.UTF_8));
            int combined = 0;
            for (byte value : digest) {
                combined = (combined * 31 + Byte.toUnsignedInt(value)) & Integer.MAX_VALUE;
            }
            return combined;
        } catch (NoSuchAlgorithmException exception) {
            return Math.abs(rawValue.hashCode());
        }
    }

    private static final class FusionSession {
        final boolean real;
        final int width;
        final int height;
        final int bands;
        final int seed;
        final Path resultPath;
        final Path fusionDataPath;
        final PythonFusionEngine pythonFusionEngine;
        final float[][][] hrhsi;
        final double[] wavelengths;

        private FusionSession(boolean real, int width, int height, int bands, int seed, Path resultPath,
                              Path fusionDataPath, PythonFusionEngine pythonFusionEngine,
                              float[][][] hrhsi, double[] wavelengths) {
            this.real = real;
            this.width = width;
            this.height = height;
            this.bands = bands;
            this.seed = seed;
            this.resultPath = resultPath;
            this.fusionDataPath = fusionDataPath;
            this.pythonFusionEngine = pythonFusionEngine;
            this.hrhsi = hrhsi;
            this.wavelengths = wavelengths;
        }

        static FusionSession fromReal(float[][][] hrhsi, Path resultPath, double[] wavelengths) {
            int w = hrhsi[0].length;
            int h = hrhsi.length;
            int b = hrhsi[0][0].length;
            return new FusionSession(true, w, h, b, 0, resultPath, null, null,
                    hrhsi, wavelengths);
        }

        static FusionSession fromHysure(int width, int height, int bands, Path resultPath,
                                        Path fusionDataPath) {
            return fromPython(width, height, bands, resultPath, fusionDataPath,
                    PythonFusionEngine.HYSURE);
        }

        static FusionSession fromPython(int width, int height, int bands, Path resultPath,
                                        Path fusionDataPath, PythonFusionEngine engine) {
            return new FusionSession(true, width, height, bands, 0, resultPath,
                    fusionDataPath, engine, null, null);
        }

        static FusionSession fromMock(int width, int height, int bands, int seed, Path resultPath) {
            return new FusionSession(false, width, height, bands, seed, resultPath,
                    null, null, null, null);
        }
    }

    private enum PythonFusionEngine {
        MIAE,
        HYSURE
    }
}
