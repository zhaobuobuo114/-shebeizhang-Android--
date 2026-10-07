package com.deviceledger.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.deviceledger.app.R

private val ARGB = ArgbEvaluator()

/**
 * 文字色 + 透明度在给定时长内平滑过渡。
 *
 * 对应鸿蒙版 `animateTo({ duration, curve: Curve.Friction })`：那边改的是 @State，
 * 组件不重建，颜色天然是插值过去的；安卓这边 setTextColor / setAlpha 都是硬切，
 * 不插值的话点下去就是"啪"一下换色，没有过渡。
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
    va.interpolator = Curves.friction
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
    va.interpolator = Curves.friction
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

/**
 * 设备类型格子的底色 + 描边（颜色与粗细）+ 圆角一起过渡。
 *
 * 对应鸿蒙版编辑面板里选中类型时的 `animateTo({ duration: 200, curve: Curve.Friction })`：
 * 那边的边框宽度和底色都是插值过去的，安卓这边不插值就是硬切。
 *
 * @param durationMs 传 0 表示立即生效（换肤、首次渲染）。
 */
fun View.tileTo(
    bgTo: Int,
    strokeColorTo: Int,
    strokeDpTo: Float,
    radiusDp: Float,
    density: Float,
    durationMs: Long
) {
    (getTag(R.id.tag_tile_anim) as? ValueAnimator)?.cancel()
    val bgFrom = (getTag(R.id.tag_tile_bg) as? Int) ?: bgTo
    val strokeFrom = (getTag(R.id.tag_tile_stroke) as? Int) ?: strokeColorTo
    val widthFrom = (getTag(R.id.tag_tile_stroke_w) as? Float) ?: strokeDpTo
    val same = bgFrom == bgTo && strokeFrom == strokeColorTo && widthFrom == strokeDpTo
    if (durationMs <= 0L || same) {
        setTag(R.id.tag_tile_bg, bgTo)
        setTag(R.id.tag_tile_stroke, strokeColorTo)
        setTag(R.id.tag_tile_stroke_w, strokeDpTo)
        background = borderedDrawable(bgTo, radiusDp, strokeColorTo, strokeDpTo, density)
        return
    }
    if (background !is GradientDrawable) {
        background = borderedDrawable(bgFrom, radiusDp, strokeFrom, widthFrom, density)
    }
    val d = background as GradientDrawable
    val va = ValueAnimator.ofFloat(0f, 1f)
    va.duration = durationMs
    va.interpolator = Curves.friction
    va.addUpdateListener {
        val f = it.animatedFraction
        val bg = ARGB.evaluate(f, bgFrom, bgTo) as Int
        val sc = ARGB.evaluate(f, strokeFrom, strokeColorTo) as Int
        val sw = widthFrom + (strokeDpTo - widthFrom) * f
        d.setColor(bg)
        d.setStroke((sw * density).toInt().coerceAtLeast(0), sc)
        setTag(R.id.tag_tile_bg, bg)
        setTag(R.id.tag_tile_stroke, sc)
        setTag(R.id.tag_tile_stroke_w, sw)
    }
    va.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            setTag(R.id.tag_tile_bg, bgTo)
            setTag(R.id.tag_tile_stroke, strokeColorTo)
            setTag(R.id.tag_tile_stroke_w, strokeDpTo)
            d.setColor(bgTo)
            d.setStroke((strokeDpTo * density).toInt().coerceAtLeast(0), strokeColorTo)
        }
    })
    va.start()
    setTag(R.id.tag_tile_anim, va)
}
