package com.deviceledger.app.ui

import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.R
import com.deviceledger.app.model.DeviceCalc
import com.deviceledger.app.model.TrashItem
import com.deviceledger.app.util.BackupStore
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.Fmt
import com.deviceledger.app.util.L10n

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
        return SheetKit.show(activity, theme, L10n.str(ctx, R.string.trash_title)) { body, dismiss ->
            // lateinit：重绘函数自己要在闭包里再调一次（恢复 / 彻底删除后重排列表）
            lateinit var paint: () -> Unit
            paint = {
                body.removeAllViews()
                val list = items()

                // 条数：鸿蒙那边标题右侧写着「7 条 / 7 items」。安卓的标题是 SheetKit 的
                // 固定栏，把数量拼进标题就成了跨语言拼接，所以单起一行 note（p_items 是完整资源）
                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        L10n.quantity(ctx, R.plurals.p_items, list.size, list.size.toString())
                    )
                )
                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        L10n.str(ctx, R.string.trash_hint, BackupStore.KEEP_DAYS.toString())
                    )
                )

                if (list.isEmpty()) {
                    body.addView(SheetKit.note(ctx, theme, L10n.str(ctx, R.string.trash_empty)))
                } else {
                    for (t in list) {
                        body.addView(row(ctx, theme, t, actions, paint))
                    }
                    body.addView(
                        SheetKit.buttonRow(
                            ctx,
                            SheetKit.button(ctx, theme, L10n.str(ctx, R.string.trash_clear), false) { actions.onClear() }
                        )
                    )
                }

                // 底部满宽关闭：鸿蒙有这颗按钮，安卓原先只能去点右上角那个 ✕，
                // 单手在大屏上够不着
                body.addView(
                    SheetKit.buttonRow(
                        ctx,
                        SheetKit.button(ctx, theme, L10n.str(ctx, R.string.editor_close), false) { dismiss() }
                    )
                )

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
        name.text = if (t.item.name.isEmpty()) L10n.str(ctx, R.string.trash_unnamed) else t.item.name
        name.setTextColor(theme.text)
        name.textSize = 13.5f
        name.setSingleLine(true)
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        left.addView(name)

        val meta = TextView(ctx)
        // "用了 N 天" 作为整段传进去：英文要按 N 分单复数（used 1 day / used N days），
        // 而各语言的片段顺序又不同，所以它必须是 trash_meta 的一个参数而不是一串拼接。
        val usedDays = DeviceCalc.daysUsed(t.item)
        // 尾巴上那句"还剩 N 天 / 即将清除"：鸿蒙一直有，安卓这两个键四语言都配了却没人引用，
        // 于是用户看不出这条还能反悔多久。两段都是完整资源，用与模板一致的 ' · ' 相接
        // （变量名避开上面那个装着文字列的 left）
        val daysLeft = TrashItem.daysLeft(t, BackupStore.KEEP_DAYS)
        val daysTail = if (daysLeft > 0) {
            L10n.str(ctx, R.string.trash_days_left, daysLeft.toString())
        } else {
            L10n.str(ctx, R.string.trash_expiring)
        }
        meta.text = L10n.str(
            ctx, R.string.trash_meta,
            Fmt.moneyLabel(ctx, t.item.price),
            DateUtil.displayDate(ctx, t.item.buyDate),
            L10n.quantity(ctx, R.plurals.p_used_days, usedDays, usedDays.toString()),
            DateUtil.formatStamp(ctx, t.deletedAt)
        ) + " · " + daysTail
        meta.setTextColor(theme.sub)
        meta.textSize = 11.5f
        meta.setSingleLine(true)
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        left.addView(meta)
        box.addView(left)

        val restore = SheetKit.button(ctx, theme, L10n.str(ctx, R.string.trash_restore), true) {
            actions.onRestore(t.item.id)
            repaint()
        }
        box.addView(restore)

        val gap = TextView(ctx)
        gap.layoutParams = LinearLayout.LayoutParams(SheetKit.dp(ctx, 6f), 0)
        box.addView(gap)

        val drop = SheetKit.button(ctx, theme, L10n.str(ctx, R.string.trash_purge), false) {
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
