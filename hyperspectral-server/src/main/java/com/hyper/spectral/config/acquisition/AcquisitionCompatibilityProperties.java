package com.hyper.spectral.config.acquisition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据采集控制旧接口兼容配置。
 */
@Component
@ConfigurationProperties(prefix = "acquisition.compatibility")
public class AcquisitionCompatibilityProperties {

    private String captureRootDir;
    private String mockRootDir;
    private long waitTimeoutMs;
    private long pollIntervalMs;
    private int sampleOriginalWidth;
    private int sampleOriginalHeight;
    private int samplePseudoWidth;
    private int samplePseudoHeight;
    private int sampleTotalBands;
    private List<Integer> sampleVisualizationBands = new ArrayList<>();

    public String getCaptureRootDir() {
        return captureRootDir;
    }

    public void setCaptureRootDir(String captureRootDir) {
        this.captureRootDir = captureRootDir;
    }

    public String getMockRootDir() {
        return mockRootDir;
    }

    public void setMockRootDir(String mockRootDir) {
        this.mockRootDir = mockRootDir;
    }

    public long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public void setWaitTimeoutMs(long waitTimeoutMs) {
        this.waitTimeoutMs = waitTimeoutMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getSampleOriginalWidth() {
        return sampleOriginalWidth;
    }

    public void setSampleOriginalWidth(int sampleOriginalWidth) {
        this.sampleOriginalWidth = sampleOriginalWidth;
    }

    public int getSampleOriginalHeight() {
        return sampleOriginalHeight;
    }

    public void setSampleOriginalHeight(int sampleOriginalHeight) {
        this.sampleOriginalHeight = sampleOriginalHeight;
    }

    public int getSamplePseudoWidth() {
        return samplePseudoWidth;
    }

    public void setSamplePseudoWidth(int samplePseudoWidth) {
        this.samplePseudoWidth = samplePseudoWidth;
    }

    public int getSamplePseudoHeight() {
        return samplePseudoHeight;
    }

    public void setSamplePseudoHeight(int samplePseudoHeight) {
        this.samplePseudoHeight = samplePseudoHeight;
    }

    public int getSampleTotalBands() {
        return sampleTotalBands;
    }

    public void setSampleTotalBands(int sampleTotalBands) {
        this.sampleTotalBands = sampleTotalBands;
    }

    public List<Integer> getSampleVisualizationBands() {
        return sampleVisualizationBands;
    }

    public void setSampleVisualizationBands(List<Integer> sampleVisualizationBands) {
        this.sampleVisualizationBands = sampleVisualizationBands;
    }
}
