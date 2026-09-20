package com.hyper.spectral.dto.acquisition;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class AcquisitionCommandRequest {

    @NotBlank
    public String taskName;

    @NotBlank
    public String sceneName;

    @NotBlank
    public String targetArea;

    @NotBlank
    public String deviceId;

    @Min(1)
    public int exposureMs;

    @Min(1)
    public int frameCount;
}
