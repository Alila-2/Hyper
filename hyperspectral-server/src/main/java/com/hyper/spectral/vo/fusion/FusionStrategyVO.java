package com.hyper.spectral.vo.fusion;

public class FusionStrategyVO {

    public String strategyCode;
    public String strategyName;
    public String description;
    public String outputResolution;

    public FusionStrategyVO() {
    }

    public FusionStrategyVO(String strategyCode, String strategyName, String description, String outputResolution) {
        this.strategyCode = strategyCode;
        this.strategyName = strategyName;
        this.description = description;
        this.outputResolution = outputResolution;
    }
}
