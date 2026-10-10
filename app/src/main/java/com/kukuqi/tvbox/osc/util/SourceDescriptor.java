package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Describes each part independently: a valid VOD source need not contain live or wallpaper. */
public final class SourceDescriptor {
    public final String url;
    public final JsonElement content;
    public SourceDescriptor(String url, String json) { this.url = url; content = JsonParser.parseString(json); }
    public boolean hasVod() { return content.isJsonObject() && array(content.getAsJsonObject(), "sites").size() > 0; }
    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
    public JsonArray lives() {
        if (content.isJsonArray()) return content.getAsJsonArray();
        if (!content.isJsonObject()) return new JsonArray();
        JsonObject object = content.getAsJsonObject();
        if (object.has("channels") || object.has("channel") || object.has("groups")) { JsonArray result = new JsonArray(); result.add(object); return result; }
        return array(object, "lives");
    }
    public boolean hasLive() {
        for (JsonElement item : lives()) {
            if (!item.isJsonObject()) continue;
            JsonObject object = item.getAsJsonObject();
            if (array(object, "channels").size() > 0 || array(object, "channel").size() > 0 || array(object, "groups").size() > 0 || !string(object, "url").isEmpty() || !string(object, "api").isEmpty()) return true;
        }
        return false;
    }
    public List<String> wallpapers() {
        List<String> result = new ArrayList<>();
        if (!content.isJsonObject()) return result;
        JsonElement value = content.getAsJsonObject().get("wallpaper");
        if (value == null || value.isJsonNull()) return result;
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive()) addWall(result, item.getAsString());
                else if (item.isJsonObject()) addWall(result, string(item.getAsJsonObject(), "url"));
            }
        } else if (value.isJsonPrimitive()) for (String item : value.getAsString().split(",")) addWall(result, item);
        return result;
    }
    private void addWall(List<String> result, String value) {
        value = value.trim();
        if (!value.isEmpty()) result.add(resolve(url, value));
    }
    public String firstPlaylist() {
        List<String> urls = playlists();
        return urls.isEmpty() ? "" : urls.get(0);
    }
    public List<String> playlists() {
        List<String> urls = new ArrayList<>();
        for (JsonElement item : lives()) if (item.isJsonObject()) {
            JsonObject group = item.getAsJsonObject();
            String value = string(group, "url");
            if (!value.isEmpty()) urls.add(resolve(url, value));
            for (JsonElement entry : array(group, "channels")) if (entry.isJsonObject()) {
                JsonObject channel = entry.getAsJsonObject();
                for (JsonElement link : array(channel, "urls")) if (link.isJsonPrimitive() && link.getAsString().startsWith("proxy://"))
                    urls.add(link.getAsString());
            }
        }
        return urls;
    }
    public String epg(String playlist) {
        for (JsonElement item : lives()) if (item.isJsonObject()) {
            JsonObject live = item.getAsJsonObject();
            if (playlist.isEmpty() || resolve(url, string(live, "url")).equals(playlist)) {
                String epg = string(live, "epg");
                if (!epg.isEmpty()) return resolve(url, epg);
            }
        }
        return content.isJsonObject() ? resolveEpg(content.getAsJsonObject()) : "";
    }
    private String resolveEpg(JsonObject object) {
        String epg = string(object, "epg");
        return epg.isEmpty() ? "" : resolve(url, epg);
    }
    public String rootEpg() { return content.isJsonObject() ? resolveEpg(content.getAsJsonObject()) : ""; }
    public String description() {
        List<String> parts = new ArrayList<>();
        if (hasVod()) parts.add("点播"); if (hasLive()) parts.add("直播"); if (!wallpapers().isEmpty()) parts.add("壁纸");
        StringBuilder result = new StringBuilder();
        for (String part : parts) { if (result.length() > 0) result.append(" · "); result.append(part); }
        return parts.isEmpty() ? "未提供点播、直播或壁纸" : result.toString();
    }
    public static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }
    public static String resolve(String base, String value) {
        if (value == null || value.isEmpty()) return "";
        try { return new URI(base).resolve(value.replace("{", "%7B").replace("}", "%7D")).toString()
                .replace("%7B", "{").replace("%7D", "}"); } catch (Exception ignored) { return value; }
    }
}
