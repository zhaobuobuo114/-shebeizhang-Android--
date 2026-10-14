package com.deviceledger.app

import com.deviceledger.app.util.Lang
import com.deviceledger.app.util.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * "跟随系统"到底解析成哪一种语言。
 *
 * 这张表与鸿蒙端 `_test_lang_resolve.mjs` 逐行对应，两端必须同时改：
 * 鸿蒙拿到的是一串 BCP-47 文本（`i18n.System.getSystemLanguages()`），安卓拿到的是
 * `Locale` 对象，输入形态不同，但**判定规则必须完全一致**，否则同一台双持设备的
 * 两边会一个显示简体一个显示繁体。
 *
 * 唯一对不上的一行是 `zh_SG` 这种下划线写法：那是鸿蒙侧要自己切字符串才有的坑，
 * 安卓侧的语言从来由框架给成 `Locale`，轮不到下划线进来。这里改用等价的
 * `Locale("zh", "SG")`（真机上中文-新加坡确实会这么报），保证行数与语义仍对得上。
 */
class LangResolveTest {

    private data class Case(
        val preferred: String,
        val system: Locale,
        val want: Lang,
        val note: String
    )

    private val tag: (String) -> Locale = { Locale.forLanguageTag(it) }

    private val cases: List<Case> = listOf(
        Case("system", tag("zh"), Lang.ZH_CN, "裸语言子标签"),
        Case("system", tag("zh-CN"), Lang.ZH_CN, "语言+地区"),
        Case("system", tag("zh-Hans-CN"), Lang.ZH_CN, "真机常见形态：脚本不能被判成别的语言"),
        Case("system", tag("zh-Hant-TW"), Lang.ZH_TW, "繁体靠脚本识别"),
        Case("system", tag("zh-Hant-HK"), Lang.ZH_TW, "繁体 HK"),
        Case("system", tag("zh-TW"), Lang.ZH_TW, "无脚本、靠地区"),
        Case("system", tag("zh-HK"), Lang.ZH_TW, "无脚本、靠地区"),
        Case("system", tag("zh-MO"), Lang.ZH_TW, "澳门"),
        Case("system", Locale("zh", "SG"), Lang.ZH_CN, "中文-新加坡仍是简体"),
        Case("system", tag("zh-Hans-MO"), Lang.ZH_CN, "脚本优先于地区：Hans 即简体"),
        Case("system", tag("zh-Hant"), Lang.ZH_TW, "只有脚本没有地区"),
        Case("system", tag("zh-Hans"), Lang.ZH_CN, "只有脚本没有地区"),
        Case("system", tag("zh-Latn-TW"), Lang.ZH_TW, "认不出的脚本不能替繁体地区说不（原先在这里短路成简体）"),
        Case("system", tag("en"), Lang.EN, "裸英文"),
        Case("system", tag("en-US"), Lang.EN, "美式英语"),
        Case("system", tag("en-GB"), Lang.EN, "英式英语"),
        Case("system", tag("ja"), Lang.JA, "裸日文"),
        Case("system", tag("ja-JP"), Lang.JA, "日语"),
        Case("system", tag("JA-jp"), Lang.JA, "标签大小写不定，框架会规范化"),
        Case("system", tag("ZH-HANS-CN"), Lang.ZH_CN, "标签整体大写"),
        Case("system", tag("de-DE"), Lang.EN, "不支持的语言 → 英文，不掉回中文"),
        Case("system", tag("fr-FR"), Lang.EN, "同上"),
        Case("system", tag("ko-KR"), Lang.EN, "同上"),
        Case("system", tag("ru"), Lang.EN, "同上"),
        Case("system", tag(""), Lang.EN, "空标签"),
        Case("system", Locale.ROOT, Lang.EN, "取不到系统语言"),
        Case("zh-CN", tag("en-US"), Lang.ZH_CN, "手动选简中，系统英文也听用户的"),
        Case("zh-TW", tag("zh-Hans-CN"), Lang.ZH_TW, "手动选繁中，系统简体也听用户的"),
        Case("ja-JP", tag("zh-CN"), Lang.JA, "手动选日文"),
        Case("en", tag("zh-CN"), Lang.EN, "手动选英文")
    )

    @Test
    fun resolveTableMatchesHarmonyos() {
        for (c in cases) {
            assertEquals("${c.note}  [system=${c.preferred}, locale=${c.system}]", c.want, L10n.resolve(c.preferred, c.system))
        }
    }

    /**
     * 存了个我们不支持的值（老版本残留、手工改过偏好文件）时，宁可退回按系统语言走，
     * 也不要把 unknown 当成某种语言写死——更不能崩。
     */
    @Test
    fun unknownPreferenceFallsBackToSystem() {
        assertEquals(Lang.EN, L10n.resolve("ko-KR", Locale.ENGLISH))
        assertEquals(Lang.ZH_TW, L10n.resolve("ko-KR", Locale.TRADITIONAL_CHINESE))
        assertEquals(Lang.EN, L10n.resolve("", Locale.ENGLISH))
        assertEquals(Lang.ZH_CN, L10n.resolve("", Locale.SIMPLIFIED_CHINESE))
    }

    /** 四种语言的 tag 必须与资源目录/鸿蒙表名对得上，改这里要同时检查生成器。 */
    @Test
    fun langTagsAreStable() {
        assertEquals("zh-CN", Lang.ZH_CN.tag)
        assertEquals("zh-TW", Lang.ZH_TW.tag)
        assertEquals("ja-JP", Lang.JA.tag)
        assertEquals("en", Lang.EN.tag)
        assertEquals("system", L10n.FOLLOW_SYSTEM)
    }

    @Test
    fun langOfOnlyAcceptsOurOwnTags() {
        assertEquals(Lang.ZH_CN, Lang.of("zh-CN"))
        assertEquals(Lang.ZH_TW, Lang.of("zh-TW"))
        assertEquals(Lang.JA, Lang.of("ja-JP"))
        assertEquals(Lang.EN, Lang.of("en"))
        // 大小写与下划线都不算命中：这些形态只出现在系统标签里，不会出现在我们写回的偏好里
        assertNull(Lang.of("zh_CN"))
        assertNull(Lang.of("ZH-cn"))
        assertNull(Lang.of("zh"))
        assertNull(Lang.of(""))
        assertNull(Lang.of(null))
    }

    /**
     * 系统 Locale 必须逐项读得准，不能被 `Locale.getDefault()` 顶包：
     * 后者在 appcompat 写入 per-app locale 后会变成"应用生效语言"，
     * 那样"跟随系统"就永远停在用户上一次的手动选择上（v2.0.1 修的就是这个）。
     */
    @Test
    fun traditionalDetectionReadsScriptBeforeRegion() {
        assertEquals(Lang.ZH_TW, L10n.resolve("system", tag("zh-Hant-CN")))
        assertEquals(Lang.ZH_CN, L10n.resolve("system", tag("zh-Hans-TW")))
        assertEquals(Lang.ZH_TW, L10n.resolve("system", Locale("zh", "TW")))
        assertEquals(Lang.ZH_CN, L10n.resolve("system", Locale("zh")))
    }
}
