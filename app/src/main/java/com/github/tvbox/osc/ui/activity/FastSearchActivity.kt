package com.github.tvbox.osc.ui.activity

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.angcyo.tablayout.DslTabLayout
import com.angcyo.tablayout.DslTabLayoutConfig
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.catvod.crawler.JsLoader
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.AbsXml
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.databinding.ActivityFastSearchBinding
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.adapter.FastSearchAdapter
import com.github.tvbox.osc.ui.adapter.SearchWordAdapter
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog
import com.github.tvbox.osc.ui.dialog.SearchSuggestionsDialog
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.SearchHelper
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.interfaces.OnSelectListener
import com.lxj.xpopup.interfaces.SimpleCallback
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.orhanobut.hawk.Hawk
import com.zhy.view.flowlayout.FlowLayout
import com.zhy.view.flowlayout.TagAdapter
import okhttp3.Response
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class FastSearchActivity : BaseVbActivity<ActivityFastSearchBinding>(), TextWatcher {

    companion object {
        private var mCheckSources: HashMap<String, String>? = null
        fun setCheckedSourcesForSearch(checkedSources: HashMap<String, String>?) {
            mCheckSources = checkedSources
        }
    }

    private lateinit var sourceViewModel : SourceViewModel
    private var searchAdapter = FastSearchAdapter()
    private var searchAdapterFilter = FastSearchAdapter()
    private var searchTitle: String? = ""
    private var spNames = HashMap<String, String>()
    private var isFilterMode = false
    private var searchFilterKey: String? = "" // 过滤的key
    private var resultVods = HashMap<String, MutableList<Movie.Video>>()
    private var pauseRunnable: MutableList<Runnable>? = null
    private var mSearchSuggestionsDialog: SearchSuggestionsDialog? = null
    override fun init() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        initView()
        initData()
        //历史搜索
        initHistorySearch()
        // 热门搜索
        hotWords
    }

    override fun onResume() {
        super.onResume()
        if (pauseRunnable != null && pauseRunnable!!.size > 0) {
            searchExecutorService = Executors.newFixedThreadPool(10)
            allRunCount.set(pauseRunnable!!.size)
            for (runnable: Runnable? in pauseRunnable!!) {
                searchExecutorService!!.execute(runnable)
            }
            pauseRunnable!!.clear()
            pauseRunnable = null
        }
    }

    private fun initView() {
        mBinding.etSearch.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(mBinding.etSearch.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }
        mBinding.etSearch.addTextChangedListener(this)
        mBinding.ivFilter.setOnClickListener { filterSearchSource() }
        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.ivSearch.setOnClickListener {
            search(mBinding.etSearch.text.toString())
        }
        mBinding.tabLayout.configTabLayoutConfig {
            onSelectViewChange  = { _, selectViewList, _, _ ->
                    val tvItem: TextView = selectViewList.first() as TextView
                    filterResult(tvItem.text.toString())
                }
        }
        mBinding.mGridView.setHasFixedSize(true)
        mBinding.mGridView.setLayoutManager(LinearLayoutManager(this))
        mBinding.mGridView.adapter = searchAdapter
        searchAdapter.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapter.data[position]
            try {
                if (searchExecutorService != null) {
                    pauseRunnable = searchExecutorService!!.shutdownNow()
                    searchExecutorService = null
                    JsLoader.stopAll()
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
            val bundle = Bundle()
            bundle.putString("id", video.id)
            bundle.putString("sourceKey", video.sourceKey)
            jumpActivity(DetailActivity::class.java, bundle)
        }
        mBinding.mGridViewFilter.setLayoutManager(LinearLayoutManager(this))

        mBinding.mGridViewFilter.adapter = searchAdapterFilter
        searchAdapterFilter.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = searchAdapterFilter.data[position]
            if (video != null) {
                try {
                    if (searchExecutorService != null) {
                        pauseRunnable = searchExecutorService!!.shutdownNow()
                        searchExecutorService = null
                        JsLoader.stopAll()
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
                val bundle = Bundle()
                bundle.putString("id", video.id)
                bundle.putString("sourceKey", video.sourceKey)
                jumpActivity(DetailActivity::class.java, bundle)
            }
        }

        setLoadSir(mBinding.llLayout)
    }

    /**
     * 指定搜索源(过滤)
     */
    private fun filterSearchSource() {
        val allSourceBean = ApiConfig.get().sourceBeanList
        if (allSourceBean.isNotEmpty()) {
            val searchAbleSource: MutableList<SourceBean> = ArrayList()
            for (sourceBean: SourceBean in allSourceBean) {
                if (sourceBean.isSearchable) {
                    searchAbleSource.add(sourceBean)
                }
            }
            val mSearchCheckboxDialog = SearchCheckboxDialog(this@FastSearchActivity, searchAbleSource, mCheckSources)
            mSearchCheckboxDialog.show()
        }

    }

    private fun filterResult(spName: String) {
        if (spName === "全部显示") {
            mBinding.mGridView.visibility = View.VISIBLE
            mBinding.mGridViewFilter.visibility = View.GONE
            return
        }
        mBinding.mGridView.visibility = View.GONE
        mBinding.mGridViewFilter.visibility = View.VISIBLE
        val key = spNames[spName]
        if (key.isNullOrEmpty()) return
        if (searchFilterKey === key) return
        searchFilterKey = key
        val list: List<Movie.Video> = (resultVods[key])!!
        searchAdapterFilter.setNewData(list)
    }

    private fun initData() {
        mCheckSources = SearchHelper.getSourcesForSearch()
        if (intent != null && intent.hasExtra("title")) {
            val title = intent.getStringExtra("title")
            if (!TextUtils.isEmpty(title)) {
                showLoading()
                search(title)
            }
        }
    }

    private fun hideHotAndHistorySearch(isHide: Boolean) {
        if (isHide) {
            mBinding.llSearchSuggest.visibility = View.GONE
            mBinding.llSearchResult.visibility = View.VISIBLE
        } else {
            mBinding.llSearchSuggest.visibility = View.VISIBLE
            mBinding.llSearchResult.visibility = View.GONE
        }
    }

    private fun initHistorySearch() {
        val mSearchHistory: List<String> = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList())
        mBinding.llHistory.visibility = if (mSearchHistory.isNotEmpty()) View.VISIBLE else View.GONE
        mBinding.flHistory.adapter = object : TagAdapter<String?>(mSearchHistory) {
            override fun getView(parent: FlowLayout, position: Int, s: String?): View {
                val tv: TextView = LayoutInflater.from(this@FastSearchActivity).inflate(
                    R.layout.item_search_word_hot,
                    mBinding.flHistory, false
                ) as TextView
                tv.text = s
                return tv
            }
        }
        mBinding.flHistory.setOnTagClickListener { _: View?, position: Int, _: FlowLayout? ->
            search(mSearchHistory[position])
            true
        }
        findViewById<View>(R.id.iv_clear_history).setOnClickListener { view: View ->
            Hawk.put(HawkConfig.HISTORY_SEARCH, ArrayList<Any>())
            //FlowLayout及其adapter貌似没有清空数据的api,简单粗暴重置
            view.postDelayed({ initHistorySearch() }, 300)
        }
    }

    /**
     * 热门搜索
     */
    private val hotWords: Unit
        get() {
            // 加载热词
            OkGo.get<String>("https://node.video.qq.com/x/api/hot_search")
                .params("channdlId", "0")
                .params("_", System.currentTimeMillis())
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                        try {
                            val hots = ArrayList<String>()
                            val itemList =
                                JsonParser.parseString(response.body()).asJsonObject["data"].asJsonObject["mapResult"].asJsonObject["0"].asJsonObject["listInfo"].asJsonArray
                            //                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (ele: JsonElement in itemList) {
                                val obj = ele as JsonObject
                                hots.add(obj["title"].asString.trim { it <= ' ' }
                                    .replace("<|>|《|》|-".toRegex(), "").split(" ".toRegex())
                                    .dropLastWhile { it.isEmpty() }
                                    .toTypedArray()[0])
                            }
                            mBinding.flHot.adapter = object : TagAdapter<String?>(hots as List<String?>?) {
                                override fun getView(
                                    parent: FlowLayout,
                                    position: Int,
                                    s: String?
                                ): View {
                                    val tv: TextView =
                                        LayoutInflater.from(this@FastSearchActivity).inflate(
                                            R.layout.item_search_word_hot,
                                            mBinding.flHot, false
                                        ) as TextView
                                    tv.text = s
                                    return tv
                                }
                            }
                            mBinding.flHot.setOnTagClickListener { _: View?, position: Int, _: FlowLayout? ->
                                search(hots.get(position))
                                true
                            }
                        } catch (th: Throwable) {
                            th.printStackTrace()
                        }
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: Response): String {
                        return response.body()!!.string()
                    }
                })
        }

    /**
     * 联想搜索
     */
    private fun getSuggest(text: String) {
        // 加载热词
        OkGo.get<String>("https://suggest.video.iqiyi.com/?if=mobile&key=$text")
            .execute(object : AbsCallback<String?>() {
                override fun onSuccess(response: com.lzy.okgo.model.Response<String?>) {
                    val titles: MutableList<String> = ArrayList()
                    try {
                        val json = JsonParser.parseString(response.body()).asJsonObject
                        val datas = json["data"].asJsonArray
                        for (data: JsonElement in datas) {
                            val item = data as JsonObject
                            titles.add(item["name"].asString.trim { it <= ' ' })
                        }
                    } catch (th: Throwable) {
                        LogUtils.d(th.toString())
                    }
                    if (titles.isNotEmpty()) {
                        showSuggestDialog(titles)
                    }
                }

                @Throws(Throwable::class)
                override fun convertResponse(response: Response): String {
                    return response.body()!!.string()
                }
            })
    }

    private fun showSuggestDialog(list: List<String>) {
        if (mSearchSuggestionsDialog == null) {
            mSearchSuggestionsDialog =
                SearchSuggestionsDialog(this@FastSearchActivity, list
                ) { _, text ->
                    LogUtils.d("搜索:$text")
                    mSearchSuggestionsDialog!!.dismissWith { search(text) }
                }
            XPopup.Builder(this@FastSearchActivity)
                .atView(mBinding.etSearch)
                .notDismissWhenTouchInView(mBinding.etSearch)
                .isViewMode(true) //开启View实现
                .isRequestFocus(false) //不强制焦点
                .setPopupCallback(object : SimpleCallback() {
                    override fun onDismiss(popupView: BasePopupView) { // 弹窗关闭了就置空对象,下次重新new
                        super.onDismiss(popupView)
                        mSearchSuggestionsDialog = null
                    }
                })
                .asCustom(mSearchSuggestionsDialog)
                .show()
        } else { // 不为空说明弹窗为打开状态(关闭就置空了).直接刷新数据
            mSearchSuggestionsDialog!!.updateSuggestions(list)
        }
    }

    private fun saveSearchHistory(searchWord: String?) {
        if (!searchWord.isNullOrEmpty()) {
            val history = Hawk.get(HawkConfig.HISTORY_SEARCH, ArrayList<String?>())
            if (!history.contains(searchWord)) {
                history.add(0, searchWord)
            } else {
                history.remove(searchWord)
                history.add(0, searchWord)
            }
            if (history.size > 30) {
                history.removeAt(30)
            }
            Hawk.put(HawkConfig.HISTORY_SEARCH, history)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            val title = event.obj as String
            showLoading()
            search(title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(if (event.obj == null) null else event.obj as AbsXml)
            } catch (e: Exception) {
                searchData(null)
            }
        }
    }

    private fun search(title: String?) {
        if (title.isNullOrEmpty()) {
            ToastUtils.showShort("请输入搜索内容")
            return
        }

        //先移除监听,避免重新设置要搜索的文字触发搜索建议并弹窗
        mBinding.etSearch.removeTextChangedListener(this)
        mBinding.etSearch.setText(title)
        mBinding.etSearch.setSelection(title.length)
        mBinding.etSearch.addTextChangedListener(this)
        if (mSearchSuggestionsDialog != null && mSearchSuggestionsDialog!!.isShow) {
            mSearchSuggestionsDialog!!.dismiss()
        }
        if (!Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) { //无痕浏览不存搜索历史
            saveSearchHistory(title)
        }
        hideHotAndHistorySearch(true)
        KeyboardUtils.hideSoftInput(this)
        cancel()
        showLoading()
        searchTitle = title
        //fenci();
        mBinding.mGridView.visibility = View.INVISIBLE
        mBinding.mGridViewFilter.visibility = View.GONE
        searchAdapter!!.setNewData(ArrayList())
        searchAdapterFilter!!.setNewData(ArrayList())
        resultVods.clear()
        searchFilterKey = ""
        isFilterMode = false
        spNames.clear()
        mBinding.tabLayout.removeAllViews()
        searchResult()
    }

    private var searchExecutorService: ExecutorService? = null
    private val allRunCount = AtomicInteger(0)
    private fun getSiteTextView(text: String): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.gravity = Gravity.CENTER
        val params = DslTabLayout.LayoutParams(-2, -2)
        params.topMargin = 20
        params.bottomMargin = 20
        textView.setPadding(20, 10, 20, 10)
        textView.layoutParams = params
        return textView
    }

    private fun searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.stopAll()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        } finally {
            searchAdapter.setNewData(ArrayList())
            searchAdapterFilter.setNewData(ArrayList())
            allRunCount.set(0)
        }
        searchExecutorService = Executors.newFixedThreadPool(10)
        val searchRequestList: MutableList<SourceBean> = ArrayList()
        searchRequestList.addAll(ApiConfig.get().sourceBeanList)
        val home = ApiConfig.get().homeSourceBean
        searchRequestList.remove(home)
        searchRequestList.add(0, home)
        val siteKey = ArrayList<String>()
        mBinding.tabLayout.addView(getSiteTextView("全部显示"))
        mBinding.tabLayout.setCurrentItem(0, true, false)
        for (bean: SourceBean in searchRequestList) {
            if (!bean.isSearchable) {
                continue
            }
            if (mCheckSources != null && !mCheckSources!!.containsKey(bean.key)) {
                continue
            }
            siteKey.add(bean.key)
            spNames[bean.name] = bean.key
            allRunCount.incrementAndGet()
        }
        for (key: String in siteKey) {
            searchExecutorService!!.execute {
                try {
                    sourceViewModel.getSearch(key, searchTitle)
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 添加到最后面并返回最后一个key
     * @param key
     * @return
     */
    private fun addWordAdapterIfNeed(key: String): String {
        try {
            var name = ""
            for (n: String in spNames.keys) {
                if ((spNames[n] == key)) {
                    name = n
                }
            }
            if ((name == "")) return key
            for (i in 0 until mBinding.tabLayout.childCount) {
                val item = mBinding.tabLayout.getChildAt(i) as TextView
                if ((name == item.text.toString())) {
                    return key
                }
            }
            mBinding.tabLayout.addView(getSiteTextView(name))
            return key
        } catch (e: Exception) {
            return key
        }
    }

    private fun matchSearchResult(name: String, searchTitle: String?): Boolean {
        var searchTitle = searchTitle
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false
        searchTitle = searchTitle!!.trim { it <= ' ' }
        val arr = searchTitle.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var matchNum = 0
        for (one: String in arr) {
            if (name.contains(one)) matchNum++
        }
        return if (matchNum == arr.size) true else false
    }

    private fun searchData(absXml: AbsXml?) {
        var lastSourceKey = ""
        if ((absXml != null) && (absXml.movie != null) && (absXml.movie.videoList != null) && (absXml.movie.videoList.size > 0)) {
            val data: MutableList<Movie.Video> = ArrayList()
            for (video: Movie.Video in absXml.movie.videoList) {
                if (!matchSearchResult(video.name, searchTitle)) continue
                data.add(video)
                if (!resultVods.containsKey(video.sourceKey)) {
                    resultVods[video.sourceKey] = ArrayList()
                }
                resultVods[video.sourceKey]!!.add(video)
                if (video.sourceKey !== lastSourceKey) { // 添加到最后面并记录最后一个key用于下次判断
                    lastSourceKey = addWordAdapterIfNeed(video.sourceKey)
                }
            }
            if (searchAdapter.data.size > 0) {
                searchAdapter.addData(data)
            } else {
                showSuccess()
                if (!isFilterMode) mBinding.mGridView.visibility = View.VISIBLE
                searchAdapter.setNewData(data)
            }
        }
        val count = allRunCount.decrementAndGet()
        if (count <= 0) {
            if (searchAdapter.data.size <= 0) {
                showEmpty()
            }
            cancel()
        }
    }

    private fun cancel() {
        OkGo.getInstance().cancelTag("search")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        try {
            if (searchExecutorService != null) {
                searchExecutorService!!.shutdownNow()
                searchExecutorService = null
                JsLoader.load()
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
    override fun afterTextChanged(editable: Editable) {
        val text = editable.toString()
        if (TextUtils.isEmpty(text)) {
            mSearchSuggestionsDialog?.dismiss()
            hideHotAndHistorySearch(false)
        } else {
            getSuggest(text)
        }
    }
}