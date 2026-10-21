package com.deviceledger.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deviceledger.app.databinding.ActivityMainBinding
import com.deviceledger.app.model.ChartData
import com.deviceledger.app.model.DEVICE_KINDS
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.BadgeDef
import com.deviceledger.app.model.Badges
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.PieSlice
import com.deviceledger.app.model.TrashItem
import com.deviceledger.app.ui.AppTheme
import com.deviceledger.app.ui.BadgeCelebration
import com.deviceledger.app.ui.BadgeWall
import com.deviceledger.app.ui.CardColors
import com.deviceledger.app.ui.ConfirmSheet
import com.deviceledger.app.ui.Curves
import com.deviceledger.app.ui.DataSheet
import com.deviceledger.app.ui.DeviceAdapter
import com.deviceledger.app.ui.FieldCompat
import com.deviceledger.app.ui.GradientBgDrawable
import com.deviceledger.app.ui.KindTilesBinder
import com.deviceledger.app.ui.MoveAnimator
import com.deviceledger.app.ui.PrivacyGate
import com.deviceledger.app.ui.SheetKit
import com.deviceledger.app.ui.SortArrowView
import com.deviceledger.app.ui.TrashSheet
import com.deviceledger.app.ui.UndoBar
import com.deviceledger.app.ui.WrapLayout
import com.deviceledger.app.ui.borderedDrawable
import com.deviceledger.app.ui.circleDrawable
import com.deviceledger.app.ui.pressEffect
import com.deviceledger.app.ui.chipTo
import com.deviceledger.app.ui.roundedDrawable
import com.deviceledger.app.ui.roundedTopDrawable
import com.deviceledger.app.ui.tintTo
import com.deviceledger.app.ui.topPressEffect
import com.deviceledger.app.util.L10n
import com.deviceledger.app.util.Lang
import com.deviceledger.app.util.BackupStore
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.DeviceStore
import com.deviceledger.app.util.Fmt
import java.util.Calendar
import java.util.Locale
import java.util.Comparator

/**
 * 页脚那条链接指向作者的 GitHub。写在这里而不是文案里：URL 是数据不是文案，
 * 放进文案表会让它有机会被译歪。鸿蒙端 `pages/Index.ets` 有同一个常量，值必须一致。
 *
 * 屏幕上不再出现这串地址：它作为超链接挂在文案里字面量「GitHub」这个词上，
 * 所以 `footer_github` 四门语言都只是「我的 GitHub」这一句。
 */
private const val GITHUB_URL = "https://github.com/zhaobuobuo114"

/** 四种语言的这条文案里都出现这个字面量，按它定位要上链接的那几个字 */
private const val GITHUB_LINK_WORD = "GitHub"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var kindBinder: KindTilesBinder

    /**
     * 界面上的文案上一次是**按哪门语言渲染**的。
     *
     * 判"语言有没有变"必须拿它当基准，不能拿 `L10n.lang()`：`apply()` 在切语言那一刻
     * 就把 current 改成了新语言，而 Resources 要等系统投递配置变更才换过来——
     * 在 onConfigurationChanged 里比较"自己前后的 L10n.lang()"永远相等，
     * 照那样加守卫就会把所有重刷跳过，直接退回 v2.0.0 那个"背景旧语言、面板新语言"的 bug。
     * 这个字段只在真的刷过一遍文案之后才更新，它记的是渲染状态，不是意图。
     */
    private var renderedLang: Lang? = null
    /** 列表动画：只让"位置挪动"播，时长与曲线每次提交列表前指定 */
    private lateinit var listAnimator: MoveAnimator

    private var dark: Boolean = false
    private var theme: AppTheme = AppTheme.lightTheme()
    private val devices = mutableListOf<DeviceItem>()

    private var sortType: String = "avg"
    /** 排序方向：false = 倒序（大在前，默认），true = 正序（小在前） */
    private var sortAsc: Boolean = false
    private var chartMode: String = "amount"
    private var chartOpen: Boolean = true

    /** 只看收藏 */
    private var favOnly: Boolean = false
    /** 编辑面板里的"收藏为最爱" */
    private var editFavorite: Boolean = false

    /** 设备类型格子：默认只铺一行，点"展开全部"再看全部 */
    private var kindExpanded: Boolean = false
    private var kindCols: Int = 4
    private var kindGridAnim: android.animation.ValueAnimator? = null

    private var editingId: String? = null
    private var editKind: String = "phone"
    // 必须公历：这个 Calendar 的 YEAR/MONTH/DAY 会被拼成存盘的 buyDate，
    // 用跟随语言的 getInstance() 在 Hijrah / 佛历 / 和历设备上会写出 1447-xx-xx 这种脏数据
    private val editDate: Calendar = Calendar.getInstance(Locale.ROOT)
    private var pendingDeleteId: String? = null

    private var displayTotal: Double = 0.0
    private var displayAvg: Double = 0.0
    /** 数字滚动正在前往的目标值：用来判断某次刷新会不会打断正在播的动画 */
    private var numberTargetTotal: Double = 0.0
    private var numberTargetAvg: Double = 0.0
    private var firstNumberPlayed: Boolean = false
    private var numberAnimator: android.animation.ValueAnimator? = null
    private var themeAnimator: android.animation.ValueAnimator? = null
    private var breathAnimator: android.animation.ValueAnimator? = null
    /** 滚动监听要留引用，否则销毁时摘不掉，页面上任何滚动都会一直驱动胶囊重算 */
    private var scrollListener: android.view.ViewTreeObserver.OnScrollChangedListener? = null
    private var lastSlices: List<PieSlice> = emptyList()
    /** 首次启动的条款确认框：持有它是为了退出界面时能收掉，不留悬空窗口 */
    private var privacyDialog: Dialog? = null
    /** 日期选择器也要留引用：不跟踪的话 Activity 销毁时它还挂着，logcat 直接报 WindowLeaked */
    private var datePickerDialog: DatePickerDialog? = null

    /** 回收站：删掉的设备先落到这里，30 天内随时原样恢复 */
    private val trash = mutableListOf<TrashItem>()
    /** 已点亮的勋章：id -> 获得时间戳 */
    private val badgeState = mutableMapOf<String, Long>()
    /** 刚删掉的那一条，撤销条按它把设备放回去 */
    private var lastDeletedId: String? = null
    /** 撤销条那句话里嵌的设备名：切语言时要按原样重取一遍文案 */
    private var undoName: String = ""
    private var undoBar: UndoBar? = null
    /** 勋章达成弹屏：根布局上的一层覆盖，取代原来的那条 Toast */
    private lateinit var celebration: BadgeCelebration
    /**
     * 同一时刻只该有一个居中面板。赋新值时把上一个 dismiss 掉：
     * 六个入口都往这一个字段上覆盖赋值，旧面板既关不掉、引用又丢，
     * Activity 销毁时没人 dismiss 就会报 WindowLeaked，
     * 还会叠出"两层回收站、要关两次"的怪状态。
     */
    private var hubDialog: Dialog? = null
        set(value) {
            if (field !== value) field?.dismiss()
            field = value
        }

    /** 导出 / 导入走系统文件选择器，不需要任何存储权限 */
    private lateinit var exportLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    private val density: Float
        get() = resources.displayMetrics.density

    /* ------------------------------ 生命周期 ------------------------------ */

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceStore.init(this)
        dark = DeviceStore.loadDark()
        // 让系统控件（日期选择器等）跟随当前昼夜模式；必须在 super.onCreate 之前设置
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        // 同理，界面语言也要在 super 之前定下来：晚一步首帧就按错语言排好了
        L10n.apply(applicationContext)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 37 在 Android 15+ 是强制 edge-to-edge：不让出系统栏，标题就顶进状态栏与挖孔，
        // 撤销条、回顶钮、面板底部按钮会落进导航手势区（底部上滑先被系统吃掉）。
        // 根节点吃 inset、页面底色也在根节点上，所以顶部仍然是一条完整的渐变，不留白带。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // 键盘弹起时把编辑面板顶起来，否则"保存/取消"会被键盘盖住
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, imeBottom))
            insets
        }
        binding.root.requestApplyInsets()
        // 上下两个三角各画一个方向：上面尖朝上、下面尖朝下。这一对形状就是"方向指示"的本体，
        // 整组翻半圈时它们连着形状一起转过去，翻完之后仍是"上尖朝上、下尖朝下"的样子。
        binding.arrowSortDown.dir = SortArrowView.Dir.DOWN
        theme = AppTheme.of(dark)

        setupOutline()
        setupList()
        setupKindGrid()
        setupActions()
        setupBack()
        setupSticky()
        setupConfirmWidth()
        setupFavRow()
        setupKindToggle()
        setupChipFx()
        // 输入框加固：给选择手柄配透明占位 Drawable，并关掉自动填充。
        // 部分厂商 ROM（含 HarmonyOS 的安卓兼容层）在这两处会抛空指针导致闪退。
        FieldCompat.apply(binding.etName, binding.etPrice, binding.etNote)
        setupDataHub()
        renderStaticTexts()
        celebration = BadgeCelebration(this, binding.badgeLayer)

        // 首次启动先把隐私条款与服务条款摆出来，勾选同意之后才把本机数据读进界面
        if (DeviceStore.loadAgreed()) {
            loadLocalData(animate = savedInstanceState == null)
        } else {
            privacyDialog = PrivacyGate.show(this, theme) { loadLocalData(animate = true) }
        }
        applyTheme(theme)
        renderDateText()
        setupBreath()
    }

    /** 把本机存的设备、回收站、勋章读进内存并整屏刷新一次 */
    private fun loadLocalData(animate: Boolean) {
        devices.clear()
        devices.addAll(DeviceStore.load())
        trash.clear()
        trash.addAll(DeviceStore.loadTrash(BackupStore.KEEP_DAYS))
        badgeState.clear()
        badgeState.putAll(DeviceStore.loadBadges())
        refreshAll(animateNumbers = animate)
        updateBadgeEntry()
        // 首次进入只是把已有状态读回来，不该为此弹"获得新勋章"
        checkBadges(announce = false)
    }

    override fun onResume() {
        super.onResume()
        // 条款还没确认：这一屏不碰任何本机数据
        if (privacyDialog?.isShowing == true) return
        // 回到前台时对齐一次磁盘数据。
        // 注意：数据没变就一个字都别碰——refreshAll 会把正在播的进场动画
        // （数字滚动 900ms、扇形展开 820ms）cancel 掉直接跳到终态，
        // 鸿蒙版 onPageShow 里 refreshStats 只在数值真的变了才更新 @State，不会打断动画。
        val disk = DeviceStore.load()
        if (disk != devices) {
            devices.clear()
            devices.addAll(disk)
            refreshAll(animateNumbers = true)
        }
    }

    override fun onDestroy() {
        privacyDialog?.dismiss()
        hubDialog?.dismiss()
        datePickerDialog?.dismiss()
        datePickerDialog = null
        celebration.dismiss()
        // 每一路动画都要收：只 cancel 三个的话，换肤/图表/回顶/起飞那几路
        // 会在 Activity 销毁后继续逐帧写已经脱离窗口的 view
        numberAnimator?.cancel()
        themeAnimator?.cancel()
        breathAnimator?.cancel()
        kindGridAnim?.cancel()
        chartAnim?.cancel()
        scrollAnim?.cancel()
        launchAnim?.cancel()
        scrollListener?.let { binding.sortScroller.viewTreeObserver.removeOnScrollChangedListener(it) }
        scrollListener = null
        undoBar?.dispose()
        super.onDestroy()
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.confirmLayer.visibility == View.VISIBLE -> showConfirm(false)
                    binding.editorLayer.visibility == View.VISIBLE -> closeEditor()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupOutline() {
        val provider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 20f * density)
            }
        }
        binding.summaryCard.outlineProvider = provider
        binding.summaryCard.clipToOutline = true
    }

    /** 面板高度由布局权重决定（窗口被软键盘压缩时会自动跟着缩小） */

    /* ------------------------------ 置顶迷你汇总条 ------------------------------ */

    private var stickyShown: Boolean = false
    /** 置顶条的淡入淡出是否进行到一半：用于判断那一层有没有被打断、要不要补一笔 */
    private var barAlphaDirty: Boolean = false

    /**
     * 整页向下滚动、大总览卡滑出屏幕后，在最上方钉住一小行：
     * 总价 / 台数 / 日均合计。滚回顶部时自动收起。
     */
    private fun setupSticky() {
        binding.scrollMain.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ -> updateSticky(scrollY) }
        )
        binding.root.post {
            updateSticky(binding.scrollMain.scrollY)
            refreshChipFx()
        }
    }

    private fun updateSticky(scrollY: Int) {
        // 与鸿蒙版 Index.ets 里的 onListScroll 完全同一个阈值：滚过 200vp 才钉住
        val threshold = (200f * density).toInt()
        val want = devices.isNotEmpty() && scrollY > threshold
        if (want != stickyShown) {
            stickyShown = want
            val bar = binding.stickyBar
            bar.animate().cancel()
            // 整层一起淡，连背后的渐变底色一起走，不做位移。
            // 早先只让里头那张卡做位移+淡出，外层那道渐变是硬切出来硬切收回的，
            // 滑回顶部的瞬间会先留一帧深色横条再消失，看着就是闪过一道黑框
            if (want) {
                barAlphaDirty = true
                bar.visibility = View.VISIBLE
                bar.alpha = 0f
                bar.animate()
                    .alpha(1f)
                    .setDuration(240L)
                    .setInterpolator(Curves.easeOut)
                    .setListener(null)
                    .withEndAction { barAlphaDirty = false }
                    .start()
            } else {
                barAlphaDirty = true
                bar.animate()
                    .alpha(0f)
                    .setDuration(240L)
                    .setInterpolator(Curves.easeOut)
                    // 只有还处于收起状态才真的摘掉；中途又往下滚了就留着继续用，
                    // 免得被上一次动画结束时摘掉之后再也露不出来
                    .withEndAction {
                        barAlphaDirty = false
                        if (!stickyShown) {
                            bar.visibility = View.GONE
                        }
                    }
                    .start()
            }
        } else if (want) {
            // 状态没变但整层的透明度还没落到位（多半是被上一次动画掐断了），补一笔。
            // 少了这一步，滚回顶部时会留着一条半透明的深色横杠，看着就像黑框没收干净
            if (barAlphaDirty && binding.stickyBar.alpha < 1f) {
                barAlphaDirty = false
                binding.stickyBar.animate()
                    .alpha(1f)
                    .setDuration(120L)
                    .setInterpolator(Curves.easeOut)
                    .setListener(null)
                    .start()
            }
        }
        // 再往下滚一点就浮出回顶部圆钮
        updateTopButton(devices.isNotEmpty() && scrollY > (320f * density).toInt())
    }

    /* ------------------------------ 回顶部圆钮 ------------------------------ */

    /** 逻辑状态（不参与渲染，只用来防抖；动画会来回触发 scroll 回调） */
    private var topShown: Boolean = false

    /**
     * 显隐跟鸿蒙版 setTopBtn 完全同步：
     * 出现 = 挂上后 170ms 冲到 1.12 倍 + 淡入，再 140ms 回落到 1 倍；
     * 收起 = 140ms 缩到 0.4 倍 + 淡出后隐藏。
     */
    private fun updateTopButton(show: Boolean) {
        if (show == topShown) {
            return
        }
        topShown = show
        val btn = binding.btnTop
        btn.animate().cancel()
        if (show) {
            // 收起时可能停在"起飞"的半路上（原位那枚已经淡没了），
            // 重新露出来之前先把两枚箭头摆回出发姿态，免得看到一枚空的圆钮
            binding.ivTop.animate().cancel()
            binding.ivTop.translationY = 0f
            binding.ivTop.alpha = 1f
            binding.ivTopFly.animate().cancel()
            binding.ivTopFly.alpha = 0f
            btn.visibility = View.VISIBLE
            btn.scaleX = 0.3f
            btn.scaleY = 0.3f
            btn.alpha = 0f
            btn.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .alpha(1f)
                // 鸿蒙版先 setTimeout 16ms 再 animateTo：让"0.3 倍"这一帧先落下去，
                // 否则动画会从 1 开始弹。这里用 startDelay 还原同样的节奏
                .setStartDelay(16L)
                .setDuration(170L)
                .setInterpolator(Curves.friction)
                .withEndAction {
                    if (!topShown) {
                        return@withEndAction
                    }
                    btn.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .setInterpolator(Curves.easeInOut)
                        .start()
                }
                .start()
        } else {
            btn.animate()
                .scaleX(0.4f)
                .scaleY(0.4f)
                .alpha(0f)
                .setDuration(140L)
                .setInterpolator(Curves.easeIn)
                .withEndAction { btn.visibility = View.GONE }
                .start()
        }
    }

    private var scrollAnim: android.animation.ValueAnimator? = null
    /** 图表区展开 / 收起的高度动画 */
    private var chartAnim: android.animation.ValueAnimator? = null

    /**
     * 回顶部：时长跟着滚动距离走（200ms 起步、最远 460ms），缓出收尾，
     * 自己驱动 scrollTo 而不是用 smoothScrollTo —— 系统那套固定 250ms，滚得远的页面会"黏"。
     */
    private fun scrollToTop() {
        val from = binding.scrollMain.scrollY
        if (from <= 0) {
            return
        }
        scrollAnim?.cancel()
        val spanVp = from / density
        val dur = Math.min(460L, Math.round(200f + spanVp * 0.3f).toLong())
        val va = android.animation.ValueAnimator.ofInt(from, 0)
        va.duration = dur
        va.interpolator = Curves.easeOut
        va.addUpdateListener { binding.scrollMain.scrollTo(0, it.animatedValue as Int) }
        va.start()
        scrollAnim = va
    }

    /* --------------------- 回顶部按钮的"起飞"特效 --------------------- */

    private var launchAnim: android.animation.ValueAnimator? = null
    /** 本轮起飞的凭据：连点时用它把上一轮还没走完的结束回调认出来、丢掉 */
    private var launchToken: Any? = null

    /**
     * 点回顶部要走的整套动作：页面照常滚回去，圆钮自己再送一段"起飞"。
     *
     * 三件事同时开始，和鸿蒙版 launchTopAction 同一个时序：
     *   1. 那枚箭头向上飞出圆钮并淡掉（300ms EaseIn）；
     *   2. 一圈主色光晕从圆钮大小扩到两倍再褪掉（420ms EaseOut）；
     *   3. 300ms 后一枚新的箭头从下方补回原位（260ms Friction），收成一记完整的"送一趟"。
     *
     * 本体本身的压缩回弹由 Press.kt 的 topPressEffect 负责（按下 90ms / 松手 210ms），
     * 这里不去抢那两个属性，免得两套动画互相掐。
     */
    private fun playTopLaunch() {
        val icon = binding.ivTop
        val fly = binding.ivTopFly
        val ring = binding.topRing
        val d = density

        launchAnim?.cancel()
        // 回到出发姿态：原位那枚露着，待命的那枚先在下方藏好。
        // 按键连点时会重新从这里起跑，不会把上一次的残留状态带进来
        icon.animate().cancel()
        icon.translationY = 0f
        icon.alpha = 1f
        fly.animate().cancel()
        fly.translationY = 16f * d
        fly.alpha = 0f

        // 1) 箭头起飞：原位那枚向上送出、淡到全无，之后就一直空着，
        //    收尾那枚由下面的第 3 步补回来。全程只有一枚可见，
        //    所以这里结束时不把 alpha 拨回去，否则会闪出第二枚箭头
        val token = Any()
        launchToken = token
        launchAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = Curves.easeIn
            addUpdateListener {
                val k = it.animatedFraction
                icon.translationY = -26f * d * k
                icon.alpha = 1f - k
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 被新的一次点击顶掉时（token 已经换人）就不接续了，
                    // 否则会和新一轮的起飞撞在一起
                    if (launchToken === token) {
                        arrive()
                    }
                }
            })
            start()
        }

        // 2) 光晕扩散
        ring.scaleX = 0.62f
        ring.scaleY = 0.62f
        ring.alpha = 0.5f
        ring.animate()
            .scaleX(2.1f)
            .scaleY(2.1f)
            .alpha(0f)
            .setDuration(420L)
            .setInterpolator(Curves.easeOut)
            .start()
    }

    /**
     * 起飞之后从下方补回原位的那一枚。
     *
     * 挂在起飞动画的结束回调上，而不是掐一个 300ms 的定时器：定时器数的是真实时间，
     * 系统把动画速度调慢（开发者选项里的动画缩放）时起飞还没走完它就点火了，
     * 圆钮里会同时挂着两枚箭头。
     */
    private fun arrive() {
        val fly = binding.ivTopFly
        fly.translationY = 16f * density
        fly.alpha = 0f
        fly.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(Curves.friction)
            .start()
    }

    /* --------------------- 排序胶囊的立体转动 --------------------- */

    /**
     * 这一行的胶囊太多了会横向排不下，本来由 HorizontalScrollView 兜着。
     * 这里再给它加一层"越线转开"的观感：每颗胶囊按它被可见范围盖掉了多少，
     * 在 Y 轴上顺着透视转向、同时淡掉——往左没进"排序"那一侧的、往右被屏幕边缘切掉的，
     * 都像绕到后头去了。
     *
     * 依据是"被切掉多少"而不是"离这一行中心多远"：后者会在整行装得下、
     * 一颗都没被挡住的时候也把最边上那颗转掉压暗，静止看就像坏了。
     * 算法与鸿蒙版 Index.ets 的 refreshChipFx 是同一套。
     */
    private fun setupChipFx() {
        val scroller = binding.sortScroller
        val listener = android.view.ViewTreeObserver.OnScrollChangedListener { refreshChipFx() }
        scrollListener = listener
        scroller.viewTreeObserver.addOnScrollChangedListener(listener)
        // 布局一落定就得算一次：光靠 post 往往赶在测量之前，
        // 那时行宽还是 0，会被当成"装得下"而整行都不转；
        // 而这一行只要没被拖动就不会再有滚动回调来补救
        scroller.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshChipFx() }
        // 行内胶囊的宽度变了（换方向时文案长短不同）同样要重量一遍
        scroller.getChildAt(0)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshChipFx()
        }
    }

    private fun refreshChipFx() {
        val scroller = binding.sortScroller
        val row = scroller.getChildAt(0) as? ViewGroup ?: return
        val viewW = scroller.width.toFloat()
        if (viewW <= 0f || row.width <= 0) return
        val scrollX = scroller.scrollX.toFloat()
        // 整行比视口宽出来的部分；<=0 表示全都能看见，那就没有可转的东西
        val overflow = row.width - viewW

        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i)
            if (chip.width <= 0) continue
            if (overflow <= 0.5f) {
                chip.rotationY = 0f
                chip.alpha = 1f
                continue
            }
            val w = chip.width.toFloat()
            val left = chip.left.toFloat()
            // 左边界是"排序"那一侧，右边界是屏幕边缘：分别量一下被它们盖掉多宽
            val hiddenLeft = (scrollX - left).coerceIn(0f, w)
            val hiddenRight = ((left + w) - (scrollX + viewW)).coerceIn(0f, w)
            val frac = ((hiddenLeft + hiddenRight) / w).coerceIn(0f, 1f)
            // 透视距离先定下来，转角才有厚度，不然只是被横向压扁
            chip.cameraDistance = 1200f * density
            // 往右滚出去（被右边切掉）转向一侧，往左没进"排序"里的转向另一侧
            chip.rotationY = (hiddenRight - hiddenLeft) / w * 42f
            // 遮挡越多掉得越快：刚压线时几乎看不出来，越往外越淡
            chip.alpha = 1f - 0.92f * Math.pow(frac.toDouble(), 1.6).toFloat()
        }
    }

    /* ------------------------------ 列表 ------------------------------ */

    private fun setupList() {
        adapter = DeviceAdapter(
            onClick = { item -> openEditor(item) },
            onStar = { item, fav -> onStarTapped(item, fav) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        // 只播位移动画，时长与曲线由每次提交列表时指定（鸿蒙端排序是在 animateTo 里改状态的）
        listAnimator = MoveAnimator()
        binding.recycler.itemAnimator = listAnimator
        binding.recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.bottom = (12f * density).toInt()
            }
        })
        setupSwipe()
    }

    /** 左滑露出红色删除按钮，点击后弹出二次确认 */
    private fun setupSwipe() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    pendingDeleteId = adapter.itemAt(pos).id
                    showConfirm(true)
                    // 立即把卡片复位；是否真的删除由确认框决定。
                    // pos 无效时绝不能 notifyItemChanged(-1)，那是把非法位置喂给 AdapterHelper
                    adapter.notifyItemChanged(pos)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }
                val itemView = viewHolder.itemView
                itemView.translationX = dX

                val right = itemView.right.toFloat()
                val left = right + dX
                if (left >= right) return

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                bgPaint.color = theme.danger
                c.drawRect(left, itemView.top.toFloat(), right, itemView.bottom.toFloat(), bgPaint)

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                textPaint.color = Color.WHITE
                textPaint.textSize = 14f * density
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.isFakeBoldText = true
                val label = getString(R.string.swipe_delete)
                // 露出的红色区宽度按文案实测：原来钉死 40dp 只够"删除"两个字，
                // 换英文后 drawText 没有省略号，字会被红色背景直接切掉
                if (right - left > textPaint.measureText(label) + 24f * density) {
                    val fm = textPaint.fontMetrics
                    c.drawText(
                        label,
                        (left + right) / 2f,
                        (itemView.top + itemView.bottom) / 2f - (fm.ascent + fm.descent) / 2f,
                        textPaint
                    )
                }
            }

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 3f
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.4f
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recycler)
    }

    /** 设备类型格子：固定 60x60，排满一行自动换行（与鸿蒙 FlexWrap 一致）；默认只铺一行 */
    private fun setupKindGrid() {
        kindBinder = KindTilesBinder(binding.kindGrid) { key ->
            editKind = key
            // 点格子：底色 / 描边 / 文字色走 200ms Friction 渐变（鸿蒙 animateTo 200）
            kindBinder.select(key, animate = true)
        }
        binding.kindGrid.spacingH = (6f * density).toInt()
        binding.kindGrid.spacingV = (6f * density).toInt()
        kindBinder.build()
        // 宽度定下来之后才能算"一行放得下几个"，并把容器高度钉到一行
        binding.kindGrid.post { computeKindCols() }
        binding.kindGrid.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val w = right - left
            if (w != lastKindWidth) {
                lastKindWidth = w
                computeKindCols()
            }
        }
    }

    private var lastKindWidth: Int = -1

    /** 容器宽度一变就算列数、钉高度（横竖屏切换、折叠屏展开都走这里） */
    private fun computeKindCols() {
        val wDp = binding.kindGrid.width / density
        val cols = if (wDp > 0f) {
            // 66 = 60 的格子 + 6 的间距，和鸿蒙版同一个公式
            Math.max(3, Math.floor(((wDp + 6f) / 66f).toDouble()).toInt())
        } else {
            4
        }
        kindCols = cols
        val lp = binding.kindGrid.layoutParams
        lp.height = targetKindGridH()
        binding.kindGrid.layoutParams = lp
        renderKindToggle(false)
    }

    /** 收起铺 1 行、展开铺满；行高步长 66，最后一行不留底部间距 */
    private fun targetKindGridH(): Int {
        val rows = if (kindExpanded) {
            Math.ceil(DEVICE_KINDS.size.toDouble() / kindCols).toInt()
        } else {
            1
        }
        return ((rows * 66 - 6) * density).toInt()
    }

    private fun setupKindToggle() {
        binding.btnKindMore.pressEffect(0.92f)
        binding.btnKindMore.setOnClickListener { toggleKindExpand() }
    }

    /** 一行就放得下时不给"展开全部" */
    private fun renderKindToggle(animateArrow: Boolean) {
        binding.btnKindMore.visibility =
            if (kindCols < DEVICE_KINDS.size) View.VISIBLE else View.GONE
        binding.tvKindMore.text =
            if (kindExpanded) getString(R.string.editor_kind_collapse)
            else getString(R.string.editor_kind_expand, DEVICE_KINDS.size.toString())
        val target = if (kindExpanded) 0f else 180f
        if (animateArrow) {
            // 鸿蒙版箭头 rotate 写在 .animation(140ms EaseOut) 之上，
            // 走的是这条组件动画，不是外层 260ms 的高度动画
            binding.tvKindMoreArrow.animate()
                .rotation(target)
                .setDuration(140L)
                .setInterpolator(Curves.easeOut)
                .start()
        } else {
            binding.tvKindMoreArrow.rotation = target
        }
    }

    /** 展开 / 收起：容器高度拉 260ms，超出的格子被裁着一层层露出来 */
    private fun toggleKindExpand() {
        kindExpanded = !kindExpanded
        val from = binding.kindGrid.height
        val to = targetKindGridH()
        kindGridAnim?.cancel()
        if (from > 0 && from != to) {
            val va = android.animation.ValueAnimator.ofInt(from, to)
            va.duration = 260L
            va.interpolator = Curves.friction
            va.addUpdateListener {
                val lp = binding.kindGrid.layoutParams
                lp.height = it.animatedValue as Int
                binding.kindGrid.layoutParams = lp
            }
            va.start()
            kindGridAnim = va
        } else {
            val lp = binding.kindGrid.layoutParams
            lp.height = to
            binding.kindGrid.layoutParams = lp
        }
        renderKindToggle(true)
    }

    /** 打开编辑面板时：选中的类型若不在第一行，自动展开，别让用户找不到 */
    private fun ensureKindVisible() {
        val idx = DEVICE_KINDS.indexOfFirst { it.key == editKind }
        kindExpanded = idx >= kindCols
        val lp = binding.kindGrid.layoutParams
        lp.height = targetKindGridH()
        binding.kindGrid.layoutParams = lp
    }

    /** 删除确认框：鸿蒙版写的是 width('80%')，这里按屏宽换算 */
    private fun setupConfirmWidth() {
        val w = resources.displayMetrics.widthPixels
        val lp = binding.confirmCard.layoutParams
        lp.width = (w * 0.8f).toInt()
        binding.confirmCard.layoutParams = lp
    }

    /* ------------------------------ 收藏（最爱） ------------------------------ */

    /**
     * 点设备卡右下角的星标：切换收藏并落库。
     * 只更新这一条数据，不整表重绘 —— 整表重绘会把星标的弹跳动画打断。
     */
    private fun onStarTapped(item: DeviceItem, fav: Boolean) {
        val idx = devices.indexOfFirst { it.id == item.id }
        if (idx < 0) {
            return
        }
        devices[idx] = devices[idx].copy(favorite = fav)
        DeviceStore.save(devices)
        updateFavChip()
        // 收藏数变了可能够到新勋章（心头好 / 宠爱有加）
        checkBadges(announce = true)
        // 开着"只看收藏"时取消收藏，这条要从列表里消失，得整表刷新一次
        if (favOnly && !fav) {
            refreshAll(animateNumbers = false)
        }
    }

    /** 排序栏里的收藏胶囊：文字带收藏数量，选中时是金色 */
    private fun updateFavChip(animate: Boolean = false) {
        val count = devices.count { it.favorite }
        val tv = binding.btnFavOnly
        tv.text = if (count > 0) getString(R.string.sort_fav_count, count.toString()) else getString(R.string.sort_fav)
        // 220ms 对应鸿蒙 setFavOnly 的 animateTo({ duration: 220, curve: Curve.Friction })
        tv.chipTo(
            if (favOnly) theme.gold else theme.chipBg,
            if (favOnly) CardColors.whiteText else theme.gold,
            13f,
            density,
            if (animate) 220L else 0L
        )
    }

    /** 编辑面板里的"收藏为最爱"整行开关 */
    private fun setupFavRow() {
        binding.rowFav.pressEffect(0.92f)
        binding.rowFav.setOnClickListener {
            editFavorite = !editFavorite
            renderFavSwitch(animate = true)
        }
    }

    /**
     * 鸿蒙版这里只有 `animateTo({ duration: 220, curve: Curve.Friction })` 改 editFavorite：
     * 状态文字的配色是插值过去的，图标换资源是硬切，并没有额外的缩放弹跳。
     * 所以这里也只做文字色渐变，不给图标加动画。
     */
    private fun renderFavSwitch(animate: Boolean = false) {
        binding.tvFavState.text =
            if (editFavorite) getString(R.string.editor_fav_on) else getString(R.string.editor_fav_off)
        binding.tvFavState.tintTo(
            if (editFavorite) theme.gold else theme.sub,
            1f,
            if (animate) 220L else 0L
        )
        binding.ivFavSwitch.setImageResource(
            if (editFavorite) R.drawable.ic_star else R.drawable.ic_star_off
        )
    }

    /* ------------------------------ 交互 ------------------------------ */

    private fun setupActions() {
        // 统一加按压动效（按下缩小、松手回弹），替掉系统默认的蓝色水波纹。
        // 鸿蒙版主页上的控件一律按到 0.92、140ms EaseOut 回弹，这里逐项对齐
        press(binding.btnTheme) { toggleTheme() }
        press(binding.btnAdd) { openEditor(null) }
        press(binding.btnEmptyAdd) {
            if (favOnly) {
                // 只看收藏且筛空了：这个按钮的作用是"看全部设备"
                setFavOnly(false)
            } else {
                openEditor(null)
            }
        }

        press(binding.btnByAmount, 0.90f) { setChartMode("amount") }
        press(binding.btnByCount, 0.90f) { setChartMode("count") }
        press(binding.btnToggleChart, 0.90f) { toggleChart() }

        // 收藏胶囊：鸿蒙版在主页上，sc() 是 0.92（只有图表里的小胶囊才是 0.90）
        press(binding.btnFavOnly) { setFavOnly(!favOnly) }

        // 排序方向：「排序」两个字 + 上下双箭头是同一块热区，点哪儿都切正序 / 倒序
        press(binding.sortDirBox) { toggleSortDir() }

        press(binding.sortAvg) { setSort("avg") }
        press(binding.sortPrice) { setSort("price") }
        press(binding.sortDate) { setSort("date") }
        press(binding.sortDays) { setSort("days") }

        // 回顶部圆钮：鸿蒙版不走通用 sc()，是按下 90ms 压到 0.84、松手 210ms 弹回
        binding.btnTop.topPressEffect { if (topShown) 1f else 0.4f }
        binding.btnTop.setOnClickListener {
            scrollToTop()
            playTopLaunch()
        }
        // 置顶那条汇总也可以点，点了同样回顶部（鸿蒙版一致）
        press(binding.stickyBar) { scrollToTop() }

        press(binding.btnEditorClose) { closeEditor() }
        press(binding.btnCancel) { closeEditor() }
        press(binding.btnSave) { onSave() }
        press(binding.tvDate) { pickDate() }

        binding.editorScrim.setOnClickListener { closeEditor() }
        binding.confirmLayer.setOnClickListener { showConfirm(false) }
        // 卡片本身拦截点击，避免点卡片空白处穿透到遮罩而关闭
        binding.confirmCard.setOnClickListener { /* 不处理 */ }
        press(binding.btnConfirmCancel) { showConfirm(false) }
        press(binding.btnConfirmDelete) { confirmDelete() }
        // 页脚那条 GitHub 不带按压缩放：它是一行文字，不是按钮
        // 页脚整行可点（点「我的」两个字也该能打开），行里的「GitHub」另有一个 URLSpan
        binding.tvGithub.movementMethod = LinkMovementMethod.getInstance()
        binding.tvGithub.setOnClickListener { openGitHub() }
    }

    /**
     * 用系统浏览器打开作者的 GitHub。没有能处理 http 的应用时静默失败 ——
     * 与隐私弹窗里那两个链接同一套做法：点不动就是点不动，不该拿一条报错打断用户。
     */
    private fun openGitHub() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        } catch (e: Exception) {
            // 机器上没有可打开的浏览器
        }
    }

    /**
     * 页脚那一行的文字：地址不画出来，挂在「GitHub」这五个字上。
     *
     * 用 URLSpan 而不是只给整行挂点击：无障碍服务会把这几个字单独念成"链接"，
     * 视觉上也有下划线和主色，跟"这行字里藏了个链接"的意图对得上。
     * 万一某条译文里没有 "GitHub" 这个字面量，就退回普通整行文字 ——
     * 行本身的点击还在（setupListeners 里那句 setOnClickListener），不会变成死链。
     */
    private fun githubFooter(t: AppTheme): CharSequence {
        val label = getString(R.string.footer_github)
        val at = label.indexOf(GITHUB_LINK_WORD)
        if (at < 0) return label
        val sp = SpannableString(label)
        sp.setSpan(object : URLSpan(GITHUB_URL) {
            override fun updateDrawState(ds: TextPaint) {
                ds.color = t.primary
                ds.isUnderlineText = true
            }
        }, at, at + GITHUB_LINK_WORD.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    private fun press(view: View, scale: Float = 0.92f, action: () -> Unit) {
        view.pressEffect(scale)
        view.setOnClickListener { action() }
    }

    /** 切换"只看收藏"：汇总统计仍按全部设备算，只是列表过一遍筛 */
    private fun setFavOnly(on: Boolean) {
        if (favOnly == on) return
        favOnly = on
        // 鸿蒙版这里走的是 animateTo 220ms Friction：胶囊不重建，配色是渐变过去的；
        // 列表项的位置变化同样在闭包内，也按 220ms Friction 平滑挪位
        refreshAll(
            animateNumbers = false,
            animateFav = true,
            listMoveMs = 220L,
            listMoveCurve = Curves.friction
        )
        // 与鸿蒙版一致：筛完列表别停在半空
        if (binding.scrollMain.scrollY > 0) scrollToTop()
    }

    private fun setSort(type: String) {
        if (sortType == type) {
            // 点的就是当前这一项：翻方向，不改变排序字段
            toggleSortDir()
            return
        }
        sortType = type
        refreshAll(
            animateNumbers = false,
            animateSort = true,
            listMoveMs = 260L,
            listMoveCurve = Curves.friction
        )
    }

    /** 切换排序方向（点「排序」或箭头，或点当前已选中的那个排序胶囊） */
    private fun toggleSortDir() {
        sortAsc = !sortAsc
        // 鸿蒙版是 animateTo 260ms Friction：箭头不重建，配色渐变过去，不是硬切；
        // 列表项的挪位也在这条闭包里，跟着一起动
        refreshAll(
            animateNumbers = false,
            animateSort = true,
            listMoveMs = 260L,
            listMoveCurve = Curves.friction
        )
    }

    private fun setChartMode(mode: String) {
        if (chartMode == mode) return
        chartMode = mode
        updateChart(animate = true)
        updateModeChips()
    }

    /**
     * 展开 / 收起：和鸿蒙版一样动的是"高度"，不是透明度。
     *
     * 鸿蒙版 ChartCard 是在 `animateTo({ duration: 260, curve: Curve.EaseOut })` 里改 open 的：
     * if 块里的组件没有 transition，所以内容是**瞬间**出现 / 消失的，
     * 真正被动画的是卡片高度 —— 下方的列表在 260ms 内平滑让位。
     */
    private fun toggleChart() {
        chartOpen = !chartOpen
        binding.btnToggleChart.text =
            if (chartOpen) getString(R.string.chart_collapse) else getString(R.string.chart_expand)
        val body = binding.chartBody
        val lp = body.layoutParams as LinearLayout.LayoutParams
        val margin = (10f * density).toInt()
        val opening = chartOpen
        chartAnim?.cancel()

        if (opening) {
            body.visibility = View.VISIBLE
            // 内容不裁剪：鸿蒙版那块也没有 clip，展开时是整块直接露出来的，
            // 高度还没长到位的一小会儿会和下面的列表短暂重叠 —— 和鸿蒙版同一个观感
            body.clipChildren = false
            binding.chartCard.clipChildren = false
            lp.height = 0
            lp.topMargin = 0
            body.layoutParams = lp
            val target = measureChartBody()
            if (target > 0) {
                animateChartHeight(0, 0, target, margin, opening)
            } else {
                lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
                lp.topMargin = margin
                body.layoutParams = lp
                body.clipChildren = true
            }
            // 展开时强制重播扇形：鸿蒙版 Canvas 重新挂载会再走一次 onReady，
            // 动画从头来过；沿用"数据没变就不重播"的判断会让它直接停在终态
            updateChart(animate = true, force = true)
        } else {
            val from = if (body.height > 0) body.height else lp.height
            // 内容立刻消失（INVISIBLE 不参与绘制，占位由下面动画着的固定高度决定）
            body.visibility = View.INVISIBLE
            lp.height = from
            lp.topMargin = margin
            body.layoutParams = lp
            if (from > 0) {
                animateChartHeight(from, margin, 0, 0, opening)
            } else {
                lp.height = LinearLayout.LayoutParams.WRAP_CONTENT
                lp.topMargin = margin
                body.layoutParams = lp
                body.visibility = View.GONE
            }
        }
    }

    /** 按当前宽度量一遍图表区的自然高度（只 measure，不动 layoutParams，免得闪一帧） */
    private fun measureChartBody(): Int {
        val body = binding.chartBody
        val w = if (body.width > 0) body.width else binding.chartCard.width
        if (w <= 0) {
            return 0
        }
        body.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return body.measuredHeight
    }

    /** 高度 + 上边距一起插值，260ms EaseOut（鸿蒙 ChartCard 的 animateTo 参数） */
    private fun animateChartHeight(
        fromH: Int,
        fromM: Int,
        toH: Int,
        toM: Int,
        opening: Boolean
    ) {
        val body = binding.chartBody
        val lp = body.layoutParams as LinearLayout.LayoutParams
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = 260L
        va.interpolator = Curves.easeOut
        va.addUpdateListener {
            val f = it.animatedFraction
            lp.height = (fromH + (toH - fromH) * f).toInt()
            lp.topMargin = (fromM + (toM - fromM) * f).toInt()
            body.layoutParams = lp
        }
        va.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val p = body.layoutParams as LinearLayout.LayoutParams
                p.height = LinearLayout.LayoutParams.WRAP_CONTENT
                p.topMargin = (10f * density).toInt()
                body.layoutParams = p
                if (opening) {
                    body.clipChildren = true
                } else {
                    body.visibility = View.GONE
                }
            }
        })
        va.start()
        chartAnim = va
    }

    /* ------------------------------ 数据刷新 ------------------------------ */

    private fun sortedList(): List<DeviceItem> {
        var list = devices.toMutableList()
        // 只看收藏时先过筛，再排序
        if (favOnly) {
            list = list.filter { it.favorite }.toMutableList()
        }
        // 与鸿蒙版保持一致：只比主排序字段，不做二次排序（两边的排序都是稳定排序）
        // 倒序是默认方向，比较器按"大在前"写，正序时整体取反
        val cmp: Comparator<DeviceItem> = when (sortType) {
            "price" -> compareByDescending<DeviceItem> { it.price }
            "date" -> compareByDescending<DeviceItem> { it.buyDate }
            "days" -> compareByDescending<DeviceItem> { DeviceCalc.daysUsed(it) }
            else -> compareByDescending<DeviceItem> { DeviceCalc.avgPerDay(it) }
        }
        list.sortWith(if (sortAsc) cmp.reversed() else cmp)
        return list
    }

    /**
     * @param animateSort 排序方向切换：箭头配色走 260ms 渐变（对齐鸿蒙 animateTo）
     * @param animateFav  "只看收藏"切换：胶囊配色走 220ms 渐变（同上）
     * @param listMoveMs  列表项挪位的时长，0 表示硬切重排。
     *                    鸿蒙版排序 / 筛选是在 animateTo 闭包里改状态的，
     *                    ArkUI 会把由此引起的位置变化按同一参数做成动画。
     * @param listMoveCurve 列表项挪位的曲线，与上面那条 animateTo 的 curve 对应
     */
    private fun refreshAll(
        animateNumbers: Boolean,
        animateSort: Boolean = false,
        animateFav: Boolean = false,
        listMoveMs: Long = 0L,
        listMoveCurve: Interpolator = Curves.friction
    ) {
        val shown = sortedList()
        if (::listAnimator.isInitialized) {
            listAnimator.configure(listMoveMs, listMoveCurve)
        }
        adapter.submit(shown)

        val empty = shown.isEmpty()
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        // 呼吸动画跟着空态一起开关：以前它在 onCreate 起一次就永远不停，
        // 有数据、甚至界面不可见时仍在以 60fps 重绘一个 GONE 的图标
        if (empty) setupBreath() else stopBreath()
        // 排序栏按"全部设备"判显隐：只看收藏筛空了也得留着，不然切不回来
        binding.sortBar.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        renderEmptyState()

        var total = 0.0
        var avg = 0.0
        var best: DeviceItem? = null
        for (item in devices) {
            total += item.price
            val a = DeviceCalc.avgPerDay(item)
            avg += a
            if (best == null || a < DeviceCalc.avgPerDay(best)) best = item
        }

        binding.tvCountBadge.text =
            resources.getQuantityString(R.plurals.p_in_record, devices.size, devices.size.toString())
        binding.tvStickyCount.text =
            resources.getQuantityString(R.plurals.p_sticky_count, devices.size, devices.size.toString())
        if (best == null) {
            binding.rowBest.visibility = View.GONE
        } else {
            binding.rowBest.visibility = View.VISIBLE
            binding.tvBestName.text = best.name
            binding.tvBestAvg.text = getString(
                R.string.sum_avg_prefix,
                Fmt.smallMoneyLabel(this, DeviceCalc.avgPerDay(best))
            )
        }

        animateNumbers(total, avg, animateNumbers)
        updateChart(animate = true)
        updateSortChips(animateSort)
        updateModeChips()
        updateFavChip(animateFav)
        // 设备被删空时，置顶条要跟着收起
        binding.root.post { updateSticky(binding.scrollMain.scrollY) }
    }

    /** 空态文案随"只看收藏"切换，按钮也跟着换成"看全部设备" */
    private fun renderEmptyState() {
        val b = binding
        if (favOnly) {
            b.tvEmptyTitle.text = getString(R.string.empty_fav_title)
            b.tvEmptyDesc1.text = getString(R.string.empty_fav_desc1)
            b.tvEmptyDesc2.text = getString(R.string.empty_fav_desc2)
            b.btnEmptyAdd.text = getString(R.string.empty_fav_action)
        } else {
            b.tvEmptyTitle.text = getString(R.string.empty_all_title)
            b.tvEmptyDesc1.text = getString(R.string.empty_all_desc1)
            b.tvEmptyDesc2.text = getString(R.string.empty_all_desc2)
            b.btnEmptyAdd.text = getString(R.string.empty_all_action)
        }
    }

    private fun animateNumbers(total: Double, avg: Double, animate: Boolean) {
        if (!animate) {
            // 已经在滚向同样的目标值就别打断。切排序、切收藏这类操作不改变金额，
            // 鸿蒙版 refreshStats 判断数值没变就不会更新 @State，滚动照旧播完；
            // 这边若照样 cancel，数字会"啪"地跳到终态。
            if (numberAnimator?.isRunning == true &&
                numberTargetTotal == total && numberTargetAvg == avg
            ) {
                return
            }
            numberAnimator?.cancel()
            displayTotal = total
            displayAvg = avg
            renderNumbers()
            return
        }
        numberAnimator?.cancel()
        numberTargetTotal = total
        numberTargetAvg = avg
        val fromTotal = displayTotal
        val fromAvg = displayAvg
        // 首次进场：鸿蒙 SummaryCard 是 60ms 延迟 + 900ms；之后每次数据变化是 720ms
        val first = !firstNumberPlayed
        firstNumberPlayed = true
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = if (first) 900L else 720L
        va.startDelay = if (first) 60L else 0L
        va.interpolator = Curves.easeOut
        va.addUpdateListener {
            val t = it.animatedValue as Float
            displayTotal = fromTotal + (total - fromTotal) * t
            displayAvg = fromAvg + (avg - fromAvg) * t
            renderNumbers()
        }
        va.start()
        numberAnimator = va
    }

    private fun renderNumbers() {
        binding.tvTotalMoney.text = Fmt.moneyLabel(this, displayTotal)
        binding.tvAvgMoney.text = Fmt.smallMoneyLabel(this, displayAvg)
        binding.tvStickyTotal.text = Fmt.moneyLabel(this, displayTotal)
        binding.tvStickyAvg.text =
            getString(R.string.sticky_avg, Fmt.smallMoneyLabel(this, displayAvg))
    }

    /**
     * @param force 展开图表时传 true。鸿蒙版收起再展开，Canvas 会重新挂载并走 onReady，
     *              扇形会重新播放 820ms 的展开动画；这里若沿用"数据没变就不重播"的判断，
     *              展开时扇形会直接是终态，和鸿蒙版对不上。
     */
    private fun updateChart(animate: Boolean, force: Boolean = false) {
        val slices = ChartData.slices(this, devices, chartMode == "amount")
        val changed = force || slices != lastSlices
        if (!changed) {
            // 数据没变就整个跳过。早先这里仍会走 setData(animate = false)，
            // 而 setData 不带动画时会 cancel 掉正在播的展开动画并把 progress 直接置 1 ——
            // 于是进场那次 820ms 的扇形展开刚起跑就被掐断，看着像"没有动画"。
            return
        }
        lastSlices = slices
        binding.pieChart.setData(slices, chartMode, animate)
        buildLegend(slices)
    }

    private fun buildLegend(slices: List<PieSlice>) {
        val box = binding.legendBox
        box.removeAllViews()
        if (slices.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.chart_empty_legend)
            tv.textSize = 12f
            tv.setTextColor(theme.sub)
            box.addView(tv)
            return
        }
        val total = ChartData.total(slices)
        // 百分比统一由「最大余数法」分配，整列加起来精确等于 100%
        val pct = ChartData.percents(slices)
        val inflater = layoutInflater
        for (i in slices.indices) {
            val s = slices[i]
            val row = inflater.inflate(R.layout.item_legend, box, false)
            val dot = row.findViewById<View>(R.id.legendDot)
            val name = row.findViewById<TextView>(R.id.tvLegendName)
            val value = row.findViewById<TextView>(R.id.tvLegendValue)
            val percent = row.findViewById<TextView>(R.id.tvLegendPercent)
            dot.background = circleDrawable(safeColor(s.c))
            name.text = s.n
            name.setTextColor(theme.text2)
            value.text = if (chartMode == "amount") {
                Fmt.moneyLabel(this, s.v)
            } else {
                val n = Math.round(s.v).toInt()
                resources.getQuantityString(R.plurals.p_sticky_count, n, n.toString())
            }
            value.setTextColor(theme.text)
            percent.text = if (total > 0) (pct[i].toString() + "%") else "0%"
            percent.setTextColor(theme.sub)
            box.addView(row)
        }
    }

    private fun safeColor(hex: String): Int =
        runCatching { Color.parseColor(hex) }.getOrDefault(Color.GRAY)

    /**
     * @param animate 只让"方向箭头"渐变。四个排序胶囊保持硬切——
     *                鸿蒙那边它们的 ForEach key 里带了方向和选中态，一切换就重建组件，
     *                本来就没有过渡，两边对齐后才是同一个观感。
     */
    private fun updateSortChips(animate: Boolean = false) {
        // 正序时文案跟着反过来，免得"日均最高"配上一行从低到高的数据自相矛盾
        binding.sortAvg.text =
            if (sortAsc) getString(R.string.sort_avg_asc) else getString(R.string.sort_avg_desc)
        binding.sortPrice.text =
            if (sortAsc) getString(R.string.sort_price_asc) else getString(R.string.sort_price_desc)
        binding.sortDate.text =
            if (sortAsc) getString(R.string.sort_date_asc) else getString(R.string.sort_date_desc)
        binding.sortDays.text =
            if (sortAsc) getString(R.string.sort_days_asc) else getString(R.string.sort_days_desc)
        setChip(binding.sortAvg, sortType == "avg", 13f)
        setChip(binding.sortPrice, sortType == "price", 13f)
        setChip(binding.sortDate, sortType == "date", 13f)
        setChip(binding.sortDays, sortType == "days", 13f)
        updateSortDirIcon(animate)
        // 换方向时四个胶囊的文案长短会变（"日均最高"↔"日均最低"），宽度一变，
        // 各自离这一行中心的距离就跟着变，得把它们的转角重算一遍
        if (animate) {
            binding.sortScroller.post { refreshChipFx() }
        }
    }

    /** 上一次落到箭头上的颜色：用来判断这次刷新到底有没有必要动它们 */
    private var sortDirUpColor: Int = 0
    private var sortDirDownColor: Int = 0
    /** 上下箭头整组的累计翻转角：每次换方向加 180°，一直往同一个方向转 */
    private var sortFlipDeg: Float = 0f
    /** 整组翻过半圈之后，写在上面的那个三角其实落到了下面：配色要按"翻完之后谁在上面"给 */
    private var sortFlipped: Boolean = false

    /** 上下箭头：当前方向的那一个点亮（主色 + 不透明），另一个压暗 */
    private fun updateSortDirIcon(animate: Boolean = false) {
        // 整组绕 X 轴翻半圈后，两个三角是互换位置的：写在上头的那个会转到下头去，
        // 顺带把自己镜像成朝下的形状。所以"点亮哪一个"要看翻完之后谁落在上面，
        // 而不是看谁被写在布局前面 —— 不然无论切几次方向，亮着的总是下面那一个。
        if (animate) sortFlipped = !sortFlipped
        val upOnTop = !sortFlipped
        val upActive = (upOnTop == sortAsc)
        val upC = if (upActive) theme.primary else theme.sub
        val downC = if (upActive) theme.sub else theme.primary
        // 颜色和上次完全一样就别碰：过渡会先 cancel 掉正在播的那一半，
        // 切收藏、点赞星标这类无关刷新会把 260ms 的箭头渐变掐断。
        // 换肤时颜色会变，判断自然放行。
        if (!animate && upC == sortDirUpColor && downC == sortDirDownColor) {
            return
        }
        sortDirUpColor = upC
        sortDirDownColor = downC
        // 260ms 对应鸿蒙 toggleSortDir 的 animateTo({ duration: 260, curve: Curve.Friction })
        val d = if (animate) 260L else 0L
        binding.arrowSortUp.arrowTo(upC, if (upActive) 1f else 0.45f, d)
        binding.arrowSortDown.arrowTo(downC, if (upActive) 0.45f else 1f, d)
        if (animate) {
            // 整组翻半圈：两个三角在 X 轴上转着交接，同时完成"点亮的那一个换手"
            doSortFlip()
        }
    }

    /**
     * 箭头组绕 X 轴转半圈。用 rotateX 而不是换位置：
     * 位置换过去只是"跳一下"，转过去才看得出这两个三角是同一组东西的两面。
     * 透视距离按 dp 给，不然 3D 旋转会被压成单纯的纵向压缩，没有厚度。
     */
    private fun doSortFlip() {
        val group = binding.arrowsSort
        group.cameraDistance = 1200f * density
        sortFlipDeg += 180f
        group.animate()
            .rotationX(sortFlipDeg)
            .setDuration(260L)
            .setInterpolator(Curves.friction)
            .setListener(null)
            .start()
    }

    private fun updateModeChips() {
        setChip(binding.btnByAmount, chartMode == "amount", 11f)
        setChip(binding.btnByCount, chartMode == "count", 11f)
    }

    private fun setChip(tv: TextView, selected: Boolean, radiusDp: Float) {
        tv.background = roundedDrawable(if (selected) theme.primary else theme.chipBg, radiusDp, density)
        tv.setTextColor(if (selected) theme.onPrimary else theme.chipText)
    }

    /* ------------------------------ 编辑面板 ------------------------------ */

    /** 按 id 回查在册列表里最新的那份 */
    private fun findLive(id: String): DeviceItem? = devices.firstOrNull { it.id == id }

    private fun openEditor(src: DeviceItem?) {
        binding.tvError.visibility = View.GONE
        // 传进来的可能是刚才点过星标之前的旧对象：onStarTapped 只换 devices 里的元素、
        // 不向适配器重新提交列表，卡片点击回调给出的还是收藏前那份。
        // 一律按 id 回查在册的最新一份，否则面板回显旧收藏状态、一保存就把刚点的收藏抹掉。
        val item = src?.let { findLive(it.id) ?: it }
        if (item == null) {
            editingId = null
            binding.tvEditorTitle.text = getString(R.string.editor_title_add)
            binding.etName.setText("")
            binding.etPrice.setText("")
            binding.etNote.setText("")
            editKind = "phone"
            editFavorite = false
            editDate.timeInMillis = System.currentTimeMillis()
        } else {
            editingId = item.id
            binding.tvEditorTitle.text = getString(R.string.editor_title_edit)
            binding.etName.setText(item.name)
            binding.etPrice.setText(plainPrice(item.price))
            binding.etNote.setText(item.note)
            editKind = item.category
            editFavorite = item.favorite
            editDate.timeInMillis = DateUtil.toCalendar(item.buyDate).timeInMillis
        }
        renderFavSwitch()
        kindBinder.select(editKind)
        // 选中的类型若不在第一行，先把类型区展开，别让用户找不到
        ensureKindVisible()
        renderDateText()
        showEditor(true)
    }

    private fun showEditor(show: Boolean) {
        val layer = binding.editorLayer
        val sheet = binding.editorSheet
        // 未布局时 height 为 0，退回用根布局高度做位移距离
        val h = if (sheet.height > 0) sheet.height.toFloat() else binding.root.height.toFloat()
        if (show) {
            layer.visibility = View.VISIBLE
            layer.alpha = 0f
            sheet.translationY = h
            // 鸿蒙版 openEditor 是在 `animateTo({ duration: 280, curve: Curve.EaseOut })` 里
            // 把 showEditor 置 true 的。animateTo 的参数会覆盖 transition 上写的 220 / 300，
            // 所以遮罩淡入和面板上移都收在 280ms EaseOut 里
            layer.animate().alpha(1f).setDuration(280L).setInterpolator(Curves.easeOut).start()
            sheet.animate()
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(Curves.easeOut)
                .start()
        } else {
            hideKeyboard()
            // 关闭：鸿蒙版 closeEditor 用的是 animateTo 240ms EaseIn，
            // 它会覆盖 transition 的参数，所以遮罩和面板都收在 240ms EaseIn 里
            layer.animate().alpha(0f).setDuration(240L).setInterpolator(Curves.easeIn).start()
            sheet.animate()
                .translationY(h)
                .setDuration(240L)
                .setInterpolator(Curves.easeIn)
                .withEndAction { layer.visibility = View.GONE }
                .start()
        }
    }

    private fun closeEditor() {
        showEditor(false)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private fun renderDateText() {
        // 存储格式恒为 yyyy-MM-dd，这里只是给人看的一层：英文显示 Mar 5, 2026
        binding.tvDate.text = DateUtil.displayDate(
            this,
            DateUtil.format(
                editDate.get(Calendar.YEAR),
                editDate.get(Calendar.MONTH) + 1,
                editDate.get(Calendar.DAY_OF_MONTH)
            )
        )
    }

    private fun pickDate() {
        datePickerDialog?.dismiss()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                editDate.set(year, month, day)
                renderDateText()
            },
            editDate.get(Calendar.YEAR),
            editDate.get(Calendar.MONTH),
            editDate.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog = dialog
        dialog.setOnDismissListener {
            if (datePickerDialog === dialog) datePickerDialog = null
        }
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    /**
     * 回填输入框用的价格串。
     *
     * `Double.toString` 从 1e7 起改用 E 记法：12345678.9 会原样变成 `1.23456789E7`
     * 显示在价格框里（整数那侧原先靠 `toLong()` 躲开了，带小数的没躲开）。
     * 一律走 BigDecimal 的 plain 形式，顺手把尾零收掉（1200.0 → 1200）。
     */
    private fun plainPrice(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return ""
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
    }

    private fun onSave() {
        val name = binding.etName.text.toString().trim()
        val priceStr = binding.etPrice.text.toString().trim()
        val price = Fmt.parsePrice(priceStr)
        val buyDate = DateUtil.format(
            editDate.get(Calendar.YEAR),
            editDate.get(Calendar.MONTH) + 1,
            editDate.get(Calendar.DAY_OF_MONTH)
        )

        // 比较用的 buyDate / today() 都是 yyyy-MM-dd 定长串，字典序即时间序，与语言无关
        val error: String? = when {
            name.isEmpty() -> getString(R.string.err_name)
            priceStr.isEmpty() -> getString(R.string.err_price_empty)
            price == null || price <= 0.0 || price.isNaN() || price.isInfinite() ||
                price > DeviceCalc.MAX_PRICE ->
                // 上限必须与 DeviceCalc.normalize 的钳制同一条：否则一个 13 位的价格
                // 能录进来、当场显示正常，杀掉进程重开却被清洗规则静默归零（数据没了）
                getString(R.string.err_price_invalid)
            buyDate > DateUtil.today() -> getString(R.string.err_date_future)
            else -> null
        }
        if (error != null) {
            showError(error)
            return
        }
        val safePrice = price ?: return
        val note = binding.etNote.text.toString().trim()

        // 增删设备之前留一份快照。用带 60 秒节流的 maybeBackup 而不是 forceBackup：
        // 连着改 5 台就把 5 个槽位全刷成同一分钟，等于没有回退空间（鸿蒙端一直是这么做的）
        BackupStore.maybeBackup(this, currentSnapshot())

        val id = editingId
        if (id == null) {
            devices.add(
                DeviceItem(
                    id = DeviceStore.newId(),
                    name = name,
                    price = safePrice,
                    buyDate = buyDate,
                    category = editKind,
                    note = note,
                    favorite = editFavorite,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            val idx = devices.indexOfFirst { it.id == id }
            if (idx >= 0) {
                devices[idx] = devices[idx].copy(
                    name = name,
                    price = safePrice,
                    buyDate = buyDate,
                    category = editKind,
                    note = note,
                    favorite = editFavorite
                )
            }
        }

        DeviceStore.save(devices)
        refreshAll(animateNumbers = true)
        showEditor(false)
        // 新记一台设备就可能够到新勋章，这里立刻判一次并报出来
        checkBadges(announce = true)
    }

    /**
     * 校验提示：鸿蒙版是直接把 errMsg 塞进 if 块里显示的（没包 animateTo），
     * 所以这里也是硬切出现，不额外加淡入 / 位移。
     */
    private fun showError(msg: String) {
        binding.tvError.animate().cancel()
        binding.tvError.alpha = 1f
        binding.tvError.translationX = 0f
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    /* ------------------------------ 删除确认 ------------------------------ */

    /**
     * @param closeMs 关闭时长：点遮罩 / 取消是 180ms，点"删除"确认是 200ms。
     *                鸿蒙版分别写在两处 animateTo 里，曲线都是 EaseIn。
     */
    private fun showConfirm(show: Boolean, closeMs: Long = 180L) {
        val layer = binding.confirmLayer
        if (show) {
            val target = devices.firstOrNull { it.id == pendingDeleteId }
            binding.tvDeleteName.text = target?.name ?: ""
            layer.visibility = View.VISIBLE
            // 鸿蒙版只有一条 TransitionEffect.OPACITY 200ms（默认 curve = EaseInOut），
            // 卡片本身没有缩放动画，所以这里也不给卡片加弹入
            layer.alpha = 0f
            binding.confirmCard.scaleX = 1f
            binding.confirmCard.scaleY = 1f
            layer.animate().alpha(1f).setDuration(200L).setInterpolator(Curves.easeInOut).start()
        } else {
            pendingDeleteId = null
            layer.animate()
                .alpha(0f)
                .setDuration(closeMs)
                .setInterpolator(Curves.easeIn)
                .withEndAction { layer.visibility = View.GONE }
                .start()
        }
    }

    /**
     * 删除：设备不直接消失，而是整体挪进回收站，同时给 5 秒撤销。
     * 就算没点撤销，它也还在回收站里躺 30 天，随时能原样取回。
     */
    private fun confirmDelete() {
        val id = pendingDeleteId ?: return
        val idx = devices.indexOfFirst { it.id == id }
        if (idx < 0) {
            showConfirm(false, closeMs = 200L)
            return
        }
        // 动数据之前先留一份快照，真想回退还有得退（同样走 60 秒节流）
        BackupStore.maybeBackup(this, currentSnapshot())

        val item = devices.removeAt(idx)
        trash.add(0, TrashItem(item, System.currentTimeMillis()))
        DeviceStore.save(devices)
        DeviceStore.saveTrash(trash)
        lastDeletedId = id

        // 鸿蒙版 confirmDelete 里关弹窗用的是 animateTo 200ms EaseIn
        showConfirm(false, closeMs = 200L)
        // 列表的增删不在 animateTo 里（鸿蒙是先改 deviceList 再 animateTo），所以硬切
        refreshAll(animateNumbers = true)
        updateBadgeEntry()
        undoName = nameOf(item)
        undoBar?.show(getString(R.string.undo_deleted, undoName)) { undoDelete() }
    }

    private fun undoDelete() {
        val id = lastDeletedId ?: return
        lastDeletedId = null
        restoreFromTrash(id)
    }

    /* ------------------------------ 数据与备份 ------------------------------ */

    private fun setupDataHub() {
        undoBar = UndoBar(binding.undoBar, binding.tvUndoText, binding.btnUndo)
        // 导出 / 导入都交给系统文件选择器，应用自己不申请任何存储权限
        exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) writeSnapshotTo(uri)
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readSnapshotFrom(uri)
        }
        press(binding.btnHub) { openDataSheet() }
        press(binding.btnBadgeEntry) { openBadgeWall() }
    }

    private fun currentSnapshot(): BackupStore.Snapshot =
        BackupStore.Snapshot(devices.toMutableList(), trash.toMutableList(), badgeState.toMutableMap())

    /** 把一份数据整体落在本机：设备、回收站、勋章一起写，再整屏刷新 */
    private fun applySnapshot(s: BackupStore.Snapshot) {
        devices.clear()
        devices.addAll(s.devices)
        trash.clear()
        // 同一台设备不能既在册又在回收站，否则恢复一次就多出一台重复项
        trash.addAll(s.recycle.filterNot { t -> devices.any { it.id == t.item.id } })
        // 勋章只增不减：整体替换会让那次快照之后点亮的勋章凭空消失，
        // 重新判定虽然能再点亮，达成日期却被改写成今天，原始时间就丢了。
        // 所以这里是并入而非覆盖，同一枚取更早的达成时间。
        for ((id, at) in s.badges) {
            val has = badgeState[id]
            if (has == null || at < has) badgeState[id] = at
        }
        DeviceStore.save(devices)
        DeviceStore.saveTrash(trash)
        DeviceStore.saveBadges(badgeState)
        refreshAll(animateNumbers = true)
        updateBadgeEntry()
        checkBadges(announce = false)
    }

    private fun openDataSheet() {
        hubDialog = DataSheet.show(
            this, theme,
            {
                DataSheet.DataInfo(
                    devices.size, trash.size, badgeState.size, BackupStore.listBackups(this),
                    L10n.preferred()
                )
            },
            DataSheet.DataActions(
                onExportFile = { exportLauncher.launch(BackupStore.fileName()) },
                onShare = { shareSnapshot() },
                // 不按 mime 过滤：部分 ROM 把 .json 报成 octet-stream，
                // 挑 MIME 会让选择器连用户自己刚导出的那份都不显示。
                // 认不认得出是 parseJson 的事，读不出来会明确提示"这个文件里没有设备账数据"。
                onImportPick = { importLauncher.launch(arrayOf("*/*")) },
                onRestoreBackup = { slot -> askRestoreBackup(slot) },
                onOpenTrash = { openTrashSheet() },
                onOpenBadges = { openBadgeWall() },
                onPickLanguage = { tag -> pickLanguage(tag) }
            )
        )
    }

    /**
     * 换语言：落库 → 关掉面板 → 让系统投递配置变更。切完直接回到主页面看列表，
     * 不再把面板重开一次（作者定的交互；旧做法会让面板闪一下、滚动位置弹回顶部）。
     *
     * 这里刻意不自己调 onConfigurationChanged：setApplicationLocales 之后 Resources
     * 要等系统投递配置变更那一刻才换过来，抢在前面刷新会用旧语言把界面重搭一遍。
     *
     * "点的语言正好是当前生效那门"这种情况（设备是简中，用户从「跟随系统」改点「简体中文」）
     * 不需要任何补救：系统不投递配置变更，界面本来就没有要换的文字，面板照旧关掉就行。
     * 旧代码在这里要自己重开面板，只是为了把勾挪到新选项上——现在面板不在了，那个理由消失。
     */
    private fun pickLanguage(tag: String) {
        if (L10n.preferred() == tag) return
        DeviceStore.saveLanguage(tag)
        hubDialog?.dismiss()
        hubDialog = null
        L10n.apply(applicationContext)
    }

    /**
     * 语言变了就重刷所有写过的文案，而不是重建 Activity。
     *
     * AndroidManifest 里把 locale 放进了 configChanges：不拦的话切一次语言
     * 就要销毁重建，而本界面有 50 多个状态字段、一行 onSaveInstanceState 都没有，
     * 重建会把排序、只看收藏、图表开合、正在编辑的设备、开着的面板全部抹掉。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L10n.syncCurrent(this)
        val now = L10n.lang()
        if (now == renderedLang) {
            // 键盘弹出、深色切换、字体大小、屏幕尺寸都会走到这里，而那些跟语言无关：
            // Resources 还是那门语言，文案一个字都不用重取。全套重刷反倒会把扇形图的
            // 展开动画掐成终态、把 41 个类型格子整体重建一遍。
            return
        }
        renderStaticTexts()
        if (::kindBinder.isInitialized) kindBinder.build()
        updateSortChips()
        updateFavChip()
        renderFavSwitch()
        renderKindToggle(animateArrow = false)
        binding.btnToggleChart.text =
            if (chartOpen) getString(R.string.chart_collapse) else getString(R.string.chart_expand)
        refreshAll(animateNumbers = false, animateSort = false, animateFav = false)
        // 卡片文字与图例都是绑定时现取的，而 DiffUtil 只比设备数据、不含语言，
        // 不显式重绑就会留下"界面已是新语言、卡片还是旧语言"的半吊子状态
        adapter.rebindTexts()
        updateChart(animate = false, force = true)
        updateBadgeEntry()
        // 编辑面板是常驻的一层，不会被面板重开顺带刷掉：标题与日期显示都得自己重取
        if (binding.editorLayer.visibility == View.VISIBLE) {
            binding.tvEditorTitle.text = getString(
                if (editingId == null) R.string.editor_title_add else R.string.editor_title_edit
            )
            renderDateText()
        }
        // 撤销条的文案驻留 5 秒，删除后立刻进面板切语言就会整段留着旧语言
        if (undoBar?.isShown == true) {
            undoBar?.retext(getString(R.string.undo_deleted, undoName))
        }
        // 勋章弹屏：底部提示与页码是 show() 那一刻定型的，语言变了得重取一遍
        if (::celebration.isInitialized) {
            celebration.retext()
        }
        // 面板是 show() 那一刻用当时的字符串搭好的，语言一变它就是半中半英，而没人会再读第二遍。
        // 现在的规则是"切完语言回到主页面"：谁开着就关掉谁 —— 数据面板、回收站、勋章墙、
        // 导入与各个确认框都挂在 hubDialog 这一层上，一次 dismiss 全 cover 住。
        hubDialog?.dismiss()
        hubDialog = null
    }

    /**
     * 把布局里写死的文案重新赋一遍值。
     *
     * 两件事都只能在这里做：
     * 一是 `android:text="@string/x"` 是 inflate 那一刻解析的，我们不重建 Activity，
     *    所以切语言后那些标签会永远停在旧语言（实测就是标题还写着"设备账"而卡片已经是"3 devices"）；
     * 二是带参数的标签（购买价格（元））在 XML 里根本引用不了。
     * 内容描述也一起刷，读屏软件拿到的才不是上一门语言。
     */
    private fun renderStaticTexts() {
        val b = binding
        b.tvTitle.setText(R.string.app_name)
        b.tvSlogan.setText(R.string.top_slogan)
        b.btnAdd.setText(R.string.top_add)
        b.ivHubIcon.setContentDescription(getString(R.string.cd_hub))
        b.ivThemeIcon.setContentDescription(getString(R.string.cd_theme))
        b.btnBadgeEntry.setContentDescription(getString(R.string.cd_badges))
        b.tvSummaryLabel.setText(R.string.sum_total_label)
        b.tvAvgLabel.setText(R.string.sum_avg_label)
        b.tvBestTag.setText(R.string.sum_best_label)
        b.tvChartTitle.setText(R.string.chart_title)
        b.tvChartSub.setText(R.string.chart_sub)
        b.btnByAmount.setText(R.string.chart_by_amount)
        b.btnByCount.setText(R.string.chart_by_count)
        b.tvSortLabel.setText(R.string.sort_label)
        // 页脚：地址不显示，作为超链接挂在「GitHub」上；换语言要重画一次 span
        b.tvGithub.text = githubFooter(theme)
        b.ivEmpty.setContentDescription(getString(R.string.cd_empty))
        b.ivTop.setContentDescription(getString(R.string.cd_top))
        b.btnUndo.setText(R.string.undo_button)
        b.btnEditorClose.setText(R.string.editor_close)
        b.tvLabelName.setText(R.string.editor_name_label)
        b.etName.setHint(R.string.editor_name_hint)
        b.etPrice.setHint(R.string.editor_price_hint)
        b.tvLabelPrice.text = getString(R.string.editor_price_label, getString(R.string.fmt_currency))
        b.tvLabelKind.setText(R.string.editor_kind_label)
        b.tvLabelDate.setText(R.string.editor_date_label)
        b.tvLabelNote.setText(R.string.editor_note_label)
        b.etNote.setHint(R.string.editor_note_hint)
        b.tvFavLabel.setText(R.string.editor_fav_label)
        b.ivFavSwitch.setContentDescription(getString(R.string.cd_favorite_toggle))
        b.btnCancel.setText(R.string.editor_cancel)
        b.btnSave.setText(R.string.editor_save)
        b.tvConfirmTitle.setText(R.string.delete_title)
        b.btnConfirmCancel.setText(R.string.delete_cancel)
        b.btnConfirmDelete.setText(R.string.delete_confirm)

        // 这几处用的是复数资源与带参资源，XML 里引用不了，只能留 ASCII 占位。
        // 它们原先要等数据加载完才被赋值，于是首次启动的条款弹窗后面会露出中文占位
        // （英文界面上飘一个"0 台在册"）。这里按当前状态先刷一遍，
        // 稍后 refreshAll 会用真实数字覆盖，两边不冲突。
        val n = devices.size
        b.tvCountBadge.text = resources.getQuantityString(R.plurals.p_in_record, n, n.toString())
        b.tvStickyCount.text = resources.getQuantityString(R.plurals.p_sticky_count, n, n.toString())
        b.tvStickyAvg.text = getString(R.string.sticky_avg, Fmt.smallMoneyLabel(this, 0.0))
        b.tvTotalMoney.text = Fmt.moneyLabel(this, 0.0)
        b.tvAvgMoney.text = Fmt.smallMoneyLabel(this, 0.0)
        b.tvBadgeEntryCount.text =
            getString(R.string.sum_badge_progress, badgeState.size.toString(), Badges.ALL.size.toString())
        // 走到这里，布局里写死的那批文案已经全部按当前语言重取过了，
        // 把这个状态记下来，onConfigurationChanged 才知道哪些配置变更其实不用刷
        renderedLang = L10n.lang()
    }

    private fun openBadgeWall() {
        hubDialog = BadgeWall.show(this, theme) { badgeState }
    }

    private fun openTrashSheet() {
        hubDialog = TrashSheet.show(
            this, theme,
            { trash.toList() },
            TrashSheet.TrashActions(
                onRestore = { id -> restoreFromTrash(id) },
                onDeleteForever = { id -> deleteForever(id) },
                onClear = { askClearTrash() }
            )
        )
    }

    /** 把当前数据写进用户挑好的位置 */
    private fun writeSnapshotTo(uri: Uri) {
        val text = BackupStore.buildJson(currentSnapshot())
        val ok = try {
            // openOutputStream 允许返回 null（部分 provider、某些 ROM 上确实如此）。
            // 原先是 `?.use{...}` 之后紧跟一句独立的 true —— 拿到 null 时一个字节都没写，
            // 却照样告诉用户"已导出 N 台设备"。成功与否必须绑在真的拿到流上。
            val out = contentResolver.openOutputStream(uri)
            if (out == null) {
                false
            } else {
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (e: Exception) {
            false
        }
        Toast.makeText(
            this,
            if (ok) getString(R.string.import_exported, devices.size.toString())
            else getString(R.string.import_export_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun readSnapshotFrom(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        val snap = text?.let { BackupStore.parseJson(it) }
        if (snap == null) {
            Toast.makeText(this, getString(R.string.import_unreadable), Toast.LENGTH_SHORT).show()
            return
        }
        askImport(snap)
    }

    /** 导入前先问清楚怎么并：合并是追加不重复的，覆盖是整体替换 */
    private fun askImport(snap: BackupStore.Snapshot) {
        val ctx = this
        hubDialog = SheetKit.show(this, theme, getString(R.string.import_title)) { body, dismiss ->
            val msg = SheetKit.note(
                ctx, theme,
                getString(
                    R.string.import_question,
                    snap.devices.size.toString(), snap.recycle.size.toString(), snap.badges.size.toString()
                )
            )
            msg.setTextColor(theme.text2)
            body.addView(msg)

            val cancel = SheetKit.button(ctx, theme, getString(R.string.import_cancel), false) { dismiss() }
            val overwrite = SheetKit.button(ctx, theme, getString(R.string.import_overwrite), false) {
                dismiss()
                // 覆盖不可逆：留底没写成就必须停下来，不能让用户以为"当前数据已经存好了"
                if (!BackupStore.forceBackup(this, currentSnapshot())) {
                    Toast.makeText(this, getString(R.string.import_rollback_overwrite), Toast.LENGTH_LONG).show()
                    return@button
                }
                applySnapshot(snap)
                Toast.makeText(this, getString(R.string.import_overwritten), Toast.LENGTH_SHORT).show()
            }
            val merge = SheetKit.button(ctx, theme, getString(R.string.import_merge), true) {
                dismiss()
                if (!BackupStore.forceBackup(this, currentSnapshot())) {
                    Toast.makeText(this, getString(R.string.import_rollback_import), Toast.LENGTH_LONG).show()
                    return@button
                }
                val before = devices.size
                applySnapshot(BackupStore.merge(currentSnapshot(), snap))
                // 报"新增了几台"，而不是文件里的台数——后者会把没并进来的重复项也算成已导入
                val added = devices.size - before
                Toast.makeText(
                    this,
                    if (added > 0) getString(R.string.import_done, added.toString())
                    else getString(R.string.import_nothing_new),
                    Toast.LENGTH_SHORT
                ).show()
            }
            val row = SheetKit.buttonRow(ctx, cancel, overwrite, merge)
            row.gravity = Gravity.END
            body.addView(row)
        }
    }

    /** 走系统分享把数据文件交给别的应用，同样不需要存储权限 */
    private fun shareSnapshot() {
        val file = BackupStore.writeExportFile(this, currentSnapshot())
        val uri = if (file != null) {
            try {
                FileProvider.getUriForFile(this, applicationContext.packageName + ".fileprovider", file)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (uri == null) {
            Toast.makeText(this, getString(R.string.import_share_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.import_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_no_receiver), Toast.LENGTH_SHORT).show()
        }
    }

    private fun askRestoreBackup(slot: Int) {
        val snap = BackupStore.readBackup(this, slot)
        if (snap == null) {
            Toast.makeText(this, getString(R.string.import_snapshot_unreadable), Toast.LENGTH_SHORT).show()
            return
        }
        hubDialog = ConfirmSheet.show(
            this, theme, getString(R.string.restore_title),
            getString(
                R.string.restore_message,
                devices.size.toString(), snap.devices.size.toString(), DateUtil.formatClock(this, snap.exportedAt)
            ),
            getString(R.string.restore_confirm)
        ) {
            // 确认文案承诺了"当前数据会先自动留一份快照"，留底失败就不能真换
            if (!BackupStore.forceBackup(this, currentSnapshot())) {
                Toast.makeText(this, getString(R.string.import_rollback_restore), Toast.LENGTH_LONG).show()
                return@show
            }
            applySnapshot(snap)
            Toast.makeText(this, getString(R.string.restore_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun askClearTrash() {
        if (trash.isEmpty()) return
        hubDialog = ConfirmSheet.show(
            this, theme, getString(R.string.clear_trash_title),
            getString(R.string.clear_trash_message, trash.size.toString()),
            getString(R.string.clear_trash_confirm)
        ) {
            hubDialog?.dismiss()
            hubDialog = null
            trash.clear()
            DeviceStore.saveTrash(trash)
            openTrashSheet()
        }
    }

    /** 回收站里的恢复 / 彻底删除 */
    private fun restoreFromTrash(id: String) {
        val idx = trash.indexOfFirst { it.item.id == id }
        if (idx < 0) return
        // 同 id 已经在册（多半是跨端合并留下的）：再塞一次列表里就有两条同 id，
        // 汇总双算、DiffUtil 键重复、下次合并静默丢一条。只把它从回收站清掉。
        if (devices.any { it.id == id }) {
            trash.removeAt(idx)
            DeviceStore.saveTrash(trash)
            refreshAll(animateNumbers = false)
            Toast.makeText(this, getString(R.string.import_duplicate), Toast.LENGTH_SHORT).show()
            return
        }
        val t = trash.removeAt(idx)
        devices.add(t.item)
        DeviceStore.save(devices)
        DeviceStore.saveTrash(trash)
        refreshAll(animateNumbers = true)
        updateBadgeEntry()
        checkBadges(announce = false)
        Toast.makeText(this, getString(R.string.import_restored, nameOf(t.item)), Toast.LENGTH_SHORT).show()
    }

    private fun deleteForever(id: String) {
        trash.removeAll { it.item.id == id }
        DeviceStore.saveTrash(trash)
    }

    private fun nameOf(item: DeviceItem): String =
        if (item.name.isEmpty()) getString(R.string.trash_unnamed) else item.name

    /** 勋章只增不减：达成即点亮并记下时间，之后数字回落也不熄灭 */
    private fun checkBadges(announce: Boolean) {
        val fresh = Badges.evaluate(devices, badgeState)
        if (fresh.isEmpty()) return
        DeviceStore.saveBadges(badgeState)
        updateBadgeEntry()
        if (announce) {
            // 达成反馈只走弹屏这一条路径，不再叠一条系统 Toast
            celebration.show(theme, fresh.map { it to (badgeState[it.id] ?: 0L) })
        }
    }

    private fun updateBadgeEntry() {
        binding.tvBadgeEntryCount.text =
            getString(R.string.sum_badge_progress, badgeState.size.toString(), Badges.ALL.size.toString())
    }

    /* ------------------------------ 主题 ------------------------------ */

    private fun toggleTheme() {
        val from = theme
        val to = AppTheme.of(!dark)
        dark = !dark
        DeviceStore.saveDark(dark)

        binding.ivThemeIcon.setImageResource(if (dark) R.drawable.ic_moon else R.drawable.ic_sun)
        // 图标旋转写在 .animation(140ms EaseOut) 之上，走的是这条组件动画，
        // 不是下面 260ms EaseInOut 的换肤动画
        binding.ivThemeIcon.animate()
            .rotation(if (dark) 20f else 0f)
            .setDuration(140L)
            .setInterpolator(Curves.easeOut)
            .start()

        themeAnimator?.cancel()
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = 260L
        va.interpolator = Curves.easeInOut
        va.addUpdateListener {
            val t = it.animatedValue as Float
            applyTheme(AppTheme.lerp(from, to, t), heavy = false)
        }
        va.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                applyTheme(to, heavy = true)
                // 动画结束后同步系统控件（日期选择器等）的昼夜状态。
                // manifest 已经把 uiMode 放进 configChanges，appcompat 只在 Activity
                // **没有**自行处理该配置时才 recreate —— 这里是原地换 Resources +
                // onConfigurationChanged，Activity 不重建，滚动位置与所有状态都还在。
                AppCompatDelegate.setDefaultNightMode(
                    if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        })
        va.start()
        themeAnimator = va
    }

    private fun applyTheme(t: AppTheme, heavy: Boolean = true) {
        theme = t
        val b = binding
        val d = density

        // 页面底色：顶部渐变。挂在根节点而不是内容层，
        // 这样根节点为系统栏留出的 padding 区域也是同一条渐变，不会露出底色带
        b.root.background =
            GradientBgDrawable(intArrayOf(t.bgTop, t.bg, t.bg), floatArrayOf(0f, 0.28f, 1f), false)

        // 顶栏
        b.tvTitle.setTextColor(t.text)
        b.tvSlogan.setTextColor(t.sub)
        b.btnTheme.background = roundedDrawable(t.card, 19f, d)
        ViewCompat.setElevation(b.btnTheme, 4f * d)
        // 数据与备份的入口：与主题按钮同款圆形底板，图标跟着文字色走
        b.btnHub.background = roundedDrawable(t.card, 19f, d)
        ViewCompat.setElevation(b.btnHub, 4f * d)
        b.ivHubIcon.setColorFilter(t.text)
        b.btnAdd.background = roundedDrawable(t.primary, 19f, d)
        // 鸿蒙版 Button 的字色写死 Color.White，深色模式下也是白字，这里保持一致
        b.btnAdd.setTextColor(CardColors.whiteText)

        // 总览卡
        b.summaryCard.background = GradientBgDrawable(intArrayOf(t.primary, t.primary2), null, true)
        ViewCompat.setElevation(b.summaryCard, 8f * d)
        b.glow1.background = circleDrawable(Color.parseColor("#1FFFFFFF"))
        b.glow2.background = circleDrawable(Color.parseColor("#14FFFFFF"))
        b.tvSummaryLabel.setTextColor(CardColors.softText)
        b.tvCountBadge.background = roundedDrawable(CardColors.badgeBg, 10f, d)
        b.tvCountBadge.setTextColor(CardColors.whiteText)
        b.tvTotalMoney.setTextColor(CardColors.whiteText)
        b.tvAvgLabel.setTextColor(CardColors.softText)
        b.tvAvgMoney.setTextColor(CardColors.softAccent)
        b.tvBestTag.background = roundedDrawable(CardColors.badgeBg, 8f, d)
        b.tvBestTag.setTextColor(CardColors.whiteText)
        b.tvBestName.setTextColor(CardColors.whiteText)
        b.tvBestAvg.setTextColor(CardColors.softAccent)

        // 置顶迷你汇总条
        b.stickyBar.background = GradientBgDrawable(intArrayOf(t.bgTop, t.bg), null, false)
        ViewCompat.setElevation(b.stickyBar, 4f * d)
        b.stickyCard.background = roundedDrawable(t.card, 14f, d)
        b.tvStickyTotal.setTextColor(t.text)
        b.tvStickyCount.setTextColor(t.sub)
        b.tvStickyAvg.setTextColor(t.accent)
        b.stickySep1.setBackgroundColor(t.line)
        b.stickySep2.setBackgroundColor(t.line)

        // 回顶部圆钮：主色实心圆，箭头永远是白的
        b.btnTop.background = circleDrawable(t.primary)
        ViewCompat.setElevation(b.btnTop, 6f * d)
        b.ivTop.alpha = 1f
        // 点下去时扩出去的那圈光晕也是主色，跟着换肤一起褪
        b.topRing.background = circleDrawable(t.primary)
        b.ivTopFly.alpha = 0f

        // 收藏相关（星标金随昼夜切换，所以要跟着主题刷一遍）
        updateFavChip()
        renderFavSwitch()

        // 图表卡
        b.chartCard.background = roundedDrawable(t.card, 18f, d)
        ViewCompat.setElevation(b.chartCard, 4f * d)
        b.tvChartTitle.setTextColor(t.text)
        b.tvChartSub.setTextColor(t.sub)
        b.btnToggleChart.setTextColor(t.sub)
        b.pieChart.applyTheme(t)

        // 排序
        b.tvSortLabel.setTextColor(t.sub)
        updateSortChips()
        updateModeChips()

        // 空态
        b.tvEmptyTitle.setTextColor(t.text)
        b.tvEmptyDesc1.setTextColor(t.sub)
        b.tvEmptyDesc2.setTextColor(t.sub)
        // 页脚：底色调成次要色，链接色由 span 自己画（换主题要重建成新配色）
        b.tvGithub.setTextColor(t.sub)
        b.tvGithub.text = githubFooter(t)
        b.btnEmptyAdd.background = roundedDrawable(t.primary, 20f, d)
        b.btnEmptyAdd.setTextColor(CardColors.whiteText)

        // 编辑面板：鸿蒙版是整个遮罩层铺 scrim（面板本身是 card 不透明），
        // 这里同样把 scrim 打在整层上，上半部分的可点区域不再单独叠一层，避免深浅不一
        b.editorLayer.setBackgroundColor(t.scrim)
        b.editorSheet.background = roundedTopDrawable(t.card, 24f, d)
        ViewCompat.setElevation(b.editorSheet, 12f * d)
        b.tvEditorTitle.setTextColor(t.text)
        b.btnEditorClose.setTextColor(t.sub)
        b.tvLabelName.setTextColor(t.sub)
        b.tvLabelPrice.setTextColor(t.sub)
        b.tvLabelKind.setTextColor(t.sub)
        b.tvLabelDate.setTextColor(t.sub)
        b.tvLabelNote.setTextColor(t.sub)
        setFieldStyle(b.etName, t)
        setFieldStyle(b.etPrice, t)
        setFieldStyle(b.etNote, t)
        b.tvDate.background = roundedDrawable(t.field, 12f, d)
        b.tvDate.setTextColor(t.text)
        b.tvError.setTextColor(t.danger)
        b.btnCancel.background = roundedDrawable(t.field, 12f, d)
        b.btnCancel.setTextColor(t.chipText)
        b.btnSave.background = roundedDrawable(t.primary, 12f, d)
        b.btnSave.setTextColor(CardColors.whiteText)

        // 勋章入口：总览卡上的半透明胶囊，配色与"0 台在册"同源（两者都压在主色渐变上）
        b.btnBadgeEntry.background = roundedDrawable(CardColors.badgeBg, 10f, d)
        b.tvBadgeEntryCount.setTextColor(CardColors.whiteText)
        b.tvBadgeEntryMore.setTextColor(CardColors.softText)

        // 撤销条
        undoBar?.applyTheme(t)

        // 删除确认
        b.confirmLayer.setBackgroundColor(t.scrim)
        b.confirmCard.background = roundedDrawable(t.card, 18f, d)
        ViewCompat.setElevation(b.confirmCard, 8f * d)
        b.tvConfirmTitle.setTextColor(t.text)
        b.tvDeleteName.setTextColor(t.sub)
        b.btnConfirmCancel.background = roundedDrawable(t.field, 12f, d)
        b.btnConfirmCancel.setTextColor(t.chipText)
        b.btnConfirmDelete.background = roundedDrawable(t.danger, 12f, d)
        b.btnConfirmDelete.setTextColor(CardColors.whiteText)

        // 系统栏
        window.statusBarColor = t.bgTop
        window.navigationBarColor = t.bg
        WindowInsetsControllerCompat(window, b.root).isAppearanceLightStatusBars = !t.dark

        // 换肤动画期间（heavy = false）也要让卡片和图例跟着一起变：
        // 鸿蒙版是在 animateTo 里改 @State，整棵树原地重绘，列表颜色是插值过去的；
        // 这边若只在动画结束时刷一次，列表和图例会在 260ms 后"啪"地硬切。
        adapter.applyTheme(t)
        adapter.rethemeVisible(binding.recycler, t)
        rethemeLegend(t)
        if (heavy) {
            kindBinder.applyTheme(t)
            buildLegend(lastSlices)
        }
    }

    /** 图例不重建，只把文字改成当前中间色（换肤动画逐帧调用） */
    private fun rethemeLegend(t: AppTheme) {
        val box = binding.legendBox
        for (i in 0 until box.childCount) {
            val row = box.getChildAt(i)
            val name = row.findViewById<TextView>(R.id.tvLegendName)
            if (name == null) {
                // 没有数据的那一行是纯 TextView
                (row as? TextView)?.setTextColor(t.sub)
                continue
            }
            name.setTextColor(t.text2)
            row.findViewById<TextView>(R.id.tvLegendValue).setTextColor(t.text)
            row.findViewById<TextView>(R.id.tvLegendPercent).setTextColor(t.sub)
        }
    }

    private fun setFieldStyle(et: EditText, t: AppTheme) {
        et.background = roundedDrawable(t.field, 12f, density)
        et.setTextColor(t.text)
        et.setHintTextColor(t.sub)
        et.highlightColor = t.primarySoft
    }

    /* ------------------------------ 空态呼吸动画 ------------------------------ */

    private fun setupBreath() {
        breathAnimator?.cancel()
        val va = android.animation.ValueAnimator.ofFloat(0f, -8f * density)
        va.duration = 1500L
        va.repeatCount = android.animation.ValueAnimator.INFINITE
        va.repeatMode = android.animation.ValueAnimator.REVERSE
        va.interpolator = Curves.easeInOut
        va.startDelay = 400L   // 与鸿蒙版一致：进场 400ms 后才开始呼吸
        va.addUpdateListener { binding.ivEmpty.translationY = it.animatedValue as Float }
        va.start()
        breathAnimator = va
    }

    /** 列表非空时空态图标根本不显示，没必要留着一条无限循环的动画 */
    private fun stopBreath() {
        breathAnimator?.cancel()
        breathAnimator = null
    }
}
