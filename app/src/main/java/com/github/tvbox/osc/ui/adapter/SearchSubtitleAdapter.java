package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Subtitle;

import java.util.ArrayList;

public class SearchSubtitleAdapter extends BaseQuickAdapter<Subtitle, BaseViewHolder> {

    public SearchSubtitleAdapter() {
        super(R.layout.item_search_subtitle_result, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Subtitle item) {
        helper.setText(R.id.subtitleName, item.getName());
        helper.setText(R.id.subtitleNameInfo, item.getIsZip() ? "压缩包" : "文件");
    }
}
