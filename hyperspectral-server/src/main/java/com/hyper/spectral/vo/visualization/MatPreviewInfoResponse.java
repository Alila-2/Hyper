package com.hyper.spectral.vo.visualization;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * MAT 文件预览附带信息。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MatPreviewInfoResponse {

    private String resolution;
    private int bands;
    private String dimensions;
    private String wavelengthRange;

    public MatPreviewInfoResponse() {
    }

    public MatPreviewInfoResponse(String resolution, int bands, String dimensions, String wavelengthRange) {
        this.resolution = resolution;
        this.bands = bands;
        this.dimensions = dimensions;
        this.wavelengthRange = wavelengthRange;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public int getBands() {
        return bands;
    }

    public void setBands(int bands) {
        this.bands = bands;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getWavelengthRange() {
        return wavelengthRange;
    }

    public void setWavelengthRange(String wavelengthRange) {
        this.wavelengthRange = wavelengthRange;
    }
}
