package com.kukuqi.tvbox.osc.util;

import android.net.Uri;
import android.util.Base64;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class LiveSourceLoader {
    public interface Result { void onResult(JsonArray groups, String error); }
    public static void load(String url, Result result) { load(url, 0, result); }
    private static void load(String url, int depth, Result result) {
        if (depth > 3) { result.onResult(null, "直播源存在循环引用，请更换来源"); return; }
        SourceManager.read(url, (content, error) -> {
            if (content == null) { result.onResult(null, error); return; }
            try {
                String json = ApiConfig.FindResult(content, null);
                if (json.trim().startsWith("{") || json.trim().startsWith("[")) {
                    SourceDescriptor source = new SourceDescriptor(url, json);
                    SourceManager.record(url, json);
                    JsonArray groups = normalize(source.lives(), url);
                    if (groups.size() > 0) { attachEpg(groups, source.rootEpg()); result.onResult(groups, ""); return; }
                    java.util.List<String> playlists = source.playlists();
                    if (playlists.isEmpty()) { result.onResult(null, "这个源未提供直播，请选择其他直播源"); return; }
                    loadPlaylist(source, playlists, 0, depth + 1, result); return;
                }
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels = new LinkedHashMap<>();
                TxtSubscribe.parse(channels, content);
                JsonArray groups = TxtSubscribe.live2JsonArray(channels);
                LivePlaylistMetadata.apply(groups, content, url);
                if (groups.size() == 0) result.onResult(null, "没有找到直播频道，支持直播配置、M3U 或 TXT");
                else result.onResult(groups, "");
            } catch (Exception e) { result.onResult(null, "直播内容无法解析，请检查来源格式"); }
        });
    }
    private static void loadPlaylist(SourceDescriptor source, java.util.List<String> urls, int index, int depth, Result result) {
        load(decode(urls.get(index)), depth, (groups, error) -> {
            if (groups != null) {
                String configured = source.epg(urls.get(index));
                if (!configured.isEmpty()) for (JsonElement group : groups) group.getAsJsonObject().addProperty("epg", configured);
                result.onResult(groups, error);
            }
            else if (index + 1 == urls.size()) result.onResult(null, error);
            else loadPlaylist(source, urls, index + 1, depth, result);
        });
    }
    private static void attachEpg(JsonArray groups, String epg) {
        if (!epg.isEmpty()) for (JsonElement group : groups) if (SourceDescriptor.string(group.getAsJsonObject(), "epg").isEmpty())
            group.getAsJsonObject().addProperty("epg", epg);
    }
    public static String epg(JsonArray groups) {
        return groups == null || groups.size() == 0 ? "" : SourceDescriptor.string(groups.get(0).getAsJsonObject(), "epg");
    }
    private static String decode(String value) {
        if (!value.startsWith("proxy://")) return value;
        try {
            String ext = Uri.parse(value).getQueryParameter("ext");
            if (ext == null) {
                int start = value.indexOf("ext=");
                if (start >= 0) ext = Uri.decode(value.substring(start + 4).split("&", 2)[0]);
            }
            if (ext == null) return value;
            return ext.startsWith("http") || ext.startsWith("content://") || ext.startsWith("file://")
                    ? ext : new String(Base64.decode(ext, Base64.DEFAULT | Base64.URL_SAFE), "UTF-8");
        } catch (Exception e) { return value; }
    }
    private static JsonArray normalize(JsonArray input, String base) {
        JsonArray result = new JsonArray();
        for (JsonElement item : input) {
            if (!item.isJsonObject()) continue;
            JsonObject group = item.getAsJsonObject();
            if (!group.has("channels") || !group.get("channels").isJsonArray()) continue;
            JsonArray channels = new JsonArray();
            for (JsonElement entry : group.getAsJsonArray("channels")) {
                if (!entry.isJsonObject()) continue;
                JsonObject original = entry.getAsJsonObject();
                JsonArray urls = new JsonArray();
                if (original.has("urls") && original.get("urls").isJsonArray()) {
                    for (JsonElement link : original.getAsJsonArray("urls")) if (link.isJsonPrimitive() && !link.getAsString().startsWith("proxy://")) urls.add(SourceDescriptor.resolve(base, link.getAsString()));
                } else if (!SourceDescriptor.string(original, "url").isEmpty()) urls.add(SourceDescriptor.resolve(base, SourceDescriptor.string(original, "url")));
                if (urls.size() == 0) continue;
                JsonObject channel = new JsonObject();
                for (String field : new String[]{"tvg-id", "tvg-name"}) channel.addProperty(field, SourceDescriptor.string(original, field));
                String guide = SourceDescriptor.string(original, "epg");
                if (!guide.isEmpty()) {
                    if (guide.contains("://") || guide.startsWith("/") || guide.contains("{") || guide.endsWith(".xml")) channel.addProperty("epg", SourceDescriptor.resolve(base, guide));
                    else if (SourceDescriptor.string(channel, "tvg-id").isEmpty()) channel.addProperty("tvg-id", guide);
                }
                channel.addProperty("name", SourceDescriptor.string(original, "name")); channel.add("urls", urls); channels.add(channel);
            }
            if (channels.size() == 0) continue;
            JsonObject output = new JsonObject();
            String name = SourceDescriptor.string(group, "group");
            if (name.isEmpty()) name = SourceDescriptor.string(group, "name");
            output.addProperty("group", name.isEmpty() ? "直播" : name); output.add("channels", channels); result.add(output);
            String epg = SourceDescriptor.string(group, "epg");
            if (!epg.isEmpty()) output.addProperty("epg", SourceDescriptor.resolve(base, epg));
        }
        return result;
    }
}
