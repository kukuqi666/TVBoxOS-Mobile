package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.player.PlaybackSpeed;

public final class PlaybackSpeedDialog {
    public interface Listener { void select(float speed); }

    public static AlertDialog show(Context context, float speed, Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, pad / 2);
        TextView value = new TextView(context);
        value.setId(R.id.speed_value);
        value.setTextSize(22);
        value.setGravity(android.view.Gravity.CENTER);
        content.addView(value);
        SeekBar slider = new SeekBar(context);
        slider.setId(R.id.speed_slider);
        slider.setMax(49);
        slider.setContentDescription("播放倍速，0.1 至 5 倍");
        content.addView(slider, new LinearLayout.LayoutParams(-1, pad * 3));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                value.setText(PlaybackSpeed.format((progress + 1) / 10f));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        float[] presets = {0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f, 3f, 5f};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            for (int col = 0; col < 4; col++) {
                float preset = presets[row * 4 + col];
                TextView button = new TextView(context);
                button.setText(PlaybackSpeed.format(preset));
                button.setTextColor(context.getResources().getColor(R.color.text_foreground));
                button.setGravity(android.view.Gravity.CENTER);
                button.setBackgroundResource(R.drawable.bg_r_common_stroke_primary);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, pad * 2, 1);
                params.setMargins(pad / 5, pad / 5, pad / 5, pad / 5);
                line.addView(button, params);
                button.setOnClickListener(v -> slider.setProgress(Math.round(preset * 10) - 1));
            }
            content.addView(line);
        }
        slider.setProgress(Math.round(PlaybackSpeed.clamp(speed) * 10) - 1);
        value.setText(PlaybackSpeed.format((slider.getProgress() + 1) / 10f));
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("播放倍速")
                .setView(content).setNegativeButton("取消", null)
                .setPositiveButton("应用", (d, which) -> listener.select((slider.getProgress() + 1) / 10f)).create();
        dialog.show();
        return dialog;
    }
}
