package com.hyper.spectral.vo.visualization;

public class SpectralImageVO {

    public String imageId;
    public String cubeId;
    public String mode;
    public int bandIndex;
    public String resolution;
    public String capturedAt;

    public SpectralImageVO() {
    }

    public SpectralImageVO(String imageId, String cubeId, String mode, int bandIndex, String resolution, String capturedAt) {
        this.imageId = imageId;
        this.cubeId = cubeId;
        this.mode = mode;
        this.bandIndex = bandIndex;
        this.resolution = resolution;
        this.capturedAt = capturedAt;
    }
}
