package com.hyper.spectral.config.fusion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 融合计算成像旧接口兼容配置。
 */
@Component
@ConfigurationProperties(prefix = "fusion.compatibility")
public class FusionCompatibilityProperties {

    private String searchRootDir;
    private String resultDir;
    private int sampleFusionWidth;
    private int sampleFusionHeight;
    private int sampleTotalBands;
    private int registrationLrSize = 0;
    private boolean registrationFallbackEnabled;
    private boolean keepIntermediateMat = true;

    public String getSearchRootDir() {
        return searchRootDir;
    }

    public void setSearchRootDir(String searchRootDir) {
        this.searchRootDir = searchRootDir;
    }

    public String getResultDir() {
        return resultDir;
    }

    public void setResultDir(String resultDir) {
        this.resultDir = resultDir;
    }

    public int getSampleFusionWidth() {
        return sampleFusionWidth;
    }

    public void setSampleFusionWidth(int sampleFusionWidth) {
        this.sampleFusionWidth = sampleFusionWidth;
    }

    public int getSampleFusionHeight() {
        return sampleFusionHeight;
    }

    public void setSampleFusionHeight(int sampleFusionHeight) {
        this.sampleFusionHeight = sampleFusionHeight;
    }

    public int getSampleTotalBands() {
        return sampleTotalBands;
    }

    public void setSampleTotalBands(int sampleTotalBands) {
        this.sampleTotalBands = sampleTotalBands;
    }

    public int getRegistrationLrSize() {
        return registrationLrSize;
    }

    public void setRegistrationLrSize(int registrationLrSize) {
        this.registrationLrSize = registrationLrSize;
    }

    public boolean isRegistrationFallbackEnabled() {
        return registrationFallbackEnabled;
    }

    public void setRegistrationFallbackEnabled(boolean registrationFallbackEnabled) {
        this.registrationFallbackEnabled = registrationFallbackEnabled;
    }

    public boolean isKeepIntermediateMat() {
        return keepIntermediateMat;
    }

    public void setKeepIntermediateMat(boolean keepIntermediateMat) {
        this.keepIntermediateMat = keepIntermediateMat;
    }
}
