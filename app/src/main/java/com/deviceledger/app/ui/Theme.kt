package com.deviceledger.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * 昼夜双主题配色。所有界面颜色都走这里，切换主题时整体重绘 / 插值过渡。
 * 颜色值与鸿蒙版本完全一致，保证两个平台视觉一致。
 */
data class AppTheme(
    val dark: Boolean,
    val bg: Int,          // 页面底色
    val bgTop: Int,       // 顶部渐变色
    val card: Int,        // 卡片
    val card2: Int,       // 次级卡片 / 内嵌块
    val text: Int,        // 主文字
    val text2: Int,       // 正文
    val sub: Int,         // 次要文字
    val line: Int,        // 分割线
    val primary: Int,     // 主色
    val primary2: Int,    // 主色渐变终点
    val primarySoft: Int, // 主色浅底
    val onPrimary: Int,   // 主色上的文字
    val accent: Int,      // 强调色（日均）
    val gold: Int,        // 收藏星标的金色
    val danger: Int,
    val field: Int,       // 输入框底
    val chipBg: Int,
    val chipText: Int,
    val iconBg: Int,      // 图标底板
    val shadow: Int,
    val scrim: Int,       // 遮罩
    val trackBg: Int      // 进度 / 空环底色
) {
    companion object {

        private fun c(hex: String): Int = Color.parseColor(hex)

        fun lightTheme(): AppTheme = AppTheme(
            dark = false,
            bg = c("#F1F4F9"),
            bgTop = c("#E7EFFF"),
            card = c("#FFFFFF"),
            card2 = c("#F5F7FB"),
            text = c("#171A21"),
            text2 = c("#3E4655"),
            sub = c("#8B94A3"),
            line = c("#EDF0F6"),
            primary = c("#2B5CFF"),
            primary2 = c("#6E8CFF"),
            primarySoft = c("#E9F0FF"),
            onPrimary = c("#FFFFFF"),
            accent = c("#FF7A45"),
            gold = c("#F5A623"),
            danger = c("#FF4D4F"),
            field = c("#F2F4F8"),
            chipBg = c("#E8ECF3"),
            chipText = c("#5A6472"),
            iconBg = c("#EFF3FB"),
            shadow = c("#141A2B"),
            scrim = c("#66000000"),
            trackBg = c("#E8ECF3")
        )

        fun darkTheme(): AppTheme = AppTheme(
            dark = true,
            bg = c("#10131A"),
            bgTop = c("#171D2B"),
            card = c("#1A1F2A"),
            card2 = c("#232937"),
            text = c("#F3F6FB"),
            text2 = c("#C7D0DE"),
            sub = c("#818B9C"),
            line = c("#252C39"),
            primary = c("#5B8CFF"),
            primary2 = c("#8AA7FF"),
            primarySoft = c("#212B45"),
            onPrimary = c("#0B0E14"),
            accent = c("#FFA171"),
            gold = c("#FFC04D"),
            danger = c("#FF6B6D"),
            field = c("#242B39"),
            chipBg = c("#2A3140"),
            chipText = c("#AEB8C7"),
            iconBg = c("#232937"),
            shadow = c("#000000"),
            scrim = c("#99000000"),
            trackBg = c("#2A3140")
        )

        fun of(dark: Boolean): AppTheme = if (dark) darkTheme() else lightTheme()

        /** 两个颜色之间插值 */
        fun mixColor(from: Int, to: Int, t: Float): Int {
            val k = when {
                t < 0f -> 0f
                t > 1f -> 1f
                else -> t
            }
            val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * k).toInt()
            val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * k).toInt()
            val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * k).toInt()
            val a = Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * k).toInt()
            return Color.argb(a, r, g, b)
        }

        /** 两套主题之间插值，用于昼夜切换的平滑过渡 */
        fun lerp(from: AppTheme, to: AppTheme, t: Float): AppTheme = AppTheme(
            dark = to.dark,
            bg = mixColor(from.bg, to.bg, t),
            bgTop = mixColor(from.bgTop, to.bgTop, t),
            card = mixColor(from.card, to.card, t),
            card2 = mixColor(from.card2, to.card2, t),
            text = mixColor(from.text, to.text, t),
            text2 = mixColor(from.text2, to.text2, t),
            sub = mixColor(from.sub, to.sub, t),
            line = mixColor(from.line, to.line, t),
            primary = mixColor(from.primary, to.primary, t),
            primary2 = mixColor(from.primary2, to.primary2, t),
            primarySoft = mixColor(from.primarySoft, to.primarySoft, t),
            onPrimary = mixColor(from.onPrimary, to.onPrimary, t),
            accent = mixColor(from.accent, to.accent, t),
            gold = mixColor(from.gold, to.gold, t),
            danger = mixColor(from.danger, to.danger, t),
            field = mixColor(from.field, to.field, t),
            chipBg = mixColor(from.chipBg, to.chipBg, t),
            chipText = mixColor(from.chipText, to.chipText, t),
            iconBg = mixColor(from.iconBg, to.iconBg, t),
            shadow = mixColor(from.shadow, to.shadow, t),
            scrim = mixColor(from.scrim, to.scrim, t),
            trackBg = mixColor(from.trackBg, to.trackBg, t)
        )
    }
}

/** 总览卡上的固定文字色（两套主题一致，卡片本身是主色渐变） */
object CardColors {
    val softText: Int = Color.parseColor("#D8E3FF")
    val softAccent: Int = Color.parseColor("#FFD9C0")
    val badgeBg: Int = Color.parseColor("#26FFFFFF")
    val whiteText: Int = Color.parseColor("#FFFFFF")
}

/** 支持自定义停靠点的渐变背景（用于页面底色与总览卡） */
class GradientBgDrawable(
    private val colors: IntArray,
    private val positions: FloatArray?,
    private val diagonal: Boolean = false
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val shader = if (diagonal) {
            LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                colors, positions, Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
                colors, positions, Shader.TileMode.CLAMP
            )
        }
        paint.shader = shader
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OverridingDeprecatedMember")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 圆角矩形背景 */
fun roundedDrawable(color: Int, radiusDp: Float, density: Float): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.setColor(color)
    d.cornerRadius = radiusDp * density
    return d
}

/** 只圆上面两个角的背景（底部面板） */
fun roundedTopDrawable(color: Int, radiusDp: Float, density: Float): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.setColor(color)
    val r = radiusDp * density
    d.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    return d
}

/** 带描边的圆角矩形背景 */
fun borderedDrawable(
    color: Int,
    radiusDp: Float,
    strokeColor: Int,
    strokeDp: Float,
    density: Float
): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.setColor(color)
    d.cornerRadius = radiusDp * density
    d.setStroke((strokeDp * density).toInt(), strokeColor)
    return d
}

/** 圆形背景 */
fun circleDrawable(color: Int): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(color)
    return d
}
