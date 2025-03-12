package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Handler
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.databinding.ActivitySplashBinding

class SplashActivity : BaseVbActivity<ActivitySplashBinding>() {
    override fun init() {
        App.getInstance().isNormalStart = true

        mBinding.root.postDelayed({
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        },500)

    }
}