package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.LogUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.ui.adapter.CastDevicesAdapter;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.core.CenterPopupView;

import org.fourthline.cling.model.meta.Device;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CastListDialog extends CenterPopupView {

    private final CastVideo castVideo;
    private CastDevicesAdapter adapter;

    public CastListDialog(@NonNull @NotNull Context context, CastVideo castVideo) {
        super(context);
        this.castVideo = castVideo;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_cast;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        DLNACastManager.getInstance().bindCastService(App.getInstance());
        findViewById(R.id.btn_cancel).setOnClickListener(view -> {
            dismiss();
        });
        findViewById(R.id.btn_confirm).setOnClickListener(view ->{
            adapter.setNewData(new ArrayList<>());
            DLNACastManager.getInstance().search(null, 1);
        });
        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CastDevicesAdapter();
        rv.setAdapter(adapter);
        DLNACastManager.getInstance().registerDeviceListener(adapter);

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                Device item = (Device)adapter.getItem(position);
                if (item!=null){
                    DLNACastManager.getInstance().cast(item,castVideo);
                }
            }
        });
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
        DLNACastManager.getInstance().unregisterListener(adapter);
        DLNACastManager.getInstance().unbindCastService(App.getInstance());
    }
}