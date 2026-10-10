package com.kukuqi.tvbox.osc.ui.activity;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.base.BaseActivity;
import com.kukuqi.tvbox.osc.bean.LiveChannelGroup;
import com.kukuqi.tvbox.osc.bean.LiveChannelItem;
import com.kukuqi.tvbox.osc.bean.LivePlayerManager;
import com.kukuqi.tvbox.osc.bean.LiveSettingGroup;
import com.kukuqi.tvbox.osc.bean.LiveSettingItem;
import com.kukuqi.tvbox.osc.player.controller.LiveNewController;
import com.kukuqi.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.kukuqi.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.kukuqi.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.kukuqi.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.kukuqi.tvbox.osc.ui.dialog.AllChannelsRightDialog;
import com.kukuqi.tvbox.osc.ui.dialog.EpgGuideDialog;
import com.kukuqi.tvbox.osc.util.epg.EpgSchedule;
import com.kukuqi.tvbox.osc.util.epg.EpgService;
import com.kukuqi.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.kukuqi.tvbox.osc.ui.dialog.LiveSettingDialog;
import com.kukuqi.tvbox.osc.ui.dialog.LiveSettingRightDialog;
import com.kukuqi.tvbox.osc.ui.tv.widget.ViewObj;
import com.kukuqi.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.kukuqi.tvbox.osc.ui.widget.PlayerMenuView;
import com.kukuqi.tvbox.osc.ui.widget.PlayerTitleView;
import com.kukuqi.tvbox.osc.util.FastClickCheckUtil;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import xyz.doikki.videocontroller.component.LiveControlView;
import xyz.doikki.videocontroller.component.TitleView;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveActivity extends BaseActivity {
    public static Context context;
    private com.kukuqi.tvbox.osc.player.MyVideoView mVideoView;
    private final com.kukuqi.tvbox.osc.player.PlaybackBackground background =
            new com.kukuqi.tvbox.osc.player.PlaybackBackground(this, () -> this.mVideoView,
                    () -> this.currentLiveChannelItem == null ? "直播" : this.currentLiveChannelItem.getChannelName(), this::playPrevious, this::playNext);
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); background.onUserLeaveHint(); }
    @Override protected void onStop() { super.onStop(); background.onStop(); }
    private TextView tvChannelInfo;
    private LinearLayout tvLeftChannelListLayout;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    public LiveChannelGroupNewAdapter liveChannelGroupAdapter;
    public LiveChannelItemNewAdapter liveChannelItemAdapter;

    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();

    public static  int currentChannelGroupIndex = 0;
    private Handler mHandler = new Handler();

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private int currentLiveChangeSourceTimes = 0;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

//EPG   by 龍
    private static LiveChannelItem  channel_Name = null;
    private static Hashtable hsEpg = new Hashtable();
    private CountDownTimer countDownTimer;
//    private CountDownTimer countDownTimerRightTop;
    TextView tv_channelnum;
    TextView tip_chname;

    TextView tv_srcinfo;
    public String epgStringAddress ="";

    private boolean isSHIYI = false;
    private boolean isBack = false;
    public static String playUrl;
    //kenson
    private ImageView imgLiveIcon;
    SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");
    private CountDownTimer countDownTimer3;
    private int videoWidth = 1920;
    private int videoHeight = 1080;
    private  boolean show = false;
    private PlayerTitleView mPlayerTitleView;
    private BasePopupView mSettingRightDialog;
    private BasePopupView mSettingBottomDialog;
    private BasePopupView mAllChannelRightDialog;
    private boolean noLiveChannelsShown;
    private int liveSourceRequest;
    private TextView epgSummary;
    private EpgSchedule epgSchedule;
    private EpgService.RequestHandle epgRequest;
    private int epgGeneration;
    private long epgLoadedAt;
    private BasePopupView epgDialog;
    private final Runnable epgTick = new Runnable() {
        @Override public void run() {
            if (currentLiveChannelItem != null && epgSchedule != null) {
                if (!epgSchedule.date.equals(EpgSchedule.today(java.util.TimeZone.getDefault()))
                        || android.os.SystemClock.elapsedRealtime() - epgLoadedAt >= 10 * 60_000L) refreshEpg(false);
                else renderEpg();
            }
            mHandler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;
    }

    @Override
    protected void init() {
        ImmersionBar.with(this).statusBarColor(R.color.black).statusBarDarkFont(false)
                .navigationBarColor(R.color.black).fitsSystemWindows(true)
                .hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).init();
        initBottomNavigation();
        context = this;
        epgStringAddress = "";

        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);
        resizePreview();
        epgSummary = findViewById(R.id.live_epg_summary);
        findViewById(R.id.live_epg_button).setOnClickListener(v -> showEpgGuide());
        epgSummary.setOnClickListener(v -> showEpgGuide());

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);

        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        mChannelGroupView.addItemDecoration(new LinearSpacingItemDecoration(20,true));
        mLiveChannelView.addItemDecoration(new LinearSpacingItemDecoration(20,true));

        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);

        //EPG  findViewById  by 龍
        tip_chname = findViewById(R.id.tv_channel_bar_name);//底部名称
        tip_chname.setOnClickListener(view -> {
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
        });
        tv_channelnum = findViewById(R.id.tv_channel_bottom_number); //底部数字

        tv_srcinfo = findViewById(R.id.tv_source);//线路状态

        //源切换
        findViewById(R.id.ic_pre_source).setOnClickListener(view -> playPreSource());
        findViewById(R.id.ic_next_source).setOnClickListener(view -> playNextSource());
        tv_srcinfo.setOnClickListener(view -> playNextSource());
        //投屏/设置
        findViewById(R.id.ic_setting).setOnClickListener(view -> showSettingDialog(false));
        findViewById(R.id.ic_cast).setVisibility(View.GONE);

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingGroupList();
    }

    private void initBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.bottom_nav);
        navigation.setSelectedItemId(R.id.navigation_live);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_live) return true;
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_START_DESTINATION, item.getItemId())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return false;
        });
    }

    //显示底部EPG
    private void showBottomEpg() {
        if (isSHIYI || channel_Name == null)
            return;
        if (channel_Name.getChannelName() != null) {
            mPlayerTitleView.setTitle(channel_Name.getChannelName());
            tip_chname.setText(channel_Name.getChannelName());
            tv_channelnum.setText("" + channel_Name.getChannelNum());
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            if (channel_Name == null || channel_Name.getSourceNum() <= 0) {
                tv_srcinfo.setText("1/1");
            } else {
                tv_srcinfo.setText("线路" + (channel_Name.getSourceIndex() + 1) + "/" + channel_Name.getSourceNum());
            }

            if (epgSchedule != null) renderEpg();

        }
    }


    private void resizePreview() {
        View view = findViewById(R.id.live_video_container);
        float density = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        params.height = landscape ? Math.min((int) (125 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.25f)) : (int) (240 * density);
        view.setLayoutParams(params);
        LinearLayout information = findViewById(R.id.ll_epg);
        information.setPadding((int) (12 * density), (int) ((landscape ? 4 : 6) * density), (int) (12 * density), (int) ((landscape ? 4 : 6) * density));
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) information.getLayoutParams();
        margins.topMargin = margins.bottomMargin = (int) ((landscape ? 6 : 10) * density);
        information.setLayoutParams(margins);
        ViewGroup.LayoutParams row = information.getChildAt(0).getLayoutParams();
        row.height = (int) ((landscape ? 36 : 40) * density); information.getChildAt(0).setLayoutParams(row);
        ((TextView) findViewById(R.id.live_epg_summary)).setMaxLines(landscape ? 1 : 2);
    }

    @Override public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        resizePreview();
        if (epgDialog != null) epgDialog.dismiss();
    }

    public String epgAddress(LiveChannelItem channel) {
        String custom = Hawk.get(EpgService.CUSTOM, "");
        if (custom.equals("-")) return "";
        if (!custom.isEmpty()) return custom;
        // Channel metadata includes the owning group's guide. Do not borrow another group's EPG.
        return channel == null ? "" : channel.getEpgUrl();
    }

    public void refreshEpg(boolean force) {
        int version = ++epgGeneration;
        if (epgRequest != null) epgRequest.cancel();
        epgSchedule = null;
        LiveChannelItem channel = currentLiveChannelItem;
        if (channel == null || epgSummary == null) return;
        mPlayerTitleView.setTitle(channel.getChannelName());
        String address = epgAddress(channel);
        if (address.isEmpty()) {
            epgSummary.setText(Hawk.get(EpgService.CUSTOM, "").equals("-") ? "节目表已关闭" : "暂无节目源 · 可在节目表中设置");
            return;
        }
        epgSummary.setText("正在加载节目表…");
        epgRequest = EpgService.load(address, channel, EpgSchedule.today(java.util.TimeZone.getDefault()), force, (schedule, error) -> {
            if (version != epgGeneration || channel != currentLiveChannelItem || isFinishing() || isDestroyed()) return;
            epgSchedule = schedule; epgLoadedAt = android.os.SystemClock.elapsedRealtime();
            if (schedule == null) epgSummary.setText(error + " · 打开节目表重试");
            else renderEpg();
        });
    }

    private void renderEpg() {
        if (epgSchedule == null || currentLiveChannelItem == null) return;
        long now = System.currentTimeMillis();
        EpgSchedule.Programme current = epgSchedule.current(now), next = epgSchedule.next(now);
        java.util.TimeZone zone = java.util.TimeZone.getDefault();
        String text = current == null ? "暂无当前节目" : "正在播 · " + current.title;
        if (next != null) text += "\n" + EpgSchedule.format(next.start, "HH:mm", zone) + " · " + next.title;
        epgSummary.setText(text);
        mPlayerTitleView.setTitle(currentLiveChannelItem.getChannelName() + (current == null ? "" : " · " + current.title));
    }

    public void showEpgGuide() {
        if (!isCurrentLiveChannelValid()) return;
        if (epgDialog != null && epgDialog.isShow()) return;
        epgDialog = new XPopup.Builder(this).autoFocusEditText(false)
                .asCustom(new EpgGuideDialog(this, currentLiveChannelItem));
        epgDialog.show();
    }

    @Override
    public void onBackPressed() {
        if (epgDialog != null && epgDialog.isShow()) {
            epgDialog.dismiss();
        } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if(isBack){
            isBack= false;
            playPreSource();
        } else if(mSettingBottomDialog!=null && mSettingBottomDialog.isShow()){//适配底部导航栏(手势条闪屏)变成view模式后在back时手动隐藏
            mSettingBottomDialog.dismiss();
        } else if(mSettingRightDialog!=null && mSettingRightDialog.isShow()){
            mSettingRightDialog.dismiss();
        }  else if(mAllChannelRightDialog!=null && mAllChannelRightDialog.isShow()){
            mAllChannelRightDialog.dismiss();
        } else if (mVideoView == null || !mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                //showSettingGroup();
            } else if (!isListOrSettingLayoutVisible()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playNext();
                        else
                            playPrevious();
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false))
                            playPrevious();
                        else
                            playNext();
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(isBack){

                        }else{
                            //showSettingGroup();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(isBack){

                        }else{
                            playNextSource();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        break;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        background.onResume();
        mHandler.removeCallbacks(epgTick);
        mHandler.post(epgTick);
        if (mVideoView != null) mVideoView.resume();
    }


    @Override
    protected void onPause() {
        super.onPause();
        background.onPause();
        mHandler.removeCallbacks(epgTick);
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        if (epgDialog != null) epgDialog.dismiss();
        if (mSettingBottomDialog != null) mSettingBottomDialog.dismiss();
        if (mSettingRightDialog != null) mSettingRightDialog.dismiss();
        if (mAllChannelRightDialog != null) mAllChannelRightDialog.dismiss();
        if (mVideoView != null) {
            if (!background.keepsPlaying()) mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        epgGeneration++;
        if (epgRequest != null) epgRequest.cancel();
        if (epgDialog != null) epgDialog.dismiss();
        if (channel_Name == currentLiveChannelItem) channel_Name = null;
        if (context == this) context = null;
        mHandler.removeCallbacksAndMessages(null);
        background.onDestroy();
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
    }

    private void showChannelList() {
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
            return;
        }
        //重新载入上一次状态
        liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
        if (currentLiveChannelIndex > -1){
            mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            if (currentChannelGroupIndex==0){
                mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(currentChannelGroupIndex);
            }
        }
    }

    private void showChannelInfo() {
        tvChannelInfo.setText(String.format(Locale.getDefault(), "%d %s %s(%d/%d)", currentLiveChannelItem.getChannelNum(),
                currentLiveChannelItem.getChannelName(), currentLiveChannelItem.getSourceName(),
                currentLiveChannelItem.getSourceIndex() + 1, currentLiveChannelItem.getSourceNum()));

        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            lParams.gravity = Gravity.LEFT;
            lParams.leftMargin = 60;
            lParams.topMargin = 30;
        } else {
            lParams.gravity = Gravity.RIGHT;
            lParams.rightMargin = 60;
            lParams.topMargin = 30;
        }
        tvChannelInfo.setLayoutParams(lParams);

        tvChannelInfo.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, 3000);
    }

    private Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            tvChannelInfo.setVisibility(View.INVISIBLE);
        }
    };

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource)
                || (changeSource && currentLiveChannelItem.getSourceNum() == 1)) {
           // showChannelInfo();
            return true;
        }
        mVideoView.release();
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }

        channel_Name = currentLiveChannelItem;
        isSHIYI=false;
        isBack = false;
        if(currentLiveChannelItem.getUrl().indexOf("PLTV/8888") !=-1){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        if (!changeSource) {
            epgSchedule = null;
            if (epgDialog != null) epgDialog.dismiss();
            refreshEpg(false);
        }
        showBottomEpg();

        mVideoView.setUrl(currentLiveChannelItem.getUrl(), currentLiveChannelItem.getHeaders());
       // showChannelInfo();
        mVideoView.start();
        return true;
    }

    private void playNext() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(-1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    public void playNextSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    //显示设置列表
    private void showSettingGroup() {

        if (tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid()) return;
            //重新载入默认状态
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(liveSettingGroupList);
            selectSettingGroup(0, false);
            mSettingGroupView.smoothScrollToPosition(0);
            mSettingItemView.smoothScrollToPosition(currentLiveChannelItem.getSourceIndex());
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200);
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    private Runnable mFocusAndShowSettingGroup = new Runnable() {
        @Override
        public void run() {
            if (mSettingGroupView.isScrolling() || mSettingItemView.isScrolling() || mSettingGroupView.isComputingLayout() || mSettingItemView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mSettingGroupView.findViewHolderForAdapterPosition(0);
                if (holder != null)
                    holder.itemView.requestFocus();
                tvRightSettingLayout.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
                if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                    ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                    ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), -tvRightSettingLayout.getLayoutParams().width, 0);
                    animator.setDuration(200);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mHandler.postDelayed(mHideSettingLayoutRun, 5000);
                        }
                    });
                    animator.start();
                }
            }
        }
    };

    private Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tvRightSettingLayout.getLayoutParams();
            if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                ViewObj viewObj = new ViewObj(tvRightSettingLayout, params);
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), 0, -tvRightSettingLayout.getLayoutParams().width);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        tvRightSettingLayout.setVisibility(View.INVISIBLE);
                        liveSettingGroupAdapter.setSelectedGroupIndex(-1);
                    }
                });
                animator.start();
            }
        }
    };

//    private void initVideoView() {
//        StandardVideoController controller = new StandardVideoController(this);
//        controller.addControlComponent(new LiveControlView(this)); //直播控制条
//        controller.setEnableInNormal(true);
//        controller.setGestureEnabled(true);
//        mVideoView.setVideoController(controller);
//        mVideoView.setProgressManager(null);
//    }

    private void initVideoView() {
        LiveNewController controller = new LiveNewController(this);
        PlayerMenuView playerMenuView = getPlayerMenuView();
        controller.addControlComponent(playerMenuView); //菜单栏,设置投屏等
        controller.addControlComponent(new LiveControlView(this)); //直播控制条
        //标题栏
        mPlayerTitleView = new PlayerTitleView(this);
        controller.addControlComponent(mPlayerTitleView);
        controller.setListener(new LiveNewController.LiveControlListener() {

            @Override
            public void setting() {
                //showSettingGroup();
            }

            @Override
            public void playStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 2000);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000);
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0){
                    playNextSource();
                } else {
                    playPreSource();
                }
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    @NonNull
    private PlayerMenuView getPlayerMenuView() {
        PlayerMenuView playerMenuView = new PlayerMenuView(this);
        playerMenuView.setOnPlayerMenuClickListener(new PlayerMenuView.OnPlayerMenuClickListener() {
            @Override
            public void expand() {
                showAllChannelDialog();
            }

            @Override
            public void onSetting() {
                showSettingDialog(true);
            }

        });
        return playerMenuView;
    }

    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            currentLiveChangeSourceTimes++;
            if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
                currentLiveChangeSourceTimes = 0;
                Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
                playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
            } else {
                playNextSource();
            }
        }
    };

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelGroupAdapter = new LiveChannelGroupNewAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);

        //手机/模拟器
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
    }

    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelItemAdapter = new LiveChannelItemNewAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);

        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
            }
        });
        liveChannelItemAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            LiveChannelItem item = liveChannelItemAdapter.getItem(position);
            if (item != null) toggleChannelFavorite(item);
            return true;
        });
    }

    public void toggleChannelFavorite(LiveChannelItem item) {
        boolean added = com.kukuqi.tvbox.osc.util.LiveFavorites.toggle(item.getChannelName());
        Toast.makeText(this, added ? "已收藏频道" : "已取消收藏", Toast.LENGTH_SHORT).show();
        liveChannelItemAdapter.notifyDataSetChanged();
        if (mAllChannelRightDialog instanceof AllChannelsRightDialog)
            ((AllChannelsRightDialog) mAllChannelRightDialog).refresh();
    }

    public List<com.kukuqi.tvbox.osc.util.LiveChannelBrowser.Channel> searchLiveChannels(String query, boolean favoritesOnly) {
        return com.kukuqi.tvbox.osc.util.LiveChannelBrowser.filter(liveChannelGroupList, query, favoritesOnly,
                com.kukuqi.tvbox.osc.util.LiveFavorites.names(), new java.util.HashSet<>(channelGroupPasswordConfirmed));
    }

    public void playBrowserChannel(com.kukuqi.tvbox.osc.util.LiveChannelBrowser.Channel channel) {
        int group = channel.groupIndex, index = channel.channelIndex;
        // A source may have changed while the browser was open.
        if (group < 0 || group >= liveChannelGroupList.size()) return;
        List<LiveChannelItem> items = liveChannelGroupList.get(group).getLiveChannels();
        if (index < 0 || index >= items.size() || items.get(index) != channel.item) return;
        liveChannelGroupAdapter.setSelectedGroupIndex(group);
        if (isNeedInputPassword(group)) showPasswordDialog(group, index);
        else loadChannelGroupDataAndPlay(group, index);
    }

    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position, false);
            }
        });
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (!isCurrentLiveChannelValid()) return;
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1)
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
        }
        int smoothScrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (smoothScrollToPosition < 0) smoothScrollToPosition = 0;
        mSettingItemView.smoothScrollToPosition(smoothScrollToPosition);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, 5000);
            }
        });

        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }

    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
                break;
            case 1://画面比例
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2://播放解码
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                mVideoView.setUrl(currentLiveChannelItem.getUrl(), currentLiveChannelItem.getHeaders());
                mVideoView.start();
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4://超时换源
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, 5000);
    }

    private void initLiveChannelList() {
        epgGeneration++;
        if (epgRequest != null) epgRequest.cancel();
        if (epgDialog != null) epgDialog.dismiss();
        epgSchedule = null;
        epgStringAddress = "";
        int request = ++liveSourceRequest;
        noLiveChannelsShown = false;
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        channelGroupPasswordConfirmed.clear();
        currentLiveChannelIndex = -1;
        currentLiveChannelItem = null;
        liveChannelGroupAdapter.setSelectedGroupIndex(-1);
        liveChannelGroupAdapter.setNewData(new ArrayList<>());
        liveChannelItemAdapter.setSelectedChannelIndex(-1);
        liveChannelItemAdapter.setNewData(new ArrayList<>());
        if (mAllChannelRightDialog != null) mAllChannelRightDialog.dismiss();
        String independent = Hawk.get(HawkConfig.LIVE_URL, "");
        String selected = independent;
        liveChannelGroupList.clear();
        ApiConfig.get().loadLives(new com.google.gson.JsonArray());
        if (!selected.isEmpty()) {
            showLoading();
            com.kukuqi.tvbox.osc.util.LiveSourceLoader.load(selected, (groups, error) -> {
                if (request != liveSourceRequest || isFinishing() || isDestroyed()) return;
                if (groups == null) { showNoLiveChannels(error); return; }
                String epg = com.kukuqi.tvbox.osc.util.LiveSourceLoader.epg(groups);
                Hawk.put(HawkConfig.EPG_URL, epg); epgStringAddress = epg;
                ApiConfig.get().loadLives(groups);
                liveChannelGroupList.clear(); liveChannelGroupList.addAll(ApiConfig.get().getChannelGroupList());
                showSuccess(); initLiveState();
            });
            return;
        }
        showNoLiveChannels();
    }

    public void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            showNoLiveChannels();
            return;
        }
        showLoading();
        if (url.startsWith("content://")) {
            final String localUrl = url;
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(Uri.parse(localUrl));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) content.append(line).append('\n');
                    runOnUiThread(() -> parseProxyLiveContent(content.toString()));
                } catch (Throwable error) {
                    runOnUiThread(this::showNoLiveChannels);
                }
            }).start();
            return;
        }
        OkGo.<String>get(url).execute(new AbsCallback<String>() {

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }

            @Override
            public void onSuccess(Response<String> response) {
                parseProxyLiveContent(response.body());
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                showNoLiveChannels();
            }
        });
    }

    private void parseProxyLiveContent(String content) {
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap = new LinkedHashMap<>();
        TxtSubscribe.parse(linkedHashMap, content);
        ApiConfig.get().loadLives(TxtSubscribe.live2JsonArray(linkedHashMap));
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        if (list.isEmpty()) {
            showNoLiveChannels();
            return;
        }
        liveChannelGroupList.clear();
        liveChannelGroupList.addAll(list);
        showSuccess();
        initLiveState();
    }

    private void showNoLiveChannels() {
        showNoLiveChannels("当前来源未提供可用直播");
    }

    private void showNoLiveChannels(String error) {
        if (noLiveChannelsShown) {
            return;
        }
        noLiveChannelsShown = true;
        showEmpty();
        new XPopup.Builder(this)
                .asConfirm("暂无直播频道", error + "。可以从已有订阅选择直播来源，或单独导入直播配置、M3U、TXT。", () -> {
                    new XPopup.Builder(this).asCustom(new com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog(this,
                            com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog.LIVE, null)).show();
                })
                .show();
    }

    @Override
    public void onSourceChanged(com.kukuqi.tvbox.osc.event.SourceChangedEvent event) {
        if (event.selectionChanged && mVideoView != null) {
            mVideoView.release(); initLiveChannelList();
        }
    }

    private void initLiveState() {
        String lastChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");

        int lastChannelGroupIndex = -1;
        int lastLiveChannelIndex = -1;
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            for (LiveChannelItem liveChannelItem : liveChannelGroup.getLiveChannels()) {
                if (liveChannelItem.getChannelName().equals(lastChannelName)) {
                    lastChannelGroupIndex = liveChannelGroup.getGroupIndex();
                    lastLiveChannelIndex = liveChannelItem.getChannelIndex();
                    break;
                }
            }
            if (lastChannelGroupIndex != -1) break;
        }
        if (lastChannelGroupIndex == -1) {
            lastChannelGroupIndex = getFirstNoPasswordChannelGroup();
            if (lastChannelGroupIndex == -1)
                lastChannelGroupIndex = 0;
            lastLiveChannelIndex = 0;
        }

        livePlayerManager.init(mVideoView);

        tvRightSettingLayout.setVisibility(View.INVISIBLE);

        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
    }

    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE || tvRightSettingLayout.getVisibility() == View.VISIBLE;
    }

    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
    }

    private void loadCurrentSourceList() {
        ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {

        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                    if (mAllChannelRightDialog instanceof AllChannelsRightDialog)
                        ((AllChannelsRightDialog) mAllChannelRightDialog).refresh();
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancel() {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1)
                mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        }
        else {
            mLiveChannelView.smoothScrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            if (groupIndex==0){//部分手机smoothScrollToPosition向上划出屏幕
                mChannelGroupView.scrollToPosition(groupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(groupIndex);
            }

            mLiveChannelView.smoothScrollToPosition(liveChannelIndex);
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty()
                && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex)
                return true;
        }
        return false;
    }

    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList<>();
        }
    }

    private Integer[] getNextChannel(int direction) {
        int channelGroupIndex = currentChannelGroupIndex;
        int liveChannelIndex = currentLiveChannelIndex;

        //跨选分组模式下跳过加密频道分组（遥控器上下键换台/超时换源）
        if (direction > 0) {
            liveChannelIndex++;
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size()) {
                liveChannelIndex = 0;
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++;
                        if (channelGroupIndex >= liveChannelGroupList.size())
                            channelGroupIndex = 0;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
            }
        } else {
            liveChannelIndex--;
            if (liveChannelIndex < 0) {
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--;
                        if (channelGroupIndex < 0)
                            channelGroupIndex = liveChannelGroupList.size() - 1;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size() - 1;
            }
        }

        Integer[] groupChannelIndex = new Integer[2];
        groupChannelIndex[0] = channelGroupIndex;
        groupChannelIndex[1] = liveChannelIndex;

        return groupChannelIndex;
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            if (liveChannelGroup.getGroupPassword().isEmpty())
                return liveChannelGroup.getGroupIndex();
        }
        return -1;
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    //计算两个时间相差的秒数
    public static long getTime(String startTime, String endTime)  {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long eTime = 0;
        try {
            eTime = df.parse(endTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long sTime = 0;
        try {
            sTime = df.parse(startTime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long diff = (eTime - sTime) / 1000;
        return diff;
    }
    private  String durationToString(int duration) {
        String result = "";
        int dur = duration / 1000;
        int hour=dur/3600;
        int min = (dur / 60) % 60;
        int sec = dur % 60;
        if(hour>0){
            if (min > 9) {
                if (sec > 9) {
                    result =hour+":"+ min + ":" + sec;
                } else {
                    result =hour+":"+ min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result =hour+":"+ "0" + min + ":" + sec;
                } else {
                    result = hour+":"+"0" + min + ":0" + sec;
                }
            }
        }else{
            if (min > 9) {
                if (sec > 9) {
                    result = min + ":" + sec;
                } else {
                    result = min + ":0" + sec;
                }
            } else {
                if (sec > 9) {
                    result ="0" + min + ":" + sec;
                } else {
                    result = "0" + min + ":0" + sec;
                }
            }
        }
        return result;
    }

    public void showAllChannelDialog() {
        mAllChannelRightDialog = new XPopup.Builder(this)
                .isViewMode(true)
                .autoFocusEditText(false)
                .hasNavigationBar(false)
                .hasShadowBg(false)
                .popupWidth(Math.min((int) (380 * getResources().getDisplayMetrics().density), (int) (ScreenUtils.getScreenWidth() * 0.92f)))
                .popupHeight(ScreenUtils.getScreenHeight())
                .popupPosition(PopupPosition.Right)
                .asCustom(new AllChannelsRightDialog(this));
        mAllChannelRightDialog.show();
    }

    public LivePlayerManager getLivePlayerManager(){
        return livePlayerManager;
    }

    public LiveChannelItem getCurrentLiveChannelItem(){
        return currentLiveChannelItem;
    }

    /**
     * 切换某个线路播放
     * @param position
     */
    public void switchingLine2Replay(int position){
        currentLiveChannelItem.setSourceIndex(position);
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
    }

    /**
     * 切换缩放比例
     * @param position
     */
    public void changeScale(int position){
        livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
    }

    /**
     * 更换播放解码
     * @param position
     */
    public void changePlayer(int position){
        mVideoView.release();
        livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
        mVideoView.setUrl(currentLiveChannelItem.getUrl(), currentLiveChannelItem.getHeaders());
        mVideoView.start();
    }

    /**
     * 设置弹窗
     * @param fullScreenStyle 全屏显示侧边弹窗
     */
    private void showSettingDialog(boolean fullScreenStyle) {
        if (!isCurrentLiveChannelValid()){
            ToastUtils.showShort("当前频道未加载");
            return;
        }
        if (fullScreenStyle){
            mSettingRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupWidth(ConvertUtils.dp2px(300))
                    .popupPosition(PopupPosition.Right)
                    .asCustom(new LiveSettingRightDialog(this));
            mSettingRightDialog.show();
        }else {
            mSettingBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .popupHeight(ScreenUtils.getScreenHeight()/2)
                    .hasNavigationBar(false)
                    .hasShadowBg(false)
                    .asCustom(new LiveSettingDialog(this));
            mSettingBottomDialog.show();
        }

    }

}
