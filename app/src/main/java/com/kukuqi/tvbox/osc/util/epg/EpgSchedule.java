package com.kukuqi.tvbox.osc.util.epg;

import com.google.gson.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;

/** Times are absolute instants; XMLTV offsets and programmes crossing midnight are preserved. */
public final class EpgSchedule {
    public static final class Programme {
        public final String title;
        public final long start, end;
        public Programme(String title, long start, long end) { this.title = title; this.start = start; this.end = end; }
        public boolean isCurrent(long now) { return start <= now && now < end; }
        public String time(TimeZone zone) { return format(start, "HH:mm", zone) + "–" + format(end, "HH:mm", zone); }
    }
    public final String date;
    public final List<Programme> programmes;
    public EpgSchedule(String date, List<Programme> programmes) {
        this.date = date;
        ArrayList<Programme> sorted = new ArrayList<>(programmes);
        sorted.sort(Comparator.comparingLong(p -> p.start));
        ArrayList<Programme> unique = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Programme p : sorted) if (p.end > p.start && p.start > 0 && !p.title.trim().isEmpty()
                && seen.add(p.start + ":" + p.end + ":" + p.title)) unique.add(p);
        this.programmes = Collections.unmodifiableList(unique);
    }
    public Programme current(long now) { for (Programme p : programmes) if (p.isCurrent(now)) return p; return null; }
    public Programme next(long now) { for (Programme p : programmes) if (p.start > now) return p; return null; }
    public static String today(TimeZone zone) { return format(System.currentTimeMillis(), "yyyy-MM-dd", zone); }
    public static String format(long time, String pattern, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT); format.setTimeZone(zone); return format.format(new Date(time));
    }
    public static String shift(String date, int days, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone); calendar.setTimeInMillis(parse(date, "yyyy-MM-dd", zone));
        calendar.add(Calendar.DATE, days); return format(calendar.getTimeInMillis(), "yyyy-MM-dd", zone);
    }
    private static long parse(String text, String pattern, TimeZone zone) {
        try { SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.ROOT); parser.setTimeZone(zone); parser.setLenient(false);
            return parser.parse(text).getTime(); } catch (Exception ignored) { return 0; }
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }
    private static long time(String date, String value, TimeZone zone) {
        if (value.equals("24:00") || value.equals("24:00:00")) return parse(shift(date, 1, zone), "yyyy-MM-dd", zone);
        if (value.matches("(?:\\d{10}|\\d{13})")) {
            try { long timestamp = Long.parseLong(value); return value.length() == 10 ? timestamp * 1000 : timestamp; } catch (Exception ignored) {}
        }
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}) {
            String text = value.contains(" ") ? value : date + " " + value;
            if (text.length() == pattern.length()) { long result = parse(text, pattern, zone); if (result > 0) return result; }
        }
        return 0;
    }
    public static EpgSchedule json(String content, String requestedDate, TimeZone zone) {
        JsonElement document = JsonParser.parseString(content);
        JsonArray data; String date = requestedDate;
        if (document.isJsonArray()) data = document.getAsJsonArray();
        else {
            JsonObject object = document.getAsJsonObject();
            if (!string(object, "date").isEmpty()) date = string(object, "date");
            JsonElement rows = object.get("epg_data");
            if (rows == null || !rows.isJsonArray()) throw new IllegalArgumentException("Missing epg_data");
            data = rows.getAsJsonArray();
        }
        ArrayList<Programme> programmes = new ArrayList<>();
        long dayStart = parse(requestedDate, "yyyy-MM-dd", zone), dayEnd = parse(shift(requestedDate, 1, zone), "yyyy-MM-dd", zone);
        for (JsonElement row : data) {
            if (!row.isJsonObject()) continue;
            JsonObject object = row.getAsJsonObject();
            long start = time(date, string(object, "start"), zone), end = time(date, string(object, "end"), zone);
            if (end > 0 && start > 0 && end < start && string(object, "end").matches("\\d{2}:\\d{2}(?::\\d{2})?")) {
                Calendar calendar = Calendar.getInstance(zone); calendar.setTimeInMillis(end); calendar.add(Calendar.DATE, 1); end = calendar.getTimeInMillis();
            }
            if (start > 0 && end > start && start < dayEnd && end > dayStart)
                programmes.add(new Programme(string(object, "title"), start, end));
        }
        return new EpgSchedule(requestedDate, programmes);
    }
    public static String channelKey(String value) { return value == null ? "" : value.replaceAll("[\\s_\\-]", "").toLowerCase(Locale.ROOT); }
    public static EpgSchedule xml(InputStream input, String id, String name, String tvgName, String date, TimeZone zone) throws Exception {
        XmlReader reader = new XmlReader(id, name, tvgName, date, zone);
        SAXParserFactory factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(false);
        for (String feature : new String[]{"http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities"}) {
            try { factory.setFeature(feature, false); } catch (Exception ignored) {}
        }
        XMLReader parser = factory.newSAXParser().getXMLReader(); parser.setContentHandler(reader);
        parser.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        parser.parse(new InputSource(input));
        return new EpgSchedule(date, reader.programmes);
    }
    private static long xmlTime(String text, TimeZone zone) {
        if (text == null) return 0;
        text = text.trim();
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(\\d{12}|\\d{14})(?:\\s*([+-]\\d{4}|Z))?$").matcher(text);
        if (!match.matches()) return 0;
        String digits = match.group(1), offset = match.group(2);
        String pattern = digits.length() == 14 ? "yyyyMMddHHmmss" : "yyyyMMddHHmm";
        return parse(digits + (offset == null ? "" : " " + (offset.equals("Z") ? "+0000" : offset)), pattern + (offset == null ? "" : " Z"), zone);
    }
    private static final class XmlReader extends DefaultHandler {
        final List<Programme> programmes = new ArrayList<>(); final Set<String> aliases = new HashSet<>(), channelIds = new HashSet<>();
        final String id; final TimeZone zone; final long dayStart, dayEnd;
        String channel = ""; boolean exactIdFound, selected, readingName, readingTitle; long start, end;
        String title = ""; final StringBuilder text = new StringBuilder();
        XmlReader(String id, String name, String tvgName, String date, TimeZone zone) {
            this.id = id == null ? "" : id.trim(); this.zone = zone;
            aliases.add(channelKey(name)); aliases.add(channelKey(tvgName)); aliases.remove("");
            if (!this.id.isEmpty()) channelIds.add(this.id);
            dayStart = parse(date, "yyyy-MM-dd", zone); dayEnd = parse(shift(date, 1, zone), "yyyy-MM-dd", zone);
        }
        @Override public void startElement(String uri, String local, String tag, Attributes attributes) {
            if (tag.equals("channel")) { channel = attributes.getValue("id"); if (channel == null) channel = ""; if (!id.isEmpty() && channel.equals(id)) exactIdFound = true; }
            else if (tag.equals("display-name")) { readingName = true; text.setLength(0); }
            else if (tag.equals("programme")) {
                String key = attributes.getValue("channel");
                selected = !id.isEmpty() && exactIdFound ? id.equals(key) : channelIds.contains(key) || aliases.contains(channelKey(key));
                start = xmlTime(attributes.getValue("start"), zone); end = xmlTime(attributes.getValue("stop"), zone); title = "";
            } else if (tag.equals("title") && selected && title.isEmpty()) { readingTitle = true; text.setLength(0); }
        }
        @Override public void characters(char[] chars, int from, int length) { if (readingTitle || readingName) text.append(chars, from, length); }
        @Override public void endElement(String uri, String local, String tag) {
            if (tag.equals("display-name")) { if (aliases.contains(channelKey(text.toString()))) channelIds.add(channel); readingName = false; }
            else if (tag.equals("channel")) channel = "";
            else if (tag.equals("title") && readingTitle) { title = text.toString().trim(); readingTitle = false; }
            else if (tag.equals("programme")) {
                if (selected && start > 0 && end > start && start < dayEnd && end > dayStart) programmes.add(new Programme(title, start, end));
                selected = false;
            }
        }
    }
}
