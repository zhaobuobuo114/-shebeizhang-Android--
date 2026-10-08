package com.deviceledger.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deviceledger.app.databinding.ActivityMainBinding
import com.deviceledger.app.model.ChartData
import com.deviceledger.app.model.DEVICE_KINDS
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.PieSlice
import com.deviceledger.app.ui.AppTheme
import com.deviceledger.app.ui.CardColors
import com.deviceledger.app.ui.Curves
import com.deviceledger.app.ui.DeviceAdapter
import com.deviceledger.app.ui.GradientBgDrawable
import com.deviceledger.app.ui.KindTilesBinder
import com.deviceledger.app.ui.MoveAnimator
import com.deviceledger.app.ui.SortArrowView
import com.deviceledger.app.ui.WrapLayout
import com.deviceledger.app.ui.circleDrawable
import com.deviceledger.app.ui.pressEffect
import com.deviceledger.app.ui.chipTo
import com.deviceledger.app.ui.roundedDrawable
import com.deviceledger.app.ui.roundedTopDrawable
import com.deviceledger.app.ui.tintTo
import com.deviceledger.app.ui.topPressEffect
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.DeviceStore
import com.deviceledger.app.util.Fmt
import java.util.Calendar
import java.util.Comparator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var kindBinder: KindTilesBinder
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
    private val editDate: Calendar = Calendar.getInstance()
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
    private var lastSlices: List<PieSlice> = emptyList()

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
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        devices.clear()
        devices.addAll(DeviceStore.load())
        refreshAll(animateNumbers = savedInstanceState == null)
        applyTheme(theme)
        renderDateText()
        setupBreath()
    }

    override fun onResume() {
        super.onResume()
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
        numberAnimator?.cancel()
        themeAnimator?.cancel()
        breathAnimator?.cancel()
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
        scroller.viewTreeObserver.addOnScrollChangedListener { refreshChipFx() }
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
                }
                // 立即把卡片复位；是否真的删除由确认框决定
                adapter.notifyItemChanged(pos)
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

                if (right - left > 40f * density) {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 14f * density
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.isFakeBoldText = true
                    val fm = textPaint.fontMetrics
                    c.drawText(
                        "删除",
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
            if (kindExpanded) "收起" else ("展开全部 " + DEVICE_KINDS.size + " 类")
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
        // 开着"只看收藏"时取消收藏，这条要从列表里消失，得整表刷新一次
        if (favOnly && !fav) {
            refreshAll(animateNumbers = false)
        }
    }

    /** 排序栏里的收藏胶囊：文字带收藏数量，选中时是金色 */
    private fun updateFavChip(animate: Boolean = false) {
        val count = devices.count { it.favorite }
        val tv = binding.btnFavOnly
        tv.text = if (count > 0) "★ 收藏 $count" else "★ 收藏"
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
        binding.tvFavState.text = if (editFavorite) "已收藏" else "未收藏"
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
        binding.btnToggleChart.text = if (chartOpen) "收起" else "展开"
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

        binding.tvCountBadge.text = devices.size.toString() + " 台在册"
        binding.tvStickyCount.text = devices.size.toString() + " 台"
        if (best == null) {
            binding.rowBest.visibility = View.GONE
        } else {
            binding.rowBest.visibility = View.VISIBLE
            binding.tvBestName.text = best.name
            binding.tvBestAvg.text = "日均 ¥" + Fmt.smallMoney(DeviceCalc.avgPerDay(best))
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
            b.tvEmptyTitle.text = "还没有收藏的设备"
            b.tvEmptyDesc1.text = "点设备卡片右下角的星标，"
            b.tvEmptyDesc2.text = "把最爱的设备收进来。"
            b.btnEmptyAdd.text = "看全部设备"
        } else {
            b.tvEmptyTitle.text = "还没有设备记录"
            b.tvEmptyDesc1.text = "记下第一台设备的购买日期和价格，"
            b.tvEmptyDesc2.text = "它会告诉你这台设备每天花了多少钱。"
            b.btnEmptyAdd.text = "添加设备"
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
        binding.tvTotalMoney.text = "¥" + Fmt.money(displayTotal)
        binding.tvAvgMoney.text = "¥" + Fmt.smallMoney(displayAvg)
        binding.tvStickyTotal.text = "¥" + Fmt.money(displayTotal)
        binding.tvStickyAvg.text = "日均 ¥" + Fmt.smallMoney(displayAvg)
    }

    /**
     * @param force 展开图表时传 true。鸿蒙版收起再展开，Canvas 会重新挂载并走 onReady，
     *              扇形会重新播放 820ms 的展开动画；这里若沿用"数据没变就不重播"的判断，
     *              展开时扇形会直接是终态，和鸿蒙版对不上。
     */
    private fun updateChart(animate: Boolean, force: Boolean = false) {
        val slices = ChartData.slices(devices, chartMode == "amount")
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
            tv.text = "还没有数据"
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
                "¥" + Fmt.money(s.v)
            } else {
                Math.round(s.v).toString() + " 台"
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
        binding.sortAvg.text = if (sortAsc) "日均最低" else "日均最高"
        binding.sortPrice.text = if (sortAsc) "价格最低" else "价格最高"
        binding.sortDate.text = if (sortAsc) "最早购买" else "最近购买"
        binding.sortDays.text = if (sortAsc) "用最短" else "用最久"
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

    private fun openEditor(item: DeviceItem?) {
        binding.tvError.visibility = View.GONE
        if (item == null) {
            editingId = null
            binding.tvEditorTitle.text = "添加设备"
            binding.etName.setText("")
            binding.etPrice.setText("")
            binding.etNote.setText("")
            editKind = "phone"
            editFavorite = false
            editDate.timeInMillis = System.currentTimeMillis()
        } else {
            editingId = item.id
            binding.tvEditorTitle.text = "编辑设备"
            binding.etName.setText(item.name)
            val p = item.price
            binding.etPrice.setText(
                if (p == Math.floor(p) && !p.isInfinite()) p.toLong().toString() else p.toString()
            )
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
        binding.tvDate.text = DateUtil.format(
            editDate.get(Calendar.YEAR),
            editDate.get(Calendar.MONTH) + 1,
            editDate.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun pickDate() {
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
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun onSave() {
        val name = binding.etName.text.toString().trim()
        val priceStr = binding.etPrice.text.toString().trim()
        val price = priceStr.toDoubleOrNull()
        val buyDate = DateUtil.format(
            editDate.get(Calendar.YEAR),
            editDate.get(Calendar.MONTH) + 1,
            editDate.get(Calendar.DAY_OF_MONTH)
        )

        val error: String? = when {
            name.isEmpty() -> "请填写设备名称"
            priceStr.isEmpty() -> "请填写购买价格"
            price == null || price <= 0.0 || price.isNaN() || price.isInfinite() -> "请填写有效的购买价格"
            buyDate > DateUtil.today() -> "购买日期不能晚于今天"
            else -> null
        }
        if (error != null) {
            showError(error)
            return
        }
        val safePrice = price ?: return
        val note = binding.etNote.text.toString().trim()

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

    private fun confirmDelete() {
        val id = pendingDeleteId ?: return
        devices.removeAll { it.id == id }
        DeviceStore.save(devices)
        // 鸿蒙版 confirmDelete 里关弹窗用的是 animateTo 200ms EaseIn
        showConfirm(false, closeMs = 200L)
        // 列表的增删不在 animateTo 里（鸿蒙是先改 deviceList 再 animateTo），所以硬切
        refreshAll(animateNumbers = true)
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
                // 动画结束后同步系统控件主题；Activity 会被重建，滚动位置由 RecyclerView 自行恢复
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

        // 页面底色：顶部渐变
        b.contentMain.background =
            GradientBgDrawable(intArrayOf(t.bgTop, t.bg, t.bg), floatArrayOf(0f, 0.28f, 1f), false)

        // 顶栏
        b.tvTitle.setTextColor(t.text)
        b.tvSlogan.setTextColor(t.sub)
        b.btnTheme.background = roundedDrawable(t.card, 19f, d)
        ViewCompat.setElevation(b.btnTheme, 4f * d)
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
}
