package com.hyper.spectral.support.fusion;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.SIFT;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Registration keeps the algorithm format-agnostic: it only receives Dataset.ImagePair.
 */
public class Registration {
    public static final int DEFAULT_LR_SIZE = 512;
    public static final int DEFAULT_HR_SIZE = 1024;
    private static final double LOWE_RATIO_THRESHOLD = 0.80d;
    private static final double RANSAC_REPROJECTION_THRESHOLD = 3.0d;
    private static final int MIN_GOOD_MATCHES = 4;
    private static final int MIN_INLIERS = 3;
    private static final int CROP_MARGIN_LOW_PIXELS = 5;

    public static void main(String[] args) throws Exception {
        Dataset.Args a = Dataset.Args.parse(args);
        if (a.has("--help") || args.length == 0) {
            printUsage();
            return;
        }
        Dataset.loadOpenCv(a.get("--opencv-lib", null));

        Dataset.ImagePair input = Dataset.fromMat(Path.of(a.required("--input")));
        RegisteredPair registered = register(input, Integer.parseInt(a.get("--lr-size", String.valueOf(DEFAULT_LR_SIZE))));

        Dataset.MatData out = new Dataset.MatData();
        out.cubes.put(Dataset.LRHSI, registered.lrhsi);
        out.cubes.put(Dataset.HRMSI, registered.hrmsi);
        if (input.wavelengths.length > 0) out.vectors.put(Dataset.WAVELENGTHS, input.wavelengths);
        out.vectors.put("affine_low", flatten(registered.affineLow));
        out.vectors.put("affine_high", flatten(registered.affineHigh));
        out.vectors.put("crop_xywh_low", registered.cropXywhLow());
        out.vectors.put("registration_good_matches", new double[]{registered.goodMatches});
        out.vectors.put("registration_inliers", new double[]{registered.inliers});
        out.vectors.put("registration_inlier_rmse", new double[]{registered.inlierRmse});

        Path outPath = Path.of(a.get("--out", "registered.mat"));
        Dataset.MatIO.write(outPath, out, Dataset.MatVersion.fromString(a.get("--mat-version", "v5")));
        System.out.println("Saved registered data: " + outPath.toAbsolutePath());
        System.out.println(registered.summary());
    }

    public static RegisteredPair register(Dataset.ImagePair input, int targetLrSize) {
        float[][][] lrhsi = input.lrhsi;
        float[][][] hrmsi = input.hrmsi;
        int hLr = lrhsi.length;
        int wLr = lrhsi[0].length;
        int hHr = hrmsi.length;
        int wHr = hrmsi[0].length;

        Mat grayLr = Dataset.grayToMat8(Dataset.grayFromLrhsi(lrhsi));
        Mat grayHrFull = Dataset.grayToMat8(Dataset.grayFromHrmsi(hrmsi));
        Mat grayHrResized = new Mat();
        Imgproc.resize(grayHrFull, grayHrResized, new Size(wLr, hLr), 0, 0, Imgproc.INTER_AREA);

        AffineEstimate affine = estimateAffine(grayLr, grayHrResized);
        double[][] low = affine.matrix;
        double sx = (double) wHr / wLr;
        double sy = (double) hHr / hLr;
        double[][] high = toHighResolutionAffine(low, sx, sy);

        float[][][] alignedHr = warpHrmsi(hrmsi, high, wHr, hHr);
        CropBox validLowBox = applyMargin(
                largestValidRectangle(warpedValidityMask(low, wLr, hLr)),
                CROP_MARGIN_LOW_PIXELS
        );
        CropBox finalLowBox = targetLrSize > 0
                ? centeredSquareInside(validLowBox, targetLrSize)
                : validLowBox;
        CropBox nativeHighBox = new CropBox(
                (int) Math.round(finalLowBox.x0 * sx),
                (int) Math.round(finalLowBox.y0 * sy),
                (int) Math.round(finalLowBox.x1 * sx),
                (int) Math.round(finalLowBox.y1 * sy)
        ).clip(wHr, hHr);
        if (nativeHighBox.isEmpty()) {
            throw new IllegalStateException("Mapped HR-MSI crop is empty.");
        }

        float[][][] finalLr = crop(lrhsi, finalLowBox);
        float[][][] nativeHrCrop = crop(alignedHr, nativeHighBox);
        float[][][] finalHr = resizeCube(
                nativeHrCrop,
                finalLr.length * 2,
                finalLr[0].length * 2
        );
        return new RegisteredPair(
                finalLr,
                finalHr,
                low,
                high,
                affine.goodMatches,
                affine.inliers,
                affine.inlierRmse,
                finalLowBox
        );
    }

    private static AffineEstimate estimateAffine(Mat referenceLr, Mat movingHrResized) {
        SIFT sift = SIFT.create(5000, 3, 0.02, 10, 1.6);
        MatOfKeyPoint kp1 = new MatOfKeyPoint();
        MatOfKeyPoint kp2 = new MatOfKeyPoint();
        Mat desc1 = new Mat();
        Mat desc2 = new Mat();
        sift.detectAndCompute(referenceLr, new Mat(), kp1, desc1);
        sift.detectAndCompute(movingHrResized, new Mat(), kp2, desc2);
        if (desc1.empty() || desc2.empty()) {
            throw new IllegalStateException("SIFT did not find descriptors in both images.");
        }

        BFMatcher matcher = BFMatcher.create(Core.NORM_L2, false);
        List<MatOfDMatch> knn = new ArrayList<>();
        matcher.knnMatch(desc1, desc2, knn, 2);

        List<DMatch> good = new ArrayList<>();
        for (MatOfDMatch m : knn) {
            DMatch[] pair = m.toArray();
            if (pair.length >= 2 && pair[0].distance < LOWE_RATIO_THRESHOLD * pair[1].distance) {
                good.add(pair[0]);
            }
        }
        good.sort(Comparator.comparingDouble(d -> d.distance));
        if (good.size() < MIN_GOOD_MATCHES) {
            throw new IllegalStateException("Not enough SIFT matches: " + good.size());
        }

        KeyPoint[] refKp = kp1.toArray();
        KeyPoint[] movKp = kp2.toArray();
        Point[] src = new Point[good.size()];
        Point[] dst = new Point[good.size()];
        for (int i = 0; i < good.size(); i++) {
            DMatch m = good.get(i);
            dst[i] = refKp[m.queryIdx].pt;
            src[i] = movKp[m.trainIdx].pt;
        }
        MatOfPoint2f srcMat = new MatOfPoint2f(src);
        MatOfPoint2f dstMat = new MatOfPoint2f(dst);
        Mat inliers = new Mat();
        Mat affine = Calib3d.estimateAffine2D(
                srcMat,
                dstMat,
                inliers,
                Calib3d.RANSAC,
                RANSAC_REPROJECTION_THRESHOLD,
                5000,
                0.99,
                10
        );
        if (affine.empty()) {
            throw new IllegalStateException("RANSAC failed to estimate affine transform.");
        }
        double[][] a = new double[2][3];
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                a[r][c] = affine.get(r, c)[0];
                if (!Double.isFinite(a[r][c])) {
                    throw new IllegalStateException("RANSAC returned a non-finite affine transform.");
                }
            }
        }
        int inlierCount = Core.countNonZero(inliers);
        if (inlierCount < MIN_INLIERS) {
            throw new IllegalStateException("RANSAC inliers below minimum: " + inlierCount);
        }
        byte[] inlierMask = new byte[good.size()];
        inliers.get(0, 0, inlierMask);
        double squaredError = 0.0d;
        for (int i = 0; i < good.size(); i++) {
            if (inlierMask[i] == 0) {
                continue;
            }
            double transformedX = a[0][0] * src[i].x + a[0][1] * src[i].y + a[0][2];
            double transformedY = a[1][0] * src[i].x + a[1][1] * src[i].y + a[1][2];
            double dx = transformedX - dst[i].x;
            double dy = transformedY - dst[i].y;
            squaredError += dx * dx + dy * dy;
        }
        double inlierRmse = Math.sqrt(squaredError / inlierCount);
        return new AffineEstimate(a, good.size(), inlierCount, inlierRmse);
    }

    private static double[][] toHighResolutionAffine(double[][] m, double sx, double sy) {
        return new double[][]{
                {m[0][0], m[0][1] * (sx / sy), m[0][2] * sx},
                {m[1][0] * (sy / sx), m[1][1], m[1][2] * sy}
        };
    }

    private static float[][][] warpHrmsi(float[][][] hrmsi, double[][] affine, int width, int height) {
        Mat srcRgb = Dataset.cubeRgbToMat(hrmsi);
        Mat srcBgr = new Mat();
        Imgproc.cvtColor(srcRgb, srcBgr, Imgproc.COLOR_RGB2BGR);
        Mat warp = new Mat(2, 3, CvType.CV_64F);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) warp.put(r, c, affine[r][c]);
        }
        Mat dstBgr = new Mat();
        Imgproc.warpAffine(srcBgr, dstBgr, warp, new Size(width, height), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar.all(0));
        Mat dstRgb = new Mat();
        Imgproc.cvtColor(dstBgr, dstRgb, Imgproc.COLOR_BGR2RGB);
        return Dataset.matToCubeRgb(dstRgb);
    }

    public static float[][][] resizeCube(float[][][] src, int newH, int newW) {
        int h = src.length;
        int w = src[0].length;
        int b = src[0][0].length;
        float[][][] out = new float[newH][newW][b];
        double yScale = (double) h / newH;
        double xScale = (double) w / newW;
        for (int y = 0; y < newH; y++) {
            double sy = (y + 0.5) * yScale - 0.5;
            int y0 = clamp((int) Math.floor(sy), 0, h - 1);
            int y1 = clamp(y0 + 1, 0, h - 1);
            double wy = sy - Math.floor(sy);
            if (sy < 0) wy = 0;
            for (int x = 0; x < newW; x++) {
                double sx = (x + 0.5) * xScale - 0.5;
                int x0 = clamp((int) Math.floor(sx), 0, w - 1);
                int x1 = clamp(x0 + 1, 0, w - 1);
                double wx = sx - Math.floor(sx);
                if (sx < 0) wx = 0;
                for (int k = 0; k < b; k++) {
                    double v00 = src[y0][x0][k];
                    double v01 = src[y0][x1][k];
                    double v10 = src[y1][x0][k];
                    double v11 = src[y1][x1][k];
                    out[y][x][k] = (float) ((1 - wy) * ((1 - wx) * v00 + wx * v01) + wy * ((1 - wx) * v10 + wx * v11));
                }
            }
        }
        return out;
    }

    private static float[][][] crop(float[][][] src, CropBox box) {
        int h = box.y1 - box.y0;
        int w = box.x1 - box.x0;
        int b = src[0][0].length;
        float[][][] out = new float[h][w][b];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                System.arraycopy(src[box.y0 + y][box.x0 + x], 0, out[y][x], 0, b);
            }
        }
        return out;
    }

    private static boolean[][] warpedValidityMask(double[][] affine, int width, int height) {
        Mat source = Mat.ones(height, width, CvType.CV_8UC1);
        Mat warped = new Mat();
        Imgproc.warpAffine(
                source,
                warped,
                affineMat(affine),
                new Size(width, height),
                Imgproc.INTER_NEAREST,
                Core.BORDER_CONSTANT,
                Scalar.all(0)
        );
        boolean[][] mask = new boolean[height][width];
        byte[] row = new byte[width];
        for (int y = 0; y < height; y++) {
            warped.get(y, 0, row);
            for (int x = 0; x < width; x++) {
                mask[y][x] = row[x] != 0;
            }
        }
        return mask;
    }

    static CropBox largestValidRectangle(boolean[][] mask) {
        if (mask.length == 0 || mask[0].length == 0) {
            throw new IllegalArgumentException("Validity mask must not be empty.");
        }
        int width = mask[0].length;
        int[] heights = new int[width];
        int bestArea = 0;
        CropBox best = null;
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < width; x++) {
                heights[x] = mask[y][x] ? heights[x] + 1 : 0;
            }
            int[] starts = new int[width + 1];
            int[] stackHeights = new int[width + 1];
            int stackSize = 0;
            for (int x = 0; x <= width; x++) {
                int currentHeight = x < width ? heights[x] : 0;
                int start = x;
                while (stackSize > 0 && stackHeights[stackSize - 1] > currentHeight) {
                    stackSize--;
                    int rectangleStart = starts[stackSize];
                    int rectangleHeight = stackHeights[stackSize];
                    int area = rectangleHeight * (x - rectangleStart);
                    if (area > bestArea) {
                        bestArea = area;
                        best = new CropBox(rectangleStart, y - rectangleHeight + 1, x, y + 1);
                    }
                    start = rectangleStart;
                }
                if (stackSize == 0 || stackHeights[stackSize - 1] < currentHeight) {
                    starts[stackSize] = start;
                    stackHeights[stackSize] = currentHeight;
                    stackSize++;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("Affine transform has no valid common area.");
        }
        return best;
    }

    static CropBox applyMargin(CropBox box, int margin) {
        if (margin < 0) {
            throw new IllegalArgumentException("Crop margin must not be negative.");
        }
        CropBox result = new CropBox(
                box.x0 + margin,
                box.y0 + margin,
                box.x1 - margin,
                box.y1 - margin
        );
        if (result.isEmpty()) {
            throw new IllegalStateException("Valid common area is smaller than the crop margin.");
        }
        return result;
    }

    private static CropBox centeredSquareInside(CropBox bounds, int requestedSize) {
        int maxSize = Math.min(requestedSize, Math.min(bounds.width(), bounds.height()));
        if (maxSize <= 0) {
            throw new IllegalStateException("Valid common area is empty.");
        }
        int x0 = bounds.x0 + (bounds.width() - maxSize) / 2;
        int y0 = bounds.y0 + (bounds.height() - maxSize) / 2;
        return new CropBox(x0, y0, x0 + maxSize, y0 + maxSize);
    }

    private static Mat affineMat(double[][] affine) {
        Mat transform = new Mat(2, 3, CvType.CV_64F);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                transform.put(row, column, affine[row][column]);
            }
        }
        return transform;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[] flatten(double[][] a) {
        double[] out = new double[a.length * a[0].length];
        int n = 0;
        for (double[] row : a) for (double v : row) out[n++] = v;
        return out;
    }

    private static void printUsage() {
        System.out.println("Registration usage:");
        System.out.println("  java Registration --input preprocessed.mat --out registered.mat");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --lr-size 512");
        System.out.println("  --mat-version v5|v73");
        System.out.println("  --opencv-lib path/to/opencv_java*.dll");
    }

    public static class RegisteredPair {
        public final float[][][] lrhsi;
        public final float[][][] hrmsi;
        public final double[][] affineLow;
        public final double[][] affineHigh;
        public final int goodMatches;
        public final int inliers;
        public final double inlierRmse;
        private final CropBox cropLow;

        public RegisteredPair(float[][][] lrhsi, float[][][] hrmsi, double[][] affineLow, double[][] affineHigh,
                              int goodMatches, int inliers, double inlierRmse, CropBox cropLow) {
            this.lrhsi = lrhsi;
            this.hrmsi = hrmsi;
            this.affineLow = affineLow;
            this.affineHigh = affineHigh;
            this.goodMatches = goodMatches;
            this.inliers = inliers;
            this.inlierRmse = inlierRmse;
            this.cropLow = cropLow;
        }

        public double[] cropXywhLow() {
            return new double[]{cropLow.x0, cropLow.y0, cropLow.width(), cropLow.height()};
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "Registered LRHSI=%dx%dx%d, HRMSI=%dx%dx%d, matches=%d, inliers=%d, rmse=%.3f",
                    lrhsi.length, lrhsi[0].length, lrhsi[0][0].length,
                    hrmsi.length, hrmsi[0].length, hrmsi[0][0].length,
                    goodMatches, inliers, inlierRmse);
        }
    }

    private static class AffineEstimate {
        public final double[][] matrix;
        public final int goodMatches;
        public final int inliers;
        public final double inlierRmse;

        AffineEstimate(double[][] matrix, int goodMatches, int inliers, double inlierRmse) {
            this.matrix = matrix;
            this.goodMatches = goodMatches;
            this.inliers = inliers;
            this.inlierRmse = inlierRmse;
        }
    }

    static class CropBox {
        public final int x0;
        public final int y0;
        public final int x1;
        public final int y1;

        CropBox(int x0, int y0, int x1, int y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        CropBox clip(int width, int height) {
            return new CropBox(clamp(x0, 0, width), clamp(y0, 0, height), clamp(x1, 0, width), clamp(y1, 0, height));
        }

        int width() {
            return x1 - x0;
        }

        int height() {
            return y1 - y0;
        }

        boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }
    }
}
