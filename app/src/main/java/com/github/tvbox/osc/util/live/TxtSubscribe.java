package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {
    private static final Pattern GROUP_TITLE_PATTERN = Pattern.compile("group-title\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|([^\\s,]+))", Pattern.CASE_INSENSITIVE);

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine();
            LinkedHashMap<String, ArrayList<String>> ungroupedChannels = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> currentTextGroup = ungroupedChannels;
            String m3uChannelName = null;
            String m3uGroupName = "未分组";
            while (readLine != null) {
                String line = readLine.trim();
                if (line.startsWith("#EXTINF")) {
                    int nameIndex = line.lastIndexOf(',');
                    m3uChannelName = nameIndex >= 0 ? line.substring(nameIndex + 1).trim() : "";
                    m3uGroupName = getM3uGroupName(line);
                } else if (line.startsWith("#EXTGRP:")) {
                    m3uGroupName = line.substring("#EXTGRP:".length()).trim();
                } else if (m3uChannelName != null && !m3uChannelName.isEmpty() && isStreamUrl(line)) {
                    addChannel(linkedHashMap, m3uGroupName, m3uChannelName, line);
                    m3uChannelName = null;
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] split = line.split(",", 2);
                    if (split.length >= 2) {
                        if (line.contains("#genre#")) {
                            String groupName = split[0].trim();
                            if (!linkedHashMap.containsKey(groupName)) {
                                linkedHashMap.put(groupName, new LinkedHashMap<>());
                            }
                            currentTextGroup = linkedHashMap.get(groupName);
                        } else {
                            String channelName = split[0].trim();
                            for (String url : split[1].trim().split("#")) {
                                String channelUrl = url.trim();
                                if (isStreamUrl(channelUrl)) {
                                    addChannel(currentTextGroup, channelName, channelUrl);
                                }
                            }
                        }
                    }
                }
                readLine = bufferedReader.readLine();
            }
            bufferedReader.close();
            if (!ungroupedChannels.isEmpty()) {
                linkedHashMap.put("未分组", ungroupedChannels);
            }
        } catch (Throwable unused) {
        }
    }

    private static String getM3uGroupName(String line) {
        Matcher matcher = GROUP_TITLE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return "未分组";
        }
        String groupName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return groupName == null || groupName.trim().isEmpty() ? "未分组" : groupName.trim();
    }

    private static boolean isStreamUrl(String url) {
        return url.startsWith("http") || url.startsWith("rtp") || url.startsWith("rtsp") || url.startsWith("rtmp");
    }

    private static void addChannel(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> groups,
                                   String groupName, String channelName, String channelUrl) {
        LinkedHashMap<String, ArrayList<String>> channels = groups.get(groupName);
        if (channels == null) {
            channels = new LinkedHashMap<>();
            groups.put(groupName, channels);
        }
        addChannel(channels, channelName, channelUrl);
    }

    private static void addChannel(LinkedHashMap<String, ArrayList<String>> channels, String channelName, String channelUrl) {
        ArrayList<String> urls = channels.get(channelName);
        if (urls == null) {
            urls = new ArrayList<>();
            channels.put(channelName, urls);
        }
        if (!urls.contains(channelUrl)) {
            urls.add(channelUrl);
        }
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (!linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (!arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                        } catch (Throwable e) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable e) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}
