package com.deviceledger.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 从左往右排，一行排不下就自动换行。
 * 用来对齐鸿蒙版 Flex({ wrap: FlexWrap.Wrap }) 的行为：
 * 子项保持自己声明的固定尺寸（设备类型格子 60x60），而不是被均分拉伸。
 */
class WrapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /** 子项之间的水平 / 垂直间距（像素） */
    var spacingH: Int = 0
    var spacingV: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = paddingTop + paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (rowWidth > 0 && rowWidth + spacingH + cw > maxWidth) {
                totalHeight += rowHeight + spacingV
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += if (rowWidth == 0) cw else spacingH + cw
            rowHeight = maxOf(rowHeight, ch)
        }
        if (rowHeight > 0) {
            totalHeight += rowHeight
        }

        setMeasuredDimension(
            resolveSize(
                MeasureSpec.getSize(widthMeasureSpec) +
                    (if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) paddingLeft + paddingRight else 0),
                widthMeasureSpec
            ),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = paddingLeft
        val limit = r - l - paddingRight
        var x = left
        var y = paddingTop
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            if (x > left && x + cw > limit) {
                x = left
                y += rowHeight + spacingV
                rowHeight = 0
            }
            child.layout(x, y, x + cw, y + ch)
            x += cw + spacingH
            rowHeight = maxOf(rowHeight, ch)
        }
    }
}
