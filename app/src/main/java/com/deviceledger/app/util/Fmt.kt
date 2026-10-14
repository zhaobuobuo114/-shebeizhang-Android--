package com.deviceledger.app.util

import android.content.Context
import com.deviceledger.app.R
import java.util.Locale

/**
 * 金额格式化工具。
 *
 * 数字本身不跟着语言走：小数点恒为 `.`、千分位恒为 `,`、数字恒为 ASCII。
 * 这是刻意的——用 `String.format(Locale.US, …)` 而不是默认 Locale，
 * 否则某些语言环境会把 1234 排成 ١٬٢٣٤，扇形图的宽度自适应和价格输入的解析都会跟着崩。
 * 只有"万/亿"这类计数单位随语言变（英文改用 K/M/B）。
 */
object Fmt {

    /**
     * 货币符号按语言从文案表取：简中/日文 `¥`、繁中 `NT$`、英文 `$`。
     * **只换符号，数值不做任何汇率换算** —— 同一笔账切英文会从 ¥1,280 变成 $1,280，
     * 显示口径如此，账本里存的仍是用户录入的那个数。
     */
    fun symbol(context: Context): String = context.getString(R.string.fmt_currency)

    /** 带符号的金额 */
    fun moneyLabel(context: Context, v: Double): String = symbol(context) + money(v)

    /** 带符号的日均金额 */
    fun smallMoneyLabel(context: Context, v: Double): String = symbol(context) + smallMoney(v)

    /** 金额：整数不带小数，超过三位加千分位 */
    fun money(v: Double): String {
        val safe = if (v.isNaN() || v.isInfinite()) 0.0 else v
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
        // -0.004 会被 %.2f 收成 "0.00"、裁掉小数后只剩 "0"，再挂负号就是一个平白无故的 "-0"
        val neg = safe < 0 && sb.toString() != "0"
        return (if (neg) "-" else "") + sb.toString()
    }

    /**
     * 大金额缩写：满 10 万写成「10.0万」，满 1 亿写成「1.2亿」，其余照常写全；英文按 K/M/B。
     * 只用于空间紧张的地方（扇形图圆心），列表和汇总卡仍用完整的 money()。
     */
    fun moneyCompact(context: Context, v: Double): String = moneyCompactAs(context, v, v)

    /**
     * 按 ref 的量级决定用不用缩写，但换算的是 v。
     * 滚动动画里金额是从 0 涨上去的，中途会跨过 10 万这条线；
     * 让过程值跟最终值共用同一套单位，数字才不会中途从「99,999」跳成「10.0万」。
     */
    fun moneyCompactAs(context: Context, v: Double, ref: Double): String {
        val safe = if (v.isNaN() || v.isInfinite()) 0.0 else v
        val a = Math.abs(if (ref.isNaN() || ref.isInfinite()) 0.0 else ref)
        val tier = tierOf(a, L10n.useWanNumerals()) ?: return money(safe)
        return oneDecimal(safe / tier.divisor) + context.getString(tier.unitRes)
    }

    /** 一档缩写：除数与单位资源放在一起，判档和换算就不会各写一份 */
    enum class Compact(val divisor: Double, val unitRes: Int) {
        WAN(1e4, R.string.fmt_wan),
        YI(1e8, R.string.fmt_yi),
        K(1e3, R.string.fmt_k),
        M(1e6, R.string.fmt_m),
        B(1e9, R.string.fmt_b)
    }

    /**
     * 该用哪一档，`null` 表示不缩写、写全数字。
     *
     * 两条分支都从 1e5 起缩（英文要是从千位就缩写，列表里绝大多数设备都会写成
     * 1.2K 这种读着别扭的样子）。判档不能只看量级，还得看**缩写之后会不会进位到 1000**：
     * 999,950 用 K 档会显示成 `1000.0K`，而它其实已经是 1.0M 了，所以要升一档；
     * 同理 999,950,000 用 M 档会写成 `1000.0M` → 改用 B；
     * 而 B 是英文最大的一档，999,950,000,000 用 B 会写成 `1000.0B`，无处可升 → 写全。
     * 汉字那侧的 `99,999,999 → 10000.0万` 是同一个道理，亿档没有上界
     * （1e12 写成 `10000.0亿` 中文读得通，就保留）。
     *
     * 抽成纯函数是为了能在 JVM 里逐边界测（`moneyCompact` 要 Context，单测拿不到）。
     * 鸿蒙端 `utils/Format.ets` 的 `tierOf` 是同一套判定，改动必须同步过去，
     * 边界表在 `_test_fmt_tier.mjs` 里逐条对着。
     */
    fun tierOf(a: Double, wanNumerals: Boolean): Compact? {
        if (wanNumerals) {
            val wan = Math.round(a / 1000.0) / 10.0
            return when {
                a >= 1e8 -> Compact.YI
                a >= 1e5 -> if (wan < 10000.0) Compact.WAN else Compact.YI
                else -> null
            }
        }
        val kb = Math.round(a / 100.0) / 10.0
        val mb = Math.round(a / 1e5) / 10.0
        val bb = Math.round(a / 1e8) / 10.0
        return when {
            a >= 1e9 -> if (bb < 1000.0) Compact.B else null
            a >= 1e6 -> if (mb < 1000.0) Compact.M else Compact.B
            a >= 1e5 -> if (kb < 1000.0) Compact.K else Compact.M
            else -> null
        }
    }

    /**
     * 保留一位小数（10.0 / 123.5）。公开的只有一个原因：与鸿蒙 `Fmt.oneDecimal` 逐值对测。
     *
     * 注意 Java 的 `%f` **不是**"对精确二进制值做 HALF_UP"——那是常见的误记，
     * 用 `BigDecimal(double).quantize(HALF_UP)` 模拟会得出与实际相反的结果：
     * 实测（jbr-21）`String.format("%.1f", 12.35)` 得到 `12.4`，而精确值 12.3499999… 按
     * HALF_UP 该是 12.3。鸿蒙侧对应的写法是 `Math.round(x * 10) / 10`，
     * 非负值上 18 个探针逐位一致；`x.toFixed(1)` 反而有 6 处偏离，别照它改。
     * 详见 `HarmonyOS/entry/src/main/ets/utils/Format.ets` 里那张实测表。
     */
    fun oneDecimal(x: Double): String = String.format(Locale.US, "%.1f", x)

    /** 日均金额：小于 0.01 时给出提示，避免出现 0.00 的误导 */
    fun smallMoney(v: Double): String {
        val safe = if (v.isNaN() || v.isInfinite()) 0.0 else v
        if (safe > 0 && safe < 0.01) return "<0.01"
        return money(safe)
    }

    /**
     * 价格输入的严格解析：不合法返回 null。
     *
     * 两条理由：
     * 1. 逗号。输入框是 `numberDecimal`，在小数点写成逗号的语言（德/法/意/俄）下
     *    输入法就吐逗号，而 `toDoubleOrNull` 只认点——用户输入 1234,56 会被告知"价格无效"。
     *    这里只在"读用户输入"这一步把逗号当小数点收，存储与解析恒为 ASCII 点。
     * 2. 严格。原先鸿蒙侧用 `parseFloat`，"12abc" 拿回 12、"1234,56" 拿回 1234，
     *    静默把截断后的整数存进用户账本，比报个错糟得多。两端一律整串匹配才算合法。
     *
     * 允许 `12` / `12.5` / `12.` / `.5`；拒绝 `1e3`、`.`、带字母、点逗号混用、多个逗号。
     */
    fun parsePrice(text: String): Double? {
        val first = text.indexOf(',')
        val s = if (first >= 0) {
            if (text.indexOf('.') >= 0 || text.indexOf(',', first + 1) >= 0) return null
            text.replace(',', '.')
        } else {
            text
        }
        if (!PRICE.matches(s)) return null
        return s.toDoubleOrNull()
    }

    private val PRICE = Regex("""^(?:\d+(?:\.\d*)?|\.\d+)$""")

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
