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
        val c = Calendar.getInstance()
        c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return if (c.get(Calendar.YEAR) == p[0] &&
            c.get(Calendar.MONTH) + 1 == p[1] &&
            c.get(Calendar.DAY_OF_MONTH) == p[2]
        ) format(p[0], p[1], p[2]) else today()
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

    /** 时间戳 -> 点分日期，如 2026.10.10（勋章获得日期用） */
    fun formatStamp(at: Long): String {
        if (at <= 0L) return ""
        val c = Calendar.getInstance()
        c.timeInMillis = at
        return c.get(Calendar.YEAR).toString() + "." +
            pad(c.get(Calendar.MONTH) + 1) + "." +
            pad(c.get(Calendar.DAY_OF_MONTH))
    }

    /** 时间戳 -> 便于排序与比对的时间串，如 10-09 14:30（备份列表用） */
    fun formatClock(at: Long): String {
        if (at <= 0L) return "--"
        val c = Calendar.getInstance()
        c.timeInMillis = at
        val now = Calendar.getInstance()
        val sameYear = c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val dayPart = if (sameYear) {
            pad(c.get(Calendar.MONTH) + 1) + "-" + pad(c.get(Calendar.DAY_OF_MONTH))
        } else {
            c.get(Calendar.YEAR).toString() + "-" + pad(c.get(Calendar.MONTH) + 1) + "-" +
                pad(c.get(Calendar.DAY_OF_MONTH))
        }
        return dayPart + " " + pad(c.get(Calendar.HOUR_OF_DAY)) + ":" + pad(c.get(Calendar.MINUTE))
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
