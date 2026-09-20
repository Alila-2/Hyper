package com.hyper.spectral.support.fusion;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Random;

/**
 * CNMF fusion algorithm: faithful Java port of the CNMF.py algorithm structure.
 *
 * Main modes:
 *   1. Fusion only:
 *      java CnmfFusion --input registered.mat --out fusion_result.mat --png Final_HRHSI_TrueColor.png
 *
 *   2. One-shot full pipeline:
 *      java CnmfFusion --hdr D.hdr --img D.img --msi color.bmp --workdir out
 *
 * Notes:
 *   - --endmembers enables manual M; otherwise VD+VCA follows the Python version.
 *   - The implementation favors formula fidelity over speed for the first Java version.
 */
public class CnmfFusion {
    private static final double TH_H = 1e-8;
    private static final double TH_M = 1e-8;
    private static final double TH_OUTER = 1e-2;
    private static final int MIN_MS_BANDS = 3;

    public static void main(String[] args) throws Exception {
        Dataset.Args a = Dataset.Args.parse(args);
        if (a.has("--help") || args.length == 0) {
            printUsage();
            return;
        }
        Dataset.loadOpenCv(a.get("--opencv-lib", null));

        Dataset.ImagePair pair;
        Path workdir = Path.of(a.get("--workdir", "."));
        String matVersion = a.get("--mat-version", "v5");
        if (a.has("--hdr")) {
            pair = Dataset.fromRaw(Path.of(a.required("--hdr")), Path.of(a.required("--img")), Path.of(a.required("--msi")));
            Dataset.MatIO.write(workdir.resolve("preprocessed.mat"), Dataset.MatData.fromImagePair(pair), Dataset.MatVersion.fromString(matVersion));
            Registration.RegisteredPair reg = Registration.register(pair, Integer.parseInt(a.get("--lr-size", "512")));
            Dataset.MatData regData = new Dataset.MatData();
            regData.cubes.put(Dataset.LRHSI, reg.lrhsi);
            regData.cubes.put(Dataset.HRMSI, reg.hrmsi);
            if (pair.wavelengths.length > 0) regData.vectors.put(Dataset.WAVELENGTHS, pair.wavelengths);
            Dataset.MatIO.write(workdir.resolve("registered.mat"), regData, Dataset.MatVersion.fromString(matVersion));
            pair = new Dataset.ImagePair(reg.lrhsi, reg.hrmsi, pair.wavelengths, pair.sourceHeader);
        } else {
            pair = Dataset.fromMat(Path.of(a.required("--input")));
        }

        int manualM = a.has("--endmembers") ? Integer.parseInt(a.get("--endmembers", "0")) : 0;
        boolean verbose = "on".equalsIgnoreCase(a.get("--verbose", "on"));
        float[][][] hrhsi = cnmfFusion(pair.lrhsi, pair.hrmsi, manualM, verbose);

        Dataset.MatData out = new Dataset.MatData();
        out.cubes.put("HRHSI", hrhsi);
        out.cubes.put(Dataset.LRHSI, pair.lrhsi);
        out.cubes.put(Dataset.HRMSI, pair.hrmsi);
        if (pair.wavelengths.length > 0) out.vectors.put(Dataset.WAVELENGTHS, pair.wavelengths);
        Path outPath = Path.of(a.get("--out", workdir.resolve("fusion_result.mat").toString()));
        Dataset.MatIO.write(outPath, out, Dataset.MatVersion.fromString(matVersion));

        int[] rgb = rgbIndices(pair.wavelengths, Integer.parseInt(a.get("--r", "60")), Integer.parseInt(a.get("--g", "38")), Integer.parseInt(a.get("--b", "16")));
        Path png = Path.of(a.get("--png", workdir.resolve("Final_HRHSI_TrueColor.png").toString()));
        Dataset.saveRgbPng(png, Dataset.trueColor(hrhsi, rgb[0], rgb[1], rgb[2], null));
        System.out.println("Saved fusion MAT: " + outPath.toAbsolutePath());
        System.out.println("Saved visualization: " + png.toAbsolutePath());
    }

    public static float[][][] cnmfFusion(float[][][] hsiCube, float[][][] msiCube, int manualEndmembers, boolean verbose) {
        int rows1 = msiCube.length;
        int cols1 = msiCube[0].length;
        int bands1 = msiCube[0][0].length;
        int rows2 = hsiCube.length;
        int cols2 = hsiCube[0].length;
        int bands2 = hsiCube[0][0].length;
        int scale = Math.round((float) rows1 / rows2);
        if (rows1 / scale != rows2 || cols1 / scale != cols2) {
            throw new IllegalArgumentException("HR/LR dimensions must have an integer scale. HR=" + rows1 + "x" + cols1 + ", LR=" + rows2 + "x" + cols2);
        }

        double[][] hsi = cubeToBandPixels(hsiCube);
        double[][] msi = cubeToBandPixels(msiCube);
        clampNonnegative(hsi);
        clampNonnegative(msi);

        if (verbose) System.out.println("Estimate R...");
        double[][] rFull = estR(hsiCube, msiCube);
        for (int b = 0; b < bands1; b++) {
            double offset = rFull[b][bands2];
            for (int n = 0; n < msi[b].length; n++) {
                msi[b][n] = Math.max(0.0, msi[b][n] - offset);
            }
        }
        double[][] r = sliceColumns(rFull, 0, bands2);

        double sum2one = 2.0 * Math.sqrt(mean(msi) / 0.7455) / Math.pow(bands1, 3);
        int innerIters = bands1 == 1 ? 20 : 20;
        int outerIters = bands1 == 1 ? 1 : 1;

        int mEst = manualEndmembers > 0 ? manualEndmembers : (int) Math.round(vd(hsi, 5e-2));
        int m = manualEndmembers > 0 ? manualEndmembers : Math.max(Math.min(30, bands2), mEst);
        if (verbose) {
            System.out.println("Scale: " + scale);
            System.out.println("Number of endmembers: " + m + (manualEndmembers > 0 ? " (manual)" : " (VD/VCA)"));
        }

        CNMFState s = cnmfInit(rows1, cols1, scale, m, hsi, msi, sum2one, innerIters, TH_H, TH_M, r, verbose);
        double[] costH = new double[outerIters + 1];
        double[] costM = new double[outerIters + 1];
        costH[0] = s.rmseH;
        costM[0] = s.rmseM;

        for (int i = 0; i < outerIters; i++) {
            IterResult it = cnmfIte(rows1, cols1, scale, m, s.hyper, s.multi, s.wHyper, s.hHyper, s.wMulti, s.hMulti,
                    innerIters, TH_H, TH_M, i, r, verbose);
            s.wHyper = it.wHyper;
            s.hHyper = it.hHyper;
            costH[i + 1] = it.rmseH;
            costM[i + 1] = it.rmseM;
            if ((costH[i] - costH[i + 1]) / costH[i] > TH_OUTER
                    && (costM[i] - costM[i + 1]) / costM[i] > TH_OUTER
                    && i < outerIters - 1) {
                s.wMulti = it.wMulti2;
                s.hMulti = it.hMulti2;
            } else if (i == outerIters - 1) {
                if (verbose) System.out.println("Max outer iteration.");
                s.wMulti = it.wMulti2;
                s.hMulti = it.hMulti2;
            } else {
                if (verbose) System.out.println("END");
                s.wMulti = it.wMulti2;
                s.hMulti = it.hMulti2;
                break;
            }
        }

        double[][] out = matmul(sliceRows(s.wHyper, 0, bands2), s.hMulti);
        return bandPixelsToCube(out, rows1, cols1, bands2);
    }

    private static CNMFState cnmfInit(int xdata, int ydata, int scale, int m, double[][] hyper, double[][] multi,
                                     double delta, int iIn, double deltaH, double deltaM, double[][] srf, boolean verbose) {
        int band = hyper.length;
        int multiBand = multi.length;
        int hx = xdata / scale;
        int hy = ydata / scale;
        if (verbose) System.out.println("Initialize Wh by VCA");
        VcaResult vca = vca(hyper, m);
        double[][] wHyper = vca.endmembers;
        double[][] hHyper = fill(m, hx * hy, 1.0 / m);

        wHyper = vstack(wHyper, fill(1, wHyper[0].length, delta));
        hyper = vstack(hyper, fill(1, hyper[0].length, delta));

        if (verbose) System.out.println("NMF for Vh (1)");
        double cost0 = nmfHyper(hyper, wHyper, hHyper, band, iIn, deltaH, verbose, true);
        double rmseH = Math.sqrt(cost0 / (hyper[0].length * band));
        if (verbose) System.out.println("    RMSE(Vh) = " + rmseH);

        double[][] wMulti = matmul(srf, sliceRows(wHyper, 0, band));
        wMulti = vstack(wMulti, fill(1, m, delta));
        multi = vstack(multi, fill(1, multi[0].length, delta));

        double[][] hMulti = fill(m, xdata * ydata, 1.0 / m);
        for (int i = 0; i < m; i++) {
            double[][] abundance = vectorToImage(hHyper[i], hx, hy);
            double[][] up = zoomBi(abundance, scale);
            if (up.length != xdata || up[0].length != ydata) up = resize2d(up, xdata, ydata);
            hMulti[i] = imageToVector(up);
        }
        clampNonnegative(hMulti);

        if (verbose) System.out.println("NMF for Vm (1)");
        double costM = nmfMulti(multi, wMulti, hMulti, multiBand, iIn, deltaM, verbose);
        double rmseM = Math.sqrt(costM / (multi[0].length * multiBand));
        if (verbose) System.out.println("    RMSE(Vm) = " + rmseM);
        return new CNMFState(hyper, multi, wHyper, hHyper, wMulti, hMulti, rmseH, rmseM);
    }

    private static IterResult cnmfIte(int xdata, int ydata, int scale, int m, double[][] hyper, double[][] multi,
                                      double[][] wHyper, double[][] hHyper, double[][] wMulti, double[][] hMulti,
                                      int iIn, double deltaH, double deltaM, int iOut, double[][] srf, boolean verbose) {
        int band = hyper.length - 1;
        int multiBand = multi.length - 1;
        int hx = xdata / scale;
        int hy = ydata / scale;

        if (verbose) System.out.println("Iteration " + iOut);
        hHyper = cubeToBandPixels(gaussianDownSample(abundanceToCube(transpose(hMulti), xdata, ydata, m), scale));

        if (verbose) System.out.println("NMF for Vh (" + (iOut + 2) + ")");
        double costH = nmfHyperIter(hyper, wHyper, hHyper, band, multiBand, iIn, deltaH, verbose);
        double rmseH = Math.sqrt(costH / (hyper[0].length * band));
        if (verbose) System.out.println("    RMSE(Vh) = " + rmseH);

        double[][] wMulti1 = copy(wMulti);
        double[][] hMulti1 = copy(hMulti);
        double[][] rw = matmul(srf, sliceRows(wHyper, 0, band));
        for (int r = 0; r < multiBand; r++) System.arraycopy(rw[r], 0, wMulti[r], 0, rw[r].length);

        if (verbose) System.out.println("NMF for Vm (" + (iOut + 2) + ")");
        double costM = nmfMulti(multi, wMulti, hMulti, multiBand, iIn, deltaM, verbose);
        double rmseM = Math.sqrt(costM / (multi[0].length * multiBand));
        if (verbose) System.out.println("    RMSE(Vm) = " + rmseM);

        return new IterResult(wHyper, hHyper, wMulti1, hMulti1, wMulti, hMulti, rmseH, rmseM);
    }

    private static double nmfHyper(double[][] hyper, double[][] wHyper, double[][] hHyper, int band, int iIn,
                                   double deltaH, boolean verbose, boolean initHFirst) {
        double cost0 = 0.0;
        for (int i = 0; i < iIn; i++) {
            if (i == 0 && initHFirst) {
                for (int q = 0; q < iIn * 3; q++) {
                    long t0 = System.currentTimeMillis();

                    double[][] old = copy(hHyper);
                    updateH(hHyper, wHyper, hyper);
                    double cost = squaredError(sliceRows(hyper, 0, band), matmul(sliceRows(wHyper, 0, band), hHyper));

                    if (verbose && (q % 1 == 0)) {
                        System.out.printf(
                                Locale.ROOT,
                                "    H_hyper init q=%d/%d, cost=%.6e, time=%.2fs%n",
                                q + 1,
                                iIn * 3,
                                cost,
                                (System.currentTimeMillis() - t0) / 1000.0
                        );
                    }
                    if (q > 1 && relativeImprovement(cost0, cost) < deltaH) {
                        if (verbose) System.out.println("Initialization of H_hyper converged at iteration " + q);
                        copyInto(old, hHyper);
                        return cost0;
                    }
                    cost0 = cost;
                }
            } else {
                double[][] oldW = copy(wHyper);
                updateWRows(wHyper, hyper, hHyper, band);
                double[][] oldH = copy(hHyper);
                updateH(hHyper, wHyper, hyper);
                double cost = squaredError(sliceRows(hyper, 0, band), matmul(sliceRows(wHyper, 0, band), hHyper));
                if (relativeImprovement(cost0, cost) < deltaH) {
                    if (verbose) System.out.println("Optimization of HS unmixing converged at iteration " + i);
                    copyInto(oldW, wHyper);
                    copyInto(oldH, hHyper);
                    return cost0;
                }
                cost0 = cost;
            }
        }
        return cost0;
    }

    private static double nmfHyperIter(double[][] hyper, double[][] wHyper, double[][] hHyper, int band, int multiBand,
                                       int iIn, double deltaH, boolean verbose) {
        double cost0 = 0.0;
        for (int i = 0; i < iIn; i++) {
            if (i == 0) {
                for (int q = 0; q < iIn; q++) {
                    double[][] oldW = copy(wHyper);
                    updateWRows(wHyper, hyper, hHyper, band);
                    double cost = squaredError(sliceRows(hyper, 0, band), matmul(sliceRows(wHyper, 0, band), hHyper));
                    if (q > 1 && relativeImprovement(cost0, cost) < deltaH) {
                        if (verbose) System.out.println("Initialization of W_hyper converged at iteration " + q);
                        copyInto(oldW, wHyper);
                        return cost0;
                    }
                    cost0 = cost;
                }
            } else {
                double[][] oldH = copy(hHyper);
                if (multiBand > MIN_MS_BANDS) updateH(hHyper, wHyper, hyper);
                double[][] oldW = copy(wHyper);
                updateWRows(wHyper, hyper, hHyper, band);
                double cost = squaredError(sliceRows(hyper, 0, band), matmul(sliceRows(wHyper, 0, band), hHyper));
                if (relativeImprovement(cost0, cost) < deltaH) {
                    if (verbose) System.out.println("Optimization of HS unmixing converged at iteration " + i);
                    copyInto(oldH, hHyper);
                    copyInto(oldW, wHyper);
                    return cost0;
                }
                cost0 = cost;
            }
        }
        return cost0;
    }

    private static double nmfMulti(double[][] multi, double[][] wMulti, double[][] hMulti, int multiBand,
                                   int iIn, double deltaM, boolean verbose) {
        double cost0 = 0.0;
        for (int i = 0; i < iIn; i++) {
            if (i == 0) {
                for (int q = 0; q < iIn; q++) {
                    double[][] old = copy(hMulti);
                    updateH(hMulti, wMulti, multi);
                    double cost = squaredError(sliceRows(multi, 0, multiBand), matmul(sliceRows(wMulti, 0, multiBand), hMulti));
                    if (q > 1 && relativeImprovement(cost0, cost) < deltaM) {
                        if (verbose) System.out.println("Initialization of H_multi converged at iteration " + q);
                        copyInto(old, hMulti);
                        return cost0;
                    }
                    cost0 = cost;
                }
            } else {
                double[][] oldW = copy(wMulti);
                if (multiBand > MIN_MS_BANDS) updateWRows(wMulti, multi, hMulti, multiBand);
                double[][] oldH = copy(hMulti);
                updateH(hMulti, wMulti, multi);
                double cost = squaredError(sliceRows(multi, 0, multiBand), matmul(sliceRows(wMulti, 0, multiBand), hMulti));
                if (relativeImprovement(cost0, cost) < deltaM) {
                    if (verbose) System.out.println("Optimization of MS unmixing converged at iteration " + i);
                    copyInto(oldW, wMulti);
                    copyInto(oldH, hMulti);
                    return cost0;
                }
                cost0 = cost;
            }
        }
        return cost0;
    }

    private static void updateH(double[][] h, double[][] w, double[][] v) {
        double[][] numerator = matmul(transpose(w), v);
        double[][] denominator = matmul(matmul(transpose(w), w), h);
        elemMulDivInPlace(h, numerator, denominator);
    }

    private static void updateWRows(double[][] w, double[][] v, double[][] h, int rows) {
        double[][] numerator = matmul(sliceRows(v, 0, rows), transpose(h));
        double[][] denominator = matmul(matmul(sliceRows(w, 0, rows), h), transpose(h));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < w[0].length; c++) {
                w[r][c] = safeMulDiv(w[r][c], numerator[r][c], denominator[r][c]);
            }
        }
    }

    private static double[][] estR(float[][][] hs, float[][][] ms) {
        int rows1 = ms.length;
        int cols1 = ms[0].length;
        int bands1 = ms[0][0].length;
        int rows2 = hs.length;
        int cols2 = hs[0].length;
        int bands2 = hs[0][0].length;
        int scale = Math.round((float) rows1 / rows2);
        float[][][] yCube = gaussianDownSample(ms, scale);
        double[][] y = cubeToPixelBands(yCube);
        double[][] a = new double[rows2 * cols2][bands2 + 1];
        int n = 0;
        for (int yy = 0; yy < rows2; yy++) {
            for (int x = 0; x < cols2; x++) {
                for (int b = 0; b < bands2; b++) a[n][b] = hs[yy][x][b];
                a[n][bands2] = 1.0;
                n++;
            }
        }
        double[][] r = new double[bands1][bands2 + 1];
        for (int b = 0; b < bands1; b++) {
            double[] target = new double[y.length];
            for (int i = 0; i < y.length; i++) target[i] = y[i][b];
            r[b] = lsqnonneg(target, a);
        }
        return r;
    }

    private static double vd(double[][] data, double alpha) {
        int n = data[0].length;
        int l = data.length;
        double[][] r = scale(matmul(data, transpose(data)), 1.0 / n);
        double[][] k = covariance(data);
        Eigen er = jacobiEigen(r, 100);
        Eigen ek = jacobiEigen(k, 100);
        double[] eR = er.values.clone();
        double[] eK = ek.values.clone();
        Arrays.sort(eR);
        Arrays.sort(eK);
        reverse(eR);
        reverse(eK);
        int count = 0;
        for (int i = 0; i < l; i++) {
            double diff = eR[i] - eK[i];
            double variance = Math.sqrt(2.0 * (eR[i] * eR[i] + eK[i] * eK[i]) / n);
            double tau = -ppf(alpha, 0.0, variance);
            if (diff > tau) count++;
        }
        return count;
    }

    private static VcaResult vca(double[][] r, int p) {
        int n = r[0].length;
        int l = r.length;
        double[] mean = rowMeans(r);
        double[][] ro = subtractColumn(r, mean);
        Eigen eig = jacobiEigen(scale(matmul(ro, transpose(ro)), 1.0 / n), 120);
        double[][] u = topEigenvectors(eig, Math.min(p, l));
        double[][] xp = matmul(transpose(u), ro);
        double py = sumSquares(r) / n;
        double px = sumSquares(xp) / n + dot(mean, mean);
        double snr = Math.abs(10.0 * Math.log10((px - (p / (double) l) * py) / Math.max(1e-12, py - px)));
        double snrTh = 15.0 + 10.0 * Math.log(p) + 8.0;

        int d;
        double[][] y;
        double[][] x;
        double[][] ud;
        double[] rm;
        if (snr > snrTh) {
            d = p;
            Eigen e2 = jacobiEigen(scale(matmul(r, transpose(r)), 1.0 / n), 120);
            ud = topEigenvectors(e2, d);
            x = matmul(transpose(ud), r);
            double[] uMean = rowMeans(x);
            y = new double[d][n];
            for (int j = 0; j < n; j++) {
                double denom = 0.0;
                for (int i = 0; i < d; i++) denom += x[i][j] * uMean[i];
                for (int i = 0; i < d; i++) y[i][j] = x[i][j] / Math.max(denom, 1e-12);
            }
            rm = new double[l];
        } else {
            d = p - 1;
            rm = rowMeans(r);
            double[][] r0 = subtractColumn(r, rm);
            Eigen e2 = jacobiEigen(scale(matmul(r0, transpose(r0)), 1.0 / n), 120);
            ud = topEigenvectors(e2, d);
            x = matmul(transpose(ud), r0);
            double c = 0.0;
            for (int j = 0; j < n; j++) {
                double s = 0.0;
                for (int i = 0; i < d; i++) s += x[i][j] * x[i][j];
                c = Math.max(c, Math.sqrt(s));
            }
            y = new double[p][n];
            for (int i = 0; i < d; i++) System.arraycopy(x[i], 0, y[i], 0, n);
            Arrays.fill(y[p - 1], c);
        }

        double[][] a = new double[p][p];
        a[p - 1][0] = 1.0;
        int[] indices = new int[p];
        Random random = new Random(1);
        for (int i = 0; i < p; i++) {
            double[] rand = new double[p];
            for (int k = 0; k < p; k++) rand[k] = random.nextDouble();
            double[] f = orthogonalResidual(a, i, rand);
            normalize(f);
            int best = 0;
            double bestVal = -1.0;
            for (int j = 0; j < n; j++) {
                double v = 0.0;
                for (int k = 0; k < p; k++) v += f[k] * y[k][j];
                double av = Math.abs(v);
                if (av > bestVal) {
                    bestVal = av;
                    best = j;
                }
            }
            indices[i] = best;
            for (int k = 0; k < p; k++) a[k][i] = y[k][best];
        }

        double[][] endmembers;
        if (snr > snrTh) {
            double[][] selected = selectColumns(x, indices);
            endmembers = matmul(ud, selected);
        } else {
            double[][] selected = selectColumns(x, indices);
            endmembers = addColumn(matmul(ud, selected), rm);
        }
        return new VcaResult(endmembers, indices);
    }

    private static double[] lsqnonneg(double[] y, double[][] a) {
        int m = y.length;
        int n = a[0].length;
        double[] x = new double[n];
        boolean[] passive = new boolean[n];
        double[] w = matVec(transpose(a), residual(y, a, x));
        double tol = 10 * 2.2204e-16 * maxColumnAbsSum(a) * Math.max(m, n);
        int guard = 0;
        while (maxInactive(w, passive) > tol && guard++ < n * 20) {
            int t = argMaxInactive(w, passive);
            passive[t] = true;
            double[] s = solvePassive(a, y, passive);
            while (minPassive(s, passive) <= 0) {
                double alpha = Double.POSITIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (passive[i] && s[i] <= 0 && x[i] != s[i]) {
                        alpha = Math.min(alpha, x[i] / (x[i] - s[i]));
                    }
                }
                if (!Double.isFinite(alpha)) break;
                for (int i = 0; i < n; i++) x[i] += alpha * (s[i] - x[i]);
                for (int i = 0; i < n; i++) {
                    if (Math.abs(x[i]) < 1e-12) {
                        x[i] = 0;
                        passive[i] = false;
                    }
                }
                s = solvePassive(a, y, passive);
            }
            x = s;
            w = matVec(transpose(a), residual(y, a, x));
        }
        for (int i = 0; i < n; i++) if (x[i] < 0) x[i] = 0;
        return x;
    }

    private static double[] solvePassive(double[][] a, double[] y, boolean[] passive) {
        int n = passive.length;
        int p = 0;
        for (boolean b : passive) if (b) p++;
        double[] out = new double[n];
        if (p == 0) return out;
        int[] idx = new int[p];
        for (int i = 0, j = 0; i < n; i++) if (passive[i]) idx[j++] = i;
        double[][] ata = new double[p][p];
        double[] aty = new double[p];
        for (int row = 0; row < a.length; row++) {
            for (int i = 0; i < p; i++) {
                double ai = a[row][idx[i]];
                aty[i] += ai * y[row];
                for (int j = 0; j < p; j++) ata[i][j] += ai * a[row][idx[j]];
            }
        }
        double[] sol = solveLinear(ata, aty);
        for (int i = 0; i < p; i++) out[idx[i]] = sol[i];
        return out;
    }

    public static float[][][] gaussianDownSample(float[][][] data, int scale) {
        int xdata = data.length;
        int ydata = data[0].length;
        int band = data[0][0].length;
        int hx = xdata / scale;
        int hy = ydata / scale;
        float[][][] out = new float[hx][hy][band];
        double sig = scale / 2.35482;
        double[][] h1 = gaussianFilter2d(scale, scale, sig);
        double[][] h2 = scale % 2 == 0 ? gaussianFilter2d(scale * 2, scale * 2, sig) : gaussianFilter2d(scale * 2 - 1, scale * 2 - 1, sig);
        int offset = scale % 2 == 0 ? scale / 2 : (scale - 1) / 2;
        for (int x = 0; x < hx; x++) {
            for (int y = 0; y < hy; y++) {
                boolean border = x == 0 || x == hx - 1 || y == 0 || y == hy - 1;
                if (border) {
                    for (int dx = 0; dx < scale; dx++) {
                        for (int dy = 0; dy < scale; dy++) {
                            int sx = x * scale + dx;
                            int sy = y * scale + dy;
                            for (int b = 0; b < band; b++) out[x][y][b] += data[sx][sy][b] * h1[dx][dy];
                        }
                    }
                } else {
                    int xs = x * scale - offset;
                    int ys = y * scale - offset;
                    for (int dx = 0; dx < h2.length; dx++) {
                        for (int dy = 0; dy < h2[0].length; dy++) {
                            int sx = xs + dx;
                            int sy = ys + dy;
                            for (int b = 0; b < band; b++) out[x][y][b] += data[sx][sy][b] * h2[dx][dy];
                        }
                    }
                }
            }
        }
        return out;
    }

    private static double[][] gaussianFilter2d(int rows, int cols, double sigma) {
        double[][] h = new double[rows][cols];
        double mr = (rows - 1.0) / 2.0;
        double mc = (cols - 1.0) / 2.0;
        double sum = 0.0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double y = r - mr;
                double x = c - mc;
                h[r][c] = Math.exp(-(x * x + y * y) / (2.0 * sigma * sigma));
                sum += h[r][c];
            }
        }
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) h[r][c] /= sum;
        return h;
    }

    private static int[] rgbIndices(double[] wavelengths, int fallbackR, int fallbackG, int fallbackB) {
        if (wavelengths == null || wavelengths.length == 0) return new int[]{fallbackR, fallbackG, fallbackB};
        return new int[]{nearest(wavelengths, 640), nearest(wavelengths, 550), nearest(wavelengths, 460)};
    }

    private static int nearest(double[] v, double target) {
        int best = 0;
        double bd = Double.POSITIVE_INFINITY;
        for (int i = 0; i < v.length; i++) {
            double d = Math.abs(v[i] - target);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static void printUsage() {
        System.out.println("CnmfFusion usage:");
        System.out.println("  java CnmfFusion --input registered.mat --out fusion_result.mat --png Final_HRHSI_TrueColor.png");
        System.out.println();
        System.out.println("One-shot full pipeline:");
        System.out.println("  java CnmfFusion --hdr D.hdr --img D.img --msi color.bmp --workdir output");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --endmembers 30       Manual endmember count; omit to use VD/VCA.");
        System.out.println("  --mat-version v5|v73");
        System.out.println("  --lr-size 512");
        System.out.println("  --opencv-lib path/to/opencv_java*.dll");
        System.out.println("  --verbose on|off");
    }

    private static class CNMFState {
        final double[][] hyper;
        final double[][] multi;
        double[][] wHyper;
        double[][] hHyper;
        double[][] wMulti;
        double[][] hMulti;
        final double rmseH;
        final double rmseM;

        CNMFState(double[][] hyper, double[][] multi, double[][] wHyper, double[][] hHyper,
                  double[][] wMulti, double[][] hMulti, double rmseH, double rmseM) {
            this.hyper = hyper;
            this.multi = multi;
            this.wHyper = wHyper;
            this.hHyper = hHyper;
            this.wMulti = wMulti;
            this.hMulti = hMulti;
            this.rmseH = rmseH;
            this.rmseM = rmseM;
        }
    }

    private static class IterResult {
        final double[][] wHyper;
        final double[][] hHyper;
        final double[][] wMulti1;
        final double[][] hMulti1;
        final double[][] wMulti2;
        final double[][] hMulti2;
        final double rmseH;
        final double rmseM;

        IterResult(double[][] wHyper, double[][] hHyper, double[][] wMulti1, double[][] hMulti1,
                   double[][] wMulti2, double[][] hMulti2, double rmseH, double rmseM) {
            this.wHyper = wHyper;
            this.hHyper = hHyper;
            this.wMulti1 = wMulti1;
            this.hMulti1 = hMulti1;
            this.wMulti2 = wMulti2;
            this.hMulti2 = hMulti2;
            this.rmseH = rmseH;
            this.rmseM = rmseM;
        }
    }

    private static class VcaResult {
        final double[][] endmembers;
        final int[] indices;

        VcaResult(double[][] endmembers, int[] indices) {
            this.endmembers = endmembers;
            this.indices = indices;
        }
    }

    private static class Eigen {
        final double[] values;
        final double[][] vectors;

        Eigen(double[] values, double[][] vectors) {
            this.values = values;
            this.vectors = vectors;
        }
    }

    private static double[][] cubeToBandPixels(float[][][] cube) {
        int h = cube.length, w = cube[0].length, b = cube[0][0].length;
        double[][] out = new double[b][h * w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int n = y * w + x;
                for (int k = 0; k < b; k++) out[k][n] = cube[y][x][k];
            }
        }
        return out;
    }

    private static double[][] cubeToPixelBands(float[][][] cube) {
        int h = cube.length;
        int w = cube[0].length;
        int b = cube[0][0].length;

        double[][] out = new double[h * w][b];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int n = y * w + x;
                for (int k = 0; k < b; k++) {
                    out[n][k] = cube[y][x][k];
                }
            }
        }

        return out;
    }

    private static float[][][] bandPixelsToCube(double[][] bandPixels, int h, int w, int bands) {
        float[][][] out = new float[h][w][bands];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int n = y * w + x;
                for (int b = 0; b < bands; b++) out[y][x][b] = (float) Math.max(0.0, bandPixels[b][n]);
            }
        }
        return out;
    }

    private static float[][][] abundanceToCube(double[][] abundancePixels, int h, int w, int m) {
        float[][][] out = new float[h][w][m];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) for (int k = 0; k < m; k++) out[y][x][k] = (float) abundancePixels[y * w + x][k];
        return out;
    }

    private static double[][] matmul(double[][] a, double[][] b) {
        int m = a.length, n = a[0].length, p = b[0].length;
        double[][] out = new double[m][p];
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < n; k++) {
                double aik = a[i][k];
                if (aik == 0.0) continue;
                for (int j = 0; j < p; j++) out[i][j] += aik * b[k][j];
            }
        }
        return out;
    }

    private static double[][] transpose(double[][] a) {
        double[][] out = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) out[j][i] = a[i][j];
        return out;
    }

    private static double[][] copy(double[][] a) {
        double[][] out = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) System.arraycopy(a[i], 0, out[i], 0, a[i].length);
        return out;
    }

    private static void copyInto(double[][] src, double[][] dst) {
        for (int i = 0; i < src.length; i++) System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
    }

    private static double[][] fill(int rows, int cols, double value) {
        double[][] out = new double[rows][cols];
        for (double[] row : out) Arrays.fill(row, value);
        return out;
    }

    private static double[][] vstack(double[][] a, double[][] b) {
        double[][] out = new double[a.length + b.length][a[0].length];
        for (int i = 0; i < a.length; i++) System.arraycopy(a[i], 0, out[i], 0, a[i].length);
        for (int i = 0; i < b.length; i++) System.arraycopy(b[i], 0, out[a.length + i], 0, b[i].length);
        return out;
    }

    private static double[][] sliceRows(double[][] a, int from, int to) {
        double[][] out = new double[to - from][a[0].length];
        for (int i = from; i < to; i++) System.arraycopy(a[i], 0, out[i - from], 0, a[i].length);
        return out;
    }

    private static double[][] sliceColumns(double[][] a, int from, int to) {
        double[][] out = new double[a.length][to - from];
        for (int i = 0; i < a.length; i++) System.arraycopy(a[i], from, out[i], 0, to - from);
        return out;
    }

    private static void elemMulDivInPlace(double[][] target, double[][] num, double[][] den) {
        for (int i = 0; i < target.length; i++) for (int j = 0; j < target[0].length; j++) target[i][j] = safeMulDiv(target[i][j], num[i][j], den[i][j]);
    }

    private static double safeMulDiv(double v, double n, double d) {
        if (d <= 1e-30 || !Double.isFinite(d)) return v;
        double out = v * n / d;
        return Double.isFinite(out) ? Math.max(0.0, out) : v;
    }

    private static double squaredError(double[][] a, double[][] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) {
            double d = a[i][j] - b[i][j];
            s += d * d;
        }
        return s;
    }

    private static double relativeImprovement(double oldCost, double newCost) {
        if (oldCost == 0.0) return Double.POSITIVE_INFINITY;
        return (oldCost - newCost) / Math.max(newCost, 1e-30);
    }

    private static void clampNonnegative(double[][] a) {
        for (double[] row : a) for (int i = 0; i < row.length; i++) if (row[i] < 0) row[i] = 0;
    }

    private static double mean(double[][] a) {
        double s = 0.0;
        long n = 0;
        for (double[] row : a) for (double v : row) {
            s += v;
            n++;
        }
        return s / Math.max(1, n);
    }

    private static double[][] vectorToImage(double[] v, int h, int w) {
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) System.arraycopy(v, y * w, out[y], 0, w);
        return out;
    }

    private static double[] imageToVector(double[][] img) {
        double[] out = new double[img.length * img[0].length];
        for (int y = 0; y < img.length; y++) System.arraycopy(img[y], 0, out, y * img[0].length, img[0].length);
        return out;
    }

    private static double[][] zoomBi(double[][] data, int scale) {
        return resize2d(data, data.length * scale, data[0].length * scale);
    }

    private static double[][] resize2d(double[][] src, int newH, int newW) {
        int h = src.length, w = src[0].length;
        double[][] out = new double[newH][newW];
        double ys = (double) h / newH, xs = (double) w / newW;
        for (int y = 0; y < newH; y++) {
            double sy = (y + 0.5) * ys - 0.5;
            int y0 = clamp((int) Math.floor(sy), 0, h - 1);
            int y1 = clamp(y0 + 1, 0, h - 1);
            double wy = Math.max(0, sy - Math.floor(sy));
            for (int x = 0; x < newW; x++) {
                double sx = (x + 0.5) * xs - 0.5;
                int x0 = clamp((int) Math.floor(sx), 0, w - 1);
                int x1 = clamp(x0 + 1, 0, w - 1);
                double wx = Math.max(0, sx - Math.floor(sx));
                out[y][x] = (1 - wy) * ((1 - wx) * src[y0][x0] + wx * src[y0][x1]) + wy * ((1 - wx) * src[y1][x0] + wx * src[y1][x1]);
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[][] covariance(double[][] data) {
        int l = data.length, n = data[0].length;
        double[] mean = rowMeans(data);
        double[][] out = new double[l][l];
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < l; i++) {
                double vi = data[i][k] - mean[i];
                for (int j = i; j < l; j++) {
                    out[i][j] += vi * (data[j][k] - mean[j]);
                }
            }
        }
        double den = Math.max(1, n - 1);
        for (int i = 0; i < l; i++) for (int j = i; j < l; j++) {
            out[i][j] /= den;
            out[j][i] = out[i][j];
        }
        return out;
    }

    private static Eigen jacobiEigen(double[][] input, int maxIter) {
        int n = input.length;
        double[][] a = copy(input);
        double[][] v = new double[n][n];
        for (int i = 0; i < n; i++) v[i][i] = 1.0;
        for (int iter = 0; iter < maxIter * n * n; iter++) {
            int p = 0, q = 1;
            double max = 0.0;
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                double av = Math.abs(a[i][j]);
                if (av > max) {
                    max = av;
                    p = i;
                    q = j;
                }
            }
            if (max < 1e-9) break;
            double theta = 0.5 * Math.atan2(2 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(theta), s = Math.sin(theta);
            for (int k = 0; k < n; k++) {
                double apk = a[p][k], aqk = a[q][k];
                a[p][k] = c * apk - s * aqk;
                a[q][k] = s * apk + c * aqk;
            }
            for (int k = 0; k < n; k++) {
                double akp = a[k][p], akq = a[k][q];
                a[k][p] = c * akp - s * akq;
                a[k][q] = s * akp + c * akq;
            }
            for (int k = 0; k < n; k++) {
                double vkp = v[k][p], vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = a[i][i];
        return new Eigen(values, v);
    }

    private static double[][] topEigenvectors(Eigen e, int k) {
        Integer[] idx = new Integer[e.values.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble((Integer i) -> e.values[i]).reversed());
        double[][] out = new double[e.vectors.length][k];
        for (int c = 0; c < k; c++) {
            int src = idx[c];
            for (int r = 0; r < e.vectors.length; r++) out[r][c] = e.vectors[r][src];
        }
        return out;
    }

    private static double[] rowMeans(double[][] a) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            double s = 0.0;
            for (double v : a[i]) s += v;
            out[i] = s / a[i].length;
        }
        return out;
    }

    private static double[][] subtractColumn(double[][] a, double[] col) {
        double[][] out = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) out[i][j] = a[i][j] - col[i];
        return out;
    }

    private static double[][] addColumn(double[][] a, double[] col) {
        double[][] out = copy(a);
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) out[i][j] += col[i];
        return out;
    }

    private static double[][] selectColumns(double[][] a, int[] indices) {
        double[][] out = new double[a.length][indices.length];
        for (int c = 0; c < indices.length; c++) for (int r = 0; r < a.length; r++) out[r][c] = a[r][indices[c]];
        return out;
    }

    private static double[][] scale(double[][] a, double s) {
        double[][] out = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) out[i][j] = a[i][j] * s;
        return out;
    }

    private static double sumSquares(double[][] a) {
        double s = 0.0;
        for (double[] row : a) for (double v : row) s += v * v;
        return s;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static void normalize(double[] a) {
        double n = Math.sqrt(dot(a, a));
        if (n == 0) return;
        for (int i = 0; i < a.length; i++) a[i] /= n;
    }

    private static double[] orthogonalResidual(double[][] basis, int usedCols, double[] w) {
        if (usedCols == 0) return w.clone();
        double[][] b = new double[basis.length][usedCols];
        for (int c = 0; c < usedCols; c++) for (int r = 0; r < basis.length; r++) b[r][c] = basis[r][c];
        double[][] bt = transpose(b);
        double[][] inv = inverse(matmul(bt, b));
        double[] coeff = matVec(matmul(inv, bt), w);
        double[] proj = matVec(b, coeff);
        double[] out = new double[w.length];
        for (int i = 0; i < w.length; i++) out[i] = w[i] - proj[i];
        return out;
    }

    private static double[][] inverse(double[][] a) {
        int n = a.length;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) if (Math.abs(aug[i][p]) > Math.abs(aug[max][p])) max = i;
            double[] tmp = aug[p]; aug[p] = aug[max]; aug[max] = tmp;
            double pivot = Math.abs(aug[p][p]) < 1e-12 ? 1e-12 : aug[p][p];
            for (int j = 0; j < 2 * n; j++) aug[p][j] /= pivot;
            for (int i = 0; i < n; i++) if (i != p) {
                double f = aug[i][p];
                for (int j = 0; j < 2 * n; j++) aug[i][j] -= f * aug[p][j];
            }
        }
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(aug[i], n, out[i], 0, n);
        return out;
    }

    private static double[] solveLinear(double[][] a, double[] b) {
        int n = a.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) if (Math.abs(aug[i][p]) > Math.abs(aug[max][p])) max = i;
            double[] tmp = aug[p]; aug[p] = aug[max]; aug[max] = tmp;
            double pivot = Math.abs(aug[p][p]) < 1e-12 ? 1e-12 : aug[p][p];
            for (int j = p; j <= n; j++) aug[p][j] /= pivot;
            for (int i = 0; i < n; i++) if (i != p) {
                double f = aug[i][p];
                for (int j = p; j <= n; j++) aug[i][j] -= f * aug[p][j];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = aug[i][n];
        return x;
    }

    private static double[] matVec(double[][] a, double[] x) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            double s = 0.0;
            for (int j = 0; j < x.length; j++) s += a[i][j] * x[j];
            out[i] = s;
        }
        return out;
    }

    private static double[] residual(double[] y, double[][] a, double[] x) {
        double[] ax = matVec(a, x);
        double[] r = new double[y.length];
        for (int i = 0; i < y.length; i++) r[i] = y[i] - ax[i];
        return r;
    }

    private static double maxColumnAbsSum(double[][] a) {
        double max = 0.0;
        for (int c = 0; c < a[0].length; c++) {
            double s = 0.0;
            for (double[] doubles : a) s += Math.abs(doubles[c]);
            max = Math.max(max, s);
        }
        return max;
    }

    private static double maxInactive(double[] w, boolean[] passive) {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < w.length; i++) if (!passive[i]) max = Math.max(max, w[i]);
        return max;
    }

    private static int argMaxInactive(double[] w, boolean[] passive) {
        int idx = 0;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < w.length; i++) if (!passive[i] && w[i] > max) {
            max = w[i];
            idx = i;
        }
        return idx;
    }

    private static double minPassive(double[] s, boolean[] passive) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < s.length; i++) if (passive[i]) min = Math.min(min, s[i]);
        return min;
    }

    private static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    private static double ppf(double p, double mu, double sigma) {
        return mu + sigma * Math.sqrt(2.0) * erfInv(2.0 * p - 1.0);
    }

    private static double erfInv(double x) {
        double a = 0.147;
        double ln = Math.log(1.0 - x * x);
        double first = 2.0 / (Math.PI * a) + ln / 2.0;
        double second = ln / a;
        return Math.copySign(Math.sqrt(Math.sqrt(first * first - second) - first), x);
    }
}
