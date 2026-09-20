package com.hyper.spectral.dto.acquisition;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 旧前端等待拍摄完成请求。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WaitForCaptureCompleteRequest {

    private String latestHdrBefore;
    private String latestDir;

    public String getLatestHdrBefore() {
        return latestHdrBefore;
    }

    public void setLatestHdrBefore(String latestHdrBefore) {
        this.latestHdrBefore = latestHdrBefore;
    }

    public String getLatestDir() {
        return latestDir;
    }

    public void setLatestDir(String latestDir) {
        this.latestDir = latestDir;
    }
}
