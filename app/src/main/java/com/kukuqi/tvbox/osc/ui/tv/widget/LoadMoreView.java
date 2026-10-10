package com.kukuqi.tvbox.osc.ui.tv.widget;


import com.kukuqi.tvbox.osc.R;

public final class LoadMoreView extends com.chad.library.adapter.base.loadmore.LoadMoreView {

    @Override
    public int getLayoutId() {
        return R.layout.item_view_load_more;
    }

    @Override
    protected int getLoadingViewId() {
        return R.id.load_more_loading_view;
    }

    @Override
    protected int getLoadFailViewId() {
        return R.id.load_more_load_fail_view;
    }

    @Override
    protected int getLoadEndViewId() {
        return R.id.load_more_load_end_view;
    }
}
