package com.github.tvbox.osc.bean;

public class Source {

    public Source() {
    }

    public Source(String sourceName, String sourceUrl) {
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
    }

    String sourceName;
    String sourceUrl;

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}