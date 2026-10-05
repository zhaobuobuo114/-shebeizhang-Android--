package com.deviceledger.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import com.deviceledger.app.model.PieSlice
import com.deviceledger.app.util.Fmt
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 扇形（环形）统计图：按设备类型看支出构成。
 * 纯 Canvas 手绘 + ValueAnimator 逐帧推进，做出"扇形展开"的动画。
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 鸿蒙版本用的是 easeOutCubic */
    private val easeOutCubic = Interpolator { input ->
        val x = 1f - input
        1f - x * x * x
    }

    private var slices: List<PieSlice> = emptyList()
    private var progress: Float = 1f
    private var animator: ValueAnimator? = null

    var theme: AppTheme = AppTheme.lightTheme()
    var mode: String = "amount"

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    private val density: Float
        get() = resources.displayMetrics.density

    fun setData(list: List<PieSlice>, mode: String, animate: Boolean) {
        this.slices = list
        this.mode = mode
        animator?.cancel()
        animator = null
        if (animate) {
            progress = 0f
            invalidate()
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.duration = 820L
            va.interpolator = easeOutCubic
            va.addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            va.start()
            animator = va
        } else {
            progress = 1f
            invalidate()
        }
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun totalValue(): Double {
        var sum = 0.0
        for (s in slices) sum += s.v
        return sum
    }

    private fun centerMain(): String {
        val shown = totalValue() * progress
        return if (mode == "amount") "¥" + Fmt.money(shown) else Math.round(shown).toString()
    }

    private fun centerSub(): String = if (mode == "amount") "总投入" else "台设备"

    /**
     * 环形动画走完（progress = 1）时圆心要显示的主数字。
     * 字号按它来定，而不是按逐帧的中间值——否则金额从 0 涨到总额的过程中
     * 字符串越来越长、字号被一路压小，看着像"数字自己在缩水"。
     */
    private fun centerSettled(): String {
        val total = totalValue()
        return if (mode == "amount") "¥" + Fmt.money(total) else Math.round(total).toString()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val rOut = min(w, h) / 2f - 5f * density
        // 内圈占比 0.64（原 0.60）：环略薄一点，把宽度让给圆心的金额
        val rIn = rOut * 0.64f
        if (rOut <= 0f) return

        val ringRadius = (rOut + rIn) / 2f
        oval.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        val total = totalValue()

        if (total <= 0.0 || slices.isEmpty()) {
            // 空态：只画一圈底环
            arcPaint.style = Paint.Style.STROKE
            arcPaint.strokeWidth = rOut - rIn
            arcPaint.color = theme.trackBg
            canvas.drawArc(oval, 0f, 360f, false, arcPaint)
        } else {
            val startAngle = -90f
            val fullSweep = 360f * progress
            var drawn = 0f
            // 鸿蒙版是给每段描一圈 3px 的卡片色边，相邻两段合起来正好是 3px 的缝；
            // 这里换算成对应的圆心角，保证两边缝隙视觉宽度一致
            val gapDeg = (3f * density / ringRadius) * (180f / Math.PI.toFloat())

            for (s in slices) {
                if (drawn >= fullSweep) break
                val frac = (s.v / total).toFloat()
                val fullSliceSweep = 360f * frac
                val visible = min(fullSliceSweep, fullSweep - drawn)
                // 整段都显示时才留出间隙，避免动画中出现缝隙抖动
                val sweep = if (visible >= fullSliceSweep) {
                    (visible - gapDeg).coerceAtLeast(0.2f)
                } else {
                    visible
                }
                if (sweep > 0.2f) {
                    arcPaint.style = Paint.Style.STROKE
                    arcPaint.strokeWidth = rOut - rIn
                    arcPaint.color = runCatching { Color.parseColor(s.c) }.getOrDefault(Color.GRAY)
                    canvas.drawArc(oval, startAngle + drawn, sweep, false, arcPaint)
                }
                drawn += visible
            }
        }

        // 圆心文字：可用宽度取"文字所在高度处的内圆弦长"，
        // 而不是直接用内圆直径——文字是上下两行错开画的，用弦长才是真实可用宽度。
        val main = centerMain()
        val sub = centerSub()
        val dyMain = -8f * density
        val dySub = 15f * density

        mainPaint.textAlign = Paint.Align.CENTER
        mainPaint.isFakeBoldText = true
        mainPaint.color = theme.text
        // 基准 24（原 21 大一档）、下限 12；字号按最终金额算，滚动时不再抖动；上限兜底不压环
        val mainCap = min(w, h) * 0.18f
        val mainSize = fitSize(mainPaint, centerSettled(), 24f * density, 12f * density, chordWidth(dyMain, rIn))
            .coerceAtMost(mainCap)
            .toInt()
            .toFloat()
        mainPaint.textSize = mainSize

        val fm = mainPaint.fontMetrics
        canvas.drawText(main, cx, cy + dyMain - (fm.ascent + fm.descent) / 2f, mainPaint)

        subPaint.textAlign = Paint.Align.CENTER
        subPaint.isFakeBoldText = false
        subPaint.color = theme.sub
        val subCap = min(w, h) * 0.10f
        val subSize = fitSize(subPaint, sub, 12f * density, 9f * density, chordWidth(dySub, rIn))
            .coerceAtMost(subCap)
            .toInt()
            .toFloat()
        subPaint.textSize = subSize

        val subFm = subPaint.fontMetrics
        canvas.drawText(sub, cx, cy + dySub - (subFm.ascent + subFm.descent) / 2f, subPaint)
    }

    /**
     * 圆心处"离圆心 dy"这条水平线上，内圆还剩多宽可写。
     * 留 3dp 呼吸空间，免得文字边缘正好贴住圆环内壁。
     */
    private fun chordWidth(dy: Float, rIn: Float): Float {
        val r = rIn - 3f * density
        if (r <= 0f) return 0f
        val inner = r * r - dy * dy
        if (inner <= 0f) return 0f
        return 2f * sqrt(inner)
    }

    /** 按可用宽度等比缩字号；缩到 minSize 还放不下就继续缩——宁可字小，也不能压到圆环。 */
    private fun fitSize(paint: Paint, text: String, base: Float, min: Float, maxW: Float): Float {
        if (maxW <= 0f) return min
        paint.textSize = base
        val w = paint.measureText(text)
        if (w <= maxW || w <= 0f) return base
        return (base * maxW / w).coerceAtLeast(min)
    }
}
