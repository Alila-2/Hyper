package com.hyper.spectral.vo.preprocess;

public class PreprocessTemplateVO {

    public String templateCode;
    public String templateName;
    public String description;
    public String steps;

    public PreprocessTemplateVO() {
    }

    public PreprocessTemplateVO(String templateCode, String templateName, String description, String steps) {
        this.templateCode = templateCode;
        this.templateName = templateName;
        this.description = description;
        this.steps = steps;
    }
}
