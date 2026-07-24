package com.github.tvbox.osc.ui.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.View
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.bean.Source
import com.github.tvbox.osc.bean.Subscription
import com.github.tvbox.osc.databinding.ActivitySubscriptionBinding
import com.github.tvbox.osc.ui.adapter.SubscriptionAdapter
import com.github.tvbox.osc.ui.dialog.ChooseSourceDialog
import com.github.tvbox.osc.ui.dialog.SubsTipDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog
import com.github.tvbox.osc.ui.dialog.SubsciptionDialog.OnSubsciptionListener
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.Utils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lxj.xpopup.XPopup
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import java.util.Locale
import java.util.function.Consumer

class SubscriptionActivity : BaseVbActivity<ActivitySubscriptionBinding>() {

    companion object {
        private const val CLEANUP_SUBSCRIPTIONS_TAG = "cleanup_subscriptions"
        private const val REQUEST_OPEN_SUBSCRIPTION_FILE = 9001
    }

    private var mBeforeUrl = Hawk.get(HawkConfig.API_URL, "")
    private var mSelectedUrl = ""
    private var mSubscriptions: MutableList<Subscription> = Hawk.get(HawkConfig.SUBSCRIPTIONS, ArrayList())
    private var mSubscriptionAdapter = SubscriptionAdapter()
    private val mSources: MutableList<Source> = ArrayList()
    private var destinationAfterFinish: Int? = null
    private var pendingLocalChecked = false

    override fun init() {

        initBottomNavigation()
        mBinding.rv.setAdapter(mSubscriptionAdapter)
        mSubscriptions.forEach(Consumer { item: Subscription ->
            if (item.isChecked) {
                mSelectedUrl = item.url
            }
        })

        mSubscriptionAdapter.setNewData(mSubscriptions)
        mBinding.ivUseTip.setOnClickListener {
            XPopup.Builder(this)
                .asCustom(SubsTipDialog(this))
                .show()
        }
        mBinding.ivCleanupSubscriptions.setOnClickListener {
            confirmSubscriptionCleanup()
        }

        mBinding.titleBar.rightView.setOnClickListener {//添加订阅
            XPopup.Builder(this)
                .autoFocusEditText(false)
                .asCustom(
                    SubsciptionDialog(
                        this,
                        "订阅: " + (mSubscriptions.size + 1),
                        object : OnSubsciptionListener {
                            override fun onConfirm(
                                name: String,
                                url: String,
                                checked: Boolean
                            ) { //只有addSub2List用到,看注释,单线路才生效,其余方法仅作为参数继续传递
                                if (hasDuplicateSubscription(url)) {
                                    ToastUtils.showLong("订阅地址已存在")
                                    return
                                }
                                addSubscription(name, url, checked)
                            }

                            override fun chooseLocal(checked: Boolean) { //本地导入
                                pickFile(checked)
                            }
                        })
                ).show()
        }

        mSubscriptionAdapter.setOnItemChildClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            LogUtils.d("删除订阅")
            if (view.id == R.id.iv_del) {
                val subscription = mSubscriptions[position]
                if (subscription.isBuiltIn) {
                    ToastUtils.showShort("内置订阅不能删除")
                    return@setOnItemChildClickListener
                }
                if (subscription.isChecked || subscription.url == mSelectedUrl) {
                    ToastUtils.showShort("不能删除当前使用的订阅")
                    return@setOnItemChildClickListener
                }
                XPopup.Builder(this@SubscriptionActivity)
                    .asConfirm("删除订阅", "确定删除订阅吗？") {
                        mSubscriptions.removeAt(position)
                        //删除/选择只刷新,不触发重新排序
                        mSubscriptionAdapter.notifyDataSetChanged()
                    }.show()
            }
        }

        mSubscriptionAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->  //选择订阅
            for (i in mSubscriptions.indices) {
                val subscription = mSubscriptions[i]
                if (i == position) {
                    subscription.setChecked(true)
                    mSelectedUrl = subscription.url
                } else {
                    subscription.setChecked(false)
                }
            }
            //删除/选择只刷新,不触发重新排序
            mSubscriptionAdapter.notifyDataSetChanged()
        }

        mSubscriptionAdapter.onItemLongClickListener =
            BaseQuickAdapter.OnItemLongClickListener { adapter: BaseQuickAdapter<*, *>?, view: View, position: Int ->
                val item = mSubscriptions[position]
                XPopup.Builder(this)
                    .atView(view.findViewById(R.id.tv_name))
                    .hasShadowBg(false)
                    .asAttachList(
                        arrayOf(
                            if (item.isTop) "取消置顶" else "置顶",
                            "重命名",
                            "复制地址"
                        ), null
                    ) { index: Int, _: String? ->
                        when (index) {
                            0 -> {
                                item.isTop = !item.isTop
                                mSubscriptions[position] = item
                                mSubscriptionAdapter.setNewData(mSubscriptions)
                            }
                            1 -> {
                                XPopup.Builder(this)
                                    .asInputConfirm(
                                        "更改为",
                                        "",
                                        item.name,
                                        "新的订阅名",
                                        { text ->
                                            if (!TextUtils.isEmpty(text)) {
                                                if (text.trim { it <= ' ' }.length > 8) {
                                                    ToastUtils.showShort("不要过长,不方便记忆")
                                                } else {
                                                    item.name = text.trim { it <= ' ' }
                                                    mSubscriptionAdapter.notifyItemChanged(position)
                                                }
                                            }
                                        },
                                        null,
                                        R.layout.dialog_input
                                    ).show()
                            }
                            2 -> {
                                ClipboardUtils.copyText(mSubscriptions.get(position).url)
                                ToastUtils.showLong("已复制")
                            }
                        }
                    }.show()
                true
            }
    }

    private fun pickFile(checked: Boolean) {
        pendingLocalChecked = checked
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, REQUEST_OPEN_SUBSCRIPTION_FILE)
    }

    @Deprecated("Use the Activity Result API when this activity is migrated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_SUBSCRIPTION_FILE || resultCode != Activity.RESULT_OK) {
            return
        }
        val resultIntent = data ?: return
        val uri = resultIntent.data ?: return
        try {
            val flags = resultIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (flags != 0) {
                contentResolver.takePersistableUriPermission(uri, flags)
            }
        } catch (_: SecurityException) {
            // Some providers do not support persistent grants; the current session still has read access.
        }
        val url = uri.toString()
        if (hasDuplicateSubscription(url)) {
            ToastUtils.showLong("订阅地址已存在")
            return
        }
        addSubscription(getDocumentName(uri), url, pendingLocalChecked)
        mSubscriptionAdapter.setNewData(mSubscriptions)
    }

    private fun getDocumentName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) {
                    return cursor.getString(column)
                }
            }
        }
        return uri.lastPathSegment ?: "本地订阅"
    }

    private fun addSubscription(name: String, url: String, checked: Boolean) {
        if (url.startsWith("clan://") || url.startsWith("content://")) {
            addSub2List(name, url, checked)
            mSubscriptionAdapter.setNewData(mSubscriptions)
        } else if (url.startsWith("http")) {
            showLoadingDialog()
            OkGo.get<String>(url)
                .tag("get_subscription")
                .execute(object : AbsCallback<String?>() {
                    override fun onSuccess(response: Response<String?>) {
                        dismissLoadingDialog()
                        try {
                            val json = JsonParser.parseString(response.body()).asJsonObject
                            // 多线路?
                            val urls = json["urls"]
                            // 多仓?
                            val storeHouse = json["storeHouse"]
                            if (urls != null && urls.isJsonArray) { // 多线路
                                if (checked) {
                                    ToastUtils.showLong("多条线路请主动选择")
                                }
                                val urlList = urls.asJsonArray
                                if (urlList != null && urlList.size() > 0 && urlList[0].isJsonObject
                                    && urlList[0].asJsonObject.has("url")
                                    && urlList[0].asJsonObject.has("name")
                                ) { //多线路格式
                                    for (i in 0 until urlList.size()) {
                                        val obj = urlList[i] as JsonObject
                                        val name = obj["name"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val url = obj["url"].asString.trim { it <= ' ' }
                                        addSub2List(name, url, false)
                                    }
                                }
                            } else if (storeHouse != null && storeHouse.isJsonArray) { // 多仓
                                val storeHouseList = storeHouse.asJsonArray
                                if (storeHouseList != null && storeHouseList.size() > 0 && storeHouseList[0].isJsonObject
                                    && storeHouseList[0].asJsonObject.has("sourceName")
                                    && storeHouseList[0].asJsonObject.has("sourceUrl")
                                ) { //多仓格式
                                    mSources.clear()
                                    for (i in 0 until storeHouseList.size()) {
                                        val obj = storeHouseList[i] as JsonObject
                                        val name = obj["sourceName"].asString.trim { it <= ' ' }
                                            .replace("<|>|《|》|-".toRegex(), "")
                                        val url = obj["sourceUrl"].asString.trim { it <= ' ' }
                                        mSources.add(Source(name, url))
                                    }
                                    XPopup.Builder(this@SubscriptionActivity)
                                        .asCustom(
                                            ChooseSourceDialog(
                                                this@SubscriptionActivity,
                                                mSources
                                            ) { position: Int, _: String? ->
                                                // 再根据多线路格式获取配置,如果仓内是正常多线路模式,name没用,直接使用线路的命名
                                                addSubscription(
                                                    mSources[position].sourceName,
                                                    mSources[position].sourceUrl,
                                                    checked
                                                )
                                            })
                                        .show()
                                }
                            } else { // 单线路/其余
                                addSub2List(name, url, checked)
                            }
                        } catch (th: Throwable) {
                            addSub2List(name, url, checked)
                        }
                        mSubscriptionAdapter.setNewData(mSubscriptions)
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body()!!.string()
                    }

                    override fun onError(response: Response<String?>) {
                        super.onError(response)
                        dismissLoadingDialog()
                        ToastUtils.showLong("订阅失败,请检查地址或网络状态")
                    }
                })
        } else {
            ToastUtils.showShort("订阅格式不正确")
        }
    }

    /**
     * 仅当选中本地文件和添加的为单线路时,使用此订阅生效。多线路会直接解析全部并添加,多仓会展开并选择,最后也按多线路处理,直接添加
     * @param name
     * @param url
     * @param checkNewest
     */
    private fun addSub2List(name: String, url: String, checkNewest: Boolean): Boolean {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty() || hasDuplicateSubscription(normalizedUrl)) {
            return false
        }
        if (checkNewest) { //选中最新的,清除以前的选中订阅
            for (subscription in mSubscriptions) {
                if (subscription.isChecked) {
                    subscription.setChecked(false)
                }
            }
            mSelectedUrl = normalizedUrl
            mSubscriptions.add(Subscription(name, normalizedUrl).setChecked(true))
        } else {
            mSubscriptions.add(Subscription(name, normalizedUrl).setChecked(false))
        }
        return true
    }

    private fun confirmSubscriptionCleanup() {
        XPopup.Builder(this)
            .asConfirm(
                "清理订阅",
                "将删除重复、格式错误和返回空内容的导入订阅。内置订阅、当前使用中的订阅及网络请求失败的订阅会保留。"
            ) {
                cleanupSubscriptions()
            }
            .show()
    }

    private fun cleanupSubscriptions() {
        var removed = removeDuplicateSubscriptions()
        val remoteSubscriptions = ArrayList<Subscription>()
        for (subscription in mSubscriptions.toList()) {
            if (isProtectedSubscription(subscription) || isLocalSubscription(subscription.url)) {
                continue
            }
            if (!isRemoteSubscription(subscription.url)) {
                mSubscriptions.remove(subscription)
                removed++
            } else {
                remoteSubscriptions.add(subscription)
            }
        }
        if (remoteSubscriptions.isEmpty()) {
            finishSubscriptionCleanup(removed, 0)
            return
        }
        ToastUtils.showShort("正在检查 ${remoteSubscriptions.size} 个订阅")
        showLoadingDialog()
        checkRemoteSubscriptions(remoteSubscriptions, 0, removed, 0)
    }

    private fun checkRemoteSubscriptions(
        subscriptions: List<Subscription>,
        index: Int,
        removed: Int,
        unavailable: Int
    ) {
        if (index >= subscriptions.size) {
            dismissLoadingDialog()
            finishSubscriptionCleanup(removed, unavailable)
            return
        }
        val subscription = subscriptions[index]
        OkGo.get<String>(subscription.url)
            .tag(CLEANUP_SUBSCRIPTIONS_TAG)
            .execute(object : AbsCallback<String?>() {
                override fun convertResponse(response: okhttp3.Response): String {
                    return response.body()!!.string()
                }

                override fun onSuccess(response: Response<String?>) {
                    val isEmpty = response.body().isNullOrBlank()
                    val removedNow = if (isEmpty && canRemoveSubscription(subscription)) {
                        mSubscriptions.remove(subscription)
                        1
                    } else {
                        0
                    }
                    checkRemoteSubscriptions(subscriptions, index + 1, removed + removedNow, unavailable)
                }

                override fun onError(response: Response<String?>) {
                    super.onError(response)
                    checkRemoteSubscriptions(subscriptions, index + 1, removed, unavailable + 1)
                }
            })
    }

    private fun finishSubscriptionCleanup(removed: Int, unavailable: Int) {
        mSubscriptionAdapter.setNewData(mSubscriptions)
        Hawk.put<List<Subscription>?>(HawkConfig.SUBSCRIPTIONS, mSubscriptions)
        val message = if (unavailable > 0) {
            "已清理 $removed 项，$unavailable 项网络异常已保留"
        } else {
            "已清理 $removed 项订阅"
        }
        ToastUtils.showLong(message)
    }

    private fun removeDuplicateSubscriptions(): Int {
        val retained = LinkedHashMap<String, Subscription>()
        var removed = 0
        for (subscription in mSubscriptions.toList()) {
            val key = subscriptionKey(subscription.url)
            if (key.isEmpty()) {
                continue
            }
            val existing = retained[key]
            if (existing == null) {
                retained[key] = subscription
            } else if (!isProtectedSubscription(subscription)) {
                mSubscriptions.remove(subscription)
                removed++
            } else if (!isProtectedSubscription(existing)) {
                mSubscriptions.remove(existing)
                retained[key] = subscription
                removed++
            }
        }
        return removed
    }

    private fun hasDuplicateSubscription(url: String): Boolean {
        val key = subscriptionKey(url)
        return key.isNotEmpty() && mSubscriptions.any { subscriptionKey(it.url) == key }
    }

    private fun isProtectedSubscription(subscription: Subscription): Boolean {
        val selectedKey = subscriptionKey(mSelectedUrl)
        return subscription.isBuiltIn || subscription.isChecked ||
            (selectedKey.isNotEmpty() && subscriptionKey(subscription.url) == selectedKey)
    }

    private fun canRemoveSubscription(subscription: Subscription): Boolean {
        return mSubscriptions.contains(subscription) && !isProtectedSubscription(subscription)
    }

    private fun isLocalSubscription(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("clan://") || value.startsWith("content://")
    }

    private fun isRemoteSubscription(url: String): Boolean {
        val uri = Uri.parse(url.trim())
        return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrEmpty()
    }

    private fun subscriptionKey(url: String): String {
        val value = url.trim()
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.toLowerCase(Locale.ROOT) ?: return value
        val host = uri.host?.toLowerCase(Locale.ROOT) ?: return value
        if (scheme != "http" && scheme != "https") {
            return value
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.encodedPath?.trimEnd('/') ?: ""
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        return "$scheme://$host$port$path$query"
    }

    private fun initBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNavigation.selectedItemId = R.id.navigation_subscription
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_subscription -> true
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_live -> {
                    destinationAfterFinish = item.itemId
                    finish()
                    false
                }
                else -> false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 更新缓存
        Hawk.put(HawkConfig.API_URL, mSelectedUrl)
        Hawk.put<List<Subscription>?>(HawkConfig.SUBSCRIPTIONS, mSubscriptions)
    }

    override fun finish() {
        val destination = destinationAfterFinish
        if (destination != null || (!TextUtils.isEmpty(mSelectedUrl) && mBeforeUrl != mSelectedUrl)) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(MainActivity.EXTRA_START_DESTINATION, destination ?: R.id.navigation_home)
            intent.flags = if (mBeforeUrl != mSelectedUrl) {
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            } else {
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        OkGo.getInstance().cancelTag("get_subscription")
        OkGo.getInstance().cancelTag(CLEANUP_SUBSCRIPTIONS_TAG)
    }
}
