package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** FongMi config conventions, independent of Android and the selected player. */
public final class ConfigCompat {
    public interface Fetcher { Resource fetch(String url) throws Exception; }
    public static final class Resource {
        public final String url, content;
        public Resource(String url, String content) { this.url = url; this.content = content; }
    }
    private ConfigCompat() {}

    public static JsonObject resolve(Resource resource, Fetcher fetcher) throws Exception {
        return resolve(resource, fetcher, new HashSet<>(), 0);
    }
    private static JsonObject resolve(Resource resource, Fetcher fetcher, Set<String> visited, int depth) throws Exception {
        if (depth > 8 || !visited.add(resource.url)) throw new IllegalArgumentException("订阅仓库存在循环引用");
        JsonObject object = JsonParser.parseString(resource.content).getAsJsonObject();
        if (object.has("msg")) throw new IllegalArgumentException(SourceDescriptor.string(object, "msg"));
        if (object.has("urls")) {
            JsonArray urls = array(object, "urls");
            if (urls.size() == 0 || !urls.get(0).isJsonObject()) throw new IllegalArgumentException("订阅仓库没有配置地址");
            String url = SourceDescriptor.string(urls.get(0).getAsJsonObject(), "url");
            if (url.isEmpty()) throw new IllegalArgumentException("订阅仓库地址为空");
            return resolve(fetcher.fetch(SourceDescriptor.resolve(resource.url, url)), fetcher, visited, depth + 1);
        }
        normalize(object, resource.url);
        // FongMi allows external arrays for rules/headers/proxy/doh, but not sites/parses.
        for (String key : new String[]{"rules", "headers", "proxy", "doh"}) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) continue;
            JsonArray result = new JsonArray();
            JsonArray inputs = new JsonArray();
            if (value.isJsonArray()) inputs = value.getAsJsonArray(); else if (value.isJsonPrimitive()) inputs.add(value);
            for (JsonElement item : inputs) {
                if (item.isJsonObject()) result.add(item);
                else if (item.isJsonPrimitive()) {
                    try {
                        Resource fetched = fetcher.fetch(SourceDescriptor.resolve(resource.url, item.getAsString()));
                        JsonElement parsed = JsonParser.parseString(fetched.content);
                        if (parsed.isJsonArray()) for (JsonElement entry : parsed.getAsJsonArray()) {
                            if (entry.isJsonObject()) { normalize(entry, fetched.url); result.add(entry); }
                        }
                    } catch (Exception ignored) { /* FongMi treats an unavailable optional list as empty. */ }
                }
            }
            object.add(key, result);
        }
        return object;
    }

    public static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    public static String text(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString().trim() : value.toString();
    }
    public static Map<String, String> headers(JsonElement value) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            if (value != null && value.isJsonPrimitive()) value = JsonParser.parseString(value.getAsString());
            if (value != null && value.isJsonObject()) for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) result.put(headerKey(entry.getKey()), entry.getValue().getAsString().trim());
            }
        } catch (Exception ignored) {}
        return result;
    }
    public static String headerKey(String key) {
        if (key.equalsIgnoreCase("user-agent") || key.equalsIgnoreCase("ua")) return "User-Agent";
        if (key.equalsIgnoreCase("referer")) return "Referer";
        if (key.equalsIgnoreCase("cookie")) return "Cookie";
        if (key.equalsIgnoreCase("origin")) return "Origin";
        return key;
    }

    /** Resolve only leading relative paths; never rewrite ./ within JS query parameters or regexes. */
    public static void normalize(JsonElement element, String base) {
        if (element.isJsonArray()) { for (JsonElement item : element.getAsJsonArray()) normalize(item, base); return; }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey(); JsonElement value = entry.getValue();
            if ((key.equals("urls") || key.equals("wallpaper")) && value.isJsonArray()) {
                JsonArray paths = value.getAsJsonArray();
                for (int i = 0; i < paths.size(); i++) if (paths.get(i).isJsonPrimitive()) {
                    String path = paths.get(i).getAsString();
                    if (path.startsWith("./") || path.startsWith("../") || path.startsWith("/")) paths.set(i, new com.google.gson.JsonPrimitive(SourceDescriptor.resolve(base, path)));
                }
            }
            if (value.isJsonObject() || value.isJsonArray()) { normalize(value, base); continue; }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
            if (key.equals("spider") || key.equals("jar") || key.equals("api") || key.equals("ext") || key.equals("url")
                    || key.equals("playUrl") || key.equals("wallpaper") || key.equals("epg") || key.equals("logo")) {
                String path = value.getAsString().trim();
                if (path.startsWith("./") || path.startsWith("../") || path.startsWith("/"))
                    entry.setValue(new com.google.gson.JsonPrimitive(SourceDescriptor.resolve(base, path)));
            }
            if (key.equals("header") && value.getAsJsonPrimitive().isString()) {
                JsonObject header = new JsonObject(); headers(value).forEach(header::addProperty); entry.setValue(header);
            }
        }
    }
}
