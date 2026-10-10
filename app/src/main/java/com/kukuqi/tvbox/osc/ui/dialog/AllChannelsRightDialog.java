package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.databinding.DialogAllChannelBinding;
import com.kukuqi.tvbox.osc.ui.activity.LiveActivity;
import com.kukuqi.tvbox.osc.util.LiveChannelBrowser;
import com.kukuqi.tvbox.osc.util.LiveFavorites;
import com.lxj.xpopup.core.DrawerPopupView;
import java.util.ArrayList;
import java.util.List;

public class AllChannelsRightDialog extends DrawerPopupView {
    private final LiveActivity activity;
    private DialogAllChannelBinding binding;
    private BaseQuickAdapter<LiveChannelBrowser.Channel, BaseViewHolder> results;
    private boolean favoritesOnly;

    public AllChannelsRightDialog(@NonNull Context context) {
        super(context); activity = (LiveActivity) context;
    }
    @Override protected int getImplLayoutId() { return R.layout.dialog_all_channel; }
    @Override protected void onCreate() {
        super.onCreate();
        binding = DialogAllChannelBinding.bind(getPopupImplView());
        binding.mGroupGridView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.mChannelGridView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.mGroupGridView.setAdapter(activity.liveChannelGroupAdapter);
        binding.mChannelGridView.setAdapter(activity.liveChannelItemAdapter);
        binding.channelResults.setLayoutManager(new LinearLayoutManager(getContext()));
        results = new BaseQuickAdapter<LiveChannelBrowser.Channel, BaseViewHolder>(R.layout.item_live_channel_result, new ArrayList<>()) {
            @Override protected void convert(BaseViewHolder holder, LiveChannelBrowser.Channel channel) {
                boolean playing = activity.getCurrentLiveChannelItem() == channel.item;
                holder.setText(R.id.result_name, channel.item.getChannelName());
                holder.setText(R.id.result_group, channel.groupName + " · " + channel.item.getChannelNum() + (playing ? " · 正在播放" : ""));
                TextView star = holder.getView(R.id.result_favorite);
                boolean saved = LiveFavorites.contains(channel.item.getChannelName());
                star.setText(saved ? "★" : "☆");
                star.setContentDescription(saved ? "取消收藏" : "收藏频道");
                star.setOnClickListener(v -> { activity.toggleChannelFavorite(channel.item); refresh(); });
                holder.itemView.setBackgroundResource(playing ? R.drawable.bg_r_common_stroke_primary : R.drawable.bg_transparent);
            }
        };
        binding.channelResults.setAdapter(results);
        results.setOnItemClickListener((adapter, view, position) -> {
            LiveChannelBrowser.Channel channel = results.getItem(position);
            if (channel != null) activity.playBrowserChannel(channel);
            close();
        });
        results.setOnItemLongClickListener((adapter, view, position) -> {
            LiveChannelBrowser.Channel channel = results.getItem(position);
            if (channel != null) activity.toggleChannelFavorite(channel.item);
            refresh(); return true;
        });
        binding.channelsAll.setOnClickListener(v -> { favoritesOnly = false; refresh(); });
        binding.channelsFavorites.setOnClickListener(v -> { favoritesOnly = true; refresh(); });
        binding.channelsClose.setOnClickListener(v -> close());
        binding.channelClear.setOnClickListener(v -> binding.channelQuery.setText(""));
        binding.channelQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        binding.channelQuery.setOnEditorActionListener((v, action, event) -> { hideKeyboard(); refresh(); return true; });
        refresh();
    }

    public void refresh() {
        if (binding == null) return;
        String query = binding.channelQuery.getText().toString().trim();
        boolean filtering = favoritesOnly || !query.isEmpty();
        binding.channelsAll.setTextColor(getResources().getColor(favoritesOnly ? R.color.text_sub_foreground : R.color.colorPrimary));
        binding.channelsFavorites.setTextColor(getResources().getColor(favoritesOnly ? R.color.colorPrimary : R.color.text_sub_foreground));
        binding.channelClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        binding.channelGroups.setVisibility(filtering ? View.GONE : View.VISIBLE);
        List<LiveChannelBrowser.Channel> channels = activity.searchLiveChannels(query, favoritesOnly);
        results.setNewData(channels);
        binding.channelResults.setVisibility(filtering && !channels.isEmpty() ? View.VISIBLE : View.GONE);
        binding.channelEmpty.setVisibility(filtering && channels.isEmpty() ? View.VISIBLE : View.GONE);
        binding.channelEmpty.setText(favoritesOnly && query.isEmpty() ? "还没有收藏可用频道\n长按频道即可收藏" : "没有匹配的频道");
        binding.channelHint.setText(filtering ? channels.size() + " 个频道 · 点击星标收藏" : "长按频道收藏，再次长按取消");
    }
    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(binding.channelQuery.getWindowToken(), 0);
        binding.channelQuery.clearFocus();
    }
    private void close() { hideKeyboard(); dismiss(); }
}
