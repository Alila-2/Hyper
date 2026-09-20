package com.hyper.spectral.vo.acquisition;

public class AcquisitionTaskVO {

    public String taskId;
    public String taskName;
    public String sceneName;
    public String deviceId;
    public String status;
    public int progress;
    public String createdAt;

    public AcquisitionTaskVO() {
    }

    public AcquisitionTaskVO(String taskId, String taskName, String sceneName, String deviceId, String status, int progress, String createdAt) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.sceneName = sceneName;
        this.deviceId = deviceId;
        this.status = status;
        this.progress = progress;
        this.createdAt = createdAt;
    }
}
