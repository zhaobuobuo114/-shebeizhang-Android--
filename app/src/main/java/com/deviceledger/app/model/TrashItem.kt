package com.deviceledger.app.model

/**
 * 回收站里的一条记录：被删掉的设备 + 删除时间。
 *
 * 设备本体沿用 [DeviceItem]，删进回收站时不改任何字段，
 * 这样恢复出来就是原样（收藏、备注、创建时间都还在）。
 */
data class TrashItem(
    val item: DeviceItem,
    val deletedAt: Long
) {
    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        /**
         * 还剩几天会被自动清除；0 表示已到期。
         * 与鸿蒙 `TrashCalc.daysLeft` 同规则：按整天向下取整，负数一律收成 0。
         */
        fun daysLeft(t: TrashItem, keepDays: Int): Int {
            val passed = ((System.currentTimeMillis() - t.deletedAt) / DAY_MS).toInt()
            val left = keepDays - passed
            return if (left > 0) left else 0
        }
    }
}
