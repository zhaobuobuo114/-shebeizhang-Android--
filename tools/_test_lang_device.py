"""设备语言解析的真机（模拟器）回归 —— 需要一台连着 adb 的安卓设备。

    python _test_lang_device.py            # 默认假定设备语言是 en-US

为什么要有这个文件：`L10n.apply()` 里"跟随系统该读哪份配置"这件事，JVM 单测测不到
（它要的是运行时的系统配置），而静态检查也看不出 `Resources.getSystem()` 在 33 以上
已经被 per-app locale 进程级覆盖过——那一版"看起来对"的改法就是被这台模拟器打回的。

三条纪律都写死在下面：
1. 期望文案一律从 `res/values-*/strings.xml` 现读，不手抄（手抄的 システムに倣う 与
   资源里的 システムに従う 只差一个码点，红了只会误判成代码错）；
2. 每次 dump 写唯一文件名（复用 /sdcard/ui.xml 会读到上一次会话的旧界面，
   所有断言都会对着昨天的屏幕"通过"）；
3. 控制台只输出 ASCII（Windows 的 GBK 终端会把中日韩字符打成乱码，连 '-' 与 '_' 都分不清）。
"""
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "Android", "app", "src", "main", "res")
PKG = "com.deviceledger.app"
ACT = PKG + ".MainActivity"
DIR = {"en": "values-en", "ja": "values-ja", "zh-TW": "values-zh-rTW", "zh-CN": "values"}
_RANGES = [(0x3040, 0x30FF), (0x3400, 0x4DBF), (0x4E00, 0x9FFF), (0xF900, 0xFAFF)]
CJK_RUN = re.compile("[" + "".join(chr(a) + "-" + chr(b) for a, b in _RANGES) + "]")


def adb_path():
    env = os.environ.get("ADB")
    if env:
        return env
    for base in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), r"D:\aAndroidsdk"):
        if not base:
            continue
        cand = os.path.join(base, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
        if os.path.exists(cand):
            return cand
    return "adb"


_seq = [0]
_cache = {}


def S(lang, key):
    """一条字符串资源，按打包进 APK 的原样取。"""
    path = DIR[lang] + "/strings.xml"
    if path not in _cache:
        x = open(os.path.join(RES, path), encoding="utf-8").read()
        _cache[path] = {k: v.replace("\\'", "'").replace("&amp;", "&").replace("&lt;", "<")
                        for k, v in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', x, re.S)}
    v = _cache[path].get(key)
    if v is None:
        raise KeyError("%s/%s" % (lang, key))
    return v


def sh(args):
    # Git Bash 会把 "/sdcard/x" 改写成 "C:/Program Files/Git/sdcard/x" 再交给 adb.exe，
    # 于是设备侧的删除与落盘全都静默地没打到目标。
    env = dict(os.environ, MSYS2_ARG_CONV_EXCL="*", MSYS_NO_PATHCONV="1")
    r = subprocess.run([adb_path()] + args, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env=env)
    return r.stdout, r.stderr, r.returncode


def esc(s):
    return s.encode("unicode_escape").decode("ascii")


def nodes(xml):
    out = []
    for m in re.finditer(r"<node\b[^>]*>", xml):
        tag = m.group(0)

        def a(name):
            g = re.search(r'\b%s="([^"]*)"' % name, tag)
            return g.group(1) if g else ""

        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a("bounds"))
        cx = cy = 0
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        out.append((a("text"), a("content-desc"), cx, cy, a("clickable") == "true"))
    return out


def dump(retries=6):
    _seq[0] += 1
    path = "/sdcard/dl_ui_%d_%d.xml" % (int(time.time()), _seq[0])
    out = err = ""
    for _ in range(retries):
        sh(["shell", "rm", "-f", path])
        out, err, _rc = sh(["shell", "uiautomator", "dump", path])
        if "dumped to" in (out + err):
            break
        time.sleep(0.8)
    else:
        raise RuntimeError("uiautomator dump failed: " + (out + err).strip()[:160])
    data, err, rc = sh(["exec-out", "cat", path])
    sh(["shell", "rm", "-f", path])
    if rc != 0 or not data:
        raise RuntimeError("cat failed: " + err[:160])
    if 'package="%s"' % PKG not in data:
        raise RuntimeError("dump 不是本应用的窗口：检查设备是否连着、界面是否在前台")
    return data


def texts(xml):
    return [t for t, _d, _x, _y, _c in nodes(xml) if t]


def find(xml, needle):
    for t, d, cx, cy, _c in nodes(xml):
        if needle in t or needle in d:
            return (t or d, cx, cy)
    return None


def tap(needle, wait=1.0):
    xml = dump()
    hit = find(xml, needle)
    if hit is None:
        raise RuntimeError("找不到节点 %r；屏上：%s" % (esc(needle), [esc(x) for x in texts(xml)][:20]))
    sh(["shell", "input", "tap", str(hit[1]), str(hit[2])])
    time.sleep(wait)


def sheet_langs(xml):
    """开着的「⋯」面板现在是哪门语言（按四种 lang_system 取值认）。"""
    j = " ".join(texts(xml))
    return [lg for lg in DIR if S(lg, "lang_system") in j]


def header_menu(xml):
    """右上角那个「⋯」：标题栏里既无文字也无 contentDescription 的最左一个可点节点。"""
    cands = [(cx, cy) for t, d, cx, cy, c in nodes(xml) if c and not t and not d and cy < 260]
    return min(cands) if cands else None


def open_menu(rep, tag):
    xml = dump()
    spot = header_menu(xml)
    if spot is None:
        rep.check(False, tag + ": 找到右上角「⋯」", " ".join(texts(xml))[:70])
        return False
    sh(["shell", "input", "tap", str(spot[0]), str(spot[1])])
    time.sleep(1.1)
    return True


def close_sheet():
    if sheet_langs(dump()):
        sh(["shell", "input", "keyevent", "4"])
        time.sleep(1.0)


class Rep:
    def __init__(self):
        self.total = 0
        self.bad = 0

    def check(self, ok, note, detail=""):
        self.total += 1
        self.bad += 0 if ok else 1
        print("%s  %s%s" % ("PASS" if ok else "FAIL", esc(note),
                            ("  [" + esc(detail) + "]") if detail else ""))

    def has(self, xml, needle, note):
        self.check(find(xml, needle) is not None, note, "" if find(xml, needle) else "missing")


def main():
    r = Rep()
    loc = sh(["shell", "getprop", "ro.product.locale"])[0].strip()
    if loc.split("-")[0].split("_")[0].lower() != "en":
        print("这台设备的系统语言是 %r，不是英语；本脚本的断言全部以英文设备为前提" % loc)
        return 2
    sh(["shell", "am", "force-stop", PKG])
    sh(["shell", "pm", "clear", PKG])
    time.sleep(1.0)
    sh(["shell", "am", "start", "-n", PKG + "/" + ACT])
    time.sleep(3.0)

    # A 首启：设备英文就必须是英文
    xml = dump()
    r.has(xml, S("en", "privacy_agree"), "A1 首次弹窗是英文而不是 base 的中文")
    cjk = [t for t in texts(xml) if CJK_RUN.search(t)]
    r.check(not cjk, "A2 这一屏没有一个中日韩字符", "; ".join(esc(t) for t in cjk)[:60])
    tap(S("en", "privacy_check"))
    time.sleep(0.5)
    tap(S("en", "privacy_agree"))
    time.sleep(1.6)
    xml = dump()
    r.has(xml, S("en", "empty_all_title"), "A3 主界面是英文")
    cjk = [t for t in texts(xml) if CJK_RUN.search(t)]
    r.check(not cjk, "A4 主界面没有残留中文")

    # B 语言栏
    if not open_menu(r, "B"):
        return finish(r)
    xml = dump()
    r.has(xml, S("en", "lang_system"), "B1 面板里有「跟随系统」")
    for lg in ("ja", "zh-TW", "zh-CN", "en"):
        r.has(xml, S(lg, "lang_" + lg.replace("-", "_").lower()), "B2 面板按自称列出 %s" % lg)

    # C 手动切日文：这一版起切完语言面板是**关掉**的（不再"关掉再重开"），
    #   所以这里断言的是"面板没了 + 主界面已经是日文"，要看面板就按需重开一次。
    tap(S("ja", "lang_ja"))
    time.sleep(1.5)
    xml = dump()
    r.check(not sheet_langs(xml), "C1 切完语言面板自己关掉，露出主页面", str(sheet_langs(xml)))
    j = " ".join(texts(xml))
    r.check(S("ja", "empty_all_title") in j, "C2 主界面已经是日文", j[:70])
    r.check(S("en", "empty_all_title") not in j, "C3 没留英文")
    r.check(S("ja", "fmt_currency") in j, "C3b 金额符号跟着换成 ¥", j[:70])
    if not open_menu(r, "C4"):
        return finish(r)
    xml = dump()
    r.check(sheet_langs(xml) == ["ja"], "C4 面板按需重开后是日文", str(sheet_langs(xml)))
    r.check(marked_is(xml, S("ja", "lang_ja")), "C5 勾在日本語那一行", mark_detail(xml))
    close_sheet()

    # D 切回「跟随系统」——这一条就是 v2.0.1 修的东西
    if not open_menu(r, "D"):
        return finish(r)
    xml = dump()
    r.check(sheet_langs(xml) == ["ja"], "D1 切换前面板仍是日文", str(sheet_langs(xml)))
    tap(S("ja", "lang_system"))
    time.sleep(1.6)
    xml = dump()
    j = " ".join(texts(xml))
    r.check(not sheet_langs(xml), "D2a 面板关掉、直接露出主页面", str(sheet_langs(xml)))
    r.check(S("en", "empty_all_title") in j, "D2 修好了：回到设备语言，没粘在日语", j[:70])
    cjk = [t for t in texts(xml) if CJK_RUN.search(t)]
    r.check(not cjk, "D4 一个日文字符都没留下")
    if not open_menu(r, "D5"):
        return finish(r)
    xml = dump()
    r.check(sheet_langs(xml) == ["en"], "D5 面板按需重开后是设备语言", str(sheet_langs(xml)))
    r.check(marked_is(xml, S("en", "lang_system")), "D6 勾在「跟随系统」那一行", mark_detail(xml))
    close_sheet()

    # E 冷启动
    sh(["shell", "am", "force-stop", PKG])
    time.sleep(1.0)
    sh(["shell", "am", "start", "-n", PKG + "/" + ACT])
    time.sleep(3.0)
    r.check(S("en", "empty_all_title") in " ".join(texts(dump())), "E1 冷启动后跟随系统仍是英文")

    # F 另外两种手动选择照旧生效
    if not open_menu(r, "F"):
        return finish(r)
    tap(S("zh-TW", "lang_zh_tw"))
    time.sleep(1.5)
    close_sheet()
    j = " ".join(texts(dump()))
    r.check(S("zh-TW", "empty_all_title") in j, "F1 繁中生效", j[:70])
    r.check(S("zh-TW", "fmt_currency") in j, "F1b 繁中的金额符号是 NT$", j[:70])
    if not open_menu(r, "F2"):
        return finish(r)
    tap(S("en", "lang_en"))
    time.sleep(1.5)
    close_sheet()
    r.check(S("en", "empty_all_title") in " ".join(texts(dump())), "F2 再切回英文生效")

    # G: 选了一门"其实已经在用"的语言。设备是 en-US，所以
    #    「跟随系统」与「English」的生效语言相同 —— 系统不会投递配置变更，
    #    onConfigurationChanged 不触发；修好之前勾会停在原处，而且那个
    #    "待重开"标志会变成哑弹，下一次任意配置变更时把面板凭空弹出来。
    #    注意：选完语言面板是**留着不动**的（安卓靠重开面板换语言文案），
    #    所以这里不能再 open_menu，直接读当前这一屏。
    if not open_menu(r, "G"):
        return finish(r)
    tap(S("en", "lang_system"), wait=1.8)
    xml = dump()
    r.check(not sheet_langs(xml), "G1 点的正好是当前生效语言：面板照样关掉", str(sheet_langs(xml)))
    if not open_menu(r, "G1b"):
        return finish(r)
    xml = dump()
    langs = sheet_langs(xml)
    r.check(langs == ["en"], "G1c 面板按需重开后仍是英文", str(langs))
    r.check(marked_is(xml, S("en", "lang_system")),
            "G2 勾已经移到「跟随系统」那一行", mark_detail(xml))
    # 再点 English：生效语言依旧没变。旧的"当场重开面板"分支已经删掉，
    # 现在统一是"关掉 + 底下那层界面照旧"，勾的位置由重开面板来确认
    tap(S("en", "lang_en"), wait=1.8)
    if not open_menu(r, "G3b"):
        return finish(r)
    xml = dump()
    r.check(marked_is(xml, S("en", "lang_en")),
            "G3 勾跟着移到 English 那一行（没有停在旧选项上）", mark_detail(xml))
    # 来一次与语言无关的配置变更（深色模式）：哑弹的标志位不该把面板凭空弹出来。
    # 先确认真的关掉了，否则 G4b 无从判断。
    close_sheet()
    pre = " ".join(texts(dump()))
    r.check(S("en", "lang_system") not in pre, "G4a 面板已经关上（否则下一条没有意义）", pre[:60])
    sh(["shell", "cmd", "uimode", "night", "yes"])
    time.sleep(1.8)
    j = " ".join(texts(dump()))
    r.check(S("en", "lang_system") not in j, "G4b 切深色模式没有把面板弹出来", j[:60])
    sh(["shell", "cmd", "uimode", "night", "no"])
    time.sleep(1.6)

    # H: 全程没崩
    out = sh(["shell", "logcat", "-d", "-b", "crash"])[0]
    bad = [l.strip()[:110] for l in out.splitlines() if re.search(r"FATAL|ANR |RangeError|StackOverflow", l)]
    r.check(not bad, "H1 crash 缓冲区里没有 FATAL/ANR", "; ".join(bad[:2]))
    return finish(r)



def node_y(xml, needle):
    """某个文案所在行的 y（用来判断勾打在哪一行上）。"""
    for t, d, cx, cy, c in nodes(xml):
        if t == needle or needle in t:
            return cy
    return None


def sheet_row_labels(xml):
    """屏上出现的语言行标签（四条自称 + 「跟随系统」在当前那门语言里的写法）。
    自称四条在四种语言里都长得一样，所以按 DIR 全扫一遍、去重即可。"""
    j = " ".join(texts(xml))
    out = []
    for lg in DIR:
        for key in ("lang_" + lg.replace("-", "_").lower(), "lang_system"):
            for l2 in DIR:
                lab = S(l2, key)
                if lab in j and lab not in out:
                    out.append(lab)
    return out


def mark_info(xml):
    """哪几行带勾。

    只在**语言行自己所在的 y 附近**数单字符节点：每条语言行行尾都有一个箭头（单字符），
    只有被选中那一行在箭头左边还多一个勾 → 同一 y 上有 ≥2 个单字符。
    原先是"整屏找 ≥2 单字符的行"，结果面板下半部「その他」分节里那两行的两个 '›'
    也被算成第二个勾（C5 就是这么假红的）。
    不按码点认勾是因为 ✕(U+2715) 与 ✓(U+2713) 都是单字符、手抄极易抄错。
    """
    by_y = {}
    for t, d, cx, cy, c in nodes(xml):
        if len(t.strip()) == 1:
            by_y.setdefault(cy, []).append(cx)
    rows, marked = [], []
    for lab in sheet_row_labels(xml):
        y = node_y(xml, lab)
        if y is None:
            rows.append((lab, None))
            continue
        rows.append((lab, y))
        n = sum(len(xs) for yy, xs in by_y.items() if abs(yy - y) <= 8)
        if n >= 2:
            marked.append(lab)
    return marked, rows


def marked_is(xml, label):
    """恰好只有一行带勾，而且就是 `label` 那一行。

    多行带勾（旧勾没清）与零行带勾都算失败。判定只看语言行那几条 y（见 mark_info）。
    """
    marked, _rows = mark_info(xml)
    return len(marked) == 1 and marked[0] == label


def mark_detail(xml):
    """失败时把带勾的行与五条语言行的 y 都打出来，一眼看出差在哪。"""
    marked, rows = mark_info(xml)
    return "marked=%s rows=%s" % (marked, rows)


def finish(r):
    print("\n共 %d 条，失败 %d 条" % (r.total, r.bad))
    return 0 if r.bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
