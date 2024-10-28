package com.github.tvbox.osc.ui.widget;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.controller.ControlWrapper;
import xyz.doikki.videoplayer.controller.IControlComponent;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * 播放器顶部标题栏
 */
public class PlayerMenuView extends FrameLayout implements IControlComponent {

    public interface OnPlayerMenuClickListener{
        void onSetting();
        void onCast();
        void expand();
    }

    private OnPlayerMenuClickListener mOnPlayerMenuClickListener;

    private ControlWrapper mControlWrapper;

    public PlayerMenuView(@NonNull Context context) {
        super(context);
    }

    public PlayerMenuView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PlayerMenuView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        setVisibility(GONE);
        LayoutInflater.from(getContext()).inflate(R.layout.dkplayer_layout_menu_view, this, true);
        findViewById(R.id.cast).setOnClickListener(view -> {
            if (mOnPlayerMenuClickListener!=null){
                mOnPlayerMenuClickListener.onCast();
            }
        });
        findViewById(R.id.setting).setOnClickListener(view -> {
            if (mOnPlayerMenuClickListener!=null){
                mOnPlayerMenuClickListener.onSetting();
            }
        });
        findViewById(R.id.expand).setOnClickListener(view -> {
            if (mOnPlayerMenuClickListener!=null){
                mOnPlayerMenuClickListener.expand();
            }
        });
    }


    @Override
    public void attach(@NonNull ControlWrapper controlWrapper) {
        mControlWrapper = controlWrapper;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onVisibilityChanged(boolean isVisible, Animation anim) {
        if (!mControlWrapper.isFullScreen()) return;
        if (isVisible) {
            if (getVisibility() == GONE) {
                setVisibility(VISIBLE);
                if (anim != null) {
                    startAnimation(anim);
                }
            }
        } else {
            if (getVisibility() == VISIBLE) {
                setVisibility(GONE);
                if (anim != null) {
                    startAnimation(anim);
                }
            }
        }
    }

    @Override
    public void onPlayStateChanged(int playState) {
        switch (playState) {
            case VideoView.STATE_IDLE:
            case VideoView.STATE_START_ABORT:
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_PLAYBACK_COMPLETED:
                setVisibility(GONE);
                break;
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState) {
        if (playerState == VideoView.PLAYER_FULL_SCREEN) {
            if (mControlWrapper.isShowing() && !mControlWrapper.isLocked()) {
                setVisibility(VISIBLE);
            }
        } else {
            setVisibility(GONE);
        }
    }

    @Override
    public void setProgress(int duration, int position) {

    }

    @Override
    public void onLockStateChanged(boolean isLocked) {
        if (isLocked) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    public void setOnPlayerMenuClickListener(OnPlayerMenuClickListener onPlayerMenuClickListener) {
        mOnPlayerMenuClickListener = onPlayerMenuClickListener;
    }
}
