package com.hyper.spectral.controller.detection;

import com.hyper.spectral.service.DetectionService;
import com.hyper.spectral.vo.detection.DetectionPreviewResponse;
import com.hyper.spectral.vo.detection.DetectionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DetectionCompatibilityController {

    private final DetectionService detectionService;

    public DetectionCompatibilityController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping("/upload_and_preview")
    public ResponseEntity<?> uploadAndPreview(@RequestParam("file") MultipartFile file) {
        try {
            DetectionPreviewResponse response = detectionService.uploadAndPreview(file);
            return ResponseEntity.ok(response);
        } catch (IOException | IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(exception.getMessage()));
        }
    }

    @PostMapping({
            "/fusion/detect_real3",
            "/fusion/detect_real2",
            "/fusion/detect_real",
            "/fusion/detect"
    })
    public ResponseEntity<?> detectTargets(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "targets", required = false) String targetsJson,
                                           @RequestParam(value = "algorithm", defaultValue = "htd-mamba") String algorithm) {
        try {
            DetectionResponse response = detectionService.detectTargets(file, targetsJson, algorithm);
            return ResponseEntity.ok(response);
        } catch (IOException | IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(exception.getMessage()));
        }
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
