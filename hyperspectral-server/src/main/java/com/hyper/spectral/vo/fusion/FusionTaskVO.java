package com.hyper.spectral.vo.fusion;

public class FusionTaskVO {

    public String taskId;
    public String taskName;
    public String strategyCode;
    public String status;
    public double qualityScore;
    public String updatedAt;

    public FusionTaskVO() {
    }

    public FusionTaskVO(String taskId, String taskName, String strategyCode, String status, double qualityScore, String updatedAt) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.strategyCode = strategyCode;
        this.status = status;
        this.qualityScore = qualityScore;
        this.updatedAt = updatedAt;
    }
}
