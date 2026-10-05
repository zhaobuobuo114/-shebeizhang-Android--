package com.deviceledger.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 统一的按压动效：按下时缩小，松手复原。
 * 用它替代系统默认的 selectableItemBackground —— 那玩意儿会带一圈蓝色水波纹。
 *
 * 参数与鸿蒙版 Index.ets / ChartCard.ets 里的 onPress + sc() 完全对齐：
 * 主页控件按到 0.92、图表里的小胶囊按到 0.9，按下与回弹都是 140ms EaseOut，
 * 只缩放、不改透明度，两边手感一致。
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
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            MotionEvent.ACTION_UP -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                val inside = event.x >= 0f && event.x <= v.width.toFloat() &&
                    event.y >= 0f && event.y <= v.height.toFloat()
                if (inside) {
                    v.performClick()
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        true
    }
}
