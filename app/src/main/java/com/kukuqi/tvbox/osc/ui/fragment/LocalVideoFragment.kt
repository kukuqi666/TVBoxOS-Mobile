package com.kukuqi.tvbox.osc.ui.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ToastUtils
import com.kukuqi.tvbox.osc.base.BaseVbFragment
import com.kukuqi.tvbox.osc.bean.VideoFolder
import com.kukuqi.tvbox.osc.databinding.FragmentLocalVideoBinding
import com.kukuqi.tvbox.osc.ui.activity.VideoListActivity
import com.kukuqi.tvbox.osc.ui.adapter.FolderAdapter
import com.kukuqi.tvbox.osc.util.Utils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalVideoFragment : BaseVbFragment<FragmentLocalVideoBinding>() {
    private val folders = FolderAdapter()
    private var scan: Job? = null
    private val videoPermission get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_VIDEO else Permission.READ_EXTERNAL_STORAGE

    override fun init() {
        mBinding.localFolders.adapter = folders
        folders.setOnItemClickListener { adapter, _, position ->
            val folder = adapter.getItem(position) as? VideoFolder ?: return@setOnItemClickListener
            jumpActivity(VideoListActivity::class.java, Bundle().apply { putString("bucketDisplayName", folder.name) })
        }
        mBinding.localPermission.setOnClickListener { requestVideoAccess() }
    }

    override fun onResume() {
        super.onResume()
        refreshFolders()
    }

    private fun refreshFolders() {
        scan?.cancel()
        if (!XXPermissions.isGranted(requireContext(), videoPermission)) {
            folders.setNewData(emptyList())
            mBinding.localStatus.text = "允许访问视频后，即可查看本地视频"
            mBinding.localEmptyPanel.visibility = View.VISIBLE
            mBinding.localPermission.visibility = View.VISIBLE
            return
        }
        mBinding.localPermission.visibility = View.GONE
        mBinding.localStatus.text = "正在读取本地视频…"
        mBinding.localEmptyPanel.visibility = if (folders.data.isEmpty()) View.VISIBLE else View.GONE
        scan = viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                Utils.getVideoList().groupBy { it.bucketDisplayName ?: "未分类" }
                    .map { (name, videos) -> VideoFolder(name, videos) }.sortedBy { it.name }
            }
            folders.setNewData(items)
            mBinding.localStatus.text = "没有找到本地视频"
            mBinding.localEmptyPanel.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun requestVideoAccess() {
        XXPermissions.with(this).permission(videoPermission).request(object : OnPermissionCallback {
            override fun onGranted(permissions: List<String>, all: Boolean) {
                if (all && view != null) refreshFolders()
            }
            override fun onDenied(permissions: List<String>, never: Boolean) {
                if (!isAdded) return
                if (never) {
                    ToastUtils.showShort("视频权限已被拒绝，请在系统设置中开启")
                    XXPermissions.startPermissionActivity(requireActivity(), permissions)
                } else ToastUtils.showShort("未获得视频权限，可点击授权重试")
            }
        })
    }

    override fun onDestroyView() {
        scan?.cancel()
        mBinding.localFolders.adapter = null
        super.onDestroyView()
    }
}
