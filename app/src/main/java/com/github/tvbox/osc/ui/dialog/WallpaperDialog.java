package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.WallpaperManager;
import com.lxj.xpopup.core.BottomPopupView;

import java.io.File;
import java.util.List;

public class WallpaperDialog extends BottomPopupView {

    public interface OnImportListener {
        void onImportWallpaper();
    }

    private RecyclerView recyclerView;
    private TextView tvTabBuiltin, tvTabSub, tvTabOnline;
    private ImageView ivPreview;
    private List<WallpaperManager.WallpaperItem> builtinList, subList, onlineList;
    private List<WallpaperManager.WallpaperItem> currentList;
    private WallpaperManager.WallpaperItem selectedItem;
    private final OnImportListener importListener;

    public WallpaperDialog(@NonNull Context context) {
        this(context, null);
    }

    public WallpaperDialog(@NonNull Context context, OnImportListener importListener) {
        super(context);
        this.importListener = importListener;
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
        recyclerView = findViewById(R.id.vp_wallpaper);
        findViewById(R.id.btn_wp_apply).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                applyAndDismiss();
            }
        });
        findViewById(R.id.btn_wp_close).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                dismiss();
            }
        });
        findViewById(R.id.btn_wp_import).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                if (importListener != null) {
                    dismissWith(importListener::onImportWallpaper);
                }
            }
        });

        WallpaperManager mgr = WallpaperManager.get();
        builtinList = mgr.getBuiltInWallpapers();
        subList = mgr.getSubscriptionWallpapers();
        onlineList = mgr.getOnlineWallpapers();

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));

        tvTabBuiltin.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                switchTab(0);
            }
        });
        tvTabSub.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                switchTab(1);
            }
        });
        tvTabOnline.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                switchTab(2);
            }
        });

        switchTab(0);
    }

    private void switchTab(int index) {
        tvTabBuiltin.setTextColor(0xFF666666);
        tvTabSub.setTextColor(0xFF666666);
        tvTabOnline.setTextColor(0xFF666666);

        switch (index) {
            case 1:
                tvTabSub.setTextColor(0xFF2196F3);
                currentList = subList;
                break;
            case 2:
                tvTabOnline.setTextColor(0xFF2196F3);
                currentList = onlineList;
                break;
            default:
                tvTabBuiltin.setTextColor(0xFF2196F3);
                currentList = builtinList;
                break;
        }

        selectedItem = null;
        ivPreview.setImageResource(R.drawable.wallpaper_gradient_blue);

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
                } else if (item.url != null && !item.url.isEmpty()) {
                    File cache = WallpaperManager.get().getCachedFile(item.url);
                    if (cache.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        } else {
                            iv.setImageResource(R.drawable.wallpaper_gradient_blue);
                        }
                    } else {
                        iv.setImageResource(R.drawable.wallpaper_gradient_blue);
                        downloadThumb(item.url, iv);
                    }
                }
            }
        };

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adp, android.view.View view, int position) {
                selectedItem = currentList.get(position);
                updatePreview(selectedItem);
            }
        });

        adapter.setNewData(currentList);
        recyclerView.setAdapter(adapter);
    }

    private void downloadThumb(String url, ImageView iv) {
        com.lzy.okgo.OkGo.<File>get(url)
                .execute(new com.lzy.okgo.callback.FileCallback() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                        Bitmap bmp = BitmapFactory.decodeFile(response.body().getAbsolutePath());
                        if (bmp != null) {
                            iv.post(new Runnable() {
                                @Override
                                public void run() {
                                    iv.setImageBitmap(bmp);
                                }
                            });
                        }
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
        } else if (item.url != null && !item.url.isEmpty()) {
            File cache = WallpaperManager.get().getCachedFile(item.url);
            if (cache.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) {
                    ivPreview.setImageBitmap(bmp);
                }
            } else {
                ivPreview.setImageResource(R.drawable.wallpaper_gradient_blue);
                com.lzy.okgo.OkGo.<File>get(item.url)
                        .execute(new com.lzy.okgo.callback.FileCallback() {
                            @Override
                            public void onSuccess(com.lzy.okgo.model.Response<File> response) {
                                Bitmap bmp = BitmapFactory.decodeFile(response.body().getAbsolutePath());
                                if (bmp != null) {
                                    ivPreview.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            ivPreview.setImageBitmap(bmp);
                                        }
                                    });
                                }
                            }
                            @Override
                            public void onError(com.lzy.okgo.model.Response<File> response) {}
                        });
            }
        }
    }

    private void applyAndDismiss() {
        if (selectedItem == null && currentList != null && !currentList.isEmpty()) {
            selectedItem = currentList.get(0);
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
