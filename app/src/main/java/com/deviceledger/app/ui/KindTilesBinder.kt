package com.deviceledger.app.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.deviceledger.app.R
import com.deviceledger.app.model.DEVICE_KINDS
import com.deviceledger.app.model.DeviceKind
import com.deviceledger.app.model.kindIconRes

/**
 * 设备类型格子（41 类，每种都有专属 Q 版图标）。
 *
 * 这里没有用 RecyclerView + GridLayoutManager：那会把格子均分拉伸，
 * 而鸿蒙版用的是 FlexWrap + 固定 60x60 的格子。为了两边长得一模一样，
 * 改成直接往 WrapLayout 里塞固定尺寸的格子，排满一行自动换行。
 */
class KindTilesBinder(
    private val container: WrapLayout,
    private val onPick: (String) -> Unit
) {

    var theme: AppTheme = AppTheme.lightTheme()
    var selectedKey: String = "phone"

    private class Tile(val view: View, val kind: DeviceKind)

    private val tiles = ArrayList<Tile>()

    fun build() {
        container.removeAllViews()
        tiles.clear()
        val inflater = LayoutInflater.from(container.context)
        for (kind in DEVICE_KINDS) {
            val view = inflater.inflate(R.layout.item_kind, container, false)
            tiles.add(Tile(view, kind))
            container.addView(view)
            view.pressEffect(0.92f)
            view.setOnClickListener { onPick(kind.key) }
        }
        render(false)
    }

    /**
     * @param animate 点格子时传 true：底色 / 描边 / 文字色走 200ms Friction 渐变，
     *                对应鸿蒙版 `animateTo({ duration: 200, curve: Curve.Friction })`。
     *                打开面板、换肤这类场合传 false，直接落到终态。
     */
    fun select(key: String, animate: Boolean = false) {
        if (selectedKey == key) return
        selectedKey = key
        render(animate)
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        render(false)
    }

    private fun render(animate: Boolean) {
        val density = container.resources.displayMetrics.density
        val duration = if (animate) 200L else 0L
        for (tile in tiles) {
            val root = tile.view.findViewById<View>(R.id.kindRoot)
            val icon = tile.view.findViewById<ImageView>(R.id.ivKindIcon)
            val label = tile.view.findViewById<TextView>(R.id.tvKindLabel)
            icon.setImageResource(kindIconRes(tile.kind.key))
            label.text = tile.kind.label

            if (tile.kind.key == selectedKey) {
                label.tintTo(theme.primary, 1f, duration)
                root.tileTo(theme.primarySoft, theme.primary, 1.5f, 13f, density, duration)
            } else {
                label.tintTo(theme.chipText, 1f, duration)
                root.tileTo(theme.card2, theme.primary, 0f, 13f, density, duration)
            }
        }
    }
}
