package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Source;
import com.github.tvbox.osc.bean.VodInfo;
import com.lihang.ShadowLayout;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class SourceAdapter extends BaseQuickAdapter<Source, BaseViewHolder> {
    public SourceAdapter() {
        super(R.layout.item_source, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Source item) {
        helper.setText(R.id.tv_name, item.getSourceName())
                .setText(R.id.tv_url, item.getSourceUrl());
    }
}