package com.kukuqi.tvbox.osc.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.kukuqi.tvbox.osc.BuildConfig;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.ui.activity.MainActivity;
import java.util.concurrent.TimeUnit;

public final class UpdateCoordinator {
    public static final String OPEN_UPDATES = "open_updates";
    private static final String DOWNLOAD = "tvboxosc-update-download";
    private UpdateCoordinator() {}

    public static void start(Context context) {
        UpdateStore store = new UpdateStore(context);
        store.clearInstalled();
        // Development APKs use a different certificate from the production release.
        if (BuildConfig.DEBUG) return;
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("tvboxosc-update-periodic", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(UpdateWorker.class, 12, TimeUnit.HOURS)
                        .setConstraints(network(NetworkType.CONNECTED)).build());
        if (System.currentTimeMillis() - store.prefs.getLong("last_check", 0) > TimeUnit.HOURS.toMillis(6)) check(context);
    }

    public static void check(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("tvboxosc-update-check", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(UpdateWorker.class).setConstraints(network(NetworkType.CONNECTED)).build());
    }

    public static void download(Context context, boolean manual) {
        // A user download must replace a queued Wi-Fi task so the current network is used immediately.
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD, manual ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(UpdateWorker.class)
                        .setInputData(new androidx.work.Data.Builder().putBoolean("download", true).putBoolean("manual", manual).build())
                        .setConstraints(network(manual ? NetworkType.CONNECTED : NetworkType.UNMETERED)).build());
    }

    public static void autoDownload(Context context, boolean enabled) {
        UpdateStore store = new UpdateStore(context);
        store.prefs.edit().putBoolean("auto_download", enabled).apply();
        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(DOWNLOAD);
            if (UpdateStore.DOWNLOADING.equals(store.state()) || UpdateStore.VERIFYING.equals(store.state()))
                store.state(UpdateStore.AVAILABLE, "后台下载已关闭，可手动下载", 0);
        } else if (store.manifest() != null && store.manifest().isNewer(BuildConfig.VERSION_CODE) && !store.hasReadyUpdate()) {
            download(context, false);
        }
    }

    private static Constraints network(NetworkType network) {
        return new Constraints.Builder().setRequiredNetworkType(network).setRequiresStorageNotLow(true).build();
    }

    @SuppressWarnings("MissingPermission")
    public static void notifyReady(Context context, String version) {
        NotificationManagerCompat notifications = NotificationManagerCompat.from(context);
        if (!notifications.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(
                    new NotificationChannel("app_updates", "应用更新", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent intent = PendingIntent.getActivity(context, 3000,
                new Intent(context, MainActivity.class).putExtra(OPEN_UPDATES, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            notifications.notify(3000, new NotificationCompat.Builder(context, "app_updates").setSmallIcon(R.drawable.ic_update_status)
                    .setContentTitle("TVboxOSC " + version + " 已准备好")
                    .setContentText("后台下载完成，点击查看并安装更新").setContentIntent(intent).setAutoCancel(true).build());
        } catch (SecurityException ignored) { /* About and the home hint remain available without notification permission. */ }
    }
}
