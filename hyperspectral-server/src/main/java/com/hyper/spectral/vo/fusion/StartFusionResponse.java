package com.hyper.spectral.vo.fusion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 启动融合兼容响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StartFusionResponse {

    private String imageUrl;
    private FusionCompatibilityInfoResponse fusionInfo;

    public StartFusionResponse() {
    }

    public StartFusionResponse(String imageUrl, FusionCompatibilityInfoResponse fusionInfo) {
        this.imageUrl = imageUrl;
        this.fusionInfo = fusionInfo;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public FusionCompatibilityInfoResponse getFusionInfo() {
        return fusionInfo;
    }

    public void setFusionInfo(FusionCompatibilityInfoResponse fusionInfo) {
        this.fusionInfo = fusionInfo;
    }
}
