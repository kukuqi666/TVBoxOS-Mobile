package com.kukuqi.tvbox.osc.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.*;
import com.kukuqi.tvbox.osc.bean.ParseBean;
import com.kukuqi.tvbox.osc.util.AdBlocker;
import com.kukuqi.tvbox.osc.util.ConfigCompat;
import com.kukuqi.tvbox.osc.util.ParseResult;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** FongMi type 4 semantics: race JSON parsers and web parsers, then cancel all losers. */
public final class AutomaticParser {
    public interface Result { void success(String url, Map<String,String> headers); void failure(); }
    private final Activity activity;
    private final Predicate<String> video;
    private final Result callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WebView> views = new ArrayList<>();
    private Map<String,String> inherited=Collections.emptyMap();
    private Map<String,String> headers(ParseBean parse){Map<String,String> result=new LinkedHashMap<>(inherited);result.putAll(parse.getHeaders());return result;}
    private final AtomicBoolean done = new AtomicBoolean();
    private final Runnable timeout = () -> fail();
    public AutomaticParser(Activity activity, Predicate<String> video, Result callback) {this.activity=activity;this.video=video;this.callback=callback;}
    public void start(List<ParseBean> jsons, List<ParseBean> webs, String url, String click) { start(jsons,webs,url,click,Collections.emptyMap()); }
    public void start(List<ParseBean> jsons, List<ParseBean> webs, String url, String click, Map<String,String> inherited) {
        this.inherited = new LinkedHashMap<>(inherited);
        if (jsons.isEmpty() && webs.isEmpty()) {fail();return;}
        main.postDelayed(timeout,20000);
        AtomicInteger pending = new AtomicInteger(jsons.size());
        for (ParseBean parse:jsons) {
            HttpHeaders headers=new HttpHeaders();headers(parse).forEach(headers::put);
            try {
                OkGo.<String>get(parse.getUrl()+url).tag(this).headers(headers).execute(new AbsCallback<String>() {
                    @Override public String convertResponse(okhttp3.Response response)throws Throwable {if(!response.isSuccessful()||response.body()==null)throw new java.io.IOException();return response.body().string();}
                    @Override public void onSuccess(Response<String> response) {
                        try {ParseResult result=ParseResult.read(response.body(),headers(parse));win(result.url,result.headers);}catch(Exception ignored){}
                        finished();
                    }
                    @Override public void onError(Response<String> response){finished();}
                    private void finished(){if(pending.decrementAndGet()==0&&webs.isEmpty())fail();}
                });
            }catch(Exception invalid){if(pending.decrementAndGet()==0&&webs.isEmpty())fail();}
        }
        for(ParseBean parse:webs) {if(done.get())break;try{web(parse,url,click);}catch(RuntimeException ignored){}}
    }
    @SuppressLint("SetJavaScriptEnabled") private void web(ParseBean parse,String url,String click) {
        WebView view=new WebView(activity);views.add(view);
        view.setFocusable(false);activity.addContentView(view,new ViewGroup.LayoutParams(1,1));
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportMultipleWindows(false);settings.setAllowFileAccess(false);
        Map<String,String> configured=headers(parse);
        if(configured.containsKey("User-Agent"))settings.setUserAgentString(configured.get("User-Agent"));
        view.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){
                String candidate=request.getUrl().toString();
                if(done.get()||AdBlocker.isAd(candidate))return AdBlocker.createEmptyResource();
                if(video.test(candidate)){
                    Map<String,String> headers=new LinkedHashMap<>(configured);
                    request.getRequestHeaders().forEach((key,value)->{if(key.equalsIgnoreCase("User-Agent")||key.equalsIgnoreCase("Referer")||key.equalsIgnoreCase("Origin")||key.equalsIgnoreCase("Cookie"))headers.put(ConfigCompat.headerKey(key),value);});
                    String cookie=CookieManager.getInstance().getCookie(candidate);if(cookie!=null&&!cookie.isEmpty())headers.put("Cookie",cookie);
                    win(candidate,headers);return AdBlocker.createEmptyResource();
                }
                return null;
            }
            @Override public void onPageFinished(WebView web,String page){
                if(done.get()||click==null||click.isEmpty())return;
                String[] parts=click.split(";",2);if(parts.length==2&&!page.contains(parts[0]))return;
                String selector=parts.length==2?parts[1]:parts[0];
                web.evaluateJavascript("var e=document.querySelector("+new com.google.gson.Gson().toJson(selector)+");if(e)e.click();",null);
            }
        });
        view.loadUrl(parse.getUrl()+url,configured);
    }
    private void win(String url,Map<String,String> headers){if(!done.compareAndSet(false,true))return;main.post(()->{cleanup();if(!activity.isDestroyed())callback.success(url,headers);});}
    private void fail(){if(!done.compareAndSet(false,true))return;main.post(()->{cleanup();if(!activity.isDestroyed())callback.failure();});}
    public void stop(){done.set(true);if(Looper.myLooper()==Looper.getMainLooper())cleanup();else main.post(this::cleanup);}
    private void cleanup(){main.removeCallbacks(timeout);OkGo.getInstance().cancelTag(this);for(WebView view:views){view.stopLoading();if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);view.removeAllViews();view.destroy();}views.clear();}
}
