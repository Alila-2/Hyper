package com.hyper.spectral.vo.acquisition;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 等待拍摄完成响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WaitForCaptureCompleteResponse {

    private boolean success;
    private String latestHdr;
    private String latestDir;
    private String message;

    public WaitForCaptureCompleteResponse() {
    }

    public WaitForCaptureCompleteResponse(boolean success, String latestHdr, String latestDir, String message) {
        this.success = success;
        this.latestHdr = latestHdr;
        this.latestDir = latestDir;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getLatestHdr() {
        return latestHdr;
    }

    public void setLatestHdr(String latestHdr) {
        this.latestHdr = latestHdr;
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
