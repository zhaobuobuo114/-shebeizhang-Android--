package com.deviceledger.app.model

import android.content.Context

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.deviceledger.app.R

/**
 * 设备类型表：key 为存储用标识，labelRes 为展示名的字符串资源，colorHex 为图表 / 标签主题色。
 * 每个 key 对应一张 Q 版图标（res/drawable-nodpi/dev_<key>.png）。
 *
 * v2.0.0 起展示名进资源表（四种语言），但 **key 恒为 ASCII 且绝不翻译**：
 * 它写进 SharedPreferences 与导出文件，另一端靠它认类型。
 */
data class DeviceKind(
    val key: String,
    @field:StringRes val labelRes: Int,
    val colorHex: String
)

/** 旧版本分类名 -> 新 key，用于老数据兼容 */
private val LEGACY_MAP: Map<String, String> = mapOf(
    "手机" to "phone",
    "电脑" to "laptop",
    "平板" to "tablet",
    "影音" to "speaker",
    "家电" to "other",
    "摄影" to "camera",
    "其他" to "other"
)

val DEVICE_KINDS: List<DeviceKind> = listOf(
    DeviceKind("phone", R.string.kind_phone, "#4F8CFF"),
    DeviceKind("laptop", R.string.kind_laptop, "#6C7BFF"),
    DeviceKind("tablet", R.string.kind_tablet, "#38BDF8"),
    DeviceKind("desktop", R.string.kind_desktop, "#64748B"),
    DeviceKind("monitor", R.string.kind_monitor, "#14B8A6"),
    DeviceKind("tv", R.string.kind_tv, "#F59E0B"),
    DeviceKind("projector", R.string.kind_projector, "#8B5CF6"),
    DeviceKind("camera", R.string.kind_camera, "#EF4444"),
    DeviceKind("actioncam", R.string.kind_actioncam, "#FB923C"),
    DeviceKind("drone", R.string.kind_drone, "#22C55E"),
    DeviceKind("headphone", R.string.kind_headphone, "#A855F7"),
    DeviceKind("earbuds", R.string.kind_earbuds, "#EC4899"),
    DeviceKind("speaker", R.string.kind_speaker, "#F43F5E"),
    DeviceKind("smartspeaker", R.string.kind_smartspeaker, "#06B6D4"),
    DeviceKind("mic", R.string.kind_mic, "#84CC16"),
    DeviceKind("watch", R.string.kind_watch, "#10B981"),
    DeviceKind("band", R.string.kind_band, "#2DD4BF"),
    DeviceKind("vr", R.string.kind_vr, "#7C3AED"),
    DeviceKind("console", R.string.kind_console, "#334155"),
    DeviceKind("handheld", R.string.kind_handheld, "#DB2777"),
    DeviceKind("keyboard", R.string.kind_keyboard, "#475569"),
    DeviceKind("mouse", R.string.kind_mouse, "#94A3B8"),
    DeviceKind("drawing", R.string.kind_drawing, "#0891B2"),
    DeviceKind("stylus", R.string.kind_stylus, "#FBBF24"),
    DeviceKind("printer", R.string.kind_printer, "#0F766E"),
    DeviceKind("router", R.string.kind_router, "#2563EB"),
    DeviceKind("nas", R.string.kind_nas, "#A5B4FC"),
    DeviceKind("powerbank", R.string.kind_powerbank, "#FACC15"),
    DeviceKind("charger", R.string.kind_charger, "#F87171"),
    DeviceKind("fridge", R.string.kind_fridge, "#93C5FD"),
    DeviceKind("washer", R.string.kind_washer, "#67E8F9"),
    DeviceKind("ac", R.string.kind_ac, "#2E9BD6"),
    DeviceKind("microwave", R.string.kind_microwave, "#FDBA74"),
    DeviceKind("fan", R.string.kind_fan, "#5EEAD4"),
    DeviceKind("robot", R.string.kind_robot, "#C4B5FD"),
    DeviceKind("ereader", R.string.kind_ereader, "#A8A29E"),
    DeviceKind("hairdryer", R.string.kind_hairdryer, "#FB7185"),
    DeviceKind("toothbrush", R.string.kind_toothbrush, "#4ADE80"),
    DeviceKind("shaver", R.string.kind_shaver, "#60A5FA"),
    DeviceKind("gamepad", R.string.kind_gamepad, "#9333EA"),
    DeviceKind("other", R.string.kind_other, "#9CA3AF")
)

/** 判断是否为合法的类型 key */
fun isValidKind(key: String): Boolean {
    for (k in DEVICE_KINDS) {
        if (k.key == key) return true
    }
    return false
}

/** 把任意脏数据解析成合法 key */
fun resolveKind(key: String): String {
    if (isValidKind(key)) return key
    val legacy = LEGACY_MAP[key]
    if (legacy != null) return legacy
    return "other"
}

fun findKind(key: String): DeviceKind {
    val k = resolveKind(key)
    for (item in DEVICE_KINDS) {
        if (item.key == k) return item
    }
    return DEVICE_KINDS[DEVICE_KINDS.size - 1]
}

fun kindLabel(context: Context, key: String): String = context.getString(findKind(key).labelRes)

fun kindColorHex(key: String): String = findKind(key).colorHex

/** key -> Q 版图标资源（编译期即可校验资源是否存在） */
@DrawableRes
fun kindIconRes(key: String): Int = when (resolveKind(key)) {
    "phone" -> R.drawable.dev_phone
    "laptop" -> R.drawable.dev_laptop
    "tablet" -> R.drawable.dev_tablet
    "desktop" -> R.drawable.dev_desktop
    "monitor" -> R.drawable.dev_monitor
    "tv" -> R.drawable.dev_tv
    "projector" -> R.drawable.dev_projector
    "camera" -> R.drawable.dev_camera
    "actioncam" -> R.drawable.dev_actioncam
    "drone" -> R.drawable.dev_drone
    "headphone" -> R.drawable.dev_headphone
    "earbuds" -> R.drawable.dev_earbuds
    "speaker" -> R.drawable.dev_speaker
    "smartspeaker" -> R.drawable.dev_smartspeaker
    "mic" -> R.drawable.dev_mic
    "watch" -> R.drawable.dev_watch
    "band" -> R.drawable.dev_band
    "vr" -> R.drawable.dev_vr
    "console" -> R.drawable.dev_console
    "handheld" -> R.drawable.dev_handheld
    "keyboard" -> R.drawable.dev_keyboard
    "mouse" -> R.drawable.dev_mouse
    "drawing" -> R.drawable.dev_drawing
    "stylus" -> R.drawable.dev_stylus
    "printer" -> R.drawable.dev_printer
    "router" -> R.drawable.dev_router
    "nas" -> R.drawable.dev_nas
    "powerbank" -> R.drawable.dev_powerbank
    "charger" -> R.drawable.dev_charger
    "fridge" -> R.drawable.dev_fridge
    "washer" -> R.drawable.dev_washer
    "ac" -> R.drawable.dev_ac
    "microwave" -> R.drawable.dev_microwave
    "fan" -> R.drawable.dev_fan
    "robot" -> R.drawable.dev_robot
    "ereader" -> R.drawable.dev_ereader
    "hairdryer" -> R.drawable.dev_hairdryer
    "toothbrush" -> R.drawable.dev_toothbrush
    "shaver" -> R.drawable.dev_shaver
    "gamepad" -> R.drawable.dev_gamepad
    else -> R.drawable.dev_other
}
