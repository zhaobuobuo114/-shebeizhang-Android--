package com.deviceledger.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.Interpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * 列表动画：只保留"位置挪动"，时长与曲线由调用方指定。
 *
 * 对应鸿蒙版的行为：排序 / "只看收藏"是在 animateTo 闭包里改 @State 的，
 * ArkUI 会把由此引起的子组件位置变化按 animateTo 的参数做成动画；
 * 而增删卡片不在 animateTo 里（也没有 transition），鸿蒙那边是硬切。
 * 所以这里把 add / remove / change 全部关掉，只让 move 播。
 *
 * 为什么自己实现 move 而不是复用 DefaultItemAnimator：
 * 它在 animateMove 里会先调 resetAnimation()，把 ViewPropertyAnimator 的插值器
 * 强行改回系统默认（ValueAnimator 的 AccelerateDecelerate），
 * 我们指定的 Curve.Friction 会被覆盖掉。这里直接自己起动画，曲线才落得住。
 */
class MoveAnimator : DefaultItemAnimator() {

    private var curve: Interpolator = Curves.friction
    private val running = ArrayList<RecyclerView.ViewHolder>()

    init {
        moveDuration = 0L
        addDuration = 0L
        removeDuration = 0L
        changeDuration = 0L
    }

    /**
     * @param durationMs 0 表示不播位移动画（列表硬切重排）。
     * @param interpolator 与鸿蒙 animateTo 的 curve 对应的曲线。
     */
    fun configure(durationMs: Long, interpolator: Interpolator) {
        moveDuration = durationMs
        curve = interpolator
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        val view = holder.itemView
        val dx = (toX - fromX).toFloat()
        val dy = (toY - fromY).toFloat()
        if (moveDuration <= 0L || (dx == 0f && dy == 0f)) {
            view.translationX = 0f
            view.translationY = 0f
            dispatchMoveFinished(holder)
            return false
        }
        // 先挪回旧位置，再动画到 0：和 DefaultItemAnimator 的做法一致
        view.translationX = -dx
        view.translationY = -dy
        dispatchMoveStarting(holder)
        running.add(holder)
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(moveDuration)
            .setInterpolator(curve)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    view.translationX = 0f
                    view.translationY = 0f
                }

                override fun onAnimationEnd(animation: Animator) {
                    view.translationX = 0f
                    view.translationY = 0f
                    finish(holder)
                }
            })
            .start()
        return true
    }

    private fun finish(holder: RecyclerView.ViewHolder) {
        running.remove(holder)
        dispatchMoveFinished(holder)
    }

    /** 新增卡片：交给 VH.playEnter 自己播错峰入场，ItemAnimator 不插手 */
    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        dispatchAddFinished(holder)
        return false
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        dispatchRemoveFinished(holder)
        return false
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preInfo: RecyclerView.ItemAnimator.ItemHolderInfo,
        postInfo: RecyclerView.ItemAnimator.ItemHolderInfo
    ): Boolean {
        // 卡片内容变了也不做交叉淡入淡出：鸿蒙版 ForEach 的 key 里带了内容，
        // 内容一变就是新建子组件、直接替换，配合 VH 的入场动画，不是变化过渡
        if (oldHolder != null) dispatchChangeFinished(oldHolder, true)
        if (newHolder != null) dispatchChangeFinished(newHolder, false)
        return false
    }

    override fun isRunning(): Boolean = running.isNotEmpty() || super.isRunning()

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        item.itemView.animate().cancel()
        running.remove(item)
        super.endAnimation(item)
    }

    override fun endAnimations() {
        for (holder in running.toList()) {
            holder.itemView.animate().cancel()
            holder.itemView.translationX = 0f
            holder.itemView.translationY = 0f
        }
        running.clear()
        super.endAnimations()
    }
}
