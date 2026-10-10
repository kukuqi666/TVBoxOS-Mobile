package com.kukuqi.tvbox.osc.ui.activity;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.kukuqi.tvbox.osc.base.BaseVbActivity;
import com.kukuqi.tvbox.osc.bean.ParseBean;
import com.kukuqi.tvbox.osc.bean.VideoInfo;
import com.kukuqi.tvbox.osc.bean.VodInfo;
import com.kukuqi.tvbox.osc.constant.CacheConst;
import com.kukuqi.tvbox.osc.databinding.ActivityLocalPlayBinding;
import com.kukuqi.tvbox.osc.event.RefreshEvent;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.player.controller.LocalVideoController;
import com.kukuqi.tvbox.osc.receiver.BatteryReceiver;
import com.kukuqi.tvbox.osc.ui.dialog.AllLocalSeriesDialog;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.PlayerHelper;
import com.google.common.reflect.TypeToken;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

public class LocalPlayActivity extends BaseVbActivity<ActivityLocalPlayBinding> {


    private MyVideoView mVideoView;
    private final com.kukuqi.tvbox.osc.player.PlaybackBackground background =
            new com.kukuqi.tvbox.osc.player.PlaybackBackground(this, () -> mVideoView,
                    () -> this.mVideoList.isEmpty() ? "本地视频" : this.mVideoList.get(this.mPosition).getDisplayName(), () -> { if (this.mPosition > 0) { this.mPosition--; play(true); } }, () -> { if (this.mPosition + 1 < this.mVideoList.size()) { this.mPosition++; play(true); } });
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); background.onUserLeaveHint(); }
    @Override protected void onStop() { super.onStop(); background.onStop(); }

    LocalVideoController mController;
    JSONObject mVodPlayerCfg;
    private List<VideoInfo> mVideoList = new ArrayList<>();
    private int mPosition;
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();
    private BasePopupView mAllSeriesRightDialog;
    @Override
    protected void init() {
        registerReceiver(mBatteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mVideoView = mBinding.player;
        mVideoView.startFullScreen();
        Bundle bundle = getIntent().getExtras();
        String videoListJson =  bundle.getString("videoList");
        mVideoList = GsonUtils.fromJson(videoListJson, new TypeToken<List<VideoInfo>>(){}.getType());
        mPosition = bundle.getInt("position", 0);

        initController();
        initPlayerCfg();
        mVideoView.setVideoController(mController); //设置控制器
        play(false);

        new Handler()
                .postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mVideoView == null || isFinishing() || isDestroyed()) return;
                        if (mVideoView.getCurrentPlayState() == VideoView.STATE_PREPARED){//不知道为啥部分长视频(不确定是不是因为时长/大小)会卡在准备完成状态,所以延迟重置下状态
                            if (!background.keepsPlaying()) mVideoView.pause();
                            mVideoView.resume();
                        }
                    }
                },500);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_BATTERY_CHANGE && mController.mMyBatteryView!=null){
            mController.mMyBatteryView.updateBattery((int) event.obj);
        }
    }


    /**
     * 跳转到上/下一集,需重新播放
     */
    private void play(boolean fromSkip) {
        VideoInfo videoInfo = mVideoList.get(mPosition);

        String path = videoInfo.getPath();

        String uri = "";
        Uri selected = Uri.parse(path);
        if ("content".equals(selected.getScheme())) {
            uri = selected.toString();
        } else {
            File file = new File("file".equals(selected.getScheme()) ? selected.getPath() : path);
            if (file.exists()) uri = Uri.fromFile(file).toString();
        }
        mController.setTitle(videoInfo.getDisplayName());
        mVideoView.setUrl(uri); //设置视频地址

        mVideoView.setProgressManager(new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {// 就本地视频页面用sp,其余用Hawk
                //有点本地文件确实总时长,设置下总时长,为什么用path,因为电影列表要通过媒体文件的path获取缓存的时长/进度,存取报纸缓存的key一直
                if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) return;
                SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).put(path, mVideoView.getDuration());
                SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).put(path, progress);
            }

            @Override
            public long getSavedProgress(String url) {
                return SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).getLong(path);
            }
        });

        PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);

        if (fromSkip){
            mVideoView.replay(true);
        }else {
            mVideoView.start(); //开始播放，不调用则不自动播放
        }
        mVideoView.setDanmakuContext(null, videoInfo.getDisplayName(), "");
    }

    private void initController() {
        mController = new LocalVideoController(this);
        mController.setListener(new LocalVideoController.VodControlListener() {

            @Override
            public void chooseSeries() {
                showAllSeriesDialog();
            }

            @Override
            public void playNext(boolean rmProgress) {
//                String preProgressKey = progressKey;
//                LocalPlayActivity.this.playNext(rmProgress);
                if (mPosition == mVideoList.size() - 1){
                    ToastUtils.showShort("当前已经是最后一集了");
                } else {
                    mPosition++;
                    play(true);
                }
            }

            @Override
            public void playPre() {
                //playPrevious();
                if (mPosition == 0){
                    ToastUtils.showShort("当前已经是第一集了");
                }else {
                    mPosition--;
                    play(true);
                }
            }

            @Override
            public void changeParse(ParseBean pb) {

            }

            @Override
            public void updatePlayerCfg() {

            }

            @Override
            public void replay(boolean replay) {
                long position = replay ? 0 : mVideoView.getCurrentPosition();
                mVideoView.release();
                PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                mVideoView.setUrl(Uri.fromFile(new File(mVideoList.get(mPosition).getPath())).toString());
                mVideoView.skipPositionWhenPlay((int) position);
                mVideoView.start();
            }

            @Override
            public void errReplay() { ToastUtils.showShort("本地视频播放失败，请切换播放器或检查文件"); }

            @Override
            public void selectSubtitle() {
                new android.app.AlertDialog.Builder(LocalPlayActivity.this).setTitle("字幕")
                        .setItems(new String[]{"内置字幕", "选择本地字幕", "关闭字幕"}, (dialog, position) -> {
                            if (position == 0) com.kukuqi.tvbox.osc.player.PlaybackTracks.select(LocalPlayActivity.this, mVideoView, mController.mSubtitleView, false);
                            else if (position == 1) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 9210);
                            else { mController.mSubtitleView.destroy(); mController.mSubtitleView.isInternal = false; mController.mSubtitleView.setVisibility(android.view.View.GONE); }
                        }).show();
            }

            @Override
            public void selectAudioTrack() {
                com.kukuqi.tvbox.osc.player.PlaybackTracks.select(LocalPlayActivity.this, mVideoView, mController.mSubtitleView, true);
            }

            @Override
            public void prepared() {
                com.kukuqi.tvbox.osc.player.PlaybackTracks.bind(mVideoView, mController.mSubtitleView);
            }

            @Override
            public void toggleFullScreen() {
                finish();
            }

            @Override
            public void exit() {
                finish();
            }
        });

    }

    void initPlayerCfg() {
        mVodPlayerCfg = new JSONObject();
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 1));
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", com.kukuqi.tvbox.osc.player.PlaybackSpeed.clamp(Hawk.get("local_playback_speed", 1.0f)));
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 9210 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try {
                String name = "subtitle.srt";
                try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (column >= 0) name = cursor.getString(column);
                    }
                }
                String suffix = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT) : ".srt";
                if (!java.util.Arrays.asList(".srt", ".ass", ".ssa", ".vtt", ".ttml", ".scc", ".stl").contains(suffix)) throw new java.io.IOException();
                File file = new File(getCacheDir(), "local-subtitle" + suffix);
                try (java.io.InputStream input = getContentResolver().openInputStream(uri); java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    if (input == null) throw new java.io.IOException();
                    byte[] buffer = new byte[8192]; int count, total = 0;
                    while ((count = input.read(buffer)) != -1) { total += count; if (total > 8 * 1024 * 1024) throw new java.io.IOException(); output.write(buffer, 0, count); }
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    mController.mSubtitleView.isInternal = false; mController.mSubtitleView.setVisibility(android.view.View.VISIBLE);
                    mController.mSubtitleView.setSubtitlePath(file.getAbsolutePath());
                });
            } catch (Exception e) { runOnUiThread(() -> ToastUtils.showShort("字幕导入失败，请选择有效字幕文件")); }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        background.onPause();
        if (!background.keepsPlaying()) mVideoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        background.onResume();
        mVideoView.resume();
    }

    @Override
    protected void onDestroy() {
        background.onDestroy();
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
    }


    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, ""));
    }

    public void showAllSeriesDialog(){
        mAllSeriesRightDialog = new XPopup.Builder(this)
                .isViewMode(true)//隐藏导航栏(手势条)在dialog模式下会闪一下,改为view模式,但需处理onBackPress的隐藏,下方同理
                .hasNavigationBar(false)
                .popupHeight(com.blankj.utilcode.util.ScreenUtils.getScreenHeight())
                .popupPosition(PopupPosition.Right)
                .asCustom(new AllLocalSeriesDialog(this, convertLocalVideo(), (position, text) -> {
                    mPosition = position;
                    play(true);
                }));
        mAllSeriesRightDialog.show();
    }

    private List<VodInfo.VodSeries> convertLocalVideo(){
        List<VodInfo.VodSeries> seriesList = new ArrayList<>();
        for (VideoInfo local : mVideoList) {
            VodInfo.VodSeries vodSeries = new VodInfo.VodSeries(local.getDisplayName(), local.getPath());
            vodSeries.selected = (Objects.equals(mVideoList.get(mPosition).getPath(), vodSeries.url));
            seriesList.add(vodSeries);
        }
        return seriesList;
    }
}
