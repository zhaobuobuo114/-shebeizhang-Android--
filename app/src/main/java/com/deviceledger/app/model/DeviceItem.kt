package com.deviceledger.app.model

import com.deviceledger.app.util.DateUtil

/**
 * 设备条目数据模型。
 * 一台设备 = 一次"固定资产投入"：记录购买日期与金额，据此计算使用天数与日均成本。
 */
data class DeviceItem(
    val id: String,        // 唯一标识
    val name: String,      // 设备名称
    val price: Double,     // 购买价格（元）
    val buyDate: String,   // 购买日期，格式 yyyy-MM-dd
    val category: String,  // 设备类型 key（见 DeviceKind.kt）
    val note: String,      // 备注
    val favorite: Boolean = false, // 是否收藏（最爱）
    val createdAt: Long    // 记录创建时间戳
)

/** 与设备相关的计算逻辑 */
object DeviceCalc {

    /** 已使用天数：含购买当天，至少 1 天 */
    fun daysUsed(item: DeviceItem): Int = DateUtil.daysSince(item.buyDate) + 1

    /** 日均成本：价格 / 已使用天数，用得越久日均越低 */
    fun avgPerDay(item: DeviceItem): Double {
        val days = daysUsed(item)
        return item.price / (if (days > 0) days else 1)
    }

    /** 折算年限，如 1.5 年 */
    fun yearsUsed(item: DeviceItem): Double = daysUsed(item) / 365.0

    /**
     * 唯一 key：内容变化时也跟着变，便于判断是否同一条记录。
     *
     * 注意：故意**不**包含 favorite。收藏只是卡片右下角那颗星的状态，
     * 放进 key 会让整张卡片重建、重播一遍入场动画（点星标时卡片乱飞一下）。
     * 星标由 DeviceAdapter.ViewHolder 自己维护一份显示状态，点击即时变色。
     */
    fun keyOf(item: DeviceItem): String =
        item.id + "#" + item.name + "#" + item.price + "#" + item.buyDate +
            "#" + item.category + "#" + item.note

    /** 兜底清洗：把历史脏数据规整成合法结构 */
    fun normalize(
        id: String?,
        name: String?,
        price: Double?,
        buyDate: String?,
        category: String?,
        note: String?,
        favorite: Boolean?,
        createdAt: Long?
    ): DeviceItem {
        val safeCreated = createdAt ?: 0L
        val safeName = name ?: ""
        val safePrice = price ?: 0.0
        val safeBuyDate = if (buyDate.isNullOrEmpty()) DateUtil.today() else buyDate
        return DeviceItem(
            id = if (id.isNullOrEmpty()) "d" + safeCreated + (0..9999).random() else id,
            name = safeName,
            price = if (safePrice.isNaN() || safePrice.isInfinite()) 0.0 else safePrice,
            buyDate = if (DateUtil.isValid(safeBuyDate)) safeBuyDate else DateUtil.today(),
            category = resolveKind(category ?: ""),
            note = note ?: "",
            // 老版本数据没有 favorite 字段，缺省就是未收藏
            favorite = favorite == true,
            createdAt = safeCreated
        )
    }
}
