package com.github.tvbox.osc.ui.adapter;

import com.android.cast.dlna.dmc.OnDeviceRegistryListener;
import com.blankj.utilcode.util.LogUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;

import org.fourthline.cling.model.meta.Device;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class CastDevicesAdapter extends BaseQuickAdapter<Device, BaseViewHolder> implements OnDeviceRegistryListener {

    public CastDevicesAdapter() {
        super(R.layout.item_title);
    }

    @Override
    protected void convert(BaseViewHolder helper, Device item) {
        helper.setText(R.id.title,item.getDetails().getFriendlyName());
    }

    @Override
    public void onDeviceAdded(Device<?, ?, ?> device) {
        addData(device);
    }

    @Override
    public void onDeviceUpdated(Device<?, ?, ?> device) {

    }

    @Override
    public void onDeviceRemoved(Device<?, ?, ?> device) {
        List<Device> data = getData();
        if (data.contains(device)) {
            data.remove(device);
            notifyDataSetChanged();
        }
    }
}