package com.hyper.spectral.controller.system;

import com.hyper.spectral.common.ApiResponse;
import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.SystemService;
import com.hyper.spectral.vo.system.SystemConfigVO;
import com.hyper.spectral.vo.system.SystemOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/overview")
    public ApiResponse<SystemOverviewVO> getOverview() {
        return ApiResponse.success(systemService.getOverview());
    }

    @GetMapping("/configs")
    public ApiResponse<PageResult<SystemConfigVO>> listConfigs(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(systemService.listConfigs(pageNo, pageSize));
    }

    @GetMapping("/runtime")
    public ApiResponse<Map<String, Object>> getRuntimeStatus() {
        return ApiResponse.success(systemService.getRuntimeStatus());
    }
}
