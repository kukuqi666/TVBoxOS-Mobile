package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.lxj.xpopup.core.PositionPopupView;
import com.lxj.xpopup.enums.DragOrientation;

public class LastViewedDialog extends PositionPopupView {
    private final VodInfo vodInfo;

    public LastViewedDialog(@NonNull Context context, VodInfo vodInfo) {
        super(context);
        this.vodInfo = vodInfo;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_last_viewed;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView textView = findViewById(R.id.tv);
        textView.setText("上次看到: "+vodInfo.name+" "+vodInfo.note);
        textView.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            dismiss();
            Bundle bundle = new Bundle();
            bundle.putString("id", vodInfo.id);
            bundle.putString("sourceKey", vodInfo.sourceKey);
            getContext().startActivity(new Intent(getContext(),DetailActivity.class).putExtras(bundle));
        });
    }

    @Override
    protected DragOrientation getDragOrientation() {
        return DragOrientation.DragToRight;
    }
}