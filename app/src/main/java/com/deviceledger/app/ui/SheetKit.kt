package com.deviceledger.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.deviceledger.app.databinding.DialogSheetBinding

/**
 * 底部面板的公共骨架与几样小控件。
 *
 * 数据与备份、回收站、勋章墙三处都从这里起手，标题、配色、圆角、关闭方式因此完全一致；
 * 内容由各自的 show() 往 body 里填，不用为每处再写一份布局。
 */
object SheetKit {

    /** 面板最高占屏幕的比例，超了就让内容区自己滚 */
    private const val MAX_RATIO = 0.82f

    /**
     * 弹出一块底部面板。[fill] 拿到内容容器和一个"收起面板"的回调。
     * 返回的 Dialog 由调用方持有。
     */
    fun show(
        activity: AppCompatActivity,
        theme: AppTheme,
        title: String,
        fill: (body: LinearLayout, dismiss: () -> Unit) -> Unit
    ): Dialog {
        val density = activity.resources.displayMetrics.density
        val binding = DialogSheetBinding.inflate(activity.layoutInflater)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        // 面板窗口在 Android 15+ 同样是 edge-to-edge，底部按钮会落进导航手势区。
        // 让遮罩层吃系统栏与键盘的 inset：scrim 是整块背景，留白区域视觉不变，
        // 但贴着底部的那张卡片会被顶上来。
        ViewCompat.setOnApplyWindowInsetsListener(binding.sheetRoot) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.sheetRoot.setBackgroundColor(theme.scrim)
        binding.sheetCard.background = roundedTopDrawable(theme.card, 20f, density)
        binding.sheetTitle.text = title
        binding.sheetTitle.setTextColor(theme.text)
        binding.sheetClose.setTextColor(theme.sub)

        // 点面板以外的遮罩就收起；面板本身吃掉点击，不会误关
        binding.sheetRoot.setOnClickListener { dialog.dismiss() }
        binding.sheetCard.setOnClickListener { }

        val dismiss = { dialog.dismiss() }
        binding.sheetClose.setOnClickListener { dismiss() }

        fill(binding.sheetBody, dismiss)

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // 内容太长时把面板压到最多 82% 屏高，让内容区滚动而不是顶到屏幕外
        binding.sheetCard.post {
            val max = (binding.sheetRoot.height * MAX_RATIO).toInt()
            if (max > 0 && binding.sheetCard.height > max) {
                val lp = binding.sheetCard.layoutParams
                lp.height = max
                binding.sheetCard.layoutParams = lp
                binding.sheetScroll.layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
        }
        return dialog
    }

    /* ---------------- 通用小控件 ---------------- */

    fun dp(ctx: android.content.Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    /** 分节标题：小而灰，用来把面板分成几块 */
    fun sectionTitle(ctx: android.content.Context, theme: AppTheme, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(theme.sub)
        tv.textSize = 12f
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(ctx, 16f)
        lp.bottomMargin = dp(ctx, 8f)
        tv.layoutParams = lp
        return tv
    }

    /** 说明文字：跟着正文色，行距放松一点 */
    fun note(ctx: android.content.Context, theme: AppTheme, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(theme.sub)
        tv.textSize = 12.5f
        tv.setLineSpacing(dp(ctx, 3f).toFloat(), 1f)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return tv
    }

    /** 一行横向排布的操作按钮；filled 为真时是主色实心，否则是浅底描边 */
    fun button(
        ctx: android.content.Context,
        theme: AppTheme,
        text: String,
        filled: Boolean,
        onClick: () -> Unit
    ): TextView {
        val density = ctx.resources.displayMetrics.density
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 13f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(ctx, 14f), dp(ctx, 10f), dp(ctx, 14f), dp(ctx, 10f))
        tv.background = if (filled) {
            roundedDrawable(theme.primary, 11f, density)
        } else {
            borderedDrawable(theme.card2, 11f, theme.line, 1f, density)
        }
        tv.setTextColor(if (filled) theme.onPrimary else theme.text2)
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tv.pressEffect(0.94f)
        tv.setOnClickListener { onClick() }
        return tv
    }

    /** 一行按钮排在一起用的容器 */
    fun buttonRow(ctx: android.content.Context, vararg buttons: TextView): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(ctx, 10f)
        row.layoutParams = lp
        for (i in buttons.indices) {
            if (i > 0) {
                val sp = LinearLayout.LayoutParams(dp(ctx, 8f), 0)
                val gap = android.view.View(ctx)
                gap.layoutParams = sp
                row.addView(gap)
            }
            row.addView(buttons[i])
        }
        return row
    }

    /** 一条可点的列表行：左侧主文案、右侧副文案与箭头 */
    fun entryRow(
        ctx: android.content.Context,
        theme: AppTheme,
        title: String,
        value: String,
        onClick: () -> Unit
    ): LinearLayout {
        val density = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(ctx, 13f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
        row.background = borderedDrawable(theme.card2, 12f, theme.line, 1f, density)

        val t = TextView(ctx)
        t.text = title
        t.setTextColor(theme.text)
        t.textSize = 13.5f
        t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(t)

        if (value.isNotEmpty()) {
            val v = TextView(ctx)
            v.text = value
            v.setTextColor(theme.sub)
            v.textSize = 12f
            row.addView(v)
        }

        val arrow = TextView(ctx)
        arrow.text = "›"
        arrow.setTextColor(theme.sub)
        arrow.textSize = 15f
        val alp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        alp.leftMargin = dp(ctx, 5f)
        arrow.layoutParams = alp
        row.addView(arrow)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(ctx, 8f)
        row.layoutParams = lp

        row.pressEffect(0.98f)
        row.setOnClickListener { onClick() }
        return row
    }
}
