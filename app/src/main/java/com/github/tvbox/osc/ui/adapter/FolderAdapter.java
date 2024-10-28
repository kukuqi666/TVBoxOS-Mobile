package com.github.tvbox.osc.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.blankj.utilcode.util.LogUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.VideoFolder;
import com.github.tvbox.osc.bean.VideoInfo;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.MD5;
import com.squareup.picasso.Callback;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class FolderAdapter extends BaseQuickAdapter<VideoFolder, BaseViewHolder> {
    public FolderAdapter() {
        super(R.layout.item_folder);
    }

    @Override
    protected void convert(BaseViewHolder helper, VideoFolder item) {
        List<VideoInfo> videoList = item.getVideoList();
        helper.setText(R.id.tv_name,item.getName());
        helper.setText(R.id.tv_count,videoList.size()+"个视频");

        Glide.with(mContext)
                .load(videoList.get(0).getPath()) // 第一个视频做封面
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.iv_video)
                .centerCrop()
                .into((ImageView) helper.getView(R.id.iv));
    }
}