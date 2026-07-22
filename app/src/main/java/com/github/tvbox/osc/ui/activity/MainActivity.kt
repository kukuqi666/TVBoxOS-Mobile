package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Process
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.constant.IntentKey
import com.github.tvbox.osc.databinding.ActivityMainBinding
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.HomeFragment
import com.github.tvbox.osc.ui.fragment.MyFragment
import kotlin.system.exitProcess

class MainActivity : BaseVbActivity<ActivityMainBinding>() {

    companion object {
        const val EXTRA_START_DESTINATION = "main_start_destination"
    }

    private val fragments = listOf(HomeFragment(), MyFragment())
    var useCacheConfig = false
    private var exitTime = 0L

    override fun init() {

        useCacheConfig = intent.extras?.getBoolean(IntentKey.CACHE_CONFIG_CHANGED, false) ?: false

        mBinding.vp.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size

            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        mBinding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    mBinding.vp.setCurrentItem(0, false)
                    true
                }
                R.id.navigation_dashboard -> {
                    mBinding.vp.setCurrentItem(1, false)
                    true
                }
                R.id.navigation_live -> {
                    jumpActivity(LiveActivity::class.java)
                    false
                }
                R.id.navigation_subscription -> {
                    jumpActivity(SubscriptionActivity::class.java)
                    false
                }
                else -> false
            }
        }
        mBinding.vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                mBinding.bottomNav.selectedItemId = if (position == 0) {
                    R.id.navigation_home
                } else {
                    R.id.navigation_dashboard
                }
            }
        })
        openDestination(intent.getIntExtra(EXTRA_START_DESTINATION, R.id.navigation_home))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDestination(intent.getIntExtra(EXTRA_START_DESTINATION, R.id.navigation_home))
    }

    private fun openDestination(destination: Int) {
        when (destination) {
            R.id.navigation_dashboard -> mBinding.bottomNav.selectedItemId = R.id.navigation_dashboard
            R.id.navigation_live -> jumpActivity(LiveActivity::class.java)
            R.id.navigation_subscription -> jumpActivity(SubscriptionActivity::class.java)
            else -> mBinding.bottomNav.selectedItemId = R.id.navigation_home
        }
    }

    override fun onBackPressed() {
        if (mBinding.vp.currentItem == 1) {
            mBinding.vp.currentItem = 0
            return
        }
        val homeFragment = fragments[0] as HomeFragment
        if (!homeFragment.isAdded) { // 资源不足销毁重建时未挂载到activity时getChildFragmentManager会崩溃
            confirmExit()
            return
        }
        val childFragments = homeFragment.allFragments
        if (childFragments.isEmpty()) { //加载中(没有tab)
            confirmExit()
            return
        }
        val fragment: Fragment = childFragments[homeFragment.tabIndex]
        if (fragment is GridFragment) { // 首页数据源动态加载的tab
            if (!fragment.restoreView()) { // 有回退的view,先回退(AList等文件夹列表),没有可回退的,返到主页tab
                if (!homeFragment.scrollToFirstTab()) {
                    confirmExit()
                }
            }
        } else {
            confirmExit()
        }
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
