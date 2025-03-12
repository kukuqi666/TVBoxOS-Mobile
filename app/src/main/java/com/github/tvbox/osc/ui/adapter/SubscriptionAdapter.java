package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.ColorUtils;
import com.blankj.utilcode.util.LogUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Subscription;
import com.github.tvbox.osc.bean.VideoFolder;
import com.github.tvbox.osc.bean.VideoInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SubscriptionAdapter extends BaseQuickAdapter<Subscription, BaseViewHolder> {
    public SubscriptionAdapter() {
        super(R.layout.item_subscription);
    }

    @Override
    protected void convert(BaseViewHolder helper, Subscription item) {
        helper.setText(R.id.tv_name,item.getName())
        .setText(R.id.tv_url,item.getUrl())
        .setChecked(R.id.cb,item.isChecked())
        .setVisible(R.id.iv_pushpin,item.isTop());

        helper.addOnClickListener(R.id.iv_del);
    }

    /**
     * 刷新列表时候,添加去重和排序
     * @param data
     */
    @Override
    public void setNewData(@Nullable List<Subscription> data) {
        if (data!=null){
            //去除url重复的订阅
            for (int i = 0; i < data.size(); i++) {
                for (int j = i+1; j < data.size(); j++) {
                    if (data.get(i).getUrl().equals(data.get(j).getUrl())){
                        data.remove(j);
                        j--;
                    }
                }
            }
            data.sort(mComparator);
        }
        super.setNewData(data);
    }

    Comparator<Subscription> mComparator = (s1, s2) -> {
        if (s1.isTop() && !s2.isTop()) {
            return -1;
        } else if (!s1.isTop() && s2.isTop()) {
            return 1;
        } else if (s1.isChecked() && !s2.isChecked()) {
            return -1;
        } else if (!s1.isChecked() && s2.isChecked()) {
            return 1;
        } else {
            return 0;
        }
    };
}