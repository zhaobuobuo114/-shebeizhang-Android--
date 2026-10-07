package com.deviceledger.app

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import com.deviceledger.app.ui.DeviceAdapter
import com.deviceledger.app.ui.GradientBgDrawable
import com.deviceledger.app.ui.KindTilesBinder
import com.deviceledger.app.ui.WrapLayout
import com.deviceledger.app.ui.circleDrawable
import com.deviceledger.app.ui.pressEffect
import com.deviceledger.app.ui.chipTo
import com.deviceledger.app.ui.roundedDrawable
import com.deviceledger.app.ui.roundedTopDrawable
import com.deviceledger.app.ui.tintTo
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.DeviceStore
import com.deviceledger.app.util.Fmt
import java.util.Calendar
import java.util.Comparator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var kindBinder: KindTilesBinder

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

        devices.clear()
        devices.addAll(DeviceStore.load())
        refreshAll(animateNumbers = savedInstanceState == null)
        applyTheme(theme)
        renderDateText()
        setupBreath()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时对齐一次磁盘数据（跨天时天数也会重算）
        val disk = DeviceStore.load()
        if (disk.size != devices.size) {
            devices.clear()
            devices.addAll(disk)
        }
        refreshAll(animateNumbers = false)
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

    /**
     * 整页向下滚动、大总览卡滑出屏幕后，在最上方钉住一小行：
     * 总价 / 台数 / 日均合计。滚回顶部时自动收起。
     */
    private fun setupSticky() {
        binding.scrollMain.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ -> updateSticky(scrollY) }
        )
        binding.root.post { updateSticky(binding.scrollMain.scrollY) }
    }

    private fun updateSticky(scrollY: Int) {
        // 与鸿蒙版 Index.ets 里的 onListScroll 完全同一个阈值：滚过 200vp 才钉住
        val threshold = (200f * density).toInt()
        val want = devices.isNotEmpty() && scrollY > threshold
        if (want != stickyShown) {
            stickyShown = want
            val bar = binding.stickyBar
            val h = if (bar.height > 0) bar.height.toFloat() else 52f * density
            bar.animate().cancel()
            if (want) {
                bar.visibility = View.VISIBLE
                bar.translationY = -h
                bar.alpha = 0f
                bar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                bar.animate()
                    .translationY(-h)
                    .alpha(0f)
                    .setDuration(200L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { bar.visibility = View.GONE }
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
            btn.visibility = View.VISIBLE
            btn.scaleX = 0.3f
            btn.scaleY = 0.3f
            btn.alpha = 0f
            btn.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .alpha(1f)
                .setDuration(170L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (!topShown) {
                        return@withEndAction
                    }
                    btn.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        } else {
            btn.animate()
                .scaleX(0.4f)
                .scaleY(0.4f)
                .alpha(0f)
                .setDuration(140L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { btn.visibility = View.GONE }
                .start()
        }
    }

    private var scrollAnim: android.animation.ValueAnimator? = null

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
        val dur = Math.min(460L, (200f + from / density * 0.3f).toLong())
        val va = android.animation.ValueAnimator.ofInt(from, 0)
        va.duration = dur
        va.interpolator = DecelerateInterpolator()
        va.addUpdateListener { binding.scrollMain.scrollTo(0, it.animatedValue as Int) }
        va.start()
        scrollAnim = va
    }

    /* ------------------------------ 列表 ------------------------------ */

    private fun setupList() {
        adapter = DeviceAdapter(
            onClick = { item -> openEditor(item) },
            onStar = { item, fav -> onStarTapped(item, fav) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
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
            kindBinder.select(key)
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
            binding.tvKindMoreArrow.animate()
                .rotation(target)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
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
            va.interpolator = AccelerateDecelerateInterpolator()
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
        binding.rowFav.pressEffect(0.98f)
        binding.rowFav.setOnClickListener {
            editFavorite = !editFavorite
            animateToFavSwitch()
            renderFavSwitch()
        }
    }

    private fun renderFavSwitch() {
        binding.tvFavState.text = if (editFavorite) "已收藏" else "未收藏"
        binding.tvFavState.setTextColor(if (editFavorite) theme.gold else theme.sub)
        binding.ivFavSwitch.setImageResource(
            if (editFavorite) R.drawable.ic_star else R.drawable.ic_star_off
        )
    }

    private fun animateToFavSwitch() {
        binding.ivFavSwitch.animate().cancel()
        binding.ivFavSwitch.scaleX = 0.8f
        binding.ivFavSwitch.scaleY = 0.8f
        binding.ivFavSwitch.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /* ------------------------------ 交互 ------------------------------ */

    private fun setupActions() {
        // 统一加按压动效（按下缩小 + 半透明，松手回弹），替掉系统默认的蓝色水波纹
        // 鸿蒙版统一是 scale 0.92 / 140ms 回弹；这里用 0.92 对齐
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

        press(binding.btnFavOnly, 0.90f) { setFavOnly(!favOnly) }

        // 排序方向：「排序」两个字 + 上下双箭头是同一块热区，点哪儿都切正序 / 倒序。
        // 按压缩到 0.92，与鸿蒙版 sc() 同一个数
        press(binding.sortDirBox, 0.92f) { toggleSortDir() }

        press(binding.sortAvg, 0.90f) { setSort("avg") }
        press(binding.sortPrice, 0.90f) { setSort("price") }
        press(binding.sortDate, 0.90f) { setSort("date") }
        press(binding.sortDays, 0.90f) { setSort("days") }

        // 回顶部：右下角圆钮，逻辑内部的按下回弹和鸿蒙版一个时序
        press(binding.btnTop, 0.90f) { scrollToTop() }
        // 置顶那条汇总也可以点，点了同样回顶部（鸿蒙版一致）
        press(binding.stickyBar, 0.99f) { scrollToTop() }

        press(binding.btnEditorClose) { closeEditor() }
        press(binding.btnCancel) { closeEditor() }
        press(binding.btnSave) { onSave() }
        press(binding.tvDate, 0.98f) { pickDate() }

        binding.editorScrim.setOnClickListener { closeEditor() }
        binding.confirmLayer.setOnClickListener { showConfirm(false) }
        // 卡片本身拦截点击，避免点卡片空白处穿透到遮罩而关闭
        binding.confirmCard.setOnClickListener { /* 不处理 */ }
        press(binding.btnConfirmCancel) { showConfirm(false) }
        press(binding.btnConfirmDelete) { confirmDelete() }
    }

    private fun press(view: View, scale: Float = 0.94f, action: () -> Unit) {
        view.pressEffect(scale)
        view.setOnClickListener { action() }
    }

    /** 切换"只看收藏"：汇总统计仍按全部设备算，只是列表过一遍筛 */
    private fun setFavOnly(on: Boolean) {
        if (favOnly == on) return
        favOnly = on
        // 鸿蒙版这里走的是 animateTo 220ms Friction：胶囊不重建，配色是渐变过去的
        refreshAll(animateNumbers = false, animateFav = true)
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
        refreshAll(animateNumbers = false)
    }

    /** 切换排序方向（点「排序」或箭头，或点当前已选中的那个排序胶囊） */
    private fun toggleSortDir() {
        sortAsc = !sortAsc
        // 鸿蒙版是 animateTo 260ms Friction：箭头不重建，配色渐变过去，不是硬切
        refreshAll(animateNumbers = false, animateSort = true)
    }

    private fun setChartMode(mode: String) {
        if (chartMode == mode) return
        chartMode = mode
        updateChart(animate = true)
        updateModeChips()
    }

    private fun toggleChart() {
        chartOpen = !chartOpen
        binding.btnToggleChart.text = if (chartOpen) "收起" else "展开"
        if (chartOpen) {
            binding.chartBody.visibility = View.VISIBLE
            binding.chartBody.alpha = 0f
            binding.chartBody.translationY = -8f * density
            binding.chartBody.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            updateChart(animate = true)
        } else {
            binding.chartBody.animate()
                .alpha(0f)
                .setDuration(200L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { binding.chartBody.visibility = View.GONE }
                .start()
        }
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
     */
    private fun refreshAll(
        animateNumbers: Boolean,
        animateSort: Boolean = false,
        animateFav: Boolean = false
    ) {
        val shown = sortedList()
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
        numberAnimator?.cancel()
        if (!animate) {
            displayTotal = total
            displayAvg = avg
            renderNumbers()
            return
        }
        val fromTotal = displayTotal
        val fromAvg = displayAvg
        // 首次进场：鸿蒙 SummaryCard 是 60ms 延迟 + 900ms；之后每次数据变化是 720ms
        val first = !firstNumberPlayed
        firstNumberPlayed = true
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = if (first) 900L else 720L
        va.startDelay = if (first) 60L else 0L
        va.interpolator = DecelerateInterpolator()
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

    private fun updateChart(animate: Boolean) {
        val slices = ChartData.slices(devices, chartMode == "amount")
        // 数据和上次完全一样时不重播展开动画（回前台、切排序时避免无谓抖动）
        val changed = slices != lastSlices
        lastSlices = slices
        binding.pieChart.setData(slices, chartMode, animate && changed)
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
        val inflater = layoutInflater
        for (s in slices) {
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
            percent.text = if (total > 0) Fmt.percent(s.v / total) else "0%"
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
    }

    /** 上下箭头：当前方向的那一个点亮（主色 + 不透明），另一个压暗 */
    private fun updateSortDirIcon(animate: Boolean = false) {
        // 260ms 对应鸿蒙 toggleSortDir 的 animateTo({ duration: 260, curve: Curve.Friction })
        val d = if (animate) 260L else 0L
        binding.tvSortUp.tintTo(
            if (sortAsc) theme.primary else theme.sub,
            if (sortAsc) 1f else 0.45f,
            d
        )
        binding.tvSortDown.tintTo(
            if (sortAsc) theme.sub else theme.primary,
            if (sortAsc) 0.45f else 1f,
            d
        )
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
            layer.animate().alpha(1f).setDuration(220L).setInterpolator(DecelerateInterpolator()).start()
            sheet.translationY = h
            sheet.animate()
                .translationY(0f)
                .setDuration(300L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            hideKeyboard()
            layer.animate().alpha(0f).setDuration(240L).setInterpolator(AccelerateInterpolator()).start()
            sheet.animate()
                .translationY(h)
                .setDuration(240L)
                .setInterpolator(AccelerateInterpolator())
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

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.alpha = 0f
        binding.tvError.translationX = -8f * density
        binding.tvError.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /* ------------------------------ 删除确认 ------------------------------ */

    private fun showConfirm(show: Boolean) {
        val layer = binding.confirmLayer
        if (show) {
            val target = devices.firstOrNull { it.id == pendingDeleteId }
            binding.tvDeleteName.text = target?.name ?: ""
            layer.visibility = View.VISIBLE
            layer.alpha = 0f
            layer.animate().alpha(1f).setDuration(200L).setInterpolator(DecelerateInterpolator()).start()
            binding.confirmCard.scaleX = 0.94f
            binding.confirmCard.scaleY = 0.94f
            binding.confirmCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            pendingDeleteId = null
            layer.animate()
                .alpha(0f)
                .setDuration(180L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { layer.visibility = View.GONE }
                .start()
        }
    }

    private fun confirmDelete() {
        val id = pendingDeleteId ?: return
        devices.removeAll { it.id == id }
        DeviceStore.save(devices)
        showConfirm(false)
        refreshAll(animateNumbers = true)
    }

    /* ------------------------------ 主题 ------------------------------ */

    private fun toggleTheme() {
        val from = theme
        val to = AppTheme.of(!dark)
        dark = !dark
        DeviceStore.saveDark(dark)

        binding.ivThemeIcon.setImageResource(if (dark) R.drawable.ic_moon else R.drawable.ic_sun)
        binding.ivThemeIcon.animate()
            .rotation(if (dark) 20f else 0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        themeAnimator?.cancel()
        val va = android.animation.ValueAnimator.ofFloat(0f, 1f)
        va.duration = 260L
        va.interpolator = AccelerateDecelerateInterpolator()
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

        if (heavy) {
            adapter.applyTheme(t)
            kindBinder.applyTheme(t)
            buildLegend(lastSlices)
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
        va.interpolator = AccelerateDecelerateInterpolator()
        va.startDelay = 400L   // 与鸿蒙版一致：进场 400ms 后才开始呼吸
        va.addUpdateListener { binding.ivEmpty.translationY = it.animatedValue as Float }
        va.start()
        breathAnimator = va
    }
}
