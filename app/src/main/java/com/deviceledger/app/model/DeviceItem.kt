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

    /**
     * 合理价格上限（元）。
     *
     * 超过这个量级只可能是文件被改坏了：1e300 不会崩，但 `Fmt.money` 会为它排出四百多个
     * 字符，把卡片和扇形图撑出屏幕，日均与排序键也跟着变成无意义的数。
     * 与负价同样处理——钳成 0，让这条记录以"没填价格"的形态留在列表里，而不是整条丢掉。
     */
    const val MAX_PRICE = 1e12

    /**
     * 补 id 用的进程内递增序号。
     *
     * 原先只靠 `0..9999` 的随机数：一批**同时缺 id 又缺 createdAt** 的记录（手工编辑过的
     * 导出文件就会出现，此时所有 id 都是 `d0<随机>`）里，100 条至少撞一次的概率约 39%。
     * 撞了之后 `findById`/`indexOfFirst` 只命中第一条，编辑与删除会改错对象，
     * `BackupStore.merge` 还会把第二条静默吃掉。加一个计数器保证同一批内绝不重复，
     * 再留 24 位随机数防跨会话同 id。
     */
    private val fallbackSeq = java.util.concurrent.atomic.AtomicLong(0)

    /** 缺 id 时补一个：时间戳 + 递增序号 + 24 位随机 */
    fun fallbackId(createdAt: Long): String =
        "d" + createdAt + "-" + fallbackSeq.incrementAndGet().toString(36) +
            "-" + ((0..0xFFFFFF).random()).toString(36)

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
        return DeviceItem(
            id = if (id.isNullOrEmpty()) fallbackId(safeCreated) else id,
            name = safeName,
            // 录入面板本来就禁止 0 与负数，负价只可能来自被改坏的文件；钳成 0 免得把扇形图算出负百分比
            price = if (safePrice.isNaN() || safePrice.isInfinite() ||
                safePrice <= 0.0 || safePrice > MAX_PRICE
            ) 0.0 else safePrice,
            // 不补零的老写法（2026-1-5）修成标准串，而不是像以前那样整个丢掉当成今天
            buyDate = DateUtil.sanitize(buyDate),
            category = resolveKind(category ?: ""),
            note = note ?: "",
            // 老版本数据没有 favorite 字段，缺省就是未收藏
            favorite = favorite == true,
            createdAt = safeCreated
        )
    }
}
