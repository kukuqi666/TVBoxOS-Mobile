package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.event.WallpaperChangedEvent;
import com.kukuqi.tvbox.osc.util.WallpaperManager;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BottomPopupView;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/** One wallpaper source, with an explicit source switch and a random-image refresh. */
public class WallpaperDialog extends BottomPopupView {
    public interface OnImportListener { void onImportWallpaper(); }
    private final OnImportListener importListener;
    private final WallpaperManager manager = WallpaperManager.get();
    private ImageView preview;
    private TextView source, status, softnessLabel;
    private View refresh;
    private Drawable image;
    private CustomTarget<Drawable> target;
    private String loadedValue;
    private java.io.File loadedFile;
    private long loadedTimestamp;
    private int version;
    private boolean closed;
    public WallpaperDialog(@NonNull Context context) { this(context, null); }
    public WallpaperDialog(@NonNull Context context, OnImportListener listener) { super(context); importListener = listener; }
    @Override protected int getImplLayoutId() { return R.layout.dialog_wallpaper; }
    @Override protected void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        preview = findViewById(R.id.iv_wp_preview); source = findViewById(R.id.wp_source);
        status = findViewById(R.id.tv_wp_status); refresh = findViewById(R.id.wp_refresh);
        softnessLabel = findViewById(R.id.tv_wp_softness);
        SeekBar slider = findViewById(R.id.sb_wp_softness); slider.setProgress(manager.getSoftness());
        softnessLabel.setText("背景柔化 " + manager.getSoftness() + "%");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean user) {
                softnessLabel.setText("背景柔化 " + value + "%");
                if (user && image != null) preview.setImageDrawable(manager.softened(image, value));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) { manager.setSoftness(seek.getProgress()); }
        });
        findViewById(R.id.wp_change_source).setOnClickListener(v -> new XPopup.Builder(getContext())
                .asCustom(new SourcePickerDialog(getContext(), SourcePickerDialog.WALLPAPER, importListener == null ? null
                        : () -> dismissWith(importListener::onImportWallpaper))).show());
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false); status.setText("正在获取新的图片…");
            manager.refresh(file -> { if (!closed) { refresh.setEnabled(true); if (file == null) status.setText("壁纸加载失败，已恢复纯色背景。可换一张重试或更换来源。"); } });
        });
        findViewById(R.id.wp_remove).setOnClickListener(v -> manager.clearWallpaper());
        findViewById(R.id.btn_wp_close).setOnClickListener(v -> dismiss());
        boolean landscape = getResources().getConfiguration().screenWidthDp > getResources().getConfiguration().screenHeightDp;
        preview.getLayoutParams().height = (int) (getResources().getDisplayMetrics().density * (landscape ? 88 : 156));
        reload();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void changed(WallpaperChangedEvent event) {
        if (closed || preview == null) return;
        source.setText(manager.sourceDescription());
        if (image != null && manager.isAvailable(loadedValue) && manager.getWallpaperPref().equals(loadedValue) && loadedFile != null && loadedFile.lastModified() == loadedTimestamp) {
            preview.setImageDrawable(manager.softened(image)); return;
        }
        reload();
    }
    private void reload() {
        String value = manager.getWallpaperPref(); int request = ++version;
        source.setText(manager.sourceDescription());
        refresh.setVisibility(value.startsWith("http") ? View.VISIBLE : View.GONE);
        if (target != null) { Glide.with(preview).clear(target); target = null; }
        image = null; preview.setImageDrawable(null);
        if (value.isEmpty()) { status.setText(manager.isFollowingSource() ? "这个订阅未提供壁纸，可更换来源。当前使用纯色背景。" : "使用纯色背景"); return; }
        int resource = manager.drawableId(value);
        if (resource != 0) { image = androidx.core.content.ContextCompat.getDrawable(getContext(), resource); preview.setImageDrawable(manager.softened(image)); return; }
        status.setText("正在加载壁纸…");
        manager.prepare(value, file -> {
            if (closed || request != version) return;
            if (file == null) { status.setText("壁纸加载失败，当前使用纯色背景。可更换来源后重试。"); return; }
            loadedFile = file; loadedTimestamp = file.lastModified(); loadedValue = value;
            target = new CustomTarget<Drawable>() {
                @Override public void onResourceReady(@NonNull Drawable ready, @Nullable Transition<? super Drawable> transition) {
                    if (closed || request != version) return;
                    image = ready; preview.setImageDrawable(manager.softened(image)); status.setText("预览与页面使用同一张图片");
                }
                @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                @Override public void onLoadFailed(@Nullable Drawable error) { if (!closed && request == version) status.setText("图片无法显示，当前使用纯色背景"); }
            };
            Glide.with(preview).load(file).signature(new ObjectKey(file.lastModified())).override(1280, 720).into(target);
        });
    }
    @Override protected void onDismiss() {
        closed = true; version++;
        if (target != null) Glide.with(preview).clear(target);
        EventBus.getDefault().unregister(this); super.onDismiss();
    }
}
