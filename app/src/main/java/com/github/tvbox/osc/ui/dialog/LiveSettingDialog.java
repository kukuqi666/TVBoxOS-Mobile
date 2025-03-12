package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.databinding.DialogLiveSettingBinding;
import com.github.tvbox.osc.ui.activity.LiveActivity;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.widget.GridSpacingItemDecoration;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.Utils;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LiveSettingDialog extends BottomPopupView {


    @NonNull
    private final LiveActivity mActivity;
    private DialogLiveSettingBinding mBinding;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();

    public LiveSettingDialog(@NonNull @NotNull Context context) {
        super(context);
        mActivity = (LiveActivity)context;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_live_setting;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mBinding = DialogLiveSettingBinding.bind(getPopupImplView());
        initSettingGroupView();
        initSettingItemView();
        initLiveSettingGroupList();
        loadCurrentSourceList();
        selectSettingGroup(0);
    }



    private void initSettingGroupView() {
        mBinding.mSettingGroupView.setHasFixedSize(true);
        mBinding.mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mBinding.mSettingGroupView.setAdapter(liveSettingGroupAdapter);

        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position);
            }
        });
    }


    private void initSettingItemView() {
        mBinding.mSettingItemView.setHasFixedSize(true);
        mBinding.mSettingItemView.setLayoutManager(new V7LinearLayoutManager(getContext(), 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mBinding.mSettingItemView.setAdapter(liveSettingItemAdapter);

        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }


    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
        liveSettingGroupAdapter.setNewData(liveSettingGroupList);
    }


    private void selectSettingGroup(int position) {
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1)
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                liveSettingItemAdapter.selectItem(mActivity.getCurrentLiveChannelItem().getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(mActivity.getLivePlayerManager().getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(mActivity.getLivePlayerManager().getLivePlayerType(), true, true);
                break;
        }
        int scrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (scrollToPosition < 0) scrollToPosition = 0;
        mBinding.mSettingItemView.scrollToPosition(scrollToPosition);
    }

    private void loadCurrentSourceList() {
        ArrayList<String> currentSourceNames = mActivity.getCurrentLiveChannelItem().getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }


    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 4) {//渲染选择状态
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                mActivity.switchingLine2Replay(position);
                break;
            case 1://画面比例
                mActivity.changeScale(position);
                break;
            case 2://播放解码
                mActivity.changePlayer(position);
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4://超时换源
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
        }
    }
}