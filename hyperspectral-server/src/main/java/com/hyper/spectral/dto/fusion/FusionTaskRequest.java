package com.hyper.spectral.dto.fusion;

import javax.validation.constraints.NotBlank;

public class FusionTaskRequest {

    @NotBlank
    public String taskName;

    @NotBlank
    public String hyperspectralCubeId;

    @NotBlank
    public String panchromaticImageId;

    @NotBlank
    public String strategyCode;
}
