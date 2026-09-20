package com.hyper.spectral.vo.visualization;

public class PseudoColorProfileVO {

    public String profileId;
    public String profileName;
    public String channels;
    public String description;

    public PseudoColorProfileVO() {
    }

    public PseudoColorProfileVO(String profileId, String profileName, String channels, String description) {
        this.profileId = profileId;
        this.profileName = profileName;
        this.channels = channels;
        this.description = description;
    }
}
