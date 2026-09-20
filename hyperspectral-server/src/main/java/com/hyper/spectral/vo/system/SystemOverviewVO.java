package com.hyper.spectral.vo.system;

public class SystemOverviewVO {

    public String serviceStatus;
    public int onlineDevices;
    public int algorithmCount;
    public int storageBucketCount;
    public String lastRefreshTime;

    public SystemOverviewVO() {
    }

    public SystemOverviewVO(String serviceStatus, int onlineDevices, int algorithmCount, int storageBucketCount, String lastRefreshTime) {
        this.serviceStatus = serviceStatus;
        this.onlineDevices = onlineDevices;
        this.algorithmCount = algorithmCount;
        this.storageBucketCount = storageBucketCount;
        this.lastRefreshTime = lastRefreshTime;
    }
}
