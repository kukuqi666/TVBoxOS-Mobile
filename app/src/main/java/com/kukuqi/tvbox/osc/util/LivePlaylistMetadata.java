package com.kukuqi.tvbox.osc.util;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/** Preserve M3U guide metadata alongside the existing TXT/M3U channel parser. */
public final class LivePlaylistMetadata {
    public static String attribute(String line, String key) {
        Matcher matcher = Pattern.compile("(?:^|\\s)" + Pattern.quote(key) + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))", Pattern.CASE_INSENSITIVE).matcher(line);
        return matcher.find() ? (matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3)) : "";
    }
    private static String group(String value) { return value.isEmpty() ? "未分组" : value; }
    public static void apply(JsonArray groups, String content, String base) {
        Map<String, JsonObject> metadata = new HashMap<>(); String epg = "";
        String pendingName = "", pendingGroup = ""; JsonObject pending = null;
        for (String raw : content.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTM3U")) {
                epg = attribute(line, "url-tvg"); if (epg.isEmpty()) epg = attribute(line, "tvg-url");
                if (!epg.isEmpty()) epg = SourceDescriptor.resolve(base, epg.split(",", 2)[0]);
            } else if (line.startsWith("#EXTINF")) {
                int comma = commaOutsideQuotes(line);
                if (comma < 0) continue;
                String name = line.substring(comma + 1).trim();
                JsonObject channel = new JsonObject();
                channel.addProperty("tvg-id", attribute(line, "tvg-id"));
                channel.addProperty("tvg-name", attribute(line, "tvg-name"));
                String guide = attribute(line, "epg"); if (!guide.isEmpty()) channel.addProperty("epg", SourceDescriptor.resolve(base, guide));
                pendingName = name; pendingGroup = group(attribute(line, "group-title")); pending = channel;
            } else if (line.startsWith("#EXTGRP:") && pending != null) {
                pendingGroup = group(line.substring(8).trim());
            } else if (!line.isEmpty() && !line.startsWith("#") && pending != null) {
                metadata.put(pendingGroup + "\n" + pendingName, pending); pending = null;
            }
        }
        for (JsonElement group : groups) {
            JsonObject object = group.getAsJsonObject(); if (!epg.isEmpty()) object.addProperty("epg", epg);
            for (JsonElement item : object.getAsJsonArray("channels")) {
                JsonObject channel = item.getAsJsonObject();
                JsonObject details = metadata.get(SourceDescriptor.string(object, "group") + "\n" + SourceDescriptor.string(channel, "name"));
                if (details != null) for (Map.Entry<String, JsonElement> entry : details.entrySet()) channel.add(entry.getKey(), entry.getValue());
            }
        }
    }
    public static int commaOutsideQuotes(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '\"' || value == '\'') { if (quote == 0) quote = value; else if (quote == value) quote = 0; }
            else if (value == ',' && quote == 0) return i;
        }
        return -1;
    }
}
