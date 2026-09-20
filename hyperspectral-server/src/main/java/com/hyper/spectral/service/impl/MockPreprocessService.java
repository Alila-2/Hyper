package com.hyper.spectral.service.impl;

import com.hyper.spectral.common.PageResult;
import com.hyper.spectral.service.PreprocessService;
import com.hyper.spectral.dto.preprocess.PreprocessTaskRequest;
import com.hyper.spectral.vo.preprocess.PreprocessOverviewVO;
import com.hyper.spectral.vo.preprocess.PreprocessTaskVO;
import com.hyper.spectral.vo.preprocess.PreprocessTemplateVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MockPreprocessService implements PreprocessService {

    @Override
    public PreprocessOverviewVO getOverview() {
        return new PreprocessOverviewVO(4, 2, 21, "GEO-CORR-STD");
    }

    @Override
    public PageResult<PreprocessTaskVO> listTasks(int pageNo, int pageSize) {
        List<PreprocessTaskVO> records = Arrays.asList(
                new PreprocessTaskVO("PRE-20260325-001", "湿地辐射校正", "CUBE-10021", "RAD-CAL", "RUNNING", 0, "2026-03-25 09:12:00"),
                new PreprocessTaskVO("PRE-20260325-002", "矿区几何校正", "CUBE-10022", "GEO-CORR-STD", "QUEUED", 1, "2026-03-25 09:08:00"),
                new PreprocessTaskVO("PRE-20260324-017", "农田噪声抑制", "CUBE-09981", "DENOISE-L1", "COMPLETED", 0, "2026-03-24 18:20:00")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public PageResult<PreprocessTemplateVO> listTemplates(int pageNo, int pageSize) {
        List<PreprocessTemplateVO> records = Arrays.asList(
                new PreprocessTemplateVO("RAD-CAL", "辐射校正模板", "用于原始高光谱数据反射率归一化", "暗电流校正 / 白板校正 / 反射率归一化"),
                new PreprocessTemplateVO("GEO-CORR-STD", "几何校正模板", "用于航带影像的空间配准与纠偏", "轨迹解算 / 控制点匹配 / 重采样"),
                new PreprocessTemplateVO("DENOISE-L1", "噪声抑制模板", "用于条带噪声与随机噪声抑制", "条纹修复 / 中值滤波 / 谱段平滑")
        );
        return PageResult.of(records, pageNo, pageSize);
    }

    @Override
    public String createTask(PreprocessTaskRequest request) {
        return "预处理任务已创建：" + request.taskName + "，模板：" + request.templateCode;
    }
}
