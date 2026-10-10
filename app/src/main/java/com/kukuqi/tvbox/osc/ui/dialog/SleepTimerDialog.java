package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.kukuqi.tvbox.osc.player.SleepTimer;

public final class SleepTimerDialog {
    public static void show(Context context) {
        SleepTimer timer = SleepTimer.get();
        String[] choices = {"15 分钟后停止", "30 分钟后停止", "60 分钟后停止", "90 分钟后停止", "自定义时间", "延长 5 分钟", "取消定时"};
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(timer.description())
                .setItems(choices, (d, index) -> {
                    if (index < 4) timer.setMinutes(new int[]{15, 30, 60, 90}[index]);
                    else if (index == 4) custom(context);
                    else if (index == 5) timer.extend();
                    else timer.cancel();
                }).setNegativeButton("关闭", null).create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable update = new Runnable() {
            @Override public void run() { dialog.setTitle(timer.description()); handler.postDelayed(this, 1000); }
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(update));
        dialog.show(); handler.post(update);
    }
    private static void custom(Context context) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("1–180 分钟");
        input.setSingleLine();
        int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("定时停止播放")
                .setView(input).setPositiveButton("开始", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int minutes = 0;
            try { minutes = Integer.parseInt(input.getText().toString().trim()); } catch (NumberFormatException ignored) {}
            if (minutes < 1 || minutes > 180) { input.setError("请输入 1–180 分钟"); return; }
            SleepTimer.get().setMinutes(minutes); dialog.dismiss();
        }));
        dialog.show();
    }
}
