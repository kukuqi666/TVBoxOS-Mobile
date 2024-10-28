package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class TitleWithDelAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public TitleWithDelAdapter() {
        super(R.layout.item_stroke_button, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvName, item);
        helper.addOnClickListener(R.id.tvName,R.id.tvDel);
    }
}