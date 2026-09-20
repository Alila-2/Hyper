package com.hyper.spectral.vo.fusion;

public class FusionOverviewVO {

    public int runningTasks;
    public int readyStrategies;
    public double averageQualityScore;
    public String latestProductId;

    public FusionOverviewVO() {
    }

    public FusionOverviewVO(int runningTasks, int readyStrategies, double averageQualityScore, String latestProductId) {
        this.runningTasks = runningTasks;
        this.readyStrategies = readyStrategies;
        this.averageQualityScore = averageQualityScore;
        this.latestProductId = latestProductId;
    }
}
