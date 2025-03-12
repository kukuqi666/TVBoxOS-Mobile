package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Source;
import com.github.tvbox.osc.ui.adapter.SourceAdapter;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.interfaces.OnSelectListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/9/7 16:33.
 * @Description :
 */
public class ChooseSourceDialog extends BottomPopupView {
    List<Source> mSources;
    private final OnSelectListener mListener;

    public ChooseSourceDialog(@NonNull Context context, List<Source> sources, OnSelectListener listener) {
        super(context);
        mSources = sources;
        mListener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_sources;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getActivity()));
        SourceAdapter sourceAdapter = new SourceAdapter();
        rv.setAdapter(sourceAdapter);
        sourceAdapter.setNewData(mSources);

        sourceAdapter.setOnItemClickListener((adapter, view, position) -> {
            dismissWith(() -> {
                if (mListener!=null){
                    mListener.onSelect(position, mSources.get(position).getSourceUrl());
                }
            });
        });
    }
}