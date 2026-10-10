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
    public static void load(String url, Result result) { load(url, java.util.Collections.emptyMap(), 0, result); }
    private static void load(String url, java.util.Map<String, String> headers, int depth, Result result) {
        if (depth > 3) { result.onResult(null, "直播源存在循环引用，请更换来源"); return; }
        SourceManager.read(url, headers, (content, error) -> {
            if (content == null) { result.onResult(null, error); return; }
            try {
                String json = ApiConfig.FindResult(content, null);
                    if (json.trim().startsWith("{") && com.google.gson.JsonParser.parseString(json).getAsJsonObject().has("urls")) {
                        SourceManager.readConfig(url, (resolved, failure) -> {
                            if (resolved == null) result.onResult(null, failure);
                            else parseConfig(url, resolved, depth, result);
                        });
                        return;
                    }
                if (json.trim().startsWith("{") || json.trim().startsWith("[")) {
                    parseConfig(url, json, depth, result); return;
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
    private static void parseConfig(String url, String json, int depth, Result result) {
        try {
                    SourceDescriptor source = new SourceDescriptor(url, json);
                    SourceManager.record(url, json);
                    JsonArray groups = LiveConfigCompat.groups(source.lives(), url);
                    if (groups.size() > 0) { attachEpg(groups, source.rootEpg()); result.onResult(groups, ""); return; }
                    JsonObject plugin = null;
                    String preferredPlugin = com.orhanobut.hawk.Hawk.get("live_playlist_url", "");
                    for (JsonElement entry : source.lives()) if (entry.isJsonObject() && !SourceDescriptor.string(entry.getAsJsonObject(), "api").isEmpty()) {
                        if (plugin == null) plugin = entry.getAsJsonObject();
                        if (SourceDescriptor.resolve(url, SourceDescriptor.string(entry.getAsJsonObject(), "url")).equals(preferredPlugin)) { plugin = entry.getAsJsonObject(); break; }
                    }
                    if (plugin != null) { loadPlugin(source, plugin, result); return; }
                    java.util.List<String> playlists = source.playlists();
                    if (playlists.isEmpty()) { result.onResult(null, "这个源未提供直播，请选择其他直播源"); return; }
                    String preferred = com.orhanobut.hawk.Hawk.get("live_playlist_url", "");
                    if (playlists.remove(preferred)) playlists.add(0, preferred);
                    loadPlaylist(source, playlists, 0, depth + 1, result); return;
            } catch (Exception e) { result.onResult(null, "直播内容无法解析，请检查来源格式"); }
    }
    private static void loadPlugin(SourceDescriptor source, JsonObject live, Result result) {
        HeavyTaskUtil.executeNewTask(() -> {
            JsonArray ready = null; String error = "直播插件加载失败，请检查模块与来源";
            try {
                com.kukuqi.tvbox.osc.bean.SourceBean site = new com.kukuqi.tvbox.osc.bean.SourceBean();
                site.setKey("live:" + source.url + ":" + SourceDescriptor.string(live, "name"));
                JsonObject normalized = live.deepCopy(); ConfigCompat.normalize(normalized, source.url);
                site.setApi(SourceDescriptor.string(normalized, "api"));
                site.setExt(ConfigCompat.text(live.get("ext")));
                String jar = SourceDescriptor.string(live, "jar");
                if (jar.isEmpty() && source.content.isJsonObject()) jar = SourceDescriptor.string(source.content.getAsJsonObject(), "spider");
                site.setJar(SourceDescriptor.resolve(source.url, jar));
                Object spider = ApiConfig.get().getCSP(site);
                String text = (String) spider.getClass().getMethod("liveContent", String.class).invoke(spider, SourceDescriptor.resolve(source.url, SourceDescriptor.string(live, "url")));
                if (text.trim().startsWith("[") || text.trim().startsWith("{")) ready = LiveConfigCompat.groups(new SourceDescriptor(source.url, text).lives(), source.url);
                else {
                    LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels = new LinkedHashMap<>();
                    TxtSubscribe.parse(channels, text); ready = TxtSubscribe.live2JsonArray(channels);
                    LivePlaylistMetadata.apply(ready, text, source.url);
                }
                attachEpg(ready, SourceDescriptor.resolve(source.url, SourceDescriptor.string(live, "epg")));
                java.util.Map<String,String> headers = LiveConfigCompat.headers(live, java.util.Collections.emptyMap());
                for (JsonElement group : ready) for (JsonElement channel : ConfigCompat.array(group.getAsJsonObject(), "channels")) {
                    JsonObject object = new JsonObject(); java.util.Map<String,String> merged = new java.util.LinkedHashMap<>(headers);
                    merged.putAll(ConfigCompat.headers(channel.getAsJsonObject().get("header"))); merged.forEach(object::addProperty);
                    channel.getAsJsonObject().add("header", object);
                }
                if (ready.size() == 0) ready = null;
            } catch (NoSuchMethodException unsupported) { error = "该直播插件未提供 liveContent，请更新兼容的模块"; }
            catch (Exception failure) {}
            JsonArray groups = ready; String message = error;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> result.onResult(groups, groups == null ? message : ""));
        });
    }
    private static void loadPlaylist(SourceDescriptor source, java.util.List<String> urls, int index, int depth, Result result) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        for (JsonElement item : source.lives()) if (item.isJsonObject() && SourceDescriptor.resolve(source.url, SourceDescriptor.string(item.getAsJsonObject(), "url")).equals(urls.get(index))) {
            headers = LiveConfigCompat.headers(item.getAsJsonObject(), headers); break;
        }
        java.util.Map<String, String> inherited = headers;
        load(decode(urls.get(index)), headers, depth, (groups, error) -> {
            if (groups != null) {
                String configured = source.epg(urls.get(index));
                if (!configured.isEmpty()) for (JsonElement group : groups) group.getAsJsonObject().addProperty("epg", configured);
                for (JsonElement group : groups) for (JsonElement channel : ConfigCompat.array(group.getAsJsonObject(), "channels")) {
                    JsonObject header = new JsonObject();
                    java.util.Map<String, String> merged = new java.util.LinkedHashMap<>(inherited);
                    merged.putAll(ConfigCompat.headers(channel.getAsJsonObject().get("header"))); merged.forEach(header::addProperty);
                    channel.getAsJsonObject().add("header", header);
                }
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
}
