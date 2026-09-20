package com.hyper.spectral.vo.preprocess;

public class PreprocessOverviewVO {

    public int pendingCount;
    public int runningCount;
    public int completedToday;
    public String defaultTemplate;

    public PreprocessOverviewVO() {
    }

    public PreprocessOverviewVO(int pendingCount, int runningCount, int completedToday, String defaultTemplate) {
        this.pendingCount = pendingCount;
        this.runningCount = runningCount;
        this.completedToday = completedToday;
        this.defaultTemplate = defaultTemplate;
    }
}
