package com.hyper.spectral.dto.preprocess;

import javax.validation.constraints.NotBlank;

public class PreprocessTaskRequest {

    @NotBlank
    public String taskName;

    @NotBlank
    public String sourceCubeId;

    @NotBlank
    public String templateCode;

    @NotBlank
    public String outputFormat;
}
