package com.deviceledger.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

/**
 * 统一的按压动效：按下时缩小，松手复原。
 * 用它替代系统默认的 selectableItemBackground —— 那玩意儿会带一圈蓝色水波纹。
 *
 * 参数与鸿蒙版 Index.ets / ChartCard.ets 里的 onPress + sc() 逐项对齐：
 *   - 主页控件按到 0.92，图表里的小胶囊按到 0.90，设备卡的星标按到 0.86；
 *   - 按下与回弹都是 140ms，曲线是 Curve.EaseOut（不是系统的 DecelerateInterpolator）；
 *   - 只改缩放、不动透明度。
 *
 * 注意：这里自己消费了触摸事件，并在抬起时手动触发 performClick()，
 * 所以外部照常 setOnClickListener 即可。
 */
@SuppressLint("ClickableViewAccessibility")
fun View.pressEffect(scaleTo: Float = 0.92f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(scaleTo)
                    .scaleY(scaleTo)
                    .setDuration(140L)
                    .setInterpolator(Curves.easeOut)
                    .start()
            }

            MotionEvent.ACTION_UP -> {
                springBack(v, 1f)
                val inside = event.x >= 0f && event.x <= v.width.toFloat() &&
                    event.y >= 0f && event.y <= v.height.toFloat()
                if (inside) {
                    v.performClick()
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> springBack(v, 1f)
        }
        true
    }
}

/**
 * 回顶部圆钮专用的按压动效。
 *
 * 鸿蒙版 Index.ets 的 onTopPress 没有走通用 sc()，而是单独的两段：
 * 按下 90ms EaseOut 压到 0.84，松手 210ms Friction 弹回当前该有的大小
 * （还在显示就回到 1，正在收起就回到 0.4）。这里照抄这个时序。
 *
 * @param restScale 松手要回到的缩放值；圆钮正在淡出时是 0.4，否则是 1
 */
@SuppressLint("ClickableViewAccessibility")
fun View.topPressEffect(restScale: () -> Float) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(0.84f)
                    .scaleY(0.84f)
                    .setDuration(90L)
                    .setInterpolator(Curves.easeOut)
                    .start()
            }

            MotionEvent.ACTION_UP -> {
                val rest = restScale()
                v.animate().cancel()
                v.animate()
                    .scaleX(rest)
                    .scaleY(rest)
                    .setDuration(210L)
                    .setInterpolator(Curves.friction)
                    .start()
                val inside = event.x >= 0f && event.x <= v.width.toFloat() &&
                    event.y >= 0f && event.y <= v.height.toFloat()
                if (inside) {
                    v.performClick()
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                val rest = restScale()
                v.animate().cancel()
                v.animate()
                    .scaleX(rest)
                    .scaleY(rest)
                    .setDuration(210L)
                    .setInterpolator(Curves.friction)
                    .start()
            }
        }
        true
    }
}

private fun springBack(v: View, to: Float) {
    v.animate().cancel()
    v.animate()
        .scaleX(to)
        .scaleY(to)
        .setDuration(140L)
        .setInterpolator(Curves.easeOut)
        .start()
}
