package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ColorUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.widget.GridSpacingItemDecoration;
import com.github.tvbox.osc.util.Utils;
import com.lxj.xpopup.core.DrawerPopupView;
import com.lxj.xpopup.interfaces.OnSelectListener;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/10/25 10:43.
 * @Description : 本地视频全集弹窗
 */
public class AllLocalSeriesDialog extends DrawerPopupView {

    List<VodInfo.VodSeries> mList;
    private final OnSelectListener mSelectListener;

    public AllLocalSeriesDialog(@NonNull @NotNull Context context, List<VodInfo.VodSeries> list, OnSelectListener selectListener) {
        super(context);
        mList = list;
        mSelectListener = selectListener;
    }

    @Override
    protected int getImplLayoutId() {//复用点播全集底部弹窗ui
        return R.layout.dialog_all_series;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        View bg = findViewById(R.id.bg);
        bg.setBackgroundColor(ColorUtils.getColor(R.color.bg_popup));
        findViewById(R.id.v_gesture_line).setVisibility(GONE);
        RecyclerView rv = findViewById(R.id.rv);

        rv.setLayoutManager(new GridLayoutManager(getContext(), Utils.getSeriesSpanCount(mList)));
        rv.addItemDecoration(new GridSpacingItemDecoration(Utils.getSeriesSpanCount(mList), 20, true));


        SeriesAdapter seriesAdapter = new SeriesAdapter(true);
        seriesAdapter.setNewData(mList);
        rv.setAdapter(seriesAdapter);

        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).selected){
                rv.scrollToPosition(i);
            }
        }

        seriesAdapter.setOnItemClickListener((adapter, view, position) -> {
            for (int j = 0; j < seriesAdapter.getData().size(); j++) {
                seriesAdapter.getData().get(j).selected = false;
                seriesAdapter.notifyItemChanged(j);
            }
            seriesAdapter.getData().get(position).selected = true;
            seriesAdapter.notifyItemChanged(position);
            mSelectListener.onSelect(position,"");
        });
    }
}