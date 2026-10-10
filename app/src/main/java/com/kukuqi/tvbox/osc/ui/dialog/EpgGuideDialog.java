package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import com.kukuqi.tvbox.osc.ui.activity.LiveActivity;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.kukuqi.tvbox.osc.util.epg.EpgSchedule;
import com.kukuqi.tvbox.osc.util.epg.EpgService;
import com.lxj.xpopup.core.CenterPopupView;
import com.orhanobut.hawk.Hawk;
import java.util.*;

public class EpgGuideDialog extends CenterPopupView {
    private final LiveActivity activity; private final LiveChannelItem channel;
    private final TimeZone zone = TimeZone.getDefault();
    private String date = EpgSchedule.today(zone); private TextView status;
    private BaseQuickAdapter<EpgSchedule.Programme, BaseViewHolder> adapter;
    private EpgService.RequestHandle request; private int generation; private boolean closed;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (adapter != null) adapter.notifyDataSetChanged();
            handler.postDelayed(this, 60_000L);
        }
    };
    public EpgGuideDialog(@NonNull Context context, LiveChannelItem channel) { super(context); activity = (LiveActivity) context; this.channel = channel; }
    @Override protected int getImplLayoutId() { return R.layout.dialog_epg_guide; }
    @Override protected int getPopupWidth() { return Math.min((int) (440 * getResources().getDisplayMetrics().density), (int) (getResources().getDisplayMetrics().widthPixels * 0.92f)); }
    @Override protected int getPopupHeight() { return (int) (getResources().getDisplayMetrics().heightPixels * 0.82f); }
    @Override protected void onCreate() {
        super.onCreate();
        ((TextView) findViewById(R.id.epg_title)).setText(channel.getChannelName() + " · 节目表");
        status = findViewById(R.id.epg_status);
        RecyclerView list = findViewById(R.id.epg_programmes); list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new BaseQuickAdapter<EpgSchedule.Programme, BaseViewHolder>(R.layout.item_epg_programme, new ArrayList<>()) {
            @Override protected void convert(BaseViewHolder holder, EpgSchedule.Programme programme) {
                long now = System.currentTimeMillis(); boolean current = programme.isCurrent(now);
                holder.setText(R.id.epg_programme_title, programme.title);
                holder.setText(R.id.epg_programme_time, programme.time(zone));
                holder.setText(R.id.epg_programme_state, current ? "正在播" : programme.start > now ? "待播" : "已播");
                holder.itemView.setBackgroundResource(current ? R.drawable.bg_r_common_stroke_primary : R.drawable.bg_transparent);
            }
        };
        list.setAdapter(adapter);
        findViewById(R.id.epg_close).setOnClickListener(v -> dismiss());
        findViewById(R.id.epg_previous).setOnClickListener(v -> shift(-1));
        findViewById(R.id.epg_next).setOnClickListener(v -> shift(1));
        findViewById(R.id.epg_date).setOnClickListener(v -> { date = EpgSchedule.today(zone); load(false); });
        findViewById(R.id.epg_refresh).setOnClickListener(v -> load(true));
        findViewById(R.id.epg_source).setOnClickListener(v -> chooseSource());
        load(false);
        handler.postDelayed(tick, 60_000L);
    }
    private void shift(int days) {
        String candidate = EpgSchedule.shift(date, days, zone), today = EpgSchedule.today(zone);
        if (candidate.compareTo(EpgSchedule.shift(today, -7, zone)) < 0 || candidate.compareTo(EpgSchedule.shift(today, 7, zone)) > 0) return;
        date = candidate; load(false);
    }
    private void load(boolean refresh) {
        if (closed) return;
        int version = ++generation; if (request != null) request.cancel();
        String today = EpgSchedule.today(zone);
        ((TextView) findViewById(R.id.epg_date)).setText(date + (date.equals(today) ? " · 今天" : " · 返回今天"));
        findViewById(R.id.epg_previous).setEnabled(!date.equals(EpgSchedule.shift(today, -7, zone)));
        findViewById(R.id.epg_next).setEnabled(!date.equals(EpgSchedule.shift(today, 7, zone)));
        String custom = Hawk.get(EpgService.CUSTOM, "");
        ((TextView) findViewById(R.id.epg_source)).setText("节目源 · " + (custom.equals("-") ? "已关闭" : custom.isEmpty() ? "跟随直播源" : "自定义") + " ›");
        adapter.setNewData(new ArrayList<>());
        String url = activity.epgAddress(channel);
        if (url.isEmpty()) { status.setText(custom.equals("-") ? "节目表已关闭，可在节目源中重新开启" : "当前直播源未提供节目表，可单独设置节目源"); return; }
        status.setText("正在加载节目表…");
        request = EpgService.load(url, channel, date, refresh, (schedule, error) -> {
            if (closed || version != generation || activity.isFinishing() || activity.isDestroyed()) return;
            if (schedule == null) { status.setText(error); return; }
            adapter.setNewData(new ArrayList<>(schedule.programmes));
            status.setText(schedule.programmes.isEmpty() ? "这一天没有节目数据" : "共 " + schedule.programmes.size() + " 个节目");
            int current = schedule.programmes.indexOf(schedule.current(System.currentTimeMillis()));
            if (current >= 0) ((RecyclerView) findViewById(R.id.epg_programmes)).scrollToPosition(current);
            if (channel == activity.getCurrentLiveChannelItem() && date.equals(EpgSchedule.today(zone))) activity.refreshEpg(false);
        });
    }
    private void chooseSource() {
        new AlertDialog.Builder(getContext()).setTitle("节目源")
                .setItems(new String[]{"跟随当前直播源", "自定义节目源", "关闭节目表"}, (dialog, index) -> {
                    if (closed) return;
                    if (index == 1) inputSource();
                    else { Hawk.put(EpgService.CUSTOM, index == 0 ? "" : "-"); activity.refreshEpg(true); load(false); }
                }).setNegativeButton("取消", null).show();
    }
    private void inputSource() {
        EditText input = new EditText(getContext()); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String saved = Hawk.get(EpgService.CUSTOM, ""); input.setText(saved.equals("-") ? "" : saved);
        input.setHint("JSON API 或 XMLTV / XML.GZ 地址");
        int pad = (int) (20 * getResources().getDisplayMetrics().density); input.setPadding(pad, pad / 2, pad, pad / 2);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("自定义节目源")
                .setMessage("支持 {name}、{id}、{date} 占位符。频道 ID 优先使用直播源的 tvg-id。")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String address = input.getText().toString().trim();
            String check = address.replace("{name}", "name").replace("{id}", "id").replace("{epg}", "id").replace("{date}", "2026-01-01");
            if (!SourceLibrary.valid(check) || !(check.startsWith("http://") || check.startsWith("https://"))) { input.setError("请输入完整的 http:// 或 https:// 节目源地址"); return; }
            Hawk.put(EpgService.CUSTOM, address); dialog.dismiss(); if (!closed) { activity.refreshEpg(false); load(false); }
        }));
        dialog.show();
    }
    @Override protected void onDismiss() { closed = true; generation++; handler.removeCallbacksAndMessages(null); if (request != null) request.cancel(); super.onDismiss(); }
}
