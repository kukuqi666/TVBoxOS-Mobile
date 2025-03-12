package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NotificationUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ServiceUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseVbActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.databinding.ActivityDetailBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.BatteryReceiver;
import com.github.tvbox.osc.service.PlayService;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesBottomDialog;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesRightDialog;
import com.github.tvbox.osc.ui.dialog.CastListDialog;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.ui.dialog.VideoDetailDialog;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ScreenShotListenManager;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.Utils;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseVbActivity<ActivityDetailBinding> {
    private PlayFragment playFragment = null;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    public SeriesFlagAdapter seriesFlagAdapter;
    public SeriesAdapter seriesAdapter;
    public String vodId;
    public String sourceKey;
    private View seriesFlagFocus = null;
    private boolean isReverse;
    private String preFlag = "";
    private HashMap<String, String> mCheckSources = null;
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();
    //改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
    private BasePopupView mAllSeriesRightDialog;
    private BasePopupView mAllSeriesBottomDialog;
    /**
     * Home键广播,用于触发后台服务
     */
    private BroadcastReceiver mHomeKeyReceiver;
    /**
     * 是否开启后台播放标记,不在广播开启,onPause根据标记开启
     */
    boolean openBackgroundPlay;
    private BroadcastReceiver mRemoteActionReceiver;

    /**
     * 截屏监听
     */
    ScreenShotListenManager screenShotListenManager;

    @Override
    protected void init() {
        initReceiver();
        initView();
        initViewModel();
        initData();
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .navigationBarColor(R.color.white)
                .fitsSystemWindows(true)
                .statusBarDarkFont(false)
                .init();
        toggleScreenShotListen(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundPlay = false;
        playServerSwitch(false);
        mBinding.ivPrivateBrowsing.postDelayed(NotificationUtils::cancelAll, 800);
    }

    private void initView() {
        mBinding.ivPrivateBrowsing.setVisibility(Hawk.get(HawkConfig.PRIVATE_BROWSING, false) ? View.VISIBLE : View.GONE);
        mBinding.ivPrivateBrowsing.setOnClickListener(view -> ToastUtils.showShort("当前为无痕浏览"));
        mBinding.previewPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);

        mBinding.mGridView.setHasFixedSize(true);
        mBinding.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        mBinding.mGridView.addItemDecoration(new LinearSpacingItemDecoration(20, false));

        seriesAdapter = new SeriesAdapter(false);
        mBinding.mGridView.setAdapter(seriesAdapter);
        mBinding.mGridViewFlag.setHasFixedSize(true);
        seriesFlagAdapter = new SeriesFlagAdapter();
        mBinding.mGridViewFlag.setAdapter(seriesFlagAdapter);
        isReverse = false;
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commit();
            getSupportFragmentManager().beginTransaction().show(playFragment).commitAllowingStateLoss();
        }

        findViewById(R.id.ll_title).setOnClickListener(view -> {
            new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .asCustom(new VideoDetailDialog(this, vodInfo))
                    .show();
        });
        findViewById(R.id.tvDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                use1DMDownload();
            }
        });
        mBinding.tvSort.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                sortSeries();
            }
        });
        mBinding.tvCast.setOnClickListener(v -> {
            showCastDialog();
        });
        mBinding.tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mBinding.tvCollect.getText().toString();
                if ("加入收藏".equals(text)) {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("取消收藏");
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已移除收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("加入收藏");
                }
            }
        });

        seriesFlagAdapter.setOnItemClickListener((adapter, view, position) -> {
            chooseFlag(position);
        });

        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                chooseSeries(position, false);
            }
        });

        mBinding.tvAllSeries.setOnClickListener(view -> {
            showAllSeriesDialog();
        });

        mBinding.tvSite.setOnClickListener(view -> {
            startQuickSearch();
            QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
            quickSearchDialog.show();
            if (pauseRunnable != null && pauseRunnable.size() > 0) {
                searchExecutorService = Executors.newFixedThreadPool(5);
                for (Runnable runnable : pauseRunnable) {
                    searchExecutorService.execute(runnable);
                }
                pauseRunnable.clear();
                pauseRunnable = null;
            }
            quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        });
        mBinding.tvChangeLine.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            quickLineChange();
        });
        setLoadSir(mBinding.llLayout);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (openBackgroundPlay) {
            playServerSwitch(true);
        }
    }

    private void initReceiver() {
        // 注册广播接收器
        if (mHomeKeyReceiver == null) {
            mHomeKeyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null && action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                        openBackgroundPlay = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 1 && playFragment.getPlayer() != null && playFragment.getPlayer().isPlaying();
                    }
                }
            };
            registerReceiver(mHomeKeyReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }

    /**
     * 排序(倒序)
     */
    public void sortSeries() {
        if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
            vodInfo.reverseSort = !vodInfo.reverseSort;
            isReverse = !isReverse;
            vodInfo.reverse();
            vodInfo.playIndex = (vodInfo.seriesMap.get(vodInfo.playFlag).size() - 1) - vodInfo.playIndex;
//                    insertVod(sourceKey, vodInfo);

            seriesAdapter.notifyDataSetChanged();
        }
    }

    public void showCastDialog() {

        VodInfo.VodSeries vodSeries = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
        new XPopup.Builder(this)
                .maxWidth(ConvertUtils.dp2px(360))
                .asCustom(new CastListDialog(this, new CastVideo(vodSeries.name
                        , TextUtils.isEmpty(playFragment.getFinalUrl()) ? vodSeries.url : playFragment.getFinalUrl())))
                .show();
    }

    public void showAllSeriesDialog() {
        if (fullWindows) {
            mAllSeriesRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)//隐藏导航栏(手势条)在dialog模式下会闪一下,改为view模式,但需处理onBackPress的隐藏,下方同理
                    .hasNavigationBar(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupPosition(PopupPosition.Right)
                    .enableDrag(false)//禁用拖拽,内部有横向rv
                    .asCustom(new AllVodSeriesRightDialog(this));
            mAllSeriesRightDialog.show();
        } else {
            mAllSeriesBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                    .asCustom(new AllVodSeriesBottomDialog(this, seriesAdapter.getData(), (position, text) -> {
                        chooseSeries(position, false);
                    }));
            mAllSeriesBottomDialog.show();
        }
    }

    private void chooseFlag(int position) {
        //新选中的flag
        String newFlag = seriesFlagAdapter.getData().get(position).name;
        if (vodInfo != null && !vodInfo.playFlag.equals(newFlag)) {
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {//遍历flag集合
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {//取消当前播放的选中状态
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
            //新选中的flag
            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(position);
            flag.selected = true;
            //清除上一个线路集数的选中状态
            List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (currentSeriesList.size() > vodInfo.playIndex) {//有效集数
                currentSeriesList.get(vodInfo.playIndex).selected = false;
            }
            vodInfo.playFlag = newFlag;
            seriesFlagAdapter.notifyItemChanged(position);
            refreshList();
        }
    }

    private void chooseSeries(int position, boolean reloadWithChangeLine) {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            boolean reload = false;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                seriesAdapter.getData().get(j).selected = false;
                seriesAdapter.notifyItemChanged(j);
            }
            //解决倒叙不刷新
            if (vodInfo.playIndex != position) {
                seriesAdapter.getData().get(position).selected = true;
                seriesAdapter.notifyItemChanged(position);
                vodInfo.playIndex = position;

                reload = true;
            }
            //解决当前集不刷新的BUG
            if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                reload = true;
            }

            seriesAdapter.getData().get(vodInfo.playIndex).selected = true;
            seriesAdapter.notifyItemChanged(vodInfo.playIndex);

            //选集全屏 想选集不全屏的注释下面一行
            if (!showPreview || reload || reloadWithChangeLine) {
                jumpToPlay();
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            Bundle bundle = new Bundle();
            //保存历史
            insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (previewVodInfo == null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(vodInfo);
                    oos.flush();
                    oos.close();
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                    previewVodInfo = (VodInfo) ois.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (previewVodInfo != null) {
                previewVodInfo.playerCfg = vodInfo.playerCfg;
                previewVodInfo.playFlag = vodInfo.playFlag;
                previewVodInfo.playIndex = vodInfo.playIndex;
                previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                App.getInstance().setVodInfo(previewVodInfo);
            }
            playFragment.setData(bundle);

            //定位选集
            mBinding.mGridView.scrollToPosition(vodInfo.playIndex);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {
        int seriesSize = vodInfo.seriesMap.get(vodInfo.playFlag).size();
        if (seriesSize > 0 && seriesSize <= vodInfo.playIndex) {//当前集数大于新选线路的总集数,设置为最后一集
            vodInfo.playIndex = seriesSize - 1;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            boolean canSelect = true;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                if (vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected) {
                    canSelect = false;
                    break;
                }
            }
            if (canSelect)
                vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
        }
        seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));

    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    mVideo = absXml.movie.videoList.get(0);
                    vodInfo = new VodInfo();
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;

                    mBinding.tvName.setText(TextUtils.isEmpty(mVideo.name) ? "暂无信息" : mVideo.name);
                    String srcName = ApiConfig.get().getSource(mVideo.sourceKey).getName();
                    mBinding.tvSite.setText("来源：" + (TextUtils.isEmpty(srcName) ? "未知" : srcName));

                    if (vodInfo.seriesMap != null && vodInfo.seriesMap.size() > 0) {//线路
                        mBinding.mGridViewFlag.setVisibility(View.VISIBLE);
                        mBinding.mGridView.setVisibility(View.VISIBLE);
                        mBinding.mEmptyPlaylist.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
                        // 读取历史记录
                        if (vodInfoRecord != null) {
                            vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                            vodInfo.playFlag = vodInfoRecord.playFlag;
                            vodInfo.playerCfg = vodInfoRecord.playerCfg;
                            vodInfo.reverseSort = vodInfoRecord.reverseSort;
                        } else {
                            vodInfo.playIndex = 0;
                            vodInfo.playFlag = null;
                            vodInfo.playerCfg = "";
                            vodInfo.reverseSort = false;
                        }

                        if (vodInfo.reverseSort) {
                            vodInfo.reverse();
                        }

                        if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                            vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

                        int flagScrollTo = 0;
                        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
                            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
                            if (flag.name.equals(vodInfo.playFlag)) {
                                flagScrollTo = j;
                                flag.selected = true;
                            } else
                                flag.selected = false;
                        }
//                        setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(0).url);
                        //设置线路数据
                        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
                        mBinding.mGridViewFlag.scrollToPosition(flagScrollTo);

                        refreshList();
                        if (showPreview) {
                            jumpToPlay();
                            mBinding.previewPlayer.setVisibility(View.VISIBLE);
                            toggleSubtitleTextSize();
                        }
                        // startQuickSearch();
                    } else {//空布局
                        mBinding.mGridViewFlag.setVisibility(View.GONE);
                        mBinding.mGridView.setVisibility(View.GONE);
                        mBinding.mEmptyPlaylist.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty();
                    mBinding.previewPlayer.setVisibility(View.GONE);
                }
            }
        });
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            showLoading();
            sourceViewModel.getDetail(sourceKey, vodId);
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            if (isVodCollect) {
                mBinding.tvCollect.setText("取消收藏");
            } else {
                mBinding.tvCollect.setText("加入收藏");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    seriesAdapter.getData().get(index).selected = true;
                    seriesAdapter.notifyItemChanged(index);
                    //mBinding.mGridView.setSelection(index);
                    vodInfo.playIndex = index;
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                } else if (event.obj instanceof JSONObject) {
                    vodInfo.playerCfg = ((JSONObject) event.obj).toString();
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                }

            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                loadDetail(video.id, video.sourceKey);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        searchTitle = mVideo.name;
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        // 分词
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        try {
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {//无痕浏览
            return;
        }
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }

    @Override
    protected void onDestroy() {
        registerActionReceiver(false);
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        // 注销广播接收器
        if (mHomeKeyReceiver != null) {
            unregisterReceiver(mHomeKeyReceiver);
            mHomeKeyReceiver = null;
        }

        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("quick_search");
        toggleScreenShotListen(false);
    }

    @Override
    public void onBackPressed() {
        if (mAllSeriesRightDialog != null && mAllSeriesRightDialog.isShow()) {
            mAllSeriesRightDialog.dismiss();
            return;
        }
        if (mAllSeriesBottomDialog != null && mAllSeriesBottomDialog.isShow()) {
            mAllSeriesBottomDialog.dismiss();
            return;
        }
        if (playFragment.hideAllDialogSuccess()) {//fragment有弹窗隐藏并拦截返回
            return;
        }
        if (fullWindows) {
            toggleFullPreview();
            mBinding.mGridView.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);
    ; // true 开启 false 关闭
    boolean fullWindows = false;
    ViewGroup.LayoutParams windowsPreview = null;
    ViewGroup.LayoutParams windowsFull = null;

    public void toggleFullPreview() {
        if (windowsPreview == null) {
            windowsPreview = mBinding.previewPlayer.getLayoutParams();
        }
        if (windowsFull == null) {//全屏尺寸
            windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        fullWindows = !fullWindows;

        //交由fragment处理播放器全屏逻辑
        playFragment.changedLandscape(fullWindows);
        //activity处理预览尺寸(全屏/非全屏预览)
        mBinding.previewPlayer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
        mBinding.mGridView.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mBinding.mGridViewFlag.setVisibility(fullWindows ? View.GONE : View.VISIBLE);

        //全屏下禁用详情页几个按键的焦点 防止上键跑过来
        mBinding.tvSort.setFocusable(!fullWindows);
        mBinding.tvCollect.setFocusable(!fullWindows);
        toggleSubtitleTextSize();
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    public void use1DMDownload() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            String url = TextUtils.isEmpty(playFragment.getFinalUrl()) ? vod.url : playFragment.getFinalUrl();
            // 创建Intent对象，启动1DM App
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setDataAndType(Uri.parse(url), "video/mp4");
            intent.putExtra("title", vodInfo.name + " " + vod.name); // 传入文件保存名
//            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.MainActivity");
            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查1DM App是否已安装
            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            boolean isIntentSafe = activities.size() > 0;

            if (isIntentSafe) {
                startActivity(intent); // 启动1DM App
            } else {
                // 如果1DM App未安装，提示用户安装1DM App
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("请先安装1DM+下载管理器");
                builder.setMessage("为了下载视频，请先安装1DM+下载管理器。是否现在安装？");
                builder.setPositiveButton("立即下载", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        // 跳转到下载链接
                        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://od.lk/d/MzRfMTg0NTcxMDdf/1DM _v15.6.apk"));
                        startActivity(downloadIntent);
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        } else {
            ToastUtils.showShort("资源异常,请稍后重试");
        }
    }

    /**
     * 画中画模式
     */
    public void enterPip() {
        if (Utils.supportsPiPMode()) {
            // 创建一个Intent对象，模拟按下Home键
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);

            // Calculate Video Resolution
            int vWidth = playFragment.getPlayer().getVideoSize()[0];
            int vHeight = playFragment.getPlayer().getVideoSize()[1];
            Rational ratio;
            if (vWidth != 0) {
                if ((((double) vWidth) / ((double) vHeight)) > 2.39) {
                    vHeight = (int) (((double) vWidth) / 2.35);
                }
                ratio = new Rational(vWidth, vHeight);
            } else {
                ratio = new Rational(16, 9);
            }
            List<RemoteAction> actions = new ArrayList<>();
            actions.add(generateRemoteAction(android.R.drawable.ic_media_previous, IntentKey.BROADCAST_ACTION_PREV, "Prev", "Play Previous"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_play, IntentKey.BROADCAST_ACTION_PLAYPAUSE, "Play", "Play/Pause"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_next, IntentKey.BROADCAST_ACTION_NEXT, "Next", "Play Next"));
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .setActions(actions).build();
            playFragment.getPlayer().postDelayed(() -> {//代码模拟home键时会立即执行,toggleFullPreview中竖屏有切换横屏操作,
                if (!fullWindows) {
                    toggleFullPreview();
                }
            }, 300);
            enterPictureInPictureMode(params);
            playFragment.getController().hideBottom();

            playFragment.getPlayer().postDelayed(() -> {
                if (!playFragment.getPlayer().isPlaying()) {
                    playFragment.getController().togglePlay();
                }
            }, 400);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction generateRemoteAction(int iconResId, int actionCode, String title, String desc) {
        final PendingIntent intent =
                PendingIntent.getBroadcast(
                        DetailActivity.this,
                        actionCode,
                        new Intent(IntentKey.BROADCAST_ACTION).putExtra("action", actionCode),
                        0);
        final Icon icon = Icon.createWithResource(DetailActivity.this, iconResId);
        return (new RemoteAction(icon, title, desc, intent));
    }

    /**
     * 事件接收广播(画中画/后台播放点击事件)
     * @param isRegister 注册/注销
     */
    private void registerActionReceiver(boolean isRegister) {
        if (isRegister) {
            mRemoteActionReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !intent.getAction().equals(IntentKey.BROADCAST_ACTION) || playFragment.getController() == null) {
                        return;
                    }

                    int currentStatus = intent.getIntExtra("action", 1);
                    if (currentStatus == IntentKey.BROADCAST_ACTION_PREV) {
                        playFragment.playPrevious();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_PLAYPAUSE) {
                        playFragment.getController().togglePlay();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_NEXT) {
                        playFragment.playNext(false);
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_CLOSE) {
                        playServerSwitch(false);
                        finish();
                        NotificationUtils.cancelAll();
                    }
                }
            };
            registerReceiver(mRemoteActionReceiver, new IntentFilter(IntentKey.BROADCAST_ACTION));
        } else {
            if (mRemoteActionReceiver != null) {
                unregisterReceiver(mRemoteActionReceiver);
                mRemoteActionReceiver = null;
            }
            if (playFragment.getPlayer().isPlaying()) {// 退出画中画时,暂停播放(画中画的全屏也会触发,但全屏后会自动播放)
                playFragment.getController().togglePlay();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        registerActionReceiver(Utils.supportsPiPMode() && isInPictureInPictureMode);
    }

    /**
     * 后台播放服务开关,开启时注册操作广播,关闭时注销
     */
    private void playServerSwitch(boolean open) {
        if (open) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            PlayService.start(playFragment.getPlayer(), vodInfo.name + "&&" + vod.name);
            registerActionReceiver(true);
        } else {
            if (ServiceUtils.isServiceRunning(PlayService.class)) {
                PlayService.stop();
                registerActionReceiver(false);
            }
        }
    }

    public String getCurrentVodUrl() {
        return playFragment == null ? "" : playFragment.getFinalUrl();
    }

    public void quickLineChange() {
        List<VodInfo.VodSeriesFlag> flags = seriesFlagAdapter.getData();
        if (flags.size() > 1) {
            int currentIndex = 0;
            for (int i = 0; i < flags.size(); i++) {
                if (flags.get(i).selected) {
                    currentIndex = i;
                }
            }
            currentIndex += 1;
            if (currentIndex >= flags.size()) {
                currentIndex = 0;
            }
            mBinding.mGridViewFlag.smoothScrollToPosition(currentIndex);
            chooseFlag(currentIndex);
            mBinding.mGridView.postDelayed(() -> chooseSeries(vodInfo.playIndex, true), 300);
        }
    }

    public void showParseRoot(boolean show, ParseAdapter adapter) {
        mBinding.rvParse.setAdapter(adapter);
        int defaultIndex = 0;
        for (int i = 0; i < adapter.getData().size(); i++) {
            if (adapter.getData().get(i).isDefault()) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex != 0) {
            mBinding.rvParse.scrollToPosition(defaultIndex);
        }
        mBinding.parseRoot.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleScreenShotListen(boolean open) {
        if (open){
            if (screenShotListenManager == null){
                screenShotListenManager = ScreenShotListenManager.newInstance(this);
            }
            screenShotListenManager.setListener(imagePath -> {

                if (playFragment.getPlayer().isInPlaybackState())return;

                new XPopup.Builder(this)
                        .isDarkTheme(Utils.isDarkTheme())
                        .asCenterList("",new String[]{"跳转阿狸","跳转优汐","跳转夸父","关闭"}, null, (position, text) -> {
                            String pkg = "";
                            String cls = "";
                            switch (position){
                                case 0:
                                    pkg = "com.alicloud.databox";
                                    cls = "com.alicloud.databox.launcher.splash.SplashActivity";
                                    break;
                                case 1:
                                    pkg = "com.UCMobile";
                                    cls = "com.uc.browser.InnerUCMobile";
                                    break;
                                case 2:
                                    pkg = "com.quark.browser";
                                    cls = "com.ucpro.MainActivity";
                                    break;
                                case 3:
                                    return;
                            }
                            try {
                                startActivity(new Intent().setComponent(new ComponentName(pkg, cls)));
                            }catch (Exception e){
                                ToastUtils.showShort("未找到应用");
                            }
                        })
                        .show();
            });
            screenShotListenManager.startListen();
        }else {
            if (screenShotListenManager != null) {
                screenShotListenManager.stopListen();
            }
        }
    }
}
