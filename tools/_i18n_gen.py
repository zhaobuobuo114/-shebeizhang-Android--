# -*- coding: utf-8 -*-
"""设备账 i18n 生成器：从 _i18n_master.json 生成两端资源文件。

产物
  Android/app/src/main/res/values/strings.xml            默认 = 简体中文
  Android/app/src/main/res/values-zh-rTW/strings.xml
  Android/app/src/main/res/values-ja/strings.xml
  Android/app/src/main/res/values-en/strings.xml
  HarmonyOS/entry/src/main/resources/base/element/string.json      默认 = 简体中文
  HarmonyOS/entry/src/main/resources/zh_CN|zh_TW|ja_JP|en_US/element/string.json

主表是唯一真源：{0} {1} 是占位符，ph 声明每个槽位的类型（s/d/f）。
校验规则（不满足直接非零退出）
  1 每个键必须有全部四种语言，且非空
  2 同一条里各语言用到的占位符集合必须完全一致（语言可以换顺序，不能多/少）
  3 含占位符的键必须给 ph，且长度等于最大槽位数
  4 plurals 表里 en 必须同时有 one 与 other，其余语言必须有 other
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
MASTER = os.path.join(ROOT, "_i18n_master.json")
OWNED = "_i18n_owned.json"

LANGS = ["zh-CN", "zh-TW", "ja", "en"]
ANDROID_DIR = {"zh-CN": "values", "zh-TW": "values-zh-rTW", "ja": "values-ja", "en": "values-en"}
HARMONY_DIR = {"zh-CN": "zh_CN", "zh-TW": "zh_TW", "ja": "ja_JP", "en": "en_US"}

SLOT = re.compile(r"\{(\d+)\}")


def die(msg):
    print("!! " + msg)
    sys.exit(1)


def load():
    m = json.load(io.open(MASTER, encoding="utf-8"))
    return m["strings"], m.get("plurals", {})


def slots_of(text):
    return [int(x) for x in SLOT.findall(text)]


def validate(strings, plurals):
    errors = []
    for name, table in strings.items():
        for lang in LANGS:
            if lang not in table:
                errors.append("%s 缺语言 %s" % (name, lang))
            elif not table[lang].strip():
                errors.append("%s 的 %s 是空串" % (name, lang))
        extra = set(table) - set(LANGS) - {"ph"}
        if extra:
            errors.append("%s 有多余字段 %s" % (name, sorted(extra)))
        per_lang = {}
        for lang in LANGS:
            if lang in table:
                got = sorted(set(slots_of(table[lang])))
                per_lang[lang] = got
        union = set()
        for v in per_lang.values():
            union |= set(v)
        if union:
            expect = list(range(max(union) + 1))
            if sorted(union) != expect:
                errors.append("%s 占位符不连续: %s" % (name, sorted(union)))
            for lang, got in per_lang.items():
                if sorted(got) != sorted(union):
                    errors.append("%s 各语言占位符不一致: %s=%s 其余=%s"
                                  % (name, lang, got, sorted(union)))
            ph = table.get("ph")
            if not ph:
                errors.append("%s 用了占位符却没有 ph" % name)
            elif len(ph) != len(union):
                errors.append("%s ph 有 %d 项，占位符有 %d 个" % (name, len(ph), len(union)))
            elif sorted(set(ph)) and any(t not in "sdf" for t in "".join(ph)):
                errors.append("%s ph 类型只能是 s/d/f: %s" % (name, ph))
    for name, table in plurals.items():
        if name in strings:
            errors.append("plurals 与 strings 撞名: %s" % name)
        for lang in LANGS:
            if lang not in table:
                errors.append("复数 %s 缺语言 %s" % (name, lang))
                continue
            forms = table[lang]
            if "other" not in forms:
                errors.append("复数 %s 的 %s 缺 other" % (name, lang))
            if lang == "en" and "one" not in forms:
                errors.append("复数 %s 的 en 缺 one" % name)
            for q, txt in forms.items():
                if q not in ("one", "other"):
                    errors.append("复数 %s 的 %s 有不支持的量形 %s（本项目只出 one/other）" % (name, lang, q))
        refs = sorted(set(int(x) for lang in table for x in slots_of(table[lang].get("other", ""))))
        for lang, forms in table.items():
            for q, txt in forms.items():
                if sorted(set(slots_of(txt))) != refs:
                    errors.append("复数 %s 的 %s/%s 占位符与 other 不一致" % (name, lang, q))
    return errors


ANDROID_HEAD = (u'<?xml version="1.0" encoding="utf-8"?>\n'
                u'<!-- 由 _i18n_gen.py 从 _i18n_master.json 生成，请勿手改；改主表再重跑。 -->\n'
                u'<resources>\n')
ANDROID_TAIL = u'</resources>\n'


def esc_android(text, positional=True):
    """{n} -> %k$s；其余字面量转义，游离的 % 变 %%。"""
    out = []
    i = 0
    while i < len(text):
        m = SLOT.match(text, i)
        if m:
            n = int(m.group(1))
            out.append(u"%%%d$s" % (n + 1) if positional else u"%s")
            i = m.end()
            continue
        ch = text[i]
        if ch == "%":
            out.append(u"%%")
        elif ch == '"':
            out.append(u'\\"')
        elif ch == "'":
            out.append(u"\\'")
        elif ch == "&":
            out.append(u"&amp;")
        elif ch == "<":
            out.append(u"&lt;")
        elif ch == ">":
            out.append(u"&gt;")
        elif ch == "\\":
            out.append(u"\\\\")
        elif ch == "\n":
            out.append(u"\\n")
        elif i == 0 and (ch == u"@" or ch == u"?"):
            # 值首的 @ / ? 会被 aapt 当成资源引用或主题属性解析（"@string/x"、"?attr/y"），
            # 轻则编译失败、重则运行时 NotFoundException。英文文案里 "@device 已同步"
            # 这种开头是很自然写法，所以在这里挡住；值中间的 @/? 不用转义。
            out.append(u"\\" + ch)
        else:
            out.append(ch)
        i += 1
    return u"".join(out)


def write_android(strings, plurals):
    counts = {}
    for lang, dirname in ANDROID_DIR.items():
        d = os.path.join(ROOT, "Android", "app", "src", "main", "res", dirname)
        if not os.path.isdir(d):
            os.makedirs(d)
        lines = [ANDROID_HEAD]
        lines.append(u'    <!-- %s -->\n' % lang)
        for name in sorted(strings):
            lines.append(u'    <string name="%s">%s</string>\n' % (name, esc_android(strings[name][lang])))
        for name in sorted(plurals):
            forms = plurals[name][lang]
            lines.append(u'    <plurals name="%s">\n' % name)
            for q in ("one", "other"):
                if q in forms:
                    lines.append(u'        <item quantity="%s">%s</item>\n' % (q, esc_android(forms[q])))
            lines.append(u'    </plurals>\n')
        lines.append(ANDROID_TAIL)
        io.open(os.path.join(d, "strings.xml"), "w", encoding="utf-8", newline="\n").write(u"".join(lines))
        counts[lang] = len(strings) + len(plurals)
    return counts


def read_existing(path):
    """读回已存在的 string.json，保留主表之外的键（如 module_desc）。"""
    if not os.path.isfile(path):
        return []
    try:
        data = json.load(io.open(path, encoding="utf-8"))
    except ValueError:
        die("现有资源文件不是合法 JSON: " + path)
    return data.get("string", [])


def owned_names():
    """上一次由本脚本写进去的键名。主表里删掉的键靠它清理，不会当成"原有键"留下来。"""
    f = os.path.join(ROOT, OWNED)
    if not os.path.isfile(f):
        return set()
    return set(json.load(io.open(f, encoding="utf-8")))


def merge_entries(existing, generated, stale):
    gen_names = set(e["name"] for e in generated)
    kept = [e for e in existing if e["name"] not in gen_names and e["name"] not in stale]
    order = ["module_desc", "EntryAbility_desc", "EntryAbility_label"]
    kept.sort(key=lambda e: (order.index(e["name"]) if e["name"] in order else 99, e["name"]))
    return kept + generated, [e["name"] for e in kept]


LANG_DIR_HARMONY = {"zh-CN": "zh_CN", "zh-TW": "zh_TW", "ja": "ja_JP", "en": "en_US"}
# 鸿蒙原生资源限定目录只在冷启动时生效（i18n.System.setAppPreferredLanguage 的官方说明），
# 所以全量文案另外落成 rawfile 表，由 utils/I18n.ets 在运行时读取，切换语言才能原地生效。
# element/string.json 里只留桌面可见的那几个键。
HARMONY_LAUNCHER_KEYS = ["module_desc", "EntryAbility_desc", "EntryAbility_label"]


def write_harmony(strings, plurals):
    stale = owned_names()
    written = set()
    counts = {}

    # 1) 运行时表：rawfile/i18n/<locale>.json，四种语言各一份全量
    raw_dir = os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "resources", "rawfile", "i18n")
    if not os.path.isdir(raw_dir):
        os.makedirs(raw_dir)
    for lang, dirname in LANG_DIR_HARMONY.items():
        body = {
            "lang": lang,
            "strings": dict((k, strings[k][lang]) for k in sorted(strings)),
            "plurals": dict((k, dict(plurals[k][lang])) for k in sorted(plurals)),
        }
        io.open(os.path.join(raw_dir, dirname + ".json"), "w", encoding="utf-8", newline="\n").write(
            json.dumps(body, ensure_ascii=False, indent=2) + "\n")
    counts["rawfile"] = len(LANG_DIR_HARMONY)

    # 2) 桌面与模块名：base + 四个限定目录 + AppScope
    tables = [("base", "zh-CN"), ("AppScope-base", "zh-CN")]
    for lang, dirname in LANG_DIR_HARMONY.items():
        tables.append((dirname, lang))
        tables.append(("AppScope-" + dirname, lang))
    for dirname, lang in tables:
        appscope = dirname.startswith("AppScope-")
        if appscope:
            sub = dirname.split("-", 1)[1]
            d = os.path.join(ROOT, "HarmonyOS", "AppScope", "resources", sub, "element")
            entries = [{"name": "app_name", "value": strings["app_name"][lang]}]
        else:
            d = os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "resources", dirname, "element")
            if dirname == "base":
                picked = HARMONY_LAUNCHER_KEYS
            else:
                picked = [k for k in HARMONY_LAUNCHER_KEYS if k in strings]
            entries = [{"name": k, "value": strings[k][lang]} for k in picked]
        if not os.path.isdir(d):
            os.makedirs(d)
        target = os.path.join(d, "string.json")
        existing = read_existing(target)
        final, kept = merge_entries(existing, entries, stale)
        io.open(target, "w", encoding="utf-8", newline="\n").write(
            json.dumps({"string": final}, ensure_ascii=False, indent=2) + "\n")
        counts[dirname] = (len(final), len(kept))
        written.update(e["name"] for e in entries)
    io.open(os.path.join(ROOT, OWNED), "w", encoding="utf-8", newline="\n").write(
        json.dumps(sorted(written), ensure_ascii=False, indent=2) + "\n")
    return counts




def main():
    strings, plurals = load()
    errs = validate(strings, plurals)
    if errs:
        print("主表校验未通过，共 %d 条：" % len(errs))
        for e in errs[:80]:
            print("  - " + e)
        if len(errs) > 80:
            print("  ...还有 %d 条" % (len(errs) - 80))
        sys.exit(1)
    ac = write_android(strings, plurals)
    hc = write_harmony(strings, plurals)
    print("OK  主表 %d 条 + 复数 %d 条" % (len(strings), len(plurals)))
    print("    Android:", ", ".join("%s=%d" % kv for kv in sorted(ac.items())))
    parts = []
    for k in sorted(hc):
        v = hc[k]
        parts.append("%s=%d份语言表" % (k, v) if isinstance(v, int) else "%s=%d(生成)+%d(原有)" % (k, v[0], v[1]))
    print("    Harmony:", ", ".join(parts))


if __name__ == "__main__":
    main()
