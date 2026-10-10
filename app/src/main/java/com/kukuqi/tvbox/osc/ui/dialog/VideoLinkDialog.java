package com.kukuqi.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.bean.VideoInfo;
import com.kukuqi.tvbox.osc.ui.activity.DetailActivity;
import com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity;
import java.util.Collections;

/** FongMi-style link/file entry, with the existing VOD and local players. */
public final class VideoLinkDialog extends DialogFragment {
    public static final String SOURCE = "__video_link__";
    private static final String TAG = "video-link";
    private TextInputEditText input;
    private TextInputLayout field;
    private final ActivityResultLauncher<Intent> chooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (!isAdded() || result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) return;
                Uri uri = data.getData();
                if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    try { requireContext().getContentResolver().takePersistableUriPermission(uri,
                            data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); }
                    catch (SecurityException ignored) { }
                }
                openFile(requireActivity(), uri);
                dismiss();
            });

    public static void show(Fragment fragment) {
        if (fragment.getChildFragmentManager().findFragmentByTag(TAG) == null) {
            new VideoLinkDialog().show(fragment.getChildFragmentManager(), TAG);
        }
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context themed = new androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.VideoLinkBaseTheme);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(themed, R.style.VideoLinkDialogTheme);
        View content = getLayoutInflater().cloneInContext(builder.getContext()).inflate(R.layout.dialog_video_link, null);
        input = content.findViewById(R.id.link_text);
        field = content.findViewById(R.id.link_input);
        field.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { chooser.launch(intent); }
            catch (android.content.ActivityNotFoundException e) { ToastUtils.showShort("未找到文件选择器"); }
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            playLink(); return true;
        });
        if (savedInstanceState == null) {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
                if (text != null && valid(text.toString().trim())) input.setText(text.toString().trim());
            }
        }
        return builder.setTitle("播放").setView(content).setNegativeButton("取消", null)
                .setPositiveButton("确定", null).create();
    }

    @Override public void onStart() {
        super.onStart();
        ((AlertDialog) requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> playLink());
    }

    private void playLink() {
        String url = input.getText() == null ? "" : input.getText().toString().trim();
        if (!valid(url)) { field.setError("请输入完整的 http 或 https 视频链接"); return; }
        requireActivity().startActivity(new Intent(requireActivity(), DetailActivity.class)
                .putExtra("id", url).putExtra("sourceKey", SOURCE));
        dismiss();
    }

    public static void openFile(Activity activity, Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (RuntimeException ignored) { }
        VideoInfo video = new VideoInfo();
        video.setPath(uri.toString());
        video.setDisplayName(name == null ? "本地视频" : name);
        activity.startActivity(new Intent(activity, LocalPlayActivity.class).putExtra("position", 0)
                .putExtra("videoList", GsonUtils.toJson(Collections.singletonList(video))));
    }

    private static boolean valid(String value) {
        Uri uri = Uri.parse(value);
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && !uri.getHost().isEmpty() && !value.matches(".*\\s+.*");
    }
}
