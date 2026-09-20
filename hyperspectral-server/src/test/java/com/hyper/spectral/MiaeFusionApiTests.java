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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MiaeFusionApiTests {

    private static final Path TEST_ROOT = createTestRoot();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        Path runner = Path.of(System.getProperty("user.dir"), "..",
                "miae_fusion_runner.py").normalize().toAbsolutePath();
        registry.add("fusion.compatibility.result-dir", () -> TEST_ROOT.toString());
        registry.add("fusion.miae.enabled", () -> "true");
        registry.add("fusion.miae.conda-environment", () -> "htd");
        registry.add("fusion.miae.runner-path", runner::toString);
        registry.add("fusion.miae.device", () -> "cpu");
        registry.add("fusion.miae.blind-iterations", () -> "2");
        registry.add("fusion.miae.fusion-iterations", () -> "2");
        registry.add("fusion.miae.batch-size", () -> "2");
        registry.add("fusion.miae.patch-size", () -> "8");
        registry.add("fusion.miae.endmembers", () -> "4");
        registry.add("fusion.miae.stages", () -> "2");
        registry.add("fusion.miae.tile-size", () -> "16");
        registry.add("fusion.miae.log-every", () -> "1");
        registry.add("fusion.miae.timeout-seconds", () -> "180");
        registry.add("fusion.hysure.enabled", () -> "false");
    }

    @Test
    void shouldFuseMatAndExposeSpectrumThroughLegacyApi() throws Exception {
        Path inputFile = TEST_ROOT.resolve("miae_api_input.mat");
        Dataset.MatIO.write(inputFile, createInput(), Dataset.MatVersion.V5);
        MockMultipartFile upload = new MockMultipartFile(
                "file1",
                "miae_api_input.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(inputFile)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty())
                .andExpect(jsonPath("$.fusion_info.bands").value(8))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(16))
                .andExpect(jsonPath("$.fusion_info.fusion_height").value(16));

        mockMvc.perform(get("/api/fusion-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(16))
                .andExpect(jsonPath("$.height").value(16))
                .andExpect(jsonPath("$.bands").value(8));

        mockMvc.perform(get("/api/fusion-spectrum").param("x", "5").param("y", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spectrum[0]").isNumber())
                .andExpect(jsonPath("$.spectrum[7]").isNumber());
    }

    private static Path createTestRoot() {
        try {
            return Files.createTempDirectory("miae_api_test_");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Dataset.MatData createInput() {
        int lowSize = 8;
        int highSize = 16;
        int bands = 8;
        float[][][] low = new float[lowSize][lowSize][bands];
        float[][][] high = new float[highSize][highSize][3];
        Random random = new Random(37L);
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
                    high[y][x][channel] = low[y / 2][x / 2][channel * 2]
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
