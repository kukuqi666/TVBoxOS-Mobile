package com.kukuqi.tvbox.osc.ui.dialog;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.blankj.utilcode.util.ToastUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kukuqi.tvbox.osc.BuildConfig;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.update.UpdateCoordinator;
import com.kukuqi.tvbox.osc.update.UpdateInstaller;
import com.kukuqi.tvbox.osc.update.UpdateStore;
import com.kukuqi.tvbox.osc.util.HeavyTaskUtil;
import com.kukuqi.tvbox.osc.util.UpdateManifest;
import com.lxj.xpopup.core.BottomPopupView;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.lxj.xpopup.XPopup;

/** A view of the persistent update task; closing it never interrupts the download. */
public class AboutDialog extends BottomPopupView {
    private final UpdateStore store;
    private MaterialButton button;
    private LinearProgressIndicator progress;
    private TextView status, title;
    private View progressLayout, animationIcon;
    private ObjectAnimator rotation;
    private boolean closed, installing;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (!closed && button != null) render();
    };

    public AboutDialog(@NonNull Context context) { super(context); store = new UpdateStore(context); }
    /** Reuse this configuration for every entry, including notifications and tests. */
    @Override protected void onAttachedToWindow() {
        popupInfo.hasBlurBg = true;
        super.onAttachedToWindow();
    }
    @Override protected int getMaxWidth() {
        int limit = Math.min(getResources().getDisplayMetrics().widthPixels, (int) (520 * getResources().getDisplayMetrics().density));
        return super.getMaxWidth() > 0 ? Math.min(super.getMaxWidth(), limit) : limit;
    }
    @Override protected int getImplLayoutId() { return R.layout.dialog_about; }

    @Override protected void onCreate() {
        super.onCreate();
        findViewById(R.id.iv_close).setOnClickListener(view -> dismiss());
        ((TextView) findViewById(R.id.tv_about_version)).setText("版本 " + BuildConfig.VERSION_NAME);
        button = findViewById(R.id.btn_check_update);
        progress = findViewById(R.id.pb_update_progress);
        status = findViewById(R.id.tv_update_progress);
        title = findViewById(R.id.tv_update_title);
        progressLayout = findViewById(R.id.layout_update_progress);
        animationIcon = findViewById(R.id.iv_update_animation);
        SwitchMaterial auto = findViewById(R.id.switch_auto_update);
        auto.setChecked(store.autoDownload());
        auto.setOnCheckedChangeListener((view, checked) -> UpdateCoordinator.autoDownload(getContext(), checked));
        findViewById(R.id.row_github).setOnClickListener(view -> showProjectAddress());
        findViewById(R.id.row_release_notes).setOnClickListener(view -> showReleaseNotes());
        button.setOnClickListener(view -> {
            if (store.hasReadyUpdate()) install();
            else if (store.manifest() != null && store.manifest().isNewer(BuildConfig.VERSION_CODE)) {
                UpdateCoordinator.download(getContext(), true);
                status.setText("准备下载，可关闭此页在后台继续");
                button.setEnabled(false);
            } else {
                UpdateCoordinator.check(getContext());
                status.setText("等待网络，准备检查更新…");
                button.setEnabled(false);
            }
        });
        store.prefs.registerOnSharedPreferenceChangeListener(listener);
        if (UpdateStore.ERROR.equals(store.state()) && store.prefs.getString("message", "").contains("java.")) {
            store.state(UpdateStore.ERROR, "更新暂不可用，请稍后重试", 0);
        }
        render();
    }

    private void render() {
        if (installing) return;
        String state = store.state();
        boolean downloading = UpdateStore.DOWNLOADING.equals(state);
        boolean checking = UpdateStore.CHECKING.equals(state);
        boolean verifying = UpdateStore.VERIFYING.equals(state);
        boolean busy = downloading || checking || verifying;
        boolean ready = store.hasReadyUpdate();
        UpdateManifest manifest = store.manifest();
        boolean available = manifest != null && manifest.isNewer(BuildConfig.VERSION_CODE);
        title.setText(ready ? "更新已就绪" : busy ? "正在更新" : available ? "发现新版本" : "版本更新");
        status.setText(store.prefs.getString("message", "自动检查新版本，下载后由你确认安装"));
        button.setEnabled(!busy);
        button.setText(ready ? "安装 v" + manifest.version : downloading ? "正在后台下载…" : checking ? "正在检查…"
                : verifying ? "正在校验…" : available ? "下载 v" + manifest.version : "检查更新");
        progressLayout.setVisibility(busy || ready ? VISIBLE : GONE);
        if (progress.isIndeterminate() != (checking || verifying)) {
            progress.setVisibility(INVISIBLE);
            progress.setIndeterminate(checking || verifying);
            progress.setVisibility(VISIBLE);
        }
        if (!checking && !verifying) progress.setProgressCompat(ready ? 100 : store.prefs.getInt("progress", 0), true);
        animate(busy);
    }

    private void animate(boolean active) {
        if (active && rotation == null) {
            rotation = ObjectAnimator.ofFloat(animationIcon, "rotation", 0f, 360f);
            rotation.setDuration(1400);
            rotation.setInterpolator(new LinearInterpolator());
            rotation.setRepeatCount(ValueAnimator.INFINITE);
            rotation.start();
        } else if (!active && rotation != null) {
            rotation.cancel(); rotation = null; animationIcon.setRotation(0);
        }
    }

    private void install() {
        installing = true;
        button.setEnabled(false);
        status.setText("正在验证安装文件…");
        animate(true);
        HeavyTaskUtil.executeNewTask(() -> {
            try {
                UpdateManifest manifest = store.manifest();
                if (manifest == null) throw new IllegalStateException("更新信息已失效");
                File apk = store.apk(manifest);
                manifest.verifyDownload(apk);
                UpdateInstaller.verifyArchive(getContext(), apk, manifest);
                post(() -> {
                    installing = false;
                    if (closed) return;
                    render();
                    try {
                        if (!UpdateInstaller.launch(getContext(), apk)) {
                            status.setText("允许安装未知应用后，返回此页点“继续安装”");
                            button.setText("继续安装");
                        }
                    } catch (Exception error) { ToastUtils.showLong("无法打开安装程序：" + error.getMessage()); }
                });
            } catch (Exception error) {
                store.state(UpdateStore.ERROR, "安装校验失败：" + error.getMessage(), 0);
                post(() -> { installing = false; if (!closed) render(); });
            }
        });
    }

    private void openUrl(String url) {
        try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception error) { ToastUtils.showShort("请安装浏览器后打开"); }
    }

    private void showProjectAddress() {
        String url = "https://github.com/kukuqi666/TVboxOSC";
        new XPopup.Builder(getContext()).asConfirm("项目地址", url,
                "浏览器打开", "复制链接", () -> {
                    ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("TVboxOSC", url));
                    ToastUtils.showShort("链接已复制");
                }, () -> openUrl(url), false).show();
    }

    private void showReleaseNotes() {
        StringBuilder notes = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContext().getAssets().open("release-notes.txt"), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) notes.append(line).append('\n');
        } catch (Exception ignored) { notes.append("详细更新记录可通过项目地址查看。"); }
        new XPopup.Builder(getContext()).asConfirm("更新记录", notes.toString(), null, "知道了", () -> {}, null, true).show();
    }

    @Override protected void onDismiss() {
        closed = true;
        store.prefs.unregisterOnSharedPreferenceChangeListener(listener);
        animate(false);
        super.onDismiss();
    }
}
