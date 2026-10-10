package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.util.epg.EpgSchedule;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class EpgScheduleTest {
    private final TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
    private EpgSchedule xml(String text, String id) throws Exception {
        return EpgSchedule.xml(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), id, "同名台", "", "2026-10-09", zone);
    }
    @Test public void jsonSortsDeduplicatesAndPreservesMidnight() {
        EpgSchedule guide = EpgSchedule.json("{\"date\":\"2026-10-09\",\"epg_data\":["
                + "{\"title\":\"深夜\",\"start\":\"23:30\",\"end\":\"00:30\"},"
                + "{\"title\":\"晨间\",\"start\":\"06:00\",\"end\":\"07:00\"},"
                + "{\"title\":\"晨间\",\"start\":\"06:00\",\"end\":\"07:00\"},"
                + "{\"title\":\"坏时间\",\"start\":\"99:00\",\"end\":\"07:00\"},null]}", "2026-10-09", zone);
        assertEquals(2, guide.programmes.size());
        assertEquals("晨间", guide.programmes.get(0).title);
        EpgSchedule.Programme night = guide.programmes.get(1);
        assertEquals(60 * 60_000L, night.end - night.start);
        assertTrue(night.isCurrent(night.start)); assertFalse(night.isCurrent(night.end));
        assertEquals(night, guide.next(guide.programmes.get(0).end));
    }
    @Test public void jsonDoesNotRelabelAnotherDaysSchedule() {
        EpgSchedule guide = EpgSchedule.json("{\"date\":\"2026-10-08\",\"epg_data\":["
                + "{\"title\":\"昨天新闻\",\"start\":\"12:00\",\"end\":\"13:00\"},"
                + "{\"title\":\"跨天\",\"start\":\"23:30\",\"end\":\"00:30\"}]}", "2026-10-09", zone);
        assertEquals(1, guide.programmes.size()); assertEquals("跨天", guide.programmes.get(0).title);
        assertEquals("2026-10-09", guide.date);
    }
    @Test public void xmlHonoursOffsetAndExactIdDespiteSameName() throws Exception {
        EpgSchedule guide = xml("<tv><channel id='cctv1'><display-name>同名台</display-name></channel>"
                + "<channel id='cctv-1'><display-name>同名台</display-name></channel>"
                + "<programme channel='cctv1' start='20261009000000 +0000' stop='20261009010000 +0000'><title>错误频道</title></programme>"
                + "<programme channel='cctv-1' start='20261009000000 +0000' stop='20261009010000 +0000'><title>正确频道</title></programme></tv>", "cctv-1");
        assertEquals(1, guide.programmes.size()); assertEquals("正确频道", guide.programmes.get(0).title);
        assertEquals("08:00–09:00", guide.programmes.get(0).time(zone));
    }
    @Test public void xmlFallsBackToDisplayNameAndIncludesPreviousNight() throws Exception {
        EpgSchedule guide = xml("<tv><channel id='actual'><display-name>同 名 台</display-name></channel>"
                + "<programme channel='actual' start='202610082330 +0800' stop='202610090030 +0800'><title>夜间节目</title></programme>"
                + "<programme channel='other' start='202610090600 +0800' stop='202610090700 +0800'><title>其他台</title></programme></tv>", "missing");
        assertEquals(1, guide.programmes.size()); assertEquals("夜间节目", guide.programmes.get(0).title);
    }
    @Test public void jsonSupports24HourAndRejectsInvalidAbsoluteRange() {
        EpgSchedule guide = EpgSchedule.json("[{\"title\":\"末班\",\"start\":\"23:00\",\"end\":\"24:00\"},"
                + "{\"title\":\"反向绝对时间\",\"start\":\"2026-10-09 23:00\",\"end\":\"2026-10-09 21:00\"}]", "2026-10-09", zone);
        assertEquals(1, guide.programmes.size()); assertEquals(60 * 60_000L, guide.programmes.get(0).end - guide.programmes.get(0).start);
    }
}
