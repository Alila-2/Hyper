package com.hyper.spectral.support.adapter;

import java.util.List;

/**
 * 存储适配层扩展点，预留对象存储或文件系统接入。
 */
public interface StorageAdapter {

    List<String> listStorageBuckets();
}
