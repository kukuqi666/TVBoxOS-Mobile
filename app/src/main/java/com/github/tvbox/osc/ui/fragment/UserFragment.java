package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LiveActivity;

import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.UA;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class UserFragment extends BaseLazyFragment {

    private GridAdapter homeHotVodAdapter;
    private List<Movie.Video> homeSourceRec;
    RecyclerView tvHotList1;

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();

        tvHotList1.setHasFixedSize(true);
        tvHotList1.setLayoutManager(new GridLayoutManager(this.mContext, 3));
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    protected void init() {
        tvHotList1 = findViewById(R.id.tvHotList1);
        findViewById(R.id.btn_live).setOnClickListener(view -> jumpActivity(LiveActivity.class));
        homeHotVodAdapter = new GridAdapter();
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()){
                    ToastUtils.showShort("暂无订阅");
                    return;
                }
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                Bundle bundle = new Bundle();
                if (!TextUtils.isEmpty(vod.id)) {
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                } else {
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
            }
        });

        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()) return true;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                Bundle bundle = new Bundle();
                bundle.putString("title", vod.name);
                jumpActivity(FastSearchActivity.class, bundle);
                return true;
            }
        });

        tvHotList1.setAdapter(homeHotVodAdapter);
        setLoadSir2(tvHotList1);
        initHomeHotVod(homeHotVodAdapter);
    }

    private void initHomeHotVod(GridAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
            if (homeSourceRec != null && homeSourceRec.size() > 0) {
                showSuccess();
                adapter.setNewData(homeSourceRec);
            }else {
                showEmpty();
            }
            return;
        }
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        showSuccess();
                        adapter.setNewData(hotMovies);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ArrayList<Movie.Video> videos = loadHots(netJson);
                                    if (videos.size()>0){
                                        showSuccess();
                                        adapter.setNewData(videos);
                                    }else {
                                        showEmpty();
                                    }
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
            if (adapter.getData().isEmpty()){
                showEmpty();
            }
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if (!vod.note.isEmpty()) vod.note += " 分";
                vod.pic = obj.get("cover").getAsString() + "@Referer=https://movie.douban.com/@User-Agent=" + UA.random();
                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }
}