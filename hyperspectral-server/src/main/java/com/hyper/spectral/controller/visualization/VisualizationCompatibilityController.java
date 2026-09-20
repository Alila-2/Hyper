package com.hyper.spectral.controller.visualization;

import com.hyper.spectral.service.impl.VisualizationCompatibilityService;
import com.hyper.spectral.vo.visualization.LoadHyperspectralDataResponse;
import com.hyper.spectral.vo.visualization.MatPreviewResponse;
import com.hyper.spectral.vo.visualization.SpectrumQueryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * 旧可视化前端兼容接口。
 */
@RestController
public class VisualizationCompatibilityController {

    private final VisualizationCompatibilityService visualizationCompatibilityService;

    public VisualizationCompatibilityController(VisualizationCompatibilityService visualizationCompatibilityService) {
        this.visualizationCompatibilityService = visualizationCompatibilityService;
    }

    @PostMapping("/api/load-hyperspectral-data")
    public LoadHyperspectralDataResponse loadHyperspectralData() {
        return visualizationCompatibilityService.loadLatestHyperspectralData();
    }

    @PostMapping("/api/load-hyperspectral-data/upload")
    public LoadHyperspectralDataResponse loadHyperspectralDataUpload(
            @RequestParam(value = "hdr_name", required = false) String hdrName,
            @RequestParam(value = "hdr_file", required = false) MultipartFile hdrFile) {
        return visualizationCompatibilityService.loadHyperspectralDataFromUpload(hdrName, hdrFile);
    }

    @GetMapping("/api/spectrum")
    public ResponseEntity<?> getSpectrum(@RequestParam int x, @RequestParam int y) {
        try {
            SpectrumQueryResponse response = visualizationCompatibilityService.querySpectrum(x, y);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("detail", exception.getMessage()));
        }
    }

    @PostMapping("/get_mat_preview")
    public ResponseEntity<?> getMatPreview(HttpServletRequest request,
                                           @RequestParam("mat_file") MultipartFile matFile,
                                           @RequestParam(value = "target_var", required = false) String targetVar) {
        try {
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            MatPreviewResponse response = visualizationCompatibilityService.generateMatPreview(baseUrl, matFile, targetVar);
            return ResponseEntity.ok(response);
        } catch (IOException exception) {
            Map<String, String> body = Collections.singletonMap("detail", "预览生成失败: " + exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
