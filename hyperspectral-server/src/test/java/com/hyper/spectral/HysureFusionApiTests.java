package com.hyper.spectral;

import com.hyper.spectral.support.fusion.Dataset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HysureFusionApiTests {

    private static final Path TEST_ROOT = createTestRoot();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        Path runner = Path.of(System.getProperty("user.dir"), "..", "Hysure-python",
                "hysure_runner.py").normalize().toAbsolutePath();
        registry.add("fusion.compatibility.result-dir", () -> TEST_ROOT.toString());
        registry.add("fusion.miae.enabled", () -> "false");
        registry.add("fusion.hysure.enabled", () -> "true");
        registry.add("fusion.hysure.conda-environment", () -> "htd");
        registry.add("fusion.hysure.runner-path", runner::toString);
        registry.add("fusion.hysure.subspace-dimension", () -> "4");
        registry.add("fusion.hysure.iterations", () -> "2");
        registry.add("fusion.hysure.blur-support", () -> "6");
        registry.add("fusion.hysure.output-block-rows", () -> "8");
        registry.add("fusion.hysure.timeout-seconds", () -> "120");
        registry.add("detection.compatibility.random-delay-min-seconds", () -> "0");
        registry.add("detection.compatibility.random-delay-max-seconds", () -> "0");
        registry.add("detection.compatibility.default-algorithm", () -> "hcem");
    }

    @Test
    void shouldFuseMatAndExposeSpectrumThroughLegacyApi() throws Exception {
        Path inputFile = TEST_ROOT.resolve("api_input.mat");
        Dataset.MatIO.write(inputFile, createInput(), Dataset.MatVersion.V5);
        MockMultipartFile upload = new MockMultipartFile(
                "file1",
                "api_input.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(inputFile)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty())
                .andExpect(jsonPath("$.fusion_info.bands").value(80))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(32))
                .andExpect(jsonPath("$.fusion_info.fusion_height").value(32));

        mockMvc.perform(get("/api/fusion-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(32))
                .andExpect(jsonPath("$.height").value(32))
                .andExpect(jsonPath("$.bands").value(80))
                .andExpect(jsonPath("$.data_type").value("float32"));

        mockMvc.perform(get("/api/fusion-spectrum").param("x", "5").param("y", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spectrum[0]").isNumber())
                .andExpect(jsonPath("$.spectrum[79]").isNumber());
    }

    @Test
    void shouldFuseSeparateLegacyHyperspectralMatAndRgbImage() throws Exception {
        Dataset.MatData input = createInput();
        Dataset.MatData legacyMat = new Dataset.MatData();
        legacyMat.cubes.put("hyperspectral_data", input.cubes.get(Dataset.LRHSI));
        Path inputFile = TEST_ROOT.resolve("legacy_input.mat");
        Dataset.MatIO.write(inputFile, legacyMat, Dataset.MatVersion.V5);
        MockMultipartFile matUpload = new MockMultipartFile(
                "file1",
                "legacy_input.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(inputFile)
        );
        MockMultipartFile imageUpload = new MockMultipartFile(
                "file2",
                "legacy_input.png",
                MediaType.IMAGE_PNG_VALUE,
                encodeRgb(input.cubes.get(Dataset.HRMSI))
        );

        mockMvc.perform(multipart("/start_fusion_1").file(matUpload).file(imageUpload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty())
                .andExpect(jsonPath("$.fusion_info.bands").value(80))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(32));
    }

    private static Path createTestRoot() {
        try {
            return Files.createTempDirectory("hysure_api_test_");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Dataset.MatData createInput() {
        int lowSize = 16;
        int highSize = 32;
        int bands = 80;
        float[][][] low = new float[lowSize][lowSize][bands];
        float[][][] high = new float[highSize][highSize][3];
        Random random = new Random(29L);
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

    private byte[] encodeRgb(float[][][] cube) throws Exception {
        BufferedImage image = new BufferedImage(cube[0].length, cube.length,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < cube.length; y++) {
            for (int x = 0; x < cube[0].length; x++) {
                int red = Math.max(0, Math.min(255, Math.round(cube[y][x][0] * 255.0f)));
                int green = Math.max(0, Math.min(255, Math.round(cube[y][x][1] * 255.0f)));
                int blue = Math.max(0, Math.min(255, Math.round(cube[y][x][2] * 255.0f)));
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
