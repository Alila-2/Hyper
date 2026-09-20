package com.hyper.spectral.vo.detection;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DetectionPreviewResponse {

    private boolean success;
    private String image;
    private String message;

    @JsonProperty("file_type")
    private String fileType;

    private int[] shape;

    public DetectionPreviewResponse() {
    }

    public DetectionPreviewResponse(boolean success, String image, String message, String fileType, int[] shape) {
        this.success = success;
        this.image = image;
        this.message = message;
        this.fileType = fileType;
        this.shape = shape;
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

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public int[] getShape() {
        return shape;
    }

    public void setShape(int[] shape) {
        this.shape = shape;
    }
}
