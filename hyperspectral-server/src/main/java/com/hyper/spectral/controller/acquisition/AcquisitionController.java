package com.hyper.spectral.controller.acquisition;

import com.hyper.spectral.common.ApiResponse;
import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.acquisition.AcquisitionCommandRequest;
import com.hyper.spectral.service.AcquisitionService;
import com.hyper.spectral.vo.acquisition.AcquisitionOverviewVO;
import com.hyper.spectral.vo.acquisition.AcquisitionTaskVO;
import com.hyper.spectral.vo.acquisition.DeviceStatusVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/acquisition")
public class AcquisitionController {

    private final AcquisitionService acquisitionService;

    public AcquisitionController(AcquisitionService acquisitionService) {
        this.acquisitionService = acquisitionService;
    }

    @GetMapping("/overview")
    public ApiResponse<AcquisitionOverviewVO> getOverview() {
        return ApiResponse.success(acquisitionService.getOverview());
    }

    @GetMapping("/devices")
    public ApiResponse<PageResult<DeviceStatusVO>> listDevices(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(acquisitionService.listDevices(pageNo, pageSize));
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<AcquisitionTaskVO>> listTasks(@RequestParam(defaultValue = "1") int pageNo,
                                                                @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(acquisitionService.listTasks(pageNo, pageSize));
    }

    @PostMapping("/tasks/start")
    public ApiResponse<String> startTask(@Valid @RequestBody AcquisitionCommandRequest request) {
        return ApiResponse.success(acquisitionService.startTask(request));
    }

    @PostMapping("/tasks/{taskId}/stop")
    public ApiResponse<String> stopTask(@PathVariable String taskId) {
        return ApiResponse.success(acquisitionService.stopTask(taskId));
    }
}
