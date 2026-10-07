package com.deviceledger.app.util

import java.util.Locale

/** 金额格式化工具 */
object Fmt {

    /** 金额：整数不带小数，超过三位加千分位 */
    fun money(v: Double): String {
        val safe = if (v.isNaN() || v.isInfinite()) 0.0 else v
        val neg = safe < 0
        var s = String.format(Locale.US, "%.2f", Math.abs(safe))
        if (s.endsWith(".00")) s = s.substring(0, s.length - 3)
        val parts = s.split(".")
        val intPart = parts[0]
        val sb = StringBuilder()
        for (i in intPart.indices) {
            if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(',')
            sb.append(intPart[i])
        }
        if (parts.size > 1) sb.append('.').append(parts[1])
        return (if (neg) "-" else "") + sb.toString()
    }

    /**
     * 大金额缩写：满 10 万写成「10.0万」，满 1 亿写成「1.2亿」，其余照常写全。
     * 只用于空间紧张的地方（如扇形图圆心），列表和汇总卡仍用完整的 money()。
     */
    fun moneyCompact(v: Double): String = moneyCompactAs(v, v)

    /**
     * 按 ref 的量级决定用不用缩写，但换算的是 v。
     * 滚动动画里金额是从 0 涨上去的，中途会跨过 10 万这条线；
     * 让过程值跟最终值共用同一套单位，数字才不会中途从「99,999」跳成「10.0万」。
     */
    fun moneyCompactAs(v: Double, ref: Double): String {
        val safe = if (v.isNaN() || v.isInfinite()) 0.0 else v
        val a = Math.abs(if (ref.isNaN() || ref.isInfinite()) 0.0 else ref)
        // 先用"万"试算一次：99,999,999 四舍五入后会变成 10000.0万，这种怪结果直接改用亿
        val wan = Math.round(a / 1000.0) / 10.0
        return when {
            wan >= 10000.0 -> oneDecimal(safe / 1e8) + "亿"
            a >= 1e5 -> oneDecimal(safe / 1e4) + "万"
            else -> money(safe)
        }
    }

    /** 保留一位小数（10.0 / 123.5） */
    private fun oneDecimal(x: Double): String {
        val s = String.format(Locale.US, "%.1f", x)
        return s
    }

    /** 日均金额：小于 0.01 时给出提示，避免出现 0.00 的误导 */
    fun smallMoney(v: Double): String {
        if (v > 0 && v < 0.01) return "<0.01"
        return money(v)
    }

    /** 百分比，0~1 */
    fun percent(v: Double): String {
        val p = v * 100.0
        val rounded = Math.round(p).toInt()
        return rounded.toString() + "%"
    }

    /** 给 #RRGGBB 加上透明度前缀（Android 的 8 位色值是 #AARRGGBB） */
    fun tint(hex: String, alpha: String): String {
        if (hex.length == 7 && hex.startsWith("#")) return "#" + alpha + hex.substring(1)
        return hex
    }
}
