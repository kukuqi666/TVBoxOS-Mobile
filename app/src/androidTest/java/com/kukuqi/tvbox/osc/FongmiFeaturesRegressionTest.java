package com.kukuqi.tvbox.osc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonPrimitive;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.ui.activity.*;
import com.kukuqi.tvbox.osc.ui.dialog.VideoLinkDialog;
import com.kukuqi.tvbox.osc.ui.fragment.PlayFragment;
import com.kukuqi.tvbox.osc.util.*;
import com.orhanobut.hawk.Hawk;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** End-to-end behavior using a local HTTP source and a real H.264 video. */
public class FongmiFeaturesRegressionTest extends InstrumentationTestCase {
    private static final String VIDEO = "/sdcard/Download/tvbox-regression.mp4";
    private final Map<String,Object> before = new HashMap<>();
    private final String[] keys = {HawkConfig.API_URL,HawkConfig.HOME_API,HawkConfig.HOME_REC,HawkConfig.PLAY_TYPE,
            HawkConfig.BACKGROUND_PLAY_TYPE,HawkConfig.PRIVATE_BROWSING,"danmaku_load","danmaku_show","danmaku_auto",
            "danmaku_api_url","danmaku_spider_first","hot_search_cache","hot_search_time","poster_size","theme_color","live_playlist_url",
            "wallpaper_mode",HawkConfig.WALLPAPER_URL,"wallpaper_softness"};
    private final java.util.Queue<String> routes=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private NanoHTTPD server; private File config; private String base; private Activity active;
    private final AtomicInteger homeRequests = new AtomicInteger(), searchRequests = new AtomicInteger(), matchRequests = new AtomicInteger(), commentRequests = new AtomicInteger();
    @Override protected void setUp() throws Exception {
        super.setUp();
        for (String key: keys) if (Hawk.contains(key)) before.put(key,Hawk.get(key));
        server = new NanoHTTPD("127.0.0.1",0) {
            @Override public Response serve(IHTTPSession session) {
                String path=session.getUri();routes.add(path);
                if(path.equals("/video.mp4")) {
                    try { return newFixedLengthResponse(Response.Status.OK,"video/mp4",new FileInputStream(VIDEO),new File(VIDEO).length()); }
                    catch(Exception e) { return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","missing fixture"); }
                }
                if(path.equals("/comments.xml")) {commentRequests.incrementAndGet();return newFixedLengthResponse(Response.Status.OK,"application/xml","<i><d p='0,1,25,16777215'>真实弹幕回归验证</d><d p='3,5,25,16711680'>顶部弹幕</d></i>");}
                if(path.equals("/match")) {
                    if (session.getMethod() == Method.POST) {
                        try { session.parseBody(new HashMap<>()); }
                        catch (Exception e) { return newFixedLengthResponse(Response.Status.BAD_REQUEST,"text/plain",e.toString()); }
                    }
                    matchRequests.incrementAndGet();return newFixedLengthResponse(Response.Status.OK,"application/json","[{\"name\":\"测试\",\"url\":\""+base+"/comments.xml\"}]");
                }
                if(path.equals("/web-empty")) return newFixedLengthResponse("<html>no video</html>");
                if(path.equals("/web-video")) return newFixedLengthResponse(Response.Status.OK,"text/html","<html><video autoplay src='"+base+"/video.mp4?from=web'></video></html>");
                if(path.equals("/json-slow")) {try{Thread.sleep(3000);}catch(Exception ignored){}return newFixedLengthResponse(Response.Status.OK,"application/json","{\"url\":\""+base+"/video.mp4?from=json\"}");}
                if(path.equals("/api")) {
                    if(session.getParms().containsKey("wd")) searchRequests.incrementAndGet(); else homeRequests.incrementAndGet();
                    StringBuilder videos=new StringBuilder();
                    for(int i=0;i<60;i++) { if(i>0) videos.append(','); videos.append("{\"vod_id\":\"").append(i).append("\",\"vod_name\":\"测试影片 ").append(i).append("\",\"vod_pic\":\"\",\"vod_play_from\":\"mp4\",\"vod_play_url\":\"播放$").append(base).append("/video.mp4\"}"); }
                    return newFixedLengthResponse(Response.Status.OK,"application/json","{\"class\":[{\"type_id\":\"1\",\"type_name\":\"电影\"}],\"filters\":{\"1\":[{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"}]}]},\"page\":1,\"pagecount\":1,\"list\":["+videos+"]}");
                }
                if(path.equals("/first.m3u")||path.equals("/second.m3u")) return newFixedLengthResponse("#EXTM3U\n#EXTINF:-1,"+path+"\n"+base+"/video.mp4\n");
                return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","not found");
            }
        };
        server.start(); base="http://127.0.0.1:"+server.getListeningPort();
        config=new File(getInstrumentation().getTargetContext().getFilesDir(),"fongmi-feature-fixture.json");
        Files.write(config.toPath(),("{\"sites\":[{\"key\":\"fixture\",\"name\":\"测试站点\",\"type\":1,\"api\":\""+base+"/api\",\"searchable\":1}],\"lives\":[{\"name\":\"线路一\",\"url\":\""+base+"/first.m3u\"},{\"name\":\"线路二\",\"url\":\""+base+"/second.m3u\"}]}").getBytes(StandardCharsets.UTF_8));
        onMain(()->{App.getInstance().isNormalStart=true; Hawk.put(HawkConfig.API_URL,"file://"+config.getAbsolutePath()); Hawk.put(HawkConfig.HOME_API,"fixture"); Hawk.put(HawkConfig.HOME_REC,1); Hawk.put(HawkConfig.PLAY_TYPE,2); Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE,0); Hawk.put(HawkConfig.PRIVATE_BROWSING,true);});
    }
    @Override protected void tearDown() throws Exception {
        onMain(()->{ if(active!=null)active.finish(); for(String key:keys) {if(before.containsKey(key))Hawk.put(key,before.get(key)); else Hawk.delete(key);} });
        server.stop(); config.delete(); super.tearDown();
    }
    public void testBrowseRefreshSwitchesButtonAndReturnsToTop() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); active=main;
        onMain(()->main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        waitFor(()->main.findViewById(R.id.tvHotList1)!=null && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter()!=null && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter().getItemCount()==60);
        RecyclerView list=main.findViewById(R.id.tvHotList1); View button=main.findViewById(R.id.btn_live);
        onMain(()->assertEquals("链接播放",button.getContentDescription()));
        onMain(()->list.scrollToPosition(30)); waitFor(()->"回到顶部".contentEquals(button.getContentDescription())); capture("browse-top-button.png");
        onMain(button::performClick); waitFor(()->!list.canScrollVertically(-1));
        waitFor(()->"链接播放".contentEquals(button.getContentDescription()));
        int count=homeRequests.get(); swipe(list);
        waitFor(()->homeRequests.get()>count); waitFor(()->!((SwipeRefreshLayout)main.findViewById(R.id.swipeRefresh)).isRefreshing());
        onMain(button::performClick); waitFor(()->node("确定")!=null);
        androidx.appcompat.app.AlertDialog[] dialog={null};
        onMain(()->{
            com.kukuqi.tvbox.osc.ui.fragment.UserFragment home=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.UserFragment.class);
            VideoLinkDialog entry=(VideoLinkDialog)home.getChildFragmentManager().findFragmentByTag("video-link");
            dialog[0]=(androidx.appcompat.app.AlertDialog)entry.getDialog();
            android.widget.EditText input=dialog[0].findViewById(R.id.link_text);
            assertTrue(input.isSingleLine());
            input.setText("https://vd2.bdstatic.com/mda-sctj0f96cn79js4n/1080p/cae_h264/1774710384405617311/mda-sctj0f96cn79js4n.mp4");
            assertEquals(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM,
                ((com.google.android.material.textfield.TextInputLayout)dialog[0].findViewById(R.id.link_input)).getEndIconMode());
        });
        Thread.sleep(2000);capture("video-link-dialog.png");
        AccessibilityNodeInfo cancel=node("取消"); assertNotNull(cancel); cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    public void testLinkReallyPlaysAndDanmakuSurvivesFullscreenAndFallback() throws Exception {
        assertTrue(new File(VIDEO).isFile());
        try (okhttp3.Response response = com.lzy.okgo.OkGo.getInstance().getOkHttpClient().newCall(new okhttp3.Request.Builder().url(base+"/match").post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build()).execute()) {
            String body=response.body().string();assertEquals(body,1,com.kukuqi.tvbox.osc.danmaku.DanmakuController.urls(com.google.gson.JsonParser.parseString(body)).size());
        }
        onMain(()->{Hawk.put("danmaku_load",true);Hawk.put("danmaku_show",true);Hawk.put("danmaku_auto",true);Hawk.put("danmaku_api_url",base+"/match");Hawk.put("danmaku_spider_first",true);});
        DetailActivity detail=(DetailActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),DetailActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sourceKey",VideoLinkDialog.SOURCE).putExtra("id",base+"/video.mp4"));active=detail;
        MyVideoView[] player={null};
        waitFor(()->{for(androidx.fragment.app.Fragment f:detail.getSupportFragmentManager().getFragments())if(f instanceof PlayFragment)player[0]=((PlayFragment)f).getPlayer();return player[0]!=null&&player[0].isPlaying();});
        onMain(()->player[0].setDanmakuContext(new JsonPrimitive(base+"/missing.xml"),"回归影片","第1集"));
        try { waitFor(()->player[0].getDanmakuCount()==2); } catch(AssertionError e) {throw new AssertionError("routes="+routes+", danmaku status="+player[0].getDanmakuStatus()+", count="+player[0].getDanmakuCount()+", matches="+matchRequests.get()+", comments="+commentRequests.get()+", load="+Hawk.get("danmaku_load",false)+", auto="+Hawk.get("danmaku_auto",false),e);}
        onMain(()->{player[0].seekTo(2000); player[0].startFullScreen();});
        waitFor(()->player[0].isFullScreen()); Thread.sleep(300);capture("video-danmaku-fullscreen.png");
        onMain(()->assertEquals(2,player[0].getDanmakuCount()));
    }
    public void testCategoryKeepsFilterAndTopActions() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=main;
        waitFor(()->main.findViewById(R.id.tvHotList1)!=null);
        onMain(()->((androidx.viewpager.widget.ViewPager)main.findViewById(R.id.mViewPager)).setCurrentItem(1,false));
        com.kukuqi.tvbox.osc.ui.fragment.GridFragment[] category={null};
        waitFor(()->{category[0]=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.GridFragment.class);return category[0]!=null&&category[0].getView()!=null&&((RecyclerView)category[0].getView().findViewById(R.id.mGridView)).getAdapter().getItemCount()==60;});
        View button=category[0].getView().findViewById(R.id.btn_filter);
        RecyclerView list=category[0].getView().findViewById(R.id.mGridView);
        waitFor(()->"筛选".contentEquals(button.getContentDescription()));
        onMain(button::performClick);waitFor(()->node("2026")!=null);capture("category-filter.png");
        click(node("2026"));
        int count=homeRequests.get();node("筛选").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->homeRequests.get()>count);
        onMain(()->list.scrollToPosition(30));waitFor(()->"回到顶部".contentEquals(button.getContentDescription()));capture("category-top.png");
        onMain(button::performClick);waitFor(()->!list.canScrollVertically(-1)&&"筛选".contentEquals(button.getContentDescription()));
        onMain(button::performClick);waitFor(()->node("2026")!=null);assertNull(node("请输入地址…"));
        node("筛选").performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    public void testLinkFolderChooserPlaysContentUri() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=main;
        waitFor(()->main.findViewById(R.id.btn_live)!=null);
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(main,main.getPackageName()+".fileprovider",new File(VIDEO));
        android.content.IntentFilter filter=new android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("video/*");
        android.app.Instrumentation.ActivityMonitor picker=getInstrumentation().addMonitor(filter,
                new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)),true);
        android.app.Instrumentation.ActivityMonitor playback=getInstrumentation().addMonitor(LocalPlayActivity.class.getName(),null,false);
        try {
            onMain(()->main.findViewById(R.id.btn_live).performClick());waitFor(()->node("确定")!=null);
            onMain(()->{
                com.kukuqi.tvbox.osc.ui.fragment.UserFragment home=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.UserFragment.class);
                VideoLinkDialog dialog=(VideoLinkDialog)home.getChildFragmentManager().findFragmentByTag("video-link");
                dialog.requireDialog().findViewById(com.google.android.material.R.id.text_input_end_icon).performClick();
            });
            LocalPlayActivity local=(LocalPlayActivity)playback.waitForActivityWithTimeout(10000);assertNotNull(local);active=local;
            assertEquals(1,picker.getHits());MyVideoView player=local.findViewById(R.id.player);waitFor(player::isPlaying);
        } finally {
            if(picker.getHits()==0)getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            getInstrumentation().removeMonitor(picker);getInstrumentation().removeMonitor(playback);onMain(main::finish);
        }
    }
    public void testAboutSurfaceRevealsWallpaper() throws Exception {
        onMain(()->{Hawk.put("wallpaper_mode","custom");Hawk.put(HawkConfig.WALLPAPER_URL,"drawable://wallpaper_gradient_ocean");Hawk.put("wallpaper_softness",0);});
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AboutActivity.class.getName(),null,false);
        try {
            waitFor(()->main.findViewById(R.id.llAbout)!=null);onMain(()->main.findViewById(R.id.llAbout).performClick());
            AboutActivity about=(AboutActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull(about);active=about;
            onMain(()->about.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitFor(()->node("Wi-Fi 后台更新")!=null);capture("about-translucent.png");
            onMain(()->{assertNull(about.findViewById(R.id.llVodApi));int color=androidx.core.content.ContextCompat.getColor(about,R.color.bg_about_panel);assertEquals(0x99,android.graphics.Color.alpha(color));});
            onMain(about::finish);waitFor(main::hasWindowFocus);active=main;
        } finally {getInstrumentation().removeMonitor(monitor);onMain(main::finish);}
    }
    public void testHotSearchClicksCompleteWordWithoutRecordingPrivateHistory() throws Exception {
        onMain(()->{Hawk.put("hot_search_cache","[\"庆余年 第二季\",\"三体\"]");Hawk.put("hot_search_time",System.currentTimeMillis());loadConfig();});
        Object history=Hawk.get(HawkConfig.HISTORY_SEARCH,null);
        FastSearchActivity search=(FastSearchActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),FastSearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=search;
        waitFor(()->search.findViewById(R.id.fl_hot)!=null && ((android.view.ViewGroup)search.findViewById(R.id.fl_hot)).getChildCount()==2);capture("hot-search.png");
        onMain(()->((android.view.ViewGroup)search.findViewById(R.id.fl_hot)).getChildAt(0).performClick());
        waitFor(()->searchRequests.get()>0);
        onMain(()->assertEquals("庆余年 第二季",((android.widget.EditText)search.findViewById(R.id.et_search)).getText().toString()));
        assertEquals(history,Hawk.get(HawkConfig.HISTORY_SEARCH,null));
    }
    public void testPreferredLivePlaylistActuallyLoads() throws Exception {
        onMain(()->Hawk.put("live_playlist_url",base+"/second.m3u"));
        com.google.gson.JsonArray[] groups={null}; String[] error={null};
        onMain(()->LiveSourceLoader.load("file://"+config.getAbsolutePath(),(value,message)->{groups[0]=value;error[0]=message;}));
        waitFor(()->groups[0]!=null||error[0]!=null);
        assertNotNull(error[0],groups[0]);
        assertEquals("/second.m3u",groups[0].get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().get("name").getAsString());
    }
    public void testReadonlyModulesCanLoadAndReloadOnAndroid14() throws Exception {
        File source=new File(getInstrumentation().getTargetContext().getCacheDir(),"module-fixture.jar");
        File cache=new File(getInstrumentation().getTargetContext().getFilesDir(),"module-regression.jar");
        try {
            try(InputStream input=getInstrumentation().getContext().getAssets().open("fixture-spider.jar");OutputStream output=new FileOutputStream(source)) {
                byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
            }
            for(int attempt=0;attempt<2;attempt++) {
                SourceManager.copyLocal(android.net.Uri.fromFile(source).toString(),cache);
                assertTrue(new com.github.catvod.crawler.JarLoader().load(cache.getAbsolutePath()));
                assertFalse("Dynamic DEX must be read-only",cache.canWrite());
                java.lang.reflect.Method load=com.github.catvod.crawler.JsLoader.class.getDeclaredMethod("loadClassLoader",String.class,String.class);
                load.setAccessible(true);
                assertTrue((Boolean)load.invoke(new com.github.catvod.crawler.JsLoader(),cache.getAbsolutePath(),"fixture-"+attempt));
            }
        } finally {source.delete();cache.delete();com.github.catvod.crawler.JsLoader.load();}
    }
    public void testBackupLeavesDatabaseUsableAndRejectsCorruptRestore() throws Exception {
        File backup=new File(getInstrumentation().getTargetContext().getCacheDir(),"room-regression.db"), corrupt=new File(getInstrumentation().getTargetContext().getCacheDir(),"room-corrupt.db");
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);
        assertTrue(com.kukuqi.tvbox.osc.data.AppDataManager.backup(backup));
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);
        Files.write(corrupt.toPath(),"SQLite format 3\u0000corrupt body".getBytes(StandardCharsets.US_ASCII));
        assertFalse(com.kukuqi.tvbox.osc.data.AppDataManager.restore(corrupt));
        assertTrue(com.kukuqi.tvbox.osc.data.AppDataManager.restore(backup));
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);backup.delete();corrupt.delete();
    }
    public void testLocalBackgroundAndPipReallyContinuePlayback() throws Exception {
        for(int mode=0;mode<=2;mode++) {
            final int selected=mode;onMain(()->Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE,selected));
            LocalPlayActivity local=(LocalPlayActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),LocalPlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("position",0).putExtra("videoList","[{\"path\":\""+VIDEO+"\",\"displayName\":\"后台播放验证\"}]"));active=local;
            MyVideoView player=local.findViewById(R.id.player);waitFor(player::isPlaying);waitFor(local::hasWindowFocus);Thread.sleep(500);
            try(android.os.ParcelFileDescriptor command=getInstrumentation().getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")){try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)){while(input.read()!=-1){}}}
            if(mode==0)waitFor(()->!player.isPlaying());
            else {Thread.sleep(800);onMain(()->assertTrue("background mode="+selected+", state="+player.getCurrentPlayState()+", pip="+local.isInPictureInPictureMode(),player.isPlaying()));if(mode==2) {waitFor(local::isInPictureInPictureMode);capture("local-pip.png");}}
            onMain(local::finish);Thread.sleep(400);active=null;
        }
    }
    public void testAutomaticParserRacesAllWebLinesAgainstJson() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        waitFor(()->main.findViewById(R.id.llModuleManager)!=null);
        com.kukuqi.tvbox.osc.bean.ParseBean json=new com.kukuqi.tvbox.osc.bean.ParseBean(),empty=new com.kukuqi.tvbox.osc.bean.ParseBean(),web=new com.kukuqi.tvbox.osc.bean.ParseBean();
        json.setUrl(base+"/json-slow?url=");empty.setUrl(base+"/web-empty?url=");web.setUrl(base+"/web-video?url=");
        String[] result={null};AtomicInteger wins=new AtomicInteger();com.kukuqi.tvbox.osc.player.AutomaticParser[] job={null};
        onMain(()->{job[0]=new com.kukuqi.tvbox.osc.player.AutomaticParser(main,url->url.contains("/video.mp4"),new com.kukuqi.tvbox.osc.player.AutomaticParser.Result(){public void success(String url,Map<String,String> headers){result[0]=url;wins.incrementAndGet();}public void failure(){result[0]="failed";}});job[0].start(Collections.singletonList(json),Arrays.asList(empty,web),"https://example.com/watch","");});
        try{waitFor(()->result[0]!=null);assertEquals(base+"/video.mp4?from=web",result[0]);Thread.sleep(3100);assertEquals(1,wins.get());}finally{onMain(()->job[0].stop());}
    }
    public void testMyPanelsApplySettingsAndAreScrollable() throws Exception {
        onMain(this::loadConfig);
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        waitFor(()->main.findViewById(R.id.llModuleManager)!=null);
        int[] entries={R.id.llVodHome,R.id.llLiveHome,R.id.llSourceHistory,R.id.llModuleManager,R.id.llPlaybackSettings,R.id.llDanmakuSettings,R.id.llImageSize,R.id.llThemeColor,R.id.llHotSearch};
        onMain(()->{for(int id:entries)assertTrue(main.findViewById(id).hasOnClickListeners());});
        capture("my-settings-top.png");
        onMain(()->main.findViewById(R.id.llModuleManager).performClick());waitFor(()->node("Exo 2.18.7 · 内置")!=null);capture("module-manager.png");node("完成").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        waitFor(()->node("模块管理")==null||node("Exo 2.18.7 · 内置")==null);
        onMain(()->main.findViewById(R.id.llDanmakuSettings).performClick());waitFor(()->node("加载弹幕：关")!=null||node("加载弹幕：开")!=null);
        boolean was=Hawk.get("danmaku_load",false);node(was?"加载弹幕：开":"加载弹幕：关").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->Hawk.get("danmaku_load",false)!=was);capture("danmaku-settings.png");node("完成").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        onMain(()->main.findViewById(R.id.llImageSize).performClick());waitFor(()->node("小")!=null);node("小").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->Hawk.get("poster_size",2)==0);
        onMain(()->assertTrue(Utils.getPosterSpanCount(main)>=3));
        onMain(()->main.findViewById(R.id.llThemeColor).performClick());waitFor(()->node("绿色")!=null);node("绿色").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->Hawk.get("theme_color",0)==3);
        onMain(()->assertEquals(0xff008577,((com.google.android.material.bottomnavigation.BottomNavigationView)main.findViewById(R.id.bottom_nav)).getItemIconTintList().getColorForState(new int[]{android.R.attr.state_checked},0)));
        onMain(()->((android.widget.ScrollView)main.findViewById(R.id.my_scroll)).fullScroll(View.FOCUS_DOWN));Thread.sleep(250);capture("my-settings-bottom.png");
    }
    private void loadConfig(){try{java.lang.reflect.Method method=ApiConfig.class.getDeclaredMethod("parseJson",String.class,String.class);method.setAccessible(true);method.invoke(ApiConfig.get(),"file://"+config.getAbsolutePath(),readConfig());}catch(Exception e){throw new RuntimeException(e);}}
    private <T extends androidx.fragment.app.Fragment> T findFragment(androidx.fragment.app.FragmentManager manager,Class<T> type){for(androidx.fragment.app.Fragment fragment:manager.getFragments()){if(type.isInstance(fragment))return type.cast(fragment);if(fragment.isAdded()){T found=findFragment(fragment.getChildFragmentManager(),type);if(found!=null)return found;}}return null;}
    private String readConfig(){try{return new String(Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8);}catch(Exception e){throw new RuntimeException(e);}}
    private AccessibilityNodeInfo node(String text){AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow(); if(root==null)return null;for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(n.getText()==null?"":n.getText()))return n;return null;}
    private void click(AccessibilityNodeInfo item){assertNotNull(item);while(!item.isClickable()&&item.getParent()!=null)item=item.getParent();assertTrue(item.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void swipe(View list)throws Exception{int[]xy=new int[2];onMain(()->list.getLocationOnScreen(xy));long start=SystemClock.uptimeMillis();float x=xy[0]+list.getWidth()/2f,y=xy[1]+30;getInstrumentation().sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0));for(int i=1;i<=15;i++){Thread.sleep(20);getInstrumentation().sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,y+i*30,0));}getInstrumentation().sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y+450,0));}
    private interface Check{boolean ready();}
    private void waitFor(Check check)throws Exception{boolean[]ready={false};for(int i=0;i<150;i++){onMain(()->ready[0]=check.ready());if(ready[0])return;Thread.sleep(100);}fail("Timed out waiting for feature");}
    private void onMain(Runnable action){Throwable[]error={null};getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void capture(String name)throws Exception{Thread.sleep(500);getInstrumentation().waitForIdleSync();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(FileOutputStream out=new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
