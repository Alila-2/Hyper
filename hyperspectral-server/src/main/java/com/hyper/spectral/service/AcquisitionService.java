package com.hyper.spectral.service;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.dto.acquisition.AcquisitionCommandRequest;
import com.hyper.spectral.vo.acquisition.AcquisitionOverviewVO;
import com.hyper.spectral.vo.acquisition.AcquisitionTaskVO;
import com.hyper.spectral.vo.acquisition.DeviceStatusVO;

public interface AcquisitionService {

    AcquisitionOverviewVO getOverview();

    PageResult<DeviceStatusVO> listDevices(int pageNo, int pageSize);

    PageResult<AcquisitionTaskVO> listTasks(int pageNo, int pageSize);

    String startTask(AcquisitionCommandRequest request);

    String stopTask(String taskId);
}
