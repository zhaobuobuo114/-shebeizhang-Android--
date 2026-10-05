package com.deviceledger.app.util

import java.util.Calendar

/**
 * 日期工具：统一按本地时区、按"自然日"计算。
 * 天数差用"当天正午的时间戳 / 一天毫秒数"来算，
 * 这样可以彻底避开夏令时、时区偏移带来的 ±1 天误差。
 */
object DateUtil {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** 数字补零 */
    fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

    /** 组装 yyyy-MM-dd */
    fun format(year: Int, month: Int, day: Int): String =
        year.toString() + "-" + pad(month) + "-" + pad(day)

    /** 今天的日期字符串 */
    fun today(): String {
        val c = Calendar.getInstance()
        return format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** 某一天正午的时间戳换算出的"日序号"，用于安全相减 */
    private fun epochDay(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month - 1, day, 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis / DAY_MS
    }

    private fun epochDayToday(): Long {
        val c = Calendar.getInstance()
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** yyyy-MM-dd 拆成 [年, 月, 日]，非法返回 null */
    fun parts(dateStr: String): IntArray? {
        if (dateStr.length != 10) return null
        val p = dateStr.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        if (m < 1 || m > 12 || d < 1 || d > 31) return null
        return intArrayOf(y, m, d)
    }

    /** 从 dateStr 到今天，相差多少个自然日（今天为 0，未来返回 0） */
    fun daysSince(dateStr: String): Int {
        val p = parts(dateStr) ?: return 0
        val diff = (epochDayToday() - epochDay(p[0], p[1], p[2])).toInt()
        return if (diff > 0) diff else 0
    }

    /** 简易合法性校验（同时能挡住 2025-02-30 这类非法日期） */
    fun isValid(dateStr: String): Boolean {
        val p = parts(dateStr) ?: return false
        val c = Calendar.getInstance()
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.get(Calendar.YEAR) == p[0] &&
            c.get(Calendar.MONTH) + 1 == p[1] &&
            c.get(Calendar.DAY_OF_MONTH) == p[2] &&
            format(p[0], p[1], p[2]) == dateStr
    }

    /** yyyy-MM-dd -> Calendar（当日） */
    fun toCalendar(dateStr: String): Calendar {
        val c = Calendar.getInstance()
        val p = parts(dateStr)
        if (p != null) {
            c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
        }
        return c
    }

    /** 把天数说成人话：如 "3 年 2 个月" */
    fun humanDays(days: Int): String {
        if (days < 30) return days.toString() + " 天"
        if (days < 365) return (days / 30).toString() + " 个月"
        val years = days / 365
        val restMonths = (days - years * 365) / 30
        return if (restMonths > 0) years.toString() + " 年 " + restMonths + " 个月" else years.toString() + " 年"
    }
}
