package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogInputSubsriptionBinding;
import com.github.tvbox.osc.databinding.DialogLiveApiBinding;
import com.github.tvbox.osc.util.HawkConfig;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

public class LiveApiDialog extends CenterPopupView {

    private com.github.tvbox.osc.databinding.DialogLiveApiBinding mBinding;

    public LiveApiDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_live_api;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mBinding = DialogLiveApiBinding.bind(getPopupImplView());
        String liveApi = Hawk.get(HawkConfig.LIVE_URL, "");
        updateEt(liveApi);

        mBinding.ivHistory.setOnClickListener(view -> {
            ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
            if (liveHistory.isEmpty()){
                ToastUtils.showShort("暂无历史记录");
                return;
            }
            new XPopup.Builder(getContext())
                    .asCustom(new ApiHistoryDialog(getContext(),liveApi, this::updateEt))
                    .show();
        });

        mBinding.btnCancel.setOnClickListener(v -> dismiss());
        mBinding.btnConfirm.setOnClickListener(view -> {
            String newLive = mBinding.etUrl.getText().toString().trim();
            // Capture Live input into Settings & Live History (max 20)
            Hawk.put(HawkConfig.LIVE_URL, newLive);
            if (!newLive.isEmpty()) {
                ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<String>());
                if (!liveHistory.contains(newLive))
                    liveHistory.add(0, newLive);
                if (liveHistory.size() > 20)
                    liveHistory.remove(20);
                Hawk.put(HawkConfig.LIVE_HISTORY, liveHistory);
            }
            ToastUtils.showShort("设置成功");
            dismiss();
        });
    }

    private void updateEt(String text){
        mBinding.etUrl.setText(text);
        mBinding.etUrl.setSelection(text.length());
    }
}