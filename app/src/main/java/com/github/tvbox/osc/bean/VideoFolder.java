package com.github.tvbox.osc.bean;

import java.util.List;


public class VideoFolder {
    public VideoFolder(String name, List<VideoInfo> videoList) {
        this.name = name;
        this.videoList = videoList;
    }

    String name;
    List<VideoInfo> videoList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<VideoInfo> getVideoList() {
        return videoList;
    }

    public void setVideoList(List<VideoInfo> videoList) {
        this.videoList = videoList;
    }
}