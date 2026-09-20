package com.hyper.spectral.controller.acquisition;

import com.hyper.spectral.dto.acquisition.WaitForCaptureCompleteRequest;
import com.hyper.spectral.service.impl.AcquisitionCompatibilityService;
import com.hyper.spectral.vo.acquisition.CheckCaptureCompleteResponse;
import com.hyper.spectral.vo.acquisition.PreloadHyperspectralDataResponse;
import com.hyper.spectral.vo.acquisition.WaitForCaptureCompleteResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧前端数据采集控制页兼容接口。
 */
@RestController
public class AcquisitionCompatibilityController {

    private final AcquisitionCompatibilityService acquisitionCompatibilityService;

    public AcquisitionCompatibilityController(AcquisitionCompatibilityService acquisitionCompatibilityService) {
        this.acquisitionCompatibilityService = acquisitionCompatibilityService;
    }

    @PostMapping("/api/check-capture-complete")
    public CheckCaptureCompleteResponse checkCaptureComplete() {
        return acquisitionCompatibilityService.checkCaptureComplete();
    }

    @PostMapping("/api/wait-for-capture-complete")
    public WaitForCaptureCompleteResponse waitForCaptureComplete(
            @RequestBody(required = false) WaitForCaptureCompleteRequest request) {
        WaitForCaptureCompleteRequest safeRequest = request == null ? new WaitForCaptureCompleteRequest() : request;
        return acquisitionCompatibilityService.waitForCaptureComplete(safeRequest);
    }

    @PostMapping("/api/pre_load-hyperspectral-data")
    public PreloadHyperspectralDataResponse preloadHyperspectralData() {
        return acquisitionCompatibilityService.preloadHyperspectralData();
    }
}
