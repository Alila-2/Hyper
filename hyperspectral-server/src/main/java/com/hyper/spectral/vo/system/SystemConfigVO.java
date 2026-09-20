package com.hyper.spectral.vo.system;

public class SystemConfigVO {

    public String configKey;
    public String configName;
    public String configValue;
    public String category;
    public String description;

    public SystemConfigVO() {
    }

    public SystemConfigVO(String configKey, String configName, String configValue, String category, String description) {
        this.configKey = configKey;
        this.configName = configName;
        this.configValue = configValue;
        this.category = category;
        this.description = description;
    }
}
