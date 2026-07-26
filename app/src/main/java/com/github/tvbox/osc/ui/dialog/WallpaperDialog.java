package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.WallpaperManager;
import com.lxj.xpopup.core.BottomPopupView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WallpaperDialog extends BottomPopupView {

    private ViewPager2 viewPager;
    private TextView tvTabBuiltin, tvTabSub, tvTabOnline;
    private ImageView ivPreview;
    private List<WallpaperManager.WallpaperItem> builtinList, subList, onlineList;
    private WallpaperManager.WallpaperItem selectedItem;

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

        ivPreview = findViewById(R.id.iv_wp_preview);
        tvTabBuiltin = findViewById(R.id.tv_tab_builtin);
        tvTabSub = findViewById(R.id.tv_tab_sub);
        tvTabOnline = findViewById(R.id.tv_tab_online);
        viewPager = findViewById(R.id.vp_wallpaper);
        findViewById(R.id.btn_wp_apply).setOnClickListener(v -> applyAndDismiss());
        findViewById(R.id.btn_wp_close).setOnClickListener(v -> dismiss());

        WallpaperManager mgr = WallpaperManager.get();
        builtinList = mgr.getBuiltInWallpapers();
        subList = mgr.getSubscriptionWallpapers();
        onlineList = mgr.getOnlineWallpapers();

        viewPager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                RecyclerView rv = new RecyclerView(parent.getContext());
                rv.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.MATCH_PARENT));
                return new Holder(rv);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
                RecyclerView rv = (RecyclerView) holder.itemView;
                rv.setLayoutManager(new GridLayoutManager(getContext(), 4));
                switch (pos) {
                    case 0: rv.setAdapter(buildAdapter(builtinList)); break;
                    case 1: rv.setAdapter(subList.isEmpty()
                            ? buildEmptyAdapter() : buildAdapter(subList)); break;
                    case 2: rv.setAdapter(onlineList.isEmpty()
                            ? buildEmptyAdapter() : buildAdapter(onlineList)); break;
                }
            }

            @Override
            public int getItemCount() { return 3; }
        });

        tvTabBuiltin.setOnClickListener(v -> selectTab(0));
        tvTabSub.setOnClickListener(v -> selectTab(1));
        tvTabOnline.setOnClickListener(v -> selectTab(2));
        selectTab(0);
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(View v) { super(v); }
    }

    private BaseQuickAdapter<WallpaperManager.WallpaperItem, BaseViewHolder> buildAdapter(
            List<WallpaperManager.WallpaperItem> items) {
        BaseQuickAdapter<WallpaperManager.WallpaperItem, BaseViewHolder> adapter =
                new BaseQuickAdapter<WallpaperManager.WallpaperItem, BaseViewHolder>(
                        R.layout.item_wallpaper) {
            @Override
            protected void convert(BaseViewHolder holder, WallpaperManager.WallpaperItem item) {
                ImageView iv = holder.getView(R.id.ivWallpaper);
                TextView tv = holder.getView(R.id.tvWallpaperName);
                tv.setText(item.name);

                if (item.type == WallpaperManager.WallpaperItem.TYPE_BUILTIN) {
                    if (item.resId != 0) {
                        iv.setImageResource(item.resId);
                    } else {
                        iv.setBackgroundColor(0xFFE0E0E0);
                    }
                } else {
                    File cache = WallpaperManager.get().getCachedFile(item.url);
                    if (cache.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
                        if (bmp != null) iv.setImageBitmap(bmp);
                        else iv.setImageResource(R.drawable.wallpaper_gradient_blue);
                    } else {
                        iv.setImageResource(R.drawable.wallpaper_gradient_blue);
                        downloadThumb(item, iv);
                    }
                }
            }
        };
        adapter.setOnItemClickListener((adp, view, position) -> {
            selectedItem = items.get(position);
            updatePreview(selectedItem);
        });
        adapter.setNewData(items);
        return adapter;
    }

    private BaseQuickAdapter<?, BaseViewHolder> buildEmptyAdapter() {
        return new BaseQuickAdapter<WallpaperManager.WallpaperItem, BaseViewHolder>(
                R.layout.item_wallpaper) {
            @Override
            protected void convert(BaseViewHolder holder, WallpaperManager.WallpaperItem item) {}
        };
    }

    private void downloadThumb(WallpaperManager.WallpaperItem item, ImageView iv) {
        com.lzy.okgo.OkGo.<File>get(item.url)
                .execute(new com.lzy.okgo.callback.FileCallback() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                        Bitmap bmp = BitmapFactory.decodeFile(response.body().getAbsolutePath());
                        if (bmp != null) iv.post(() -> iv.setImageBitmap(bmp));
                    }
                    @Override
                    public void onError(com.lzy.okgo.model.Response<File> response) {}
                });
    }

    private void updatePreview(WallpaperManager.WallpaperItem item) {
        if (item.type == WallpaperManager.WallpaperItem.TYPE_BUILTIN) {
            if (item.resId != 0) {
                ivPreview.setImageResource(item.resId);
            } else {
                ivPreview.setBackgroundColor(0xFFF5F5F5);
            }
        } else {
            File cache = WallpaperManager.get().getCachedFile(item.url);
            if (cache.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) ivPreview.setImageBitmap(bmp);
            } else {
                ivPreview.setImageResource(R.drawable.wallpaper_gradient_blue);
                com.lzy.okgo.OkGo.<File>get(item.url)
                        .execute(new com.lzy.okgo.callback.FileCallback() {
                            @Override
                            public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                                Bitmap bmp = BitmapFactory.decodeFile(response.body().getAbsolutePath());
                                if (bmp != null) ivPreview.post(() -> ivPreview.setImageBitmap(bmp));
                            }
                            @Override
                            public void onError(com.lzy.okgo.model.Response<File> response) {}
                        });
            }
        }
    }

    private void selectTab(int index) {
        resetTabs();
        switch (index) {
            case 1: tvTabSub.setTextColor(0xFF2196F3); break;
            case 2: tvTabOnline.setTextColor(0xFF2196F3); break;
            default: tvTabBuiltin.setTextColor(0xFF2196F3); break;
        }
        viewPager.setCurrentItem(index, false);
    }

    private void resetTabs() {
        tvTabBuiltin.setTextColor(0xFF666666);
        tvTabSub.setTextColor(0xFF666666);
        tvTabOnline.setTextColor(0xFF666666);
    }

    private void applyAndDismiss() {
        if (selectedItem == null) {
            int idx = viewPager.getCurrentItem();
            List<WallpaperManager.WallpaperItem> list;
            switch (idx) {
                case 1: list = subList; break;
                case 2: list = onlineList; break;
                default: list = builtinList; break;
            }
            if (list != null && !list.isEmpty()) selectedItem = list.get(0);
        }
        if (selectedItem != null) {
            WallpaperManager.get().saveWallpaper(selectedItem);
            if (getContext() instanceof Activity) {
                WallpaperManager.get().applyToActivity((Activity) getContext());
            }
        }
        dismiss();
    }
}
