package com.hyper.spectral.service.impl;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.VisualizationService;
import com.hyper.spectral.vo.visualization.PseudoColorProfileVO;
import com.hyper.spectral.vo.visualization.SpectralImageVO;
import com.hyper.spectral.vo.visualization.VisualizationOverviewVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MockVisualizationService implements VisualizationService {

    @Override
    public VisualizationOverviewVO getOverview() {
        return new VisualizationOverviewVO(8, 3, 96, "PSEUDO_COLOR");
    }

    @Override
    public PageResult<SpectralImageVO> listImages(int pageNo, int pageSize) {
        List<SpectralImageVO> records = Arrays.asList(
                new SpectralImageVO("IMG-001", "CUBE-10021", "单波段浏览", 32, "1024x1024", "2026-03-25 08:45:00"),
                new SpectralImageVO("IMG-002", "CUBE-10021", "伪彩合成", 96, "1024x1024", "2026-03-25 08:46:30"),
                new SpectralImageVO("IMG-003", "CUBE-10022", "灰度增强", 154, "2048x1024", "2026-03-25 09:02:00")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public PageResult<PseudoColorProfileVO> listProfiles(int pageNo, int pageSize) {
        List<PseudoColorProfileVO> records = Arrays.asList(
                new PseudoColorProfileVO("PROFILE-01", "植被分析组合", "R:84 / G:56 / B:28", "突出植被长势与水分差异"),
                new PseudoColorProfileVO("PROFILE-02", "地物纹理组合", "R:120 / G:76 / B:42", "强化地物纹理边界与形态"),
                new PseudoColorProfileVO("PROFILE-03", "异常热点组合", "R:160 / G:110 / B:35", "用于热异常区域的快速筛查")
        );
        return PageResult.of(records, pageNo, pageSize);
    }
}
