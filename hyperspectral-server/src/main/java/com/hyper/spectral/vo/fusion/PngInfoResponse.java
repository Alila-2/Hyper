package com.hyper.spectral.vo.fusion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 匹配到的 BMP/PNG 图像信息。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PngInfoResponse {

    private String resolution;
    private String format;
    private String fileSize;
    private String mode;
    private String filename;

    public PngInfoResponse() {
    }

    public PngInfoResponse(String resolution, String format, String fileSize, String mode, String filename) {
        this.resolution = resolution;
        this.format = format;
        this.fileSize = fileSize;
        this.mode = mode;
        this.filename = filename;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
