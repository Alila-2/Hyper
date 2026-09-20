package com.hyper.spectral.vo.acquisition;

public class AcquisitionOverviewVO {

    public int onlineDevices;
    public int runningTasks;
    public int todayCollectedCubes;
    public String currentScene;

    public AcquisitionOverviewVO() {
    }

    public AcquisitionOverviewVO(int onlineDevices, int runningTasks, int todayCollectedCubes, String currentScene) {
        this.onlineDevices = onlineDevices;
        this.runningTasks = runningTasks;
        this.todayCollectedCubes = todayCollectedCubes;
        this.currentScene = currentScene;
    }
}
