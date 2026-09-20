package com.hyper.spectral.vo.acquisition;

public class DeviceStatusVO {

    public String deviceId;
    public String deviceName;
    public String deviceType;
    public String status;
    public String wavelengthRange;
    public String lastHeartbeat;

    public DeviceStatusVO() {
    }

    public DeviceStatusVO(String deviceId, String deviceName, String deviceType, String status, String wavelengthRange, String lastHeartbeat) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.status = status;
        this.wavelengthRange = wavelengthRange;
        this.lastHeartbeat = lastHeartbeat;
    }
}
