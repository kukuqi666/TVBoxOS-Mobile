package com.github.tvbox.osc.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.blankj.utilcode.util.LogUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.ui.activity.DetailActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

public class PlayService extends Service {

    static String videoInfo = "MBox&&第一集";
    private static MyVideoView videoView;

    public static void start(MyVideoView controller,String currentVideoInfo) {
        videoInfo = currentVideoInfo;
        PlayService.videoView = controller;
        ContextCompat.startForegroundService(App.getInstance(), new Intent(App.getInstance(), PlayService.class));
    }

    public static void stop() {
        App.getInstance().stopService(new Intent(App.getInstance(), PlayService.class));
    }


    private static final String CHANNEL_ID = "MyChannelId";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "My Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        videoView.start();
        return START_NOT_STICKY;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event){
        if (event.type == RefreshEvent.TYPE_REFRESH_NOTIFY){
            if (event.obj != null) {
                videoInfo = event.obj.toString();
            }
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification(){

        String title = videoInfo.split("&&")[0];
        String episodes = videoInfo.split("&&")[1];
        // 展开布局
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_player);
        remoteViews.setTextViewText(R.id.tv_title, title);
        remoteViews.setTextViewText(R.id.tv_subtitle, "正在播放: "+episodes);
        remoteViews.setImageViewResource(R.id.iv_play_pause,videoView.isPlaying()?R.drawable.ic_notify_pause:R.drawable.ic_notify_play);
        // 创建通知栏操作
        remoteViews.setOnClickPendingIntent(R.id.iv_previous, getPendingIntent(IntentKey.BROADCAST_ACTION_PREV));
        remoteViews.setOnClickPendingIntent(R.id.iv_play_pause, getPendingIntent(IntentKey.BROADCAST_ACTION_PLAYPAUSE));
        remoteViews.setOnClickPendingIntent(R.id.iv_next, getPendingIntent(IntentKey.BROADCAST_ACTION_NEXT));
        remoteViews.setOnClickPendingIntent(R.id.iv_close, getPendingIntent(IntentKey.BROADCAST_ACTION_CLOSE));

        // 普通布局
        RemoteViews remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.notification_player_small);
        remoteViewsSmall.setTextViewText(R.id.tv_title, title);
        remoteViewsSmall.setTextViewText(R.id.tv_subtitle, "正在播放: "+episodes);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContent(remoteViews)
                .setCustomContentView(remoteViewsSmall)
                .setCustomBigContentView(remoteViews)
                .setContentIntent(getPendingIntentActivity())
                .setOngoing(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("默认展开"));

        return builder.build();
    }

    private PendingIntent getPendingIntentActivity() {
        Intent intent = new Intent(this, DetailActivity.class);
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static PendingIntent getPendingIntent(int actionCode) {
        return PendingIntent.getBroadcast(App.getInstance(), actionCode, new Intent(IntentKey.BROADCAST_ACTION).putExtra("action", actionCode).setPackage(App.getInstance().getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
