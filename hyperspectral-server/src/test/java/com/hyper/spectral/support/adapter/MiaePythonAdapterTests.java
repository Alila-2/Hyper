package com.hyper.spectral.support.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyper.spectral.config.fusion.MiaeFusionProperties;
import com.hyper.spectral.support.adapter.MiaePythonAdapter.MiaeFusionResult;
import com.hyper.spectral.support.fusion.Dataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiaePythonAdapterTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldFuseAndReuseMemoryMappedSpectrumWorker() throws Exception {
        Path runner = Path.of(System.getProperty("user.dir"), "..",
                "miae_fusion_runner.py").normalize().toAbsolutePath();
        assertTrue(Files.isRegularFile(runner));

        MiaeFusionProperties properties = new MiaeFusionProperties();
        properties.setRunnerPath(runner.toString());
        properties.setCondaEnvironment("htd");
        properties.setDevice("cpu");
        properties.setBlindIterations(2);
        properties.setFusionIterations(2);
        properties.setBatchSize(2);
        properties.setPatchSize(8);
        properties.setEndmembers(4);
        properties.setStages(2);
        properties.setTileSize(16);
        properties.setLogEvery(1);
        properties.setTimeoutSeconds(180);
        properties.setSpectrumTimeoutMillis(30000);

        MiaePythonAdapter adapter = new MiaePythonAdapter(new ObjectMapper(), properties);
        try {
            Path inputFile = temporaryDirectory.resolve("input.mat");
            Dataset.MatIO.write(inputFile, createInput(), Dataset.MatVersion.V5);
            MiaeFusionResult result = adapter.fuse(inputFile,
                    temporaryDirectory.resolve("output"));

            assertEquals(16, result.width);
            assertEquals(16, result.height);
            assertEquals(8, result.bands);
            assertTrue(Files.isRegularFile(result.getFusionPath()));
            assertTrue(Files.isRegularFile(result.getMatPath()));
            assertTrue(Files.isRegularFile(result.getPreviewPath()));

            List<Double> firstSpectrum = adapter.readSpectrum(result.getFusionPath(), 5, 7);
            long firstWorkerPid = adapter.currentSpectrumWorkerPid();
            List<Double> secondSpectrum = adapter.readSpectrum(result.getFusionPath(), 6, 7);
            long secondWorkerPid = adapter.currentSpectrumWorkerPid();

            assertEquals(8, firstSpectrum.size());
            assertEquals(8, secondSpectrum.size());
            assertTrue(firstSpectrum.stream().allMatch(Double::isFinite));
            assertTrue(secondSpectrum.stream().allMatch(Double::isFinite));
            assertTrue(firstWorkerPid > 0L);
            assertEquals(firstWorkerPid, secondWorkerPid);
        } finally {
            adapter.close();
        }
    }

    private Dataset.MatData createInput() {
        int lowSize = 8;
        int highSize = 16;
        int bands = 8;
        float[][][] low = new float[lowSize][lowSize][bands];
        float[][][] high = new float[highSize][highSize][3];
        Random random = new Random(31L);
        for (int y = 0; y < lowSize; y++) {
            for (int x = 0; x < lowSize; x++) {
                for (int band = 0; band < bands; band++) {
                    low[y][x][band] = 0.05f + random.nextFloat() * 0.9f;
                }
            }
        }
        for (int y = 0; y < highSize; y++) {
            for (int x = 0; x < highSize; x++) {
                for (int channel = 0; channel < 3; channel++) {
                    int lowY = y / 2;
                    int lowX = x / 2;
                    high[y][x][channel] = low[lowY][lowX][channel * 2]
                            + random.nextFloat() * 0.001f;
                }
            }
        }
        Dataset.MatData data = new Dataset.MatData();
        data.cubes.put(Dataset.LRHSI, low);
        data.cubes.put(Dataset.HRMSI, high);
        return data;
    }
}
