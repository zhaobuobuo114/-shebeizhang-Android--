package com.deviceledger.app

import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * 语言不能碰的东西。
 *
 * v2.0.0 加了四语言之后，最危险的回归不是"某个字没翻"，而是**存储与数字格式跟着
 * 设备语言漂**：buyDate 一旦按本地习惯写成 2026/03/05 或 5.3.2026，
 * 另一端就认不出日期；小数点一旦变成逗号，价格解析会静默截断成整数。
 * 导出文件是两端共用的，这类回归不会报错，只会让数据悄悄变脏。
 *
 * 这里全部只测不需要 Context 的那半套（带文案的走设备端截图验）。
 */
class LocaleSafetyTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    /** 挑几个"格式习惯明显不同"的语言：德语/法语用逗号做小数点，阿拉伯语数字形态不同 */
    private val HOSTILE = listOf(
        Locale.GERMANY, Locale.FRANCE, Locale.ITALY,
        Locale("ar", "EG"), Locale("hi", "IN"), Locale("th", "TH"),
        Locale("ja", "JP"), Locale("zh", "TW")
    )

    @Test
    fun storageFormatIsIdenticalUnderEveryHostileLocale() {
        val expected = "2026-03-05"
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            assertEquals("默认语言 $loc 下存储格式变了", expected, DateUtil.format(2026, 3, 5))
            assertEquals("默认语言 $loc 下补零失效", "2026-01-09", DateUtil.format(2026, 1, 9))
        }
    }

    @Test
    fun parseAcceptsTheCanonicalFormatUnderEveryHostileLocale() {
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            val parts = DateUtil.parts("2026-03-05")
            assertNotNull("默认语言 $loc 下解析不出标准日期", parts)
            assertEquals("默认语言 $loc 下年份读错", 2026, parts!![0])
            assertEquals("默认语言 $loc 下月份读错", 3, parts[1])
            assertEquals("默认语言 $loc 下日期读错", 5, parts[2])
            // 斜杠写法从来就不是我们的存储格式，任何语言下都不该被"顺手兼容"
            assertNull("默认语言 $loc 下把斜杠日期误判成了合法", DateUtil.parts("2026/03/05"))
        }
    }

    @Test
    fun moneyKeepsAsciiDigitsAndDotDecimalRegardlessOfLocale() {
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            assertEquals("默认语言 $loc 下千分位或小数点被本地化了", "18,297", Fmt.money(18297.0))
            assertEquals("默认语言 $loc 下小数格式漂移", "12.05", Fmt.money(12.05))
            assertEquals("默认语言 $loc 下负号或两位小数漂移", "-1,234.50", Fmt.money(-1234.5))
            assertEquals("默认语言 $loc 下 <0.01 提示漂移", "<0.01", Fmt.smallMoney(0.004))
            assertEquals("默认语言 $loc 下百分号漂移", "82%", Fmt.percent(0.823))
        }
    }

    /** 独立算出的公历串，作为"生产代码没被语言带跑"的对照基准 */
    private fun gregorian(offsetDays: Int): String {
        val c = Calendar.getInstance(Locale.ROOT)
        c.add(Calendar.DAY_OF_MONTH, offsetDays)
        return String.format(
            Locale.ROOT, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    @Test
    fun sanitizeStillRepairsDirtyDatesUnderEveryHostileLocale() {
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            // 不补零的老写法要修成标准串，而不是被当成"读不懂"退化成今天
            assertEquals("默认语言 $loc 下没补零", "2026-01-05", DateUtil.sanitize("2026-1-5"))
            // 本来就规范的串必须原样返回，不能被"修"成别的日期
            assertEquals("默认语言 $loc 下把规范日期改写了", "2026-03-05", DateUtil.sanitize("2026-03-05"))
            // 不存在的一天退回今天：这里要比对**具体值**，
            // 早先只查长度和分隔符位置，非公历日历下退化成 1447-xx-xx 也能过关
            assertEquals("默认语言 $loc 下回退结果不是公历今天", gregorian(0), DateUtil.sanitize("2025-02-30"))
        }
    }

    @Test
    fun todayIsGregorianRegardlessOfDefaultLocale() {
        val expected = gregorian(0)
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            assertEquals("默认语言 $loc 下 today() 用了非公历年", expected, DateUtil.today())
        }
    }

    @Test
    fun daysSinceIsUnaffectedByLocale() {
        val expectedToday = gregorian(0)
        val thirtyDaysAgo = gregorian(-30)
        for (loc in HOSTILE) {
            Locale.setDefault(loc)
            assertEquals("默认语言 $loc 下今天到自己算不出 0", 0, DateUtil.daysSince(DateUtil.today()))
            assertEquals("默认语言 $loc 下今天算错", expectedToday, DateUtil.today())
            assertEquals("默认语言 $loc 下 30 天前的天数算错", 30, DateUtil.daysSince(thirtyDaysAgo))
        }
    }
}
