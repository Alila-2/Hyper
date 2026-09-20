package com.hyper.spectral.controller.fusion;

import com.hyper.spectral.common.ApiResponse;
import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.fusion.FusionTaskRequest;
import com.hyper.spectral.service.FusionService;
import com.hyper.spectral.vo.fusion.FusionOverviewVO;
import com.hyper.spectral.vo.fusion.FusionStrategyVO;
import com.hyper.spectral.vo.fusion.FusionTaskVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/fusion")
public class FusionController {

    private final FusionService fusionService;

    public FusionController(FusionService fusionService) {
        this.fusionService = fusionService;
    }

    @GetMapping("/overview")
    public ApiResponse<FusionOverviewVO> getOverview() {
        return ApiResponse.success(fusionService.getOverview());
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<FusionTaskVO>> listTasks(@RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(fusionService.listTasks(pageNo, pageSize));
    }

    @GetMapping("/strategies")
    public ApiResponse<PageResult<FusionStrategyVO>> listStrategies(@RequestParam(defaultValue = "1") int pageNo,
                                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(fusionService.listStrategies(pageNo, pageSize));
    }

    @PostMapping("/tasks")
    public ApiResponse<String> createTask(@Valid @RequestBody FusionTaskRequest request) {
        return ApiResponse.success(fusionService.createTask(request));
    }
}
