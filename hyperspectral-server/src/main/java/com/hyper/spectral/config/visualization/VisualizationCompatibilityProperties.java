package com.hyper.spectral.config.visualization;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 高光谱可视化旧接口兼容配置。
 */
@Component
@ConfigurationProperties(prefix = "visualization.compatibility")
public class VisualizationCompatibilityProperties {

    private String hdrRootDir;
    private String previewDir;
    private int sampleOriginalWidth;
    private int sampleOriginalHeight;
    private int samplePseudoWidth;
    private int samplePseudoHeight;
    private int sampleTotalBands;
    private String sampleWavelengthRange;
    private List<Integer> sampleVisualizationBands = new ArrayList<>();

    public String getHdrRootDir() {
        return hdrRootDir;
    }

    public void setHdrRootDir(String hdrRootDir) {
        this.hdrRootDir = hdrRootDir;
    }

    public String getPreviewDir() {
        return previewDir;
    }

    public void setPreviewDir(String previewDir) {
        this.previewDir = previewDir;
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

    public String getSampleWavelengthRange() {
        return sampleWavelengthRange;
    }

    public void setSampleWavelengthRange(String sampleWavelengthRange) {
        this.sampleWavelengthRange = sampleWavelengthRange;
    }

    public List<Integer> getSampleVisualizationBands() {
        return sampleVisualizationBands;
    }

    public void setSampleVisualizationBands(List<Integer> sampleVisualizationBands) {
        this.sampleVisualizationBands = sampleVisualizationBands;
    }
}
