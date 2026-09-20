package com.hyper.spectral.support.adapter;

import java.util.List;

/**
 * 设备适配层扩展点，后续可替换为真实硬件 SDK 集成。
 */
public interface DeviceAdapter {

    List<String> listOnlineDevices();
}
