package com.deviceledger.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.deviceledger.app.R

private val ARGB = ArgbEvaluator()

/**
 * 文字色 + 透明度在给定时长内平滑过渡。
 *
 * 对应鸿蒙版 `animateTo({ duration, curve: Curve.Friction })`：那边改的是 @State，
 * 组件不重建，颜色天然是插值过去的；安卓这边 setTextColor / setAlpha 都是硬切，
 * 不插值的话点下去就是"啪"一下换色，没有过渡。这里用缓出曲线近似 Friction。
 *
 * @param durationMs 传 0 表示立即生效（换肤、首次渲染这类不需要动画的场合）。
 *                   同一个 View 上重复调用会先取消上一次，两个动画不会打架。
 */
fun TextView.tintTo(colorTo: Int, alphaTo: Float, durationMs: Long) {
    (getTag(R.id.tag_tint_anim) as? ValueAnimator)?.cancel()
    if (durationMs <= 0L) {
        setTextColor(colorTo)
        alpha = alphaTo
        return
    }
    val colorFrom = currentTextColor
    val alphaFrom = alpha
    if (colorFrom == colorTo && alphaFrom == alphaTo) {
        return
    }
    val va = ValueAnimator.ofFloat(0f, 1f)
    va.duration = durationMs
    va.interpolator = DecelerateInterpolator(1.5f)
    va.addUpdateListener {
        val f = it.animatedFraction
        setTextColor(ARGB.evaluate(f, colorFrom, colorTo) as Int)
        alpha = alphaFrom + (alphaTo - alphaFrom) * f
    }
    va.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            setTextColor(colorTo)
            alpha = alphaTo
        }
    })
    va.start()
    setTag(R.id.tag_tint_anim, va)
}

/**
 * 胶囊的底色 + 文字色一起过渡（"只看收藏"这类状态切换用）。
 *
 * 底色起点存在 tag 里：GradientDrawable 取不回已经设进去的颜色，只能自己记。
 *
 * @param durationMs 传 0 表示立即生效。
 */
fun TextView.chipTo(
    bgTo: Int,
    colorTo: Int,
    radiusDp: Float,
    density: Float,
    durationMs: Long
) {
    (getTag(R.id.tag_chip_anim) as? ValueAnimator)?.cancel()
    val bgFrom = (getTag(R.id.tag_chip_bg) as? Int) ?: bgTo
    val colorFrom = currentTextColor
    if (durationMs <= 0L || (bgFrom == bgTo && colorFrom == colorTo)) {
        setTag(R.id.tag_chip_bg, bgTo)
        background = roundedDrawable(bgTo, radiusDp, density)
        setTextColor(colorTo)
        return
    }
    if (background !is GradientDrawable) {
        background = roundedDrawable(bgFrom, radiusDp, density)
    }
    val d = background as GradientDrawable
    val va = ValueAnimator.ofFloat(0f, 1f)
    va.duration = durationMs
    va.interpolator = DecelerateInterpolator(1.5f)
    va.addUpdateListener {
        val f = it.animatedFraction
        val bg = ARGB.evaluate(f, bgFrom, bgTo) as Int
        d.setColor(bg)
        setTextColor(ARGB.evaluate(f, colorFrom, colorTo) as Int)
        setTag(R.id.tag_chip_bg, bg)
    }
    va.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            d.setColor(bgTo)
            setTextColor(colorTo)
            setTag(R.id.tag_chip_bg, bgTo)
        }
    })
    va.start()
    setTag(R.id.tag_chip_anim, va)
}
