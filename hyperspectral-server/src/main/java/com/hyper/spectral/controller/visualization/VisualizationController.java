package com.hyper.spectral.controller.visualization;

import com.hyper.spectral.common.ApiResponse;
import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.VisualizationService;
import com.hyper.spectral.vo.visualization.PseudoColorProfileVO;
import com.hyper.spectral.vo.visualization.SpectralImageVO;
import com.hyper.spectral.vo.visualization.VisualizationOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visualization")
public class VisualizationController {

    private final VisualizationService visualizationService;

    public VisualizationController(VisualizationService visualizationService) {
        this.visualizationService = visualizationService;
    }

    @GetMapping("/overview")
    public ApiResponse<VisualizationOverviewVO> getOverview() {
        return ApiResponse.success(visualizationService.getOverview());
    }

    @GetMapping("/images")
    public ApiResponse<PageResult<SpectralImageVO>> listImages(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(visualizationService.listImages(pageNo, pageSize));
    }

    @GetMapping("/profiles")
    public ApiResponse<PageResult<PseudoColorProfileVO>> listProfiles(@RequestParam(defaultValue = "1") int pageNo,
                                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(visualizationService.listProfiles(pageNo, pageSize));
    }
}
