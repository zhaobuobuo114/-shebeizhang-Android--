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
        render()
    }

    fun select(key: String) {
        if (selectedKey == key) return
        selectedKey = key
        render()
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        render()
    }

    private fun render() {
        val density = container.resources.displayMetrics.density
        for (tile in tiles) {
            val root = tile.view.findViewById<View>(R.id.kindRoot)
            val icon = tile.view.findViewById<ImageView>(R.id.ivKindIcon)
            val label = tile.view.findViewById<TextView>(R.id.tvKindLabel)
            icon.setImageResource(kindIconRes(tile.kind.key))
            label.text = tile.kind.label

            if (tile.kind.key == selectedKey) {
                label.setTextColor(theme.primary)
                root.background = borderedDrawable(theme.primarySoft, 13f, theme.primary, 1.5f, density)
            } else {
                label.setTextColor(theme.chipText)
                root.background = roundedDrawable(theme.card2, 13f, density)
            }
        }
    }
}
