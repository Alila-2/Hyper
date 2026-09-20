package com.hyper.spectral.vo.fusion;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 融合数据基本信息兼容响应。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FusionDataInfoResponse {

    private int width;
    private int height;
    private int bands;
    private String dataType;
    private List<Integer> dataShape;

    public FusionDataInfoResponse() {
    }

    public FusionDataInfoResponse(int width, int height, int bands, String dataType, List<Integer> dataShape) {
        this.width = width;
        this.height = height;
        this.bands = bands;
        this.dataType = dataType;
        this.dataShape = dataShape;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getBands() {
        return bands;
    }

    public void setBands(int bands) {
        this.bands = bands;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<Integer> getDataShape() {
        return dataShape;
    }

    public void setDataShape(List<Integer> dataShape) {
        this.dataShape = dataShape;
    }
}
