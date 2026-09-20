package com.hyper.spectral.vo.acquisition;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 检查拍摄前最新文件状态响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CheckCaptureCompleteResponse {

    private boolean success;
    private String latestHdrBefore;
    private String latestDir;
    private String message;

    public CheckCaptureCompleteResponse() {
    }

    public CheckCaptureCompleteResponse(boolean success, String latestHdrBefore, String latestDir, String message) {
        this.success = success;
        this.latestHdrBefore = latestHdrBefore;
        this.latestDir = latestDir;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
