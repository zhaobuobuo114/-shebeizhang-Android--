package com.deviceledger.app.util

import android.content.Context
import android.content.SharedPreferences
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.TrashItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化：SharedPreferences + JSON。
 * 数据量小、读写简单；卸载应用会随之清除，不涉及任何网络或权限。
 */
object DeviceStore {

    private const val PREF_NAME = "device_ledger_store"
    private const val KEY_LIST = "device_list"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_AGREED = "privacy_agreed"
    private const val KEY_TRASH = "trash_list"
    private const val KEY_BADGE = "badge_state"
    private const val KEY_BACKUP_SLOT = "backup_slot"
    private const val KEY_BACKUP_AT = "backup_at"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 读取全部设备（脏数据会被自动清洗） */
    fun load(): MutableList<DeviceItem> {
        val sp = prefs ?: return mutableListOf()
        val raw = try {
            sp.getString(KEY_LIST, "[]") ?: "[]"
        } catch (e: Exception) {
            "[]"
        }
        if (raw.isEmpty()) return mutableListOf()

        val out = mutableListOf<DeviceItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.optJSONObject(i) ?: continue
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
        } catch (e: Exception) {
            return out
        }
        return out
    }

    /** 整体写回 */
    fun save(list: List<DeviceItem>) {
        val sp = prefs ?: return
        try {
            val arr = JSONArray()
            for (item in list) {
                val o = JSONObject()
                o.put("id", item.id)
                o.put("name", item.name)
                o.put("price", item.price)
                o.put("buyDate", item.buyDate)
                o.put("category", item.category)
                o.put("note", item.note)
                o.put("favorite", item.favorite)
                o.put("createdAt", item.createdAt)
                arr.put(o)
            }
            sp.edit().putString(KEY_LIST, arr.toString()).apply()
        } catch (e: Exception) {
            // 存储失败不应影响界面使用
        }
    }

    /** 读取上次选择的主题 */
    fun loadDark(): Boolean {
        val sp = prefs ?: return false
        return try {
            sp.getBoolean(KEY_DARK, false)
        } catch (e: Exception) {
            false
        }
    }

    fun saveDark(dark: Boolean) {
        val sp = prefs ?: return
        try {
            sp.edit().putBoolean(KEY_DARK, dark).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 是否已阅读并同意隐私条款与服务条款（首次启动时确认过一次就记下来） */
    fun loadAgreed(): Boolean {
        val sp = prefs ?: return false
        return try {
            sp.getBoolean(KEY_AGREED, false)
        } catch (e: Exception) {
            false
        }
    }

    fun saveAgreed(agreed: Boolean) {
        val sp = prefs ?: return
        try {
            sp.edit().putBoolean(KEY_AGREED, agreed).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /* ---------------- 回收站 ---------------- */

    /**
     * 读取回收站。超过保留期的记录在这里就被丢掉，
     * 不必另外跑清理任务——读的时候顺手做最省事，也不会漏。
     */
    fun loadTrash(keepDays: Int = 30): MutableList<TrashItem> {
        val sp = prefs ?: return mutableListOf()
        val raw = try {
            sp.getString(KEY_TRASH, "[]") ?: "[]"
        } catch (e: Exception) {
            "[]"
        }
        if (raw.isEmpty()) return mutableListOf()
        val deadline = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        val out = mutableListOf<TrashItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.optJSONObject(i) ?: continue
                val storedAt = if (o.has("deletedAt")) o.optLong("deletedAt", 0L) else 0L
                // 缺失或为 0 的 deletedAt 不能当"永不过期"处理：那样一条脏记录会在回收站里活到永远，
                // 30 天的保留期承诺就形同虚设。按"刚刚删的"算，到期照常清除（与鸿蒙版 TrashCalc.normalize 一致）。
                val deletedAt = if (storedAt > 0L) storedAt else System.currentTimeMillis()
                if (deletedAt < deadline) continue
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
                out.add(TrashItem(item, deletedAt))
            }
        } catch (e: Exception) {
            return out
        }
        return out
    }

    fun saveTrash(list: List<TrashItem>) {
        val sp = prefs ?: return
        try {
            val arr = JSONArray()
            for (t in list) {
                val it = t.item
                val o = JSONObject()
                o.put("id", it.id)
                o.put("name", it.name)
                o.put("price", it.price)
                o.put("buyDate", it.buyDate)
                o.put("category", it.category)
                o.put("note", it.note)
                o.put("favorite", it.favorite)
                o.put("createdAt", it.createdAt)
                o.put("deletedAt", t.deletedAt)
                arr.put(o)
            }
            sp.edit().putString(KEY_TRASH, arr.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /* ---------------- 勋章 ---------------- */

    /** 已点亮的勋章：id -> 获得时间戳 */
    fun loadBadges(): MutableMap<String, Long> {
        val sp = prefs ?: return mutableMapOf()
        val raw = try {
            sp.getString(KEY_BADGE, "{}") ?: "{}"
        } catch (e: Exception) {
            "{}"
        }
        val out = mutableMapOf<String, Long>()
        try {
            val o = JSONObject(raw)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.optLong(k, 0L)
                if (v > 0L) out[k] = v
            }
        } catch (e: Exception) {
            return out
        }
        return out
    }

    fun saveBadges(map: Map<String, Long>) {
        val sp = prefs ?: return
        try {
            val o = JSONObject()
            for ((k, v) in map) o.put(k, v)
            sp.edit().putString(KEY_BADGE, o.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /* ---------------- 自动备份的轮转位置 ---------------- */

    /** 下一个要写入的备份槽位（1..slots） */
    fun loadBackupSlot(slots: Int): Int {
        val sp = prefs ?: return 1
        val v = try {
            sp.getInt(KEY_BACKUP_SLOT, 1)
        } catch (e: Exception) {
            1
        }
        return if (v in 1..slots) v else 1
    }

    fun saveBackupSlot(slot: Int) {
        val sp = prefs ?: return
        try {
            sp.edit().putInt(KEY_BACKUP_SLOT, slot).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 上次自动备份的时间，用来控制留快照的间隔 */
    fun loadBackupAt(): Long {
        val sp = prefs ?: return 0L
        return try {
            sp.getLong(KEY_BACKUP_AT, 0L)
        } catch (e: Exception) {
            0L
        }
    }

    fun saveBackupAt(at: Long) {
        val sp = prefs ?: return
        try {
            sp.edit().putLong(KEY_BACKUP_AT, at).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** 生成唯一 id */
    fun newId(): String = "d" + System.currentTimeMillis().toString() + (0..9999).random().toString()
}
