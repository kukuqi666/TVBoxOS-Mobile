package com.kukuqi.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.util.SourceLibrary;
import com.lxj.xpopup.core.CenterPopupView;

public class SourceAddDialog extends CenterPopupView {
    public interface Result { void saved(SourceLibrary.Entry entry, boolean use); }
    private final int kind;
    private final Result result;
    private final SourceLibrary.Entry editing;
    private EditText name, url;
    public SourceAddDialog(@NonNull Context context, int kind, Result result) { this(context, kind, null, result); }
    public SourceAddDialog(@NonNull Context context, int kind, SourceLibrary.Entry editing, Result result) {
        super(context); this.kind = kind; this.editing = editing; this.result = result;
    }
    @Override protected int getImplLayoutId() { return R.layout.dialog_source_add; }
    @Override protected int getMaxHeight() { return (int) (getResources().getDisplayMetrics().heightPixels * 0.86f); }
    @Override protected int getPopupWidth() {
        return Math.min((int) (520 * getResources().getDisplayMetrics().density), (int) (getResources().getDisplayMetrics().widthPixels * 0.90f));
    }
    @Override protected void onCreate() {
        super.onCreate();
        ((TextView) findViewById(R.id.source_add_title)).setText((editing == null ? "添加" : "编辑") + (kind == SourceLibrary.LIVE ? "直播源" : "壁纸源"));
        name = findViewById(R.id.source_name); url = findViewById(R.id.source_url);
        if (editing != null) { name.setText(editing.name); url.setText(editing.url); }
        ((TextView) findViewById(R.id.source_add_hint)).setText(kind == SourceLibrary.LIVE ? "支持组合订阅、直播 JSON、M3U、TXT" : "支持图片地址、随机图片接口或包含壁纸的订阅");
        if (editing != null && SourceLibrary.isBuiltIn(kind, editing.url)) {
            url.setEnabled(false);
            ((TextView) findViewById(R.id.source_add_title)).setText("重命名");
            ((TextView) findViewById(R.id.source_add_hint)).setText("内置来源的地址保留，可修改名称");
        }
        findViewById(R.id.source_cancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.source_save).setOnClickListener(v -> save(false));
        findViewById(R.id.source_apply).setOnClickListener(v -> save(true));
    }
    private void save(boolean use) {
        String address = url.getText().toString().trim();
        boolean localEdit = editing != null && address.equals(editing.url) && !(address.startsWith("https://") || address.startsWith("http://"));
        if (!SourceLibrary.valid(address) || (!localEdit && !(address.startsWith("https://") || address.startsWith("http://")))) {
            url.setError("请输入完整的 http:// 或 https:// 地址"); return;
        }
        String label = name.getText().toString().trim();
        if (label.isEmpty()) label = SourceLibrary.defaultName(address);
        if (editing == null) SourceLibrary.save(kind, label, address);
        else SourceLibrary.replace(kind, editing.url, label, address);
        SourceLibrary.Entry entry = new SourceLibrary.Entry(label, address);
        dismissWith(() -> result.saved(entry, use));
    }
}
