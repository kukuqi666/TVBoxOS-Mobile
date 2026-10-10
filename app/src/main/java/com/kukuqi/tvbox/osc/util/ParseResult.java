package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON parser protocol shared by standalone and aggregate parsers. */
public final class ParseResult {
    public final String url;
    public final Map<String, String> headers;
    private ParseResult(String url, Map<String, String> headers) { this.url = url; this.headers = headers; }
    public static ParseResult read(String json, Map<String, String> fallback) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : new JsonObject();
        String url = SourceDescriptor.string(root, "url");
        if (url.isEmpty()) url = SourceDescriptor.string(data, "url");
        if (url.startsWith("//")) url = "http:" + url;
        if (url.isEmpty()) throw new IllegalArgumentException("解析没有返回播放地址");
        Map<String, String> headers = new LinkedHashMap<>();
        if (fallback != null) headers.putAll(fallback);
        addHeaders(headers, data); addHeaders(headers, root);
        return new ParseResult(url, headers);
    }
    private static void addHeaders(Map<String, String> result, JsonObject object) {
        result.putAll(ConfigCompat.headers(object.get("header")));
        for (String name : new String[]{"ua", "user-agent", "User-Agent", "referer", "Referer", "cookie", "Cookie", "origin", "Origin"}) {
            JsonElement value = object.get(name);
            if (value != null && value.isJsonPrimitive() && !value.getAsString().trim().isEmpty())
                result.put(ConfigCompat.headerKey(name), value.getAsString().trim());
        }
    }
}
