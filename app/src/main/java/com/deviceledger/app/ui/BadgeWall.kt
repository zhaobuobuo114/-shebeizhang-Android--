package com.deviceledger.app.ui

import android.app.Dialog
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.model.BadgeDef
import com.deviceledger.app.model.Badges
import com.deviceledger.app.util.DateUtil

/**
 * 勋章墙：14 枚里程碑勋章，达成的点亮并显示获得日期，没达成的置灰并写清条件。
 *
 * 判定只看数据，达成即永久点亮——之后数字回落（比如删掉设备导致总投入下降）
 * 也不会熄灭，拿到过就是拿到过。
 */
object BadgeWall {

    private const val COLS = 3

    fun show(
        activity: AppCompatActivity,
        theme: AppTheme,
        state: () -> Map<String, Long>
    ): Dialog {
        val ctx = activity
        return SheetKit.show(activity, theme, "里程碑勋章") { body, _ ->
            val map = state()
            body.addView(
                SheetKit.note(
                    ctx, theme,
                    "已点亮 " + map.size + " / " + Badges.ALL.size +
                        " · 达成后一直保留，即便数字回落也不会熄灭。"
                )
            )

            var index = 0
            while (index < Badges.ALL.size) {
                val rowItems = ArrayList<BadgeDef>()
                val end = minOf(index + COLS, Badges.ALL.size)
                for (i in index until end) rowItems.add(Badges.ALL[i])
                body.addView(row(ctx, theme, rowItems, map))
                index = end
            }

            val tail = TextView(ctx)
            tail.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                SheetKit.dp(ctx, 6f)
            )
            body.addView(tail)
        }
    }

    private fun row(
        ctx: android.content.Context,
        theme: AppTheme,
        items: List<BadgeDef>,
        state: Map<String, Long>
    ): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = SheetKit.dp(ctx, 12f)
        row.layoutParams = lp

        for (i in items.indices) {
            if (i > 0) {
                val gap = TextView(ctx)
                gap.layoutParams = LinearLayout.LayoutParams(SheetKit.dp(ctx, 8f), 0)
                row.addView(gap)
            }
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            fillCell(box, ctx, theme, items[i], state)
            row.addView(box)
        }
        return row
    }

    private fun fillCell(
        host: LinearLayout,
        ctx: android.content.Context,
        theme: AppTheme,
        def: BadgeDef,
        state: Map<String, Long>
    ) {
        val at = state[def.id] ?: 0L
        val on = at > 0L

        // 勋章本体：专属插画自带圆底、描边与丝带，这里不再另画一圈
        val size = SheetKit.dp(ctx, 52f)
        val badge = ImageView(ctx)
        badge.setImageResource(def.iconRes)
        // 没点亮：去色 + 压暗，轮廓还在但一眼看得出"没拿到"。
        // 灰度在运行时算，不另存一套灰色图，省掉一半素材体积
        if (!on) {
            val m = ColorMatrix()
            m.setSaturation(0f)
            badge.colorFilter = ColorMatrixColorFilter(m)
        }
        badge.alpha = if (on) 1f else 0.35f
        val bparams = LinearLayout.LayoutParams(size, size)
        bparams.bottomMargin = SheetKit.dp(ctx, 7f)
        badge.layoutParams = bparams
        host.addView(badge)

        val name = TextView(ctx)
        name.text = def.name
        name.textSize = 12f
        name.gravity = Gravity.CENTER
        name.setTextColor(if (on) theme.text else theme.sub)
        name.setSingleLine(true)
        host.addView(name)

        val sub = TextView(ctx)
        sub.text = if (on) DateUtil.formatStamp(at) + " 达成" else def.desc
        sub.textSize = 10.5f
        sub.gravity = Gravity.CENTER
        sub.setTextColor(if (on) theme.primary else theme.sub)
        sub.alpha = if (on) 1f else 0.8f
        sub.maxLines = 2
        sub.setLineSpacing(SheetKit.dp(ctx, 1f).toFloat(), 1f)
        val sparams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sparams.topMargin = SheetKit.dp(ctx, 2f)
        sub.layoutParams = sparams
        host.addView(sub)
    }
}
