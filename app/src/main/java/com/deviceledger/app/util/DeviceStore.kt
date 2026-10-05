package com.deviceledger.app.util

import android.content.Context
import android.content.SharedPreferences
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
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

    /** 生成唯一 id */
    fun newId(): String = "d" + System.currentTimeMillis().toString() + (0..9999).random().toString()
}
