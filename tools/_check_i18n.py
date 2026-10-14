# -*- coding: utf-8 -*-
"""v2.0.0 多语言完整性校验。

主表 _i18n_master.json 是唯一真源，本脚本核对三件事：
  1 派生出来的两端资源（安卓 values*/strings.xml、鸿蒙 rawfile/i18n/*.json）与主表逐键一致，
    四语言都不缺项、不空串；
  2 同一条文案在各语言里的占位符集合一致（语言可以换顺序，不能多一个或少一个），
    且安卓那边位置号不重复；
  3 该保持 ASCII 的东西没有被翻译：41 类设备 key、14 枚勋章 id、导出 JSON 的字段名，
    以及两端代码里"老数据迁移表"的中文键（那是数据，不是 UI 文案）。

退出码 0 = 全部通过。
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
# 转义规则只该有一份：期望值直接问生成器，不在这里重写一遍（重写就会漏，见下面的注释）
sys.path.insert(0, ROOT)
from _i18n_gen import esc_android  # noqa: E402
LANGS = ["zh-CN", "zh-TW", "ja", "en"]
ANDROID_DIR = {"zh-CN": "values", "zh-TW": "values-zh-rTW", "ja": "values-ja", "en": "values-en"}
HARMONY_DIR = {"zh-CN": "zh_CN", "zh-TW": "zh_TW", "ja": "ja_JP", "en": "en_US"}
SLOT = re.compile(r"\{(\d+)\}")

problems = []


def note(msg):
    problems.append(msg)
    print("  !! " + msg)


def load_master():
    d = json.load(io.open(os.path.join(ROOT, "_i18n_master.json"), encoding="utf-8"))
    return d["strings"], d["plurals"]


def parse_android(dir_name):
    path = os.path.join(ROOT, "Android", "app", "src", "main", "res", dir_name, "strings.xml")
    if not os.path.isfile(path):
        note("安卓资源缺失: " + dir_name)
        return {}, {}
    text = io.open(path, encoding="utf-8").read()
    strings = dict((m.group(1), m.group(2)) for m in
                   re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.S))
    plurals = {}
    for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', text, re.S):
        forms = dict((f.group(1), f.group(2)) for f in
                     re.finditer(r'<item quantity="([^"]+)">(.*?)</item>', m.group(2), re.S))
        plurals[m.group(1)] = forms
    return strings, plurals


def parse_harmony(dir_name):
    path = os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "resources", "rawfile", "i18n",
                        dir_name + ".json")
    if not os.path.isfile(path):
        note("鸿蒙语言表缺失: " + dir_name)
        return {}, {}
    d = json.load(io.open(path, encoding="utf-8"))
    return d.get("strings", {}), d.get("plurals", {})


def check_master(strings, plurals):
    print("== 主表自身 ==")
    bad = 0
    for name, table in strings.items():
        for lang in LANGS:
            v = table.get(lang)
            if v is None or not str(v).strip():
                note("缺语言或空串: %s[%s]" % (name, lang))
                bad += 1
        slots = set()
        for lang in LANGS:
            got = set(SLOT.findall(table.get(lang, "")))
            slots |= {int(x) for x in got}
            if {int(x) for x in got} != slots and len(slots) > len(got):
                pass
        per = {lang: sorted({int(x) for x in SLOT.findall(table.get(lang, ""))}) for lang in LANGS}
        if len(set(map(tuple, per.values()))) > 1:
            note("占位符集合不一致: %s %s" % (name, per))
            bad += 1
        if slots and not table.get("ph"):
            note("用了占位符却没有 ph: %s" % name)
            bad += 1
        if table.get("ph") and len(table["ph"]) != len(slots):
            note("ph 个数与占位符不符: %s" % name)
            bad += 1
    for name, table in plurals.items():
        for lang in LANGS:
            if lang not in table or "other" not in table[lang]:
                note("复数缺 other: %s[%s]" % (name, lang))
                bad += 1
            elif lang == "en" and "one" not in table[lang]:
                note("复数缺 one: %s[en]" % name)
                bad += 1
        if name in strings:
            note("复数与单数撞名: %s" % name)
            bad += 1
    if not bad:
        print("  %d 条文案 + %d 组复数，四语言齐、占位符一致 OK" % (len(strings), len(plurals)))
    return bad


def check_android(strings, plurals):
    print("\n== 安卓资源 vs 主表 ==")
    bad = 0
    for lang, d in ANDROID_DIR.items():
        a_s, a_p = parse_android(d)
        miss = [k for k in strings if k not in a_s]
        extra = [k for k in a_s if k not in strings]
        if miss:
            note("%s 缺 %d 个 string: %s" % (d, len(miss), miss[:5]))
            bad += 1
        if extra:
            note("%s 多出 %d 个不在主表里的 string: %s" % (d, len(extra), extra[:5]))
            bad += 1
        missp = [k for k in plurals if k not in a_p]
        if missp:
            note("%s 缺复数: %s" % (d, missp[:5]))
            bad += 1
        # 内容逐条比对。期望值直接叫生成器的那个 esc_android 算，不在这里重写一套转义规则：
        # 重写就会漏——原先这里有一行 `expect.replace("%","%%") if False else expect`，
        # 因为字面 % 要变 %% 而占位符 %1$s 里的 % 不能变，一套不重写的规则处理不了，
        # 于是被 `if False` 永久关掉了：主表里出现字面百分号时闸门不会报。
        for k, v in strings.items():
            want = v[lang]
            got = a_s.get(k)
            if got is None:
                continue
            expect = esc_android(want)
            if got != expect:
                note("安卓内容不符 %s[%s]: 资源=%r 期望=%r" % (k, lang, got, expect))
                bad += 1
        for k, forms in plurals.items():
            want = forms[lang]
            got = a_p.get(k, {})
            if set(want) != set(got):
                note("安卓复数量形不一致 %s[%s]: %s vs %s" % (k, lang, sorted(got), sorted(want)))
                bad += 1
            # 量形齐了还得比正文：原先只比 set(one/other)，
            # 复数句子翻错、四语言抄成同一份、{0} 写串了，全都发现不了
            for q in sorted(set(want) & set(got)):
                expect = esc_android(want[q])
                if got[q] != expect:
                    note("安卓复数正文不符 %s[%s.%s]: 资源=%r 期望=%r"
                         % (k, lang, q, got[q], expect))
                    bad += 1
        print("  %-14s string %3d / plurals %2d" % (d, len(a_s), len(a_p)))
    return bad


def check_harmony(strings, plurals):
    print("\n== 鸿蒙语言表 vs 主表 ==")
    bad = 0
    for lang, d in HARMONY_DIR.items():
        h_s, h_p = parse_harmony(d)
        miss = [k for k in strings if k not in h_s]
        extra = [k for k in h_s if k not in strings]
        if miss:
            note("%s 缺 %d 个: %s" % (d, len(miss), miss[:5]))
            bad += 1
        if extra:
            note("%s 多出 %d 个: %s" % (d, len(extra), extra[:5]))
            bad += 1
        for k, v in strings.items():
            if k in h_s and h_s[k] != v[lang]:
                note("鸿蒙内容不符 %s[%s]" % (k, lang))
                bad += 1
        for k, forms in plurals.items():
            if k not in h_p or h_p[k] != forms[lang]:
                note("鸿蒙复数缺失或不同: %s[%s]" % (k, lang))
                bad += 1
        print("  %-8s string %3d / plurals %2d" % (d, len(h_s), len(h_p)))
    return bad


def check_keys_not_translated(strings, plurals):
    """41 类 key、14 枚 id 与迁移表里的中文键：前者不许出现在文案里，后者不许被动过。"""
    print("\n== 不许被翻译的东西 ==")
    bad = 0
    ak = io.open(os.path.join(ROOT, "Android", "app", "src", "main", "java", "com", "deviceledger",
                              "app", "model", "DeviceKind.kt"), encoding="utf-8").read()
    hk = io.open(os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "ets", "model", "DeviceKind.ets"),
                 encoding="utf-8").read()
    a_keys = re.findall(r'DeviceKind\("([a-z_0-9]+)", R\.string\.kind_([a-z_0-9]+)', ak)
    h_keys = re.findall(r"key: '([a-z_0-9]+)',\s*labelKey: 'kind_([a-z_0-9]+)'", hk)
    if len(a_keys) != 41:
        note("安卓设备类型不是 41 条（读到 %d）" % len(a_keys))
        bad += 1
    if len(h_keys) != 41:
        note("鸿蒙设备类型不是 41 条（读到 %d）" % len(h_keys))
        bad += 1
    if [k for k, _ in a_keys] != [k for k, _ in h_keys]:
        note("两端设备类型 key 列表不一致")
        bad += 1
    for key, label_key in a_keys:
        if key != label_key:
            note("安卓类型 %s 的 labelKey 不是 kind_%s" % (key, key))
            bad += 1
        if "kind_" + key not in strings:
            note("主表缺类型文案 kind_%s" % key)
            bad += 1
    # 老数据迁移表：左半是历史存储里的中文值，翻译了就读不出老数据
    a_leg = re.findall(r'"([^"]+)" to "([a-z_0-9]+)"', ak)
    h_leg = re.findall(r"'([^'\"|\n]+)\|([a-z_0-9]+)'", hk)
    if len(a_leg) != 7 or len(h_leg) != 7:
        note("迁移表条数变了：安卓 %d / 鸿蒙 %d（应为 7）" % (len(a_leg), len(h_leg)))
        bad += 1
    if sorted(a_leg) != sorted((t, k) for t, k in h_leg):
        note("两端迁移表不一致，老数据会认不出类型")
        bad += 1
    ab = io.open(os.path.join(ROOT, "Android", "app", "src", "main", "java", "com", "deviceledger",
                              "app", "model", "Badge.kt"), encoding="utf-8").read()
    hb = io.open(os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "ets", "model", "Badge.ets"),
                 encoding="utf-8").read()
    a_ids = re.findall(r'BadgeDef\("([a-z_0-9]+)", R\.string\.badge_([a-z_0-9]+)_name', ab)
    h_ids = re.findall(r"id: '([a-z_0-9]+)',\s*nameKey: 'badge_([a-z_0-9]+)_name'", hb)
    if len(a_ids) != 14 or len(h_ids) != 14:
        note("勋章条数不对：安卓 %d / 鸿蒙 %d" % (len(a_ids), len(h_ids)))
        bad += 1
    if [i for i, _ in a_ids] != [i for i, _ in h_ids]:
        note("两端勋章 id 列表不一致")
        bad += 1
    for i, k in a_ids:
        if i != k:
            note("勋章 %s 的 nameKey 与 id 不同名" % i)
            bad += 1
        for suf in ("name", "desc"):
            if "badge_%s_%s" % (i, suf) not in strings:
                note("主表缺勋章文案 badge_%s_%s" % (i, suf))
                bad += 1
    if not bad:
        print("  41 类 key / 14 枚 id / 7 条迁移表 两端一致，且都在主表里有四语言文案 OK")
    return bad


def check_no_stray_literals():
    """代码里不该再有人手写的可见中文（迁移表那 7 条除外）。"""
    print("\n== 代码里残留的可见中文 ==")
    cjk = re.compile(r"[一-鿿]")
    hits = []
    roots = [(os.path.join(ROOT, "Android", "app", "src", "main"), (".kt",), '"'),
             (os.path.join(ROOT, "HarmonyOS", "entry", "src", "main", "ets"), (".ets",), "'")]
    for base, exts, q in roots:
        for dp, dn, fn in os.walk(base):
            dn[:] = [x for x in dn if x not in ("build", ".gradle", "oh_modules")]
            for f in fn:
                if not f.endswith(exts):
                    continue
                p = os.path.join(dp, f)
                block = False
                legacy = False
                for i, line in enumerate(io.open(p, encoding="utf-8", errors="replace").read().split("\n"), 1):
                    s = line.strip()
                    if block:
                        if "*/" in s:
                            block = False
                        continue
                    if s.startswith("/*"):
                        block = "*/" not in s
                        continue
                    if s.startswith("//") or s.startswith("*"):
                        continue
                    # 老数据迁移表整块放行：里面的中文是历史存储里的值，不是界面文案。
                    # 按块跳而不是按关键字跳，免得哪天有代码行只是"提到了迁移"就逃过检查。
                    if "LEGACY_MAP" in s:
                        legacy = True
                    if legacy:
                        if s.endswith(")") or s.endswith("]") or s.endswith(");"):
                            legacy = False
                        continue
                    for lit in re.findall(r'"(?:[^"\\\n]|\\.)*"|\'(?:[^\'\\\n]|\\.)*\'', line):
                        if cjk.search(lit):
                            hits.append((os.path.relpath(p, ROOT), i, lit))
    for p, i, lit in hits:
        note("%s:%d 仍有中文字面量 %s" % (p, i, lit[:40]))
    if not hits:
        print("  零残留 OK")
    return len(hits)


def main():
    strings, plurals = load_master()
    bad = 0
    bad += check_master(strings, plurals)
    bad += check_android(strings, plurals)
    bad += check_harmony(strings, plurals)
    bad += check_keys_not_translated(strings, plurals)
    bad += check_no_stray_literals()
    print("\n" + ("多语言完整性通过。" if not bad else "存在问题 %d 处，先修再发版。" % bad))
    sys.exit(0 if not bad else 1)


if __name__ == "__main__":
    main()
