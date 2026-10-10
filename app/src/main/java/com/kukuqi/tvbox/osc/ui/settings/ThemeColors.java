package com.kukuqi.tvbox.osc.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.orhanobut.hawk.Hawk;

public final class ThemeColors {
    public static final String KEY = "theme_color";
    public static final String[] NAMES = {"关闭", "自动", "蓝色", "绿色", "紫色", "橙色"};
    public static int color(Context context) {
        int value = Hawk.get(KEY, 0);
        if (value == 1 && Build.VERSION.SDK_INT >= 31) return context.getColor(android.R.color.system_accent1_500);
        if (value == 3) return 0xff008577;
        if (value == 4) return 0xff8555c7;
        if (value == 5) return 0xffc86418;
        return 0xff567df4;
    }
    public static void apply(View root) {
        int color = color(root.getContext()), inactive = com.kukuqi.tvbox.osc.util.Utils.isDarkTheme() ? 0xffbbbbbb : 0xff666666;
        ColorStateList states = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{color, inactive});
        if (root instanceof BottomNavigationView) {
            ((BottomNavigationView) root).setItemIconTintList(states); ((BottomNavigationView) root).setItemTextColor(states);
        } else if (root instanceof SwitchMaterial) ((SwitchMaterial) root).setThumbTintList(states);
        else if (root instanceof MaterialButton) ((MaterialButton) root).setBackgroundTintList(ColorStateList.valueOf(color));
        else if (root instanceof com.google.android.material.floatingactionbutton.FloatingActionButton)
            ((com.google.android.material.floatingactionbutton.FloatingActionButton) root).setBackgroundTintList(ColorStateList.valueOf(color));
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) apply(((ViewGroup) root).getChildAt(i));
    }
}
