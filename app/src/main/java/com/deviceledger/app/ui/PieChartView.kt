package com.deviceledger.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import com.deviceledger.app.R
import com.deviceledger.app.model.PieSlice
import com.deviceledger.app.util.Fmt
import com.deviceledger.app.util.L10n
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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
    private val oOut = RectF()
    private val oIn = RectF()
    private val slicePath = Path()
    private val ringPath = Path()

    /** 每片扇区的径向渐变只跟"颜色 + 几何"有关，逐帧新建会一天下来白扔几十万个对象 */
    private val gradCache = HashMap<Int, RadialGradient>()
    private var gradCx = -1f
    private var gradCy = -1f
    private var gradRIn = -1f
    private var gradROut = -1f

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
        val total = totalValue()
        val shown = total * progress
        // 单位跟最终值保持一致，动画过程里才不会在「99,999」和「10.0万」之间跳一下
        return if (mode == "amount") Fmt.symbol(context) + Fmt.moneyCompactAs(context, shown, total)
        else Math.round(shown).toString()
    }

    private fun centerSub(): String = if (mode == "amount") {
        L10n.str(context, R.string.chart_center_amount)
    } else {
        // 圆心下方那个词要跟着台数走单复数：英文一台是 "1 device"，两台才是 "2 devices"
        L10n.quantity(context, R.plurals.p_center_count, Math.round(totalValue()).toInt())
    }

    /**
     * 环形动画走完（progress = 1）时圆心要显示的主数字。
     * 字号按它来定，而不是按逐帧的中间值——否则金额从 0 涨到总额的过程中
     * 字符串越来越长、字号被一路压小，看着像"数字自己在缩水"。
     */
    private fun centerSettled(): String {
        val total = totalValue()
        return if (mode == "amount") Fmt.symbol(context) + Fmt.moneyCompact(context, total)
        else Math.round(total).toString()
    }

    /**
     * 一个色值往白（delta>0）或往黑（delta<0）挪 delta 的比例。
     * 渐变两档由它从扇区自己的主题色派生，而不是另配一套色板：
     * 41 种设备类型每种只有一个色，深色模式下另配色板会亮成一片不像同个 app。
     * 与鸿蒙 `ChartCard.shade` 同参数（内圈 -0.10、外圈 +0.16）。
     */
    private fun shade(color: Int, delta: Float): Int {
        val to = if (delta > 0f) 255f else 0f
        val k = if (delta > 0f) delta else -delta
        fun mix(c: Int): Int {
            val v = (c + (to - c) * k).toInt()
            return if (v < 0) 0 else if (v > 255) 255 else v
        }
        return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
    }

    private fun sliceShader(cx: Float, cy: Float, rIn: Float, rOut: Float, base: Int): Shader {
        if (cx != gradCx || cy != gradCy || rIn != gradRIn || rOut != gradROut) {
            gradCache.clear()
            gradCx = cx
            gradCy = cy
            gradRIn = rIn
            gradROut = rOut
        }
        val hit = gradCache[base]
        if (hit != null) return hit
        val g = RadialGradient(
            cx, cy, rOut,
            // 注意参数顺序：Android 这个构造器是 (颜色数组, 位置数组)，与鸿蒙的
            // addColorStop(位置, 颜色) 正好相反，写反了编译期就报"没有匹配的构造器"
            intArrayOf(shade(base, -0.10f), shade(base, 0.16f)),
            floatArrayOf(rIn / rOut, 1f),
            Shader.TileMode.CLAMP
        )
        gradCache[base] = g
        return g
    }

    /** 整圈扇环（投影与轨道用）：外圆顺时针 + 内圆逆时针，默认 WINDING 就挖出洞 */
    private fun buildRing(path: Path, cx: Float, cy: Float, rIn: Float, rOut: Float, dy: Float) {
        path.reset()
        oOut.set(cx - rOut, cy + dy - rOut, cx + rOut, cy + dy + rOut)
        oIn.set(cx - rIn, cy + dy - rIn, cx + rIn, cy + dy + rIn)
        path.addOval(oOut, Path.Direction.CW)
        path.addOval(oIn, Path.Direction.CCW)
    }

    private fun px(cx: Float, rad: Float, deg: Float): Float =
        cx + rad * cos(Math.toRadians(deg.toDouble()).toFloat())

    private fun py(cy: Float, rad: Float, deg: Float): Float =
        cy + rad * sin(Math.toRadians(deg.toDouble()).toFloat())

    /**
     * 圆角扇环：外弧 → 右下圆角 → 侧边 → 内下圆角 → 内弧 → 左下圆角 → 侧边 → 左上圆角。
     *
     * 圆角半径被三件事卡着：环宽的一半、这一片在内圈上的弧长的一半、在外圈上的弧长的一半。
     * 占比千分之几的窄片会被后两项压到 0，这时**退回直角**——
     * 宁可那一片是个小楔子，也不能让两个圆角互相穿过、把扇面扭成蝴蝶结。
     * 角度用度，弧长比较时才转成弧度，与鸿蒙 `ChartCard.slicePath` 逐条同构。
     */
    private fun buildSlice(path: Path, cx: Float, cy: Float, rIn: Float, rOut: Float,
                           a0: Float, a1: Float, cr: Float) {
        path.reset()
        oOut.set(cx - rOut, cy - rOut, cx + rOut, cy + rOut)
        oIn.set(cx - rIn, cy - rIn, cx + rIn, cy + rIn)
        val w = rOut - rIn
        val spanRad = (a1 - a0) * Math.PI.toFloat() / 180f
        var r = cr
        if (r > w / 2f) r = w / 2f
        if (r > spanRad * rIn / 2f) r = spanRad * rIn / 2f
        if (r > spanRad * rOut / 2f) r = spanRad * rOut / 2f
        if (r < 0.6f) {
            path.arcTo(oOut, a0, a1 - a0, true)
            path.arcTo(oIn, a1, -(a1 - a0), false)
            path.close()
            return
        }
        val aoOut = r / rOut * 180f / Math.PI.toFloat()
        val aoIn = r / rIn * 180f / Math.PI.toFloat()
        path.moveTo(px(cx, rOut, a0 + aoOut), py(cy, rOut, a0 + aoOut))
        path.arcTo(oOut, a0 + aoOut, (a1 - a0) - 2f * aoOut, false)
        // 弧走到头，用"尖角当控制点"的二次曲线拐进侧边；侧边是径向直线
        path.quadTo(px(cx, rOut, a1), py(cy, rOut, a1), px(cx, rOut - r, a1), py(cy, rOut - r, a1))
        path.lineTo(px(cx, rIn + r, a1), py(cy, rIn + r, a1))
        path.quadTo(px(cx, rIn, a1), py(cy, rIn, a1), px(cx, rIn, a1 - aoIn), py(cy, rIn, a1 - aoIn))
        path.arcTo(oIn, a1 - aoIn, -((a1 - a0) - 2f * aoIn), false)
        path.quadTo(px(cx, rIn, a0), py(cy, rIn, a0), px(cx, rIn + r, a0), py(cy, rIn + r, a0))
        path.lineTo(px(cx, rOut - r, a0), py(cy, rOut - r, a0))
        path.quadTo(px(cx, rOut, a0), py(cy, rOut, a0), px(cx, rOut, a0 + aoOut), py(cy, rOut, a0 + aoOut))
        path.close()
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

        val total = totalValue()

        /* ============ 材质：投影 + 轨道先落底，扇片贴上去 ============ */
        // 投影画成"整体下移 2dp 的一份低透明度环形"，不用 setShadowLayer：
        // 硬件加速画布上它对填充路径的支持要看 API 级别。两端要长得一模一样，
        // 就只用两边行为完全一致的原语（鸿蒙 ChartCard 同法同参数）。
        arcPaint.shader = null
        arcPaint.style = Paint.Style.FILL
        arcPaint.color = if (theme.dark) 0x57000000 else 0x1A000000
        buildRing(ringPath, cx, cy, rIn, rOut, 2f * density)
        canvas.drawPath(ringPath, arcPaint)
        arcPaint.color = theme.trackBg
        buildRing(ringPath, cx, cy, rIn, rOut, 0f)
        canvas.drawPath(ringPath, arcPaint)

        if (total > 0.0 && slices.isNotEmpty()) {
            val startAngle = -90f
            val fullSweep = 360f * progress
            var drawn = 0f
            // 相邻两段之间留 3dp 的缝。换算成圆心角后再让位，而不是给每段描一圈边框：
            // stroke 是以路径为中心向两侧各铺一半的，会把两侧的扇面各吃掉一点，
            // 份额小的那一段看着就比图例的百分比窄，而且每段的起止角都不准
            val gapDeg = (3f * density / ringRadius) * (180f / Math.PI.toFloat())
            // 摆得太满时别让缝把小份额那一段整个吃掉
            val minSweep = 1.2f

            for (i in slices.indices) {
                if (drawn >= fullSweep) break
                val frac = (slices[i].v / total).toFloat()
                // 最后一段直接收在整圈上，避免逐段累加的浮点误差在 12 点方向留一道细缝
                val fullSliceSweep = if (i == slices.size - 1) {
                    (360f - drawn).coerceAtLeast(minSweep)
                } else {
                    360f * frac
                }
                val visible = min(fullSliceSweep, fullSweep - drawn)
                // 整段都显示出来时才让出尾巴上的缝，否则动画推进中的那一段会一跳一跳地变窄
                val whole = visible >= fullSliceSweep - 0.01f
                val sweep = if (whole) {
                    (visible - gapDeg).coerceAtLeast(minSweep).coerceAtMost(visible)
                } else {
                    visible
                }
                if (sweep > 0.05f) {
                    // 圆角扇环 + 从内圈到外圈的径向渐变（内暗外亮），色由本片主题色派生
                    buildSlice(slicePath, cx, cy, rIn, rOut,
                        startAngle + drawn, startAngle + drawn + sweep, 4f * density)
                    arcPaint.shader = sliceShader(cx, cy, rIn, rOut,
                        runCatching { Color.parseColor(slices[i].c) }.getOrDefault(Color.GRAY))
                    canvas.drawPath(slicePath, arcPaint)
                }
                drawn += visible
            }
        }

        /* ============ 内圈高光：1.2dp 的一圈浅色描边，把"洞口"边缘立起来 ============ */
        arcPaint.shader = null
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeWidth = 1.2f * density
        arcPaint.color = if (theme.dark) 0x1AFFFFFF else 0x0D000000
        canvas.drawCircle(cx, cy, rIn - 1.4f * density, arcPaint)

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
