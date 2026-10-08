package com.deviceledger.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.deviceledger.app.R

/**
 * 排序方向的小箭头。
 *
 * 早先这里是两个 TextView 写着 Unicode 的 ▲ / ▼ —— 字形由系统字体决定，
 * 各家手机上胖瘦、重心、粗细都不一样，还会被当成文字参与排版，看着发虚。
 * 改成自己画一条闭合的三角形：底边与两腰都用圆角接头收边，
 * 10×6dp 的块面里 fill 与 stroke 同色，尖角自然带一点圆润，不再是一枚字。
 *
 * 颜色与透明度都做成可动画的属性：和鸿蒙版 animateTo(260ms Friction) 对齐，
 * 切换方向时两个三角是插值着交换的，不是硬切。
 */
class SortArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Dir { UP, DOWN }

    var dir: Dir = Dir.UP
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 三角的颜色（可动画） */
    var arrowColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    /** 整体不透明度（可动画） */
    var arrowAlpha: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    private val density: Float
        get() = resources.displayMetrics.density

    private val path = Path()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // stroke 会往路径两边各铺一半，先把三角形往里缩同样的量，
        // 画出来才正好落在 View 的尺寸里，不会顶到边缘
        val line = 1.4f * density
        val inset = line / 2f
        val left = inset
        val right = w - inset
        val top = inset
        val bottom = h - inset
        if (right <= left || bottom <= top) return

        path.reset()
        if (dir == Dir.UP) {
            path.moveTo(left, bottom)
            path.lineTo(w / 2f, top)
            path.lineTo(right, bottom)
        } else {
            path.moveTo(left, top)
            path.lineTo(w / 2f, bottom)
            path.lineTo(right, top)
        }
        path.close()

        val a = (arrowAlpha.coerceIn(0f, 1f) * 255f).toInt()
        borderPaint.color = arrowColor
        borderPaint.strokeWidth = line
        borderPaint.alpha = a
        solidPaint.color = arrowColor
        solidPaint.alpha = a

        canvas.drawPath(path, solidPaint)
        canvas.drawPath(path, borderPaint)
    }

    /**
     * 颜色 + 不透明度在给定时长内平滑过渡。
     * 与 Tint.kt 里的思路一致，只是作用对象是自绘的三角形而不是 TextView。
     */
    fun arrowTo(colorTo: Int, alphaTo: Float, durationMs: Long) {
        (getTag(R.id.tag_arrow_anim) as? android.animation.ValueAnimator)?.cancel()
        if (durationMs <= 0L) {
            arrowColor = colorTo
            arrowAlpha = alphaTo
            return
        }
        val colorFrom = arrowColor
        val alphaFrom = arrowAlpha
        if (colorFrom == colorTo && alphaFrom == alphaTo) return
        val evaluator = android.animation.ArgbEvaluator()
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = durationMs
        va.interpolator = Curves.friction
        va.addUpdateListener {
            val f = it.animatedFraction
            arrowColor = evaluator.evaluate(f, colorFrom, colorTo) as Int
            arrowAlpha = alphaFrom + (alphaTo - alphaFrom) * f
        }
        va.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                arrowColor = colorTo
                arrowAlpha = alphaTo
            }
        })
        va.start()
        setTag(R.id.tag_arrow_anim, va)
    }
}
