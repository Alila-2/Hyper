package com.hyper.spectral.vo.detection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TargetStatisticsVO {

    @JsonProperty("detected_pixels")
    private int detectedPixels;

    @JsonProperty("object_count")
    private int objectCount;

    private List<DetectedObjectVO> objects;

    @JsonProperty("max_confidence")
    private double maxConfidence;

    @JsonProperty("detection_threshold")
    private double detectionThreshold;

    @JsonProperty("is_detected")
    private boolean detected;

    @JsonProperty("mean_confidence")
    private double meanConfidence;

    @JsonProperty("coverage_percentage")
    private double coveragePercentage;

    public TargetStatisticsVO() {
    }

    public TargetStatisticsVO(int detectedPixels, int objectCount, List<DetectedObjectVO> objects,
                              double maxConfidence, double detectionThreshold, boolean detected,
                              double meanConfidence, double coveragePercentage) {
        this.detectedPixels = detectedPixels;
        this.objectCount = objectCount;
        this.objects = objects;
        this.maxConfidence = maxConfidence;
        this.detectionThreshold = detectionThreshold;
        this.detected = detected;
        this.meanConfidence = meanConfidence;
        this.coveragePercentage = coveragePercentage;
    }

    public int getDetectedPixels() {
        return detectedPixels;
    }

    public void setDetectedPixels(int detectedPixels) {
        this.detectedPixels = detectedPixels;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }

    public List<DetectedObjectVO> getObjects() {
        return objects;
    }

    public void setObjects(List<DetectedObjectVO> objects) {
        this.objects = objects;
    }

    public double getMaxConfidence() {
        return maxConfidence;
    }

    public void setMaxConfidence(double maxConfidence) {
        this.maxConfidence = maxConfidence;
    }

    public double getDetectionThreshold() {
        return detectionThreshold;
    }

    public void setDetectionThreshold(double detectionThreshold) {
        this.detectionThreshold = detectionThreshold;
    }

    public boolean isDetected() {
        return detected;
    }

    public void setDetected(boolean detected) {
        this.detected = detected;
    }

    public double getMeanConfidence() {
        return meanConfidence;
    }

    public void setMeanConfidence(double meanConfidence) {
        this.meanConfidence = meanConfidence;
    }

    public double getCoveragePercentage() {
        return coveragePercentage;
    }

    public void setCoveragePercentage(double coveragePercentage) {
        this.coveragePercentage = coveragePercentage;
    }
}
