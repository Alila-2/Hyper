package com.hyper.spectral.support.adapter;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MockDeviceAdapter implements DeviceAdapter {

    @Override
    public List<String> listOnlineDevices() {
        return Arrays.asList("HS-CAM-01", "HS-CAM-02", "PAN-DEVICE-01");
    }
}
