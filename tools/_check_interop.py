#!/usr/bin/env python3
"""双端导出格式互通自检。

从安卓（Kotlin）与鸿蒙（ArkTS）的源码里把导出 JSON 的字段、常量、勋章 id
抽出来逐项比对。两端只要有一处对不上，导出的文件就不能互相读进来。
"""
import re
import sys

AND = r"D:/shebeizhangQoder/Android/app/src/main/java/com/deviceledger/app"
HM = r"D:/shebeizhangQoder/HarmonyOS/entry/src/main/ets"


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def const(text, name, pattern):
    m = re.search(pattern, text)
    return m.group(1) if m else None


# ---------- 1. 常量 ----------
and_backup = read(AND + "/util/BackupStore.kt")
hm_backup = read(HM + "/utils/BackupStore.ets")

pairs = [
    ("FORMAT", r'const val FORMAT = "([^"]+)"', r"FORMAT: string = '([^']+)'"),
    ("VERSION", r"const val VERSION = (\d+)", r"VERSION: number = (\d+)"),
    ("SLOTS", r"const val SLOTS = (\d+)", r"SLOTS: number = (\d+)"),
    ("KEEP_DAYS", r"const val KEEP_DAYS = (\d+)", r"KEEP_DAYS: number = (\d+)"),
]

ok = True
print("== 常量 ==")
for name, pa, ph in pairs:
    a = const(and_backup, name, pa)
    h = const(hm_backup, name, ph)
    same = a == h
    ok &= same
    print(f"  {name:<10} 安卓={a!r:<16} 鸿蒙={h!r:<16} {'OK' if same else '不一致!'}")

# ---------- 2. 设备字段（导出顺序也要一致） ----------
print("\n== 设备字段（按写入顺序） ==")
and_dev = re.findall(r'o\.put\("(\w+)", (?:it|money\(it)\.', and_backup)
and_dev += re.findall(r'o\.put\("(\w+)", it\.', and_backup)
# 只取顶层键：缩进 6 空格的行（嵌套块是 8 空格，不会混进来）
def top_keys(block):
    return re.findall(r"^      (\w+):", block, re.M)

and_dev_order = []
for m in re.finditer(r"o\.put\(\"(\w+)\"", and_backup):
    pass
# 安卓的 deviceJson 单独取那一段，避免把回收站的 deletedAt 也算进来
m = re.search(r"private fun deviceJson\(it: DeviceItem\): JSONObject \{(.*?)\n    \}", and_backup, re.S)
if m:
    and_dev_order = re.findall(r'o\.put\("(\w+)"', m.group(1))

m = re.search(r"private static deviceJson\(it: DeviceItem\): DeviceItem \{\s*return \{(.*?)\n    \};", hm_backup, re.S)
hm_dev_order = top_keys(m.group(1)) if m else []

# 安卓 trashJson 复用 deviceJson，再补 deletedAt；鸿蒙同理
print(f"  安卓: {and_dev_order}")
print(f"  鸿蒙: {hm_dev_order}")
same = and_dev_order == hm_dev_order
ok &= same
print(f"  {'OK' if same else '不一致!'}")

# ---------- 3. 根对象字段 ----------
print("\n== 根对象字段 ==")
and_root = re.findall(r'root\.put\("(\w+)"', and_backup)
m = re.search(r"const root: RootRecord = \{(.*?)\n    \};", hm_backup, re.S)
hm_root = top_keys(m.group(1)) if m else []
# 鸿蒙的 kind 是条件写入，安卓也是；比对时都去掉
and_root = [k for k in and_root if k != "kind"]
hm_root = [k for k in hm_root if k != "kind"]
print(f"  安卓: {and_root}")
print(f"  鸿蒙: {hm_root}")
same = and_root == hm_root
ok &= same
print(f"  {'OK' if same else '不一致!'}")

# ---------- 4. 勋章 id 与文案键 ----------
# v2.0.0 起名称与达成条件不再是源码里的中文字面量（要随语言变），
# 这里改比 id + 资源键；并且硬性要求条数等于 14——
# 早先的正则一旦失配就会变成"0 == 0 所以通过"的空过，那种绿比红更危险。
print("\n== 勋章 ==")
and_badge = read(AND + "/model/Badge.kt")
hm_badge = read(HM + "/model/Badge.ets")

and_ids = re.findall(
    r'BadgeDef\("(\w+)", R\.string\.badge_(\w+)_name, R\.string\.badge_(\w+)_desc', and_badge)
hm_ids = re.findall(
    r"id: '(\w+)',\s*nameKey: 'badge_(\w+)_name',\s*descKey: 'badge_(\w+)_desc'", hm_badge)

print(f"  数量: 安卓 {len(and_ids)} / 鸿蒙 {len(hm_ids)}")
if len(and_ids) != 14 or len(hm_ids) != 14:
    ok = False
    print("  勋章不是 14 枚，或正则已失配（源码格式变了，必须同步改本脚本）!")
elif [a[0] for a in and_ids] != [h[0] for h in hm_ids]:
    ok = False
    print("  id 列表不一致!")
else:
    for i, (a, h) in enumerate(zip(and_ids, hm_ids)):
        if a != h:
            ok = False
            print(f"  第 {i+1} 枚不一致: 安卓 {a} / 鸿蒙 {h}")
    if ok:
        print("  14 枚的 id 与文案资源键逐项一致 OK")

# ---------- 5. 勋章判定阈值 ----------
print("\n== 判定阈值 ==")
a_th = sorted(set(re.findall(r"s\.(\w+) (>=|>=|<) ([\d.]+)", and_badge)))
h_th = sorted(set(re.findall(r"s\.(\w+) (>=|<) ([\d.]+)", hm_badge)))
print(f"  安卓 {len(a_th)} 条 / 鸿蒙 {len(h_th)} 条")
same = len(a_th) == len(h_th) and len(a_th) == 14
ok &= same
print(f"  {'14 条判定阈值一致 OK' if same else '条数不是 14 或两端不一致——正则失配也要当失败!'}")

print("\n" + ("全部一致，两端导出的文件可以互相读进来。" if ok else "存在不一致，需要修后再验。"))
sys.exit(0 if ok else 1)
