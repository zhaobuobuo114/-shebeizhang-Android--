package com.deviceledger.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 勋章弹屏背后那一朵烟花：一次性播放，放完就停，不常驻重绘。
 *
 * 刻意做得小：14 颗粒子、500ms、亮度压到六成，奖赏感有就行，主角始终是勋章。
 * 播放只在 start() 之后发生，视图脱离窗口时立刻取消动画。
 */
class SparkleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Spark(
        val rad: Float,      // 弧度
        val reach: Float,    // 从起爆半径再往外飞多远（像素）
        val size: Float,     // 半径（像素）
        val color: Int,
        val drift: Float     // 每颗粒子稍微错开一点时序
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sparks: List<Spark> = emptyList()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        visibility = GONE
    }

    /**
     * 起爆半径必须落在勋章圆盘之外：粒子若从画面正中起爆，整段飞行都被不透明的
     * 圆盘挡在身后，看着就像没有烟花。
     */
    private val startRadius = 60f * density

    /** 在视图中心放一朵烟花。colors 由调用方按当前主题给，保证昼夜两套都不刺眼 */
    fun burst(colors: IntArray) {
        // 粒子参数是一张写死的表，与鸿蒙版 BadgeCelebration 里的 SPARKS 逐行相同 ——
        // 各端自己随机的话，同一朵烟花在两端会长得不一样，"完全同步"就落不到实处。
        val list = ArrayList<Spark>(SPARKS.size)
        for (s in SPARKS) {
            list.add(Spark(s.rad, s.reachDp * density, s.sizeDp * density, colors[list.size % colors.size], s.drift))
        }
        sparks = list
        visibility = VISIBLE
        animator?.cancel()
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 560L
        va.interpolator = Curves.easeOut
        va.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        va.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                visibility = GONE
            }
        })
        animator = va
        va.start()
    }

    fun stop() {
        animator?.cancel()
        animator = null
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sparks.isEmpty() || visibility != VISIBLE) return
        val cx = width / 2f
        val cy = height / 2f
        for (s in sparks) {
            val life = ((progress - s.drift) / (1f - s.drift)).coerceIn(0f, 1f)
            if (life <= 0f) continue
            val eased = 1f - (1f - life) * (1f - life)          // 先快后慢，像被空气拖住
            val r = startRadius + s.reach * eased
            val x = cx + cos(s.rad) * r
            val y = cy + sin(s.rad) * r * 0.92f + 14f * density * life * life   // 尾段受重力下坠
            // 前半程保持亮度，后半程才淡出：否则粒子刚飞出圆盘就已经看不见了
            paint.alpha = (230f * (1f - life * life)).toInt().coerceIn(0, 255)
            paint.color = s.color
            canvas.drawCircle(x, y, s.size * (1f - life * 0.4f), paint)
        }
    }

    /** 粒子参数：角度、外扩距离(dp)、点半径(dp)、时序偏移 */
    private class SparkSpec(val rad: Float, val reachDp: Float, val sizeDp: Float, val drift: Float)

    companion object {
        /** 与鸿蒙版 BadgeCelebration.ets 里的 SPARKS 逐行相同，改一处必须改两处 */
        private val SPARKS = listOf(
            SparkSpec(0.110000f, 62f, 3.4f, 0.00f),
            SparkSpec(0.688799f, 48f, 4.4f, 0.12f),
            SparkSpec(0.927598f, 78f, 2.6f, 0.05f),
            SparkSpec(1.516397f, 55f, 3.8f, 0.17f),
            SparkSpec(1.865196f, 84f, 3.0f, 0.09f),
            SparkSpec(2.463995f, 44f, 4.6f, 0.02f),
            SparkSpec(2.822794f, 70f, 3.2f, 0.14f),
            SparkSpec(3.151593f, 58f, 2.4f, 0.07f),
            SparkSpec(3.780392f, 46f, 4.0f, 0.19f),
            SparkSpec(4.129191f, 80f, 3.6f, 0.04f),
            SparkSpec(4.737990f, 52f, 2.8f, 0.11f),
            SparkSpec(4.986788f, 74f, 4.2f, 0.16f),
            SparkSpec(5.535587f, 60f, 3.1f, 0.06f),
            SparkSpec(5.854386f, 66f, 3.9f, 0.13f)
        )
    }
}
