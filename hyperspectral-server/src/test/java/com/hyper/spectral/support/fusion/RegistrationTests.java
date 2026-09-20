package com.hyper.spectral.support.fusion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationTests {

    @BeforeAll
    static void loadOpenCv() {
        Dataset.loadOpenCv(null);
    }

    @Test
    void shouldRecoverKnownTranslationAndProduceHysureScale() {
        int size = 128;
        int bands = 6;
        int shiftX = 6;
        int shiftY = 4;
        float[][] reference = createTexturedImage(size, size);
        float[][] moving = translate(reference, shiftX, shiftY);
        float[][][] lowHsi = toHyperspectralCube(reference, bands);
        float[][][] highMsi = toTwoTimesRgbCube(moving);

        Dataset.ImagePair pair = new Dataset.ImagePair(lowHsi, highMsi, new double[0], null);
        Registration.RegisteredPair result = Registration.register(pair, 64);

        assertEquals(64, result.lrhsi.length);
        assertEquals(64, result.lrhsi[0].length);
        assertEquals(128, result.hrmsi.length);
        assertEquals(128, result.hrmsi[0].length);
        assertTrue(result.goodMatches >= 4);
        assertTrue(result.inliers >= 3);
        assertTrue(result.inlierRmse <= 3.0d);
        assertEquals(-shiftX, result.affineLow[0][2], 1.5d);
        assertEquals(-shiftY, result.affineLow[1][2], 1.5d);
    }

    @Test
    void shouldFindLargestAllValidRectangle() {
        boolean[][] mask = new boolean[5][7];
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 4; x++) {
                mask[y][x] = true;
            }
        }
        mask[0][6] = true;

        Registration.CropBox box = Registration.largestValidRectangle(mask);

        assertEquals(1, box.x0);
        assertEquals(1, box.y0);
        assertEquals(5, box.x1);
        assertEquals(4, box.y1);
    }

    @Test
    void shouldApplySymmetricCropMargin() {
        Registration.CropBox box = Registration.applyMargin(
                new Registration.CropBox(0, 0, 20, 16),
                5
        );

        assertEquals(5, box.x0);
        assertEquals(5, box.y0);
        assertEquals(15, box.x1);
        assertEquals(11, box.y1);
        assertThrows(
                IllegalStateException.class,
                () -> Registration.applyMargin(new Registration.CropBox(0, 0, 8, 8), 5)
        );
    }

    private static float[][] createTexturedImage(int height, int width) {
        float[][] image = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int hash = x * 73 + y * 151 + x * y * 17 + (x ^ y) * 29;
                float value = hash & 0xff;
                if ((x / 12 + y / 10) % 2 == 0) {
                    value = Math.min(255.0f, value + 55.0f);
                }
                image[y][x] = value;
            }
        }
        return image;
    }

    private static float[][] translate(float[][] source, int shiftX, int shiftY) {
        int height = source.length;
        int width = source[0].length;
        float[][] translated = new float[height][width];
        for (int y = shiftY; y < height; y++) {
            for (int x = shiftX; x < width; x++) {
                translated[y][x] = source[y - shiftY][x - shiftX];
            }
        }
        return translated;
    }

    private static float[][][] toHyperspectralCube(float[][] image, int bands) {
        float[][][] cube = new float[image.length][image[0].length][bands];
        for (int y = 0; y < image.length; y++) {
            for (int x = 0; x < image[0].length; x++) {
                for (int band = 0; band < bands; band++) {
                    cube[y][x][band] = image[y][x] + band;
                }
            }
        }
        return cube;
    }

    private static float[][][] toTwoTimesRgbCube(float[][] image) {
        int height = image.length * 2;
        int width = image[0].length * 2;
        float[][][] cube = new float[height][width][3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = image[y / 2][x / 2];
                cube[y][x][0] = value;
                cube[y][x][1] = value;
                cube[y][x][2] = value;
            }
        }
        return cube;
    }
}
