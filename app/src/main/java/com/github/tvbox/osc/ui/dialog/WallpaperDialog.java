package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.lxj.xpopup.core.CenterPopupView;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WallpaperDialog extends CenterPopupView {

    private static class WallpaperItem {
        String name;
        String type; // "drawable", "url"
        String value; // resource name or URL
        int resId;

        WallpaperItem(String name, String type, String value, int resId) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.resId = resId;
        }
    }

    private List<WallpaperItem> mWallpapers = new ArrayList<>();

    public WallpaperDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_wallpaper;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        buildWallpaperList();

        RecyclerView rv = findViewById(R.id.rvWallpaper);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 3));

        BaseQuickAdapter<WallpaperItem, BaseViewHolder> adapter =
                new BaseQuickAdapter<WallpaperItem, BaseViewHolder>(R.layout.item_wallpaper) {
            @Override
            protected void convert(BaseViewHolder holder, WallpaperItem item) {
                ImageView iv = holder.getView(R.id.ivWallpaper);
                holder.setText(R.id.tvWallpaperName, item.name);

                if (item.resId != 0) {
                    iv.setImageResource(item.resId);
                } else {
                    iv.setImageResource(R.drawable.wallpaper_gradient_blue);
                    OkGo.<File>get(item.value)
                            .tag(this)
                            .execute(new FileCallback() {
                                @Override
                                public void onSuccess(Response<File> response) {
                                    iv.setImageBitmap(
                                            android.graphics.BitmapFactory.decodeFile(
                                                    response.body().getAbsolutePath()));
                                }
                                @Override
                                public void onError(Response<File> response) {
                                    // keep placeholder
                                }
                            });
                }
            }
        };

        adapter.setOnItemClickListener((adp, view, position) -> {
            WallpaperItem selected = mWallpapers.get(position);
            if ("drawable".equals(selected.type)) {
                Hawk.put(HawkConfig.WALLPAPER_URL, "drawable://" + selected.value);
            } else {
                Hawk.put(HawkConfig.WALLPAPER_URL, selected.value);
            }
            ToastUtils.showShort("壁纸已设置为: " + selected.name);
            applyWallpaperToActivity();
            dismiss();
        });

        rv.setAdapter(adapter);
        adapter.setNewData(mWallpapers);
    }

    private void buildWallpaperList() {
        mWallpapers.clear();
        mWallpapers.add(new WallpaperItem("深邃蓝", "drawable",
                "wallpaper_gradient_blue", R.drawable.wallpaper_gradient_blue));
        mWallpapers.add(new WallpaperItem("极夜紫", "drawable",
                "wallpaper_gradient_night", R.drawable.wallpaper_gradient_night));
        mWallpapers.add(new WallpaperItem("日落橙", "drawable",
                "wallpaper_gradient_sunset", R.drawable.wallpaper_gradient_sunset));
        mWallpapers.add(new WallpaperItem("森林绿", "drawable",
                "wallpaper_gradient_forest", R.drawable.wallpaper_gradient_forest));
        mWallpapers.add(new WallpaperItem("海洋蓝", "drawable",
                "wallpaper_gradient_ocean", R.drawable.wallpaper_gradient_ocean));
        mWallpapers.add(new WallpaperItem("梅子紫", "drawable",
                "wallpaper_gradient_plum", R.drawable.wallpaper_gradient_plum));

        String configWallpaper = ApiConfig.get().wallpaper;
        if (configWallpaper != null && !configWallpaper.isEmpty()) {
            String[] urls = configWallpaper.split(",");
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (url.startsWith("http")) {
                    mWallpapers.add(new WallpaperItem("订阅壁纸" + (i + 1), "url", url, 0));
                }
            }
        }

        mWallpapers.add(new WallpaperItem("随机渐变(在线)", "url",
                "https://jianbian.chuqiuyu.workers.dev", 0));
        mWallpapers.add(new WallpaperItem("随机风景(在线)", "url",
                "https://picsum.photos/1280/720/?blur=10", 0));
        mWallpapers.add(new WallpaperItem("随机动漫(在线)", "url",
                "https://www.dmoe.cc/random.php", 0));
    }

    private void applyWallpaperToActivity() {
        if (getContext() instanceof Activity) {
            View root = ((Activity) getContext()).findViewById(android.R.id.content);
            if (root != null) applyCurrentWallpaper(root);
        }
    }

    public static void applyCurrentWallpaper(View rootView) {
        String wallpaper = Hawk.get(HawkConfig.WALLPAPER_URL, "");
        if (wallpaper.isEmpty()) {
            rootView.setBackgroundResource(0);
            return;
        }
        if (wallpaper.startsWith("drawable://")) {
            String resName = wallpaper.substring("drawable://".length());
            int resId = rootView.getResources().getIdentifier(
                    resName, "drawable", rootView.getContext().getPackageName());
            if (resId != 0) {
                rootView.setBackgroundResource(resId);
            }
        } else if (wallpaper.startsWith("http")) {
            OkGo.<File>get(wallpaper)
                    .execute(new FileCallback() {
                        @Override
                        public void onSuccess(Response<File> response) {
                            rootView.setBackground(new android.graphics.drawable.BitmapDrawable(
                                    rootView.getResources(),
                                    android.graphics.BitmapFactory.decodeFile(
                                            response.body().getAbsolutePath())));
                        }
                        @Override
                        public void onError(Response<File> response) {}
                    });
        }
    }
}
