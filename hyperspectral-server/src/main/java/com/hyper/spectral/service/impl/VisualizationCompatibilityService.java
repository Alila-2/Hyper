package com.hyper.spectral.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hyper.spectral.config.visualization.VisualizationCompatibilityProperties;
import com.hyper.spectral.support.envi.EnviCubeReader;
import com.hyper.spectral.support.envi.EnviCubeReader.EnviCube;
import com.hyper.spectral.support.matlab.MatCubeWriter;
import com.hyper.spectral.vo.visualization.LoadHyperspectralDataResponse;
import com.hyper.spectral.vo.visualization.MatPreviewInfoResponse;
import com.hyper.spectral.vo.visualization.MatPreviewResponse;
import com.hyper.spectral.vo.visualization.SpectrumQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Legacy visualization compatibility service.
 */
@Service
public class VisualizationCompatibilityService {

    private static final DateTimeFormatter PREVIEW_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Logger log = LoggerFactory.getLogger(VisualizationCompatibilityService.class);

    private final VisualizationCompatibilityProperties properties;
    private final EnviCubeReader enviCubeReader;

    private volatile VisualizationSession currentSession;

    public VisualizationCompatibilityService(VisualizationCompatibilityProperties properties) {
        this.properties = properties;
        this.enviCubeReader = new EnviCubeReader();
    }

    public LoadHyperspectralDataResponse loadLatestHyperspectralData() {
        try {
            Optional<Path> latestHdr = findLatestHdr();
            if (!latestHdr.isPresent()) {
                return buildFailureResponse("未找到可加载的 HDR 文件，请检查 visualization.compatibility.hdr-root-dir");
            }
            return buildLoadResponse(latestHdr.get());
        } catch (Exception exception) {
            log.error("加载最新 HDR 可视化数据失败", exception);
            return buildFailureResponse("加载高光谱数据失败: " + exception.getMessage());
        }
    }

    public LoadHyperspectralDataResponse loadHyperspectralDataFromUpload(String hdrName, MultipartFile hdrFile) {
        String candidateName = resolveUploadedHdrName(hdrName, hdrFile);
        if (!StringUtils.hasText(candidateName)) {
            return buildFailureResponse("未提供有效的 HDR 文件名");
        }

        try {
            Optional<Path> hdrPath = resolveHdrPathByName(candidateName);
            if (!hdrPath.isPresent()) {
                return buildFailureResponse("未找到所选 HDR 对应的真实文件: " + candidateName);
            }
            return buildLoadResponse(hdrPath.get());
        } catch (Exception exception) {
            log.error("加载指定 HDR 可视化数据失败: {}", candidateName, exception);
            return buildFailureResponse("加载所选 HDR 失败: " + exception.getMessage());
        }
    }

    public SpectrumQueryResponse querySpectrum(int x, int y) {
        VisualizationSession session = currentSession;
        if (session == null) {
            throw new IllegalStateException("未加载高光谱数据，请先调用 /api/load-hyperspectral-data");
        }
        if (x < 0 || x >= session.getPseudoWidth() || y < 0 || y >= session.getPseudoHeight()) {
            throw new IllegalArgumentException(
                    String.format(
                            Locale.ROOT,
                            "坐标超出伪彩图范围，x in [0,%d], y in [0,%d]",
                            session.getPseudoWidth() - 1,
                            session.getPseudoHeight() - 1
                    )
            );
        }

        EnviCube cube = session.getCube();
        int cubeX = mapCoordinate(x, session.getPseudoWidth(), cube.getSamples());
        int cubeY = mapCoordinate(y, session.getPseudoHeight(), cube.getLines());
        return new SpectrumQueryResponse(cube.spectrumAt(cubeX, cubeY));
    }

    public MatPreviewResponse generateMatPreview(String baseUrl, MultipartFile matFile, String targetVar)
            throws IOException {
        if (matFile == null || matFile.isEmpty()) {
            throw new IOException("MAT 文件为空");
        }

        String fileName = StringUtils.hasText(matFile.getOriginalFilename())
                ? matFile.getOriginalFilename()
                : "uploaded.mat";
        Optional<Path> matchedPreview = findMatchingVisualizationArtifact(fileName, "png");
        long fileSize = Math.max(matFile.getSize(), 1L);
        int bands = resolvePreviewBands(fileName, targetVar, matchedPreview);
        BufferedImage previewImage = matchedPreview
                .flatMap(this::readOriginalImage)
                .orElseGet(() -> createMatPreviewImage(fileName, fileSize, 256, 256));
        int previewWidth = previewImage.getWidth();
        int previewHeight = previewImage.getHeight();
        String previewName = "preview_" + LocalDateTime.now().format(PREVIEW_NAME_FORMATTER) + ".png";
        Path previewDirectory = ensurePreviewDirectory();
        Path previewPath = previewDirectory.resolve(previewName);
        ImageIO.write(previewImage, "png", previewPath.toFile());

        MatPreviewInfoResponse matInfo = new MatPreviewInfoResponse(
                previewWidth + "\u00D7" + previewHeight,
                bands,
                previewWidth + " \u00D7 " + previewHeight + " \u00D7 " + bands,
                properties.getSampleWavelengthRange()
        );
        String previewUrl = baseUrl + "/static/visualization-previews/" + previewName;
        return new MatPreviewResponse(previewUrl, matInfo);
    }

    private int resolvePreviewBands(String fileName, String targetVar, Optional<Path> matchedPreview) {
        if (matchedPreview.isPresent()) {
            Path previewPath = matchedPreview.get();
            Path candidateMeta = previewPath.resolveSibling(stripExtension(previewPath.getFileName().toString()) + ".meta.json");
            if (Files.exists(candidateMeta)) {
                try {
                    Map<?, ?> metadata = OBJECT_MAPPER.readValue(candidateMeta.toFile(), Map.class);
                    Object totalBands = metadata.get("totalBands");
                    if (totalBands instanceof Number) {
                        return ((Number) totalBands).intValue();
                    }
                } catch (IOException exception) {
                    log.warn("读取可视化元数据失败: {}", candidateMeta, exception);
                }
            }
            try {
                Optional<Path> matchedHdr = findMatchingVisualizationArtifact(fileName, "hdr");
                if (matchedHdr.isPresent()) {
                    return enviCubeReader.read(matchedHdr.get()).getBands();
                }
            } catch (IOException exception) {
                log.warn("读取同名 HDR 波段数失败: {}", fileName, exception);
            }
        }
        return inferBands(fileName, targetVar);
    }

    private Optional<Path> findMatchingVisualizationArtifact(String fileName, String extension) throws IOException {
        Optional<Path> rootDirectory = resolvePath(properties.getHdrRootDir());
        if (!rootDirectory.isPresent() || !Files.isDirectory(rootDirectory.get())) {
            return Optional.empty();
        }

        String targetName = stripExtension(fileName) + "." + extension.toLowerCase(Locale.ROOT);
        String dateDirectory = extractDateDirectory(stripExtension(fileName) + ".hdr");
        if (StringUtils.hasText(dateDirectory)) {
            Path datedDirectory = rootDirectory.get().resolve(dateDirectory);
            Path directPath = datedDirectory.resolve(targetName);
            if (Files.exists(directPath) && Files.isRegularFile(directPath)) {
                return Optional.of(directPath);
            }
        }

        try (Stream<Path> stream = Files.walk(rootDirectory.get())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(targetName))
                    .max(Comparator.comparingLong(this::safeLastModifiedTime));
        }
    }

    private LoadHyperspectralDataResponse buildLoadResponse(Path hdrPath) throws IOException {
        EnviCube cube = enviCubeReader.read(hdrPath);
        List<Integer> visualizationBands = normalizeVisualizationBands(cube.getBands());
        DerivedArtifacts artifacts = ensureDerivedArtifacts(hdrPath, cube, visualizationBands);

        BufferedImage originalImage = readRequiredImage(artifacts.getOriginalBmpPath(), "原始可见光 BMP");
        BufferedImage pseudoColorImage = readRequiredImage(artifacts.getPseudoPngPath(), "伪彩图 PNG");

        List<Integer> bands = IntStream.range(0, cube.getBands()).boxed().collect(Collectors.toList());
        currentSession = new VisualizationSession(
                cube,
                pseudoColorImage.getWidth(),
                pseudoColorImage.getHeight(),
                hdrPath,
                artifacts.getMatPath(),
                artifacts.getOriginalBmpPath(),
                artifacts.getPseudoPngPath()
        );

        return new LoadHyperspectralDataResponse(
                true,
                "数据加载完成",
                encodeImage(originalImage),
                encodeImage(pseudoColorImage),
                originalImage.getWidth(),
                originalImage.getHeight(),
                pseudoColorImage.getWidth(),
                pseudoColorImage.getHeight(),
                bands,
                cube.getBands(),
                visualizationBands
        );
    }

    private DerivedArtifacts ensureDerivedArtifacts(Path hdrPath, EnviCube cube, List<Integer> visualizationBands)
            throws IOException {
        Path directory = hdrPath.getParent();
        if (directory == null) {
            throw new IOException("HDR 文件路径缺少父目录: " + hdrPath);
        }

        String stem = stripExtension(hdrPath.getFileName().toString());
        Path originalBmpPath = directory.resolve(stem + ".bmp");
        Path pseudoPngPath = directory.resolve(stem + ".png");
        Path matPath = directory.resolve(stem + ".mat");
        Path metadataPath = directory.resolve(stem + ".meta.json");

        Optional<Path> visibleSource = resolveVisibleImageSource(directory, stem);
        if (visibleSource.isPresent()) {
            Path source = visibleSource.get();
            if (!source.equals(originalBmpPath) && needsRefresh(originalBmpPath, source)) {
                copyFileAtomically(source, originalBmpPath);
            }
        }
        if (!isReadableImage(originalBmpPath)) {
            saveImageAtomically(cube.renderDirectComposite(visualizationBands), "bmp", originalBmpPath);
        }

        if (needsRefresh(matPath, hdrPath)) {
            writeMatFileDirectly(cube, matPath);
        }

        if (needsRefresh(pseudoPngPath, hdrPath) || !isReadableImage(pseudoPngPath)) {
            saveImageAtomically(cube.renderMinMaxComposite(visualizationBands), "png", pseudoPngPath);
        }

        BufferedImage originalImage = readRequiredImage(originalBmpPath, "原始可见光 BMP");
        BufferedImage pseudoImage = readRequiredImage(pseudoPngPath, "伪彩图 PNG");
        writeMetadataAtomically(
                metadataPath,
                buildArtifactMetadata(
                        hdrPath,
                        matPath,
                        originalBmpPath,
                        pseudoPngPath,
                        visibleSource.orElse(originalBmpPath),
                        cube,
                        originalImage,
                        pseudoImage,
                        visualizationBands
                )
        );

        return new DerivedArtifacts(matPath, originalBmpPath, pseudoPngPath, metadataPath);
    }

    private Map<String, Object> buildArtifactMetadata(Path hdrPath, Path matPath, Path originalBmpPath, Path pseudoPngPath,
                                                      Path originalSourcePath, EnviCube cube, BufferedImage originalImage,
                                                      BufferedImage pseudoImage, List<Integer> visualizationBands)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hdrPath", hdrPath.toString());
        payload.put("matPath", matPath.toString());
        payload.put("originalBmpPath", originalBmpPath.toString());
        payload.put("pseudoPngPath", pseudoPngPath.toString());
        payload.put("originalSourcePath", originalSourcePath.toString());
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("hdrLastModified", Files.getLastModifiedTime(hdrPath).toMillis());
        payload.put("matLastModified", Files.getLastModifiedTime(matPath).toMillis());
        payload.put("originalBmpLastModified", Files.getLastModifiedTime(originalBmpPath).toMillis());
        payload.put("pseudoPngLastModified", Files.getLastModifiedTime(pseudoPngPath).toMillis());
        payload.put("cubeWidth", cube.getSamples());
        payload.put("cubeHeight", cube.getLines());
        payload.put("totalBands", cube.getBands());
        payload.put("visualizationBands", visualizationBands);
        payload.put("originalImageWidth", originalImage.getWidth());
        payload.put("originalImageHeight", originalImage.getHeight());
        payload.put("pseudoImageWidth", pseudoImage.getWidth());
        payload.put("pseudoImageHeight", pseudoImage.getHeight());
        return payload;
    }

    private void writeMetadataAtomically(Path metadataPath, Map<String, Object> payload) throws IOException {
        Path tempPath = createTempSibling(metadataPath);
        try {
            OBJECT_MAPPER.writeValue(tempPath.toFile(), payload);
            moveTempFile(tempPath, metadataPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void writeMatFileDirectly(EnviCube cube, Path matPath) throws IOException {
        MatCubeWriter.write(matPath, "hyperspectral_data", cube);
    }

    private void copyFileAtomically(Path sourcePath, Path targetPath) throws IOException {
        Path tempPath = createTempSibling(targetPath);
        try {
            Files.copy(sourcePath, tempPath, StandardCopyOption.REPLACE_EXISTING);
            moveTempFile(tempPath, targetPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void saveImageAtomically(BufferedImage image, String format, Path targetPath) throws IOException {
        Path tempPath = createTempSibling(targetPath);
        try {
            ImageIO.write(image, format, tempPath.toFile());
            moveTempFile(tempPath, targetPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private BufferedImage readRequiredImage(Path imagePath, String label) throws IOException {
        return readOriginalImage(imagePath).orElseThrow(() -> new IOException(label + " 不可读取: " + imagePath));
    }

    private boolean isReadableImage(Path imagePath) {
        return readOriginalImage(imagePath).isPresent();
    }

    private boolean needsRefresh(Path derivedPath, Path sourcePath) throws IOException {
        if (!Files.exists(derivedPath) || !Files.isRegularFile(derivedPath)) {
            return true;
        }
        if (sourcePath == null || !Files.exists(sourcePath)) {
            return false;
        }
        return Files.getLastModifiedTime(derivedPath).toMillis() < Files.getLastModifiedTime(sourcePath).toMillis();
    }

    private Optional<Path> resolveVisibleImageSource(Path directory, String stem) {
        List<Path> candidates = List.of(
                directory.resolve(stem + ".bmp"),
                directory.resolve("color.bmp")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Path createTempSibling(Path targetPath) {
        String tempName = targetPath.getFileName().toString() + ".tmp";
        return targetPath.resolveSibling(tempName);
    }

    private void moveTempFile(Path tempPath, Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int mapCoordinate(int coordinate, int sourceLength, int targetLength) {
        if (targetLength <= 1 || sourceLength <= 1) {
            return 0;
        }
        double ratio = (double) coordinate / (double) sourceLength;
        int mapped = (int) Math.floor(ratio * targetLength);
        return Math.max(0, Math.min(mapped, targetLength - 1));
    }

    private LoadHyperspectralDataResponse buildFailureResponse(String message) {
        currentSession = null;
        return new LoadHyperspectralDataResponse(
                false,
                message,
                null,
                null,
                0,
                0,
                0,
                0,
                Collections.emptyList(),
                0,
                properties.getSampleVisualizationBands()
        );
    }

    private Optional<Path> findLatestHdr() throws IOException {
        Optional<Path> rootDirectory = resolvePath(properties.getHdrRootDir());
        if (!rootDirectory.isPresent() || !Files.isDirectory(rootDirectory.get())) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(rootDirectory.get())) {
            Optional<Path> latestDateDirectory = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("\\d{8}"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()));
            if (!latestDateDirectory.isPresent()) {
                return Optional.empty();
            }
            try (Stream<Path> hdrStream = Files.list(latestDateDirectory.get())) {
                return hdrStream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches("D.*\\.hdr"))
                        .max(Comparator.comparingLong(this::safeLastModifiedTime));
            }
        }
    }

    private Optional<Path> resolveHdrPathByName(String hdrName) throws IOException {
        Optional<Path> rootDirectory = resolvePath(properties.getHdrRootDir());
        if (!rootDirectory.isPresent() || !Files.isDirectory(rootDirectory.get())) {
            return Optional.empty();
        }
        String dateDir = extractDateDirectory(hdrName);
        if (!StringUtils.hasText(dateDir)) {
            return Optional.empty();
        }
        Path workingDirectory = rootDirectory.get().resolve(dateDir);
        if (!Files.isDirectory(workingDirectory)) {
            return Optional.empty();
        }
        Path hdrPath = workingDirectory.resolve(hdrName);
        return Files.exists(hdrPath) ? Optional.of(hdrPath) : Optional.empty();
    }

    private Optional<BufferedImage> readOriginalImage(Path imagePath) {
        if (imagePath == null || !Files.exists(imagePath)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            return Optional.ofNullable(ImageIO.read(inputStream));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private List<Integer> normalizeVisualizationBands(int totalBands) {
        if (properties.getSampleVisualizationBands() == null || properties.getSampleVisualizationBands().isEmpty()) {
            return defaultVisualizationBands(totalBands);
        }
        List<Integer> normalized = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int requested = properties.getSampleVisualizationBands()
                    .get(Math.min(index, properties.getSampleVisualizationBands().size() - 1));
            normalized.add(Math.max(0, Math.min(requested, totalBands - 1)));
        }
        return normalized;
    }

    private List<Integer> defaultVisualizationBands(int totalBands) {
        List<Integer> values = new ArrayList<>(3);
        values.add(Math.max(0, Math.min(0, totalBands - 1)));
        values.add(Math.max(0, Math.min(1, totalBands - 1)));
        values.add(Math.max(0, Math.min(2, totalBands - 1)));
        return values;
    }

    private BufferedImage createMatPreviewImage(String fileName, long fileSize, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int seed = computeSeed(fileName + ":" + fileSize);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pseudoValue = (int) ((seed + fileSize + x * 17L + y * 31L) % 255L);
                float hue = (float) ((pseudoValue + seed + x * 2 + y * 3) % 255) / 255.0f;
                float saturation = 0.45f + ((x + seed) % 25) / 100.0f;
                float brightness = 0.35f + ((y + pseudoValue) % 55) / 100.0f;
                image.setRGB(x, y, Color.HSBtoRGB(hue, Math.min(saturation, 0.92f), Math.min(brightness, 0.95f)));
            }
        }
        graphics.setColor(new Color(255, 255, 255, 180));
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawRoundRect(16, 16, width - 32, height - 32, 18, 18);
        graphics.setFont(new Font("Arial", Font.BOLD, 18));
        graphics.drawString("MAT PREVIEW", 18, 34);
        graphics.dispose();
        return image;
    }

    private Path ensurePreviewDirectory() throws IOException {
        Path previewDirectory = Paths.get(properties.getPreviewDir());
        Files.createDirectories(previewDirectory);
        return previewDirectory;
    }

    private String encodeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private int inferBands(String fileName, String targetVar) {
        if (StringUtils.hasText(fileName) && fileName.contains("320")) {
            return 320;
        }
        if (StringUtils.hasText(targetVar) && targetVar.toLowerCase(Locale.ROOT).contains("hyper")) {
            return Math.max(properties.getSampleTotalBands(), 320);
        }
        return properties.getSampleTotalBands();
    }

    private String resolveUploadedHdrName(String hdrName, MultipartFile hdrFile) {
        if (StringUtils.hasText(hdrName)) {
            return hdrName.trim();
        }
        if (hdrFile != null && StringUtils.hasText(hdrFile.getOriginalFilename())) {
            return hdrFile.getOriginalFilename().trim();
        }
        return "";
    }

    private Optional<Path> resolvePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(rawPath));
    }

    private long safeLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private String extractDateDirectory(String hdrName) {
        if (!StringUtils.hasText(hdrName) || !hdrName.startsWith("D_") || hdrName.length() < 10) {
            return "";
        }
        return hdrName.substring(2, 10);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
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

    private static final class DerivedArtifacts {

        private final Path matPath;
        private final Path originalBmpPath;
        private final Path pseudoPngPath;
        private final Path metadataPath;

        private DerivedArtifacts(Path matPath, Path originalBmpPath, Path pseudoPngPath, Path metadataPath) {
            this.matPath = matPath;
            this.originalBmpPath = originalBmpPath;
            this.pseudoPngPath = pseudoPngPath;
            this.metadataPath = metadataPath;
        }

        public Path getMatPath() {
            return matPath;
        }

        public Path getOriginalBmpPath() {
            return originalBmpPath;
        }

        public Path getPseudoPngPath() {
            return pseudoPngPath;
        }

        public Path getMetadataPath() {
            return metadataPath;
        }
    }

    /**
     * Current visualization session.
     */
    private static final class VisualizationSession {

        private final EnviCube cube;
        private final int pseudoWidth;
        private final int pseudoHeight;
        private final Path hdrPath;
        private final Path matPath;
        private final Path originalBmpPath;
        private final Path pseudoPngPath;

        private VisualizationSession(EnviCube cube, int pseudoWidth, int pseudoHeight, Path hdrPath, Path matPath,
                                     Path originalBmpPath, Path pseudoPngPath) {
            this.cube = cube;
            this.pseudoWidth = pseudoWidth;
            this.pseudoHeight = pseudoHeight;
            this.hdrPath = hdrPath;
            this.matPath = matPath;
            this.originalBmpPath = originalBmpPath;
            this.pseudoPngPath = pseudoPngPath;
        }

        public EnviCube getCube() {
            return cube;
        }

        public int getPseudoWidth() {
            return pseudoWidth;
        }

        public int getPseudoHeight() {
            return pseudoHeight;
        }

        public Path getHdrPath() {
            return hdrPath;
        }

        public Path getMatPath() {
            return matPath;
        }

        public Path getOriginalBmpPath() {
            return originalBmpPath;
        }

        public Path getPseudoPngPath() {
            return pseudoPngPath;
        }
    }
}
