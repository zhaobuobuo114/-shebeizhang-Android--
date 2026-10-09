# 设备账（Android / Kotlin）

> **作者：浮梁卖茶人**
> 一句话简介：**把每台设备的价格摊到每一天**

鸿蒙「设备账」App 的 Android 版本：功能、配色、动效、41 类 Q 版图标与昼夜模式一一对应。
记录每一台设备**什么时候买的、花了多少钱、到今天用了多少天、平均每天多少钱**——
用得越久，日均成本越低，一眼看出哪些设备最"值回票价"。

变更记录见 [`CHANGELOG.md`](./CHANGELOG.md)。

## 一、快速开始

1. `File > Open` → 选择本工程根目录（含 `settings.gradle.kts` 的那一层）。
2. 首次打开会提示 Sync，点 `Sync Now` 等待完成。
3. 连接手机或启动模拟器 → 点 ▶ 运行。

工程自带 `gradlew` / `gradlew.bat` 与 Gradle wrapper，无需另外准备 Gradle。

**环境要求**

| 项 | 版本 |
| --- | --- |
| Android Studio | 任意近两年版本 |
| Gradle | 8.14（随工程提供 wrapper） |
| AGP | 8.13.2 |
| Kotlin | 2.2.21 |
| compileSdk / targetSdk | 37 |
| buildToolsVersion | 36.0.0 |
| minSdk | 24（Android 7.0） |
| JDK | 17 ~ 21 |

## 二、功能

| 功能 | 说明 |
| --- | --- |
| 记一笔 | 名称、价格、购买日期（系统日期选择器，禁止选未来）、设备类型、备注 |
| 距今天数 | 按自然日计算，「已用 730 天 · 2 年」；用正午时间戳相减，不受时区影响 |
| 日均成本 | 价格 ÷ 已用天数，购买当天算第 1 天，不足 0.01 显示 `<0.01` |
| 汇总看板 | 台数、总投入、日均合计、最划算设备；保存后即时刷新，数字带滚动动画 |
| 扇形统计图 | 自定义 Canvas 环形图，可按 **金额 / 台数** 切换，820ms 展开动画，可收起；圆心金额按长度自适应字号，满 10 万起简写为「10.0万」；图例百分比按最大余数法分配，各段相加恒为 **100%**，扇形按累计角度绘制、最后一段收在整圈上 |
| 设备图标 | 41 种设备各有专属 Q 版插画 |
| 排序 | 日均最高 / 价格最高 / 最近购买 / 用最久 |
| 排序方向 | 「排序」右侧一对自绘小三角（上面尖朝上、下面尖朝下）切换正序 / 倒序，点亮的那一个始终落在对应的一侧；正序时文案变为「日均最低 / 价格最低 / 最早购买 / 用最短」，点当前选中的胶囊也可翻转 |
| 排序胶囊 | 排序栏可以横向拨动，每颗胶囊按被可见范围盖掉多少绕 Y 轴转开、同步淡出——往左没进「排序」一侧的、往右被屏幕边缘切掉的，都像绕到后面去了；整行装得下时一颗都不动 |
| 收藏 | 卡片右下角星标切换收藏；排序栏「★ 收藏 N」胶囊可只看收藏 |
| 回顶部 | 向下滚过 320dp 浮出圆钮，按距离自适应（≤460ms）滚回顶部；置顶条也可点；点下去圆钮会送出一段「起飞」动效 |
| 编辑 | 点卡片直接改，日期、类型、价格全部回显 |
| 删除 | 列表左滑露出红色删除按钮 → 二次确认 |
| 持久化 | SharedPreferences + JSON，杀进程 / 重启不丢 |
| 昼夜模式 | 右上角太阳 / 月亮切换，260ms 颜色插值过渡，选择会被记住，系统控件同步跟随 |
| 首次启动 | 进来先阅读《隐私条款》与《服务条款》，两份条款用系统浏览器打开；勾选同意后才能进应用，不同意可直接退出，确认过一次就不再打扰 |
| 隐私 | 纯本地，不联网，不申请任何权限 |

## 三、动效清单

| 动效 | 时长 / 曲线 |
| --- | --- |
| 总览数字滚动 | 720ms，加速减速插值 |
| 卡片错峰入场 | 430ms，每行延迟 42ms（透明 + 上移 + 缩放） |
| 扇形图展开 | 820ms，easeOutCubic |
| 底部编辑面板 | 滑入 300ms + 遮罩淡入 220ms；关闭 240ms |
| 昼夜切换 | 260ms 全界面颜色插值（`AppTheme.lerp`），图标旋转 20° |
| 空态呼吸 | 1500ms 无限循环往复 |
| 删除确认 | 淡入 200ms + 卡片 0.94→1 缩放 |
| 左滑删除 | `ItemTouchHelper`，红色背景 + 「删除」文字随滑动渐变出现 |
| 排序胶囊立体转动 | 跟手实时计算（无过渡），Y 轴 ±42° + 透明度随遮挡量衰减 |
| 排序方向箭头 | 上下两个自绘实心三角各占一个朝向，切换方向时整组绕 X 轴翻半圈 260ms，翻完后仍是「上尖朝上、下尖朝下」，点亮的那一个随翻转换手 |
| 回顶部「起飞」 | 箭头送出 300ms（EaseIn）+ 光晕扩散 420ms（EaseOut）+ 新箭头自下方补回 260ms |
| 置顶汇总条 | 整层（含背后渐变底）240ms 整体渐隐渐现 |

## 四、目录结构

```
Androidshebeizhang
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/src/main
    ├── AndroidManifest.xml               无任何权限声明
    ├── java/com/deviceledger/app
    │   ├── MainActivity.kt               主界面：总览 / 图表 / 列表 / 编辑 / 删除 / 主题
    │   ├── model
    │   │   ├── DeviceItem.kt             数据模型 + 天数 / 日均计算 + 脏数据清洗
    │   │   ├── DeviceKind.kt             41 种设备类型表（标签 / 配色 / 图标映射）
    │   │   └── ChartData.kt              按类型聚合成饼图数据（最多 6 段，其余合并）
    │   ├── util
    │   │   ├── DateUtil.kt               日期解析、天数差、人性化文案
    │   │   ├── DeviceStore.kt            SharedPreferences 增删改查 + 主题持久化
    │   │   └── Fmt.kt                    金额格式化（千分位、日均、色值透明度）
    │   └── ui
    │       ├── Theme.kt                  昼夜两套配色 + 插值 + 圆角 / 渐变 Drawable 工厂
    │       ├── PieChartView.kt           环形扇形图自定义 View（Canvas + ValueAnimator）
    │       ├── DeviceAdapter.kt          设备卡片列表（错峰入场动画）
    │       ├── KindGridAdapter.kt        41 类图标选择网格
    │       ├── WrapLayout.kt             类型格子流式布局（固定 60×60，排满换行）
    │       ├── SortArrowView.kt          排序方向的自绘实心三角（颜色 / 透明度可动画）
    │       ├── Curve.kt                  与 ArkUI 同参数的插值曲线（Ease 系列 / Friction / Smooth）
    │       ├── MoveAnimator.kt           列表挪位动画（排序 / 筛选时平滑换位）
    │       ├── Tint.kt                   文字与图标的颜色渐变工具
    │       ├── Press.kt                  按压反馈（缩放 + 回弹）
    │       ├── PrivacyGate.kt            首次启动的条款确认（勾选同意 / 退出应用）
    │       └── FieldCompat.kt            输入框加固：选择手柄占位 Drawable + 关闭自动填充
    └── res
        ├── layout/                       activity_main / dialog_privacy / item_device / item_kind / item_legend
        ├── drawable-nodpi/dev_*.png      41 张 Q 版设备插画 + ic_sun / ic_moon / ic_empty
        ├── mipmap-*                      启动图标（自适应图标 + 各密度方形图标）
        ├── values/                       colors / themes / strings
        └── values-night/colors.xml       深色启动底色，防冷启动白闪
```

## 五、与鸿蒙版的差异说明

同一个 App 的两种实现，行为保持一致，底层按各自平台的习惯落地：

| 方面 | 鸿蒙 | Android |
| --- | --- | --- |
| 存储 | `Preferences` | `SharedPreferences`（同样的 JSON 结构，字段完全一致） |
| 日期选择 | `CalendarPicker` | `DatePickerDialog`，限制 `maxDate = 今天` |
| 滑动删除 | `ListItem.swipeAction`（滑出 76vp 的「删除」按钮，点它才弹确认） | `ItemTouchHelper`（左滑到 40% 直接弹确认框） |
| 类型格子 | `Flex({ wrap: FlexWrap.Wrap })` + 固定 60×60 | 自写 `ui/WrapLayout.kt`（同样 60×60、排满换行；不用 `GridLayoutManager`，它会把格子均分拉伸） |
| 按压动效 | `@State pressedKey` + `.scale()`（0.92 / 小胶囊 0.9，140ms） | `ui/Press.kt` 的 `pressEffect()`，同样 0.92 / 0.9 与 140ms，**不改透明度** |
| 主题 | `@State theme` 整体重绘 | `AppTheme.lerp` 做 260ms 颜色插值，动画结束后调用 `AppCompatDelegate.setDefaultNightMode`，让日期选择器等系统控件同步 |
| 渐变 | 系统渐变 | 页面底色与总览卡用自定义 `GradientBgDrawable`（支持非等分停靠点，与鸿蒙 0.0 / 0.28 / 1.0 一致） |

## 六、构建

```bash
./gradlew assembleDebug       # macOS / Linux
gradlew.bat assembleDebug     # Windows
```

调试包产物：`app/build/outputs/apk/debug/app-debug.apk`。

### 正式发布包

正式包走 release 变体，**不带 debuggable 标记**，用私有证书签名：

```bash
./gradlew assembleRelease     # 产物 app/build/outputs/apk/release/app-release.apk
```

签名证书与口令不进版本库，工程根目录放 `keystore.properties` 即可被自动读取
（文件缺失时仍能编译，只是不出正式签名）：

```properties
storeFile=../keystore/device-ledger.jks
storePassword=你的口令
keyAlias=你的别名
keyPassword=你的口令
```

### 覆盖升级包

早期版本用的是另一份证书，换成正式证书后无法原地覆盖安装（Android 会提示签名不同）。
所以额外出一个**内容完全相同、只换了签名**的包，让装了早期版本的设备能直接升上来、数据不丢：

```bash
./gradlew assembleUpgrade   # 产物 app/build/outputs/apk/upgrade/app-upgrade.apk
```

它的证书同样从 `keystore.properties` 读取，加上下面四项即可（同样是本地文件，不进版本库）：

```properties
legacyStoreFile=../keystore/调试证书路径
legacyStorePassword=android
legacyKeyAlias=androiddebugkey
legacyKeyPassword=android
```

两个包选哪个：要提交应用市场就用正式包，自己手机上从旧版升上来就用覆盖升级包。
也可以直接从 [Releases](../../releases) 下载已签好的安装包。
APK 内包含全部 41 张设备图标与 3 张功能图标，`AndroidManifest.xml` 无任何权限声明。

## 七、想再加点什么

- **导出 CSV / 备份**：在 `DeviceStore` 里把同一份 JSON 写到 `filesDir` 即可。
- **换数据库**：数据量大时改用 Room，`DeviceStore` 是对外唯一出入口，替换不影响界面。
- **加新设备类型**：在 `model/DeviceKind.kt` 的 `DEVICE_KINDS` 加一行、在 `kindIconRes()` 补一条分支，
  并把对应的 PNG 放进 `res/drawable-nodpi/`（命名 `dev_<key>.png`），其余逻辑自动生效。

## 八、版本历史

| 版本 | 内容 |
| --- | --- |
| v1.8.2 | 日期选择器的系统文字色改为按昼夜两套取值，深色模式下月份标题与日期数字用亮色，系统控件从此跟着应用的昼夜模式走 |
| v1.8.1 | 输入框按各家 ROM 的规则加固：文本选择手柄在左 / 中 / 右三个位置显式指定为透明 Drawable，并关闭自动填充，在 HarmonyOS 兼容层等各家 ROM 上表现一致 |
| v1.8.0 | 首次启动先确认《隐私条款》与《服务条款》：两份条款用系统浏览器打开，勾选同意后才能进入应用，不同意可直接退出，确认过一次后不再打扰 |
| v1.7.1 | 排序方向箭头上下各画一个朝向（上面尖朝上、下面尖朝下），切换方向翻半圈后点亮的那一个交接到正确一侧 |
| v1.7.0 | 扇形图百分比按最大余数法分配（各段相加恒为 100%）、扇形按累计角度绘制；排序胶囊横向拨动的 Y 轴立体转动；排序箭头改为自绘实心三角 + 切换方向整组翻转；置顶汇总条整体渐隐；回顶部圆钮新增「起飞」动效 |
| v1.6.2 | 列表卡片错峰入场跟随鸿蒙版重播（切收藏筛选、增删设备后重新飞入）；新增沿用早期证书的覆盖升级包 |
| v1.6.1 | 动效的播放时机进一步对齐：进场动画完整播完、换肤不重建界面、图表展开重播扇形 |
| v1.6 | 全部动效按鸿蒙版的时长、曲线、缩放值逐帧对齐；列表排序 / 筛选改为平滑挪位 |
| v1.5.1 | 排序方向热区扩大到「排序」文字、箭头间距收紧、方向切换与收藏切换改为渐变过渡 |
| v1.5 | 排序方向（正序 / 倒序）切换、扇形图圆心金额自适应与大额简写 |
| v1.4.1 | 提供正式发布包：新增 release 签名配置（证书与口令外置到 `keystore.properties`），版本号对齐 1.4 |
| v1.4 | 扇形图中心金额放大、回顶部圆钮、41 类图标默认收一行、收藏（最爱） |
| v1.3 | 与鸿蒙版逐项对齐：阈值、时长、曲线、配色、两套主题全部色值统一 |
| v1.2 | 圆心文字字号按弦长自适应；按压反馈覆盖到全部可点控件 |
| v1.1 | 加按压动效、整页滚动 + 置顶迷你汇总条、圆心数字防重叠 |

各版本详见 [`CHANGELOG.md`](./CHANGELOG.md)。

---

作者：**浮梁卖茶人**
