package com.github.tvbox.osc.ui.fragment;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ClipboardUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.base.BaseVbFragment;
import com.github.tvbox.osc.databinding.FragmentMyBinding;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LocalPlayActivity;
import com.github.tvbox.osc.ui.activity.MovieFoldersActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.WallpaperDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.Utils;
import com.github.tvbox.osc.util.WallpaperManager;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

import java.util.Arrays;
import java.util.List;

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class MyFragment extends BaseVbFragment<FragmentMyBinding> {

    private static final int REQUEST_IMPORT_WALLPAPER = 9201;


    @Override
    protected void init() {
        mBinding.tvVersion.setText("v"+ AppUtils.getAppVersionName());

        mBinding.addrPlay.setOnClickListener(v ->{
            new XPopup.Builder(getContext())
                    .asInputConfirm("点播", "", isPush(ClipboardUtils.getText().toString())?ClipboardUtils.getText():"", "影视地址", text -> {
                        if (!TextUtils.isEmpty(text)){
                            Intent newIntent = new Intent(mContext, DetailActivity.class);
                            newIntent.putExtra("id", text);
                            newIntent.putExtra("sourceKey", "push_agent");
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }, null, R.layout.dialog_input).show();
        });
        mBinding.tvSetting.setOnClickListener(v -> jumpActivity(SettingActivity.class));

        mBinding.tvHistory.setOnClickListener(v -> jumpActivity(HistoryActivity.class));

        mBinding.tvFavorite.setOnClickListener(v -> jumpActivity(CollectActivity.class));

        mBinding.tvLocal.setOnClickListener(v -> {
            if (!hasVideoAccess()) {
                showPermissionTipPopup();
            } else {
                jumpActivity(MovieFoldersActivity.class);
            }
        });

        mBinding.tvWallpaper.setOnClickListener(v -> {
            new XPopup.Builder(mActivity)
                    .asCustom(new WallpaperDialog(mActivity, this::pickWallpaper))
                    .show();
        });

        mBinding.llAbout.setOnClickListener(v -> {
            new XPopup.Builder(mActivity)
                    .asCustom(new AboutDialog(mActivity))
                    .show();
        });
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMPORT_WALLPAPER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_WALLPAPER || resultCode != android.app.Activity.RESULT_OK
                || data == null || data.getData() == null) return;
        String value = WallpaperManager.get().importWallpaper(data.getData());
        if (value.isEmpty()) {
            ToastUtils.showShort("图片导入失败，请选择有效图片");
        } else {
            WallpaperManager.get().applyToActivity(mActivity);
            ToastUtils.showShort("壁纸已导入并应用");
        }
    }

    private void showPermissionTipPopup(){
        new XPopup.Builder(mActivity)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示","为了读取本地视频,需要访问设备中的视频媒体", () -> {
                    getPermission();
                }).show();
    }

    private boolean hasVideoAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return XXPermissions.isGranted(mContext, Manifest.permission.READ_MEDIA_VIDEO);
        }
        return XXPermissions.isGranted(mContext, Permission.READ_EXTERNAL_STORAGE);
    }

    private void getPermission(){
        XXPermissions.with(this)
                .permission(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Manifest.permission.READ_MEDIA_VIDEO
                        : Permission.READ_EXTERNAL_STORAGE)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        if (all) {
                            jumpActivity(MovieFoldersActivity.class);
                        }else {
                            ToastUtils.showLong("部分权限未正常授予,请授权");
                        }
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        if (never) {
                            ToastUtils.showLong("视频媒体权限被永久拒绝，请手动授权");
                            XXPermissions.startPermissionActivity(mActivity, permissions);
                        } else {
                            ToastUtils.showShort("获取权限失败");
                            showPermissionTipPopup();
                        }
                    }
                });
    }

    private boolean isPush(String text) {
        return !TextUtils.isEmpty(text) && Arrays.asList("smb", "http", "https", "thunder", "magnet", "ed2k", "mitv", "jianpian").contains(Uri.parse(text).getScheme());
    }

}
