// 与 utils/Format.ets 同构的纯逻辑副本，用来在 Node 下跑金额边界用例。
// 改这里必须同步改 Format.ets —— 两边逻辑一致才有验证意义。
// 安卓端对应的是 app/src/test/.../MoneyFormatTest.kt，两张表逐行相同。
const K = { divisor: 1e3, unitKey: 'fmt_k' };
const M = { divisor: 1e6, unitKey: 'fmt_m' };
const B = { divisor: 1e9, unitKey: 'fmt_b' };
const WAN = { divisor: 1e4, unitKey: 'fmt_wan' };
const YI = { divisor: 1e8, unitKey: 'fmt_yi' };

function tierOf(a, wanNumerals) {
  if (wanNumerals) {
    const wan = Math.round(a / 1000) / 10;
    if (a >= 1e8) return YI;
    if (a >= 1e5) return wan < 10000 ? WAN : YI;
    return null;
  }
  const kb = Math.round(a / 100) / 10;
  const mb = Math.round(a / 1e5) / 10;
  const bb = Math.round(a / 1e8) / 10;
  if (a >= 1e9) return bb < 1000 ? B : null;
  if (a >= 1e6) return mb < 1000 ? M : B;
  if (a >= 1e5) return kb < 1000 ? K : M;
  return null;
}

function parsePrice(text) {
  const t = text.trim();
  const first = t.indexOf(',');
  let s = t;
  if (first >= 0) {
    if (t.indexOf('.') >= 0 || t.indexOf(',', first + 1) >= 0) return NaN;
    s = t.replace(',', '.');
  }
  return /^\d+(?:\.\d*)?$|^\.\d+$/.test(s) ? Number(s) : NaN;
}

/** Format.ets 的 money()：恒 ASCII、不出现 "-0" */
function money(v) {
  const safe = Number.isFinite(v) ? v : 0;
  const neg = safe < 0;
  let s = Math.abs(safe).toFixed(2);
  if (s.endsWith('.00')) s = s.substring(0, s.length - 3);
  const parts = s.split('.');
  let out = '';
  for (let i = 0; i < parts[0].length; i++) {
    if (i > 0 && (parts[0].length - i) % 3 === 0) out += ',';
    out += parts[0].charAt(i);
  }
  if (parts.length > 1) out = out + '.' + parts[1];
  return (neg && out !== '0' ? '-' : '') + out;
}

let bad = 0;
function eq(want, got, note) {
  const w = want === null ? 'null' : (want && want.unitKey) || String(want);
  const g = got === null ? 'null' : (got && got.unitKey) || String(got);
  const ok = w === g;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${note}  期望=${w} 实际=${g}`);
}

const EN = [
  [0, null], [99999, null], [99999.5, null],
  [100000, K], [123456, K], [999949, K],
  [999950, M], [999999, M], [1000000, M], [999949999, M],
  [999950000, B], [999999999, B], [1000000000, B], [999949999999, B],
  [999950000000, null], [1000000000000, null], [9.9e12, null]
];
for (const [a, want] of EN) eq(want, tierOf(a, false), `EN 档位 a=${a}`);

const ZH = [
  [0, null], [99999, null], [100000, WAN], [123456, WAN], [9999499, WAN],
  // 9,999,950 用万档是 1000.0万，没到 10000 就不用升档；真正的升档点是 99,999,500
  [9999950, WAN], [99994999, WAN], [99999499, WAN], [99999500, YI], [99999999, YI],
  [100000000, YI], [1e12, YI]
];
for (const [a, want] of ZH) eq(want, tierOf(a, true), `汉字档位 a=${a}`);

// 除数必须与档位配对：缩写时算的是 v / divisor
for (const [a, want] of EN) {
  const got = tierOf(a, false);
  eq(want === null ? null : want.divisor, got === null ? null : got.divisor, `EN 除数 a=${a}`);
}

const PRICE = [
  ['1234.5', 1234.5], ['1234,5', 1234.5], ['1234.', 1234], ['.5', 0.5],
  ['1234', 1234], [' 42 ', 42],
  ['12abc', NaN], ['abc12', NaN], ['1e3', NaN], ['1.2.3', NaN],
  ['1,2.3', NaN], ['1,2,3', NaN], ['', NaN], [' ', NaN], ['.', NaN], ['-5', NaN]
];
for (const [src, want] of PRICE) {
  const got = parsePrice(src);
  const ok = (Number.isNaN(want) && Number.isNaN(got)) || got === want;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  parsePrice(${JSON.stringify(src)}) = ${got}  期望 ${want}`);
}

const MONEY = [
  [0, '0'], [1234567, '1,234,567'], [1234.5, '1,234.50'], [-1234.5, '-1,234.50'],
  [-0.004, '0'], [0.004, '0'], [NaN, '0'], [Infinity, '0'], [-Infinity, '0'],
  [0.5, '0.50'], [100, '100']
];
for (const [src, want] of MONEY) {
  const got = money(src);
  const ok = got === want;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  money(${src}) = ${got}  期望 ${want}`);
}

/** Format.ets 的 oneDecimal()：先按十分位取整，再补出 "1000.0" 这种尾零 */
function oneDecimal(x) {
  return (Math.round(x * 10) / 10).toFixed(1);
}

/** Format.ets 的 fallbackId()：时间戳 + 进程内递增序号 + 24 位随机 */
let fallbackSeq = 0;
function fallbackId(createdAt) {
  fallbackSeq += 1;
  return 'd' + createdAt + '-' + fallbackSeq.toString(36) + '-' +
    Math.floor(Math.random() * 0x1000000).toString(36);
}

// 取一位小数：与安卓 String.format("%.1f") 逐值对齐（表与 MoneyFormatTest 同一张）。
// 数值是真机量的：jbr-21 跑 %.1f、Node 跑本函数，18 个探针全等。
// 别改成 x.toFixed(1)——那是《BUG报告》B8 的"修法"，实测反而在 12.35/55.55/56.05/
// 5.55/0.15/0.35 六处与 Java 分歧（Java 的 %f 并不是"对精确值 HALF_UP"）。
const ONE_DEC = [
  [12.35, '12.4'], [55.55, '55.6'], [56.05, '56.1'], [5.55, '5.6'],
  [8.65, '8.7'], [2.675, '2.7'], [999.95, '1000.0'], [0.05, '0.1'],
  [0.25, '0.3'], [1.005, '1.0'], [123.456, '123.5'], [0.15, '0.2'],
  [2.5, '2.5'], [12.3499999, '12.3'], [0.0, '0.0'], [0.35, '0.4'],
  [1.0499999, '1.0'], [7.25, '7.3']
];
for (const [v, want] of ONE_DEC) {
  const got = oneDecimal(v);
  const ok = got === want;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  oneDecimal(${v}) = ${got}  期望 ${want}`);
}

// 补出来的 id 同批内必须互不相同
{
  const before = fallbackSeq;
  const set = new Set();
  for (let i = 0; i < 500; i++) set.add(fallbackId(0));
  const ok = set.size === 500 && fallbackSeq - before === 500;
  if (!ok) bad++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  fallbackId 500 条互不相同（实得 ${set.size}）`);
}

console.log(`\n失败 ${bad} 条`);
process.exit(bad === 0 ? 0 : 1);
