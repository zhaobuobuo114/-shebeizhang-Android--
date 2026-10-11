package com.deviceledger.app.ui

import android.app.Dialog
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.util.BackupStore
import com.deviceledger.app.util.DateUtil

/**
 * 数据与备份面板：导出、导入、自动备份、回收站、勋章墙五件事的入口。
 *
 * 面板本身不碰数据，具体动作都由 [DataActions] 交回主界面去做，
 * 这样文件选择器（要在 Activity 上注册）与落库逻辑都留在主界面那一处。
 */
object DataSheet {

    data class DataInfo(
        val deviceCount: Int,
        val trashCount: Int,
        val badgeCount: Int,
        val backups: List<BackupStore.BackupFile>
    )

    class DataActions(
        val onExportFile: () -> Unit,
        val onShare: () -> Unit,
        val onImportPick: () -> Unit,
        val onRestoreBackup: (slot: Int) -> Unit,
        val onOpenTrash: () -> Unit,
        val onOpenBadges: () -> Unit
    )

    /**
     * @param info 每次重绘内容时重新取一次，恢复备份之后刷新就能看到最新的条数
     */
    fun show(
        activity: AppCompatActivity,
        theme: AppTheme,
        info: () -> DataInfo,
        actions: DataActions
    ): Dialog {
        val ctx = activity
        return SheetKit.show(activity, theme, "数据与备份") { body, dismiss ->
            val paint = {
                body.removeAllViews()
                val cur = info()

                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        "导出的文件安卓版与鸿蒙版通用，可以互相导入：一次带走设备、回收站与勋章。" +
                            "整个过程都在本机完成，不联网、不上传。"
                    )
                )

                body.addView(
                    SheetKit.buttonRow(
                        ctx,
                        SheetKit.button(ctx, theme, "导出到文件", true) { actions.onExportFile() },
                        SheetKit.button(ctx, theme, "分享", false) { actions.onShare() },
                        SheetKit.button(ctx, theme, "从文件导入", false) { actions.onImportPick() }
                    )
                )

                // ---- 自动备份 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, "自动备份"))
                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        "每次改动数据前自动留一份快照，最多 " + BackupStore.SLOTS +
                            " 份，超出就覆盖最旧的。恢复前会先把当前数据也留一份。"
                    )
                )
                if (cur.backups.isEmpty()) {
                    body.addView(SheetKit.note(ctx, theme, "还没有快照，改动一次数据后就会出现。"))
                } else {
                    for (b in cur.backups) {
                        val value = b.deviceCount.toString() + " 台设备" +
                            (if (b.badgeCount > 0) " · 勋章 " + b.badgeCount else "")
                        body.addView(
                            SheetKit.entryRow(ctx, theme, DateUtil.formatClock(b.at), value) {
                                actions.onRestoreBackup(b.slot)
                            }
                        )
                    }
                }

                // ---- 回收站 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, "回收站"))
                body.addView(
                    SheetKit.entryRow(
                        ctx, theme,
                        "回收站",
                        cur.trashCount.toString() + " 条 · 保留 " + BackupStore.KEEP_DAYS + " 天"
                    ) {
                        dismiss()
                        actions.onOpenTrash()
                    }
                )

                // ---- 勋章 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, "里程碑勋章"))
                body.addView(
                    SheetKit.entryRow(
                        ctx, theme,
                        "里程碑勋章",
                        "已点亮 " + cur.badgeCount + " / " + com.deviceledger.app.model.Badges.ALL.size
                    ) {
                        dismiss()
                        actions.onOpenBadges()
                    }
                )

                val tail = android.widget.TextView(ctx)
                tail.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    SheetKit.dp(ctx, 4f)
                )
                body.addView(tail)
            }
            paint()
        }
    }
}
