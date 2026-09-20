package com.hyper.spectral.service;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.vo.visualization.PseudoColorProfileVO;
import com.hyper.spectral.vo.visualization.SpectralImageVO;
import com.hyper.spectral.vo.visualization.VisualizationOverviewVO;

public interface VisualizationService {

    VisualizationOverviewVO getOverview();

    PageResult<SpectralImageVO> listImages(int pageNo, int pageSize);

    PageResult<PseudoColorProfileVO> listProfiles(int pageNo, int pageSize);
}
