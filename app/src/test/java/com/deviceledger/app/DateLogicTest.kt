package com.deviceledger.app

import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.util.DateUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 日期与脏数据清洗的回归测试。
 *
 * 这组用例是给"两端必须算出同一个数"兜底的：鸿蒙版 DateUtil 用的是同一套
 * 正午日序号算法，任何一端改动导致结果不同，这里会先红。
 *
 * java.time 只在测试里出现 —— 它要 API 26，而工程 minSdk 24，主源码不能碰。
 */
class DateLogicTest {

    private val DAY_MS = 24L * 60L * 60L * 1000L

    private lateinit var originalZone: TimeZone

    /** 全部用例都跑在一个真会拨钟的时区里，否则夏令时那条等于没测 */
    @Before
    fun useDstZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    /**
     * 2026-03-08 美国拨快一小时。旧写法（两个本地零点相减再除以一天毫秒）在这里会少算一天，
     * 因为那一整天只有 23 小时；正午日序号相减不受影响。
     * 断言基准是 java.time 的自然日差，与实现无关。
     */
    @Test
    fun crossingSpringForwardKeepsExactDayCount() {
        val purchase = LocalDate.of(2026, 3, 1)
        val expected = ChronoUnit.DAYS.between(purchase, LocalDate.now())
        assertTrue("用例依赖今天晚于 2026-03-08", expected > 0)
        assertEquals(expected, DateUtil.daysSince(purchase.toString()).toLong())
    }

    @Test
    fun daysSinceCountsCalendarDays() {
        val purchase = LocalDate.now().minusDays(37)
        assertEquals(37, DateUtil.daysSince(purchase.toString()))
        assertEquals(0, DateUtil.daysSince(LocalDate.now().toString()))
    }

    /** 未来的购买日期不算负天数，钳成 0（当天即已用 1 天） */
    @Test
    fun futureDateClampsToZero() {
        assertEquals(0, DateUtil.daysSince(LocalDate.now().plusDays(30).toString()))
    }

    @Test
    fun garbageDateYieldsZeroDays() {
        assertEquals(0, DateUtil.daysSince(""))
        assertEquals(0, DateUtil.daysSince("not a date"))
        assertEquals(0, DateUtil.daysSince("2026-13-45"))
    }

    /** 老数据里不补零的写法要修成标准串，而不是干脆丢掉当成今天 */
    @Test
    fun sanitizeRepairsUnpaddedDates() {
        assertEquals("2026-01-05", DateUtil.sanitize("2026-1-5"))
        assertEquals("2026-01-05", DateUtil.sanitize("2026-01-05"))
        assertEquals("1999-12-01", DateUtil.sanitize("1999-12-1"))
    }

    @Test
    fun sanitizeFallsBackToToday() {
        val today = LocalDate.now().toString()
        assertEquals(today, DateUtil.sanitize("2025-02-30"))
        assertEquals(today, DateUtil.sanitize("abc"))
        assertEquals(today, DateUtil.sanitize(""))
        assertEquals(today, DateUtil.sanitize(null))
    }

    /** 购买当天算第 1 天，日均就等于整台价格 */
    @Test
    fun firstDayCountsAsOneDayUsed() {
        val today = DeviceItem("id", "测试机", 1000.0, LocalDate.now().toString(), "phone", "", false, 1L)
        assertEquals(1, DeviceCalc.daysUsed(today))
        assertEquals(1000.0, DeviceCalc.avgPerDay(today), 0.0001)

        val older = today.copy(buyDate = LocalDate.now().minusDays(99).toString())
        assertEquals(100, DeviceCalc.daysUsed(older))
        assertEquals(10.0, DeviceCalc.avgPerDay(older), 0.0001)
    }

    /** 导入文件里的脏值一律收敛成合法值，绝不让 NaN 流进列表 */
    @Test
    fun normalizeAbsorbsDirtyFields() {
        val dirty = DeviceCalc.normalize(
            id = null,
            name = null,
            price = Double.NaN,
            buyDate = "2025-02-30",
            category = null,
            note = null,
            favorite = null,
            createdAt = null
        )
        assertTrue(dirty.id.isNotEmpty())
        assertEquals("", dirty.name)
        assertEquals(0.0, dirty.price, 0.0)
        assertEquals(LocalDate.now().toString(), dirty.buyDate)
        assertEquals("", dirty.note)
        assertEquals(false, dirty.favorite)
        assertEquals(1, DeviceCalc.daysUsed(dirty))
    }

    /** 负价与天价都来自被改坏的文件，一律钳成 0：负价会把扇形图算成负百分比，
     *  而 1e300 会让 Fmt.money 排出四百多个字符，把卡片和图表一起撑爆 */
    @Test
    fun normalizeClampsImpossiblePrices() {
        assertEquals(0.0, baseNormalize(price = -500.0).price, 0.0)
        assertEquals(0.0, baseNormalize(price = Double.POSITIVE_INFINITY).price, 0.0)
        assertEquals(0.0, baseNormalize(price = Double.NaN).price, 0.0)
        assertEquals(1234.5, baseNormalize(price = 1234.5).price, 0.0001)
        assertEquals(DeviceCalc.MAX_PRICE, baseNormalize(price = DeviceCalc.MAX_PRICE).price, 0.0)
        assertEquals(0.0, baseNormalize(price = DeviceCalc.MAX_PRICE * 10).price, 0.0)
    }

    /** 老版本的中文分类名必须映射到 key，否则"覆盖多少种类型"的勋章会算错 */
    @Test
    fun normalizeResolvesLegacyCategoryNames() {
        assertEquals("phone", baseNormalize(category = "手机").category)
        assertEquals("laptop", baseNormalize(category = "电脑").category)
        assertEquals("other", baseNormalize(category = "没见过的分类").category)
    }

    @Test
    fun normalizeKeepsUnpaddedPurchaseDate() {
        assertEquals("2026-01-05", baseNormalize(buyDate = "2026-1-5").buyDate)
        assertNotEquals(LocalDate.now().toString(), baseNormalize(buyDate = "2026-1-5").buyDate)
    }

    /**
     * 给上面那条用例上保险：确认这个锚点区间真的横跨夏令时切换。
     * 同一个日期对用"两个本地零点相减再整除"的老写法必须算少一天，
     * 否则 crossingSpringForwardKeepsExactDayCount 就只是碰巧通过，毫无约束力。
     */
    @Test
    fun springForwardAnchorActuallyCrossesDst() {
        val expected = ChronoUnit.DAYS.between(LocalDate.of(2026, 3, 1), LocalDate.now()).toInt()
        // 必须与 DateUtil 一样钉死公历：JVM 默认语言是 th-TH / ja-JP 时
        // Calendar.getInstance() 会给出佛历/和历的年份，这个对照组本身就错，用例就失去意义
        val now = Calendar.getInstance(Locale.ROOT)
        val midnightDiff = ((localMidnight(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
            - localMidnight(2026, 3, 1)) / DAY_MS).toInt()
        assertNotEquals("锚点没覆盖到拨快那天，用例失去意义", expected, midnightDiff)
        assertEquals(expected - 1, midnightDiff)
    }

    private fun localMidnight(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance(Locale.ROOT)
        c.clear()
        c.set(year, month - 1, day, 0, 0, 0)
        return c.timeInMillis
    }

    private fun baseNormalize(
        price: Double? = 100.0,
        buyDate: String? = "2026-01-01",
        category: String? = "phone"
    ): DeviceItem = DeviceCalc.normalize(
        id = "d1", name = "x", price = price, buyDate = buyDate,
        category = category, note = "", favorite = false, createdAt = 1L
    )
}
