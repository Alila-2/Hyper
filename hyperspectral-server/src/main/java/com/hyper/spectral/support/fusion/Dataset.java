package com.hyper.spectral.support.fusion;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dataset is the only format-facing layer in the pipeline.
 *
 * Dependencies:
 *   - OpenCV Java bindings for image loading and basic image conversion.
 *
 * Current MAT support:
 *   - Writes MATLAB v5 dense numeric arrays.
 *   - Reads MATLAB v5 dense numeric arrays written by this class and common SciPy savemat output.
 *   - Detects MATLAB v7.3/HDF5 files and fails with a clear message unless an HDF5 adapter is added.
 *
 * The downstream modules should only use ImagePair and MatData, not raw ENVI/BMP details.
 */
public class Dataset {
    public static final String LRHSI = "LRHSI";
    public static final String HRMSI = "HRMSI";
    public static final String WAVELENGTHS = "wavelengths";

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);
        if (a.has("--help") || args.length == 0) {
            printUsage();
            return;
        }
        loadOpenCv(a.get("--opencv-lib", null));

        ImagePair pair = fromRaw(
                Path.of(a.required("--hdr")),
                Path.of(a.required("--img")),
                Path.of(a.required("--msi"))
        );
        Path out = Path.of(a.get("--out", "preprocessed.mat"));
        MatIO.write(out, MatData.fromImagePair(pair), MatVersion.fromString(a.get("--mat-version", "v5")));
        System.out.println("Saved preprocessed data: " + out.toAbsolutePath());
        System.out.println(pair.summary());
    }

    public static ImagePair fromRaw(Path hdrPath, Path imgPath, Path msiPath) throws IOException {
        EnviHeader header = EnviHeader.read(hdrPath);
        float[][][] lrhsi = readEnviImage(imgPath, header);
        float[][][] hrmsi = readColorImage(msiPath);
        return new ImagePair(lrhsi, hrmsi, header.wavelengths, header);
    }

    public static ImagePair fromRaw(Path hdrPath, Path imgPath, Path msiPath, int targetLrSize)
            throws IOException {
        EnviHeader header = EnviHeader.read(hdrPath);
        int stagingLrSize = Math.max(8, targetLrSize) + 10;
        float[][][] lrhsi = readEnviImageResized(imgPath, header, stagingLrSize, stagingLrSize);
        float[][][] hrmsi = readColorImage(msiPath, stagingLrSize * 2, stagingLrSize * 2);
        return new ImagePair(lrhsi, hrmsi, header.wavelengths, header);
    }

    public static ImagePair fromMat(Path matPath) throws IOException {
        MatData data = MatIO.read(matPath);
        float[][][] lrhsi = data.requiredCube(LRHSI);
        float[][][] hrmsi = data.requiredCube(HRMSI);
        double[] wavelengths = data.vectors.getOrDefault(WAVELENGTHS, new double[0]);
        return new ImagePair(lrhsi, hrmsi, wavelengths, null);
    }

    public static float[][] grayFromLrhsi(float[][][] lrhsi) {
        int h = lrhsi.length;
        int w = lrhsi[0].length;
        int b = lrhsi[0][0].length;
        float[][] out = new float[h][w];
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0.0;
                for (int k = 0; k < b; k++) {
                    s += lrhsi[y][x][k];
                }
                float v = (float) (s / b);
                out[y][x] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        normalizeInPlace(out, min, max);
        return out;
    }

    public static float[][] grayFromHrmsi(float[][][] hrmsi) {
        int h = hrmsi.length;
        int w = hrmsi[0].length;
        float[][] out = new float[h][w];
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] p = hrmsi[y][x];
                float v = p.length >= 3 ? 0.299f * p[0] + 0.587f * p[1] + 0.114f * p[2] : p[0];
                out[y][x] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        normalizeInPlace(out, min, max);
        return out;
    }

    public static Mat grayToMat8(float[][] gray) {
        int h = gray.length;
        int w = gray[0].length;
        Mat m = new Mat(h, w, CvType.CV_8UC1);
        byte[] row = new byte[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = Math.round(gray[y][x]);
                row[x] = (byte) Math.max(0, Math.min(255, v));
            }
            m.put(y, 0, row);
        }
        return m;
    }

    public static float[][][] matToCubeRgb(Mat rgb) {
        int h = rgb.rows();
        int w = rgb.cols();
        int c = rgb.channels();
        float[][][] out = new float[h][w][c];
        byte[] row = new byte[w * c];
        for (int y = 0; y < h; y++) {
            rgb.get(y, 0, row);
            int idx = 0;
            for (int x = 0; x < w; x++) {
                for (int k = 0; k < c; k++) {
                    out[y][x][k] = row[idx++] & 0xff;
                }
            }
        }
        return out;
    }

    public static Mat cubeRgbToMat(float[][][] cube) {
        int h = cube.length;
        int w = cube[0].length;
        int c = cube[0][0].length;
        Mat m = new Mat(h, w, c == 1 ? CvType.CV_8UC1 : CvType.CV_8UC3);
        byte[] row = new byte[w * c];
        for (int y = 0; y < h; y++) {
            int idx = 0;
            for (int x = 0; x < w; x++) {
                for (int k = 0; k < c; k++) {
                    int v = Math.round(cube[y][x][k]);
                    row[idx++] = (byte) Math.max(0, Math.min(255, v));
                }
            }
            m.put(y, 0, row);
        }
        return m;
    }

    public static void saveRgbPng(Path path, float[][][] rgb) throws IOException {
        Mat mat = cubeRgbToMat(rgb);
        Mat bgr = new Mat();
        if (mat.channels() == 3) {
            Imgproc.cvtColor(mat, bgr, Imgproc.COLOR_RGB2BGR);
        } else {
            bgr = mat;
        }
        MatOfByte encoded = new MatOfByte();
        if (!Imgcodecs.imencode(".png", bgr, encoded)) {
            throw new IOException("OpenCV failed to encode PNG: " + path);
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.write(path, encoded.toArray());
    }

    public static float[][][] trueColor(float[][][] hrhsi, int rIdx, int gIdx, int bIdx, float[][][] colorReferenceOrNull) {
        int h = hrhsi.length;
        int w = hrhsi[0].length;
        int bands = hrhsi[0][0].length;
        int[] idx = {
                Math.max(0, Math.min(bands - 1, rIdx)),
                Math.max(0, Math.min(bands - 1, gIdx)),
                Math.max(0, Math.min(bands - 1, bIdx))
        };
        float[][][] rgb = new float[h][w][3];
        for (int c = 0; c < 3; c++) {
            float[] vals = new float[h * w];
            int n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    vals[n++] = hrhsi[y][x][idx[c]];
                }
            }
            Arrays.sort(vals);
            float lo = vals[(int) Math.floor(0.02 * (vals.length - 1))];
            float hi = vals[(int) Math.floor(0.98 * (vals.length - 1))];
            if (hi <= lo) hi = lo + 1e-6f;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float v = (hrhsi[y][x][idx[c]] - lo) / (hi - lo) * 255f;
                    rgb[y][x][c] = Math.max(0, Math.min(255, v));
                }
            }
        }
        if (colorReferenceOrNull != null) {
            histogramMatchRgb(rgb, colorReferenceOrNull);
        }
        return rgb;
    }

    public static void histogramMatchRgb(float[][][] target, float[][][] reference) {
        int h = target.length;
        int w = target[0].length;
        int rh = reference.length;
        int rw = reference[0].length;
        for (int c = 0; c < 3; c++) {
            int[] histT = new int[256];
            int[] histR = new int[256];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) histT[clamp8(target[y][x][c])]++;
            }
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) histR[clamp8(reference[y][x][Math.min(c, reference[y][x].length - 1)])]++;
            }
            double[] cdfT = cdf(histT, h * w);
            double[] cdfR = cdf(histR, rh * rw);
            int[] map = new int[256];
            int j = 0;
            for (int i = 0; i < 256; i++) {
                while (j < 255 && cdfR[j] < cdfT[i]) j++;
                map[i] = j;
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) target[y][x][c] = map[clamp8(target[y][x][c])];
            }
        }
    }

    private static double[] cdf(int[] hist, int total) {
        double[] out = new double[hist.length];
        double s = 0.0;
        for (int i = 0; i < hist.length; i++) {
            s += hist[i];
            out[i] = s / Math.max(1, total);
        }
        return out;
    }

    private static int clamp8(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static void normalizeInPlace(float[][] data, float min, float max) {
        float den = max - min;
        if (den <= 0) den = 1f;
        for (int y = 0; y < data.length; y++) {
            for (int x = 0; x < data[0].length; x++) {
                data[y][x] = Math.max(0, Math.min(255, (data[y][x] - min) / den * 255f));
            }
        }
    }

    private static float[][][] readColorImage(Path path) throws IOException {
        return readColorImage(path, 0, 0);
    }

    private static float[][][] readColorImage(Path path, int targetHeight, int targetWidth) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Mat img = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_UNCHANGED);
        if (img.empty()) {
            throw new IOException("Cannot read image: " + path);
        }
        Mat rgb = new Mat();
        if (img.channels() == 3) {
            Imgproc.cvtColor(img, rgb, Imgproc.COLOR_BGR2RGB);
        } else if (img.channels() == 4) {
            Imgproc.cvtColor(img, rgb, Imgproc.COLOR_BGRA2RGB);
        } else {
            Imgproc.cvtColor(img, rgb, Imgproc.COLOR_GRAY2RGB);
        }
        if (targetHeight > 0 && targetWidth > 0
                && (rgb.rows() != targetHeight || rgb.cols() != targetWidth)) {
            Mat resized = new Mat();
            Imgproc.resize(rgb, resized, new Size(targetWidth, targetHeight), 0, 0, Imgproc.INTER_AREA);
            rgb = resized;
        }
        return matToCubeRgb(rgb);
    }

    private static float[][][] readEnviImage(Path imgPath, EnviHeader h) throws IOException {
        int bytesPer = h.bytesPerSample();
        long expected = (long) h.samples * h.lines * h.bands * bytesPer + h.headerOffset;
        if (Files.size(imgPath) < expected) {
            throw new IOException("ENVI image is smaller than expected: " + imgPath);
        }
        float[][][] out = new float[h.lines][h.samples][h.bands];
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(imgPath.toFile())))) {
            skipFully(in, h.headerOffset);
            if ("bsq".equals(h.interleave)) {
                for (int b = 0; b < h.bands; b++) {
                    for (int y = 0; y < h.lines; y++) {
                        for (int x = 0; x < h.samples; x++) out[y][x][b] = readSample(in, h);
                    }
                }
            } else if ("bil".equals(h.interleave)) {
                for (int y = 0; y < h.lines; y++) {
                    for (int b = 0; b < h.bands; b++) {
                        for (int x = 0; x < h.samples; x++) out[y][x][b] = readSample(in, h);
                    }
                }
            } else if ("bip".equals(h.interleave)) {
                for (int y = 0; y < h.lines; y++) {
                    for (int x = 0; x < h.samples; x++) {
                        for (int b = 0; b < h.bands; b++) out[y][x][b] = readSample(in, h);
                    }
                }
            } else {
                throw new IOException("Unsupported ENVI interleave: " + h.interleave);
            }
        }
        return out;
    }

    private static float[][][] readEnviImageResized(Path imgPath, EnviHeader h, int targetH, int targetW)
            throws IOException {
        int bytesPer = h.bytesPerSample();
        long expected = (long) h.samples * h.lines * h.bands * bytesPer + h.headerOffset;
        if (Files.size(imgPath) < expected) {
            throw new IOException("ENVI image is smaller than expected: " + imgPath);
        }

        float[][][] out = new float[targetH][targetW][h.bands];
        int[][] counts = new int[targetH][targetW];
        for (int y = 0; y < h.lines; y++) {
            int targetY = Math.min(targetH - 1, y * targetH / h.lines);
            for (int x = 0; x < h.samples; x++) {
                int targetX = Math.min(targetW - 1, x * targetW / h.samples);
                counts[targetY][targetX]++;
            }
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(imgPath.toFile())))) {
            skipFully(in, h.headerOffset);
            if ("bsq".equals(h.interleave)) {
                for (int b = 0; b < h.bands; b++) {
                    for (int y = 0; y < h.lines; y++) {
                        int targetY = Math.min(targetH - 1, y * targetH / h.lines);
                        for (int x = 0; x < h.samples; x++) {
                            int targetX = Math.min(targetW - 1, x * targetW / h.samples);
                            out[targetY][targetX][b] += readSample(in, h);
                        }
                    }
                }
            } else if ("bil".equals(h.interleave)) {
                for (int y = 0; y < h.lines; y++) {
                    int targetY = Math.min(targetH - 1, y * targetH / h.lines);
                    for (int b = 0; b < h.bands; b++) {
                        for (int x = 0; x < h.samples; x++) {
                            int targetX = Math.min(targetW - 1, x * targetW / h.samples);
                            out[targetY][targetX][b] += readSample(in, h);
                        }
                    }
                }
            } else if ("bip".equals(h.interleave)) {
                for (int y = 0; y < h.lines; y++) {
                    int targetY = Math.min(targetH - 1, y * targetH / h.lines);
                    for (int x = 0; x < h.samples; x++) {
                        int targetX = Math.min(targetW - 1, x * targetW / h.samples);
                        for (int b = 0; b < h.bands; b++) {
                            out[targetY][targetX][b] += readSample(in, h);
                        }
                    }
                }
            } else {
                throw new IOException("Unsupported ENVI interleave: " + h.interleave);
            }
        }

        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int count = counts[y][x];
                if (count > 0) {
                    for (int b = 0; b < h.bands; b++) {
                        out[y][x][b] /= count;
                    }
                    continue;
                }

                int sourceY = Math.min(h.lines - 1, y * h.lines / targetH);
                int sourceX = Math.min(h.samples - 1, x * h.samples / targetW);
                int mappedY = Math.min(targetH - 1, sourceY * targetH / h.lines);
                int mappedX = Math.min(targetW - 1, sourceX * targetW / h.samples);
                System.arraycopy(out[mappedY][mappedX], 0, out[y][x], 0, h.bands);
            }
        }
        return out;
    }

    private static float readSample(DataInputStream in, EnviHeader h) throws IOException {
        byte[] buf = new byte[h.bytesPerSample()];
        in.readFully(buf);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(h.byteOrder == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        switch (h.dataType) {
            case 1:
                return (float) (buf[0] & 0xff);
            case 2:
                return bb.getShort();
            case 3:
                return bb.getInt();
            case 4:
                return bb.getFloat();
            case 5:
                return (float) bb.getDouble();
            case 12:
                return (float) (bb.getShort() & 0xffff);
            default:
                throw new IOException("Unsupported ENVI data type: " + h.dataType);
        }
    }

    private static void skipFully(DataInputStream in, int bytes) throws IOException {
        int left = bytes;
        while (left > 0) {
            int skipped = in.skipBytes(left);
            if (skipped <= 0) throw new EOFException("Cannot skip ENVI header offset.");
            left -= skipped;
        }
    }

    public static void loadOpenCv(String explicitLibraryPath) {
        if (explicitLibraryPath != null && !explicitLibraryPath.isEmpty()) {
            System.load(explicitLibraryPath);
            return;
        }
        try {
            Class<?> loader = Class.forName("nu.pattern.OpenCV");
            loader.getMethod("loadLocally").invoke(null);
            return;
        } catch (Throwable ignored) {
            // Try the loader exposed by newer OpenPnP OpenCV artifacts.
        }
        try {
            Class<?> loader = Class.forName("org.openpnp.opencv.OpenCV");
            loader.getMethod("loadLocally").invoke(null);
            return;
        } catch (Throwable ignored) {
            // Fall through to the standard OpenCV library name.
        }
        try {
            System.loadLibrary("opencv_java");
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("OpenCV native library not loaded. Pass --opencv-lib <opencv_java*.dll> or add OpenCV to java.library.path.", e);
        }
    }

    private static void printUsage() {
        System.out.println("Dataset usage:");
        System.out.println("  java Dataset --hdr D_20251203_110343.hdr --img D_20251203_110343.img --msi color.bmp --out preprocessed.mat");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --mat-version v5|v73");
        System.out.println("  --opencv-lib path/to/opencv_java*.dll");
    }

    public static class ImagePair {
        public final float[][][] lrhsi;
        public final float[][][] hrmsi;
        public final double[] wavelengths;
        public final EnviHeader sourceHeader;

        public ImagePair(float[][][] lrhsi, float[][][] hrmsi, double[] wavelengths, EnviHeader sourceHeader) {
            this.lrhsi = lrhsi;
            this.hrmsi = hrmsi;
            this.wavelengths = wavelengths == null ? new double[0] : wavelengths;
            this.sourceHeader = sourceHeader;
        }

        public int scale() {
            return Math.round((float) hrmsi.length / lrhsi.length);
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "LRHSI=%dx%dx%d, HRMSI=%dx%dx%d, scale=%d, wavelengths=%d",
                    lrhsi.length, lrhsi[0].length, lrhsi[0][0].length,
                    hrmsi.length, hrmsi[0].length, hrmsi[0][0].length,
                    scale(), wavelengths.length);
        }
    }

    public static class EnviHeader {
        public int samples;
        public int lines;
        public int bands;
        public int headerOffset;
        public int dataType;
        public int byteOrder;
        public String interleave;
        public double[] wavelengths;

        public static EnviHeader read(Path path) throws IOException {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            EnviHeader h = new EnviHeader();
            h.samples = intField(text, "samples");
            h.lines = intField(text, "lines");
            h.bands = intField(text, "bands");
            h.headerOffset = intField(text, "header offset", 0);
            h.dataType = intField(text, "data type");
            h.byteOrder = intField(text, "byte order", 0);
            h.interleave = stringField(text, "interleave", "bsq").toLowerCase(Locale.ROOT);
            h.wavelengths = vectorField(text, "wavelength");
            return h;
        }

        public int bytesPerSample() throws IOException {
            switch (dataType) {
                case 1:
                    return 1;
                case 2:
                case 12:
                    return 2;
                case 3:
                case 4:
                    return 4;
                case 5:
                    return 8;
                default:
                    throw new IOException("Unsupported ENVI data type: " + dataType);
            }
        }

        private static int intField(String text, String key) throws IOException {
            return intField(text, key, Integer.MIN_VALUE);
        }

        private static int intField(String text, String key, int fallback) throws IOException {
            Matcher m = Pattern.compile("(?im)^\\s*" + Pattern.quote(key) + "\\s*=\\s*([^\\r\\n]+)").matcher(text);
            if (!m.find()) {
                if (fallback != Integer.MIN_VALUE) return fallback;
                throw new IOException("Missing ENVI header field: " + key);
            }
            return Integer.parseInt(m.group(1).trim());
        }

        private static String stringField(String text, String key, String fallback) {
            Matcher m = Pattern.compile("(?im)^\\s*" + Pattern.quote(key) + "\\s*=\\s*([^\\r\\n]+)").matcher(text);
            return m.find() ? m.group(1).replace("{", "").replace("}", "").trim() : fallback;
        }

        private static double[] vectorField(String text, String key) {
            Matcher m = Pattern.compile("(?is)" + Pattern.quote(key) + "\\s*=\\s*\\{(.*?)\\}").matcher(text);
            if (!m.find()) return new double[0];
            String[] parts = m.group(1).replace("\r", "").replace("\n", "").split(",");
            List<Double> vals = new ArrayList<>();
            for (String p : parts) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    try {
                        vals.add(Double.parseDouble(s));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            double[] out = new double[vals.size()];
            for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
            return out;
        }
    }

    public enum MatVersion {
        V5, V73;

        public static MatVersion fromString(String s) {
            String v = s.toLowerCase(Locale.ROOT).replace(".", "");
            if (v.equals("v73") || v.equals("73") || v.equals("hdf5")) return V73;
            return V5;
        }
    }

    public static class MatData {
        public final Map<String, float[][][]> cubes = new HashMap<>();
        public final Map<String, double[]> vectors = new HashMap<>();

        public static MatData fromImagePair(ImagePair pair) {
            MatData d = new MatData();
            d.cubes.put(LRHSI, pair.lrhsi);
            d.cubes.put(HRMSI, pair.hrmsi);
            if (pair.wavelengths.length > 0) d.vectors.put(WAVELENGTHS, pair.wavelengths);
            return d;
        }

        public float[][][] requiredCube(String name) {
            float[][][] v = cubes.get(name);
            if (v == null) throw new IllegalArgumentException("Missing MAT cube variable: " + name);
            return v;
        }
    }

    public static class MatIO {
        public static void write(Path path, MatData data, MatVersion version) throws IOException {
            if (version == MatVersion.V73) {
                Hdf5Mat73.write(path, data);
            } else {
                MatV5.write(path, data);
            }
        }

        public static MatData read(Path path) throws IOException {
            if (isMat73(path)) {
                return Hdf5Mat73.read(path);
            }
            return MatV5.read(path);
        }

        private static boolean isMat73(Path path) throws IOException {
            byte[] header = new byte[(int) Math.min(128, Files.size(path))];
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                int n = in.read(header);
                String s = new String(header, 0, Math.max(0, n), StandardCharsets.US_ASCII);
                return s.contains("MATLAB 7.3") || s.contains("HDF5");
            }
        }
    }

    public static class Hdf5Mat73 {
        public static void write(Path path, MatData data) throws IOException {
            throw new IOException("MATLAB v7.3 requires an HDF5 Java adapter. Add an HDF5 backend here; v7.3 is detected by Dataset.MatIO.");
        }

        public static MatData read(Path path) throws IOException {
            throw new IOException("MATLAB v7.3/HDF5 file detected. Add an HDF5 Java adapter here to read: " + path);
        }
    }

    public static class MatV5 {
        private static final int STREAM_BUFFER_SIZE = 64 * 1024;
        private static final int MI_INT8 = 1;
        private static final int MI_UINT32 = 6;
        private static final int MI_INT32 = 5;
        private static final int MI_SINGLE = 7;
        private static final int MI_DOUBLE = 9;
        private static final int MI_MATRIX = 14;
        private static final int MX_SINGLE_CLASS = 7;
        private static final int MX_DOUBLE_CLASS = 6;

        public static void write(Path path, MatData data) throws IOException {
            Path target = path.toAbsolutePath();
            Path parent = target.getParent();
            if (parent == null) {
                throw new IOException("MAT output path has no parent directory: " + path);
            }
            Path temporary = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
            boolean completed = false;
            try {
                try (BufferedOutputStream out = new BufferedOutputStream(
                        new FileOutputStream(temporary.toFile()), STREAM_BUFFER_SIZE)) {
                    writeHeader(out);
                    for (Map.Entry<String, float[][][]> e : data.cubes.entrySet()) {
                        writeCube(out, e.getKey(), e.getValue());
                    }
                    for (Map.Entry<String, double[]> e : data.vectors.entrySet()) {
                        writeVector(out, e.getKey(), e.getValue());
                    }
                }
                replaceAtomically(temporary, target);
                completed = true;
            } finally {
                if (!completed) {
                    deleteQuietly(temporary);
                }
            }
        }

        private static void replaceAtomically(Path source, Path target) throws IOException {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private static void deleteQuietly(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve the original write failure; a stale .tmp file is safer than a corrupt final MAT file.
            }
        }

        public static MatData read(Path path) throws IOException {
            MatData data = new MatData();
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                raf.seek(128);
                while (raf.getFilePointer() < raf.length()) {
                    int type = readIntLE(raf);
                    int size = readIntLE(raf);
                    long end = raf.getFilePointer() + size;
                    if (type != MI_MATRIX) {
                        raf.seek(end + pad(size));
                        continue;
                    }
                    MatrixVar v = readMatrix(raf);
                    if (v.dims.length == 2 && (v.dims[0] == 1 || v.dims[1] == 1)) {
                        data.vectors.put(v.name, v.toVector());
                    } else {
                        data.cubes.put(v.name, v.toCube());
                    }
                    raf.seek(end + pad(size));
                }
            }
            return data;
        }

        private static MatrixVar readMatrix(RandomAccessFile raf) throws IOException {
            Element flags = readElement(raf);
            int cls = ByteBuffer.wrap(flags.payload).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xff;
            Element dimsEl = readElement(raf);
            int[] dims = new int[dimsEl.payload.length / 4];
            ByteBuffer db = ByteBuffer.wrap(dimsEl.payload).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < dims.length; i++) dims[i] = db.getInt();
            Element nameEl = readElement(raf);
            String name = new String(nameEl.payload, StandardCharsets.US_ASCII).trim();
            Element realEl = readElement(raf);
            int total = 1;
            for (int d : dims) total *= d;
            double[] values = new double[total];
            ByteBuffer rb = ByteBuffer.wrap(realEl.payload).order(ByteOrder.LITTLE_ENDIAN);
            if (realEl.type == MI_SINGLE || cls == MX_SINGLE_CLASS) {
                for (int i = 0; i < total; i++) values[i] = rb.getFloat();
            } else {
                for (int i = 0; i < total; i++) values[i] = rb.getDouble();
            }
            return new MatrixVar(name, dims, values);
        }

        private static Element readElement(RandomAccessFile raf) throws IOException {
            int tag = readIntLE(raf);
            int type;
            int size;
            boolean small = (tag >>> 16) != 0;
            if (small) {
                type = tag & 0xffff;
                size = tag >>> 16;
                byte[] payload = new byte[size];
                raf.readFully(payload);
                raf.skipBytes(4 - size);
                return new Element(type, payload);
            }
            type = tag;
            size = readIntLE(raf);
            byte[] payload = new byte[size];
            raf.readFully(payload);
            raf.skipBytes(pad(size));
            return new Element(type, payload);
        }

        private static void writeHeader(BufferedOutputStream out) throws IOException {
            byte[] text = new byte[116];
            byte[] msg = "MATLAB 5.0 MAT-file, Created by image-fusion-registration Java pipeline".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(msg, 0, text, 0, Math.min(text.length, msg.length));
            out.write(text);
            writeZeros(out, 8);
            writeShortLE(out, 0x0100);
            out.write('I');
            out.write('M');
        }

        private static void writeCube(BufferedOutputStream out, String name, float[][][] cube) throws IOException {
            int[] dims = cubeDimensions(name, cube);
            int h = dims[0];
            int w = dims[1];
            int b = dims[2];
            byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
            long realSize = checkedProduct(name, h, w, b, Float.BYTES);
            int bodySize = checkedMatrixBodySize(name, dims.length, nameBytes.length, realSize);

            writeIntLE(out, MI_MATRIX);
            writeIntLE(out, bodySize);
            writeArrayFlags(out, MX_SINGLE_CLASS);
            writeDims(out, dims);
            writeName(out, nameBytes);
            writeIntLE(out, MI_SINGLE);
            writeIntLE(out, (int) realSize);

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int offset = 0;
            for (int k = 0; k < b; k++) {
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int bits = Float.floatToIntBits(cube[y][x][k]);
                        buffer[offset++] = (byte) bits;
                        buffer[offset++] = (byte) (bits >>> 8);
                        buffer[offset++] = (byte) (bits >>> 16);
                        buffer[offset++] = (byte) (bits >>> 24);
                        if (offset == buffer.length) {
                            out.write(buffer);
                            offset = 0;
                        }
                    }
                }
            }
            if (offset > 0) {
                out.write(buffer, 0, offset);
            }
            writeZeros(out, pad((int) realSize));
        }

        private static void writeVector(BufferedOutputStream out, String name, double[] v) throws IOException {
            int[] dims = new int[]{1, v.length};
            byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
            long realSize = checkedProduct(name, v.length, Double.BYTES);
            int bodySize = checkedMatrixBodySize(name, dims.length, nameBytes.length, realSize);

            writeIntLE(out, MI_MATRIX);
            writeIntLE(out, bodySize);
            writeArrayFlags(out, MX_DOUBLE_CLASS);
            writeDims(out, dims);
            writeName(out, nameBytes);
            writeIntLE(out, MI_DOUBLE);
            writeIntLE(out, (int) realSize);
            for (double x : v) {
                writeDoubleLE(out, x);
            }
            writeZeros(out, pad((int) realSize));
        }

        private static int[] cubeDimensions(String name, float[][][] cube) throws IOException {
            if (cube == null || cube.length == 0 || cube[0] == null || cube[0].length == 0
                    || cube[0][0] == null || cube[0][0].length == 0) {
                throw new IOException("MAT cube must be non-empty: " + name);
            }
            int h = cube.length;
            int w = cube[0].length;
            int b = cube[0][0].length;
            for (int y = 0; y < h; y++) {
                if (cube[y] == null || cube[y].length != w) {
                    throw new IOException("MAT cube is not rectangular: " + name);
                }
                for (int x = 0; x < w; x++) {
                    if (cube[y][x] == null || cube[y][x].length != b) {
                        throw new IOException("MAT cube has inconsistent band counts: " + name);
                    }
                }
            }
            return new int[]{h, w, b};
        }

        private static long checkedProduct(String name, long... factors) throws IOException {
            long result = 1L;
            try {
                for (long factor : factors) {
                    result = Math.multiplyExact(result, factor);
                }
            } catch (ArithmeticException e) {
                throw new IOException("MAT variable is too large: " + name, e);
            }
            if (result > Integer.MAX_VALUE) {
                throw new IOException("MAT v5 variable exceeds the supported 2 GiB limit: " + name);
            }
            return result;
        }

        private static int checkedMatrixBodySize(String name, int dimensionCount, int nameLength,
                                                 long realSize) throws IOException {
            long size = elementSize(8L)
                    + elementSize((long) dimensionCount * Integer.BYTES)
                    + elementSize(nameLength)
                    + elementSize(realSize);
            if (size > Integer.MAX_VALUE) {
                throw new IOException("MAT v5 matrix exceeds the supported 2 GiB limit: " + name);
            }
            return (int) size;
        }

        private static long elementSize(long payloadSize) {
            return 8L + payloadSize + pad(payloadSize);
        }

        private static void writeArrayFlags(OutputStream out, int cls) throws IOException {
            writeIntLE(out, MI_UINT32);
            writeIntLE(out, 8);
            writeIntLE(out, cls);
            writeIntLE(out, 0);
        }

        private static void writeDims(OutputStream out, int[] dims) throws IOException {
            int payloadSize = dims.length * Integer.BYTES;
            writeIntLE(out, MI_INT32);
            writeIntLE(out, payloadSize);
            for (int dimension : dims) {
                writeIntLE(out, dimension);
            }
            writeZeros(out, pad(payloadSize));
        }

        private static void writeName(OutputStream out, byte[] name) throws IOException {
            writeElement(out, MI_INT8, name);
        }

        private static void writeElement(OutputStream out, int type, byte[] payload) throws IOException {
            writeIntLE(out, type);
            writeIntLE(out, payload.length);
            out.write(payload);
            writeZeros(out, pad(payload.length));
        }

        private static int pad(int n) {
            return (8 - (n % 8)) % 8;
        }

        private static long pad(long n) {
            return (8L - (n % 8L)) % 8L;
        }

        private static void writeZeros(OutputStream out, int n) throws IOException {
            for (int i = 0; i < n; i++) out.write(0);
        }

        private static void writeIntLE(OutputStream out, int v) throws IOException {
            out.write(v & 0xff);
            out.write((v >>> 8) & 0xff);
            out.write((v >>> 16) & 0xff);
            out.write((v >>> 24) & 0xff);
        }

        private static void writeShortLE(OutputStream out, int v) throws IOException {
            out.write(v & 0xff);
            out.write((v >>> 8) & 0xff);
        }

        private static void writeDoubleLE(OutputStream out, double v) throws IOException {
            long x = Double.doubleToLongBits(v);
            for (int i = 0; i < 8; i++) out.write((int) ((x >>> (8 * i)) & 0xff));
        }

        private static int readIntLE(RandomAccessFile raf) throws IOException {
            int b0 = raf.read();
            int b1 = raf.read();
            int b2 = raf.read();
            int b3 = raf.read();
            if ((b0 | b1 | b2 | b3) < 0) throw new EOFException();
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }

        private static class Element {
            public final int type;
            public final byte[] payload;

            Element(int type, byte[] payload) {
                this.type = type;
                this.payload = payload;
            }
        }

        private static class MatrixVar {
            public final String name;
            public final int[] dims;
            public final double[] values;

            MatrixVar(String name, int[] dims, double[] values) {
                this.name = name;
                this.dims = dims;
                this.values = values;
            }

            float[][][] toCube() {
                int h = dims[0], w = dims[1], b = dims.length > 2 ? dims[2] : 1;
                float[][][] out = new float[h][w][b];
                int idx = 0;
                for (int k = 0; k < b; k++) {
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) out[y][x][k] = (float) values[idx++];
                    }
                }
                return out;
            }

            double[] toVector() {
                return values;
            }
        }
    }

    public static class Args {
        private final Map<String, String> values = new HashMap<>();

        public static Args parse(String[] args) {
            Args out = new Args();
            for (int i = 0; i < args.length; i++) {
                String k = args[i];
                if (k.startsWith("--")) {
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) out.values.put(k, args[++i]);
                    else out.values.put(k, "true");
                }
            }
            return out;
        }

        public boolean has(String key) {
            return values.containsKey(key);
        }

        public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        public String required(String key) {
            String v = values.get(key);
            if (v == null) throw new IllegalArgumentException("Missing required argument: " + key);
            return v;
        }
    }
}
