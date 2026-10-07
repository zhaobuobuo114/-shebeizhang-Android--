package com.deviceledger.app.ui

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * ArkUI Curve 的等价实现。
 *
 * 鸿蒙端 animateTo / .animation() 走的都是 CSS 三次贝塞尔曲线（官方给了确定的控制点），
 * 安卓自带的 DecelerateInterpolator（1-(1-x)²）、AccelerateInterpolator（x²）、
 * AccelerateDecelerateInterpolator（余弦）是另外三套公式，只是"看着差不多"，
 * 逐帧对不上。这里用 PathInterpolator 按官方文档的控制点原样还原，
 * 保证两端的推进过程逐帧一致。
 *
 * 控制点取值来源：ArkUI「枚举说明 / 动画样式」——
 *   Ease        cubic-bezier(0.25, 0.1, 0.25, 1.0)
 *   EaseIn      cubic-bezier(0.42, 0.0, 1.0, 1.0)
 *   EaseOut     cubic-bezier(0.0, 0.0, 0.58, 1.0)
 *   EaseInOut   cubic-bezier(0.42, 0.0, 0.58, 1.0)
 *   Friction    cubic-bezier(0.2, 0.0, 0.2, 1.0)
 *   Smooth      cubic-bezier(0.4, 0.0, 0.4, 1.0)
 */
object Curves {

    /** Curve.Linear */
    val linear: Interpolator = PathInterpolator(0f, 0f, 1f, 1f)

    /** Curve.Ease */
    val ease: Interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)

    /** Curve.EaseIn：先慢后快 */
    val easeIn: Interpolator = PathInterpolator(0.42f, 0f, 1f, 1f)

    /** Curve.EaseOut：先快后慢 */
    val easeOut: Interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)

    /** Curve.EaseInOut */
    val easeInOut: Interpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)

    /** Curve.Friction：阻尼曲线，页面里大多数状态切换都用它 */
    val friction: Interpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)

    /** Curve.Smooth */
    val smooth: Interpolator = PathInterpolator(0.4f, 0f, 0.4f, 1f)
}
