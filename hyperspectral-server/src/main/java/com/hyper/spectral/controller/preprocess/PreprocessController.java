package com.hyper.spectral.controller.preprocess;

import com.hyper.spectral.common.ApiResponse;
import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.preprocess.PreprocessTaskRequest;
import com.hyper.spectral.service.PreprocessService;
import com.hyper.spectral.vo.preprocess.PreprocessOverviewVO;
import com.hyper.spectral.vo.preprocess.PreprocessTaskVO;
import com.hyper.spectral.vo.preprocess.PreprocessTemplateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/preprocess")
public class PreprocessController {

    private final PreprocessService preprocessService;

    public PreprocessController(PreprocessService preprocessService) {
        this.preprocessService = preprocessService;
    }

    @GetMapping("/overview")
    public ApiResponse<PreprocessOverviewVO> getOverview() {
        return ApiResponse.success(preprocessService.getOverview());
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<PreprocessTaskVO>> listTasks(@RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(preprocessService.listTasks(pageNo, pageSize));
    }

    @GetMapping("/templates")
    public ApiResponse<PageResult<PreprocessTemplateVO>> listTemplates(@RequestParam(defaultValue = "1") int pageNo,
                                                                       @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(preprocessService.listTemplates(pageNo, pageSize));
    }

    @PostMapping("/tasks")
    public ApiResponse<String> createTask(@Valid @RequestBody PreprocessTaskRequest request) {
        return ApiResponse.success(preprocessService.createTask(request));
    }
}
