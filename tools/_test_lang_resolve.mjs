// 与 I18n.resolve() 同构的纯逻辑副本，用于在 Node 下跑用例表。
// 改这里必须同步改 entry/src/main/ets/utils/I18n.ets —— 两边逻辑一致才有验证意义。
const ZH_CN = 'zh-CN', ZH_TW = 'zh-TW', JA = 'ja-JP', EN = 'en';
const FOLLOW_SYSTEM = 'system';

function resolve(preferred, systemTag) {
  if (preferred !== FOLLOW_SYSTEM) {
    if (preferred === ZH_CN) return ZH_CN;
    if (preferred === ZH_TW) return ZH_TW;
    if (preferred === JA) return JA;
    if (preferred === EN) return EN;
  }
  const raw = (systemTag ?? '').toLowerCase();
  const parts = [];
  const byDash = raw.split('-');
  for (const a of byDash) {
    for (const b of a.split('_')) parts.push(b);
  }
  const primary = parts.length > 0 ? parts[0] : '';
  if (primary === 'zh') {
    if (parts.indexOf('hant') >= 0) return ZH_TW;
    if (parts.indexOf('hans') >= 0) return ZH_CN;
    for (let i = 1; i < parts.length; i++) {
      const p = parts[i];
      if (p === 'tw' || p === 'hk' || p === 'mo') return ZH_TW;
    }
    return ZH_CN;
  }
  if (primary === 'ja') return JA;
  return EN;
}

const CASES = [
  // [preferred, systemTag, expected, 说明]
  ['system', 'zh', ZH_CN, '裸语言子标签'],
  ['system', 'zh-CN', ZH_CN, '语言+地区'],
  ['system', 'zh-Hans-CN', ZH_CN, '真机常见形态（旧代码在这里判错 → 英文）'],
  ['system', 'zh-Hant-TW', ZH_TW, '繁体靠脚本识别'],
  ['system', 'zh-Hant-HK', ZH_TW, '繁体 HK'],
  ['system', 'zh-TW', ZH_TW, '无脚本、靠地区'],
  ['system', 'zh-HK', ZH_TW, '无脚本、靠地区'],
  ['system', 'zh-MO', ZH_TW, '澳门'],
  ['system', 'zh_SG', ZH_CN, '下划线分隔'],
  ['system', 'zh-Hans-MO', ZH_CN, '脚本优先于地区：Hans 即简体'],
  ['system', 'zh-Hant', ZH_TW, '只有脚本没有地区'],
  ['system', 'zh-Hans', ZH_CN, '只有脚本没有地区'],
  ['system', 'zh-Latn-TW', ZH_TW, '认不出的脚本不能替繁体地区说"不"（安卓原先在这里短路成简体）'],
  ['system', 'en', EN, '裸英文'],
  ['system', 'en-US', EN, '美式英语'],
  ['system', 'en-GB', EN, '英式英语'],
  ['system', 'ja', JA, '裸日文'],
  ['system', 'ja-JP', JA, '日语'],
  ['system', 'JA-jp', JA, '大小写混写'],
  ['system', 'ZH-HANS-CN', ZH_CN, '标签整体大写'],
  ['system', 'de-DE', EN, '不支持的语言 → 英文，不掉回中文'],
  ['system', 'fr-FR', EN, '同上'],
  ['system', 'ko-KR', EN, '同上'],
  ['system', 'ru', EN, '同上'],
  ['system', '', EN, '空标签'],
  ['system', undefined, EN, '取不到系统语言'],
  ['zh-CN', 'en-US', ZH_CN, '手动选简中，系统英文也听用户的'],
  ['zh-TW', 'zh-Hans-CN', ZH_TW, '手动选繁中，系统简体也听用户的'],
  ['ja-JP', 'zh-CN', JA, '手动选日文'],
  ['en', 'zh-CN', EN, '手动选英文'],
];

let bad = 0;
for (const [pref, tag, want, note] of CASES) {
  const got = resolve(pref, tag);
  const ok = got === want;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  resolve(${JSON.stringify(pref)}, ${JSON.stringify(tag)}) = ${got}` +
    `${ok ? '' : `  期望 ${want}`}   // ${note}`);
}
console.log(`\n用例 ${CASES.length} 条，失败 ${bad} 条`);
process.exit(bad === 0 ? 0 : 1);
