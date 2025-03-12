package com.github.tvbox.osc.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.AppUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.Utils;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.OnTitleBarListener;
import com.hjq.bar.TitleBar;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.impl.LoadingPopupView;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import me.jessyan.autosize.internal.CustomAdapt;

public abstract class BaseActivity extends AppCompatActivity implements CustomAdapt, OnTitleBarListener {
    protected Context mContext;
    private LoadService mLoadService;

    private ImmersionBar mImmersionBar;
    private TitleBar mTitleBar;
    private LoadingPopupView loadingPopup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        if (getLayoutResID()==-1){
            initVb();
        }else {
            setContentView(getLayoutResID());
        }
        mContext = this;
        AppManager.getInstance().addActivity(this);
        initStatusBar();
        initTitleBar();
        init();
        if (!App.getInstance().isNormalStart){
            AppUtils.relaunchApp(true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {

    }


    private void initStatusBar(){
        ImmersionBar.with(this)
                .statusBarDarkFont(!Utils.isDarkTheme())
                .titleBar(findTitleBar(getWindow().getDecorView().findViewById(android.R.id.content)))
                .navigationBarColor(R.color.white)
                .init();
    }

    private void initTitleBar(){
        if (getTitleBar() != null) {
            getTitleBar().setOnTitleBarListener(this);
        }
    }

    /**
     * 递归获取 ViewGroup 中的 TitleBar 对象
     */
    private TitleBar findTitleBar(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if ((view instanceof TitleBar)) {
                return (TitleBar) view;
            } else if (view instanceof ViewGroup) {
                TitleBar titleBar = findTitleBar((ViewGroup) view);
                if (titleBar != null) {
                    return titleBar;
                }
            }
        }
        return null;
    }

    private TitleBar getTitleBar() {
        if (mTitleBar == null) {
            mTitleBar = findTitleBar(getWindow().getDecorView().findViewById(android.R.id.content));
        }
        return mTitleBar;
    }


    public boolean hasPermission(String permission) {
        boolean has = true;
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return has;
    }

    protected abstract int getLayoutResID();

    protected abstract void init();

    protected void initVb() {

    }

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                }
            });
        }
    }

    protected void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    protected void showEmpty() {
        if (null != mLoadService) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    protected void showSuccess() {
        if (null != mLoadService) {
            mLoadService.showSuccess();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        AppManager.getInstance().finishActivity(this);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz) {
        Intent intent = new Intent(mContext, clazz);
        startActivity(intent);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz, Bundle bundle) {
        if (DetailActivity.class.isAssignableFrom(clazz) && Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2) {
            //1.重新打开singleTask的页面(关闭小窗) 2.关闭画中画，重进detail再开启画中画会闪退
            ActivityUtils.finishActivity(DetailActivity.class);
        }
        Intent intent = new Intent(mContext, clazz);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    protected String getAssetText(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assets = getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(assets.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public float getSizeInDp() {
        return isBaseOnWidth() ? 360 : 720;
    }

    @Override
    public boolean isBaseOnWidth() {
        return true;
    }

    @Override
    public void onLeftClick(TitleBar titleBar) {
        finish();
    }


    /**
     * 显示加载框
     */
    public void showLoadingDialog() {
        if (loadingPopup == null) {
            loadingPopup = new XPopup.Builder(this)
                    .isLightNavigationBar(true)
                    .hasShadowBg(false)
                    .asLoading();
        }
        loadingPopup.show();
    }

    /**
     * 隐藏加载框
     */
    public void dismissLoadingDialog() {
        if (loadingPopup != null && loadingPopup.isShow()) {
            loadingPopup.dismiss();
        }
    }

}