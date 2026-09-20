package com.hyper.spectral.support.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyper.spectral.config.fusion.HysureFusionProperties;
import com.hyper.spectral.support.adapter.HysurePythonAdapter.HysureFusionResult;
import com.hyper.spectral.support.fusion.Dataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HysurePythonAdapterTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldFuseAndReadSpectrumThroughHtdEnvironment() throws Exception {
        Path runner = Path.of(System.getProperty("user.dir"), "..", "Hysure-python",
                "hysure_runner.py").normalize().toAbsolutePath();
        assertTrue(Files.isRegularFile(runner));

        HysureFusionProperties properties = new HysureFusionProperties();
        properties.setRunnerPath(runner.toString());
        properties.setCondaEnvironment("htd");
        properties.setSubspaceDimension(4);
        properties.setIterations(2);
        properties.setBlurSupport(6);
        properties.setOutputBlockRows(8);
        properties.setTimeoutSeconds(120);
        properties.setSpectrumTimeoutMillis(30000);
        HysurePythonAdapter adapter = new HysurePythonAdapter(new ObjectMapper(), properties);
        try {
            Path inputFile = temporaryDirectory.resolve("input.mat");
            Dataset.MatIO.write(inputFile, createInput(), Dataset.MatVersion.V5);
            HysureFusionResult result = adapter.fuse(
                    inputFile, temporaryDirectory.resolve("output"));

            assertEquals(32, result.width);
            assertEquals(32, result.height);
            assertEquals(80, result.bands);
            assertTrue(Files.isRegularFile(result.getFusionPath()));
            assertTrue(Files.isRegularFile(result.getPreviewPath()));

            List<Double> firstSpectrum = adapter.readSpectrum(result.getFusionPath(), 5, 7);
            long firstWorkerPid = adapter.currentSpectrumWorkerPid();
            List<Double> secondSpectrum = adapter.readSpectrum(result.getFusionPath(), 6, 7);
            long secondWorkerPid = adapter.currentSpectrumWorkerPid();

            assertEquals(80, firstSpectrum.size());
            assertEquals(80, secondSpectrum.size());
            assertTrue(firstSpectrum.stream().allMatch(Double::isFinite));
            assertTrue(secondSpectrum.stream().allMatch(Double::isFinite));
            assertTrue(firstWorkerPid > 0L);
            assertEquals(firstWorkerPid, secondWorkerPid);
        } finally {
            adapter.close();
        }
    }

    private Dataset.MatData createInput() {
        int lowSize = 16;
        int highSize = 32;
        int bands = 80;
        float[][][] low = new float[lowSize][lowSize][bands];
        float[][][] high = new float[highSize][highSize][3];
        Random random = new Random(23L);
        for (int y = 0; y < lowSize; y++) {
            for (int x = 0; x < lowSize; x++) {
                for (int band = 0; band < bands; band++) {
                    low[y][x][band] = 0.05f + random.nextFloat() * 0.9f;
                }
            }
        }
        int[][] ranges = {{44, 76}, {23, 54}, {7, 35}};
        for (int y = 0; y < highSize; y++) {
            for (int x = 0; x < highSize; x++) {
                int lowY = y / 2;
                int lowX = x / 2;
                for (int channel = 0; channel < 3; channel++) {
                    int first = ranges[channel][0];
                    int last = ranges[channel][1];
                    double sum = 0.0d;
                    for (int band = first; band < last; band++) {
                        sum += low[lowY][lowX][band];
                    }
                    high[y][x][channel] = (float) (sum / (last - first)
                            + random.nextGaussian() * 0.001d);
                }
            }
        }
        Dataset.MatData data = new Dataset.MatData();
        data.cubes.put(Dataset.LRHSI, low);
        data.cubes.put(Dataset.HRMSI, high);
        return data;
    }
}
