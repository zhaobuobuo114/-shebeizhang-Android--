package com.deviceledger.app.util

import android.content.Context
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.TrashItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 数据的导出、导入与自动备份。
 *
 * 导出的文件是一份自描述的 JSON，安卓版与鸿蒙版**完全同一套格式**，
 * 一端导出的文件另一端能原样读进来（设备、回收站、勋章三块一起带走）。
 *
 * 自动备份写在应用私有目录里，轮流占 5 个槽位：每次数据变动前留一份快照，
 * 超出 5 份就覆盖最旧的。卸载应用会随之清除，不涉及任何权限。
 */
object BackupStore {

    const val FORMAT = "device-ledger"
    const val VERSION = 1

    /** 自动备份最多保留几份 */
    const val SLOTS = 5
    /** 回收站保留天数 */
    const val KEEP_DAYS = 30
    /** 两次自动快照之间的最小间隔，避免连续编辑把 5 个槽位全刷成同一分钟 */
    private const val MIN_INTERVAL_MS = 60_000L

    private const val DIR_BACKUP = "backups"
    private const val DIR_EXPORT = "export"

    /** 一份完整数据：设备 + 回收站 + 勋章 */
    data class Snapshot(
        val devices: MutableList<DeviceItem>,
        val recycle: MutableList<TrashItem>,
        val badges: MutableMap<String, Long>,
        val exportedAt: Long = 0L
    )

    data class BackupFile(
        val slot: Int,
        val at: Long,
        val deviceCount: Int,
        val recycleCount: Int,
        val badgeCount: Int
    )

    /* ---------------- 编解码 ---------------- */

    /** 价格写进文件前统一到分，避免浮点尾巴在另一端被当成不同的值 */
    private fun money(v: Double): Double {
        if (v.isNaN() || v.isInfinite()) return 0.0
        return Math.round(v * 100.0) / 100.0
    }

    private fun deviceJson(it: DeviceItem): JSONObject {
        val o = JSONObject()
        o.put("id", it.id)
        o.put("name", it.name)
        o.put("price", money(it.price))
        o.put("buyDate", it.buyDate)
        o.put("category", it.category)
        o.put("note", it.note)
        o.put("favorite", it.favorite)
        o.put("createdAt", it.createdAt)
        return o
    }

    private fun trashJson(t: TrashItem): JSONObject {
        val o = deviceJson(t.item)
        o.put("deletedAt", t.deletedAt)
        return o
    }

    /** 组装导出内容。字段顺序与鸿蒙端一致，便于两边人工比对 */
    fun buildJson(snapshot: Snapshot, forBackup: Boolean = false): String {
        val root = JSONObject()
        root.put("app", "DeviceLedger")
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", if (snapshot.exportedAt > 0L) snapshot.exportedAt else System.currentTimeMillis())
        if (forBackup) root.put("kind", "auto-backup")

        val devices = JSONArray()
        for (it in snapshot.devices) devices.put(deviceJson(it))
        root.put("devices", devices)

        val recycle = JSONArray()
        for (t in snapshot.recycle) recycle.put(trashJson(t))
        root.put("recycle", recycle)

        val badges = JSONArray()
        for ((id, at) in snapshot.badges) {
            val b = JSONObject()
            b.put("id", id)
            b.put("at", at)
            badges.put(b)
        }
        root.put("badges", badges)

        val counts = JSONObject()
        counts.put("devices", snapshot.devices.size)
        counts.put("recycle", snapshot.recycle.size)
        counts.put("badges", snapshot.badges.size)
        root.put("counts", counts)
        return root.toString()
    }

    /**
     * 把一段文本解析成数据；认不出来返回 null。
     *
     * 认不出来必须是 null 而不是空数据：调用方拿到 null 只会提示读不出来、什么都不改；
     * 而返回一个 devices 为空的 Snapshot 会被"覆盖"导入当成合法文件整体落盘，
     * 挑错一个 json（比如随便一份 package.json）就等于把台账清空。
     */
    fun parseJson(text: String): Snapshot? {
        val s = text.trim()
        if (s.isEmpty()) return null
        return try {
            when {
                s.startsWith("[") -> Snapshot(
                    devices = parseDevices(JSONArray(s)),
                    recycle = mutableListOf(),
                    badges = mutableMapOf()
                )
                s.startsWith("{") -> {
                    val root = JSONObject(s)
                    // format 对不上就不是设备账的文件；version 比本机新则不敢猜它的意思
                    if (root.has("format") && root.optString("format") != FORMAT) return null
                    if (root.optLong("version", 0L) > VERSION) return null
                    // 老文件可能没有 format/version，但至少得真有一个 devices 数组
                    val devicesArr = root.optJSONArray("devices") ?: return null
                    Snapshot(
                        devices = parseDevices(devicesArr),
                        recycle = parseRecycle(root.optJSONArray("recycle")),
                        badges = parseBadges(root.opt("badges")),
                        exportedAt = root.optLong("exportedAt", 0L)
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDevices(arr: JSONArray?): MutableList<DeviceItem> {
        val out = mutableListOf<DeviceItem>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                DeviceCalc.normalize(
                    id = if (o.has("id")) o.optString("id") else null,
                    name = if (o.has("name")) o.optString("name") else null,
                    price = if (o.has("price")) o.optDouble("price", 0.0) else null,
                    buyDate = if (o.has("buyDate")) o.optString("buyDate") else null,
                    category = if (o.has("category")) o.optString("category") else null,
                    note = if (o.has("note")) o.optString("note") else null,
                    favorite = if (o.has("favorite")) o.optBoolean("favorite", false) else null,
                    createdAt = if (o.has("createdAt")) o.optLong("createdAt", 0L) else null
                )
            )
        }
        return out
    }

    private fun parseRecycle(arr: JSONArray?): MutableList<TrashItem> {
        val out = mutableListOf<TrashItem>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = DeviceCalc.normalize(
                id = if (o.has("id")) o.optString("id") else null,
                name = if (o.has("name")) o.optString("name") else null,
                price = if (o.has("price")) o.optDouble("price", 0.0) else null,
                buyDate = if (o.has("buyDate")) o.optString("buyDate") else null,
                category = if (o.has("category")) o.optString("category") else null,
                note = if (o.has("note")) o.optString("note") else null,
                favorite = if (o.has("favorite")) o.optBoolean("favorite", false) else null,
                createdAt = if (o.has("createdAt")) o.optLong("createdAt", 0L) else null
            )
            // deletedAt 缺失或为 0 一律按"刚刚删的"算，否则这条会永不过期（与鸿蒙版 TrashCalc.normalize 一致）
            val at = o.optLong("deletedAt", 0L)
            out.add(TrashItem(item, if (at > 0L) at else System.currentTimeMillis()))
        }
        return out
    }

    /** 勋章在文件里可能是数组，也可能是 id -> 时间戳的对象，两种都认 */
    private fun parseBadges(raw: Any?): MutableMap<String, Long> {
        val out = mutableMapOf<String, Long>()
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val o = raw.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val at = o.optLong("at", 0L)
                    if (id.isNotEmpty() && at > 0L) out[id] = at
                }
            }
            is JSONObject -> {
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val at = raw.optLong(k, 0L)
                    if (at > 0L) out[k] = at
                }
            }
        }
        return out
    }

    /* ---------------- 导入的两种落地方式 ---------------- */

    /** 合并：保留本机已有，只把不重复的补进来；勋章取更早的获得时间 */
    fun merge(local: Snapshot, incoming: Snapshot): Snapshot {
        val devices = local.devices.toMutableList()
        val seen = HashSet<String>()
        for (it in devices) seen.add(it.id)
        for (it in incoming.devices) {
            if (!seen.contains(it.id)) {
                devices.add(it)
                seen.add(it.id)
            }
        }

        val recycle = local.recycle.toMutableList()
        val seenTrash = HashSet<String>()
        for (t in recycle) seenTrash.add(t.item.id)
        for (t in incoming.recycle) {
            if (!seenTrash.contains(t.item.id)) {
                recycle.add(t)
                seenTrash.add(t.item.id)
            }
        }

        // 一台设备不能同时"在册"又在"回收站"：甲机删了 X、乙机还留着 X，合并后就会出现两份同 id。
        // 以在册为准（用户还在用），把回收站里那条同 id 的快照剔掉，否则恢复一次就多出一台重复设备。
        val liveIds = HashSet<String>()
        for (it in devices) liveIds.add(it.id)
        recycle.removeAll { liveIds.contains(it.item.id) }

        val badges = local.badges.toMutableMap()
        for ((id, at) in incoming.badges) {
            val has = badges[id]
            if (has == null || at < has) badges[id] = at
        }
        return Snapshot(devices, recycle, badges)
    }

    /* ---------------- 自动备份 ---------------- */

    private fun backupDir(context: Context): File {
        val d = File(context.filesDir, DIR_BACKUP)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun slotFile(context: Context, slot: Int): File =
        File(backupDir(context), "backup-$slot.json")

    /**
     * 数据变动前留一份快照。刚备份过（60 秒内）就跳过，
     * 免得连着编辑几次就把 5 个槽位刷成同一时刻、回退空间被吃掉。
     */
    fun maybeBackup(context: Context, snapshot: Snapshot): Boolean {
        val now = System.currentTimeMillis()
        if (now - DeviceStore.loadBackupAt() < MIN_INTERVAL_MS) return false
        return writeBackup(context, snapshot, now)
    }

    /** 直接写一份快照（导入、恢复这类大动作前强制留底） */
    fun forceBackup(context: Context, snapshot: Snapshot): Boolean {
        return writeBackup(context, snapshot, System.currentTimeMillis())
    }

    private fun writeBackup(context: Context, snapshot: Snapshot, now: Long): Boolean {
        return try {
            val slot = DeviceStore.loadBackupSlot(SLOTS)
            val withTime = snapshot.copy(exportedAt = now)
            slotFile(context, slot).writeText(buildJson(withTime, true), Charsets.UTF_8)
            DeviceStore.saveBackupSlot(if (slot >= SLOTS) 1 else slot + 1)
            DeviceStore.saveBackupAt(now)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 列出已有快照，新的排前面 */
    fun listBackups(context: Context): List<BackupFile> {
        val out = ArrayList<BackupFile>()
        for (slot in 1..SLOTS) {
            val f = slotFile(context, slot)
            if (!f.exists()) continue
            val snap = try {
                parseJson(f.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
            out.add(
                BackupFile(
                    slot = slot,
                    at = snap?.exportedAt ?: f.lastModified(),
                    deviceCount = snap?.devices?.size ?: 0,
                    recycleCount = snap?.recycle?.size ?: 0,
                    badgeCount = snap?.badges?.size ?: 0
                )
            )
        }
        out.sortByDescending { it.at }
        return out
    }

    /** 读取某一槽位的快照内容 */
    fun readBackup(context: Context, slot: Int): Snapshot? {
        val f = slotFile(context, slot)
        if (!f.exists()) return null
        return try {
            parseJson(f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    /* ---------------- 导出用的临时文件 ---------------- */

    /** 导出文件名：device-ledger-20261010-2045.json */
    fun fileName(at: Long = System.currentTimeMillis()): String {
        val c = java.util.Calendar.getInstance(java.util.Locale.ROOT)
        c.timeInMillis = at
        fun p(n: Int) = if (n < 10) "0$n" else n.toString()
        return "device-ledger-" + c.get(java.util.Calendar.YEAR).toString() +
            p(c.get(java.util.Calendar.MONTH) + 1) +
            p(c.get(java.util.Calendar.DAY_OF_MONTH)) + "-" +
            p(c.get(java.util.Calendar.HOUR_OF_DAY)) +
            p(c.get(java.util.Calendar.MINUTE)) + ".json"
    }

    /**
     * 在私有目录里写出一份待分享 / 待发送的文件，返回它。
     * 走 FileProvider 交给别的应用，不需要任何存储权限。
     */
    fun writeExportFile(context: Context, snapshot: Snapshot): File? {
        return try {
            val dir = File(context.filesDir, DIR_EXPORT)
            if (!dir.exists()) dir.mkdirs()
            // 目录里只留最近那一份，别让临时文件越堆越多
            dir.listFiles()?.forEach { if (it.isFile) it.delete() }
            val f = File(dir, fileName())
            f.writeText(buildJson(snapshot), Charsets.UTF_8)
            f
        } catch (e: Exception) {
            null
        }
    }
}
