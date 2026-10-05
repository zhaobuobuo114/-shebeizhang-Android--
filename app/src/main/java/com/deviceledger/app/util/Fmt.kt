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
