package com.hyper.spectral.support.adapter;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MockStorageAdapter implements StorageAdapter {

    @Override
    public List<String> listStorageBuckets() {
        return Arrays.asList("raw-cube", "preprocessed-cube", "fusion-result", "detection-result");
    }
}
