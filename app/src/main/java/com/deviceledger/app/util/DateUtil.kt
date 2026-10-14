package com.deviceledger.app.util

import android.content.Context
import com.deviceledger.app.R
import java.util.Calendar
import java.util.Locale

/**
 * 日期工具：统一按本地时区、按"自然日"计算。
 * 天数差用"当天正午的时间戳 / 一天毫秒数"来算，
 * 这样可以彻底避开夏令时、时区偏移带来的 ±1 天误差。
 */
object DateUtil {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * 语言中立的日历。
     *
     * 绝不能用 Calendar.getInstance()：它跟随默认 Locale，而某些语言（如 ar-EG 的
     * Hijrah、th-TH 的佛历、ja-JP 的和历）会返回非公历实现，
     * get(YEAR) 就拿回 1447 / 2569 / 令和 8 这类年号年。
     * 本文件里"日序号相减"的两端只要有一次落在非公历上，天数就会整体算错，
     * 而且不报错、不崩溃，只是每天悄悄少算或多算几十天。
     */
    private fun cal(): Calendar = Calendar.getInstance(Locale.ROOT)


    /** 数字补零 */
    fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

    /** 组装 yyyy-MM-dd */
    fun format(year: Int, month: Int, day: Int): String =
        year.toString() + "-" + pad(month) + "-" + pad(day)

    /** 今天的日期字符串 */
    fun today(): String {
        val c = cal()
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** 某一天正午的时间戳换算出的"日序号"，用于安全相减 */
    private fun epochDay(year: Int, month: Int, day: Int): Long {
        val c = cal()
        c.set(year, month - 1, day, 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis / DAY_MS
    }

    private fun epochDayToday(): Long {
        val c = cal()
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** yyyy-MM-dd 拆成 [年, 月, 日]，非法返回 null（月日允许不补零，兼容老数据写法） */
    fun parts(dateStr: String): IntArray? {
        val p = dateStr.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        if (y < 1900 || m < 1 || m > 12 || d < 1 || d > 31) return null
        return intArrayOf(y, m, d)
    }

    /** 从 dateStr 到今天，相差多少个自然日（今天为 0，未来返回 0） */
    fun daysSince(dateStr: String): Int {
        val p = parts(dateStr) ?: return 0
        val diff = (epochDayToday() - epochDay(p[0], p[1], p[2])).toInt()
        return if (diff > 0) diff else 0
    }

    /**
     * 把任意写法的日期修成标准串：'2026-1-5' 补零成 '2026-01-05'，
     * '2025-02-30' 这类不存在的一天、以及彻底读不懂的串一律修成今天。
     * 导入外部文件与读历史数据都走这里，保证落进列表的日期一定是规范串。
     * 与鸿蒙版 DateUtil.sanitize 同一套规则。
     */
    fun sanitize(dateStr: String?): String {
        val p = dateStr?.let { parts(it) } ?: return today()
        val c = cal()
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return if (c.get(Calendar.YEAR) == p[0] &&
            c.get(Calendar.MONTH) + 1 == p[1] &&
            c.get(Calendar.DAY_OF_MONTH) == p[2]
        ) format(p[0], p[1], p[2]) else today()
    }

    /** yyyy-MM-dd -> Calendar（当日） */
    fun toCalendar(dateStr: String): Calendar {
        val c = cal()
        val p = parts(dateStr)
        if (p != null) {
            c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
        }
        return c
    }

    /**
     * 把天数说成人话：中文 "3 年 2 个月"、英文 "3 yr 2 mo"。
     *
     * 换算规则跨语言保持一致（30 天为一月、365 天为一年），只换单位文案；
     * 若哪天要改成真历法月，记得两端一起改。
     */
    fun humanDays(context: Context, days: Int): String {
        // 整数参数一律 toString()：占位符恒为 %1$s（见 L10n.str 的约定），
        // 直传 Int 不崩但会让 lint 的 StringFormatMatches 长期报警，把它的真价值淹掉
        if (days < 30) return quantity(context, R.plurals.p_days, days, days.toString())
        if (days < 365) {
            val months = days / 30
            return quantity(context, R.plurals.p_months, months, months.toString())
        }
        val years = days / 365
        val restMonths = (days - years * 365) / 30
        return if (restMonths > 0) {
            quantity(context, R.plurals.p_years_months, years, years.toString(), restMonths.toString())
        } else {
            quantity(context, R.plurals.p_years, years, years.toString())
        }
    }

    private fun quantity(context: Context, id: Int, count: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, count, *args)

    /**
     * 卡片上的购买日期。
     *
     * 存储永远是 yyyy-MM-dd（`format` / `parts` / `sanitize` 都不动它），
     * 这里只是"给人看"的一层：汉字文化圈沿用 ISO 串，英文改用 Mar 5, 2026。
     * 换算是显式的，绝不能顺手写成 SimpleDateFormat(dateStr)——那会让解析跟着设备语言走。
     */
    fun displayDate(context: Context, dateStr: String): String {
        val p = parts(dateStr) ?: return dateStr
        if (L10n.lang() != Lang.EN) return dateStr
        return MONTHS_EN[p[1] - 1] + " " + p[2] + ", " + p[0]
    }

    /** 时间戳 -> 勋章获得日期。英文 "Oct 10, 2026"，其余维持 2026.10.10 */
    fun formatStamp(context: Context, at: Long): String {
        if (at <= 0L) return ""
        val c = cal()
        c.timeInMillis = at
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        if (L10n.lang() != Lang.EN) return y.toString() + "." + pad(m) + "." + pad(d)
        return MONTHS_EN[m - 1] + " " + d + ", " + y
    }

    /** 时间戳 -> 备份列表的时间串。英文同年 "Oct 9, 14:30"，跨年 "Oct 9, 2025, 14:30" */
    fun formatClock(context: Context, at: Long): String {
        if (at <= 0L) return "--"
        val c = cal()
        c.timeInMillis = at
        val now = cal()
        val sameYear = c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val clock = pad(c.get(Calendar.HOUR_OF_DAY)) + ":" + pad(c.get(Calendar.MINUTE))
        val year = c.get(Calendar.YEAR)
        val dayPart = if (L10n.lang() == Lang.EN) {
            val month = MONTHS_EN[c.get(Calendar.MONTH)]
            val day = c.get(Calendar.DAY_OF_MONTH).toString()
            // 逗号一律由下面的 sep 提供，这里不要自己加（加了就成了 "Oct 9,, 14:30"）
            if (sameYear) month + " " + day else month + " " + day + ", " + year
        } else {
            val month = pad(c.get(Calendar.MONTH) + 1)
            val day = pad(c.get(Calendar.DAY_OF_MONTH))
            if (sameYear) "$month-$day" else "$year-$month-$day"
        }
        // 英文用 ", " 分隔日期与时间（"Oct 9, 14:30" / "Oct 9, 2025, 14:30"）：
        // 鸿蒙端一直是这个写法，原先两端跨年时差一个逗号
        val sep = if (L10n.lang() == Lang.EN) ", " else " "
        return dayPart + sep + clock
    }

    private val MONTHS_EN =
        arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
