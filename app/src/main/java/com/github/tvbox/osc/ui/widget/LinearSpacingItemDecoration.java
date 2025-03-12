package com.github.tvbox.osc.ui.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.owen.tvrecyclerview.widget.GridLayoutManager;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/9/14 15:31.
 * @Description :
 */
public class LinearSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spacing;
    private final boolean isSpacingBeforeFirstItem;

    public LinearSpacingItemDecoration(int spacing, boolean isSpacingBeforeFirstItem) {
        this.spacing = spacing;
        this.isSpacingBeforeFirstItem = isSpacingBeforeFirstItem;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == 0 && isSpacingBeforeFirstItem) {
            if (parent.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) parent.getLayoutManager();
                if (layoutManager.getOrientation() == LinearLayoutManager.VERTICAL) {
                    outRect.top = spacing;
                } else {
                    outRect.left = spacing;
                }
            } else if (parent.getLayoutManager() instanceof GridLayoutManager) {
                outRect.top = spacing;
                outRect.left = spacing;
            }
        }
        if (parent.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) parent.getLayoutManager();
            if (layoutManager.getOrientation() == LinearLayoutManager.VERTICAL) {
                outRect.bottom = spacing;
            } else {
                outRect.right = spacing;
            }
        } else if (parent.getLayoutManager() instanceof GridLayoutManager) {
            outRect.bottom = spacing;
            outRect.right = spacing;
        }
    }
}