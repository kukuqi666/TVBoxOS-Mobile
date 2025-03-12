package com.github.tvbox.osc.base

import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType


abstract class BaseVbActivity<T : ViewBinding> : BaseActivity() {

    protected lateinit var mBinding: T
    public override fun getLayoutResID(): Int {
        return -1
    }

    /**
     * 初始化viewBinding
     */
    override fun initVb() {
        val type = javaClass.genericSuperclass as ParameterizedType
        val cls = type.actualTypeArguments[0] as Class<*>
        try {
            val inflate = cls.getDeclaredMethod("inflate", LayoutInflater::class.java)
            mBinding = inflate.invoke(null, layoutInflater) as T
            setContentView(mBinding.root)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}