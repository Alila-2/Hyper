package com.hyper.spectral.support.envi;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读取 ENVI HDR + 数据文件，并提供基础渲染能力。
 */
public class EnviCubeReader {

    public EnviCube read(Path hdrPath) throws IOException {
        Map<String, String> header = parseHeader(hdrPath);
        int samples = parseRequiredInt(header, "samples");
        int lines = parseRequiredInt(header, "lines");
        int bands = parseRequiredInt(header, "bands");
        int dataType = parseRequiredInt(header, "data type");
        int headerOffset = parseOptionalInt(header, "header offset", 0);
        int byteOrder = parseOptionalInt(header, "byte order", 0);
        String interleave = header.getOrDefault("interleave", "bsq").trim().toLowerCase(Locale.ROOT);
        Path dataPath = resolveDataPath(hdrPath, header);
        float[] values = readCubeValues(dataPath, samples, lines, bands, dataType, interleave, headerOffset, byteOrder);
        return new EnviCube(samples, lines, bands, dataType, values);
    }

    private Map<String, String> parseHeader(Path hdrPath) throws IOException {
        String content = Files.readString(hdrPath, StandardCharsets.UTF_8);
        String normalized = content.replace("\r", "");
        String[] lines = normalized.split("\n");
        Map<String, String> header = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || "ENVI".equalsIgnoreCase(line)) {
                continue;
            }
            if (currentKey != null) {
                currentValue.append(' ').append(line);
                if (line.contains("}")) {
                    header.put(currentKey, cleanupValue(currentValue.toString()));
                    currentKey = null;
                    currentValue.setLength(0);
                }
                continue;
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex < 0) {
                continue;
            }
            String key = line.substring(0, equalsIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(equalsIndex + 1).trim();
            if (value.startsWith("{") && !value.contains("}")) {
                currentKey = key;
                currentValue.append(value);
            } else {
                header.put(key, cleanupValue(value));
            }
        }
        return header;
    }

    private String cleanupValue(String rawValue) {
        String value = rawValue.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private int parseRequiredInt(Map<String, String> header, String key) throws IOException {
        String value = header.get(key);
        if (value == null) {
            throw new IOException("HDR 缺少关键字段: " + key);
        }
        return Integer.parseInt(value.trim());
    }

    private int parseOptionalInt(Map<String, String> header, String key, int defaultValue) {
        String value = header.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private Path resolveDataPath(Path hdrPath, Map<String, String> header) throws IOException {
        if (header.containsKey("data file")) {
            Path candidate = hdrPath.getParent().resolve(header.get("data file")).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        String baseName = stripExtension(hdrPath.getFileName().toString());
        List<Path> candidates = Arrays.asList(
                hdrPath.getParent().resolve(baseName),
                hdrPath.getParent().resolve(baseName + ".img"),
                hdrPath.getParent().resolve(baseName + ".raw"),
                hdrPath.getParent().resolve(baseName + ".dat"),
                hdrPath.getParent().resolve(baseName + ".bin")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("未找到与 HDR 对应的数据文件: " + hdrPath);
    }

    private float[] readCubeValues(Path dataPath, int samples, int lines, int bands, int dataType,
                                   String interleave, int headerOffset, int byteOrder) throws IOException {
        byte[] bytes = Files.readAllBytes(dataPath);
        int bytesPerSample = bytesPerSample(dataType);
        long requiredLength = headerOffset + (long) samples * lines * bands * bytesPerSample;
        if (bytes.length < requiredLength) {
            throw new IOException("ENVI 数据文件长度不足: " + dataPath);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(byteOrder == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        buffer.position(headerOffset);

        float[] values = new float[samples * lines * bands];
        String normalizedInterleave = interleave.toLowerCase(Locale.ROOT);
        switch (normalizedInterleave) {
            case "bip":
                for (int y = 0; y < lines; y++) {
                    for (int x = 0; x < samples; x++) {
                        for (int b = 0; b < bands; b++) {
                            values[indexOf(samples, bands, x, y, b)] = (float) readValue(buffer, dataType);
                        }
                    }
                }
                break;
            case "bil":
                for (int y = 0; y < lines; y++) {
                    for (int b = 0; b < bands; b++) {
                        for (int x = 0; x < samples; x++) {
                            values[indexOf(samples, bands, x, y, b)] = (float) readValue(buffer, dataType);
                        }
                    }
                }
                break;
            case "bsq":
                for (int b = 0; b < bands; b++) {
                    for (int y = 0; y < lines; y++) {
                        for (int x = 0; x < samples; x++) {
                            values[indexOf(samples, bands, x, y, b)] = (float) readValue(buffer, dataType);
                        }
                    }
                }
                break;
            default:
                throw new IOException("暂不支持的 ENVI interleave: " + interleave);
        }
        return values;
    }

    private int bytesPerSample(int dataType) throws IOException {
        switch (dataType) {
            case 1:
                return 1;
            case 2:
            case 12:
                return 2;
            case 3:
            case 4:
            case 13:
                return 4;
            case 5:
                return 8;
            default:
                throw new IOException("暂不支持的 ENVI data type: " + dataType);
        }
    }

    private double readValue(ByteBuffer buffer, int dataType) throws IOException {
        switch (dataType) {
            case 1:
                return Byte.toUnsignedInt(buffer.get());
            case 2:
                return buffer.getShort();
            case 3:
                return buffer.getInt();
            case 4:
                return buffer.getFloat();
            case 5:
                return buffer.getDouble();
            case 12:
                return Short.toUnsignedInt(buffer.getShort());
            case 13:
                return Integer.toUnsignedLong(buffer.getInt());
            default:
                throw new IOException("暂不支持的 ENVI data type: " + dataType);
        }
    }

    private int indexOf(int samples, int bands, int x, int y, int band) {
        return (y * samples + x) * bands + band;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 已加载的高光谱立方体。
     */
    public static class EnviCube {

        private final int samples;
        private final int lines;
        private final int bands;
        private final int dataType;
        private final float[] values;

        public EnviCube(int samples, int lines, int bands, int dataType, float[] values) {
            this.samples = samples;
            this.lines = lines;
            this.bands = bands;
            this.dataType = dataType;
            this.values = values;
        }

        public int getSamples() {
            return samples;
        }

        public int getLines() {
            return lines;
        }

        public int getBands() {
            return bands;
        }

        public int getDataType() {
            return dataType;
        }

        public float getValue(int x, int y, int band) {
            return values[(y * samples + x) * bands + band];
        }

        public List<Double> spectrumAt(int x, int y) {
            List<Double> spectrum = new ArrayList<>(bands);
            for (int band = 0; band < bands; band++) {
                spectrum.add((double) getValue(x, y, band));
            }
            return spectrum;
        }

        public BufferedImage renderMinMaxComposite(List<Integer> requestedBands) {
            int[] selected = normalizeBands(requestedBands);
            float[] minValues = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] maxValues = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

            for (int y = 0; y < lines; y++) {
                for (int x = 0; x < samples; x++) {
                    for (int index = 0; index < 3; index++) {
                        float value = getValue(x, y, selected[index]);
                        if (value < minValues[index]) {
                            minValues[index] = value;
                        }
                        if (value > maxValues[index]) {
                            maxValues[index] = value;
                        }
                    }
                }
            }

            BufferedImage image = new BufferedImage(samples, lines, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < lines; y++) {
                for (int x = 0; x < samples; x++) {
                    int red = scaleMinMax(getValue(x, y, selected[0]), minValues[0], maxValues[0]);
                    int green = scaleMinMax(getValue(x, y, selected[1]), minValues[1], maxValues[1]);
                    int blue = scaleMinMax(getValue(x, y, selected[2]), minValues[2], maxValues[2]);
                    image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
            return image;
        }

        public BufferedImage renderDirectComposite(List<Integer> requestedBands) {
            int[] selected = normalizeBands(requestedBands);
            double divisor = isUInt16Family() ? 65535.0d : estimateGlobalMax();
            if (divisor <= 0.0d) {
                divisor = 1.0d;
            }

            BufferedImage image = new BufferedImage(samples, lines, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < lines; y++) {
                for (int x = 0; x < samples; x++) {
                    int red = scaleDirect(getValue(x, y, selected[0]), divisor);
                    int green = scaleDirect(getValue(x, y, selected[1]), divisor);
                    int blue = scaleDirect(getValue(x, y, selected[2]), divisor);
                    image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
            return image;
        }

        private int[] normalizeBands(List<Integer> requestedBands) {
            List<Integer> safeBands = requestedBands == null || requestedBands.isEmpty()
                    ? Arrays.asList(0, 1, 2)
                    : requestedBands;
            int[] selected = new int[3];
            for (int index = 0; index < 3; index++) {
                int requested = safeBands.get(Math.min(index, safeBands.size() - 1));
                selected[index] = Math.max(0, Math.min(requested, bands - 1));
            }
            return selected;
        }

        private int scaleMinMax(float value, float min, float max) {
            if (max - min < 1e-8f) {
                return 0;
            }
            double normalized = (value - min) / (max - min);
            return clampColor(normalized * 255.0d);
        }

        private int scaleDirect(float value, double divisor) {
            return clampColor((value / divisor) * 255.0d);
        }

        private int clampColor(double value) {
            return (int) Math.max(0, Math.min(255, Math.round(value)));
        }

        private boolean isUInt16Family() {
            return dataType == 12 || dataType == 2;
        }

        private double estimateGlobalMax() {
            double max = 0.0d;
            for (float value : values) {
                if (value > max) {
                    max = value;
                }
            }
            return max;
        }
    }
}
