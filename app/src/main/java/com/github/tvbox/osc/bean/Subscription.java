package com.github.tvbox.osc.bean;


public class Subscription {
    public Subscription() {
    }

    public Subscription(String name, String url) {
        this.name = name;
        this.url = url;
    }

    String name;
    String url;
    //选择状态
    boolean isChecked;
    //置顶
    private boolean top;

    public boolean isTop() {
        return top;
    }

    public void setTop(boolean top) {
        this.top = top;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public Subscription setChecked(boolean checked) {
        isChecked = checked;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}