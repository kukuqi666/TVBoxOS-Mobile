package com.kukuqi.tvbox.osc.danmaku;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bilibili XML and DandanPlay JSON comments; timing is always media time in milliseconds. */
public final class DanmakuTimeline {
    public static final class Entry {
        public final long time;
        public final int mode, color;
        public final float size;
        public final String text;
        public Entry(long time, int mode, int color, float size, String text) {
            this.time = time; this.mode = mode; this.color = color | 0xff000000;
            this.size = Math.max(10, Math.min(60, size)); this.text = text;
        }
    }
    public final List<Entry> entries;
    private DanmakuTimeline(List<Entry> entries) { entries.sort(Comparator.comparingLong(e -> e.time)); this.entries = entries; }
    public static DanmakuTimeline parse(String content) {
        List<Entry> result = new ArrayList<>();
        if (content.trim().startsWith("[") || content.trim().startsWith("{")) {
            JsonElement root = JsonParser.parseString(content);
            JsonArray items = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("comments");
            if (items != null) for (JsonElement item : items) if (item.isJsonObject()) {
                JsonObject object = item.getAsJsonObject();
                if (object.has("p") && object.has("m")) add(result, object.get("p").getAsString(), object.get("m").getAsString(), false);
            }
        } else {
            Matcher matcher = Pattern.compile("<d\\s+[^>]*p=[\"']([^\"']+)[\"'][^>]*>(.*?)</d>", Pattern.DOTALL).matcher(content);
            while (matcher.find() && result.size() < 100000) add(result, matcher.group(1), decode(matcher.group(2)), true);
        }
        return new DanmakuTimeline(result);
    }
    private static void add(List<Entry> result, String parameter, String text, boolean xml) {
        if (result.size() >= 100000 || text.trim().isEmpty()) return;
        try {
            String[] fields = parameter.split(",");
            long time = (long) (Double.parseDouble(fields[0]) * 1000);
            int mode = Integer.parseInt(fields[1]);
            float size = xml ? Float.parseFloat(fields[2]) : 25;
            int color = (int) Long.parseLong(fields[xml ? 3 : 2]);
            if (time < 0 || !(mode == 1 || mode == 4 || mode == 5 || mode == 6)) return;
            result.add(new Entry(time, mode, color, size, text.length() > 300 ? text.substring(0, 300) : text));
        } catch (Exception ignored) {}
    }
    private static String decode(String text) {
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
        Matcher matcher = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text); StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1), replacement = matcher.group();
            try { replacement = new String(Character.toChars(Integer.parseInt(value.startsWith("x") ? value.substring(1) : value, value.startsWith("x") ? 16 : 10))); } catch (Exception ignored) {}
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result); return result.toString();
    }
    public int from(long time) {
        int low = 0, high = entries.size();
        while (low < high) { int middle = (low + high) >>> 1; if (entries.get(middle).time < time) low = middle + 1; else high = middle; }
        return low;
    }
}
