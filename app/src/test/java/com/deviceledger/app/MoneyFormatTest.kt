package com.deviceledger.app

import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额缩写与价格解析的边界。
 *
 * 这两处都是"看着对、边界错"的典型：缩写档位原先只按量级判，
 * 999,950 用 K 档四舍五入后就成了 `1000.0K`（它其实已经是 1.0M），
 * 而 B 档在 1e12 附近会写成 `1000.0B`。汉字那侧的同类问题早就有保护，
 * 英文那侧漏了——所以这里把两档的边界逐点钉住。
 *
 * 表里的期望值与鸿蒙端 `_test_fmt_tier.mjs` 是同一张，改一边必须改另一边。
 */
class MoneyFormatTest {

    /** 缩写档位：null 表示不缩写、写全数字 */
    private val EN_TIERS = listOf(
        0.0 to null,
        99_999.0 to null,
        99_999.5 to null,
        100_000.0 to Fmt.Compact.K,
        123_456.0 to Fmt.Compact.K,
        999_949.0 to Fmt.Compact.K,
        // 以下三条就是原先会显示成 1000.0K 的那一段
        999_950.0 to Fmt.Compact.M,
        999_999.0 to Fmt.Compact.M,
        1_000_000.0 to Fmt.Compact.M,
        999_949_999.0 to Fmt.Compact.M,
        // 这一段原先会显示成 1000.0M
        999_950_000.0 to Fmt.Compact.B,
        999_999_999.0 to Fmt.Compact.B,
        1_000_000_000.0 to Fmt.Compact.B,
        999_949_999_999.0 to Fmt.Compact.B,
        // B 是最大一档，进位到 1000.0B 就不缩了（原先会真写成 1000.0B）
        999_950_000_000.0 to null,
        1_000_000_000_000.0 to null,
        9.9e12 to null
    )

    private val ZH_TIERS = listOf(
        0.0 to null,
        99_999.0 to null,
        100_000.0 to Fmt.Compact.WAN,
        123_456.0 to Fmt.Compact.WAN,
        9_999_499.0 to Fmt.Compact.WAN,
        // 9,999,950 用万档是 1000.0万，没到 10000 就不必升档；真正的升档点是 99,999,500
        9_999_950.0 to Fmt.Compact.WAN,
        99_994_999.0 to Fmt.Compact.WAN,
        99_999_499.0 to Fmt.Compact.WAN,
        // 这一段用万档会四舍五入成 10000.0万，改用亿
        99_999_500.0 to Fmt.Compact.YI,
        99_999_999.0 to Fmt.Compact.YI,
        100_000_000.0 to Fmt.Compact.YI,
        1e12 to Fmt.Compact.YI
    )

    @Test
    fun englishTiersNeverRoundUpTo1000() {
        for ((a, want) in EN_TIERS) {
            assertEquals("a=$a", want, Fmt.tierOf(a, false))
        }
    }

    @Test
    fun chineseTiersNeverRoundUpTo10000Wan() {
        for ((a, want) in ZH_TIERS) {
            assertEquals("a=$a", want, Fmt.tierOf(a, true))
        }
    }

    /** 档位除数与单位资源必须成对，缩写时是"值 / 除数 + 单位" */
    @Test
    fun compactDivisors() {
        assertEquals(1e3, Fmt.Compact.K.divisor, 0.0)
        assertEquals(1e6, Fmt.Compact.M.divisor, 0.0)
        assertEquals(1e9, Fmt.Compact.B.divisor, 0.0)
        assertEquals(1e4, Fmt.Compact.WAN.divisor, 0.0)
        assertEquals(1e8, Fmt.Compact.YI.divisor, 0.0)
    }

    @Test
    fun moneyKeepsAsciiDigitsAndNoNegativeZero() {
        assertEquals("0", Fmt.money(0.0))
        assertEquals("1,234,567", Fmt.money(1234567.0))
        // 带小数的一律写满两位（"整数不带小数"只对 .00 那种）
        assertEquals("1,234.50", Fmt.money(1234.5))
        assertEquals("-1,234.50", Fmt.money(-1234.5))
        // -0.004 会被 %.2f 收成 "0.00"，挂上负号就是一个平白无故的 "-0"
        assertEquals("0", Fmt.money(-0.004))
        assertEquals("0", Fmt.money(0.004))
        // NaN / Infinity 不许漏进界面（扇形图与价格解析都靠这条兜底）
        assertEquals("0", Fmt.money(Double.NaN))
        assertEquals("0", Fmt.money(Double.POSITIVE_INFINITY))
        assertEquals("0", Fmt.money(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun smallMoneyFlagsSubCentAmounts() {
        assertEquals("<0.01", Fmt.smallMoney(0.004))
        assertEquals("0", Fmt.smallMoney(0.0))
        assertEquals("12.34", Fmt.smallMoney(12.34))
        assertEquals("0", Fmt.smallMoney(Double.NaN))
    }

    /**
     * 价格输入解析。
     *
     * 逗号必须收：`numberDecimal` 在小数点是逗号的语言（德/法/意/俄）下就吐逗号。
     * 但绝不能"读到哪算哪"——鸿蒙端原先用 parseFloat，"12abc" 拿回 12、
     * "1234,56" 拿回 1234，静默把截断值存进账本，比报个错糟得多。
     */
    @Test
    fun priceInputIsParsedStrictly() {
        assertEquals(1234.5, Fmt.parsePrice("1234.5")!!, 0.0)
        assertEquals(1234.5, Fmt.parsePrice("1234,5")!!, 0.0)
        assertEquals(1234.0, Fmt.parsePrice("1234.")!!, 0.0)
        assertEquals(0.5, Fmt.parsePrice(".5")!!, 0.0)
        assertEquals(1234.0, Fmt.parsePrice("1234")!!, 0.0)
        assertNull("带字母必须判无效，不能静默截断", Fmt.parsePrice("12abc"))
        assertNull(Fmt.parsePrice("abc12"))
        assertNull(Fmt.parsePrice("1e3"))
        assertNull(Fmt.parsePrice("1.2.3"))
        assertNull(Fmt.parsePrice("1,2.3"))
        assertNull(Fmt.parsePrice("1,2,3"))
        assertNull(Fmt.parsePrice(""))
        assertNull(Fmt.parsePrice(" "))
        assertNull(Fmt.parsePrice("."))
        assertNull("负数交给上层的价格校验，这里不收", Fmt.parsePrice("-5"))
    }

    /**
     * 取一位小数与鸿蒙 `Fmt.oneDecimal` 必须逐值一致，表里的数是**真机量出来的**
     * （jbr-21 跑 `String.format("%.1f", x)`，Node 跑 `Math.round(x*10)/10`，18 个探针全等）。
     *
     * 为什么专门钉这条：《BUG报告》B8 声称"Java 的 %.1f 对精确二进制值 HALF_UP，
     * 所以 12.35 该舍成 12.3、鸿蒙的 round10 会进成 12.4，两端差 0.1"。那是拿
     * `Decimal(float).quantize(HALF_UP)` 模拟 Java 得到的结论 —— 实际 JVM 给的是 12.4。
     * 照着它把鸿蒙改成 `toFixed(1)` 反而会制造出 6 处分歧（12.35/55.55/56.05/5.55/0.15/0.35）。
     * 所以这里两侧都按实测写死，谁再"顺手统一"就会被这条判红。
     *
     * 全是非负值：`oneDecimal` 只被金额缩写调用，而价格经 normalize 后恒 >= 0。
     * 负数上两边定义确实不同（Java `-12.35` → `-12.4`，round10 → `-12.3`），走不到，不写成断言。
     */
    @Test
    fun oneDecimalMatchesHarmonyos() {
        val cases = listOf(
            12.35 to "12.4", 55.55 to "55.6", 56.05 to "56.1", 5.55 to "5.6",
            8.65 to "8.7", 2.675 to "2.7", 999.95 to "1000.0", 0.05 to "0.1",
            0.25 to "0.3", 1.005 to "1.0", 123.456 to "123.5", 0.15 to "0.2",
            2.5 to "2.5", 12.3499999 to "12.3", 0.0 to "0.0", 0.35 to "0.4",
            1.0499999 to "1.0", 7.25 to "7.3"
        )
        for ((v, want) in cases) assertEquals("oneDecimal($v)", want, Fmt.oneDecimal(v))
    }

    /** 缺 id 时补出来的那批必须互不相同（原先只靠 0..9999 随机，100 条撞车概率约 39%） */
    @Test
    fun fallbackIdsAreUniqueWithinABatch() {
        val ids = (1..500).map { DeviceCalc.fallbackId(0L) }.toSet()
        assertEquals("同一批补出来的 id 必须互不相同", 500, ids.size)
        assertTrue(ids.all { it.startsWith("d0-") })
    }

    /**
     * 录入上限与清洗上限必须是同一个数。
     *
     * 之前只有清洗侧钳（`normalize` 把 > MAX_PRICE 归零），录入侧不拦：
     * 一个 13 位的价格能录进来、当场显示正常，杀掉进程重开就静默变成 ¥0。
     */
    @Test
    fun priceCeilingIsShared() {
        assertEquals(1e12, DeviceCalc.MAX_PRICE, 0.0)
        assertNotNull("上限本身要能被录入解析接受", Fmt.parsePrice("1000000000000"))
        assertEquals(1e12, Fmt.parsePrice("1000000000000")!!, 0.0)
        assertTrue("超过上限的必须被清洗归零", Fmt.parsePrice("2000000000000")!! > DeviceCalc.MAX_PRICE)
    }
}
