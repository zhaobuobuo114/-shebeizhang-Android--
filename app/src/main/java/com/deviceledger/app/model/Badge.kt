package com.deviceledger.app.model

import androidx.annotation.DrawableRes
import com.deviceledger.app.R
import com.deviceledger.app.util.DateUtil

/**
 * 里程碑勋章：给记设备这件事配上一点达成感。
 *
 * 判定只看本机已有的数据，达成即永久点亮并记录获得日期——
 * 之后即便数字回落（比如删掉设备导致总投入下降），勋章也不会熄灭，
 * 这一点和 QQ 的勋章墙一致：拿到过就是拿到过。
 *
 * 清单、id、名称、达成条件文案必须与鸿蒙端逐项一致——导出文件里只写 id，
 * 三样里任何一项对不上，从一端导出的文件在另一端就会认错或认不出勋章。
 * 图案（iconRes）是各端自己的呈现物，不参与互通：安卓用专属插画，
 * 鸿蒙目前仍是 emoji。改图案不需要动另一端，改上面三样必须同时改两端。
 */
data class BadgeDef(
    val id: String,       // 唯一标识，写进导出文件
    val name: String,     // 勋章名
    val desc: String,     // 达成条件说明
    @field:DrawableRes val iconRes: Int   // 专属徽章插画
)

/** 一次判定需要的全部统计量 */
data class BadgeStat(
    val count: Int,      // 设备台数
    val total: Double,   // 总投入
    val fav: Int,        // 收藏台数
    val maxDays: Int,    // 用得最久的那台用了多少天
    val kinds: Int,      // 覆盖了多少种设备类型
    val minAvg: Double,  // 最低的日均成本
    val maxPrice: Double // 单台最高价
)

object Badges {

    /** 总览卡胶囊里那颗通用奖牌：不随解锁情况变化，只当"勋章"这个功能的门面 */
    @DrawableRes
    const val MEDAL_RES: Int = R.drawable.badge_medal

    /** 回收站里已删除的设备也计入统计，避免删掉设备后勋章"倒退"的观感 */
    const val KEEP_AFTER_DELETE = true

    val ALL: List<BadgeDef> = listOf(
        BadgeDef("first_device", "初次见面", "记录第一台设备", R.drawable.badge_first_device),
        BadgeDef("count_10", "十全十美", "设备数达到 10 台", R.drawable.badge_count_10),
        BadgeDef("count_25", "成群结队", "设备数达到 25 台", R.drawable.badge_count_25),
        BadgeDef("spend_1k", "小有积蓄", "总投入超过 1000 元", R.drawable.badge_spend_1k),
        BadgeDef("spend_10k", "万元户", "总投入超过 10000 元", R.drawable.badge_spend_10k),
        BadgeDef("spend_50k", "家底殷实", "总投入超过 50000 元", R.drawable.badge_spend_50k),
        BadgeDef("fav_3", "心头好", "收藏 3 台设备", R.drawable.badge_fav_3),
        BadgeDef("fav_10", "宠爱有加", "收藏 10 台设备", R.drawable.badge_fav_10),
        BadgeDef("days_365", "长情相伴", "有设备用满 365 天", R.drawable.badge_days_365),
        BadgeDef("days_1095", "三年之约", "有设备用满 1095 天", R.drawable.badge_days_1095),
        BadgeDef("kind_8", "品类达人", "覆盖 8 种设备类型", R.drawable.badge_kind_8),
        BadgeDef("kind_20", "面面俱到", "覆盖 20 种设备类型", R.drawable.badge_kind_20),
        BadgeDef("roi", "回本达人", "有设备日均低于 0.5 元", R.drawable.badge_roi),
        BadgeDef("big_ticket", "大手笔", "单台设备超过 5000 元", R.drawable.badge_big_ticket)
    )

    fun byId(id: String): BadgeDef? = ALL.firstOrNull { it.id == id }

    /** 从设备列表算出判定所需的统计量 */
    fun statOf(list: List<DeviceItem>): BadgeStat {
        var total = 0.0
        var fav = 0
        var maxDays = 0
        var minAvg = Double.MAX_VALUE
        var maxPrice = 0.0
        val kinds = HashSet<String>()
        for (it in list) {
            total += it.price
            if (it.favorite) fav++
            val days = DeviceCalc.daysUsed(it)
            if (days > maxDays) maxDays = days
            val avg = DeviceCalc.avgPerDay(it)
            if (avg < minAvg) minAvg = avg
            if (it.price > maxPrice) maxPrice = it.price
            kinds.add(it.category)
        }
        return BadgeStat(
            count = list.size,
            total = total,
            fav = fav,
            maxDays = maxDays,
            kinds = kinds.size,
            minAvg = if (minAvg == Double.MAX_VALUE) Double.MAX_VALUE else minAvg,
            maxPrice = maxPrice
        )
    }

    /** 当前数据满足了哪些勋章（返回 id 集合） */
    fun satisfied(s: BadgeStat): Set<String> {
        val out = HashSet<String>()
        if (s.count >= 1) out.add("first_device")
        if (s.count >= 10) out.add("count_10")
        if (s.count >= 25) out.add("count_25")
        if (s.total >= 1000.0) out.add("spend_1k")
        if (s.total >= 10000.0) out.add("spend_10k")
        if (s.total >= 50000.0) out.add("spend_50k")
        if (s.fav >= 3) out.add("fav_3")
        if (s.fav >= 10) out.add("fav_10")
        if (s.maxDays >= 365) out.add("days_365")
        if (s.maxDays >= 1095) out.add("days_1095")
        if (s.kinds >= 8) out.add("kind_8")
        if (s.kinds >= 20) out.add("kind_20")
        if (s.count > 0 && s.minAvg < 0.5) out.add("roi")
        if (s.maxPrice >= 5000.0) out.add("big_ticket")
        return out
    }

    /**
     * 对照数据重新判定，把新达成的写进已有的状态表。
     * 返回本次**新点亮**的勋章（供界面弹一句提示），已点亮的不重复返回。
     */
    fun evaluate(list: List<DeviceItem>, state: MutableMap<String, Long>): List<BadgeDef> {
        val hit = satisfied(statOf(list))
        val now = System.currentTimeMillis()
        val fresh = ArrayList<BadgeDef>()
        for (def in ALL) {
            if (hit.contains(def.id) && !state.containsKey(def.id)) {
                state[def.id] = now
                fresh.add(def)
            }
        }
        return fresh
    }

    /** 勋章获得日期的展示文案，如「2026.03.05 达成」 */
    fun dateText(at: Long): String = DateUtil.formatStamp(at)
}
