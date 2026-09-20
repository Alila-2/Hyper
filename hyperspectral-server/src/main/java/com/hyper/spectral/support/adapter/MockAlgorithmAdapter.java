package com.hyper.spectral.support.adapter;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MockAlgorithmAdapter implements AlgorithmAdapter {

    @Override
    public List<String> listAvailableAlgorithms() {
        return Arrays.asList("辐射校正", "像元配准", "光谱角映射", "多源融合", "异常目标探测");
    }
}
