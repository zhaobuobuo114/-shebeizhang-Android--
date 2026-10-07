package com.deviceledger.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.deviceledger.app.R
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.DeviceItem
import com.deviceledger.app.model.kindColorHex
import com.deviceledger.app.model.kindIconRes
import com.deviceledger.app.model.kindLabel
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.Fmt

/** 设备列表适配器：卡片内容 + 错峰入场动画 */
class DeviceAdapter(
    private val onClick: (DeviceItem) -> Unit,
    /** 点星标：回调新的收藏状态，由页面落库 */
    private val onStar: (DeviceItem, Boolean) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DeviceItem>()
    var theme: AppTheme = AppTheme.lightTheme()

    /**
     * 已播过入场动画的卡片（存的是"内容 key"）。
     * 用静态集合保存：切换昼夜模式时 Activity 会重建，
     * 这样列表不会重播一遍入场动画。
     */
    private val enteredKeys: MutableSet<String> = Companion.enteredKeys

    /**
     * 提交新列表。
     *
     * 走 DiffUtil 而不是 notifyDataSetChanged：鸿蒙版排序 / 筛选是在 animateTo 闭包里改
     * @State 的，ArkUI 会把由此引起的子组件位置变化做成动画（列表项平滑挪位）。
     * 只有精确派发 move 通知，RecyclerView 才会播放位移动画；
     * notifyDataSetChanged 会让列表硬切重排。
     */
    fun submit(list: List<DeviceItem>) {
        val old = ArrayList(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = list.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                DeviceCalc.keyOf(old[oldPos]) == DeviceCalc.keyOf(list[newPos])

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                old[oldPos] == list[newPos]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * 换肤：只记下新主题，再给当前挂着的卡片刷一遍配色。
     *
     * 不用 notifyDataSetChanged —— 那会让所有 ViewHolder 重新绑定，
     * 把正在播的入场动画和列表挪位动画一起打断，动画结束后再硬切一次。
     * 鸿蒙版换肤时 DeviceCard 只是 @Prop theme 变了，组件不重建，动画照旧播完。
     */
    fun applyTheme(t: AppTheme) {
        theme = t
    }

    /** 只刷新当前已挂载卡片的配色（换肤动画逐帧调用） */
    fun rethemeVisible(recycler: RecyclerView, t: AppTheme) {
        theme = t
        for (i in 0 until recycler.childCount) {
            (recycler.getChildViewHolder(recycler.getChildAt(i)) as? VH)?.applyThemeColors(t)
        }
    }

    fun itemAt(position: Int): DeviceItem = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.starCb = onStar
        holder.bind(item, theme)
        holder.itemView.setOnClickListener { onClick(item) }
        // 用"内容 key"而不是 id：鸿蒙版 ForEach 的 key 变了就会重建子组件、重播入场动画，
        // 所以编辑过的卡片同样会再播一次，这里保持一致
        val contentKey = DeviceCalc.keyOf(item)
        if (!enteredKeys.contains(contentKey)) {
            enteredKeys.add(contentKey)
            holder.playEnter(position)
        } else {
            holder.resetTransform()
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val cardRoot: View = view.findViewById(R.id.cardRoot)
        private val iconBg: View = view.findViewById(R.id.iconBg)
        private val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        private val tvKind: TextView = view.findViewById(R.id.tvKind)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val tvDays: TextView = view.findViewById(R.id.tvDays)
        private val tvAvgLabel: TextView = view.findViewById(R.id.tvAvgLabel)
        private val tvAvg: TextView = view.findViewById(R.id.tvAvg)
        private val tvNote: TextView = view.findViewById(R.id.tvNote)
        private val starBox: View = view.findViewById(R.id.starBox)
        private val ivStar: ImageView = view.findViewById(R.id.ivStar)

        /** 星标的显示状态：本地维护一份，点下去立刻变色，不等页面回传 */
        private var favState: Boolean = false

        /** 点星标的回调：由适配器在每个 item 绑定前塞进来（VH 是静态嵌套类，拿不到外层成员） */
        var starCb: ((DeviceItem, Boolean) -> Unit)? = null

        private val density: Float
            get() = itemView.resources.displayMetrics.density

        fun bind(item: DeviceItem, theme: AppTheme) {
            resetTransform()

            cardRoot.background = roundedDrawable(theme.card, 18f, density)
            ViewCompat.setElevation(cardRoot, 3f * density)

            val colorHex = kindColorHex(item.category)
            val tintColor = android.graphics.Color.parseColor(Fmt.tint(colorHex, "22"))
            iconBg.background = roundedDrawable(tintColor, 17f, density)
            ivIcon.setImageResource(kindIconRes(item.category))

            tvName.text = item.name
            tvName.setTextColor(theme.text)
            tvPrice.text = "¥" + Fmt.money(item.price)
            tvPrice.setTextColor(theme.text)

            tvKind.text = kindLabel(item.category)
            tvKind.setTextColor(android.graphics.Color.parseColor(colorHex))
            tvKind.background = roundedDrawable(tintColor, 5f, density)

            tvDate.text = item.buyDate
            tvDate.setTextColor(theme.sub)

            val days = DeviceCalc.daysUsed(item)
            tvDays.text = "已用 " + days + " 天 · " + DateUtil.humanDays(days)
            tvDays.setTextColor(theme.sub)

            tvAvgLabel.setTextColor(theme.sub)
            tvAvg.text = "¥" + Fmt.smallMoney(DeviceCalc.avgPerDay(item))
            tvAvg.setTextColor(theme.accent)

            // 备注一直参与布局（为空时只是不显示字），这样星标总能被顶到最右边
            tvNote.text = item.note
            tvNote.setTextColor(theme.sub)

            favState = item.favorite
            renderStar()

            starBox.pressEffect(0.86f)
            starBox.setOnClickListener {
                val now = !favState
                favState = now
                renderStar()
                playStarPop()
                starCb?.invoke(item, now)
            }
        }

        private fun renderStar() {
            ivStar.setImageResource(if (favState) R.drawable.ic_star else R.drawable.ic_star_off)
        }

        /**
         * 只刷配色：不动数据、不复位 transform、不重播动画。
         * 换肤动画期间每帧调用，卡片颜色和整页一起渐变过去。
         * 类型标签用的是类型固有色，不随昼夜变化，所以这里不碰它。
         */
        fun applyThemeColors(t: AppTheme) {
            cardRoot.background = roundedDrawable(t.card, 18f, density)
            ViewCompat.setElevation(cardRoot, 3f * density)
            tvName.setTextColor(t.text)
            tvPrice.setTextColor(t.text)
            tvDate.setTextColor(t.sub)
            tvDays.setTextColor(t.sub)
            tvAvgLabel.setTextColor(t.sub)
            tvAvg.setTextColor(t.accent)
            tvNote.setTextColor(t.sub)
        }

        /**
         * 点星标：先弹大再回落。
         * 鸿蒙版 DeviceCard.tapStar 是 150ms EaseOut 冲到 1.34，紧接着 260ms Friction 落回 1，
         * 这里逐项对齐（原来是 150ms Decelerate + 260ms AccelerateDecelerate）。
         */
        private fun playStarPop() {
            ivStar.animate().cancel()
            ivStar.animate()
                .scaleX(1.34f)
                .scaleY(1.34f)
                .setDuration(150L)
                .setInterpolator(Curves.easeOut)
                .withEndAction {
                    ivStar.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260L)
                        .setInterpolator(Curves.friction)
                        .start()
                }
                .start()
        }

        /**
         * 错峰入场：透明 + 上移 + 轻微缩放。
         * 与鸿蒙版 DeviceCard.aboutToAppear 一致：延迟 24 + min(index,10)×42，
         * 然后 430ms Curve.Friction 走到终态。
         */
        fun playEnter(position: Int) {
            val delay = (24 + minOf(position, 10) * 42).toLong()
            itemView.alpha = 0f
            itemView.translationY = 26f * density
            itemView.scaleX = 0.96f
            itemView.scaleY = 0.96f
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(430L)
                .setStartDelay(delay)
                .setInterpolator(Curves.friction)
                .start()
        }

        /** 复位到最终状态（复用 / 已播过动画时调用） */
        fun resetTransform() {
            itemView.animate().cancel()
            itemView.alpha = 1f
            itemView.translationY = 0f
            itemView.translationX = 0f
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            // 星标也可能停在弹跳中间（holder 被复用），一起复位
            ivStar.animate().cancel()
            ivStar.scaleX = 1f
            ivStar.scaleY = 1f
        }
    }

    companion object {
        private val enteredKeys: MutableSet<String> = HashSet()
    }
}
