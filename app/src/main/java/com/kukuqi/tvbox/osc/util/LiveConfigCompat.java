package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapts FongMi groups/channel and legacy group/channels without discarding headers. */
public final class LiveConfigCompat {
    private LiveConfigCompat() {}
    public static Map<String, String> headers(JsonObject object, Map<String, String> parent) {
        Map<String, String> result = new LinkedHashMap<>(parent);
        result.putAll(ConfigCompat.headers(object.get("header")));
        for (String key : new String[]{"ua", "referer", "origin"}) {
            String value = SourceDescriptor.string(object, key);
            if (!value.isEmpty()) result.put(ConfigCompat.headerKey(key), value);
        }
        return result;
    }
    public static JsonArray groups(JsonArray input, String base) {
        JsonArray result = new JsonArray();
        append(result, input, base, new LinkedHashMap<>(), "");
        return result;
    }
    private static void append(JsonArray result, JsonArray input, String base, Map<String, String> parent, String parentEpg) {
        for (JsonElement item : input) {
            if (!item.isJsonObject()) continue;
            JsonObject group = item.getAsJsonObject();
            Map<String, String> groupHeaders = headers(group, parent);
            String guide = SourceDescriptor.string(group, "epg");
            guide = guide.isEmpty() ? parentEpg : SourceDescriptor.resolve(base, guide);
            if (group.has("groups")) { append(result, ConfigCompat.array(group, "groups"), base, groupHeaders, guide); continue; }
            JsonArray entries = ConfigCompat.array(group, group.has("channel") ? "channel" : "channels");
            JsonArray channels = new JsonArray();
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) continue;
                JsonObject original = entry.getAsJsonObject(), channel = original.deepCopy();
                JsonArray urls = new JsonArray();
                for (JsonElement link : ConfigCompat.array(original, "urls")) if (link.isJsonPrimitive()) {
                    String url = link.getAsString();
                    if (!url.startsWith("proxy://")) urls.add(SourceDescriptor.resolve(base, url));
                }
                if (urls.size() == 0 && !SourceDescriptor.string(original, "url").isEmpty())
                    urls.add(SourceDescriptor.resolve(base, SourceDescriptor.string(original, "url")));
                if (urls.size() == 0) continue;
                channel.add("urls", urls);
                alias(channel, "tvg-id", "tvgId"); alias(channel, "tvg-name", "tvgName");
                JsonObject header = new JsonObject(); headers(original, groupHeaders).forEach(header::addProperty);
                channel.add("header", header);
                String epg = SourceDescriptor.string(original, "epg");
                if (!epg.isEmpty()) {
                    if (epg.contains("://") || epg.startsWith("/") || epg.contains("{") || epg.endsWith(".xml")) channel.addProperty("epg", SourceDescriptor.resolve(base, epg));
                    else { if (SourceDescriptor.string(channel, "tvg-id").isEmpty()) channel.addProperty("tvg-id", epg); channel.remove("epg"); }
                }
                channels.add(channel);
            }
            if (channels.size() == 0) continue;
            JsonObject output = new JsonObject();
            String name = SourceDescriptor.string(group, "group");
            if (name.isEmpty()) name = SourceDescriptor.string(group, "name");
            String pass = SourceDescriptor.string(group, "pass");
            if (!pass.isEmpty() && !name.contains("_")) name += "_" + pass;
            output.addProperty("group", name.isEmpty() ? "直播" : name); output.add("channels", channels);
            if (!guide.isEmpty()) output.addProperty("epg", guide);
            result.add(output);
        }
    }
    private static void alias(JsonObject object, String target, String source) {
        if (SourceDescriptor.string(object, target).isEmpty()) object.addProperty(target, SourceDescriptor.string(object, source));
    }
}
