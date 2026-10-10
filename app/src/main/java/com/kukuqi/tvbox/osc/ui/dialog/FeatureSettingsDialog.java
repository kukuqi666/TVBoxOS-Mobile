package com.kukuqi.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.bean.SourceBean;
import com.kukuqi.tvbox.osc.event.SourceChangedEvent;
import com.kukuqi.tvbox.osc.ui.settings.ThemeColors;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;
import org.greenrobot.eventbus.EventBus;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Functional settings panels shared by My and the standalone settings host. */
public final class FeatureSettingsDialog {
    private final Activity activity;
    private final LinearLayout content;
    private final AlertDialog dialog;
    private FeatureSettingsDialog(Activity activity, String title) {
        this.activity = activity;
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        dialog = new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("完成", null).create();
    }
    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density); }
    private TextView row(String label, Runnable click) {
        TextView text = new TextView(activity); text.setText(label); text.setTextSize(16); text.setPadding(dp(4), dp(16), dp(4), dp(16));
        text.setFocusable(click != null); if (click != null) text.setOnClickListener(v -> click.run()); content.addView(text); return text;
    }
    private void choice(String label, String key, String[] names, int defaultValue) {
        TextView[] view = new TextView[1];
        Runnable render = () -> view[0].setText(label + "：" + names[Math.max(0, Math.min(names.length - 1, Hawk.get(key, defaultValue)))]);
        view[0] = row(label, () -> new AlertDialog.Builder(activity).setTitle(label).setSingleChoiceItems(names, Hawk.get(key, defaultValue), (d, index) -> {
            Hawk.put(key, index); render.run(); d.dismiss();
        }).setNegativeButton("取消", null).show()); render.run();
    }
    private void toggle(String label, String key, boolean defaultValue) {
        TextView[] view = new TextView[1];
        Runnable render = () -> view[0].setText(label + "：" + (Hawk.get(key, defaultValue) ? "开" : "关"));
        view[0] = row(label, () -> { Hawk.put(key, !Hawk.get(key, defaultValue)); render.run(); }); render.run();
    }
    private void number(String label, String key, int[] values, String unit, int defaultValue) {
        TextView[] view = new TextView[1]; String[] names = new String[values.length]; for (int i = 0; i < values.length; i++) names[i] = values[i] + unit;
        Runnable render = () -> view[0].setText(label + "：" + Hawk.get(key, defaultValue) + unit);
        view[0] = row(label, () -> new AlertDialog.Builder(activity).setTitle(label).setItems(names, (d, index) -> { Hawk.put(key, values[index]); render.run(); }).show()); render.run();
    }
    public static void danmaku(Activity activity) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "弹幕设置");
        panel.toggle("加载弹幕", "danmaku_load", false); panel.toggle("显示弹幕", "danmaku_show", true);
        panel.toggle("自动匹配", "danmaku_auto", false); panel.toggle("优先使用源返回的弹幕", "danmaku_spider_first", true);
        TextView[] api = new TextView[1]; Runnable render = () -> api[0].setText("匹配接口：" + (Hawk.get("danmaku_api_url", "").isEmpty() ? "跟随点播源" : Hawk.get("danmaku_api_url", "")));
        api[0] = panel.row("匹配接口", () -> {
            EditText input = new EditText(activity); input.setText(Hawk.get("danmaku_api_url", "")); input.setHint("接口地址，留空跟随点播源");
            new AlertDialog.Builder(activity).setTitle("弹幕匹配接口").setMessage("支持 {name}、{episode} GET 模板；无模板时发送 name、episode JSON。返回弹幕名称和地址列表。")
                    .setView(input).setPositiveButton("保存", (d, which) -> { Hawk.put("danmaku_api_url", input.getText().toString().trim()); render.run(); }).setNegativeButton("取消", null).show();
        }); render.run();
        panel.number("不透明度", "danmaku_opacity", new int[]{25, 50, 75, 100}, "%", 100);
        panel.number("同屏数量上限", "danmaku_density", new int[]{10, 30, 60, 100, 150, 300, 500}, "条", 150);
        TextView[] scale = new TextView[1]; scale[0] = panel.row("文字大小：" + Hawk.get("danmaku_scale", 1f), () -> new AlertDialog.Builder(activity).setTitle("文字大小")
                .setItems(new String[]{"0.5", "0.75", "1.0", "1.5", "2.0", "3.0"}, (d, index) -> { float value = new float[]{.5f,.75f,1f,1.5f,2f,3f}[index]; Hawk.put("danmaku_scale", value); scale[0].setText("文字大小：" + value); }).show());
        TextView[] area = new TextView[1]; area[0] = panel.row("显示区域：" + (int) (Hawk.get("danmaku_area", .5f) * 100) + "%", () -> new AlertDialog.Builder(activity).setTitle("显示区域")
                .setItems(new String[]{"25%", "50%", "75%", "100%"}, (d, index) -> { float value = (index + 1) * .25f; Hawk.put("danmaku_area", value); area[0].setText("显示区域：" + (int) (value * 100) + "%"); }).show());
        TextView[] duration = new TextView[1]; duration[0] = panel.row("通过时间：" + Hawk.get("danmaku_duration", 8000L) / 1000 + "秒", () -> new AlertDialog.Builder(activity).setTitle("弹幕通过时间")
                .setItems(new String[]{"3秒", "5秒", "8秒", "10秒", "15秒"}, (d, index) -> { long value = new long[]{3000,5000,8000,10000,15000}[index]; Hawk.put("danmaku_duration", value); duration[0].setText("通过时间：" + value / 1000 + "秒"); }).show());
        TextView[] offset = new TextView[1]; offset[0] = panel.row("时间调整：" + Hawk.get("danmaku_offset", 0L) / 1000 + "秒", () -> new AlertDialog.Builder(activity).setTitle("时间调整（正值延后）")
                .setItems(new String[]{"-30秒", "-10秒", "-5秒", "0秒", "+5秒", "+10秒", "+30秒"}, (d, index) -> { long value = new long[]{-30000,-10000,-5000,0,5000,10000,30000}[index]; Hawk.put("danmaku_offset", value); offset[0].setText("时间调整：" + value / 1000 + "秒"); }).show());
        panel.dialog.show();
    }
    public static void playback(Activity activity, Runnable refresh) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "播放设置");
        panel.choice("默认播放器", HawkConfig.PLAY_TYPE, new String[]{"系统播放器", "IJK播放器", "Exo播放器"}, 2);
        panel.choice("渲染方式", HawkConfig.PLAY_RENDER, new String[]{"TextureView", "SurfaceView"}, 0);
        panel.choice("画面缩放", HawkConfig.PLAY_SCALE, new String[]{"默认", "16:9", "4:3", "填充", "原始", "裁剪"}, 0);
        panel.choice("后台播放", HawkConfig.BACKGROUND_PLAY_TYPE, new String[]{"关闭", "开启", "画中画"}, 2);
        panel.toggle("广告过滤", HawkConfig.VIDEO_PURIFY, true); panel.toggle("IJK缓存", HawkConfig.IJK_CACHE_PLAY, false);
        panel.dialog.setOnDismissListener(d -> refresh.run()); panel.dialog.show();
    }
    public static void modules(Activity activity) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "模块管理");
        panel.row("Exo 2.18.7 · 内置", null); panel.row("IJK · 内置", null); panel.row("QuickJS · 内置", null);
        List<File> caches = new ArrayList<>();
        File root = activity.getFilesDir(), main = new File(root, "csp.jar");
        if (ApiConfig.get().getSpider() != null && !ApiConfig.get().getSpider().isEmpty()) { caches.add(main); panel.row("公共 Spider：" + (main.isFile() ? main.length() / 1024 + " KB" : "尚未缓存"), null); }
        java.util.Set<String> jars = new java.util.HashSet<>();
        for (SourceBean site : ApiConfig.get().getSourceBeanList()) if (!site.getJar().isEmpty() && jars.add(site.getJar())) {
            File cache = new File(root, com.kukuqi.tvbox.osc.util.MD5.string2MD5(site.getJar().split(";md5;", 2)[0]) + ".jar"); caches.add(cache);
            panel.row(site.getName() + " Spider：" + (cache.isFile() ? cache.length() / 1024 + " KB" : "使用时加载"), null);
        }
        TextView status = panel.row("重新加载模块", () -> {});
        Runnable reload = () -> {
            status.setText("正在重新加载…");
            com.kukuqi.tvbox.osc.util.HeavyTaskUtil.executeNewTask(() -> {
                ApiConfig.get().resetModules(); String spider = ApiConfig.get().getSpider();
                if (spider == null || spider.isEmpty()) {
                    activity.runOnUiThread(() -> { if (!activity.isDestroyed()) status.setText("模块已重置，将在使用时加载"); });
                    return;
                }
                ApiConfig.get().loadJar(true, spider, new ApiConfig.LoadConfigCallback() {
                    public void success() { activity.runOnUiThread(() -> { if (!activity.isDestroyed()) status.setText("模块重新加载完成"); }); }
                    public void retry() {}
                    public void error(String message) { activity.runOnUiThread(() -> { if (!activity.isDestroyed()) status.setText("加载失败，请检查来源与网络"); }); }
                });
            });
        };
        status.setOnClickListener(v -> reload.run());
        panel.row("清除已下载的模块缓存", () -> new AlertDialog.Builder(activity).setTitle("清除模块缓存")
                .setMessage("使用来源时会重新下载所需模块。").setPositiveButton("清除", (d, which) -> {
                    for (File cache : caches) if (cache.isFile()) cache.delete(); reload.run();
                }).setNegativeButton("取消", null).show());
        panel.dialog.show();
    }
    public static void homeSite(Activity activity) {
        List<SourceBean> sites = ApiConfig.get().getSourceBeanList();
        if (sites.isEmpty()) { new AlertDialog.Builder(activity).setMessage("当前点播源尚未加载，请先选择有效来源").setPositiveButton("知道了", null).show(); return; }
        String[] names = new String[sites.size()]; int selected = 0;
        for (int i = 0; i < sites.size(); i++) { names[i] = sites.get(i).getName(); if (sites.get(i).getKey().equals(ApiConfig.get().getHomeSourceBean().getKey())) selected = i; }
        new AlertDialog.Builder(activity).setTitle("首页站点").setSingleChoiceItems(names, selected, (d, index) -> {
            ApiConfig.get().setSourceBean(sites.get(index)); EventBus.getDefault().post(new SourceChangedEvent()); d.dismiss();
        }).setNegativeButton("取消", null).show();
    }
}
