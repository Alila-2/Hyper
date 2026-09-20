package com.hyper.spectral.vo.visualization;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * MAT 预览兼容响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MatPreviewResponse {

    private String previewUrl;
    private MatPreviewInfoResponse matInfo;

    public MatPreviewResponse() {
    }

    public MatPreviewResponse(String previewUrl, MatPreviewInfoResponse matInfo) {
        this.previewUrl = previewUrl;
        this.matInfo = matInfo;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public MatPreviewInfoResponse getMatInfo() {
        return matInfo;
    }

    public void setMatInfo(MatPreviewInfoResponse matInfo) {
        this.matInfo = matInfo;
    }
}
