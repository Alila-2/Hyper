package com.hyper.spectral.service.impl;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.FusionService;
import com.hyper.spectral.dto.fusion.FusionTaskRequest;
import com.hyper.spectral.vo.fusion.FusionOverviewVO;
import com.hyper.spectral.vo.fusion.FusionStrategyVO;
import com.hyper.spectral.vo.fusion.FusionTaskVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MockFusionService implements FusionService {

    @Override
    public FusionOverviewVO getOverview() {
        return new FusionOverviewVO(2, 3, 0.93, "FUSION-PRD-1008");
    }

    @Override
    public PageResult<FusionTaskVO> listTasks(int pageNo, int pageSize) {
        List<FusionTaskVO> records = Arrays.asList(
                new FusionTaskVO("FUS-20260325-001", "湿地区域清晰化融合", "GRAM-SCHMIDT", "RUNNING", 0.91, "2026-03-25 09:10:00"),
                new FusionTaskVO("FUS-20260325-002", "矿区高分辨率融合", "BAYESIAN", "QUEUED", 0.0, "2026-03-25 09:06:00"),
                new FusionTaskVO("FUS-20260324-011", "农田专题图融合", "PCA", "COMPLETED", 0.96, "2026-03-24 17:45:00")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public PageResult<FusionStrategyVO> listStrategies(int pageNo, int pageSize) {
        List<FusionStrategyVO> records = Arrays.asList(
                new FusionStrategyVO("GRAM-SCHMIDT", "Gram-Schmidt 融合", "适用于高光谱与全色影像细节增强", "0.5m"),
                new FusionStrategyVO("BAYESIAN", "贝叶斯融合", "适用于融合质量与噪声控制平衡", "1m"),
                new FusionStrategyVO("PCA", "主成分融合", "适用于快速实验与方案验证", "2m")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public String createTask(FusionTaskRequest request) {
        return "融合任务已加入队列：" + request.taskName + "，策略：" + request.strategyCode;
    }
}
