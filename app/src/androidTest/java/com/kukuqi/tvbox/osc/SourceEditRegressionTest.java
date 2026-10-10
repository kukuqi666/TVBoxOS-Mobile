package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.EditText;
import android.widget.TextView;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.ui.activity.SettingActivity;
import com.kukuqi.tvbox.osc.ui.dialog.SourceAddDialog;
import com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.orhanobut.hawk.Hawk;
import java.util.*;
import java.util.concurrent.*;

public class SourceEditRegressionTest extends InstrumentationTestCase {
    public void testBothCategoryEditorsSaveOfflineAndFailedUsePreservesActiveSource() throws Exception {
        String[] keys = {SourceLibrary.key(1), SourceLibrary.key(2), HawkConfig.LIVE_URL, HawkConfig.WALLPAPER_URL, HawkConfig.LIVE_HISTORY, HawkConfig.WALLPAPER_HISTORY};
        Map<String, Object> before = new HashMap<>(); for (String key : keys) if (Hawk.contains(key)) before.put(key, Hawk.get(key));
        onMain(() -> { App.getInstance().isNormalStart = true; Hawk.put(SourceLibrary.key(1), "[]"); Hawk.put(SourceLibrary.key(2), "[]"); });
        SettingActivity activity = (SettingActivity) getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), SettingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        BasePopupView[] opened = new BasePopupView[1];
        try {
            for (int kind : new int[]{1, 2}) {
                String oldUrl = "https://example.test/current-" + kind, newUrl = "http://127.0.0.1:1/new-" + kind;
                onMain(() -> { Hawk.put(kind == 1 ? HawkConfig.LIVE_URL : HawkConfig.WALLPAPER_URL, oldUrl); SourceLibrary.save(kind, "旧名称", oldUrl); });
                CountDownLatch saved = new CountDownLatch(1);
                SourceAddDialog editor = new SourceAddDialog(activity, kind, new SourceLibrary.Entry("旧名称", oldUrl), (entry, use) -> { assertFalse(use); saved.countDown(); });
                onMain(() -> { opened[0] = editor; new XPopup.Builder(activity).autoFocusEditText(false).asCustom(editor).show(); });
                waitUntil(() -> editor.findViewById(R.id.source_name) != null);
                onMain(() -> {
                    assertEquals("旧名称", ((EditText) editor.findViewById(R.id.source_name)).getText().toString());
                    assertEquals(oldUrl, ((EditText) editor.findViewById(R.id.source_url)).getText().toString());
                    assertTrue(((TextView) editor.findViewById(R.id.source_add_title)).getText().toString().startsWith("编辑"));
                    ((EditText) editor.findViewById(R.id.source_name)).setText("新名称");
                    ((EditText) editor.findViewById(R.id.source_url)).setText(newUrl);
                    editor.findViewById(R.id.source_save).performClick();
                });
                assertTrue(saved.await(5, TimeUnit.SECONDS));
                assertEquals("新名称", SourceLibrary.name(kind, newUrl)); assertEquals(oldUrl, Hawk.get(kind == 1 ? HawkConfig.LIVE_URL : HawkConfig.WALLPAPER_URL));
                Thread.sleep(350);
                SourcePickerDialog picker = new SourcePickerDialog(activity, kind, null);
                onMain(() -> { opened[0] = picker; new XPopup.Builder(activity).asCustom(picker).show(); });
                waitUntil(() -> picker.findViewById(R.id.source_status) != null);
                onMain(() -> { try {
                    java.lang.reflect.Method use = SourcePickerDialog.class.getDeclaredMethod("use", SourceLibrary.Entry.class); use.setAccessible(true);
                    use.invoke(picker, new SourceLibrary.Entry("新名称", newUrl));
                } catch (Exception error) { throw new RuntimeException(error); } });
                waitUntil(() -> ((TextView) picker.findViewById(R.id.source_status)).getText().toString().contains("保留"));
                assertEquals(oldUrl, Hawk.get(kind == 1 ? HawkConfig.LIVE_URL : HawkConfig.WALLPAPER_URL));
                assertEquals("新名称", SourceLibrary.name(kind, newUrl));
                onMain(picker::dismiss); Thread.sleep(350);
            }
        } finally { onMain(() -> { if (opened[0] != null) opened[0].dismiss(); activity.finish(); for (String key : keys) { if (before.containsKey(key)) Hawk.put(key, before.get(key)); else Hawk.delete(key); } }); }
    }
    private interface Check { boolean ready(); }
    private void waitUntil(Check check) throws Exception {
        boolean[] ready = new boolean[1]; for (int i = 0; i < 100; i++) { onMain(() -> ready[0] = check.ready()); if (ready[0]) return; Thread.sleep(150); } fail("来源界面未就绪");
    }
    private void onMain(Runnable action) {
        Throwable[] error = new Throwable[1]; getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] instanceof Error) throw (Error) error[0]; if (error[0] != null) throw new RuntimeException(error[0]);
    }
}
