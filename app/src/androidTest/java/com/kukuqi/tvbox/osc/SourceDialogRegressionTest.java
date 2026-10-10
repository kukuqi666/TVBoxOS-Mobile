package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runs the actual form and saved-source list; an offline URL must be saved without applying it. */
public class SourceDialogRegressionTest extends InstrumentationTestCase {
    public void testOfflineFormSaveAndReopenedCategoryLists() throws Exception {
        String[] keys = {SourceLibrary.key(SourceLibrary.LIVE), SourceLibrary.key(SourceLibrary.WALLPAPER)};
        boolean[] existed = {Hawk.contains(keys[0]), Hawk.contains(keys[1])};
        String[] originals = {Hawk.get(keys[0], "[]"), Hawk.get(keys[1], "[]")};
        String live = Hawk.get(HawkConfig.LIVE_URL, ""), wall = Hawk.get(HawkConfig.WALLPAPER_URL, "");
        getInstrumentation().runOnMainSync(() -> App.getInstance().isNormalStart = true);
        Intent intent = new Intent(getInstrumentation().getTargetContext(), SettingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SettingActivity activity = (SettingActivity) getInstrumentation().startActivitySync(intent);
        BasePopupView[] open = new BasePopupView[1];
        try {
            for (int kind : new int[]{SourceLibrary.LIVE, SourceLibrary.WALLPAPER}) {
                CountDownLatch saved = new CountDownLatch(1); boolean[] applied = new boolean[1];
                SourceAddDialog dialog = new SourceAddDialog(activity, kind, (entry, use) -> { applied[0] = use; saved.countDown(); });
                getInstrumentation().runOnMainSync(() -> { open[0] = dialog; new XPopup.Builder(activity).autoFocusEditText(false).asCustom(dialog).show(); });
                waitForShown(dialog);
                getInstrumentation().runOnMainSync(() -> {
                    assertEquals("", ((EditText) dialog.findViewById(R.id.source_name)).getText().toString());
                    assertEquals("", ((EditText) dialog.findViewById(R.id.source_url)).getText().toString());
                    ((EditText) dialog.findViewById(R.id.source_name)).setText("离线来源");
                    ((EditText) dialog.findViewById(R.id.source_url)).setText("https://offline.example.test/source");
                    dialog.findViewById(R.id.source_save).performClick();
                });
                assertTrue(saved.await(5, TimeUnit.SECONDS)); assertFalse(applied[0]);
                assertEquals(live, Hawk.get(HawkConfig.LIVE_URL, "")); assertEquals(wall, Hawk.get(HawkConfig.WALLPAPER_URL, ""));
                SourcePickerDialog picker = new SourcePickerDialog(activity, kind, null);
                getInstrumentation().runOnMainSync(() -> { open[0] = picker; new XPopup.Builder(activity).asCustom(picker).show(); });
                waitForShown(picker);
                getInstrumentation().runOnMainSync(() -> {
                    assertEquals(kind == SourceLibrary.LIVE ? live : wall, ((TextView) picker.findViewById(R.id.source_current_url)).getText().toString());
                    assertTrue(hasText(picker.findViewById(R.id.source_saved), "离线来源"));
                    assertTrue(hasText(picker.findViewById(R.id.source_saved), "https://offline.example.test/source"));
                    picker.dismiss();
                });
                Thread.sleep(350);
            }
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                if (open[0] != null) open[0].dismiss(); activity.finish();
                for (int i = 0; i < keys.length; i++) { if (existed[i]) Hawk.put(keys[i], originals[i]); else Hawk.delete(keys[i]); }
            });
        }
    }
    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private void waitForShown(BasePopupView popup) throws Exception {
        boolean[] shown = new boolean[1];
        for (int i = 0; i < 50; i++) {
            getInstrumentation().runOnMainSync(() -> shown[0] = popup.isShow() && popup.findViewById(R.id.source_title) != null || popup.isShow() && popup.findViewById(R.id.source_name) != null);
            if (shown[0]) return;
            Thread.sleep(100);
        }
        fail("来源弹窗未显示");
    }
}
