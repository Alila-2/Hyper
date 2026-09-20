package com.hyper.spectral.vo.detection;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DetectedObjectVO {

    private int id;
    private int size;
    private int[] bbox;
    private double[] center;

    @JsonProperty("max_confidence")
    private double maxConfidence;

    @JsonProperty("mean_confidence")
    private double meanConfidence;

    public DetectedObjectVO() {
    }

    public DetectedObjectVO(int id, int size, int[] bbox, double[] center,
                            double maxConfidence, double meanConfidence) {
        this.id = id;
        this.size = size;
        this.bbox = bbox;
        this.center = center;
        this.maxConfidence = maxConfidence;
        this.meanConfidence = meanConfidence;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int[] getBbox() {
        return bbox;
    }

    public void setBbox(int[] bbox) {
        this.bbox = bbox;
    }

    public double[] getCenter() {
        return center;
    }

    public void setCenter(double[] center) {
        this.center = center;
    }

    public double getMaxConfidence() {
        return maxConfidence;
    }

    public void setMaxConfidence(double maxConfidence) {
        this.maxConfidence = maxConfidence;
    }

    public double getMeanConfidence() {
        return meanConfidence;
    }

    public void setMeanConfidence(double meanConfidence) {
        this.meanConfidence = meanConfidence;
    }
}
