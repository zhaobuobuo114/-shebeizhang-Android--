package com.deviceledger.app.ui

import android.app.Dialog
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.R
import com.deviceledger.app.util.BackupStore
import com.deviceledger.app.util.DateUtil
import com.deviceledger.app.util.L10n
import com.deviceledger.app.util.Lang

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
        val backups: List<BackupStore.BackupFile>,
        /** 当前选择的语言：`system` 或四种 tag 之一，用来打勾 */
        val languageTag: String
    )

    class DataActions(
        val onExportFile: () -> Unit,
        val onShare: () -> Unit,
        val onImportPick: () -> Unit,
        val onRestoreBackup: (slot: Int) -> Unit,
        val onOpenTrash: () -> Unit,
        val onOpenBadges: () -> Unit,
        val onPickLanguage: (tag: String) -> Unit
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
        return SheetKit.show(activity, theme, L10n.str(ctx, R.string.data_title)) { body, dismiss ->
            val paint = {
                body.removeAllViews()
                val cur = info()

                // ---- 语言 ----
                // 放在最前面：界面已经是错语言的人，最需要第一眼就看到这一栏。
                // 五种选项的文案用自称（简体中文 / 繁體中文 / 日本語 / English），
                // 四种语言下都一样，这样即便完全看不懂当前界面也能认出我要的那一种。
                body.addView(SheetKit.sectionTitle(ctx, theme, L10n.str(ctx, R.string.data_lang_title)))
                val options = listOf(
                    L10n.FOLLOW_SYSTEM to R.string.lang_system,
                    Lang.ZH_CN.tag to R.string.lang_zh_cn,
                    Lang.ZH_TW.tag to R.string.lang_zh_tw,
                    Lang.JA.tag to R.string.lang_ja,
                    Lang.EN.tag to R.string.lang_en
                )
                for ((tag, res) in options) {
                    body.addView(
                        SheetKit.entryRow(
                            ctx, theme,
                            L10n.str(ctx, res),
                            if (cur.languageTag == tag) "✓" else ""
                        ) {
                            actions.onPickLanguage(tag)
                        }
                    )
                }

                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        L10n.str(ctx, R.string.data_intro)
                    )
                )

                body.addView(
                    SheetKit.buttonRow(
                        ctx,
                        SheetKit.button(ctx, theme, L10n.str(ctx, R.string.data_export), true) { actions.onExportFile() },
                        SheetKit.button(ctx, theme, L10n.str(ctx, R.string.data_share), false) { actions.onShare() },
                        SheetKit.button(ctx, theme, L10n.str(ctx, R.string.data_import), false) { actions.onImportPick() }
                    )
                )

                // ---- 自动备份 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, L10n.str(ctx, R.string.data_auto_title)))
                body.addView(
                    SheetKit.note(
                        ctx, theme,
                        L10n.str(ctx, R.string.data_auto_desc, BackupStore.SLOTS.toString())
                    )
                )
                if (cur.backups.isEmpty()) {
                    body.addView(SheetKit.note(ctx, theme, L10n.str(ctx, R.string.data_no_snapshots)))
                } else {
                    for (b in cur.backups) {
                        val devices = L10n.str(ctx, R.string.data_snapshot_devices, b.deviceCount.toString())
                        val value = if (b.badgeCount > 0) {
                            L10n.str(
                                ctx, R.string.data_snapshot_row,
                                devices, L10n.str(ctx, R.string.data_snapshot_badges, b.badgeCount.toString())
                            )
                        } else devices
                        body.addView(
                            SheetKit.entryRow(ctx, theme, DateUtil.formatClock(ctx, b.at), value) {
                                actions.onRestoreBackup(b.slot)
                            }
                        )
                    }
                }

                // ---- 回收站 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, L10n.str(ctx, R.string.data_section_trash)))
                val trashValue = if (cur.trashCount == 0) {
                    L10n.str(ctx, R.string.data_trash_row_empty)
                } else {
                    L10n.str(ctx, R.string.data_trash_row, cur.trashCount.toString(), BackupStore.KEEP_DAYS.toString())
                }
                body.addView(
                    SheetKit.entryRow(
                        ctx, theme,
                        L10n.str(ctx, R.string.data_section_trash),
                        trashValue
                    ) {
                        dismiss()
                        actions.onOpenTrash()
                    }
                )

                // ---- 勋章 ----
                body.addView(SheetKit.sectionTitle(ctx, theme, L10n.str(ctx, R.string.data_section_badges)))
                body.addView(
                    SheetKit.entryRow(
                        ctx, theme,
                        L10n.str(ctx, R.string.data_section_badges),
                        L10n.str(
                            ctx, R.string.data_badges_row,
                            cur.badgeCount.toString(), com.deviceledger.app.model.Badges.ALL.size.toString()
                        )
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
