package com.hyper.spectral.vo.visualization;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 旧可视化页面加载高光谱数据响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoadHyperspectralDataResponse {

    private boolean success;
    private String message;
    private String originalImage;
    private String pseudoColorImage;
    private int originalWidth;
    private int originalHeight;
    private int pseudoWidth;
    private int pseudoHeight;
    private List<Integer> bands;
    private int totalBands;
    private List<Integer> visualizationBands;

    public LoadHyperspectralDataResponse() {
    }

    public LoadHyperspectralDataResponse(boolean success, String message, String originalImage, String pseudoColorImage,
                                         int originalWidth, int originalHeight, int pseudoWidth, int pseudoHeight,
                                         List<Integer> bands, int totalBands, List<Integer> visualizationBands) {
        this.success = success;
        this.message = message;
        this.originalImage = originalImage;
        this.pseudoColorImage = pseudoColorImage;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.pseudoWidth = pseudoWidth;
        this.pseudoHeight = pseudoHeight;
        this.bands = bands;
        this.totalBands = totalBands;
        this.visualizationBands = visualizationBands;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOriginalImage() {
        return originalImage;
    }

    public void setOriginalImage(String originalImage) {
        this.originalImage = originalImage;
    }

    public String getPseudoColorImage() {
        return pseudoColorImage;
    }

    public void setPseudoColorImage(String pseudoColorImage) {
        this.pseudoColorImage = pseudoColorImage;
    }

    public int getOriginalWidth() {
        return originalWidth;
    }

    public void setOriginalWidth(int originalWidth) {
        this.originalWidth = originalWidth;
    }

    public int getOriginalHeight() {
        return originalHeight;
    }

    public void setOriginalHeight(int originalHeight) {
        this.originalHeight = originalHeight;
    }

    public int getPseudoWidth() {
        return pseudoWidth;
    }

    public void setPseudoWidth(int pseudoWidth) {
        this.pseudoWidth = pseudoWidth;
    }

    public int getPseudoHeight() {
        return pseudoHeight;
    }

    public void setPseudoHeight(int pseudoHeight) {
        this.pseudoHeight = pseudoHeight;
    }

    public List<Integer> getBands() {
        return bands;
    }

    public void setBands(List<Integer> bands) {
        this.bands = bands;
    }

    public int getTotalBands() {
        return totalBands;
    }

    public void setTotalBands(int totalBands) {
        this.totalBands = totalBands;
    }

    public List<Integer> getVisualizationBands() {
        return visualizationBands;
    }

    public void setVisualizationBands(List<Integer> visualizationBands) {
        this.visualizationBands = visualizationBands;
    }
}
