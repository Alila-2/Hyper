package com.hyper.spectral.config.fusion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Python HySure fusion bridge.
 */
@Component
@ConfigurationProperties(prefix = "fusion.hysure")
public class HysureFusionProperties {

    private boolean enabled = true;
    private String pythonCommand;
    private String condaCommand = "conda";
    private String condaEnvironment = "htd";
    private String runnerPath = "../Hysure-python/hysure_runner.py";
    private long timeoutSeconds = 1800L;
    private int subspaceDimension = 10;
    private int iterations = 30;
    private int randomSeed;
    private int blurSupport = 10;
    private int outputBlockRows = 32;
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

    public int getSubspaceDimension() {
        return subspaceDimension;
    }

    public void setSubspaceDimension(int subspaceDimension) {
        this.subspaceDimension = subspaceDimension;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public int getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
    }

    public int getBlurSupport() {
        return blurSupport;
    }

    public void setBlurSupport(int blurSupport) {
        this.blurSupport = blurSupport;
    }

    public int getOutputBlockRows() {
        return outputBlockRows;
    }

    public void setOutputBlockRows(int outputBlockRows) {
        this.outputBlockRows = outputBlockRows;
    }

    public long getSpectrumTimeoutMillis() {
        return spectrumTimeoutMillis;
    }

    public void setSpectrumTimeoutMillis(long spectrumTimeoutMillis) {
        this.spectrumTimeoutMillis = spectrumTimeoutMillis;
    }
}
