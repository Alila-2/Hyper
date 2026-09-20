package com.hyper.spectral.service.impl;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.SystemService;
import com.hyper.spectral.vo.system.SystemConfigVO;
import com.hyper.spectral.vo.system.SystemOverviewVO;
import com.hyper.spectral.support.adapter.AlgorithmAdapter;
import com.hyper.spectral.support.adapter.DeviceAdapter;
import com.hyper.spectral.support.adapter.StorageAdapter;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockSystemService implements SystemService {

    private final DeviceAdapter deviceAdapter;
    private final AlgorithmAdapter algorithmAdapter;
    private final StorageAdapter storageAdapter;

    public MockSystemService(DeviceAdapter deviceAdapter,
                             AlgorithmAdapter algorithmAdapter,
                             StorageAdapter storageAdapter) {
        this.deviceAdapter = deviceAdapter;
        this.algorithmAdapter = algorithmAdapter;
        this.storageAdapter = storageAdapter;
    }

    @Override
    public SystemOverviewVO getOverview() {
        return new SystemOverviewVO(
                "RUNNING",
                deviceAdapter.listOnlineDevices().size(),
                algorithmAdapter.listAvailableAlgorithms().size(),
                storageAdapter.listStorageBuckets().size(),
                "2026-03-25 09:20:00"
        );
    }

    @Override
    public PageResult<SystemConfigVO> listConfigs(int pageNo, int pageSize) {
        List<SystemConfigVO> records = Arrays.asList(
                new SystemConfigVO("system.scene.default", "默认场景", "滨海湿地巡检", "业务参数", "系统启动后默认加载的演示场景"),
                new SystemConfigVO("acquisition.buffer.size", "采集缓存队列", "32", "采集配置", "采集链路的内存缓冲队列长度"),
                new SystemConfigVO("detection.threshold.default", "默认探测阈值", "0.85", "算法配置", "探测任务未显式指定时采用的置信阈值")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public Map<String, Object> getRuntimeStatus() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("jvmHeap", "512M / 1024M");
        runtime.put("activeTasks", 7);
        runtime.put("deviceHeartbeatDelayMs", 180);
        runtime.put("storageBuckets", storageAdapter.listStorageBuckets());
        runtime.put("loadedAlgorithms", algorithmAdapter.listAvailableAlgorithms());
        return runtime;
    }
}
