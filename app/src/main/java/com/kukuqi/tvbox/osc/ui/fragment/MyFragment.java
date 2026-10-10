package com.kukuqi.tvbox.osc.ui.fragment;

import android.content.Intent;
import com.kukuqi.tvbox.osc.base.BaseVbFragment;
import com.kukuqi.tvbox.osc.databinding.FragmentMyBinding;
import com.kukuqi.tvbox.osc.ui.dialog.AboutDialog;
import com.kukuqi.tvbox.osc.ui.settings.SettingsController;
import com.lxj.xpopup.XPopup;

public class MyFragment extends BaseVbFragment<FragmentMyBinding> {
    private SettingsController settings;

    @Override protected void init() {
        settings = new SettingsController(
                (com.kukuqi.tvbox.osc.base.BaseActivity) requireActivity(), mBinding.settings,
                (intent, code) -> startActivityForResult(intent, code));
        settings.init();
        mBinding.llAbout.setOnClickListener(v -> new XPopup.Builder(mActivity)
                .asCustom(new AboutDialog(mActivity)).show());
    }

    @Override public void onResume() {
        super.onResume();
        if (settings != null) settings.refresh();
    }

    @Override public void onDestroyView() {
        if (settings != null) { settings.dispose(); settings = null; }
        super.onDestroyView();
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (settings != null) settings.onActivityResult(requestCode, resultCode, data);
    }
}
