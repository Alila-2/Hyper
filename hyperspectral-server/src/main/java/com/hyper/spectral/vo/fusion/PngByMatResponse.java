package com.hyper.spectral.vo.fusion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 根据 MAT 文件获取对应图像的兼容响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PngByMatResponse {

    private String pngBase64;
    private PngInfoResponse pngInfo;
    private String filename;

    public PngByMatResponse() {
    }

    public PngByMatResponse(String pngBase64, PngInfoResponse pngInfo, String filename) {
        this.pngBase64 = pngBase64;
        this.pngInfo = pngInfo;
        this.filename = filename;
    }

    public String getPngBase64() {
        return pngBase64;
    }

    public void setPngBase64(String pngBase64) {
        this.pngBase64 = pngBase64;
    }

    public PngInfoResponse getPngInfo() {
        return pngInfo;
    }

    public void setPngInfo(PngInfoResponse pngInfo) {
        this.pngInfo = pngInfo;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
