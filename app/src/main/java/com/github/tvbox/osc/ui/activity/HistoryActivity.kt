package com.github.tvbox.osc.ui.activity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.databinding.ActivityHistoryBinding
import com.github.tvbox.osc.ui.adapter.HistoryAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.Utils
import com.lxj.xpopup.XPopup
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : BaseVbActivity<ActivityHistoryBinding>() {
    private var historyAdapter: HistoryAdapter? = null
    override fun init() {
        initView()
        initData()
    }

    private fun initView() {
        setLoadSir(mBinding.mGridView)

        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.setLayoutManager(GridLayoutManager(this, 3))
        historyAdapter = HistoryAdapter()
        mBinding.mGridView.setAdapter(historyAdapter)

        historyAdapter!!.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                historyAdapter!!.remove(position)
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo)
                if (historyAdapter!!.data.isEmpty()) {
                    mBinding.topTip.visibility = View.GONE
                }
                true
            }

        mBinding.titleBar.rightView.setOnClickListener { view: View? ->
            XPopup.Builder(this)
                .isDarkTheme(Utils.isDarkTheme())
                .asConfirm("提示", "确定清空?") {

                    showLoadingDialog()
                    lifecycleScope.launch(Dispatchers.IO) {
                        RoomDataManger.deleteVodRecordAll()
                        // 在主线程更新数据
                        withContext(Dispatchers.Main) {
                            dismissLoadingDialog()
                            historyAdapter!!.setNewData(ArrayList())
                            mBinding.topTip.visibility = View.GONE
                            showEmpty()
                        }
                    }

                }.show()
        }

        historyAdapter!!.onItemClickListener =
            BaseQuickAdapter.OnItemClickListener { _: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                FastClickCheckUtil.check(view)
                val vodInfo = historyAdapter!!.data[position]
                val bundle = Bundle()
                bundle.putString("id", vodInfo.id)
                bundle.putString("sourceKey", vodInfo.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }
    }

    private fun initData() {

        lifecycleScope.launch(Dispatchers.IO) {
            val allVodRecord = RoomDataManger.getAllVodRecord(100)
            val vodInfoList: MutableList<VodInfo> = ArrayList()
            for (vodInfo in allVodRecord) {
                if (vodInfo.playNote != null && vodInfo.playNote.isNotEmpty()) vodInfo.note =
                    vodInfo.playNote
                vodInfoList.add(vodInfo)
            }

            withContext(Dispatchers.Main) {
                historyAdapter!!.setNewData(vodInfoList)
                if (vodInfoList.isNotEmpty()) {
                    showSuccess()
                    mBinding.topTip.visibility = View.VISIBLE
                } else {
                    showEmpty()
                }
            }
        }
    }
}