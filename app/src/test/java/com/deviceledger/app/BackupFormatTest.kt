package com.deviceledger.app

import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.TrashItem
import com.deviceledger.app.util.BackupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入/导出契约的回归测试。
 *
 * 导出文件是鸿蒙版与安卓版共用的格式，所以这里的每一条断言同时也是两端互通的规格；
 * 鸿蒙端 BackupStore.ets 必须保持同样的行为（改动时两端一起改）。
 */
class BackupFormatTest {

    private fun device(id: String, name: String = "设备", price: Double = 100.0, buyDate: String = "2026-01-01") =
        DeviceItem(id, name, price, buyDate, "phone", "", false, 1000L)

    private fun snap(vararg items: DeviceItem) = BackupStore.Snapshot(
        items.toMutableList(), mutableListOf(), mutableMapOf(), 1700000000000L
    )

    /** 挑错文件不能"解析成功但数据是空的"——那会被覆盖式导入当成合法数据整表落盘 */
    @Test
    fun foreignJsonIsRejectedNotEmptySuccess() {
        assertNull(BackupStore.parseJson(""))
        assertNull(BackupStore.parseJson("plain text"))
        assertNull(BackupStore.parseJson("""{"name":"some-package","version":"1.0.0"}"""))
        assertNull(BackupStore.parseJson("""{}"""))
        // devices 写成字符串时，旧实现会按字符迭代造出三条垃圾设备
        assertNull(BackupStore.parseJson("""{"format":"device-ledger","devices":"abc"}"""))
    }

    @Test
    fun selfDescribingFieldsAreChecked() {
        assertNull(BackupStore.parseJson("""{"format":"not-my-app","devices":[]}"""))
        assertNull(BackupStore.parseJson("""{"format":"device-ledger","version":${BackupStore.VERSION + 1},"devices":[]}"""))
        assertNotNull(BackupStore.parseJson("""{"format":"device-ledger","version":${BackupStore.VERSION},"devices":[]}"""))
    }

    /** 历史文件没有 format/version，甚至就是裸的设备数组，这些还得继续认 */
    @Test
    fun legacyShapesStillParse() {
        val bare = BackupStore.parseJson("""[{"id":"d1","name":"老设备","price":50.0,"buyDate":"2026-01-01","category":"phone","note":"","favorite":false,"createdAt":1}]""")
        assertNotNull(bare)
        assertEquals(1, bare!!.devices.size)
        assertEquals("老设备", bare.devices[0].name)

        val noHeader = BackupStore.parseJson("""{"devices":[{"id":"d2","name":"另一台"}]}""")
        assertNotNull(noHeader)
        assertEquals(1, noHeader!!.devices.size)
    }

    /** 脏值在解析阶段就被吸收，绝不带着 null / 字符串价格进列表 */
    @Test
    fun dirtyRecordsAreSanitized() {
        val s = BackupStore.parseJson(
            """{"format":"device-ledger","devices":[
               {"id":"d1","name":null,"price":"abc","buyDate":null,"category":null,"note":null,"favorite":null,"createdAt":null},
               {"id":null,"name":"甲","price":-30,"buyDate":"2025-02-30","category":"手机","note":"","favorite":false,"createdAt":0},
               {"id":"d3","name":"乙","price":"1234.5","buyDate":"2026-1-5","category":"phone","note":"","favorite":false,"createdAt":1}
            ]}"""
        )
        assertNotNull(s)
        val first = s!!.devices[0]
        assertEquals("", first.name)
        assertEquals(0.0, first.price, 0.0)
        assertTrue(first.id.isNotEmpty())
        // 同 id 缺失时各自补出不同 id，不能都变成 null 互相判重复
        assertTrue(s.devices[1].id.isNotEmpty())
        assertNotEqualsTo(s.devices[0].id, s.devices[1].id)
        assertEquals(0.0, s.devices[1].price, 0.0)
        assertEquals("phone", s.devices[1].category)
        // 不补零的老写法修成标准串，而不是丢掉变成今天
        assertEquals("2026-01-05", s.devices[2].buyDate)
        assertEquals(1234.5, s.devices[2].price, 0.0001)
    }

    /** 写出去再读回来，三块数据必须原样 */
    @Test
    fun roundTripKeepsEverything() {
        val local = BackupStore.Snapshot(
            mutableListOf(device("d1", "笔记本", 6999.999), device("d2", "手机", 1999.5)),
            mutableListOf(TrashItem(device("d3", "旧平板"), 1700000000000L)),
            mutableMapOf("first_device" to 1690000000000L, "count_10" to 1699999999999L),
            1700000000000L
        )
        val back = BackupStore.parseJson(BackupStore.buildJson(local))
        assertNotNull(back)
        assertEquals(2, back!!.devices.size)
        assertEquals(1, back.recycle.size)
        assertEquals(2, back.badges.size)
        assertEquals(1690000000000L, back.badges["first_device"])
        // 价格统一到分，不留浮点尾巴
        assertEquals(7000.0, back.devices[0].price, 0.0)
        assertEquals("d3", back.recycle[0].item.id)
        assertEquals(1700000000000L, back.recycle[0].deletedAt)
    }

    /** 勋章在文件里也可能写成 {id: 时间戳} 对象（本机 prefs 的内部形状），两种都要认 */
    @Test
    fun badgesAcceptedInBothShapes() {
        val asObject = BackupStore.parseJson("""{"devices":[],"badges":{"roi":1690000000000,"count_10":0}}""")
        assertNotNull(asObject)
        assertEquals(1, asObject!!.badges.size)
        assertEquals(1690000000000L, asObject.badges["roi"])
        val asArray = BackupStore.parseJson("""{"devices":[],"badges":[{"id":"roi","at":1690000000000}]}""")
        assertEquals(1690000000000L, asArray!!.badges["roi"])
    }

    /** 合并保留本机、只补不重复的；勋章取更早的达成时间 */
    @Test
    fun mergePrefersLocalAndKeepsEarliestBadge() {
        val local = BackupStore.Snapshot(
            mutableListOf(device("d1", "本机名字", 100.0)),
            mutableListOf(),
            mutableMapOf("count_10" to 1800000000000L),
            0L
        )
        val incoming = BackupStore.Snapshot(
            mutableListOf(device("d1", "文件名字", 999.0), device("d2", "新设备", 50.0)),
            mutableListOf(),
            mutableMapOf("count_10" to 1600000000000L, "spend_1k" to 1650000000000L),
            0L
        )
        val merged = BackupStore.merge(local, incoming)
        assertEquals(2, merged.devices.size)
        assertEquals("本机名字", merged.devices[0].name)
        assertEquals(1600000000000L, merged.badges["count_10"])
        assertEquals(1650000000000L, merged.badges["spend_1k"])
    }

    /** 一台设备不能既在册又在回收站：甲机删了 X、乙机留着 X，合并后必须只剩一份 */
    @Test
    fun mergeNeverLeavesDeviceBothLiveAndInTrash() {
        val local = BackupStore.Snapshot(
            mutableListOf(device("d1", "手机")),
            mutableListOf(TrashItem(device("dX", "已删的"), 1700000000000L)),
            mutableMapOf(),
            0L
        )
        val incoming = BackupStore.Snapshot(
            mutableListOf(device("dX", "还在用的")),
            mutableListOf(TrashItem(device("d1", "本机在册的那台"), 1700000000000L)),
            mutableMapOf(),
            0L
        )
        val merged = BackupStore.merge(local, incoming)
        assertEquals(listOf("d1", "dX"), merged.devices.map { it.id }.sorted())
        assertTrue("回收站里不能再有在册设备的快照", merged.recycle.isEmpty())
    }

    private fun assertNotEqualsTo(a: String, b: String) = assertTrue("两条补出来的 id 撞了：$a", a != b)
}
