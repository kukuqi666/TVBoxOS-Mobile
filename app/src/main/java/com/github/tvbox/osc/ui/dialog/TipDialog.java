package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.hjq.bar.TitleBar;

import org.jetbrains.annotations.NotNull;

public class TipDialog extends BaseDialog {

    public TipDialog(@NonNull @NotNull Context context, String tip, String left, String right, OnListener listener) {
        super(context);
        setContentView(R.layout.dialog_tip);
        
        TextView tipInfo = findViewById(R.id.tipInfo);
        TextView leftBtn = findViewById(R.id.leftBtn);
        TextView rightBtn = findViewById(R.id.rightBtn);
        TitleBar titleBar = findViewById(R.id.title_bar);
        tipInfo.setText(tip);
        leftBtn.setText(left);
        rightBtn.setText(right);
        leftBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.left();
            }
        });
        rightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.right();
            }
        });
        setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                listener.cancel();
            }
        });
        titleBar.getRightView().setOnClickListener(view -> {
            listener.onTitleClick();
        });
    }

    public interface OnListener {
        void left();

        void right();

        void cancel();
        void onTitleClick();
    }
}
