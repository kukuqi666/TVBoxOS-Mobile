package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ColorUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.SPUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.constant.CacheConst
import com.github.tvbox.osc.databinding.ActivityMovieFoldersBinding
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.adapter.LocalVideoAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.Utils
import com.lxj.xpopup.XPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.stream.Collectors

class VideoListActivity : BaseVbActivity<ActivityMovieFoldersBinding>() {
    private var mBucketDisplayName = ""
    private var mLocalVideoAdapter = LocalVideoAdapter()
    private var mSelectedCount = 0
    override fun init() {

        mBucketDisplayName = intent.extras?.getString("bucketDisplayName")?:""

        mBinding.titleBar.setTitle(mBucketDisplayName)
        mBinding.rv.setAdapter(mLocalVideoAdapter)
        mLocalVideoAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { adapter: BaseQuickAdapter<*, *>, view: View?, position: Int ->
                val videoInfo = adapter.getItem(position) as VideoInfo?
                if (mLocalVideoAdapter.isSelectMode) {
                    videoInfo!!.isChecked = !videoInfo.isChecked
                    mLocalVideoAdapter.notifyDataSetChanged()
                } else {
                    val bundle = Bundle()
                    //                    bundle.putString("path",videoInfo.getPath());
                    bundle.putString("videoList", GsonUtils.toJson(mLocalVideoAdapter.data))
                    bundle.putInt("position", position)
                    jumpActivity(LocalPlayActivity::class.java, bundle)
                }
            }
        mLocalVideoAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { adapter: BaseQuickAdapter<*, *>, view: View?, position: Int ->
                toggleListSelectMode(true)
                val videoInfo = adapter.getItem(position) as VideoInfo?
                videoInfo!!.isChecked = true
                mLocalVideoAdapter!!.notifyDataSetChanged()
                true
            }

        mBinding.tvAllCheck.setOnClickListener { view: View? ->  //全选
            FastClickCheckUtil.check(view)
            for (item in mLocalVideoAdapter.data) {
                item.isChecked = true
            }
            mLocalVideoAdapter!!.notifyDataSetChanged()
        }

        mBinding.tvCancelAllChecked.setOnClickListener { view: View? ->  //取消全选
            FastClickCheckUtil.check(view)
            cancelAll()
        }

        mLocalVideoAdapter.setOnSelectCountListener { count: Int ->
            mSelectedCount = count
            if (mSelectedCount > 0) {
                mBinding.tvDelete.isEnabled = true
                mBinding.tvDelete.setTextColor(ColorUtils.getColor(R.color.colorPrimary))
            } else {
                mBinding.tvDelete.isEnabled = false
                mBinding.tvDelete.setTextColor(ColorUtils.getColor(R.color.disable_text))
            }
        }

        mBinding.tvDelete.setOnClickListener { view: View? ->
            FastClickCheckUtil.check(view)
            XPopup.Builder(this)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示", "确定删除所选视频吗？") {
                    showLoadingDialog()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val data = mLocalVideoAdapter.data
                        val deleteList: MutableList<VideoInfo> = ArrayList()
                        for (item in data) {
                            if (item.isChecked) {
                                deleteList.add(item)
                                if (FileUtils.delete(item.path)) {
                                    // 删除缓存的影片时长、进度
                                    SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).remove(item.path)
                                    SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).remove(item.path)
                                    // 文件增删需要通知系统扫描,否则删除文件后还能查出来
                                    // 这个工具类直接传文件路径不知道为啥通知失败,手动获取一下
                                    FileUtils.notifySystemToScan(FileUtils.getDirName(item.path))
                                }
                            }
                        }
                        data.removeAll(deleteList)

                        withContext(Dispatchers.Main){
                            dismissLoadingDialog()
                            mLocalVideoAdapter.notifyDataSetChanged()
                            toggleListSelectMode(false)
                        }
                    }
                }.show()
        }
    }

    private fun toggleListSelectMode(open: Boolean) {
        mLocalVideoAdapter.setSelectMode(open)
        mBinding.llMenu.visibility = if (open) View.VISIBLE else View.GONE
        if (!open) { // 开启时设置了当前item为选中状态已经刷新了.所以只在关闭刷新列表
            mLocalVideoAdapter.notifyDataSetChanged()
        }
    }

    private fun cancelAll() {
        for (item in mLocalVideoAdapter.data) {
            item.isChecked = false
        }
        mLocalVideoAdapter.notifyDataSetChanged()
    }

    override fun refresh(event: RefreshEvent) {
        Handler().postDelayed({ groupVideos() }, 1000)
    }

    override fun onResume() {
        super.onResume()
        groupVideos()
    }

    /**
     * 根据文件夹名字筛选视频
     */
    private fun groupVideos() {
        val videoList = Utils.getVideoList()
        val collect = videoList.stream()
            .filter { videoInfo: VideoInfo -> videoInfo.bucketDisplayName == mBucketDisplayName }
            .collect(Collectors.toList())
        mLocalVideoAdapter.setNewData(collect)
    }

    override fun onBackPressed() {
        if (mLocalVideoAdapter.isSelectMode) {
            if (mSelectedCount > 0) {
                cancelAll()
            } else {
                toggleListSelectMode(false)
            }
        } else {
            super.onBackPressed()
        }
    }
}