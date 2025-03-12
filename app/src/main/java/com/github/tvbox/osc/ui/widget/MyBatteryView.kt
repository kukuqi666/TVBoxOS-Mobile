package com.github.tvbox.osc.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.github.tvbox.osc.R

/**
 * @Author : Liu XiaoRan
 * @Email : 592923276@qq.com
 * @Date : on 2023/2/1 14:11.
 * @Description :
 */
class MyBatteryView : View {
    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : super(context, attrs)

    private var battery: Int = 80

    private val OUTLINE_STROKE_SIZE = 3.0f // 外层电池框厚度
    private val CORNER = 4f // 通用圆角

    private val CAP_WIDTH = 2.0f // 电池盖宽度
    private val CAP_HEIGHT = 10.0f // 电池盖高度

    private val BATTERY_MARGIN = 4f // 电量到电池框的距离

    // 外边框
    private val boxOut by lazy {
        RectF().also {
            // 从指定坐标开始画的话 坐标的位置貌似是在stroke线内 所以左和上坐标是 线宽 OUTLINE_STROKE_SIZE
            it.left = OUTLINE_STROKE_SIZE
            it.top = OUTLINE_STROKE_SIZE
        }
    }

    // 正极
    private val boxCap = RectF()

    // 电量
    private val boxBattery = RectF()

    // 外边框画笔
    private val boxOutPaint by lazy {
        Paint().also {
            it.color = Color.WHITE
            it.style = Paint.Style.STROKE
            it.strokeWidth = OUTLINE_STROKE_SIZE
            it.isAntiAlias = true
        }
    }

    // 正极画笔
    private val boxCapPaint by lazy {
        Paint().also {
            it.color = Color.WHITE
            it.style = Paint.Style.FILL
            it.isAntiAlias = true
        }
    }

    // 电量画笔
    private val boxBatteryPaint by lazy {
        Paint().also {
            it.color = Color.GREEN
            it.style = Paint.Style.FILL
            it.isAntiAlias = true
        }
    }

    // 电量文字画笔
    private val boxBatteryTextPaint by lazy {
        Paint().also {
            it.color = Color.WHITE
            it.style = Paint.Style.FILL_AND_STROKE
            it.textSize = 20f
            it.textAlign = Paint.Align.CENTER
            it.strokeWidth = 2f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val specWidthSize = MeasureSpec.getSize(widthMeasureSpec)
        val specHeightSize = MeasureSpec.getSize(heightMeasureSpec)
        // 外边框的右侧应该在总长度 - 设定的盖的宽度 - 画笔的线的宽度
        boxOut.right = specWidthSize - CAP_WIDTH - OUTLINE_STROKE_SIZE
        boxOut.bottom = specHeightSize - OUTLINE_STROKE_SIZE

        // 电量框测量位置
        boxBattery.left = boxOut.left + BATTERY_MARGIN
        boxBattery.top = boxOut.top + BATTERY_MARGIN
        boxBattery.bottom = boxOut.bottom - BATTERY_MARGIN

        // 正极测量位置
        boxCap.left = boxOut.right
        boxCap.top = specHeightSize / 2 - CAP_HEIGHT / 2
        boxCap.right = specWidthSize.toFloat()
        boxCap.bottom = specHeightSize / 2 + CAP_HEIGHT / 2

        setMeasuredDimension(specWidthSize, specHeightSize)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // 满电的长度
        val fullPowerSize = boxOut.right - BATTERY_MARGIN - boxBattery.left

        boxBattery.right = boxBattery.left + fullPowerSize * (battery.toFloat() / 100)
        if (battery <= 20) {
            boxBatteryPaint.color = com.blankj.utilcode.util.ColorUtils.getColor(R.color.warn)
        }
        canvas?.drawRoundRect(boxOut, CORNER, CORNER, boxOutPaint)
        canvas?.drawRoundRect(boxCap, 1F, 1F, boxCapPaint)
        canvas?.drawRoundRect(boxBattery, CORNER / 2, CORNER / 2, boxBatteryPaint)

        val fontMetrics : Paint.FontMetrics = boxBatteryTextPaint.fontMetrics;
        // 计算文字高度
        val fontHeight = fontMetrics.bottom - fontMetrics.top
        // 计算文字baseline
        val textBaseY = height - (height - fontHeight) / 2 - fontMetrics.bottom;
        canvas?.drawText(battery.toString(), boxOut.centerX(), textBaseY, boxBatteryTextPaint)
    }

    fun updateBattery(battery: Int) {
//        this.battery = battery > 100 ? 100 : battery < 1 ? 1 : battery;
        this.battery = battery
        invalidate()
    }
}
