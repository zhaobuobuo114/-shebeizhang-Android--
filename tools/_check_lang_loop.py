# -*- coding: utf-8 -*-
"""v2.0.1 语言切换防回环的结构性检查。

崩溃根因是回环：
    setPreference -> setAppPreferredLanguage -> onConfigurationUpdate -> setPreference -> ...
修好之后必须保证：配置变更回调在结构上不可能再触达平台 API。

这里把"不可能"写成可机械核对的不变式。注意先剥掉注释再判——
文档里会写"这里绝不能调 setPreference"，不剥注释就会把说明文字当成调用而误报。
"""
import io
import os
import re
import sys

ROOT = 'HarmonyOS/entry/src/main/ets/'


def read(rel):
    return io.open(os.path.join(ROOT, rel), encoding='utf-8').read()


def strip_comments(src):
    """去掉 // 行注释与 /* */ 块注释，保留行号（空行占位）。"""
    out = []
    block = False
    for line in src.split('\n'):
        s = line.strip()
        if block:
            out.append('')
            if '*/' in s:
                block = False
            continue
        if s.startswith('/*'):
            out.append('')
            if '*/' not in s:
                block = True
            continue
        if s.startswith('//'):
            out.append('')
            continue
        # 行尾注释也去掉：判断"是否调用"时不该被说明文字干扰
        cut = re.sub(r'//[^"\']*$', '', line)
        out.append(cut)
    return '\n'.join(out)


def method_body(src, signature):
    """按大括号配平取出方法体。"""
    i = src.index(signature)
    j = src.index('{', i)
    depth = 0
    for k in range(j, len(src)):
        if src[k] == '{':
            depth += 1
        elif src[k] == '}':
            depth -= 1
            if depth == 0:
                return src[j:k + 1]
    return src[j:]


checks = []


def chk(name, ok, detail=''):
    checks.append((name, ok, detail))


# ---- 收集所有 .ets 的去注释文本 ----
sources = {}
for base, _, files in os.walk(ROOT):
    for f in files:
        if f.endswith('.ets'):
            p = os.path.relpath(os.path.join(base, f), ROOT)
            sources[p] = strip_comments(read(p))

# 1. 全工程只允许一处真正调用 setAppPreferredLanguage
CALL = re.compile(r'System\.setAppPreferredLanguage\s*\(')
hits = []
for p, text in sources.items():
    for m in CALL.finditer(text):
        hits.append((p, text[:m.start()].count('\n') + 1))
chk('全工程只有一处真正调用 setAppPreferredLanguage',
    len(hits) == 1 and hits[0][0] == os.path.join('utils', 'I18n.ets'),
    str(hits))

ab = sources[os.path.join('entryability', 'EntryAbility.ets')]
cb = method_body(ab, 'onConfigurationUpdate(config: Configuration): void')
chk('onConfigurationUpdate 方法体内不出现 setPreference', 'setPreference' not in cb)
chk('onConfigurationUpdate 方法体内不出现 setAppPreferredLanguage',
    'setAppPreferredLanguage' not in cb)
chk('onConfigurationUpdate 有未 attach 的守卫', 'isAttached()' in cb)
chk('onConfigurationUpdate 有重入守卫 isApplying()', 'isApplying()' in cb)
chk('onConfigurationUpdate 只走 refreshFromSystem', 'refreshFromSystem()' in cb)

ip = sources[os.path.join('utils', 'I18n.ets')]
sp = method_body(ip, 'static setPreference(tag: string): Lang')
chk('setPreference 在调平台 API 前置 applying=true', 'applying = true' in sp)
chk('setPreference 用 finally 复位 applying', 'finally' in sp and 'applying = false' in sp)
chk('applying=true 早于平台调用',
    sp.index('applying = true') < sp.index('System.setAppPreferredLanguage'))
chk('applying=false 在 finally 里（异常也不会卡在 true）',
    sp.index('finally') < sp.rindex('applying = false'))
rf = method_body(ip, 'static refreshFromSystem(): Lang')
chk('refreshFromSystem 不触平台 API、也不回调 setPreference',
    'setAppPreferredLanguage' not in rf and 'setPreference' not in rf)

ix = sources[os.path.join('pages', 'Index.ets')]
pk = method_body(ix, 'private pickLanguage(tag: string): void')
chk('界面入口 pickLanguage 走 setPreference', 'I18n.setPreference(tag)' in pk)
chk('pickLanguage 先落库再切', pk.index('saveLanguage') < pk.index('setPreference'))

oncreate = method_body(ab, 'onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void')
chk('attach 排在 setColorMode 之前（否则那次回调会抢跑）',
    oncreate.index('I18n.attach(') < oncreate.index('setColorMode('))

# ---- @Builder 按值传参：算好的句子只在首帧求值，切语言不会重算 ----
# Mate 90 Pro 实测：选「跟随系统」时平台一条配置回调都不发，面板"关掉重开"
# 也没能刷回导出按钮那行。这类位置只许传键名，取词必须发生在 builder 体内。
BUILDER_DEF = re.compile(r'@Builder\s+(?:private\s+)?(\w+)\s*\(')


def top_args(src, open_idx):
    """从 '(' 之后读到配平的 ')'，按最外层逗号切成实参列表。"""
    depth = 1
    parts, cur = [], []
    for k in range(open_idx + 1, len(src)):
        ch = src[k]
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
            if depth == 0:
                parts.append(''.join(cur))
                return parts
        if ch == ',' and depth == 1:
            parts.append(''.join(cur))
            cur = []
            continue
        cur.append(ch)
    return parts


stale_calls = []
for path, text in sources.items():
    for m in BUILDER_DEF.finditer(text):
        for c in re.finditer(r'this\.' + m.group(1) + r'\s*\(', text):
            for arg in top_args(text, c.end() - 1):
                # 回调实参里的 t() 不在首帧求值成文案，放过
                head = arg.split('=>')[0] if '=>' in arg else arg
                if re.search(r'this\.(t|qt)\s*\(', head):
                    stale_calls.append('%s:%s(%s)' % (path, m.group(1), ' '.join(head.split())[:36]))
chk('@Builder 实参不传算好的文案，只传键名', not stale_calls, ' | '.join(stale_calls[:4]))

bad = 0
for name, ok, detail in checks:
    print(('PASS  ' if ok else 'FAIL  ') + name + (('   ' + detail) if (detail and not ok) else ''))
    if not ok:
        bad += 1
print('\n不变式 %d 条，违反 %d 条' % (len(checks), bad))
sys.exit(0 if bad == 0 else 1)
