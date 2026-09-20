package com.hyper.spectral.service;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.fusion.FusionTaskRequest;
import com.hyper.spectral.vo.fusion.FusionOverviewVO;
import com.hyper.spectral.vo.fusion.FusionStrategyVO;
import com.hyper.spectral.vo.fusion.FusionTaskVO;

public interface FusionService {

    FusionOverviewVO getOverview();

    PageResult<FusionTaskVO> listTasks(int pageNo, int pageSize);

    PageResult<FusionStrategyVO> listStrategies(int pageNo, int pageSize);

    String createTask(FusionTaskRequest request);
}
