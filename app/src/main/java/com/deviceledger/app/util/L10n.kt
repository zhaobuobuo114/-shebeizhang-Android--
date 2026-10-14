package com.deviceledger.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 界面语言。
 *
 * 只有四种，且刻意不做"资源缺项就回落到 base"的那套隐式行为：
 * 系统语言落在这四种之外时统一按英文走（base 是简体中文，让外国人看中文不合理）。
 * 各语言的显示名取 `lang_*` 资源，随当前语言变化。
 */
enum class Lang(val tag: String) {
    ZH_CN("zh-CN"),
    ZH_TW("zh-TW"),
    JA("ja-JP"),
    EN("en");

    companion object {
        fun of(tag: String?): Lang? {
            if (tag.isNullOrEmpty()) return null
            return values().firstOrNull { it.tag == tag }
        }
    }
}

/**
 * 语言中枢：解析"跟随系统"该用哪种语言、把它落到 appcompat 的 per-app locale，
 * 并给全工程提供一条取文案的入口。
 *
 * 取文案一律走这里而不是散落的 context.getString，有两个原因：
 * 一是复数形式要在鸿蒙（没有 <plurals>）与安卓之间保持同一条规则，
 * 二是切语言后需要能一次性重刷所有赋值点，入口统一才好核对。
 */
object L10n {

    /** 持久化里的"跟随系统"哨兵值 */
    const val FOLLOW_SYSTEM = "system"

    private var current: Lang = Lang.ZH_CN

    /** 当前生效的语言。取文案前它必须已经就绪，所以默认给简体中文（与 base 一致）。 */
    fun lang(): Lang = current

    /** 用户在设置里选的值：四种语言之一，或 system */
    fun preferred(): String = DeviceStore.loadLanguage()

    /**
     * 把"用户选的"化成"实际用的"。
     * 选跟随系统时看系统语言；系统语言不在四种之内就按英文，不回落中文。
     */
    fun resolve(preferred: String, system: Locale): Lang {
        if (preferred != FOLLOW_SYSTEM) Lang.of(preferred)?.let { return it }
        return when (system.language.lowercase(Locale.ROOT)) {
            "zh" -> if (isTraditional(system)) Lang.ZH_TW else Lang.ZH_CN
            "ja" -> Lang.JA
            "en" -> Lang.EN
            else -> Lang.EN
        }
    }

    /**
     * 中文要再分简繁。
     * 优先看脚本（zh-Hant / zh-HK / zh-MO / zh-TW 都算繁），
     * 老系统上 scriptCode 可能是空串，只能退回看地区。
     */
    private fun isTraditional(system: Locale): Boolean {
        val script = system.script
        if (!script.isNullOrEmpty()) {
            if (script.equals("Hant", ignoreCase = true)) return true
            // 认不出的脚本不能替繁体地区说"不"：zh-Latn-TW 这种虽然现实中不会出现，
            // 但鸿蒙端是拆子标签的，看不到 hant 就继续看地区；这边短路成简体，
            // 两端就会对同一个标签给出不同结论。
            if (!script.equals("Hans", ignoreCase = true)) return isTraditionalRegion(system)
            return false
        }
        return isTraditionalRegion(system)
    }

    private fun isTraditionalRegion(system: Locale): Boolean =
        when (system.country.uppercase(Locale.ROOT)) {
            "TW", "HK", "MO" -> true
            else -> false
        }

    /**
     * 落到 appcompat 的 per-app locale。
     *
     * 注意这里永远写一个明确的 tag，绝不传空表：传空表示"交还给系统"，
     * 而系统语言可能压根不在四种里，那样就会掉进 base 的简体中文。
     */
    fun apply(context: Context): Lang {
        val target = resolve(preferred(), deviceLocale(context))
        current = target
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target.tag))
        return target
    }

    /**
     * 设备语言，不是"本应用当前生效的语言"。
     *
     * 两者必须分开取，否则"跟随系统"永远选不中：per-app locale 是持久化的，
     * 用户从日语切回"跟随系统"时，凡是读"应用生效语言"的入口都还会返回日语。
     *
     * 33 以上这个坑尤其深：per-app locale 是**系统**能力，覆盖发生在进程级，
     * 连 `Resources.getSystem()` 的配置都已经是本应用的那份了（实测：设备 en-US、
     * 手动选过日语、再切跟随系统，界面停在日语）。只有 `LocaleManager.getSystemLocales()`
     * 能把设备语言与应用语言分开。
     * 33 以下 per-app locale 只是 appcompat 自己的偏好，不碰系统配置，
     * 那时 `Resources.getSystem()` 才是干净的设备语言。
     */
    private fun deviceLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val system = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (system != null && !system.isEmpty) return system[0] ?: Locale.getDefault()
        }
        return Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    }

    /** 冷启动时同步一次，不走 setApplicationLocales（那时语言本来就还是对的） */
    fun syncCurrent(context: Context) {
        // 这里读的是"本应用当前生效"的配置，不是设备语言：这个字段要描述的就是
        // Resources 现在按哪门语言取字，两者必须一致，所以不能改用 deviceLocale()。
        val effective = context.resources.configuration.locales[0] ?: Locale.getDefault()
        current = resolve(preferred(), effective)
    }

    /**
     * 取带参数的文案。
     *
     * 约定：**整数参数一律 `.toString()` 之后再传**。生成器把主表里的 `{0}` 一律写成 `%1$s`
     * 而不是 `%1$d`，这是刻意的——`%s` 走 `String.valueOf`，任何语言下都输出 ASCII 数字；
     * 而 `%d` 会按当前 Locale 的十进制符号格式化，在阿拉伯语环境下会把 12 排成 `١٢`，
     * 扇形图的宽度自适应与价格解析都会跟着崩（与 `Fmt` 里两处 `Locale.US` 同一个理由）。
     *
     * 传 Int 虽然不崩（`%s` 接受任意类型），但会让 lint 的 `StringFormatMatches`
     * 一直报警——那条检查真正的价值是抓"参数个数对不上"（那才会崩 MissingFormatArgumentException），
     * 长期挂着 50 多条噪音就等于废掉它。
     */
    fun str(context: Context, id: Int, vararg args: Any?): String =
        context.getString(id, *args)

    /**
     * 复数。
     *
     * 安卓这边只区分 one / other 两档——四种语言都够用（ja/zh 只用到 other）。
     * 鸿蒙没有 <plurals> 机制，那边用同一份主表生成的 `xxx__one` / `xxx__other` 两个键，
     * 判定规则与此处逐字一致，两端才不会一个显示 "1 day" 一个显示 "1 days"。
     */
    fun quantity(context: Context, id: Int, count: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, count, *args)

    /** 数字是否要用万/亿这类汉字简写。英文改用 K/M。 */
    fun useWanNumerals(): Boolean = current != Lang.EN

}
