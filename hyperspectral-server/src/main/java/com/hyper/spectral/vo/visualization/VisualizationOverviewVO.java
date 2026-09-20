package com.hyper.spectral.vo.visualization;

public class VisualizationOverviewVO {

    public int loadedCubes;
    public int activeLayers;
    public int selectedBand;
    public String renderMode;

    public VisualizationOverviewVO() {
    }

    public VisualizationOverviewVO(int loadedCubes, int activeLayers, int selectedBand, String renderMode) {
        this.loadedCubes = loadedCubes;
        this.activeLayers = activeLayers;
        this.selectedBand = selectedBand;
        this.renderMode = renderMode;
    }
}
