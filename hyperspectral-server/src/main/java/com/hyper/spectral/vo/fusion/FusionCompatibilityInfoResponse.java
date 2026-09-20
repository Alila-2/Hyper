package com.hyper.spectral.vo.fusion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 融合结果信息兼容响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FusionCompatibilityInfoResponse {

    private String resolution;
    private int bands;
    private String processingTime;
    private String fileSize;
    private String colorMode;
    private String outputFormat;
    private int fusionWidth;
    private int fusionHeight;

    public FusionCompatibilityInfoResponse() {
    }

    public FusionCompatibilityInfoResponse(String resolution, int bands, String processingTime, String fileSize,
                                           String colorMode, String outputFormat, int fusionWidth, int fusionHeight) {
        this.resolution = resolution;
        this.bands = bands;
        this.processingTime = processingTime;
        this.fileSize = fileSize;
        this.colorMode = colorMode;
        this.outputFormat = outputFormat;
        this.fusionWidth = fusionWidth;
        this.fusionHeight = fusionHeight;
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

    public String getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(String processingTime) {
        this.processingTime = processingTime;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getColorMode() {
        return colorMode;
    }

    public void setColorMode(String colorMode) {
        this.colorMode = colorMode;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public int getFusionWidth() {
        return fusionWidth;
    }

    public void setFusionWidth(int fusionWidth) {
        this.fusionWidth = fusionWidth;
    }

    public int getFusionHeight() {
        return fusionHeight;
    }

    public void setFusionHeight(int fusionHeight) {
        this.fusionHeight = fusionHeight;
    }
}
