package com.kukuqi.tvbox.osc.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class ConfigCompatTest {
    private ConfigCompat.Resource resource(String url, String json) { return new ConfigCompat.Resource(url, json); }
    @Test public void depotUsesFirstEntryAndItsOwnBase() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("https://test/config/main.json", "{\"spider\":\"../spider.jar;md5;abc\",\"sites\":[{\"api\":\"./drpy.js?module=./lib\",\"ext\":{\"token\":\"./unchanged\"}}],\"lives\":[{\"groups\":[{\"channel\":[{\"urls\":[\"../a.m3u8\"]}]}]}]}");
        JsonObject result = ConfigCompat.resolve(resource("https://test/depot.json", "{\"urls\":[{\"url\":\"./config/main.json\"},{\"url\":\"./unused.json\"}]}"), url -> resource(url, files.get(url)));
        assertEquals("https://test/spider.jar;md5;abc", result.get("spider").getAsString());
        JsonObject site = result.getAsJsonArray("sites").get(0).getAsJsonObject();
        assertEquals("https://test/config/drpy.js?module=./lib", site.get("api").getAsString());
        assertEquals("./unchanged", site.getAsJsonObject("ext").get("token").getAsString());
        assertTrue(result.toString().contains("https://test/a.m3u8"));
    }
    @Test(expected = IllegalArgumentException.class) public void cyclicDepotFailsInsteadOfHanging() throws Exception {
        ConfigCompat.Resource input = resource("https://test/main.json", "{\"urls\":[{\"url\":\"./main.json\"}]}");
        ConfigCompat.resolve(input, url -> input);
    }
    @Test public void mixedRemoteRulesExpandAndOptionalFailuresAreEmpty() throws Exception {
        JsonObject result = ConfigCompat.resolve(resource("https://test/config/main.json", "{\"rules\":[{\"host\":\"legacy\"},\"../rules.json\",\"missing.json\"],\"doh\":\"../doh.json\"}"), url -> {
            if (url.equals("https://test/rules.json")) return resource(url, "[{\"hosts\":[\"test\"],\"regex\":[\".*\\\\.m3u8\"]}]");
            if (url.equals("https://test/doh.json")) return resource(url, "[{\"name\":\"test\",\"url\":\"./dns-query\"}]");
            throw new java.io.IOException();
        });
        assertEquals(2, result.getAsJsonArray("rules").size());
        assertEquals("https://test/dns-query", result.getAsJsonArray("doh").get(0).getAsJsonObject().get("url").getAsString());
    }
    @Test public void stringAndObjectHeadersUseCanonicalNames() {
        assertEquals("Agent", ConfigCompat.headers(JsonParser.parseString("\"{\\\"user-agent\\\":\\\"Agent\\\"}\"")).get("User-Agent"));
        assertEquals("token=ok", ConfigCompat.headers(JsonParser.parseString("{\"cookie\":\"token=ok\",\"Referer\":null}")).get("Cookie"));
        assertTrue(ConfigCompat.headers(JsonParser.parseString("\"invalid\"")).isEmpty());
    }
    @Test public void liveGroupsInheritHeadersAndMapFongmiIdentifiers() {
        SourceDescriptor source = new SourceDescriptor("https://test/config.json", "{\"lives\":[{\"name\":\"来源\",\"ua\":\"Parent\",\"header\":{\"cookie\":\"a=b\"},\"epg\":\"./guide.xml\",\"groups\":[{\"name\":\"新闻\",\"pass\":\"123\",\"channel\":[{\"name\":\"央视\",\"tvgId\":\"CCTV1\",\"tvgName\":\"央视一套\",\"ua\":\"Child\",\"urls\":[\"./live.m3u8$高清\"]}]}]}]}");
        assertTrue(source.hasLive());
        JsonObject group = LiveConfigCompat.groups(source.lives(), source.url).get(0).getAsJsonObject();
        assertEquals("新闻_123", group.get("group").getAsString());
        assertEquals("https://test/guide.xml", group.get("epg").getAsString());
        JsonObject channel = group.getAsJsonArray("channels").get(0).getAsJsonObject();
        assertEquals("CCTV1", channel.get("tvg-id").getAsString());
        assertEquals("央视一套", channel.get("tvg-name").getAsString());
        assertEquals("Child", channel.getAsJsonObject("header").get("User-Agent").getAsString());
        assertEquals("a=b", channel.getAsJsonObject("header").get("Cookie").getAsString());
        assertEquals("https://test/live.m3u8$高清", channel.getAsJsonArray("urls").get(0).getAsString());
    }
    @Test public void legacyChannelsAndEpgIdRemainSupported() {
        SourceDescriptor source = new SourceDescriptor("https://test/config.json", "[{\"group\":\"新闻\",\"channels\":[{\"name\":\"央视\",\"url\":\"/live\",\"epg\":\"CCTV1\"}]}]");
        JsonObject channel = LiveConfigCompat.groups(source.lives(), source.url).get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject();
        assertEquals("CCTV1", channel.get("tvg-id").getAsString());
        assertFalse(channel.has("epg")); assertEquals("https://test/live", channel.getAsJsonArray("urls").get(0).getAsString());
    }
    @Test public void parserResponsePrefersTopLevelUrlAndMergesHeaders() {
        ParseResult result = ParseResult.read("{\"url\":\"https://test/top.mp4\",\"data\":{\"url\":\"https://test/nested.mp4\",\"header\":{\"Cookie\":\"nested\"}},\"header\":\"{\\\"cookie\\\":\\\"root\\\"}\",\"user-agent\":\"ResponseUA\",\"referer\":\"https://test/\"}", Collections.singletonMap("User-Agent", "Fallback"));
        assertEquals("https://test/top.mp4", result.url); assertEquals("root", result.headers.get("Cookie"));
        assertEquals("ResponseUA", result.headers.get("User-Agent")); assertEquals("https://test/", result.headers.get("Referer"));
    }
    @Test public void nestedUrlAndFallbackParserHeadersWork() {
        ParseResult result = ParseResult.read("{\"data\":{\"url\":\"//test/media.mp4\"}}", Collections.singletonMap("Cookie", "auth=ok"));
        assertEquals("http://test/media.mp4", result.url); assertEquals("auth=ok", result.headers.get("Cookie"));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyParserResponseFailsExplicitly() {
        ParseResult.read("{\"data\":{}}", null);
    }
    @Test public void optionalNullExtDoesNotBecomeLiteralNull() {
        assertEquals("", ConfigCompat.text(JsonParser.parseString("null")));
        assertEquals("{\"a\":1}", ConfigCompat.text(JsonParser.parseString("{\"a\":1}")));
        assertEquals("", SourceDescriptor.resolve("https://test/a.json", ""));
    }
}
