package com.kukuqi.tvbox.osc.update;

import android.content.Context;
import android.content.SharedPreferences;
import com.kukuqi.tvbox.osc.BuildConfig;
import com.kukuqi.tvbox.osc.util.UpdateManifest;
import java.io.File;

/** Durable state shared by background workers and the About panel. */
public final class UpdateStore {
    public static final String IDLE = "idle", CHECKING = "checking", CURRENT = "current",
            AVAILABLE = "available", DOWNLOADING = "downloading", VERIFYING = "verifying",
            READY = "ready", ERROR = "error";
    public final SharedPreferences prefs;
    private final Context context;

    public UpdateStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("app_updates", Context.MODE_PRIVATE);
    }
    public String state() { return prefs.getString("state", IDLE); }
    public boolean autoDownload() { return prefs.getBoolean("auto_download", true); }
    public void state(String state, String message, int progress) {
        prefs.edit().putString("state", state).putString("message", message).putInt("progress", progress).apply();
    }
    public UpdateManifest manifest() {
        try { return new UpdateManifest(prefs.getString("manifest", ""), context.getPackageName()); }
        catch (Exception ignored) { return null; }
    }
    public File apk(UpdateManifest manifest) {
        return new File(context.getFilesDir(), "updates/TVboxOSC-v" + manifest.version + ".apk");
    }
    public boolean hasReadyUpdate() {
        UpdateManifest manifest = manifest();
        return READY.equals(state()) && manifest != null && manifest.isNewer(BuildConfig.VERSION_CODE)
                && apk(manifest).isFile() && apk(manifest).length() == manifest.size;
    }
    public void clearInstalled() {
        UpdateManifest manifest = manifest();
        if (manifest != null && !manifest.isNewer(BuildConfig.VERSION_CODE)) {
            apk(manifest).delete();
            prefs.edit().remove("manifest").putString("state", CURRENT).putString("message", "当前已是最新版本").apply();
        }
    }
}
