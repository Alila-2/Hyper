package com.hyper.spectral.service;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.vo.system.SystemConfigVO;
import com.hyper.spectral.vo.system.SystemOverviewVO;

import java.util.Map;

public interface SystemService {

    SystemOverviewVO getOverview();

    PageResult<SystemConfigVO> listConfigs(int pageNo, int pageSize);

    Map<String, Object> getRuntimeStatus();
}
