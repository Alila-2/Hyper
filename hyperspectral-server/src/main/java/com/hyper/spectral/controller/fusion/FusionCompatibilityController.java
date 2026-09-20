package com.hyper.spectral.controller.fusion;

import com.hyper.spectral.service.impl.FusionCompatibilityService;
import com.hyper.spectral.vo.fusion.FusionDataInfoResponse;
import com.hyper.spectral.vo.fusion.FusionSpectrumResponse;
import com.hyper.spectral.vo.fusion.PngByMatResponse;
import com.hyper.spectral.vo.fusion.StartFusionResponse;
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
 * 旧融合计算成像前端兼容接口。
 */
@RestController
public class FusionCompatibilityController {

    private final FusionCompatibilityService fusionCompatibilityService;

    public FusionCompatibilityController(FusionCompatibilityService fusionCompatibilityService) {
        this.fusionCompatibilityService = fusionCompatibilityService;
    }

    @PostMapping("/get_png_by_mat")
    public ResponseEntity<?> getPngByMat(@RequestParam("file") MultipartFile file) {
        try {
            PngByMatResponse response = fusionCompatibilityService.getPngByMat(file);
            return ResponseEntity.ok(response);
        } catch (IOException exception) {
            Map<String, String> body = Collections.singletonMap("detail", "获取BMP文件失败：" + exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @PostMapping("/start_fusion_1")
    public ResponseEntity<?> startFusion(HttpServletRequest request,
                                         @RequestParam("file1") MultipartFile file1,
                                         @RequestParam(value = "file2", required = false) MultipartFile file2) {
        try {
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            StartFusionResponse response = fusionCompatibilityService.startFusion(baseUrl, file1, file2);
            return ResponseEntity.ok(response);
        } catch (IOException exception) {
            Map<String, String> body = Collections.singletonMap("detail", "融合失败：" + exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @GetMapping("/api/fusion-info")
    public ResponseEntity<?> getFusionInfo() {
        try {
            FusionDataInfoResponse response = fusionCompatibilityService.getFusionInfo();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("detail", exception.getMessage()));
        }
    }

    @GetMapping("/api/fusion-spectrum")
    public ResponseEntity<?> getFusionSpectrum(@RequestParam int x, @RequestParam int y) {
        try {
            FusionSpectrumResponse response = fusionCompatibilityService.getFusionSpectrum(x, y);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("detail", exception.getMessage()));
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("detail", exception.getMessage()));
        }
    }
}
