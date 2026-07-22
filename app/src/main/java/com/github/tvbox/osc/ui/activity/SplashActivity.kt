package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Build
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.databinding.ActivitySplashBinding
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions

class SplashActivity : BaseVbActivity<ActivitySplashBinding>() {
    override fun init() {
        App.getInstance().isNormalStart = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !XXPermissions.isGranted(this, POST_NOTIFICATIONS_PERMISSION)
        ) {
            XXPermissions.with(this)
                .permission(POST_NOTIFICATIONS_PERMISSION)
                .request(object : OnPermissionCallback {
                    override fun onGranted(permissions: List<String>, all: Boolean) {
                        openMain()
                    }

                    override fun onDenied(permissions: List<String>, never: Boolean) {
                        openMain()
                    }
                })
        } else {
            openMain()
        }
    }

    private fun openMain() {
        mBinding.root.postDelayed({
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 500)
    }

    private companion object {
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
