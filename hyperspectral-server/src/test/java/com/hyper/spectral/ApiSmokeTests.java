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
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTests {

    private static final Path VISUALIZATION_ROOT = prepareVisualizationRoot();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("visualization.compatibility.hdr-root-dir", () -> VISUALIZATION_ROOT.toString());
        registry.add("visualization.compatibility.preview-dir", () -> VISUALIZATION_ROOT.resolve("_previews").toString());
        registry.add("fusion.compatibility.search-root-dir", () -> VISUALIZATION_ROOT.toString());
        registry.add("fusion.compatibility.result-dir", () -> VISUALIZATION_ROOT.resolve("_fusion-results").toString());
        registry.add("fusion.compatibility.registration-lr-size", () -> "16");
        registry.add("fusion.compatibility.registration-fallback-enabled", () -> "true");
        registry.add("fusion.compatibility.keep-intermediate-mat", () -> "true");
        registry.add("fusion.miae.enabled", () -> "false");
        registry.add("fusion.hysure.enabled", () -> "false");
        registry.add("acquisition.compatibility.mock-root-dir", () -> VISUALIZATION_ROOT.resolve("_mock-capture").toString());
        registry.add("detection.compatibility.random-delay-min-seconds", () -> "0");
        registry.add("detection.compatibility.random-delay-max-seconds", () -> "0");
        registry.add("detection.compatibility.default-algorithm", () -> "hcem");
    }

    @Test
    void shouldReturnSystemOverview() throws Exception {
        mockMvc.perform(get("/api/v1/system/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.serviceStatus").value("RUNNING"));
    }

    @Test
    void shouldReturnAcquisitionTaskPage() throws Exception {
        mockMvc.perform(get("/api/v1/acquisition/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].taskId").value("ACQ-20260325-001"));
    }

    @Test
    void shouldReturnCaptureStatusForLegacyFrontend() throws Exception {
        mockMvc.perform(post("/api/check-capture-complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.latest_hdr_before").isNotEmpty())
                .andExpect(jsonPath("$.latest_dir").isNotEmpty());
    }

    @Test
    void shouldWaitForCaptureCompleteForLegacyFrontend() throws Exception {
        mockMvc.perform(post("/api/wait-for-capture-complete")
                        .contentType("application/json")
                        .content("{\"latest_hdr_before\":\"\",\"latest_dir\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.latest_hdr").isNotEmpty())
                .andExpect(jsonPath("$.latest_dir").isNotEmpty());
    }

    @Test
    void shouldReturnPreloadPayloadForLegacyFrontend() throws Exception {
        mockMvc.perform(post("/api/pre_load-hyperspectral-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.original_image").isNotEmpty())
                .andExpect(jsonPath("$.pseudo_color_image").isNotEmpty())
                .andExpect(jsonPath("$.total_bands").value(320))
                .andExpect(jsonPath("$.visualization_bands[0]").value(66));
    }

    @Test
    void shouldReturnVisualizationPayloadForLegacyFrontend() throws Exception {
        mockMvc.perform(post("/api/load-hyperspectral-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.original_image").isNotEmpty())
                .andExpect(jsonPath("$.pseudo_color_image").isNotEmpty())
                .andExpect(jsonPath("$.total_bands").value(70))
                .andExpect(jsonPath("$.visualization_bands[0]").value(66));
    }

    @Test
    void shouldReturnSpectrumForLegacyVisualizationPage() throws Exception {
        mockMvc.perform(post("/api/load-hyperspectral-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/spectrum").param("x", "39").param("y", "35"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spectrum[0]").isNumber())
                .andExpect(jsonPath("$.spectrum[69]").isNumber());
    }

    @Test
    void shouldLoadVisualizationDataFromUploadedHdr() throws Exception {
        MockMultipartFile hdrFile = new MockMultipartFile(
                "hdr_file",
                "D_20251010_131632.hdr",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock hdr".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/load-hyperspectral-data/upload").file(hdrFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.original_image").isNotEmpty())
                .andExpect(jsonPath("$.total_bands").value(70))
                .andExpect(jsonPath("$.visualization_bands[1]").value(44));
    }

    @Test
    void shouldReturnDifferentVisualizationImagesForDifferentHdrFiles() throws Exception {
        MvcResult latestResult = mockMvc.perform(post("/api/load-hyperspectral-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MockMultipartFile hdrFile = new MockMultipartFile(
                "hdr_file",
                "D_20251010_131632.hdr",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock hdr".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult selectedResult = mockMvc.perform(multipart("/api/load-hyperspectral-data/upload").file(hdrFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String latestPayload = latestResult.getResponse().getContentAsString();
        String selectedPayload = selectedResult.getResponse().getContentAsString();
        assertNotEquals(latestPayload, selectedPayload);
    }

    @Test
    void shouldGenerateCanonicalVisualizationArtifactsBesideHdr() throws Exception {
        mockMvc.perform(post("/api/load-hyperspectral-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Path latestDirectory = VISUALIZATION_ROOT.resolve("20251011");
        assertTrue(Files.exists(latestDirectory.resolve("D_20251011_141500.bmp")));
        assertTrue(Files.exists(latestDirectory.resolve("D_20251011_141500.mat")));
        assertTrue(Files.exists(latestDirectory.resolve("D_20251011_141500.png")));
        assertTrue(Files.exists(latestDirectory.resolve("D_20251011_141500.meta.json")));
    }

    @Test
    void shouldReturnMatPreviewForLegacyPages() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "mat_file",
                "scene_320.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/get_mat_preview")
                        .file(matFile)
                        .param("target_var", "hyperspectral_data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview_url").isNotEmpty())
                .andExpect(jsonPath("$.mat_info.resolution").value("256×256"))
                .andExpect(jsonPath("$.mat_info.bands").value(320));
    }

    @Test
    void shouldReturnMatchedPreviewForGeneratedMat() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "mat_file",
                "D_20251011_141500.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/get_mat_preview")
                        .file(matFile)
                        .param("target_var", "hyperspectral_data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview_url").isNotEmpty())
                .andExpect(jsonPath("$.mat_info.resolution").value("40×36"))
                .andExpect(jsonPath("$.mat_info.bands").value(70));
    }

    @Test
    void shouldReturnPngByMatPayloadForFusionPage() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "scene_320.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/get_png_by_mat").file(matFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.png_base64").isNotEmpty())
                .andExpect(jsonPath("$.png_info.resolution").isNotEmpty())
                .andExpect(jsonPath("$.filename").isNotEmpty());
    }

    @Test
    void shouldReturnMatchedBmpForGeneratedMat() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "D_20251010_131632.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/get_png_by_mat").file(matFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.png_base64").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("D_20251010_131632.bmp"));
    }

    @Test
    void shouldStartFusionForLegacyFrontend() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file1",
                "scene_320.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile imageFile = new MockMultipartFile(
                "file2",
                "scene.bmp",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock image payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(matFile).file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty())
                .andExpect(jsonPath("$.fusion_info.bands").value(320))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(512));
    }

    @Test
    void shouldReturnFusionInfoAndSpectrumAfterFusion() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file1",
                "scene_320.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock mat payload".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile imageFile = new MockMultipartFile(
                "file2",
                "scene.bmp",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock image payload".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(matFile).file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty());

        mockMvc.perform(get("/api/fusion-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(512))
                .andExpect(jsonPath("$.height").value(512))
                .andExpect(jsonPath("$.bands").value(320));

        mockMvc.perform(get("/api/fusion-spectrum").param("x", "10").param("y", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spectrum[0]").isNumber())
                .andExpect(jsonPath("$.spectrum[319]").isNumber());
    }

    @Test
    void shouldPreviewUploadedImageForDetectionPage() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "target.png",
                MediaType.IMAGE_PNG_VALUE,
                createDetectionImage()
        );

        mockMvc.perform(multipart("/upload_and_preview").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.image").isNotEmpty())
                .andExpect(jsonPath("$.shape[0]").value(20))
                .andExpect(jsonPath("$.shape[1]").value(24))
                .andExpect(jsonPath("$.shape[2]").value(3));
    }

    @Test
    void shouldPreviewCompressedHyperspectralMatWithSciPy() throws Exception {
        Path example = Path.of(System.getProperty("user.dir"), "..", "example",
                "1km_ta_downsampled.mat").normalize();
        assertTrue(Files.exists(example));
        byte[] exampleBytes = Files.readAllBytes(example);
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                example.getFileName().toString(),
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                exampleBytes
        );

        mockMvc.perform(multipart("/upload_and_preview").file(matFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.image").isNotEmpty())
                .andExpect(jsonPath("$.shape[0]").value(512))
                .andExpect(jsonPath("$.shape[1]").value(544))
                .andExpect(jsonPath("$.shape[2]").value(64));

        MockMultipartFile detectionFile = new MockMultipartFile(
                "file",
                example.getFileName().toString(),
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                exampleBytes
        );
        mockMvc.perform(multipart("/fusion/detect_real3")
                        .file(detectionFile)
                        .param("targets", "[\"d1\",\"d12\"]")
                        .param("algorithm", "hcem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.algorithm").value("hcem"))
                .andExpect(jsonPath("$.target_statistics.d12").exists())
                .andExpect(jsonPath("$.target_statistics.d1").exists());
    }

    @Test
    void shouldDetectTargetsForLegacyDetectionPage() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "target.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                createDetectionMat()
        );

        mockMvc.perform(multipart("/fusion/detect_real3")
                        .file(matFile)
                        .param("targets", "[\"d1\",\"d9\"]")
                        .param("algorithm", "hcem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.image").isNotEmpty())
                .andExpect(jsonPath("$.detected_targets").isArray())
                .andExpect(jsonPath("$.target_statistics.d1.detection_threshold").value(0.5))
                .andExpect(jsonPath("$.target_statistics.d9.detection_threshold").value(0.63))
                .andExpect(jsonPath("$.runtime").isNumber());
    }

    @Test
    void shouldUseExternalSpectrumForCubeOnlyDetectionMat() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "cube-only.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                createCubeOnlyDetectionMat(64)
        );

        mockMvc.perform(multipart("/fusion/detect_real3")
                        .file(matFile)
                        .param("targets", "[\"d1\"]")
                        .param("algorithm", "hcem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.image").isNotEmpty())
                .andExpect(jsonPath("$.target_statistics.d1").exists())
                .andExpect(jsonPath("$.runtime").isNumber());
    }

    @Test
    void shouldRejectCubeWhenNoTargetSpectrumMatchesItsBands() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "incompatible-cube.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                createCubeOnlyDetectionMat(3)
        );

        mockMvc.perform(multipart("/fusion/detect_real3")
                        .file(matFile)
                        .param("targets", "[\"d1\"]")
                        .param("algorithm", "hcem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.image").value(""))
                .andExpect(jsonPath("$.message").value("未找到与输入波段数兼容的目标先验光谱"));
    }

    @Test
    void shouldRejectUnsupportedDetectionAlgorithm() throws Exception {
        MockMultipartFile matFile = new MockMultipartFile(
                "file",
                "target.mat",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                createDetectionMat()
        );

        mockMvc.perform(multipart("/fusion/detect_real3")
                        .file(matFile)
                        .param("targets", "[\"d1\"]")
                        .param("algorithm", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unsupported detection algorithm: unknown"));
    }

    @Test
    void shouldRunDatasetRegistrationAndFusionFromRawHdr() throws Exception {
        MockMultipartFile hdrFile = new MockMultipartFile(
                "file1",
                "D_20251010_131632.hdr",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "selected by name".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(hdrFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").isNotEmpty())
                .andExpect(jsonPath("$.fusion_info.bands").value(70))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(32))
                .andExpect(jsonPath("$.fusion_info.fusion_height").value(32));

        Path workRoot = VISUALIZATION_ROOT.resolve("_fusion-results").resolve("fusion-work");
        try (java.util.stream.Stream<Path> stream = Files.walk(workRoot)) {
            assertTrue(stream.anyMatch(path -> path.getFileName().toString().equals("preprocessed.mat")));
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(workRoot)) {
            assertTrue(stream.anyMatch(path -> path.getFileName().toString().equals("registered.mat")));
        }
    }

    @Test
    void shouldAcceptImgAsRawDatasetEntry() throws Exception {
        MockMultipartFile imgFile = new MockMultipartFile(
                "file1",
                "D_20251010_131632.img",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "selected by name".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/start_fusion_1").file(imgFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fusion_info.bands").value(70))
                .andExpect(jsonPath("$.fusion_info.fusion_width").value(32));
    }

    private static byte[] createDetectionImage() throws IOException {
        BufferedImage image = new BufferedImage(24, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int red = Math.min(255, 20 + x * 5);
                int green = Math.min(255, 40 + y * 8);
                int blue = Math.min(255, 30 + x + y);
                image.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private static byte[] createDetectionMat() throws IOException {
        float[][][] cube = new float[8][7][3];
        for (int y = 0; y < cube.length; y++) {
            for (int x = 0; x < cube[0].length; x++) {
                cube[y][x][0] = 20 + y * 4 + x;
                cube[y][x][1] = 35 + y * 3 + x * 2;
                cube[y][x][2] = 50 + y + x * 3;
            }
        }
        Dataset.MatData matData = new Dataset.MatData();
        matData.cubes.put("hyperspectral_data", cube);
        matData.vectors.put("d1", new double[]{40, 55, 70});
        matData.vectors.put("d9", new double[]{65, 45, 35});
        Path temp = Files.createTempFile("detection-test-", ".mat");
        try {
            Dataset.MatIO.write(temp, matData, Dataset.MatVersion.V5);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] createCubeOnlyDetectionMat(int bands) throws IOException {
        float[][][] cube = new float[8][7][bands];
        for (int y = 0; y < cube.length; y++) {
            for (int x = 0; x < cube[0].length; x++) {
                for (int band = 0; band < bands; band++) {
                    cube[y][x][band] = 20 + y * 4 + x * 2 + band;
                }
            }
        }
        Dataset.MatData matData = new Dataset.MatData();
        matData.cubes.put("hyperspectral_data", cube);
        Path temp = Files.createTempFile("cube-only-detection-test-", ".mat");
        try {
            Dataset.MatIO.write(temp, matData, Dataset.MatVersion.V5);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path prepareVisualizationRoot() {
        try {
            Path root = Files.createTempDirectory("hyper-visualization-root");
            createVisualizationDataset(root.resolve("20251010"), "D_20251010_131632", new Color(220, 40, 40), 1000);
            createVisualizationDataset(root.resolve("20251011"), "D_20251011_141500", new Color(40, 180, 90), 6000);
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建测试用 ENVI 数据", exception);
        }
    }

    private static void createVisualizationDataset(Path dateDirectory, String fileStem, Color color, int baseValue)
            throws IOException {
        Files.createDirectories(dateDirectory);

        Path hdrPath = dateDirectory.resolve(fileStem + ".hdr");
        Path imgPath = dateDirectory.resolve(fileStem + ".img");
        Path colorBmpPath = dateDirectory.resolve("color.bmp");

        int samples = 20;
        int lines = 18;
        int bands = 70;

        String header = "ENVI\n"
                + "samples = " + samples + "\n"
                + "lines = " + lines + "\n"
                + "bands = " + bands + "\n"
                + "header offset = 0\n"
                + "file type = ENVI Standard\n"
                + "data type = 12\n"
                + "interleave = bsq\n"
                + "byte order = 0\n"
                + "data file = " + fileStem + ".img\n";
        Files.writeString(hdrPath, header, StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(samples * lines * bands * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int band = 0; band < bands; band++) {
            for (int y = 0; y < lines; y++) {
                for (int x = 0; x < samples; x++) {
                    int value = baseValue + band * 30 + y * 4 + x;
                    buffer.putShort((short) value);
                }
            }
        }
        Files.write(imgPath, buffer.array());

        BufferedImage bmp = new BufferedImage(samples, lines, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < lines; y++) {
            for (int x = 0; x < samples; x++) {
                int red = Math.min(255, color.getRed() + x * 2);
                int green = Math.min(255, color.getGreen() + y * 2);
                int blue = Math.min(255, color.getBlue() + (x + y));
                bmp.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        ImageIO.write(bmp, "bmp", colorBmpPath.toFile());
        ImageIO.write(bmp, "bmp", dateDirectory.resolve(fileStem + ".bmp").toFile());

        if (fileStem.endsWith("141500")) {
            BufferedImage pseudo = new BufferedImage(samples * 2, lines * 2, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < pseudo.getHeight(); y++) {
                for (int x = 0; x < pseudo.getWidth(); x++) {
                    int red = Math.min(255, 30 + x * 3);
                    int green = Math.min(255, 40 + y * 2);
                    int blue = Math.min(255, 80 + (x + y));
                    pseudo.setRGB(x, y, new Color(red, green, blue).getRGB());
                }
            }
            ImageIO.write(pseudo, "png", dateDirectory.resolve(fileStem + ".png").toFile());
        }

        long now = System.currentTimeMillis();
        Files.setLastModifiedTime(hdrPath, FileTime.fromMillis(now));
        Path pseudoPath = dateDirectory.resolve(fileStem + ".png");
        if (Files.exists(pseudoPath)) {
            Files.setLastModifiedTime(pseudoPath, FileTime.fromMillis(now + 1_000L));
        }
    }
}
