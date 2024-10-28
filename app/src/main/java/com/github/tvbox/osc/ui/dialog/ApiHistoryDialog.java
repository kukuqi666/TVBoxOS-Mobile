package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogTitleListBinding;
import com.github.tvbox.osc.ui.adapter.TitleWithDelAdapter;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.util.HawkConfig;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/10/26 10:52.
 * @Description :
 */
public class ApiHistoryDialog extends BottomPopupView {
    private final String mPreApi;
    private final OnInputConfirmListener mOnInputConfirmListener;
    private ArrayList<String> mLiveHistory;

    public ApiHistoryDialog(@NonNull Context context, String preApi, OnInputConfirmListener onInputConfirmListener) {
        super(context);
        mPreApi = preApi;
        mOnInputConfirmListener = onInputConfirmListener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_title_list;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        DialogTitleListBinding binding = DialogTitleListBinding.bind(getPopupImplView());
        binding.title.setText("历史直播源");

        binding.ivUseTip.setOnClickListener(view -> {
            new XPopup.Builder(getContext())
                    .asConfirm("使用帮助","订阅的内置直播源会被解析并存到历史记录,即使未使用,最多20条,按需选择!","","知道了",null,null,true)
                    .show();
        });

        binding.rv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rv.addItemDecoration(new LinearSpacingItemDecoration(20, true));
        TitleWithDelAdapter adapter = new TitleWithDelAdapter();
        binding.rv.setAdapter(adapter);

        mLiveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
        mLiveHistory.remove(mPreApi);
        adapter.setNewData(mLiveHistory);
        adapter.setOnItemChildClickListener((adapter1, view, position) -> {
            if (view.getId() == R.id.tvDel) {
                mLiveHistory.remove(position);
                adapter1.notifyDataSetChanged();
                Hawk.put(HawkConfig.LIVE_HISTORY, mLiveHistory);
            }else {
                mOnInputConfirmListener.onConfirm(mLiveHistory.get(position));
                dismiss();
            }
        });
    }

    @Override
    public void onDestroy() {
        mLiveHistory.add(0,mPreApi);
        Hawk.put(HawkConfig.LIVE_HISTORY, mLiveHistory);
        super.onDestroy();
    }
}