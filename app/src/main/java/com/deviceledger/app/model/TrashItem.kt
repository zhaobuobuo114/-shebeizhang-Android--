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
)
