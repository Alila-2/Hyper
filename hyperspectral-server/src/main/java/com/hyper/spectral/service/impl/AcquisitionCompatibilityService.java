package com.hyper.spectral.service.impl;

import com.hyper.spectral.config.acquisition.AcquisitionCompatibilityProperties;
import com.hyper.spectral.dto.acquisition.WaitForCaptureCompleteRequest;
import com.hyper.spectral.vo.acquisition.CheckCaptureCompleteResponse;
import com.hyper.spectral.vo.acquisition.PreloadHyperspectralDataResponse;
import com.hyper.spectral.vo.acquisition.WaitForCaptureCompleteResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 数据采集控制旧接口兼容实现。
 */
@Service
public class AcquisitionCompatibilityService {

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HDR_NAME_FORMATTER = DateTimeFormatter.ofPattern("'D_'yyyyMMdd_HHmmss'.hdr'");

    private final AcquisitionCompatibilityProperties properties;

    public AcquisitionCompatibilityService(AcquisitionCompatibilityProperties properties) {
        this.properties = properties;
    }

    public CheckCaptureCompleteResponse checkCaptureComplete() {
        try {
            Path workingDirectory = resolveWorkingDirectory();
            Path latestHdr = ensureLatestHdrExists(workingDirectory);
            return new CheckCaptureCompleteResponse(true, latestHdr.toString(), workingDirectory.toString(), "已获取拍摄前文件状态");
        } catch (IOException exception) {
            return new CheckCaptureCompleteResponse(false, null, null, "检查文件状态失败: " + exception.getMessage());
        }
    }

    public WaitForCaptureCompleteResponse waitForCaptureComplete(WaitForCaptureCompleteRequest request) {
        try {
            Path workingDirectory = resolveRequestDirectory(request.getLatestDir());
            Path latestBefore = resolvePath(request.getLatestHdrBefore()).orElse(null);

            if (isMockMode(workingDirectory)) {
                createMockHdr(workingDirectory, latestBefore);
            }

            Optional<Path> latestHdr = waitForNewHdr(latestBefore, workingDirectory);
            if (latestHdr.isPresent()) {
                return new WaitForCaptureCompleteResponse(true, latestHdr.get().toString(), workingDirectory.toString(), "拍摄完成");
            }

            return new WaitForCaptureCompleteResponse(false, null, workingDirectory.toString(), "等待拍摄完成超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new WaitForCaptureCompleteResponse(false, null, request.getLatestDir(), "等待拍摄完成被中断");
        } catch (IOException exception) {
            return new WaitForCaptureCompleteResponse(false, null, request.getLatestDir(), "等待拍摄完成失败: " + exception.getMessage());
        }
    }

    public PreloadHyperspectralDataResponse preloadHyperspectralData() {
        try {
            return new PreloadHyperspectralDataResponse(
                    true,
                    "预处理完成",
                    buildOriginalImageBase64(),
                    buildPseudoColorImageBase64(),
                    properties.getSampleOriginalWidth(),
                    properties.getSampleOriginalHeight(),
                    properties.getSamplePseudoWidth(),
                    properties.getSamplePseudoHeight(),
                    properties.getSampleTotalBands(),
                    properties.getSampleVisualizationBands()
            );
        } catch (IOException exception) {
            return new PreloadHyperspectralDataResponse(
                    false,
                    "预处理失败: " + exception.getMessage(),
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    properties.getSampleVisualizationBands()
            );
        }
    }

    private Path resolveWorkingDirectory() throws IOException {
        Optional<Path> realRoot = resolvePath(properties.getCaptureRootDir());
        if (realRoot.isPresent() && Files.isDirectory(realRoot.get())) {
            Optional<Path> latestDateDirectory = findLatestDateDirectory(realRoot.get());
            if (latestDateDirectory.isPresent()) {
                return latestDateDirectory.get();
            }
        }

        Path mockDirectory = Paths.get(properties.getMockRootDir(), LocalDate.now().format(DATE_DIR_FORMATTER));
        Files.createDirectories(mockDirectory);
        return mockDirectory;
    }

    private Path resolveRequestDirectory(String latestDir) throws IOException {
        Optional<Path> requested = resolvePath(latestDir);
        if (requested.isPresent() && Files.isDirectory(requested.get())) {
            return requested.get();
        }
        return resolveWorkingDirectory();
    }

    private Optional<Path> findLatestDateDirectory(Path rootDirectory) throws IOException {
        try (Stream<Path> stream = Files.list(rootDirectory)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("\\d{8}"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()));
        }
    }

    private Path ensureLatestHdrExists(Path directory) throws IOException {
        Optional<Path> latest = findLatestHdr(directory);
        if (latest.isPresent()) {
            return latest.get();
        }
        return createMockHdr(directory, null);
    }

    private Optional<Path> waitForNewHdr(Path latestBefore, Path directory) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + properties.getWaitTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            Optional<Path> latestNow = findLatestHdr(directory);
            if (latestNow.isPresent() && isNewerHdr(latestNow.get(), latestBefore)) {
                long sizeBefore = Files.size(latestNow.get());
                Thread.sleep(properties.getPollIntervalMs());
                long sizeAfter = Files.size(latestNow.get());
                if (sizeBefore > 0 && sizeBefore == sizeAfter) {
                    return latestNow;
                }
            }
            Thread.sleep(properties.getPollIntervalMs());
        }
        return Optional.empty();
    }

    private boolean isNewerHdr(Path latestNow, Path latestBefore) {
        if (latestBefore == null) {
            return true;
        }
        return !latestNow.toAbsolutePath().normalize().equals(latestBefore.toAbsolutePath().normalize());
    }

    private Optional<Path> findLatestHdr(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("D.*\\.hdr"))
                    .max(Comparator.comparingLong(this::safeLastModifiedTime));
        }
    }

    private long safeLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private Path createMockHdr(Path directory, Path latestBefore) throws IOException {
        Files.createDirectories(directory);
        LocalDateTime timestamp = LocalDateTime.now();
        Path candidate = directory.resolve(timestamp.format(HDR_NAME_FORMATTER));
        while ((latestBefore != null && candidate.equals(latestBefore)) || Files.exists(candidate)) {
            timestamp = timestamp.plusSeconds(1);
            candidate = directory.resolve(timestamp.format(HDR_NAME_FORMATTER));
        }
        Files.write(candidate, ("mock hdr placeholder for compatibility: " + candidate.getFileName()).getBytes(StandardCharsets.UTF_8));
        return candidate;
    }

    private boolean isMockMode(Path workingDirectory) {
        if (!StringUtils.hasText(properties.getCaptureRootDir())) {
            return true;
        }
        Path captureRoot = Paths.get(properties.getCaptureRootDir()).toAbsolutePath().normalize();
        return !workingDirectory.toAbsolutePath().normalize().startsWith(captureRoot);
    }

    private Optional<Path> resolvePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(rawPath));
    }

    private String buildOriginalImageBase64() throws IOException {
        return encodeImage(createOriginalImage());
    }

    private String buildPseudoColorImageBase64() throws IOException {
        return encodeImage(createPseudoColorImage());
    }

    private BufferedImage createOriginalImage() {
        int width = Math.max(properties.getSampleOriginalWidth() / 4, 320);
        int height = Math.max(properties.getSampleOriginalHeight() / 6, 240);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(22, 44, 68), width, height, new Color(115, 143, 176)));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(245, 247, 250, 180));
        graphics.setStroke(new BasicStroke(3f));
        for (int index = 1; index < 6; index++) {
            int x = width / 6 * index;
            graphics.drawLine(x, 24, x - 24, height - 24);
        }
        graphics.setColor(new Color(255, 255, 255, 200));
        graphics.drawString("VISIBLE FRAME", 20, 28);
        graphics.dispose();
        return image;
    }

    private BufferedImage createPseudoColorImage() {
        int width = Math.max(properties.getSamplePseudoWidth(), 256);
        int height = Math.max(properties.getSamplePseudoHeight(), 256);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int y = 0; y < height; y++) {
            float ratio = (float) y / (float) Math.max(height - 1, 1);
            Color lineColor = Color.getHSBColor(0.72f - ratio * 0.72f, 0.9f, 0.95f);
            graphics.setColor(lineColor);
            graphics.drawLine(0, y, width, y);
        }
        graphics.setColor(new Color(255, 255, 255, 170));
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(width / 5, height / 4, width / 3, height / 3);
        graphics.drawRect(width / 2, height / 3, width / 4, height / 4);
        graphics.drawString("PSEUDO COLOR", 16, 24);
        graphics.dispose();
        return image;
    }

    private String encodeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
