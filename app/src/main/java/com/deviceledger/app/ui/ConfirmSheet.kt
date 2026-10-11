package com.deviceledger.app.ui

import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 一句问话 + 两个按钮的确认面板。
 *
 * 恢复备份、清空回收站这类"点了就回不来"的动作都走它，
 * 从底部升起，样式与数据面板一致。
 */
object ConfirmSheet {

    fun show(
        activity: AppCompatActivity,
        theme: AppTheme,
        title: String,
        message: String,
        yesText: String,
        onYes: () -> Unit
    ): Dialog {
        val ctx = activity
        return SheetKit.show(activity, theme, title) { body, dismiss ->
            val msg = SheetKit.note(ctx, theme, message)
            msg.setTextColor(theme.text2)
            body.addView(msg)

            val no = SheetKit.button(ctx, theme, "取消", false) { dismiss() }
            val yes = SheetKit.button(ctx, theme, yesText, true) {
                dismiss()
                onYes()
            }
            val row = SheetKit.buttonRow(ctx, no, yes)
            row.gravity = Gravity.END
            body.addView(row)

            val spacer = TextView(ctx)
            spacer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                SheetKit.dp(ctx, 6f)
            )
            body.addView(spacer)
        }
    }
}
