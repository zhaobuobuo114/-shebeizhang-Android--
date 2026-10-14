# tools/ —— 跨端工具链（两端共用）

这些脚本原本躺在作者的工作区根目录，与 `Android/`、`HarmonyOS/` **三个目录并排**：
`_i18n_gen.py` 等用的是 `ROOT = os.path.dirname(os.path.abspath(__file__))`，
再从 `ROOT` 往下找 `Android/app/src/main/res/…` 与 `HarmonyOS/entry/src/main/resources/…`。

所以：**单独 clone 这一个仓库时，它们会找不到另一端的目录。**
要跑的话，把两个仓库并排放在同一个父目录下，并按各自实际的目录名调整脚本顶部的
`ROOT` / 平台子路径；只改顶部那几行，逻辑不用动。

| 文件 | 作用 |
| --- | --- |
| `_i18n_master.json` | **四语言文案的唯一真源**（241 条文案 + 10 组复数）。两端资源都由它生成，改文案只改这里 |
| `_i18n_gen.py` | 生成安卓四份 `values*/strings.xml` 与鸿蒙四份 `resources/rawfile/i18n/*.json` |
| `_check_i18n.py` | 静态门：四语言键集合一致、占位符一致、转义合法、复数 one/other 齐全 |
| `_check_interop.py` | 静态门：两端备份文件字段与解析规则逐条对齐（一端导出的文件另一端必须能原样读回） |
| `_check_lang_loop.py` | 静态门：15 条结构不变量，锁住"切语言 → 平台回调 → 再调平台接口"那条会打爆栈的回环，以及 `@Builder` 按值传文案等已知陷阱 |
| `_test_lang_resolve.mjs` | 语言解析用例表（30 条），与安卓 `LangResolveTest` 同一张表 |
| `_test_fmt_tier.mjs` | 金额缩写跨档进位与 `oneDecimal` 边界（92 条），两端共用同一张表 |
| `_test_lang_device.py` | 安卓真机/模拟器回归：连设备跑 uiautomator 文案断言，期望值全部从 `values*/strings.xml` 现读 |
| `_test_lang_device_harmony.py` | 鸿蒙真机/模拟器回归：`uitest dumpLayout` 文本树断言 97 条，崩溃只认 `faultlogger` 里多出来的 jscrash 文件 |

后两个脚本里写了几处本机路径（DevEco SDK、Android SDK、hdc/adb 的位置），换机器要改。
