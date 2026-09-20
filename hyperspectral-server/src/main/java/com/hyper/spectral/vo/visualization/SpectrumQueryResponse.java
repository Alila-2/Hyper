package com.hyper.spectral.vo.visualization;

import java.util.List;

/**
 * 光谱查询兼容响应。
 */
public class SpectrumQueryResponse {

    private List<Double> spectrum;

    public SpectrumQueryResponse() {
    }

    public SpectrumQueryResponse(List<Double> spectrum) {
        this.spectrum = spectrum;
    }

    public List<Double> getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(List<Double> spectrum) {
        this.spectrum = spectrum;
    }
}
