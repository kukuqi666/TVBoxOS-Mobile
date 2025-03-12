package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogInputSubsriptionBinding;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/8/17 09:28.
 * @Description :
 */
public class SubsciptionDialog extends CenterPopupView {

    public interface OnSubsciptionListener {
        void onConfirm(String name,String url,boolean check);
        void chooseLocal(boolean check);
    }

    private final String mDefaultName;
    private OnSubsciptionListener listener;

    public SubsciptionDialog(@NonNull Context context, String defaultName, OnSubsciptionListener listener) {
        super(context);
        mDefaultName = defaultName;
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_input_subsription;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        DialogInputSubsriptionBinding binding = DialogInputSubsriptionBinding.bind(getPopupImplView());
        binding.etName.setText(mDefaultName);
        binding.etName.setSelection(mDefaultName.length());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnConfirm.setOnClickListener(view -> {
            String name = binding.etName.getText().toString().trim();
            if (name.isEmpty()) {
                ToastUtils.showShort("请输入名称");
                return;
            }
            String url = binding.etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                ToastUtils.showShort("请输入订阅地址");
                return;
            }
            if (listener != null) {
                listener.onConfirm(name,url,binding.cbCheck.isChecked());
            }
            dismiss();
        });

        binding.tvLocal.setOnClickListener(view -> {
            dismissWith(() -> listener.chooseLocal(binding.cbCheck.isChecked()));
        });
    }
}