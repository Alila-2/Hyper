package com.hyper.spectral.service.impl;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.AcquisitionService;
import com.hyper.spectral.dto.acquisition.AcquisitionCommandRequest;
import com.hyper.spectral.vo.acquisition.AcquisitionOverviewVO;
import com.hyper.spectral.vo.acquisition.AcquisitionTaskVO;
import com.hyper.spectral.vo.acquisition.DeviceStatusVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MockAcquisitionService implements AcquisitionService {

    @Override
    public AcquisitionOverviewVO getOverview() {
        return new AcquisitionOverviewVO(3, 2, 14, "滨海湿地巡检航线");
    }

    @Override
    public PageResult<DeviceStatusVO> listDevices(int pageNo, int pageSize) {
        List<DeviceStatusVO> records = Arrays.asList(
                new DeviceStatusVO("HS-CAM-01", "机载高光谱相机 A", "高光谱相机", "ONLINE", "400-1000nm", "2026-03-25 09:20:11"),
                new DeviceStatusVO("HS-CAM-02", "地面高光谱相机 B", "高光谱相机", "ONLINE", "900-1700nm", "2026-03-25 09:18:42"),
                new DeviceStatusVO("PAN-DEVICE-01", "全色辅助成像设备", "全色相机", "STANDBY", "450-750nm", "2026-03-25 09:15:06")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public PageResult<AcquisitionTaskVO> listTasks(int pageNo, int pageSize) {
        List<AcquisitionTaskVO> records = Arrays.asList(
                new AcquisitionTaskVO("ACQ-20260325-001", "海岸线巡检采集", "滨海湿地", "HS-CAM-01", "RUNNING", 78, "2026-03-25 08:30:00"),
                new AcquisitionTaskVO("ACQ-20260325-002", "矿区热异常采集", "北侧露天矿区", "HS-CAM-02", "QUEUED", 0, "2026-03-25 09:00:00"),
                new AcquisitionTaskVO("ACQ-20260324-013", "农田病虫害采集", "东区试验田", "HS-CAM-01", "COMPLETED", 100, "2026-03-24 15:10:00")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public String startTask(AcquisitionCommandRequest request) {
        return "已提交采集任务 " + request.taskName + "，目标区域：" + request.targetArea;
    }

    @Override
    public String stopTask(String taskId) {
        return "已下发停止指令，任务编号：" + taskId;
    }
}
