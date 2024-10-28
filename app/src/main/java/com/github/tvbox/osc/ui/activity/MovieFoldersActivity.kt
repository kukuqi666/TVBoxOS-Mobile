package com.github.tvbox.osc.ui.activity

import android.os.Build
import android.os.Bundle
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VideoFolder
import com.github.tvbox.osc.bean.VideoInfo
import com.github.tvbox.osc.databinding.ActivityMovieFoldersBinding
import com.github.tvbox.osc.ui.adapter.FolderAdapter
import com.github.tvbox.osc.util.Utils
import java.util.function.Function
import java.util.stream.Collectors

class MovieFoldersActivity : BaseVbActivity<ActivityMovieFoldersBinding>() {

    private var mFolderAdapter = FolderAdapter()
    override fun init() {
        mBinding.rv.setAdapter(mFolderAdapter)
        mFolderAdapter.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { adapter, view, position ->
                val videoFolder = adapter.getItem(position) as VideoFolder?
                if (videoFolder != null) {
                    val bundle = Bundle()
                    bundle.putString("bucketDisplayName", videoFolder.name)
                    jumpActivity(VideoListActivity::class.java, bundle)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        groupVideos()
    }

    /**
     * 按文件夹名字分组视频
     */
    private fun groupVideos() {
        val videoList = Utils.getVideoList()
        val videoMap = videoList.stream()
            .collect(
                Collectors.groupingBy { obj: VideoInfo -> obj.bucketDisplayName }
            )
        val videoFolders: MutableList<VideoFolder> = ArrayList()
        videoMap.forEach { (key: String?, value: List<VideoInfo>?) ->
            val videoFolder = VideoFolder(key, value)
            videoFolders.add(videoFolder)
        }
        mFolderAdapter.setNewData(videoFolders)
    }
}