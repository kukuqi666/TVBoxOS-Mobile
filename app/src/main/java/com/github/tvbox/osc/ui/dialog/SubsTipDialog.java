package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ScreenUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogSubsTipBinding;
import com.lxj.xpopup.core.BottomPopupView;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/9/5 14:11.
 * @Description :
 */
public class SubsTipDialog extends BottomPopupView {

    public SubsTipDialog(@NonNull Context context) {
        super(context);
    }


    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_subs_tip;
    }

    @Override
    protected int getMaxHeight() {
        return ScreenUtils.getScreenHeight()-100;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        DialogSubsTipBinding binding = DialogSubsTipBinding.bind(getPopupImplView());
        binding.btnCancel.setOnClickListener(view -> {
            dismiss();
        });
    }
}