package com.hyper.spectral.support.fusion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetMatIoTests {
    @TempDir
    Path tempDirectory;

    @Test
    void shouldStreamMatV5CubeAndVectorWithoutChangingValues() throws Exception {
        float[][][] cube = new float[2][3][4];
        for (int y = 0; y < cube.length; y++) {
            for (int x = 0; x < cube[y].length; x++) {
                for (int band = 0; band < cube[y][x].length; band++) {
                    cube[y][x][band] = (float) (y * 100 + x * 10 + band + 0.25d);
                }
            }
        }
        double[] wavelengths = new double[]{450.5d, 550.25d, 650.75d, 850.0d};
        Dataset.MatData source = new Dataset.MatData();
        source.cubes.put("HRHSI", cube);
        source.vectors.put(Dataset.WAVELENGTHS, wavelengths);
        Path output = tempDirectory.resolve("fusion.mat");

        Dataset.MatIO.write(output, source, Dataset.MatVersion.V5);

        assertTrue(Files.size(output) > 128L);
        assertNoTemporaryFiles();
        Dataset.MatData loaded = Dataset.MatIO.read(output);
        float[][][] loadedCube = loaded.requiredCube("HRHSI");
        assertEquals(cube.length, loadedCube.length);
        for (int y = 0; y < cube.length; y++) {
            for (int x = 0; x < cube[y].length; x++) {
                assertArrayEquals(cube[y][x], loadedCube[y][x]);
            }
        }
        assertArrayEquals(wavelengths, loaded.vectors.get(Dataset.WAVELENGTHS));
    }

    @Test
    void shouldRejectJaggedCubeWithoutPublishingFinalMat() throws Exception {
        float[][][] cube = new float[2][][];
        cube[0] = new float[][]{{1.0f, 2.0f}};
        cube[1] = new float[][]{{3.0f}};
        Dataset.MatData source = new Dataset.MatData();
        source.cubes.put("HRHSI", cube);
        Path output = tempDirectory.resolve("invalid.mat");

        assertThrows(Exception.class,
                () -> Dataset.MatIO.write(output, source, Dataset.MatVersion.V5));

        assertFalse(Files.exists(output));
        assertNoTemporaryFiles();
    }

    private void assertNoTemporaryFiles() throws Exception {
        try (Stream<Path> files = Files.list(tempDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
