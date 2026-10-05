package com.deviceledger.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
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

    fun submit(list: List<DeviceItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        notifyDataSetChanged()
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

        /** 点星标：先弹大再回落（和鸿蒙版同为 150ms + 260ms 两段） */
        private fun playStarPop() {
            ivStar.animate().cancel()
            ivStar.animate()
                .scaleX(1.34f)
                .scaleY(1.34f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    ivStar.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }

        /** 错峰入场：透明 + 上移 + 轻微缩放 */
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
                .setInterpolator(AccelerateDecelerateInterpolator())
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
