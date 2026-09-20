package com.hyper.spectral.vo.preprocess;

public class PreprocessTaskVO {

    public String taskId;
    public String taskName;
    public String sourceCubeId;
    public String templateCode;
    public String status;
    public int queuePosition;
    public String updatedAt;

    public PreprocessTaskVO() {
    }

    public PreprocessTaskVO(String taskId, String taskName, String sourceCubeId, String templateCode, String status, int queuePosition, String updatedAt) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.sourceCubeId = sourceCubeId;
        this.templateCode = templateCode;
        this.status = status;
        this.queuePosition = queuePosition;
        this.updatedAt = updatedAt;
    }
}
