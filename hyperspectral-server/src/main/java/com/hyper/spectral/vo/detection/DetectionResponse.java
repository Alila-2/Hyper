package com.hyper.spectral.vo.detection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class DetectionResponse {

    private boolean success;
    private String image;
    private String message;

    @JsonProperty("detected_targets")
    private List<DetectedTargetVO> detectedTargets;

    @JsonProperty("target_statistics")
    private Map<String, TargetStatisticsVO> targetStatistics;

    private boolean cached;
    private double runtime;
    private String algorithm;
    private int wzml;
    private int jsml;
    private Double pyl;
    private Double jsjd;

    public DetectionResponse() {
    }

    public DetectionResponse(boolean success, String image, String message,
                             List<DetectedTargetVO> detectedTargets,
                             Map<String, TargetStatisticsVO> targetStatistics,
                             boolean cached, double runtime) {
        this.success = success;
        this.image = image;
        this.message = message;
        this.detectedTargets = detectedTargets;
        this.targetStatistics = targetStatistics;
        this.cached = cached;
        this.runtime = runtime;
        this.wzml = 0;
        this.jsml = 0;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<DetectedTargetVO> getDetectedTargets() {
        return detectedTargets;
    }

    public void setDetectedTargets(List<DetectedTargetVO> detectedTargets) {
        this.detectedTargets = detectedTargets;
    }

    public Map<String, TargetStatisticsVO> getTargetStatistics() {
        return targetStatistics;
    }

    public void setTargetStatistics(Map<String, TargetStatisticsVO> targetStatistics) {
        this.targetStatistics = targetStatistics;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public double getRuntime() {
        return runtime;
    }

    public void setRuntime(double runtime) {
        this.runtime = runtime;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getWzml() {
        return wzml;
    }

    public void setWzml(int wzml) {
        this.wzml = wzml;
    }

    public int getJsml() {
        return jsml;
    }

    public void setJsml(int jsml) {
        this.jsml = jsml;
    }

    public Double getPyl() {
        return pyl;
    }

    public void setPyl(Double pyl) {
        this.pyl = pyl;
    }

    public Double getJsjd() {
        return jsjd;
    }

    public void setJsjd(Double jsjd) {
        this.jsjd = jsjd;
    }
}
