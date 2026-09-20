package com.hyper.spectral.vo.fusion;

import java.util.List;

/**
 * 融合后光谱查询响应。
 */
public class FusionSpectrumResponse {

    private List<Double> spectrum;

    public FusionSpectrumResponse() {
    }

    public FusionSpectrumResponse(List<Double> spectrum) {
        this.spectrum = spectrum;
    }

    public List<Double> getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(List<Double> spectrum) {
        this.spectrum = spectrum;
    }
}
