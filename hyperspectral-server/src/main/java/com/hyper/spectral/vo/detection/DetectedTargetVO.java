package com.hyper.spectral.vo.detection;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DetectedTargetVO {

    private String type;
    private String name;
    private int[] color;

    @JsonProperty("color_hex")
    private String colorHex;

    @JsonProperty("pixel_count")
    private int pixelCount;

    @JsonProperty("object_count")
    private int objectCount;

    @JsonProperty("max_value")
    private double maxValue;

    public DetectedTargetVO() {
    }

    public DetectedTargetVO(String type, String name, int[] color, String colorHex,
                            int pixelCount, int objectCount, double maxValue) {
        this.type = type;
        this.name = name;
        this.color = color;
        this.colorHex = colorHex;
        this.pixelCount = pixelCount;
        this.objectCount = objectCount;
        this.maxValue = maxValue;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getColor() {
        return color;
    }

    public void setColor(int[] color) {
        this.color = color;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public int getPixelCount() {
        return pixelCount;
    }

    public void setPixelCount(int pixelCount) {
        this.pixelCount = pixelCount;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }
}
