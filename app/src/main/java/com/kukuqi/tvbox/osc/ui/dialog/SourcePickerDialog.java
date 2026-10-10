package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.bean.Subscription;
import com.kukuqi.tvbox.osc.event.SourceChangedEvent;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.LiveSourceLoader;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.kukuqi.tvbox.osc.util.WallpaperManager;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.CenterPopupView;
import com.orhanobut.hawk.Hawk;
import org.greenrobot.eventbus.EventBus;
import java.util.ArrayList;

/** Each category has a persistent library; saving does not change the active selection. */
public class SourcePickerDialog extends CenterPopupView {
    public static final int LIVE = SourceLibrary.LIVE, WALLPAPER = SourceLibrary.WALLPAPER;
    private final int kind;
    private final Runnable localImport;
    private TextView status;
    private LinearLayout saved;
    private boolean closed;
    private int requestVersion;
    public SourcePickerDialog(@NonNull Context context, int kind, Runnable localImport) {
        super(context); this.kind = kind; this.localImport = localImport;
    }
    @Override protected int getImplLayoutId() { return R.layout.dialog_source_picker; }
    @Override protected int getMaxHeight() { return (int) (getResources().getDisplayMetrics().heightPixels * 0.86f); }
    @Override protected int getPopupWidth() {
        return Math.min((int) (520 * getResources().getDisplayMetrics().density), (int) (getResources().getDisplayMetrics().widthPixels * 0.90f));
    }
    @Override protected void onCreate() {
        super.onCreate();
        ((TextView) findViewById(R.id.source_title)).setText(kind == LIVE ? "直播源" : "壁纸源");
        status = findViewById(R.id.source_status); saved = findViewById(R.id.source_saved);
        findViewById(R.id.source_existing).setOnClickListener(v -> existing());
        View file = findViewById(R.id.source_file); file.setVisibility(localImport == null ? View.GONE : View.VISIBLE);
        file.setOnClickListener(v -> dismissWith(localImport));
        findViewById(R.id.source_cancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.source_add).setOnClickListener(v -> new XPopup.Builder(getContext()).autoFocusEditText(false)
                .asCustom(new SourceAddDialog(getContext(), kind, (entry, use) -> {
                    if (closed) return;
                    render(); if (use) use(entry); else message("已保存，下次可直接选择");
                })).show());
        render();
    }
    private String current() { return kind == LIVE ? Hawk.get(HawkConfig.LIVE_URL, "") : WallpaperManager.get().getSelectionValue(); }
    private void message(String text) { status.setText(text); status.setVisibility(View.VISIBLE); }
    private void render() {
        status.setVisibility(View.GONE);
        String address = current();
        ((TextView) findViewById(R.id.source_current_url)).setText(address.isEmpty() ? "未设置，请添加或选择来源" : address);
        ArrayList<SourceLibrary.Entry> items = SourceLibrary.list(kind);
        ((TextView) findViewById(R.id.source_saved_title)).setText("已保存 · " + items.size());
        saved.removeAllViews();
        findViewById(R.id.source_empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (SourceLibrary.Entry entry : items) {
            View row = View.inflate(getContext(), R.layout.item_saved_source, null);
            ((TextView) row.findViewById(R.id.source_item_name)).setText(entry.name);
            ((TextView) row.findViewById(R.id.source_item_url)).setText(entry.url.startsWith("file://") || entry.url.startsWith("content://") ? "本地文件" : entry.url);
            ((RadioButton) row.findViewById(R.id.source_item_check)).setChecked(entry.url.equals(current()));
            row.findViewById(R.id.source_item_select).setOnClickListener(v -> use(entry));
            row.findViewById(R.id.source_item_more).setOnClickListener(v -> actions(entry));
            saved.addView(row);
        }
    }
    private void actions(SourceLibrary.Entry entry) {
        boolean builtIn = SourceLibrary.isBuiltIn(kind, entry.url);
        new XPopup.Builder(getContext()).asBottomList(entry.name, builtIn ? new String[]{"重命名"} : new String[]{"编辑名称和地址", "移除"}, (index, text) -> {
            if (closed) return;
            if (index == 0) new XPopup.Builder(getContext()).autoFocusEditText(false)
                    .asCustom(new SourceAddDialog(getContext(), kind, entry, (updated, apply) -> {
                if (!closed) {
                    requestVersion++;
                    boolean changedActiveAddress = entry.url.equals(current()) && !entry.url.equals(updated.url);
                    if (changedActiveAddress) SourceLibrary.save(kind, entry.name, entry.url);
                    EventBus.getDefault().post(new SourceChangedEvent());
                    if (kind == WALLPAPER) EventBus.getDefault().post(new com.kukuqi.tvbox.osc.event.WallpaperChangedEvent());
                    render();
                    if (apply) use(updated);
                    else message(changedActiveAddress ? "新地址已保存，当前仍使用原来源；选择新来源后验证并切换" : "修改已保存");
                }
            })).show();
            else new XPopup.Builder(getContext()).asConfirm("移除来源", "移除“" + entry.name + "”？" +
                    (entry.url.equals(current()) ? kind == LIVE ? "当前直播源会停用，可添加或选择其他来源。" : "当前壁纸会恢复纯色背景。" : ""), () -> {
                if (closed) return;
                requestVersion++;
                SourceLibrary.remove(kind, entry.url);
                if (entry.url.equals(current())) {
                    if (kind == LIVE) { Hawk.put(HawkConfig.LIVE_URL, ""); EventBus.getDefault().post(new SourceChangedEvent(true)); }
                    else WallpaperManager.get().clearWallpaper();
                }
                render();
            }).show();
        }).show();
    }
    private void existing() {
        ArrayList<Subscription> items = Hawk.get(HawkConfig.SUBSCRIPTIONS, new ArrayList<>());
        if (items.isEmpty()) { message("还没有视频订阅，可先添加来源"); return; }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).getName();
        }
        new XPopup.Builder(getContext()).asBottomList("从视频订阅选择", labels, (index, text) -> {
            if (closed) return;
            Subscription item = items.get(index);
            use(new SourceLibrary.Entry(item.getName(), item.getUrl()));
        }).show();
    }
    private void use(SourceLibrary.Entry entry) {
        int request = ++requestVersion;
        message("正在加载“" + entry.name + "”…");
        if (kind == LIVE) LiveSourceLoader.load(entry.url, (groups, error) -> {
            if (closed || request != requestVersion) return;
            if (groups == null) { message(error + "，已保存的来源仍保留"); return; }
            SourceLibrary.save(kind, entry.name, entry.url);
            Hawk.put(HawkConfig.LIVE_URL, entry.url);
            String epg = LiveSourceLoader.epg(groups);
            Hawk.put(HawkConfig.EPG_URL, epg);
            EventBus.getDefault().post(new SourceChangedEvent(true)); dismiss();
        });
        else WallpaperManager.get().prepareSource(entry.url, file -> {
            if (closed || request != requestVersion) return;
            if (file == null) { message("壁纸暂时无法加载，来源仍保留，可重试或选择其他来源"); return; }
            SourceLibrary.save(kind, entry.name, entry.url);
            WallpaperManager.get().saveWallpaper(new WallpaperManager.WallpaperItem(entry.name, WallpaperManager.WallpaperItem.TYPE_CUSTOM, entry.url, 0));
            dismiss();
        });
    }
    @Override protected void onDismiss() { closed = true; requestVersion++; super.onDismiss(); }
}
