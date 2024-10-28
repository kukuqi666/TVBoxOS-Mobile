package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ClipboardUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.databinding.DialogVideoDetailBinding;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.MD5;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.SmartGlideImageLoader;
import com.squareup.picasso.Picasso;

import me.jessyan.autosize.utils.AutoSizeUtils;


public class VideoDetailDialog extends BottomPopupView {


    @NonNull
    private final DetailActivity mActivity;
    private VodInfo mVideo;

    public VideoDetailDialog(@NonNull Context context, VodInfo vodInfo) {
        super(context);
        mActivity = (DetailActivity) context;
        mVideo = vodInfo;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_video_detail;
    }

    @Override
    protected int getMaxHeight() {
        return ScreenUtils.getScreenHeight()-ScreenUtils.getScreenHeight()/10;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        DialogVideoDetailBinding binding = DialogVideoDetailBinding.bind(getPopupImplView());

        binding.tvName.setText(mVideo.name);
        binding.tvYear.setText("年份："+(mVideo.year == 0 ? "" : String.valueOf(mVideo.year)));
        binding.tvArea.setText("地区："+getText(mVideo.area));
        binding.tvType.setText("类型："+getText(mVideo.type));
        binding.tvActor.setText("演员："+getText(mVideo.actor));
        binding.tvDirector.setText("导演："+getText(mVideo.director));
        binding.tvDes.setContent("简介："+removeHtmlTag(mVideo.des));
        binding.url.setText(mActivity.getCurrentVodUrl());
        binding.tvLinkCopy.setOnClickListener(view -> {
            ClipboardUtils.copyText(mActivity.getCurrentVodUrl());
            ToastUtils.showLong("已复制");
        });
        String picUrl = DefaultConfig.checkReplaceProxy(mVideo.pic);
        if (!TextUtils.isEmpty(picUrl)){
            Picasso.get()
                    .load(picUrl)
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(binding.ivThum);

            binding.llThum.setOnClickListener(view -> {
                // 单张图片场景
                new XPopup.Builder(getContext())
                        .asImageViewer(binding.ivThum, picUrl, new SmartGlideImageLoader())
                        .show();
            });
        }
    }

    private String getText(String str){
        if (TextUtils.isEmpty(str)){
            return "未知";
        }else {
            return str;
        }
    }

    private String removeHtmlTag(String info) {
        if (TextUtils.isEmpty(info))
            return "暂无";
        return info.replaceAll("\\<.*?\\>", "").replaceAll("\\s", "");
    }

}