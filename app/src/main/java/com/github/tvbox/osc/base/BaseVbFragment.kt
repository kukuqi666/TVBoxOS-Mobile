package com.github.tvbox.osc.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.github.tvbox.osc.callback.EmptyCallback
import com.github.tvbox.osc.callback.LoadingCallback
import com.kingja.loadsir.core.LoadService
import com.kingja.loadsir.core.LoadSir
import me.jessyan.autosize.AutoSize
import me.jessyan.autosize.internal.CustomAdapt
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

/**
 * Fragment的基类(vb)
 */
abstract class BaseVbFragment<T : ViewBinding> : Fragment(), CustomAdapt {
    @JvmField
    protected var mContext: Context? = null
    @JvmField
    protected var mActivity: Activity? = null

    protected lateinit var mBinding: T
    private var mLoadService: LoadService<*>? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        AutoSize.autoConvertDensity(activity, sizeInDp, isBaseOnWidth)
        return initBindingViewRoot(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        mActivity = context as Activity
    }

    /**
     * 初始化viewBinding返回根布局
     */
    private fun initBindingViewRoot(container: ViewGroup?): View? {
        val type = javaClass.genericSuperclass as ParameterizedType
        val aClass = type.actualTypeArguments[0] as Class<*>
        val method: Method
        try {
            method = aClass.getDeclaredMethod(
                "inflate",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Boolean::class.javaPrimitiveType
            )
            mBinding = method.invoke(null, getLayoutInflater(), container, false) as T
            return mBinding.root
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 在滑动或者跳转的过程中，第一次创建fragment的时候均会调用onResume方法
     */
    override fun onResume() {
        AutoSize.autoConvertDensity(activity, sizeInDp, isBaseOnWidth)
        super.onResume()
    }

    protected abstract fun init()
    protected fun setLoadSir(view: View?) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view) { }
        }
    }

    protected fun setLoadSir2(view: View?) {
        mLoadService = LoadSir.getDefault().register(view) { }
    }

    protected fun showLoading() {
        if (mLoadService != null) {
            mLoadService!!.showCallback(LoadingCallback::class.java)
        }
    }

    protected fun showEmpty() {
        if (null != mLoadService) {
            mLoadService!!.showCallback(EmptyCallback::class.java)
        }
    }

    protected fun showSuccess() {
        if (null != mLoadService) {
            mLoadService!!.showSuccess()
        }
    }

    fun jumpActivity(clazz: Class<out BaseActivity?>?) {
        val intent = Intent(mContext, clazz)
        startActivity(intent)
    }

    fun jumpActivity(clazz: Class<out BaseActivity?>?, bundle: Bundle?) {
        val intent = Intent(mContext, clazz)
        intent.putExtras(bundle!!)
        startActivity(intent)
    }

    override fun getSizeInDp(): Float {
        return if (activity != null && activity is CustomAdapt) (activity as CustomAdapt?)!!.sizeInDp else 0f
    }

    override fun isBaseOnWidth(): Boolean {
        return if (activity != null && activity is CustomAdapt) (activity as CustomAdapt?)!!.isBaseOnWidth else true
    }
}
