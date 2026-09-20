package com.hyper.spectral.config.fusion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the MIAE blind-fusion Python bridge.
 */
@Component
@ConfigurationProperties(prefix = "fusion.miae")
public class MiaeFusionProperties {

    private boolean enabled = true;
    private String pythonCommand;
    private String condaCommand = "conda";
    private String condaEnvironment = "htd";
    private String runnerPath = "../miae_fusion_runner.py";
    private long timeoutSeconds = 10800L;
    private String device = "cuda";
    private int randomSeed = 2026;
    private int blindIterations = 3000;
    private int fusionIterations = 5000;
    private int batchSize = 16;
    private int patchSize = 64;
    private int endmembers = 30;
    private int stages = 3;
    private int tileSize = 256;
    private int haloLowResolution = 2;
    private int logEvery = 100;
    private boolean ampInference;
    private long spectrumTimeoutMillis = 15000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    public String getCondaCommand() {
        return condaCommand;
    }

    public void setCondaCommand(String condaCommand) {
        this.condaCommand = condaCommand;
    }

    public String getCondaEnvironment() {
        return condaEnvironment;
    }

    public void setCondaEnvironment(String condaEnvironment) {
        this.condaEnvironment = condaEnvironment;
    }

    public String getRunnerPath() {
        return runnerPath;
    }

    public void setRunnerPath(String runnerPath) {
        this.runnerPath = runnerPath;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public int getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
    }

    public int getBlindIterations() {
        return blindIterations;
    }

    public void setBlindIterations(int blindIterations) {
        this.blindIterations = blindIterations;
    }

    public int getFusionIterations() {
        return fusionIterations;
    }

    public void setFusionIterations(int fusionIterations) {
        this.fusionIterations = fusionIterations;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getPatchSize() {
        return patchSize;
    }

    public void setPatchSize(int patchSize) {
        this.patchSize = patchSize;
    }

    public int getEndmembers() {
        return endmembers;
    }

    public void setEndmembers(int endmembers) {
        this.endmembers = endmembers;
    }

    public int getStages() {
        return stages;
    }

    public void setStages(int stages) {
        this.stages = stages;
    }

    public int getTileSize() {
        return tileSize;
    }

    public void setTileSize(int tileSize) {
        this.tileSize = tileSize;
    }

    public int getHaloLowResolution() {
        return haloLowResolution;
    }

    public void setHaloLowResolution(int haloLowResolution) {
        this.haloLowResolution = haloLowResolution;
    }

    public int getLogEvery() {
        return logEvery;
    }

    public void setLogEvery(int logEvery) {
        this.logEvery = logEvery;
    }

    public boolean isAmpInference() {
        return ampInference;
    }

    public void setAmpInference(boolean ampInference) {
        this.ampInference = ampInference;
    }

    public long getSpectrumTimeoutMillis() {
        return spectrumTimeoutMillis;
    }

    public void setSpectrumTimeoutMillis(long spectrumTimeoutMillis) {
        this.spectrumTimeoutMillis = spectrumTimeoutMillis;
    }
}
