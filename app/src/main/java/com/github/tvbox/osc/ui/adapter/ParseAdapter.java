package com.github.tvbox.osc.ui.adapter;

import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.ParseBean;

import java.util.ArrayList;

public class ParseAdapter extends BaseQuickAdapter<ParseBean, BaseViewHolder> {
    public ParseAdapter() {
        super(R.layout.item_select_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, ParseBean item) {
        View select = helper.getView(R.id.vFlag);
        if (item.isDefault()) {
            select.setVisibility(View.VISIBLE);
        } else {
            select.setVisibility(View.GONE);
        }
        helper.setText(R.id.tvFlag, item.getName());
    }
}