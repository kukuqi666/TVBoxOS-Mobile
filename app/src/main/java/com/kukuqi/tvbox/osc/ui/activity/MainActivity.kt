package com.kukuqi.tvbox.osc.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.kukuqi.tvbox.osc.R
import com.kukuqi.tvbox.osc.base.BaseVbActivity
import com.kukuqi.tvbox.osc.constant.IntentKey
import com.kukuqi.tvbox.osc.databinding.ActivityMainBinding
import com.kukuqi.tvbox.osc.ui.fragment.GridFragment
import com.kukuqi.tvbox.osc.ui.fragment.HomeFragment
import com.kukuqi.tvbox.osc.ui.fragment.MyFragment
import com.kukuqi.tvbox.osc.ui.fragment.LocalVideoFragment
import com.kukuqi.tvbox.osc.util.HawkConfig
import com.kukuqi.tvbox.osc.update.UpdateCoordinator
import com.kukuqi.tvbox.osc.update.UpdateStore
import com.kukuqi.tvbox.osc.ui.dialog.AboutDialog
import com.google.android.material.snackbar.Snackbar
import com.lxj.xpopup.XPopup
import com.orhanobut.hawk.Hawk
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>() {
    companion object {
        const val EXTRA_START_DESTINATION = "main_start_destination"
        private var shownUpdateCode = 0L
    }
    private val fragments = listOf(HomeFragment(), LocalVideoFragment(), MyFragment())
    private var sourceUrl = Hawk.get(HawkConfig.API_URL, "")
    private val homeRec = Hawk.get(HawkConfig.HOME_REC, 0)
    private val dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0)
    private var exitTime = 0L
    var useCacheConfig = false
    private val updates by lazy { UpdateStore(this) }
    private val updateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> showReadyUpdate() }

    override fun init() {
        // Notification entry can start a fresh process; preserve its update destination.
        if (intent.getBooleanExtra(UpdateCoordinator.OPEN_UPDATES, false)) {
            com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = true
        }
        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false) ?: false
        mBinding.vp.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }
        mBinding.vp.isUserInputEnabled = false
        mBinding.bottomNav.root.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_home -> {
                    mBinding.vp.setCurrentItem(0, false)
                    if (homeRec != Hawk.get(HawkConfig.HOME_REC, 0) || dnsOpt != Hawk.get(HawkConfig.DOH_URL, 0)) {
                        intent.putExtra(IntentKey.CACHE_CONFIG_CHANGED, true)
                        intent.putExtra(EXTRA_START_DESTINATION, R.id.navigation_home)
                        recreate()
                    }
                    true
                }
                R.id.navigation_local -> { mBinding.vp.setCurrentItem(1, false); true }
                R.id.navigation_dashboard -> { mBinding.vp.setCurrentItem(2, false); true }
                R.id.navigation_live -> { jumpActivity(LiveActivity::class.java); false }
                else -> false
            }
        }
        mBinding.vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                mBinding.bottomNav.root.selectedItemId = when (position) {
                    0 -> R.id.navigation_home
                    1 -> R.id.navigation_local
                    else -> R.id.navigation_dashboard
                }
            }
        })
        openDestination(intent)
        UpdateCoordinator.start(this)
        openUpdatePanel(intent)
    }
    private fun openDestination(intent: Intent) {
        when (intent.getIntExtra(EXTRA_START_DESTINATION, R.id.navigation_home)) {
            R.id.navigation_dashboard -> mBinding.bottomNav.root.selectedItemId = R.id.navigation_dashboard
            R.id.navigation_local -> mBinding.bottomNav.root.selectedItemId = R.id.navigation_local
            R.id.navigation_live -> jumpActivity(LiveActivity::class.java)
            else -> mBinding.bottomNav.root.selectedItemId = R.id.navigation_home
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDestination(intent)
        openUpdatePanel(intent)
    }
    private fun openUpdatePanel(intent: Intent) {
        if (intent.getBooleanExtra(UpdateCoordinator.OPEN_UPDATES, false)) {
            intent.removeExtra(UpdateCoordinator.OPEN_UPDATES)
            mBinding.root.post { if (!isFinishing) XPopup.Builder(this).asCustom(AboutDialog(this)).show() }
        }
    }
    private fun showReadyUpdate() {
        val manifest = updates.manifest() ?: return
        if (!updates.hasReadyUpdate() || shownUpdateCode == manifest.versionCode || isFinishing) return
        shownUpdateCode = manifest.versionCode
        Snackbar.make(mBinding.root, "新版本 ${manifest.version} 已在后台下载完成", Snackbar.LENGTH_LONG)
            .setAnchorView(mBinding.bottomNav.root)
            .setAction("查看") { XPopup.Builder(this).asCustom(AboutDialog(this)).show() }.show()
    }
    override fun onResume() {
        super.onResume()
        updates.prefs.registerOnSharedPreferenceChangeListener(updateListener)
        mBinding.root.post { if (!isFinishing) showReadyUpdate() }
        // Replace the home configuration after a source is selected in Settings.
        if (sourceUrl != Hawk.get(HawkConfig.API_URL, "")) {
            intent.removeExtra(IntentKey.CACHE_CONFIG_CHANGED)
            intent.putExtra(EXTRA_START_DESTINATION, mBinding.bottomNav.root.selectedItemId)
            recreate()
        } else if (homeRec != Hawk.get(HawkConfig.HOME_REC, 0) || dnsOpt != Hawk.get(HawkConfig.DOH_URL, 0)) {
            intent.putExtra(IntentKey.CACHE_CONFIG_CHANGED, true)
            intent.putExtra(EXTRA_START_DESTINATION, mBinding.bottomNav.root.selectedItemId)
            recreate()
        }
    }
    override fun onPause() {
        updates.prefs.unregisterOnSharedPreferenceChangeListener(updateListener)
        super.onPause()
    }
    override fun onBackPressed() {
        if (mBinding.vp.currentItem != 0) { mBinding.vp.setCurrentItem(0, false); return }
        val home = supportFragmentManager.findFragmentByTag("f0") as? HomeFragment
        val child = home?.allFragments?.getOrNull(home.tabIndex)
        if (child is GridFragment) {
            if (!child.restoreView() && !home.scrollToFirstTab()) confirmExit()
        } else confirmExit()
    }
    private fun confirmExit() {
        if (System.currentTimeMillis() - exitTime > 2000) {
            ToastUtils.showShort("再按一次退出程序")
            exitTime = System.currentTimeMillis()
        } else {
            ActivityUtils.finishAllActivities(true)
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }
}
