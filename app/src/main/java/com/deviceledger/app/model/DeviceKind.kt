package com.deviceledger.app.model

import androidx.annotation.DrawableRes
import com.deviceledger.app.R

/**
 * 设备类型表：key 为存储用标识，label 为展示名，colorHex 为图表 / 标签主题色。
 * 每个 key 对应一张 Q 版图标（res/drawable-nodpi/dev_<key>.png）。
 */
data class DeviceKind(
    val key: String,
    val label: String,
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
    DeviceKind("phone", "手机", "#4F8CFF"),
    DeviceKind("laptop", "笔记本", "#6C7BFF"),
    DeviceKind("tablet", "平板", "#38BDF8"),
    DeviceKind("desktop", "台式主机", "#64748B"),
    DeviceKind("monitor", "显示器", "#14B8A6"),
    DeviceKind("tv", "电视", "#F59E0B"),
    DeviceKind("projector", "投影仪", "#8B5CF6"),
    DeviceKind("camera", "相机", "#EF4444"),
    DeviceKind("actioncam", "运动相机", "#FB923C"),
    DeviceKind("drone", "无人机", "#22C55E"),
    DeviceKind("headphone", "头戴耳机", "#A855F7"),
    DeviceKind("earbuds", "蓝牙耳机", "#EC4899"),
    DeviceKind("speaker", "音箱", "#F43F5E"),
    DeviceKind("smartspeaker", "智能音箱", "#06B6D4"),
    DeviceKind("mic", "麦克风", "#84CC16"),
    DeviceKind("watch", "智能手表", "#10B981"),
    DeviceKind("band", "智能手环", "#2DD4BF"),
    DeviceKind("vr", "VR 眼镜", "#7C3AED"),
    DeviceKind("console", "游戏主机", "#334155"),
    DeviceKind("handheld", "掌上游戏机", "#DB2777"),
    DeviceKind("keyboard", "键盘", "#475569"),
    DeviceKind("mouse", "鼠标", "#94A3B8"),
    DeviceKind("drawing", "数位板", "#0891B2"),
    DeviceKind("stylus", "手写笔", "#FBBF24"),
    DeviceKind("printer", "打印机", "#0F766E"),
    DeviceKind("router", "路由器", "#2563EB"),
    DeviceKind("nas", "硬盘 / 存储", "#A5B4FC"),
    DeviceKind("powerbank", "充电宝", "#FACC15"),
    DeviceKind("charger", "充电器", "#F87171"),
    DeviceKind("fridge", "冰箱", "#93C5FD"),
    DeviceKind("washer", "洗衣机", "#67E8F9"),
    DeviceKind("ac", "空调", "#2E9BD6"),
    DeviceKind("microwave", "微波炉", "#FDBA74"),
    DeviceKind("fan", "风扇", "#5EEAD4"),
    DeviceKind("robot", "扫地机器人", "#C4B5FD"),
    DeviceKind("ereader", "电纸书", "#A8A29E"),
    DeviceKind("hairdryer", "吹风机", "#FB7185"),
    DeviceKind("toothbrush", "电动牙刷", "#4ADE80"),
    DeviceKind("shaver", "剃须刀", "#60A5FA"),
    DeviceKind("gamepad", "游戏手柄", "#9333EA"),
    DeviceKind("other", "其他", "#9CA3AF")
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

fun kindLabel(key: String): String = findKind(key).label

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
