package com.hyper.spectral.service;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.preprocess.PreprocessTaskRequest;
import com.hyper.spectral.vo.preprocess.PreprocessOverviewVO;
import com.hyper.spectral.vo.preprocess.PreprocessTaskVO;
import com.hyper.spectral.vo.preprocess.PreprocessTemplateVO;

public interface PreprocessService {

    PreprocessOverviewVO getOverview();

    PageResult<PreprocessTaskVO> listTasks(int pageNo, int pageSize);

    PageResult<PreprocessTemplateVO> listTemplates(int pageNo, int pageSize);

    String createTask(PreprocessTaskRequest request);
}
