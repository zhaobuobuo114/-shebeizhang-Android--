package com.deviceledger.app.ui

import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.TrashItem
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.Fmt

/**
 * 回收站：误删的设备先落在这里，30 天内随时原样放回去。
 *
 * 恢复出来的就是删除前的那条记录——收藏、备注、创建时间都还在，
 * 因为删进来时没有改过任何字段。
 */
object TrashSheet {

    class TrashActions(
        val onRestore: (id: String) -> Unit,
        val onDeleteForever: (id: String) -> Unit,
        val onClear: () -> Unit
    )

    fun show(
        activity: AppCompatActivity,
        theme: AppTheme,
        items: () -> List<TrashItem>,
        actions: TrashActions
    ): Dialog {
        val ctx = activity
        return SheetKit.show(activity, theme, "回收站") { body, dismiss ->
            // lateinit：重绘函数自己要在闭包里再调一次（恢复 / 彻底删除后重排列表）
            lateinit var paint: () -> Unit
            paint = {
                body.removeAllViews()
                val list = items()

                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        "删除的设备会在这里留 30 天，期间可以原样恢复；" +
                            "超过 30 天会自动清掉。"
                    )
                )

                if (list.isEmpty()) {
                    body.addView(SheetKit.note(ctx, theme, "回收站是空的。"))
                } else {
                    for (t in list) {
                        body.addView(row(ctx, theme, t, actions, paint))
                    }
                    body.addView(
                        SheetKit.buttonRow(
                            ctx,
                            SheetKit.button(ctx, theme, "清空回收站", false) { actions.onClear() }
                        )
                    )
                }

                val tail = TextView(ctx)
                tail.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    SheetKit.dp(ctx, 4f)
                )
                body.addView(tail)
            }
            paint()
        }
    }

    private fun row(
        ctx: android.content.Context,
        theme: AppTheme,
        t: TrashItem,
        actions: TrashActions,
        repaint: () -> Unit
    ): LinearLayout {
        val density = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(SheetKit.dp(ctx, 13f), SheetKit.dp(ctx, 11f), SheetKit.dp(ctx, 10f), SheetKit.dp(ctx, 11f))
        box.background = borderedDrawable(theme.card2, 12f, theme.line, 1f, density)

        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val name = TextView(ctx)
        name.text = if (t.item.name.isEmpty()) "未命名" else t.item.name
        name.setTextColor(theme.text)
        name.textSize = 13.5f
        name.setSingleLine(true)
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        left.addView(name)

        val meta = TextView(ctx)
        meta.text = "¥" + Fmt.money(t.item.price) + " · " + t.item.buyDate +
            " · 用了 " + DeviceCalc.daysUsed(t.item) + " 天 · " +
            DateUtil.formatStamp(t.deletedAt) + " 删除"
        meta.setTextColor(theme.sub)
        meta.textSize = 11.5f
        meta.setSingleLine(true)
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        left.addView(meta)
        box.addView(left)

        val restore = SheetKit.button(ctx, theme, "恢复", true) {
            actions.onRestore(t.item.id)
            repaint()
        }
        box.addView(restore)

        val gap = TextView(ctx)
        gap.layoutParams = LinearLayout.LayoutParams(SheetKit.dp(ctx, 6f), 0)
        box.addView(gap)

        val drop = SheetKit.button(ctx, theme, "彻底删除", false) {
            actions.onDeleteForever(t.item.id)
            repaint()
        }
        box.addView(drop)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = SheetKit.dp(ctx, 8f)
        box.layoutParams = lp
        return box
    }
}
