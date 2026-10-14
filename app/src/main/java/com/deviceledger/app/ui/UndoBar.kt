package com.deviceledger.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 删除后的撤销条：给 5 秒反悔时间。
 *
 * 删掉的设备其实已经进了回收站，这里只是把"立刻放回去"的捷径摆在手边——
 * 就算没点撤销，东西也还在回收站里，30 天内都找得回来。
 */
class UndoBar(
    private val bar: LinearLayout,
    private val text: TextView,
    private val btn: TextView
) {

    companion object {
        private const val HOLD_MS = 5000L
        private const val IN_MS = 220L
        private const val OUT_MS = 180L
    }

    private var pending: Runnable? = null
    private var shown = false

    /** 正在显示：切语言时要不要重取文案，就看这个 */
    val isShown: Boolean
        get() = shown

    /**
     * 只换字，不重启动画、不延长停留时间。
     *
     * 撤销条的文案是 show() 那一刻定型的，驻留 5 秒；这期间用户进「⋯」面板换了语言，
     * 整条提示就会一直留着旧语言（删除后立刻切语言是条很顺的路径）。
     */
    fun retext(message: String) {
        text.text = message
    }

    fun applyTheme(theme: AppTheme) {
        val density = bar.resources.displayMetrics.density
        bar.background = borderedDrawable(theme.card, 14f, theme.line, 1f, density)
        bar.elevation = 8f * density
        text.setTextColor(theme.text2)
        btn.setTextColor(theme.primary)
    }

    fun show(message: String, onUndo: () -> Unit) {
        text.text = message
        btn.setOnClickListener {
            hide()
            onUndo()
        }
        cancelPending()
        if (!shown) {
            shown = true
            // 先把退场动画的收尾回调摘掉再取消：hide() 的 onAnimationEnd 会把 visibility 写回 GONE，
            // 若正好这次 show() 卡在它那 180ms 里，撤销条就会"显示了但看不见"，并且本次会话再也不出现
            bar.animate().setListener(null).cancel()
            bar.visibility = View.VISIBLE
            bar.alpha = 0f
            bar.translationY = (24 * bar.resources.displayMetrics.density)
            bar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(IN_MS)
                .setInterpolator(Curves.easeOut)
                .start()
        } else {
            bar.alpha = 1f
            bar.translationY = 0f
        }
        val task = Runnable { hide() }
        pending = task
        bar.postDelayed(task, HOLD_MS)
    }

    fun hide() {
        cancelPending()
        if (!shown) return
        shown = false
        bar.animate().cancel()
        bar.animate()
            .alpha(0f)
            .translationY(16 * bar.resources.displayMetrics.density)
            .setDuration(OUT_MS)
            .setInterpolator(Curves.easeIn)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bar.visibility = View.GONE
                    bar.animate().setListener(null)
                }
            })
            .start()
    }

    private fun cancelPending() {
        pending?.let { bar.removeCallbacks(it) }
        pending = null
    }

    /** Activity 销毁时用：只摘排队中的隐藏任务并立刻收起，不再起新动画 */
    fun dispose() {
        cancelPending()
        bar.animate().setListener(null).cancel()
        shown = false
        bar.visibility = View.GONE
    }
}
