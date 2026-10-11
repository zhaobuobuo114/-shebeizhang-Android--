package com.deviceledger.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deviceledger.app.databinding.ItemBadgePageBinding
import com.deviceledger.app.databinding.ViewBadgeCelebrationBinding
import com.deviceledger.app.model.BadgeDef
import com.deviceledger.app.model.Badges
import kotlin.math.max
import kotlin.math.min

/**
 * 勋章达成弹屏：全屏遮罩 + 居中勋章特写，背后一朵短小烟花，多枚可以左右划着看。
 *
 * 三处刻意的设计：
 *  · 它是根布局上的一层 View，不是 Dialog —— 少一套窗口生命周期，返回键也走应用已有的拦截；
 *  · 震动走 performHapticFeedback，不申请 VIBRATE 权限，用户在系统里关掉触感时它自己就不震；
 *  · 不自动收起（用户要能慢慢看），但入场 1.2s 后淡出一行"轻点空白处关闭"。
 */
class BadgeCelebration(
    private val activity: AppCompatActivity,
    private val binding: ViewBadgeCelebrationBinding
) {

    private val ctx: Context = activity
    private val density = ctx.resources.displayMetrics.density
    private var theme: AppTheme = AppTheme.of(false)

    private var backCallback: OnBackPressedCallback? = null
    private var entrance: ValueAnimator? = null
    private val pending = ArrayList<Runnable>()
    private var shown = false

    val isShowing: Boolean get() = shown

    /** items 是本次新点亮的勋章（id -> 达成时间）；为空什么都不做 */
    fun show(t: AppTheme, items: List<Pair<BadgeDef, Long>>) {
        if (items.isEmpty() || shown) return
        theme = t
        shown = true

        val root = binding.badgeLayer
        // 这里刻意不用 theme.scrim：深色主题的遮罩是 60% 黑，压在近黑的页面上几乎压不出
        // 层次，勋章会跟底下的列表糊在一起。弹屏要的是"世界先暗下去"，所以给一档更重的专用底
        binding.badgeScrim.setBackgroundColor(if (t.dark) DIM_DARK else DIM_LIGHT)
        binding.badgeHint.setTextColor(t.sub)
        binding.badgePageLabel.setTextColor(t.sub)
        binding.badgeHint.alpha = 0f

        val pager = binding.badgePager
        pager.adapter = PageAdapter(items)
        pager.setCurrentItem(0, false)
        // 只有一枚时不该能滑，也不该出现页码
        pager.isUserInputEnabled = items.size > 1
        binding.badgePageLabel.visibility = if (items.size > 1) View.VISIBLE else View.GONE
        binding.badgePageLabel.text = pageText(1, items.size)
        pager.unregisterOnPageChangeCallback(pageCallback)
        pager.registerOnPageChangeCallback(pageCallback)

        root.visibility = View.VISIBLE
        root.alpha = 0f
        root.animate().cancel()
        root.animate().alpha(1f).setDuration(FADE_MS).start()

        // 遮罩本身吃掉点击：点勋章以外的任何地方都关闭
        root.setOnClickListener { dismiss() }

        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        runEntrance(items.size == 1)
        // 烟花与第二下震动都放在"落位那一刻"，而不是 t=0 —— 勋章还在半空中就起爆会显得抢拍
        post(LAND_MS) {
            haptic(HapticFeedbackConstants.LONG_PRESS)
            sparkle(t)
        }
        post(HIDE_HINT_MS) { binding.badgeHint.animate().alpha(1f).setDuration(FADE_MS).start() }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
            }
        }.also { activity.onBackPressedDispatcher.addCallback(it) }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        entrance?.cancel(); entrance = null
        clearPending()
        binding.badgeSparkle.stop()
        binding.badgePager.unregisterOnPageChangeCallback(pageCallback)
        backCallback?.remove(); backCallback = null
        val root = binding.badgeLayer
        root.setOnClickListener(null)
        root.animate().cancel()
        root.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            root.visibility = View.GONE
        }.start()
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            binding.badgePageLabel.text = pageText(position + 1, binding.badgePager.adapter?.itemCount ?: 1)
        }
    }

    private fun pageText(i: Int, n: Int): String = "$i / $n"

    /**
     * 入场：从下方升起 + 转半圈 + 落位时缩放收回 1.12→1.0。
     * 三条属性用一个 ValueAnimator 分段驱动 —— ViewPropertyAnimator 每个 view 只有一条链，
     * 分头 animate 会互相掐掉。
     */
    private fun runEntrance(firstPageOnly: Boolean) {
        binding.badgePager.post {
            val icon = firstVisibleIcon() ?: return@post
            if (!firstPageOnly) {
                icon.alpha = 1f; icon.translationY = 0f; icon.rotation = 0f
                icon.scaleX = 1f; icon.scaleY = 1f
                return@post
            }
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.duration = ENTRANCE_MS
            va.interpolator = Curves.easeOut
            va.addUpdateListener {
                val p = it.animatedValue as Float
                icon.alpha = min(1f, p * 3f)
                icon.translationY = (1f - p) * RISE_DP * density
                icon.rotation = (p - 1f) * 180f
                // 最后 26% 的时间把 1.12 收到 1.0，就是"落位那一下轻弹"
                val settle = max(0f, (p - 0.74f) / 0.26f)
                val s = 1.12f - 0.12f * settle
                icon.scaleX = s; icon.scaleY = s
            }
            entrance?.cancel()
            entrance = va
            va.start()
        }
    }

    private fun firstVisibleIcon(): android.widget.ImageView? {
        val rv = binding.badgePager.getChildAt(0) as? RecyclerView ?: return null
        val child = rv.getChildAt(0) ?: return null
        return ItemBadgePageBinding.bind(child).pageIcon
    }

    private fun sparkle(t: AppTheme) {
        binding.badgeSparkle.burst(intArrayOf(t.primary, t.primary2, t.accent, t.gold))
    }

    private fun haptic(code: Int) {
        binding.badgeLayer.performHapticFeedback(code)
    }

    private fun post(delayMs: Long, action: () -> Unit) {
        // 排队中的任务逐个记句柄：只存一个字段的话，后一个会把前一个的句柄覆盖掉，
        // 关闭时前一个任务仍然会回调到已经收起的弹屏上
        val r = object : Runnable {
            override fun run() {
                pending.remove(this)
                action()
            }
        }
        pending.add(r)
        binding.badgeLayer.postDelayed(r, delayMs)
    }

    private fun clearPending() {
        for (r in pending.toList()) binding.badgeLayer.removeCallbacks(r)
        pending.clear()
    }

    private class PageHolder(val b: ItemBadgePageBinding) : RecyclerView.ViewHolder(b.root)

    private inner class PageAdapter(val items: List<Pair<BadgeDef, Long>>) :
        RecyclerView.Adapter<PageHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
            PageHolder(ItemBadgePageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val (def, at) = items[position]
            holder.b.pageIcon.setImageResource(def.iconRes)
            holder.b.pageName.text = def.name
            holder.b.pageName.setTextColor(theme.text)
            holder.b.pageDate.text = Badges.dateText(at) + " 达成"
            holder.b.pageDate.setTextColor(theme.primary)
            // 第一页由入场动画接手透明度；其余页直接可见
            if (position != 0) {
                holder.b.pageIcon.alpha = 1f
                holder.b.pageIcon.translationY = 0f
                holder.b.pageIcon.rotation = 0f
                holder.b.pageIcon.scaleX = 1f
                holder.b.pageIcon.scaleY = 1f
            }
        }
    }

    companion object {
        private const val ENTRANCE_MS = 620L
        private const val FADE_MS = 240L
        private const val RISE_DP = 120f
        private const val HIDE_HINT_MS = 1200L
        private const val LAND_MS = 460L

        /** 弹屏专用遮罩：比 theme.scrim 重，深色下才压得住深色页面 */
        private const val DIM_DARK = 0xE6000000.toInt()
        private const val DIM_LIGHT = 0xB3000000.toInt()
    }
}
