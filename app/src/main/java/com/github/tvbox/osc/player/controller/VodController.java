package com.github.tvbox.osc.player.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.subtitle.widget.SimpleSubtitleView;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.widget.MyBatteryView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.Utils;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import java.util.Date;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;

public class VodController extends BaseController {

    public VodController(@NonNull @NotNull Context context) {
        super(context);
        mHandlerCallback = new HandlerCallback() {
            @Override
            public void callback(Message msg) {
                switch (msg.what) {
                    case 1000: { // seek 刷新
                        mProgressRoot.setVisibility(VISIBLE);
                        break;
                    }
                    case 1001: { // seek 关闭
                        mProgressRoot.setVisibility(GONE);
                        break;
                    }
                    case 1002: { // 显示底部菜单
                        toggleViewShowWithAlpha(mBottomRoot, true);
                        toggleViewShowWithAlpha(mTopRoot1, true);
                        toggleViewShowWithAlpha(mTopRoot2, true);
                        if (!isLock){// 未上锁,随底部显示
                            toggleViewShowWithAlpha(mLockView, true);
                        }
                        mNextBtn.requestFocus();
                        break;
                    }
                    case 1003: { // 隐藏底部菜单
                        toggleViewShowWithAlpha(mBottomRoot, false);
                        toggleViewShowWithAlpha(mTopRoot1, false);
                        toggleViewShowWithAlpha(mTopRoot2, false);
                        if (!isLock){// 未上锁,随底部显示
                            toggleViewShowWithAlpha(mLockView, false);
                        }
                        if (listener != null) {
                            listener.onHideBottom();
                        }
                        break;
                    }
                    case 1004: { // 设置速度
                        if (isInPlaybackState()) {
                            try {
                                float speed = (float) mPlayerConfig.getDouble("sp");
                                mControlWrapper.setSpeed(speed);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else
                            mHandler.sendEmptyMessageDelayed(1004, 100);
                        break;
                    }
                }
            }
        };
    }

    private LinearLayout mLlSpeed;
    TextView mTvSpeedTip;
    SeekBar mSeekBar;
    TextView mCurrentTime;
    TextView mTotalTime;
    boolean mIsDragging;
    View mProgressRoot;
    TextView mProgressText;
    ImageView mProgressIcon;
    LinearLayout mBottomRoot;
    LinearLayout mTopRoot1;
    View mTopRoot2;
    LinearLayout mParseRoot;
    TvRecyclerView mGridView;
    TextView mPlayTitle1;
    TextView mPlayLoadNetSpeedRightTop;
    ImageView mNextBtn;
    ImageView mPreBtn;
    public TextView mPlayerScaleBtn;
    public TextView mPlayerSpeedBtn;
    public TextView mPlayerBtn;
    public TextView mPlayerIJKBtn;
    public TextView mPlayerTimeStartEndText;
    public TextView mPlayerTimeStartBtn;
    public TextView mPlayerTimeSkipBtn;
    public TextView mPlayerTimeResetBtn;
    TextView mPlayPauseTime;
    TextView mPlayLoadNetSpeed;
    TextView mVideoSize;
    public SimpleSubtitleView mSubtitleView;
    public TextView mZimuBtn;
    public TextView mAudioTrackBtn;
    public TextView mLandscapePortraitBtn;
    private ImageView mIvPlayStatus;
    private View mChooseSeries;
    public MyBatteryView mMyBatteryView;
    private View mTopRightDeviceInfo;
    public TextView mPlayRetry;
    public TextView mPlayRefresh;
    ImageView mLockView;
    Handler myHandle;
    Runnable myRunnable;
    int dismissTimeOperationBar = 5000;//闲置多少毫秒隐藏操作栏(上中下)  默认6秒
    int dismissTimeLock = 2000;//闲置多少毫秒隐藏已上锁按钮

    int videoPlayState = 0;
    LockRunnable lockRunnable = new LockRunnable();
    private boolean isLock = false;
    private ParseAdapter mParseAdapter;

    private Runnable myRunnable2 = new Runnable() {
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            mPlayPauseTime.setText(timeFormat.format(date));
            String speed = PlayerHelper.getDisplaySpeed(mControlWrapper.getTcpSpeed());
            mPlayLoadNetSpeedRightTop.setText(speed);
            mPlayLoadNetSpeed.setText(speed);

            if (mControlWrapper.getVideoSize()[0] > 0 && mControlWrapper.getVideoSize()[1] > 0) {
                String width = Integer.toString(mControlWrapper.getVideoSize()[0]);
                String height = Integer.toString(mControlWrapper.getVideoSize()[1]);
                mVideoSize.setText(width + " x " + height);
            }

            mHandler.postDelayed(this, 1000);
        }
    };
    private class LockRunnable implements Runnable {
        @Override
        public void run() {
            if (isLock){//上锁的才隐藏,非上锁状态随操作栏显示隐藏
                mLockView.setVisibility(GONE);
            }
        }
    }

    @Override
    protected void initView() {
        super.initView();
        View pip = findViewById(R.id.pip);
        pip.setVisibility((Utils.supportsPiPMode() && Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2)?VISIBLE:GONE);
        mMyBatteryView = findViewById(R.id.battery);
        mTopRightDeviceInfo = findViewById(R.id.container_top_right_device_info);
        mLlSpeed = findViewById(R.id.ll_speed);
        mTvSpeedTip = findViewById(R.id.tv_speed);
        mCurrentTime = findViewById(R.id.curr_time);
        mTotalTime = findViewById(R.id.total_time);
        mPlayTitle1 = findViewById(R.id.tv_info_name1);
        mPlayLoadNetSpeedRightTop = findViewById(R.id.tv_play_load_net_speed_right_top);
        mSeekBar = findViewById(R.id.seekBar);
        mProgressRoot = findViewById(R.id.tv_progress_container);
        mProgressIcon = findViewById(R.id.tv_progress_icon);
        mProgressText = findViewById(R.id.tv_progress_text);
        mBottomRoot = findViewById(R.id.bottom_container);
        mTopRoot1 = findViewById(R.id.tv_top_l_container);
        mTopRoot2 = findViewById(R.id.tv_top_r_container);
        mParseRoot = findViewById(R.id.parse_root);
        mGridView = findViewById(R.id.mGridView);
        mNextBtn = findViewById(R.id.play_next);
        mPreBtn = findViewById(R.id.play_pre);
        mPlayerScaleBtn = findViewById(R.id.play_scale);
        mPlayerSpeedBtn = findViewById(R.id.play_speed);
        mPlayerBtn = findViewById(R.id.play_player);
        mPlayerIJKBtn = findViewById(R.id.play_ijk);
        mPlayerTimeStartEndText = findViewById(R.id.play_time_start_end_text);
        mPlayerTimeStartBtn = findViewById(R.id.play_time_start);
        mPlayerTimeSkipBtn = findViewById(R.id.play_time_end);
        mPlayerTimeResetBtn = findViewById(R.id.play_time_reset);
        mPlayPauseTime = findViewById(R.id.tv_sys_time);
        mPlayLoadNetSpeed = findViewById(R.id.tv_play_load_net_speed);
        mVideoSize = findViewById(R.id.tv_videosize);
        mSubtitleView = findViewById(R.id.subtitle_view);
        mZimuBtn = findViewById(R.id.zimu_select);
        mAudioTrackBtn = findViewById(R.id.audio_track_select);
        mLandscapePortraitBtn = findViewById(R.id.landscape_portrait);
        mIvPlayStatus = findViewById(R.id.play_status);
        mChooseSeries = findViewById(R.id.choose_series);
        mLockView = findViewById(R.id.iv_lock);

        initSubtitleInfo();

        myHandle = new Handler();

        mLockView.setOnClickListener(v -> {
            isLock = !isLock;
            if (isLock){// 上了锁
                mLockView.setImageResource(R.drawable.ic_lock);
                hideBottom();
                mHandler.removeCallbacks(lockRunnable);
                mHandler.postDelayed(lockRunnable,dismissTimeLock);
            }else {// 解了锁
                mLockView.setImageResource(R.drawable.ic_unlock);
                showBottom();
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            }
        });
        View rootView = findViewById(R.id.rootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLock) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {//短暂显示上锁view,lockRunnable统一隐藏上锁view
                        mLockView.setVisibility(VISIBLE);
                        mHandler.removeCallbacks(lockRunnable);
                        mHandler.postDelayed(lockRunnable, dismissTimeLock);
                    }
                }
                return isLock;
            }
        });

        myRunnable = this::hideBottom;

        mPlayPauseTime.post(new Runnable() {
            @Override
            public void run() {
                mHandler.post(myRunnable2);
            }
        });

        mGridView.setLayoutManager(new V7LinearLayoutManager(getContext(), 0, false));
        mParseAdapter = new ParseAdapter();
        mParseAdapter.setOnItemClickListener((adapter, view, position) -> {
            ParseBean parseBean = mParseAdapter.getItem(position);
            // 当前默认解析需要刷新
            int currentDefault = mParseAdapter.getData().indexOf(ApiConfig.get().getDefaultParse());
            mParseAdapter.notifyItemChanged(currentDefault);
            ApiConfig.get().setDefaultParse(parseBean);
            mParseAdapter.notifyItemChanged(position);
            listener.changeParse(parseBean);
            hideBottom();
        });
        mGridView.setAdapter(mParseAdapter);
        mParseAdapter.setNewData(ApiConfig.get().getParseBeanList());

        //mParseRoot.setVisibility(VISIBLE);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTime != null)
                    mCurrentTime.setText(stringForTime((int) newPosition));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo((int) newPosition);
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
            }
        });

        mTopRoot1.setOnClickListener(view -> listener.exit());

        mPlayRetry = findViewById(R.id.play_retry);
        mPlayRetry.setOnClickListener(v -> {
            listener.replay(true);
            hideBottom();
        });
        mPlayRefresh = findViewById(R.id.play_refresh);
        mPlayRefresh.setOnClickListener(v -> {
            listener.replay(false);
            hideBottom();
        });
        mIvPlayStatus.setOnClickListener(view -> {
            togglePlay();
            if (videoPlayState == VideoView.STATE_PLAYING) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, 300);
            }
        });
        mNextBtn.setOnClickListener(view -> {
            listener.playNext(false);
            hideBottom();
        });
        mPreBtn.setOnClickListener(view -> {
            listener.playPre();
            hideBottom();
        });
        findViewById(R.id.setting).setOnClickListener(view -> {
            hideBottom();
            listener.showSetting();
        });
        findViewById(R.id.iv_fullscreen).setOnClickListener(view -> {
            listener.toggleFullScreen();
            hideBottom();
        });
        findViewById(R.id.cast).setOnClickListener(view -> {
            listener.cast();
            hideBottom();
        });
        pip.setOnClickListener(view -> {//画中画
            if (isInPlaybackState()){
                listener.pip();
                hideBottom();
            }
        });
        mPlayerScaleBtn.setOnClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                int scaleType = mPlayerConfig.getInt("sc");
                scaleType++;
                if (scaleType > 5)
                    scaleType = 0;
                mPlayerConfig.put("sc", scaleType);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setScreenScaleType(scaleType);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerSpeedBtn.setOnClickListener(view -> setSpeed(""));

        mPlayerSpeedBtn.setOnLongClickListener(view -> {
            try {
                mPlayerConfig.put("sp", 1.0f);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                speed_old = 1.0f;
                mControlWrapper.setSpeed(1.0f);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        });
        mPlayerBtn.setOnClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                int playerType = mPlayerConfig.getInt("pl");
                ArrayList<Integer> exsitPlayerTypes = PlayerHelper.getExistPlayerTypes();
                int playerTypeIdx = 0;
                int playerTypeSize = exsitPlayerTypes.size();
                for (int i = 0; i < playerTypeSize; i++) {
                    if (playerType == exsitPlayerTypes.get(i)) {
                        if (i == playerTypeSize - 1) {
                            playerTypeIdx = 0;
                        } else {
                            playerTypeIdx = i + 1;
                        }
                    }
                }
                playerType = exsitPlayerTypes.get(playerTypeIdx);
                mPlayerConfig.put("pl", playerType);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                listener.replay(false);
                hideBottom();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mPlayerBtn.requestFocus();
            mPlayerBtn.requestFocusFromTouch();
        });

        mPlayerBtn.setOnLongClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            FastClickCheckUtil.check(view);
            try {
                int playerType = mPlayerConfig.getInt("pl");
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for (int p = 0; p < players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择播放器");
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        try {
                            dialog.cancel();
                            int thisPlayType = players.get(pos);
                            if (thisPlayType != playerType) {
                                mPlayerConfig.put("pl", thisPlayType);
                                updatePlayerCfgView();
                                listener.updatePlayerCfg();
                                listener.replay(false);
                                hideBottom();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mPlayerBtn.requestFocus();
                        mPlayerBtn.requestFocusFromTouch();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        });
        mPlayerIJKBtn.setOnClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                String ijk = mPlayerConfig.getString("ijk");
                List<IJKCode> codecs = ApiConfig.get().getIjkCodes();
                for (int i = 0; i < codecs.size(); i++) {
                    if (ijk.equals(codecs.get(i).getName())) {
                        if (i >= codecs.size() - 1)
                            ijk = codecs.get(0).getName();
                        else {
                            ijk = codecs.get(i + 1).getName();
                        }
                        break;
                    }
                }
                mPlayerConfig.put("ijk", ijk);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                listener.replay(false);
                hideBottom();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mPlayerIJKBtn.requestFocus();
            mPlayerIJKBtn.requestFocusFromTouch();
        });
//        增加播放页面片头片尾时间重置
        mPlayerTimeResetBtn.setOnClickListener(v -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                mPlayerConfig.put("et", 0);
                mPlayerConfig.put("st", 0);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeStartBtn.setOnClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                int current = (int) mControlWrapper.getCurrentPosition();
                int duration = (int) mControlWrapper.getDuration();
                if (current > duration / 2) return;
                mPlayerConfig.put("st", current / 1000);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeStartBtn.setOnLongClickListener(view -> {
            try {
                mPlayerConfig.put("st", 0);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        });
        mPlayerTimeSkipBtn.setOnClickListener(view -> {
            myHandle.removeCallbacks(myRunnable);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            try {
                int current = (int) mControlWrapper.getCurrentPosition();
                int duration = (int) mControlWrapper.getDuration();
                if (current < duration / 2) return;
                mPlayerConfig.put("et", (duration - current) / 1000);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeSkipBtn.setOnLongClickListener(view -> {
            try {
                mPlayerConfig.put("et", 0);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        });
        mZimuBtn.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            listener.selectSubtitle();
            hideBottom();
        });
        mAudioTrackBtn.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            listener.selectAudioTrack();
            hideBottom();
        });
        mLandscapePortraitBtn.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            setLandscapePortrait();
            hideBottom();
        });
        mNextBtn.setNextFocusLeftId(R.id.play_time_start);
        mChooseSeries.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            hideBottom();
            listener.chooseSeries();
        });

        findViewById(R.id.container_playing_setting).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // User is scrolling, remove callbacks
                    myHandle.removeCallbacks(myRunnable);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // User stopped scrolling, post callbacks
                    myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
                    break;
            }
            return false;
        });
    }

    public void setSpeed(String speedStr) {
        myHandle.removeCallbacks(myRunnable);
        myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
        try {
            float speed = (float) mPlayerConfig.getDouble("sp");
            if (TextUtils.isEmpty(speedStr)) {// 未设置.点击切换
                speed += 0.25f;
                if (speed > 3)
                    speed = 0.5f;
            } else {
                speed = Float.parseFloat(speedStr);
            }
            mPlayerConfig.put("sp", speed);
            updatePlayerCfgView();
            listener.updatePlayerCfg();
            speed_old = speed;
            mControlWrapper.setSpeed(speed);
        } catch (Exception e) {
            ToastUtils.showShort("倍速参数异常");
            e.printStackTrace();
        }
    }

    private void hideLiveAboutBtn() {
        if (mControlWrapper != null && mControlWrapper.getDuration() == 0) {
            mPlayerSpeedBtn.setVisibility(GONE);
            mPlayerTimeStartEndText.setVisibility(GONE);
            mPlayerTimeStartBtn.setVisibility(GONE);
            mPlayerTimeSkipBtn.setVisibility(GONE);
            mPlayerTimeResetBtn.setVisibility(GONE);
            mNextBtn.setNextFocusLeftId(R.id.zimu_select);
        } else {
            mPlayerSpeedBtn.setVisibility(View.VISIBLE);
            mPlayerTimeStartEndText.setVisibility(View.VISIBLE);
            mPlayerTimeStartBtn.setVisibility(View.VISIBLE);
            mPlayerTimeSkipBtn.setVisibility(View.VISIBLE);
            mPlayerTimeResetBtn.setVisibility(View.VISIBLE);
            mNextBtn.setNextFocusLeftId(R.id.play_time_start);
        }
    }

    public void initLandscapePortraitBtnInfo() {
        if (mControlWrapper != null && mActivity != null) {
            int width = mControlWrapper.getVideoSize()[0];
            int height = mControlWrapper.getVideoSize()[1];
            double screenSqrt = ScreenUtils.getSqrt(mActivity);
            if (screenSqrt < 10.0 && width < height) {
                mLandscapePortraitBtn.setVisibility(View.VISIBLE);
                mLandscapePortraitBtn.setText("竖屏");
            }
        }
    }

    /**
     * 横竖屏切换
     */
    void setLandscapePortrait() {
        if (com.blankj.utilcode.util.ScreenUtils.isPortrait()){
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }else {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    void initSubtitleInfo() {
        int subtitleTextSize = SubtitleHelper.getTextSize(mActivity);
        mSubtitleView.setTextSize(subtitleTextSize);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    public void showParse(boolean userJxList) {
        //mParseRoot.setVisibility(userJxList ? VISIBLE : GONE);
        if (listener!=null && mParseAdapter!=null){
            listener.showParseRoot(userJxList,mParseAdapter);
        }
    }

    private JSONObject mPlayerConfig = null;

    public void setPlayerConfig(JSONObject playerCfg) {
        this.mPlayerConfig = playerCfg;
        updatePlayerCfgView();
    }

    void updatePlayerCfgView() {
        try {
            int playerType = mPlayerConfig.getInt("pl");
            mPlayerBtn.setText(PlayerHelper.getPlayerName(playerType));
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerIJKBtn.setText(mPlayerConfig.getString("ijk"));
            mPlayerIJKBtn.setVisibility(playerType == 1 ? VISIBLE : GONE);
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mPlayerConfig.getInt("sc")));
            mPlayerSpeedBtn.setText("x" + mPlayerConfig.getDouble("sp"));
            mPlayerTimeStartBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("st") * 1000));
            mPlayerTimeSkipBtn.setText(PlayerUtils.stringForTime(mPlayerConfig.getInt("et") * 1000));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setTitle(String playTitleInfo) {
        mPlayTitle1.setText(playTitleInfo);
    }

    public void resetSpeed() {
        skipEnd = true;
        mHandler.removeMessages(1004);
        mHandler.sendEmptyMessageDelayed(1004, 100);
    }

    /**
     * 变成全屏
     *
     * @param b
     */
    public void changedLandscape(boolean b) {
        mPlayTitle1.setSelected(true);
        if (b) {
            mPreBtn.setVisibility(VISIBLE);
            mNextBtn.setVisibility(VISIBLE);
            mChooseSeries.setVisibility(VISIBLE);
            mTopRightDeviceInfo.setVisibility(VISIBLE);
        } else {
            mTopRightDeviceInfo.setVisibility(INVISIBLE);
            mPreBtn.setVisibility(GONE);
            mNextBtn.setVisibility(GONE);
            mChooseSeries.setVisibility(GONE);
        }
    }

    public interface VodControlListener {
        void chooseSeries();

        void playNext(boolean rmProgress);

        void playPre();

        void prepared();

        void changeParse(ParseBean pb);

        void updatePlayerCfg();

        void replay(boolean replay);

        void errReplay();

        void selectSubtitle();

        void selectAudioTrack();

        void toggleFullScreen();

        void exit();

        void cast();

        /**
         * Imm..bar沉浸式在系统弹窗/部分弹窗消失后会重新显示标题栏状态栏(未解决),暂时将隐藏底部栏的时机回调给外部页面处理
         */
        void onHideBottom();

        void showSetting();

        void pip();

        void showParseRoot(boolean show,ParseAdapter adapter);
    }

    public void setListener(VodControlListener listener) {
        this.listener = listener;
    }

    private VodControlListener listener;

    private boolean skipEnd = true;

    @Override
    protected void setProgress(int duration, int position) {

        if (mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        if (skipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mPlayerConfig.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false;
                listener.playNext(true);
            }
        }
        mCurrentTime.setText(PlayerUtils.stringForTime(position));
        mTotalTime.setText(PlayerUtils.stringForTime(duration));
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            int pos = (int) (position * 1.0 / duration * mSeekBar.getMax());
            mSeekBar.setProgress(pos);
        } else {
            mSeekBar.setEnabled(false);
        }
        int percent = mControlWrapper.getBufferedPercentage();
        if (percent >= 95) {
            mSeekBar.setSecondaryProgress(mSeekBar.getMax());
        } else {
            mSeekBar.setSecondaryProgress(percent * 10);
        }
    }

    private boolean simSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;

    public void tvSlideStop() {
        if (!simSlideStart)
            return;
        mControlWrapper.seekTo(simSeekPosition);
        if (!mControlWrapper.isPlaying())
            mControlWrapper.start();
        simSlideStart = false;
        simSeekPosition = 0;
        simSlideOffset = 0;
    }

    public void tvSlideStart(int dir) {
        int duration = (int) mControlWrapper.getDuration();
        if (duration <= 0)
            return;
        if (!simSlideStart) {
            simSlideStart = true;
        }
        // 每次10秒
        simSlideOffset += (10000.0f * dir);
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, int duration) {
        super.updateSeekUI(curr, seekTo, duration);
        if (seekTo > curr) {
            mProgressIcon.setImageResource(R.drawable.icon_pre);
        } else {
            mProgressIcon.setImageResource(R.drawable.icon_back);
        }
        mProgressText.setText(PlayerUtils.stringForTime(seekTo) + " / " + PlayerUtils.stringForTime(duration));
        mHandler.sendEmptyMessage(1000);
        mHandler.removeMessages(1001);
        mHandler.sendEmptyMessageDelayed(1001, 1000);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH_NOTIFY, null));
        videoPlayState = playState;
        switch (playState) {
            case VideoView.STATE_IDLE:
                break;
            case VideoView.STATE_PLAYING:
                initLandscapePortraitBtnInfo();
                startProgress();
                mIvPlayStatus.setImageResource(R.drawable.ic_pause);
                break;
            case VideoView.STATE_PAUSED:
                mIvPlayStatus.setImageResource(R.drawable.ic_play);
                break;
            case VideoView.STATE_ERROR:
                listener.errReplay();
                break;
            case VideoView.STATE_PREPARED:
                mPlayLoadNetSpeed.setVisibility(GONE);
                hideLiveAboutBtn();
                listener.prepared();
                break;
            case VideoView.STATE_BUFFERED:
                mPlayLoadNetSpeed.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                if (mProgressRoot.getVisibility() == GONE) mPlayLoadNetSpeed.setVisibility(VISIBLE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                listener.playNext(true);
                break;
        }
    }

    boolean isBottomVisible() {
        return mBottomRoot.getVisibility() == VISIBLE;
    }

    void showBottom() {
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
    }

    public void hideBottom() {
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1003);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        myHandle.removeCallbacks(myRunnable);
        if (super.onKeyEvent(event)) {
            return true;
        }
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (isBottomVisible()) {
            mHandler.removeMessages(1002);
            mHandler.removeMessages(1003);
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
            return super.dispatchKeyEvent(event);
        }
        boolean isInPlayback = isInPlaybackState();
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback) {
                    togglePlay();
                    return true;
                }
//            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {  return true;// 闲置开启计时关闭透明底栏
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_MENU) {
                if (!isBottomVisible()) {
                    showBottom();
                    myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
                    return true;
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    tvSlideStop();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }


    private boolean fromLongPress;
    private float speed_old = 1.0f;

    @Override
    public void onLongPress(MotionEvent e) {
        if (videoPlayState != VideoView.STATE_PAUSED) {
            fromLongPress = true;
            try {
                speed_old = (float) mPlayerConfig.getDouble("sp");
                float speed = Hawk.get(HawkConfig.VIDEO_SPEED, 2.0f);
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
                mLlSpeed.setVisibility(VISIBLE);
                mTvSpeedTip.setText(speed + "x");
            } catch (JSONException f) {
                f.printStackTrace();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (fromLongPress) {
                fromLongPress = false;
                mLlSpeed.setVisibility(GONE);
                try {
                    float speed = speed_old;
                    mPlayerConfig.put("sp", speed);
                    updatePlayerCfgView();
                    listener.updatePlayerCfg();
                    mControlWrapper.setSpeed(speed);
                } catch (JSONException f) {
                    f.printStackTrace();
                }
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        myHandle.removeCallbacks(myRunnable);
        if (!isBottomVisible()) {
            showBottom();
            // 闲置计时关闭
            myHandle.postDelayed(myRunnable, dismissTimeOperationBar);
        } else {
            hideBottom();
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        if (super.onBackPressed()) {
            return true;
        }
        if (isBottomVisible()) {
            hideBottom();
            return true;
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(myRunnable2);
    }

    public void openSubtitle(boolean open) {
        if (open) {
            mSubtitleView.setVisibility(VISIBLE);
            Toast.makeText(getContext(), "字幕已开启", Toast.LENGTH_SHORT).show();
        } else {
            mSubtitleView.setVisibility(View.GONE);
            Toast.makeText(getContext(), "字幕已关闭", Toast.LENGTH_SHORT).show();
        }
        hideBottom();
    }

    public void increaseTime(String type) {
        try {
            int step = Hawk.get(HawkConfig.PLAY_TIME_STEP, 1);
            int time = mPlayerConfig.getInt(type);
            time += step;
            if (time > 30 * 10)
                time = 0;
            mPlayerConfig.put(type, time);
            updatePlayerCfgView();
            listener.updatePlayerCfg();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void decreaseTime(String type) {
        try {
            int step = Hawk.get(HawkConfig.PLAY_TIME_STEP, 1);
            int time = mPlayerConfig.getInt(type);
            time -= step;
            if (time < 0)
                time = (30 * 10);
            mPlayerConfig.put(type, time);
            updatePlayerCfgView();
            listener.updatePlayerCfg();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void toggleViewShowWithAlpha(View view, boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(1.0f)
                    .setDuration(100)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        } else {
            view.animate()
                    .alpha(0.0f)
                    .setDuration(100)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> view.setVisibility(View.GONE))
                    .start();
        }
    }
}
