package com.deviceledger.app.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.deviceledger.app.R
import com.deviceledger.app.util.DateUtil

/**
 * 里程碑勋章：给记设备这件事配上一点达成感。
 *
 * 判定只看本机已有的数据，达成即永久点亮并记录获得日期——
 * 之后即便数字回落（比如删掉设备导致总投入下降），勋章也不会熄灭，
 * 这一点和 QQ 的勋章墙一致：拿到过就是拿到过。
 *
 * 清单与 id 必须与鸿蒙端逐项一致——导出文件里只写 id，id 对不上就从另一端认错勋章。
 * 名称与达成条件是 v2.0.0 起随语言走的展示文案，两端的取值同源于根目录的
 * _i18n_master.json（badge_<id>_name / badge_<id>_desc），所以"一致"这件事由主表保证，
 * 不再靠两边各抄一遍中文字面量。图案（iconRes）仍是各端自己的呈现物，不参与互通。
 */
data class BadgeDef(
    val id: String,       // 唯一标识，写进导出文件
    @field:StringRes val nameRes: Int,     // 勋章名（随语言）
    @field:StringRes val descRes: Int,     // 达成条件说明（随语言）
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

    val ALL: List<BadgeDef> = listOf(
        BadgeDef("first_device", R.string.badge_first_device_name, R.string.badge_first_device_desc, R.drawable.badge_first_device),
        BadgeDef("count_10", R.string.badge_count_10_name, R.string.badge_count_10_desc, R.drawable.badge_count_10),
        BadgeDef("count_25", R.string.badge_count_25_name, R.string.badge_count_25_desc, R.drawable.badge_count_25),
        BadgeDef("spend_1k", R.string.badge_spend_1k_name, R.string.badge_spend_1k_desc, R.drawable.badge_spend_1k),
        BadgeDef("spend_10k", R.string.badge_spend_10k_name, R.string.badge_spend_10k_desc, R.drawable.badge_spend_10k),
        BadgeDef("spend_50k", R.string.badge_spend_50k_name, R.string.badge_spend_50k_desc, R.drawable.badge_spend_50k),
        BadgeDef("fav_3", R.string.badge_fav_3_name, R.string.badge_fav_3_desc, R.drawable.badge_fav_3),
        BadgeDef("fav_10", R.string.badge_fav_10_name, R.string.badge_fav_10_desc, R.drawable.badge_fav_10),
        BadgeDef("days_365", R.string.badge_days_365_name, R.string.badge_days_365_desc, R.drawable.badge_days_365),
        BadgeDef("days_1095", R.string.badge_days_1095_name, R.string.badge_days_1095_desc, R.drawable.badge_days_1095),
        BadgeDef("kind_8", R.string.badge_kind_8_name, R.string.badge_kind_8_desc, R.drawable.badge_kind_8),
        BadgeDef("kind_20", R.string.badge_kind_20_name, R.string.badge_kind_20_desc, R.drawable.badge_kind_20),
        BadgeDef("roi", R.string.badge_roi_name, R.string.badge_roi_desc, R.drawable.badge_roi),
        BadgeDef("big_ticket", R.string.badge_big_ticket_name, R.string.badge_big_ticket_desc, R.drawable.badge_big_ticket)
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

    /** 勋章获得日期本身（不含"达成"字样），如 2026.03.05 / Oct 10, 2026 */
    fun dateText(context: Context, at: Long): String = DateUtil.formatStamp(context, at)
}
