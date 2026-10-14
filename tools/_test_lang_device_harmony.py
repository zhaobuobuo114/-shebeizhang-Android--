#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
鸿蒙真机语言回归（Mate 90 Pro 模拟器 / hdc + uitest dumpLayout）。

三条硬规矩，都是上一轮踩出来的：

1. 期望文案一律从 rawfile 文案表里读，脚本里不手写 CJK。
   安卓那轮我就是手抄「システムに従う」把 U+5F93 写成 U+5023，凭空造出一条假 FAIL。
2. 崩溃只认 /data/log/faultlog/faultlogger 里多出来的 jscrash 文件，不认肉眼看到的画面。
   基线是 12:47 的 6 个（Version 字段写着 2.0.0，就是 setAppPreferredLanguage 递归那次）。
3. 主列表上绝不做滑动。卡片带 swipeAction（Index.ets:1659），21:58 有一次设备被划进回收站
   至今没能复现，代价太大；面板内部的 Scroll 滑动实测安全，只在面板里用，且每次滑完都核一遍条数。

设备语言 zh-CN 也不是猜的：系统自己广播的 Ace 配置里写着 "0#ohos.system.language":"zh-CN"，
锁屏下拉的「10月13日星期二」「没有通知」也是中文。
"""

import html
import itertools
import json
import os
import re
import subprocess
import sys
import time

HDC = r"D:\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe"
BUNDLE = "shebeizhang.1.0.1"
ABILITY = "EntryAbility"
ROOT = r"D:\shebeizhangQoder"
TMP = os.path.join(ROOT, "_hmdev", "tmp")
I18N_DIR = os.path.join(ROOT, "HarmonyOS", "entry", "src", "main",
                        "resources", "rawfile", "i18n")
PREFS = "/data/app/el2/100/base/%s/haps/entry/preferences/device_ledger_store" % BUNDLE
FAULT = "/data/log/faultlog/faultlogger"
DEV_LANG = "zh-CN"
# 10-13 21:58:31 我那两条 uitest 滑动把这台设备划进了回收站（deletedAt 就是证据）。
# 之后作者自己又动过数据（00:02 删了「许诺」），所以只回滚我这一条，作者那条原样留着。
MY_ACCIDENT_ID = "d17914692424674372"
MY_ACCIDENT_NAME = "123123131"
FILE_OF = {"zh-CN": "zh_CN.json", "zh-TW": "zh_TW.json", "en": "en_US.json", "ja-JP": "ja_JP.json"}
ROW_OF = {"zh-CN": "lang_zh_cn", "zh-TW": "lang_zh_tw", "en": "lang_en", "ja-JP": "lang_ja"}
FOLLOW = "system"
GITHUB_URL = "https://github.com/zhaobuobuo114"
MORE = "⋯"  # ⋯  顶栏那个按钮，跟语言无关

MAIN_KEYS = ["app_name", "top_add", "sum_total_label", "sum_avg_label", "sum_best_label",
             "chart_title", "chart_sub", "chart_by_amount", "chart_by_count",
             "sort_label", "sort_avg_desc", "sort_price_desc"]
PANEL_KEYS = ["data_title", "data_lang_title", "lang_system", "editor_close",
              "data_export_short", "data_import_short", "data_auto_title", "data_snapshot_restore"]
PANEL_BOT = ["kind_other", "data_section_trash", "data_section_badges"]
EDITOR_KEYS = ["editor_title_add", "editor_name_label", "editor_price_label",
               "editor_kind_label", "editor_date_label", "editor_note_label",
               "editor_save", "editor_cancel"]
TRASH_KEYS = ["trash_title", "trash_clear", "trash_restore", "editor_close"]
CONFIRM_KEYS = ["clear_trash_title", "common_cancel", "clear_trash_confirm"]

TAB = {}
ALLKEYS = set()
for _tag, _fn in FILE_OF.items():
    with open(os.path.join(I18N_DIR, _fn), encoding="utf-8") as _f:
        TAB[_tag] = json.load(_f)["strings"]
    ALLKEYS |= set(TAB[_tag].keys())

LOG = []
RESULTS = []
BASE = set()
SNAP0 = {}
ASFOUND = {}
RESTORED = False
cur = "ja-JP"


def out(s):
    LOG.append(s)
    print(s.encode("ascii", "replace").decode("ascii"))


def show(s):
    return str(s).encode("unicode_escape").decode("ascii")


def ck(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    out("%-4s %s%s" % ("PASS" if ok else "FAIL", name,
                       ("  <- " + detail) if (detail and not ok) else ""))
    return bool(ok)


# ---------------------------------------------------------------- hdc

def run(args, timeout=120):
    p = subprocess.run([HDC] + args, capture_output=True, timeout=timeout)
    return (p.stdout.decode("utf-8", "replace") + p.stderr.decode("utf-8", "replace")).replace("\r", "")


def sh(cmd, timeout=120):
    return run(["shell", cmd], timeout)


_seq = itertools.count()


def dump():
    last = ""
    for _ in range(5):
        name = "dl_%d_%05d.json" % (os.getpid(), next(_seq))
        remote = "/data/local/tmp/" + name
        last = sh("uitest dumpLayout -p %s" % remote)
        if "saved to" not in last:
            time.sleep(0.6)
            continue
        run(["file", "recv", remote, name])
        sh("rm -f %s" % remote)
        path = os.path.join(TMP, name)
        if not os.path.exists(path):
            time.sleep(0.6)
            continue
        try:
            with open(path, encoding="utf-8") as f:
                raw = json.load(f)
            os.remove(path)
        except Exception:
            time.sleep(0.6)
            continue
        return build(raw)
    raise RuntimeError("dumpLayout failed: " + show(last))


def build(n, parent=None):
    a = n.get("attributes", {})
    m = re.findall(r"-?\d+", a.get("bounds", ""))
    node = {"t": a.get("type", ""), "x": a.get("text", ""), "d": a.get("description", ""),
            "clk": a.get("clickable") == "true", "bg": a.get("backgroundColor", ""),
            "b": (int(m[0]), int(m[1]), int(m[2]), int(m[3])) if len(m) >= 4 else None,
            "p": parent}
    node["k"] = [build(c, node) for c in (n.get("children") or [])]
    return node


def flat(root):
    res = [root]
    for c in root["k"]:
        res.extend(flat(c))
    return res


def texts(root):
    return set(n["x"].strip() for n in flat(root) if n["x"].strip())


def center(n):
    return (n["b"][0] + n["b"][2]) // 2, (n["b"][1] + n["b"][3]) // 2


def by_text(root, want):
    return [n for n in flat(root) if n["x"].strip() == want and n["b"]]


def wait_until(pred, timeout=10.0, every=0.5):
    end = time.time() + timeout
    root = None
    while time.time() < end:
        root = dump()
        try:
            if pred(root):
                return root
        except Exception:
            pass
        time.sleep(every)
    return root


def tap(node):
    x, y = center(node)
    sh("uitest uiInput click %d %d" % (x, y))


def back():
    sh("uitest uiInput keyEvent Back")


def foreground():
    o = sh("aa dump -a")
    for blk in o.split("Mission ID")[1:]:
        if ("bundle name [%s]" % BUNDLE) in blk and "state #FOREGROUND" in blk:
            return True
    return False


def crashes():
    # 设备掉线时 ls 只会回一行错误，集合是空的——那会让"没有新崩溃"白送一个 PASS。
    # 所以先确认这个目录真的列出来了，列不出来就直接炸，别让断言悄悄变成真。
    o = sh("ls -d %s 2>/dev/null" % FAULT)
    if FAULT not in o:
        raise RuntimeError("faultlogger not readable: " + show(o.strip()[:120]))
    lst = sh("ls %s 2>/dev/null" % FAULT)
    return set(x for x in re.findall(r"\S+\.log", lst) if x.startswith("jscrash-") and BUNDLE in x)


def target_ready():
    v = run(["list", "targets", "-v"])
    return "Connected" in v


def store():
    run(["file", "recv", PREFS, "store_snap.xml"])
    path = os.path.join(TMP, "store_snap.xml")
    with open(path, encoding="utf-8") as f:
        s = f.read()
    os.remove(path)

    def field(key):
        m = re.search(r'key="%s">(.*?)</string>' % key, s)
        return html.unescape(m.group(1)) if m else None

    def flag(key):
        m = re.search(r'key="%s" value="([^"]*)"' % key, s)
        return m.group(1) if m else None

    devs = json.loads(field("device_list") or "[]")
    trash = json.loads(field("trash_list") or "[]")
    return {"n": len(devs), "ids": sorted(d.get("id") for d in devs),
            "order": [(d.get("id"), d.get("favorite")) for d in devs],
            "names": sorted(d.get("name") for d in devs),
            "fav": [d.get("favorite") for d in devs],
            "trash": len(trash), "trash_ids": sorted(t.get("item", {}).get("id") for t in trash),
            "lang": field("app_language"), "dark": flag("dark_mode")}


def shot(tag):
    remote = "/data/local/tmp/hm_%s.png" % tag
    sh("uitest screenCap -p %s" % remote)
    run(["file", "recv", remote, "shot_%s.png" % tag])
    sh("rm -f %s" % remote)
    return os.path.join(TMP, "shot_%s.png" % tag)


# ---------------------------------------------------------------- 断言

def leak_raw_keys(root):
    """t() 取不到键会把键名原样画到屏幕上，出现 'app_name' 这类词就是缺词条。"""
    return sorted(t for t in texts(root) if t in ALLKEYS and len(t) > 3)


def want_present(ts, want):
    """带 {0} 的模板按前缀认；其余要求整串相等（截断单独报，不算失败）。"""
    if "{0}" in want:
        pre = want.split("{0}")[0]
        return any(t == want or (pre and t.startswith(pre) and len(pre) >= 3) for t in ts)
    return want in ts


def check_lang(root, tag, keys, phase):
    ts = texts(root)
    miss, stale, trunc, leaks = [], [], [], []
    for k in keys:
        want = TAB[tag].get(k)
        if want is None:
            miss.append(k + "(缺键)")
            continue
        if not want_present(ts, want):
            (trunc if any(want.startswith(t) and len(t) >= 5 for t in ts) else miss).append(k)
    for other in FILE_OF:
        if other == tag:
            continue
        for k in keys:
            a, b = TAB[tag].get(k), TAB[other].get(k)
            if a is None or b is None or a == b:
                continue
            if "{0}" in a or "{0}" in b:
                continue
            if b in ts:
                stale.append("%s=%s" % (k, other))
    leaks = leak_raw_keys(root)
    detail = "missing=%s stale=%s leak=%s" % (",".join(miss), ",".join(stale), ",".join(leaks))
    ok = ck("%s [%s]" % (phase, tag), not miss and not stale and not leaks, detail)
    if trunc:
        out("     ellipsized (tolerated): %s" % ",".join(trunc))
    return ok


def panel_open(root, cur):
    return TAB[cur]["data_title"] in texts(root)


def open_panel(cur):
    root = dump()
    if panel_open(root, cur):
        return root
    hits = by_text(root, MORE)
    if not hits:
        return None
    tap(hits[0])
    root = wait_until(lambda r: panel_open(r, cur), 12)
    return root if panel_open(root, cur) else None


def scroll_panel_to(cur, label, tries=4):
    """面板里那一屏放不下回收站/勋章两行，得在 Scroll 内部往上推。
    只允许在面板开着的时候滑：主列表上的滑动实测会把系统通知栏拉下来，
    21:58 那次设备被划进回收站就发生在这种手势里（详见 _存疑清单.md 第八节）。"""
    if not panel_open(dump(), cur):
        return None
    for _ in range(tries):
        root = dump()
        if by_text(root, label):
            return root
        sh("uitest uiInput swipe 640 2000 640 1300 900")
        time.sleep(1.0)
    root = dump()
    return root if by_text(root, label) else None


def pick_language(cur, tag):
    """tag 为四种语言之一或 'system'。返回 (切完后的 root, 错误说明, 新语言)。"""
    root = open_panel(cur)
    if root is None:
        return None, "panel did not open", cur
    row_key = "lang_system" if tag == FOLLOW else ROW_OF[tag]
    label = TAB[cur][row_key]
    hits = by_text(root, label)
    if not hits:
        return None, "row not found: " + show(label), cur
    tap(hits[0])
    new = DEV_LANG if tag == FOLLOW else tag
    # 切完语言面板必须关掉、直接露出主页面（这一版起的新行为，两端一致）；
    # 点的正好是当前那门语言时 pickLanguage 会提前返回，面板留着是正确行为
    same = tag == cur
    root = wait_until(lambda r: TAB[new]["app_name"] in texts(r)
                      and (same or not panel_open(r, new)), 12)
    return root, "", new


def check_tick(root, cur, tag):
    """✓ 只该落在选中的那一行上。"""
    rows = {}
    for t in FILE_OF:
        rows[TAB[t][ROW_OF[t]]] = t
    rows[TAB[cur]["lang_system"]] = FOLLOW
    ticks = [n for n in flat(root) if n["x"].strip() == "✓" and n["b"]]
    if len(ticks) != 1:
        return False, "tick count %d" % len(ticks)
    ty = (ticks[0]["b"][1] + ticks[0]["b"][3]) // 2
    best, bestd = None, 1 << 30
    for label, t in rows.items():
        for n in by_text(root, label):
            d = abs((n["b"][1] + n["b"][3]) // 2 - ty)
            if d < bestd:
                best, bestd = t, d
    return (best == tag and bestd < 130), "tick on %s dist %d" % (best, bestd)


def restart():
    sh("aa force-stop %s" % BUNDLE)
    time.sleep(1.5)
    sh("aa start -b %s -a %s" % (BUNDLE, ABILITY))
    return wait_until(lambda r: len(texts(r)) > 10, 25)


def guard(name, before):
    st = store()
    return ck("%s: data unchanged" % name,
              st["n"] == before["n"] and st["trash"] == before["trash"] and st["ids"] == before["ids"],
              "n=%d trash=%d (was %d/%d)" % (st["n"], st["trash"], before["n"], before["trash"]))


# ---------------------------------------------------------------- 阶段

def p1_cold_start():
    root = restart()
    check_lang(root, cur, MAIN_KEYS, "P1 cold start renders stored language")
    check_currency(root, cur, "P1")
    check_footer_scrolled(cur, "P1")
    ck("P1 foreground", foreground())
    ck("P1 no new jscrash", crashes() == BASE, show(sorted(crashes() - BASE)))
    return cur


MONEY_RE = re.compile(r"^(NT\$|\u00a5|\$)[\d,.]")


def currencies(root):
    """屏幕上出现在金额开头的货币符号集合。NT$ 要先试，否则会被 '$' 抢先匹配。"""
    found = set()
    for t in texts(root):
        m = MONEY_RE.match(t)
        if m:
            found.add(m.group(1))
    return found


def check_currency(root, tag, phase):
    want = TAB[tag]["fmt_currency"]
    got = currencies(root)
    others = set(TAB[t]["fmt_currency"] for t in FILE_OF) - {want}
    ok = want in got and not (got & (others - {want}))
    ck("%s currency=%s" % (phase, want), ok, "seen=%s" % show(sorted(got)))


def check_footer(root, phase):
    hit = [t for t in texts(root) if GITHUB_URL in t]
    ck("%s github footer present" % phase, len(hit) >= 1, "no node contains the URL")


def check_footer_scrolled(cur, phase):
    """页脚是 List 的最后一项，ArkUI 的 List 是懒加载的：不滚进视口就不会出现在文本树里。
    滑动只从列表中部起手（从状态栏起手会把通知栏拉下来，21:58 那次误删就是这么来的），
    并且滑完立刻核对条数与 id —— 这条护栏比断言本身更重要。"""
    before = store()
    found = None
    for _ in range(6):
        root = dump()
        hit = [t for t in texts(root) if GITHUB_URL in t]
        if hit:
            found = hit[0]
            break
        sh("uitest uiInput swipe 640 2200 640 700 1200")
        time.sleep(0.9)
    st = store()
    ck("%s: scrolling the list touched no data" % phase,
       st["n"] == before["n"] and st["trash"] == before["trash"] and st["ids"] == before["ids"],
       "n=%d trash=%d (was %d/%d)" % (st["n"], st["trash"], before["n"], before["trash"]))
    want = TAB[cur]["footer_github"].replace("{0}", GITHUB_URL)
    ck("%s github footer present" % phase, found == want,
       "seen=%s" % show(found if found else "never came into view"))
    # 滚回顶部，否则后面的阶段看不到设备卡
    wait_until(lambda r: TAB[cur]["sum_total_label"] in texts(r), 12)
    for _ in range(6):
        root = dump()
        if TAB[cur]["sum_total_label"] in texts(root):
            break
        sh("uitest uiInput swipe 640 700 640 2200 1200")
        time.sleep(0.9)


def p2_cycle(cur):
    for tag in ["zh-CN", "zh-TW", "en", "ja-JP"]:
        before = store()
        root, err, new = pick_language(cur, tag)
        if root is None:
            ck("P2 switch -> %s" % tag, False, err)
            continue
        ck("P2 switch -> %s" % tag, new == tag)
        ck("P2 %s: panel closed, main page shown" % tag, not panel_open(root, tag))
        check_lang(root, tag, MAIN_KEYS, "P2 %s main page" % tag)
        check_currency(root, tag, "P2 %s" % tag)
        check_footer_scrolled(tag, "P2 %s" % tag)
        root = open_panel(tag)
        if root is None:
            ck("P2 %s: panel opens on demand" % tag, False, "tap did not open the panel")
            cur = tag
            continue
        ck("P2 %s: panel opens on demand" % tag, True)
        check_lang(root, tag, PANEL_KEYS, "P2 %s data panel" % tag)
        root2 = scroll_panel_to(tag, TAB[tag]["data_section_badges"])
        if root2 is not None:
            check_lang(root2, tag, PANEL_BOT, "P2 %s panel rows below the fold" % tag)
            back()
        else:
            ck("P2 %s: could not scroll to badge row" % tag, False, "row never appeared")
        root = open_panel(tag)
        if root is None:
            ck("P2 %s: tick on its own row" % tag, False, "panel did not reopen")
        else:
            t, d = check_tick(root, tag, tag)
            ck("P2 %s: tick on its own row" % tag, t, d)
            back()
        ck("P2 %s: foreground" % tag, foreground())
        ck("P2 %s: no new jscrash" % tag, crashes() == BASE, show(sorted(crashes() - BASE)))
        st = store()
        ck("P2 %s: pref persisted" % tag, st["lang"] == tag, "store=%s" % st["lang"])
        guard("P2 " + tag, before)
        cur = tag
    return cur


def p3_follow_system(cur):
    root, err, new = pick_language(cur, FOLLOW)
    if root is None:
        ck("P3 follow system", False, err)
        return cur
    check_lang(root, DEV_LANG, MAIN_KEYS, "P3 %s -> system" % cur)
    check_currency(root, DEV_LANG, "P3 %s -> system" % cur)
    ck("P3 %s -> system: panel closed" % cur, not panel_open(root, DEV_LANG))
    root = open_panel(DEV_LANG)
    if root is None:
        ck("P3 panel after system switch", False, "panel did not open")
    else:
        check_lang(root, DEV_LANG, PANEL_KEYS, "P3 %s -> system panel" % cur)
        t, d = check_tick(root, DEV_LANG, FOLLOW)
        ck("P3 tick on Match system", t, d)
        back()

    _, err, cur2 = pick_language(DEV_LANG, "en")
    if cur2 != "en":
        ck("P3 setup en", False, err)
    root, err, new = pick_language("en", FOLLOW)
    if root is None:
        ck("P3 en -> system", False, err)
    else:
        check_lang(root, DEV_LANG, MAIN_KEYS, "P3 en -> system (must not stick at en)")
        check_currency(root, DEV_LANG, "P3 en -> system")
        root = open_panel(DEV_LANG)
        if root is not None:
            check_lang(root, DEV_LANG, PANEL_KEYS, "P3 en -> system panel")
            back()
    st = store()
    ck("P3 pref = system", st["lang"] == FOLLOW, "store=%s" % st["lang"])

    root = restart()
    check_lang(root, DEV_LANG, MAIN_KEYS, "P3 cold start keeps system language")
    ck("P3 foreground after restarts", foreground())
    ck("P3 no new jscrash", crashes() == BASE, show(sorted(crashes() - BASE)))
    return DEV_LANG


def canvas_box(root):
    cv = [n for n in flat(root) if n["t"] == "Canvas" and n["b"]]
    return cv[0]["b"] if cv else None


def px_diff(path_a, box_a, path_b, box_b):
    from PIL import Image, ImageChops
    ia = Image.open(path_a).convert("RGB").crop(box_a)
    ib = Image.open(path_b).convert("RGB").crop(box_b)
    diff = ImageChops.difference(ia, ib).convert("L")
    # histogram() 而不是 getdata()：Pillow 14 要删 getdata，而且 256 个桶比逐像素快两个数量级
    return sum(diff.histogram()[25:]), ia, ib


def p4_canvas(cur):
    """圆心的三处文案是 Canvas 画上去的，文本树里看不见，只能比像素。
    先拍两张同语言的当噪声下限，再拿跨语言的差值去比——不然动画抖一下就能骗过这条断言。"""
    root = dump()
    box = canvas_box(root)
    if box is None:
        ck("P4 donut canvas", False, "no Canvas node on screen")
        return cur
    a1 = shot("c1_%s" % cur)
    a2 = shot("c2_%s" % cur)
    noise, _, _ = px_diff(a1, box, a2, box)
    other = "en" if cur != "en" else "zh-CN"
    root, err, new = pick_language(cur, other)
    if root is None:
        ck("P4 donut canvas", False, err)
        return cur
    # 切完面板已经自己关掉了，这里不能再按 Back —— 没有可关的东西时 Back 会把应用退到后台
    time.sleep(2.0)
    root = dump()
    box2 = canvas_box(root)
    if box2 is None:
        ck("P4 donut canvas", False, "canvas gone after switch")
        return new
    b = shot("c_%s" % new)
    changed, ia, ib = px_diff(a1, box, b, box2)
    ia.save(os.path.join(TMP, "crop_%s.png" % cur))
    ib.save(os.path.join(TMP, "crop_%s.png" % new))
    ck("P4 donut center redraws on language switch", changed > max(500, noise * 5),
       "changed %d px vs same-language noise %d px (crop %d px)" % (changed, noise, ia.size[0] * ia.size[1]))
    return new


def find_star(root, name):
    hits = by_text(root, name)
    if not hits:
        return None
    li = hits[0]
    while li["p"] is not None and li["t"] != "ListItem":
        li = li["p"]
    if li["t"] != "ListItem":
        return None
    mid_x = (li["b"][0] + li["b"][2]) // 2
    cands = []
    for n in flat(li):
        if n is li or not n["clk"] or not n["b"]:
            continue
        w, h = n["b"][2] - n["b"][0], n["b"][3] - n["b"][1]
        if 40 <= w <= 220 and 40 <= h <= 220 and (n["b"][0] + n["b"][2]) // 2 > mid_x:
            cands.append(n)
    return cands[-1] if cands else None


def fav_chip(root, cur):
    pref = TAB[cur]["sort_fav"]
    for t in sorted(texts(root)):
        if t.startswith(pref):
            m = re.search(r"(\d+)\s*$", t)
            if m:
                return int(m.group(1))
    return None


def fav_pairs(st):
    return sorted(st["order"])


def p5_favorite(cur):
    before = store()
    root = dump()
    star, used = None, None
    for name in sorted(before["names"], key=len, reverse=True):
        star = find_star(root, name)
        if star is not None:
            used = name
            break
    if star is None:
        ck("P5 favorite star", False, "no star button found on a visible card")
        return
    c0 = fav_chip(root, cur)
    if c0 is None:
        ck("P5 favorite star", False, "no fav chip on screen")
        return
    tap(star)
    mid = wait_until(lambda r: fav_chip(r, cur) not in (None, c0), 10)
    c1 = fav_chip(mid, cur)
    ck("P5 star tap moves the chip count", c1 in (c0 + 1, c0 - 1), "chip %d -> %s" % (c0, c1))
    st = store()
    ck("P5 chip count matches the store", st["fav"].count(True) == c1,
       "store=%d chip=%s" % (st["fav"].count(True), c1))
    root = dump()
    again = find_star(root, used)
    if again is None:
        ck("P5 second tap", False, "star of %s gone after the first tap" % show(used))
        return
    tap(again)
    back_root = wait_until(lambda r: fav_chip(r, cur) == c0, 10)
    ck("P5 second tap restores the count", fav_chip(back_root, cur) == c0,
       "chip=%s" % fav_chip(back_root, cur))
    ck("P5 store back to original favourites", fav_pairs(store()) == fav_pairs(before))
    ck("P5 no new jscrash", crashes() == BASE, show(sorted(crashes() - BASE)))


def p6_editor(cur):
    before = store()
    root = dump()
    add = by_text(root, TAB[cur]["top_add"])
    if not add:
        ck("P6 editor", False, "add button not found")
        return
    tap(add[0])
    root = wait_until(lambda r: TAB[cur]["editor_title_add"] in texts(r), 12)
    check_lang(root, cur, EDITOR_KEYS, "P6 editor screen")
    back()
    wait_until(lambda r: TAB[cur]["editor_title_add"] not in texts(r), 10)
    st = store()
    ck("P6 cancel adds no device", st["n"] == before["n"] and st["ids"] == before["ids"],
       "n=%d" % st["n"])


def sheets_open(root):
    ts = texts(root)
    for t in FILE_OF:
        for k in ("data_title", "trash_title", "editor_title_add"):
            if TAB[t][k] in ts:
                return True
    return False


def close_all():
    """回收站/勋章墙退回上一层会重新打开数据面板（Index.ets:274），所以要连按几次 Back。"""
    root = dump()
    for _ in range(4):
        if not sheets_open(root):
            return root
        back()
        time.sleep(0.8)
        root = dump()
    return root


def restore_button_for(root, name):
    labels = set(TAB[t]["trash_restore"] for t in FILE_OF)
    hits = by_text(root, name)
    if not hits:
        return None
    n = hits[0]
    while n["p"] is not None:
        n = n["p"]
        btns = [m for m in flat(n) if m["x"].strip() in labels and m["b"]]
        if len(btns) == 1:
            return btns[0]
        if len(btns) > 1:
            return None
    return None


def p7_trash(cur):
    global RESTORED
    root = open_panel(cur)
    if root is None:
        ck("P7 trash sheet", False, "panel did not open")
        return
    row = TAB[cur]["data_section_trash"]
    root = scroll_panel_to(cur, row)
    if root is None:
        ck("P7 trash sheet", False, "trash row never became visible")
        close_all()
        return
    check_lang(root, cur, PANEL_BOT, "P7 panel rows below the fold")
    tap(by_text(root, row)[0])
    root = wait_until(lambda r: TAB[cur]["trash_title"] in texts(r)
                      and TAB[cur]["trash_clear"] in texts(r), 12)
    check_lang(root, cur, TRASH_KEYS, "P7 trash sheet")
    st = store()
    if st["trash"] == 0:
        out("     trash empty: confirmation dialog not exercised")
        close_all()
        return
    clear = by_text(root, TAB[cur]["trash_clear"])
    if not clear:
        ck("P7 clear button", False, "not found")
        close_all()
        return
    tap(clear[0])
    root = wait_until(lambda r: TAB[cur]["clear_trash_confirm"] in texts(r), 12)
    check_lang(root, cur, CONFIRM_KEYS, "P7 clear confirmation")
    ck("P7 dialog alone clears nothing", store()["trash"] == st["trash"])
    cancel = by_text(root, TAB[cur]["common_cancel"])
    if not cancel:
        ck("P7 cancel button", False, "not found")
        close_all()
        return
    tap(cancel[0])
    wait_until(lambda r: TAB[cur]["clear_trash_confirm"] not in texts(r), 10)
    ck("P7 cancel keeps the items", store()["trash"] == st["trash"])

    root = dump()
    if MY_ACCIDENT_ID not in st["trash_ids"]:
        out("     my accidental item is no longer in the trash (author handled it): restore step skipped")
        close_all()
        return
    rest = restore_button_for(root, MY_ACCIDENT_NAME)
    if rest is None:
        ck("P7 restore my accidental delete", False, "restore button for that row not found")
        close_all()
        return
    tap(rest)
    root = wait_until(lambda r: MY_ACCIDENT_NAME not in texts(r), 12)
    st2 = store()
    ck("P7 restore puts that one device back",
       MY_ACCIDENT_ID in st2["ids"] and MY_ACCIDENT_ID not in st2["trash_ids"],
       "n=%d trash=%d" % (st2["n"], st2["trash"]))
    ck("P7 the author's own trashed item is left alone",
       st2["trash"] == st["trash"] - 1 and set(st2["trash_ids"]) == set(st["trash_ids"]) - {MY_ACCIDENT_ID},
       "trash %d -> %d" % (st["trash"], st2["trash"]))
    RESTORED = True
    close_all()


def theme_button(root):
    """顶栏里 ⋯ 左边那个圆形按钮。写死坐标错过一次：英文下标题换行会把整条顶栏推歪，
    所以从 ⋯ 往上找父容器，取它左边那个可点的兄弟。"""
    hits = by_text(root, MORE)
    if not hits:
        return None
    st = hits[0]
    while st["p"] is not None and not st["clk"]:
        st = st["p"]
    p = st["p"]
    if p is None:
        return None
    left = [c for c in p["k"] if c is not st and c["clk"] and c["b"] and c["b"][2] <= st["b"][0] + 4]
    return max(left, key=lambda n: n["b"][2]) if left else None


def page_pixel(tag):
    """主题色只能看像素：页面底色是 linearGradient，文本树里所有节点的 backgroundColor 都是 #00000000。
    取左上角那条页边（x=20）当采样点，它不在任何卡片上。"""
    from PIL import Image
    p = shot("theme_%s" % tag)
    im = Image.open(p).convert("RGB")
    return im.getpixel((20, 1200)), im.getpixel((20, 2700))


def p8_theme(cur):
    root = close_all()
    sw = theme_button(root)
    if sw is None:
        ck("P8 theme", False, "theme button not found")
        return
    a = page_pixel("a")
    tap(sw)
    time.sleep(1.5)
    b = page_pixel("b")
    tap(theme_button(dump()) or sw)
    time.sleep(1.5)
    c = page_pixel("c")
    ck("P8 theme toggle repaints the page", a != b, "%s -> %s" % (show(a), show(b)))
    ck("P8 theme restored", c == a, "%s vs %s" % (show(c), show(a)))
    st = store()
    ck("P8 dark_mode flag back to as-found", st["dark"] == SNAP0["dark"], "%s" % st["dark"])


def p9_final(cur):
    """收尾：语言与主题回到开测前，数据只多回我那一条误删，作者自己删的东西一律不动。"""
    want_lang = SNAP0["lang"]
    root, err, new = pick_language(cur, want_lang)
    close_all()
    root = restart()
    shown = DEV_LANG if want_lang == FOLLOW else want_lang
    check_lang(root, shown, MAIN_KEYS, "P9 back to the as-found language")
    st = store()
    back_ids = ASFOUND["ids"] + ([MY_ACCIDENT_ID] if RESTORED else [])
    back_trash = ASFOUND["trash"] - (1 if RESTORED else 0)
    ck("P9 device list = as-found + my undone delete",
       st["n"] == len(set(back_ids)) and st["ids"] == sorted(set(back_ids)),
       "n=%d want %d" % (st["n"], len(set(back_ids))))
    ck("P9 trash = as-found minus my undone delete", st["trash"] == back_trash,
       "trash=%d want %d" % (st["trash"], back_trash))
    ck("P9 language pref back to %s" % want_lang, st["lang"] == want_lang, "%s" % st["lang"])
    ck("P9 dark_mode unchanged", st["dark"] == ASFOUND["dark"], "%s" % st["dark"])
    ck("P9 no jscrash in the whole run", crashes() == BASE, show(sorted(crashes() - BASE)))
    ck("P9 foreground", foreground())


PHASES = [("1", p1_cold_start), ("2", p2_cycle), ("3", p3_follow_system), ("4", p4_canvas),
          ("5", p5_favorite), ("6", p6_editor), ("7", p7_trash), ("8", p8_theme), ("9", p9_final)]


def main():
    global BASE, SNAP0, ASFOUND, RESTORED, cur
    only = sys.argv[1].split(",") if len(sys.argv) > 1 else None
    os.makedirs(TMP, exist_ok=True)
    os.chdir(TMP)
    t0 = time.time()
    out("targets: " + show(run(["list", "targets"]).strip()))
    if not target_ready():
        out("ABORT: no Connected hdc target (emulator offline?)")
        return 2
    BASE = crashes()
    out("jscrash baseline: %d file(s)" % len(BASE))
    sh("aa start -b %s -a %s" % (BUNDLE, ABILITY))
    time.sleep(3)
    SNAP0 = store()
    ASFOUND = json.loads(json.dumps(SNAP0))
    RESTORED = False
    out("as-found state: n=%d trash=%d trash_ids=%s lang=%s dark=%s"
        % (SNAP0["n"], SNAP0["trash"], show(SNAP0["trash_ids"]), SNAP0["lang"], SNAP0["dark"]))
    cur = DEV_LANG if SNAP0["lang"] in (FOLLOW, None) else SNAP0["lang"]
    for pid, fn in PHASES:
        if only and pid not in only:
            continue
        out("")
        out("---- phase %s ----" % pid)
        try:
            r = fn() if fn is p1_cold_start else fn(cur)
            if isinstance(r, str):
                cur = r
        except Exception as e:
            ck("phase %s raised" % pid, False, show("%s: %s" % (type(e).__name__, e)))
            try:
                close_all()
            except Exception:
                pass
    if only and "9" not in only:
        # 单跑某几个阶段时也要把语言放回去：P4 这类阶段切完是不回头的，
        # 我这么漏过一次，设备留在 en 上了。
        out("")
        out("---- restoring language (phase 9 was not selected) ----")
        try:
            p9_final(cur)
        except Exception as e:
            ck("restore raised", False, show("%s: %s" % (type(e).__name__, e)))
    passed = sum(1 for r in RESULTS if r[1] is True)
    failed = sum(1 for r in RESULTS if r[1] is False)
    out("")
    out("==== %d passed, %d failed in %.0fs ====" % (passed, failed, time.time() - t0))
    for n, ok, d in RESULTS:
        if ok is False:
            out("  FAIL " + n + ("  <- " + d if d else ""))
    with open(os.path.join(ROOT, "_hmdev", "harmony_report.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(LOG) + "\n")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
