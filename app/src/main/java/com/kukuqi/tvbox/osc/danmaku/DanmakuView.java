package com.kukuqi.tvbox.osc.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.orhanobut.hawk.Hawk;

/** Player-independent overlay, so IJK and Exo share the same settings and seek behavior. */
public final class DanmakuView extends View {
    private final MyVideoView player;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DanmakuTimeline timeline;
    public DanmakuView(Context context, MyVideoView player) { super(context); this.player = player; setClickable(false); setFocusable(false); }
    public void setTimeline(DanmakuTimeline timeline) { this.timeline = timeline; invalidate(); }
    public int count() { return timeline == null ? 0 : timeline.entries.size(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (timeline == null || !Hawk.get("danmaku_load", false) || !Hawk.get("danmaku_show", true)) return;
        long position = player.getCurrentPosition() - Hawk.get("danmaku_offset", 0L);
        long duration = Math.max(3000, Math.min(15000, Hawk.get("danmaku_duration", 8000L)));
        float scale = Math.max(.5f, Math.min(3, Hawk.get("danmaku_scale", 1f)));
        float area = Math.max(.1f, Math.min(1, Hawk.get("danmaku_area", .5f)));
        float lineHeight = 30 * getResources().getDisplayMetrics().scaledDensity * scale;
        int lines = Math.max(1, (int) (getHeight() * area / lineHeight));
        int max = Math.max(10, Math.min(500, Hawk.get("danmaku_density", 150)));
        long[] occupied = new long[lines * 3]; java.util.Arrays.fill(occupied, Long.MIN_VALUE);
        int shown = 0, considered = 0;
        for (int index = timeline.from(Math.max(0, position - duration)); index < timeline.entries.size() && shown < max && considered++ < max * 8; index++) {
            DanmakuTimeline.Entry entry = timeline.entries.get(index);
            long age = position - entry.time;
            if (age < 0) break;
            if (age >= duration) continue;
            int group = entry.mode == 4 ? 2 : entry.mode == 5 ? 1 : 0;
            paint.setTextSize(entry.size * getResources().getDisplayMetrics().scaledDensity * scale);
            float width = paint.measureText(entry.text);
            int lane = -1;
            // Reserve time for a whole comment to leave; density never produces overlapping text.
            for (int i = 0; i < lines; i++) if (occupied[group * lines + i] <= entry.time) { lane = i; occupied[group * lines + i] = entry.time + duration; break; }
            if (lane < 0) continue;
            float x = entry.mode == 4 || entry.mode == 5 ? (getWidth() - width) / 2
                    : entry.mode == 6 ? -width + (getWidth() + width) * age / duration : getWidth() - (getWidth() + width) * age / duration;
            float y = entry.mode == 4 ? getHeight() - lane * lineHeight - 12 : (lane + 1) * lineHeight;
            paint.setColor(entry.color);
            paint.setAlpha(Math.max(25, Math.min(255, Hawk.get("danmaku_opacity", 100) * 255 / 100)));
            paint.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
            canvas.drawText(entry.text, x, y, paint); shown++;
        }
        if (player.isPlaying() && isShown()) postInvalidateDelayed(33);
    }
}
