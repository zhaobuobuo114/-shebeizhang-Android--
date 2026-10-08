package com.deviceledger.app.ui

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deviceledger.app.R
import com.deviceledger.app.databinding.DialogPrivacyBinding
import com.deviceledger.app.util.DeviceStore
import kotlin.math.min

/**
 * 首次启动的条款确认：先把隐私条款与服务条款摆在眼前，勾选同意之后才进应用。
 * 两份条款用系统浏览器打开，应用本身不申请任何网络权限。
 */
object PrivacyGate {

    const val URL_PRIVACY = "https://policiesforge.com/p/r5247n23hd"
    const val URL_TERMS = "https://policiesforge.com/p/r8qza22rha"

    /**
     * 弹出条款确认框。返回 Dialog 由调用方持有，好在退出界面时一并收掉。
     * 勾选同意后回调 [onAgreed]；点「不同意」则直接结束整个应用。
     */
    fun show(activity: AppCompatActivity, theme: AppTheme, onAgreed: () -> Unit): Dialog {
        val density = activity.resources.displayMetrics.density
        val binding = DialogPrivacyBinding.inflate(activity.layoutInflater)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(binding.root)

        binding.privacyRoot.setBackgroundColor(theme.scrim)
        binding.privacyCard.background = borderedDrawable(theme.card, 18f, theme.line, 1f, density)
        binding.privacyTitle.setTextColor(theme.text)
        binding.privacyBody.setTextColor(theme.text2)
        binding.privacyLinkPolicy.setTextColor(theme.primary)
        binding.privacyLinkTerms.setTextColor(theme.primary)
        binding.privacyCheck.setTextColor(theme.text2)
        binding.privacyExit.setTextColor(theme.sub)
        binding.privacyCheck.buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(theme.primary, theme.sub)
        )

        // 没勾选之前按钮是灰的，勾上才亮起来
        val paintAgree = { checked: Boolean ->
            binding.privacyAgree.background = roundedDrawable(
                if (checked) theme.primary else theme.primarySoft,
                12f,
                density
            )
            binding.privacyAgree.setTextColor(if (checked) theme.onPrimary else theme.sub)
        }
        paintAgree(false)
        binding.privacyCheck.setOnCheckedChangeListener { _, checked -> paintAgree(checked) }

        binding.privacyLinkPolicy.setOnClickListener { openUrl(activity, URL_PRIVACY) }
        binding.privacyLinkTerms.setOnClickListener { openUrl(activity, URL_TERMS) }

        binding.privacyAgree.setOnClickListener {
            if (!binding.privacyCheck.isChecked) {
                Toast.makeText(activity, activity.getString(R.string.privacy_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            DeviceStore.saveAgreed(true)
            dialog.dismiss()
            onAgreed()
        }
        binding.privacyExit.setOnClickListener {
            dialog.dismiss()
            activity.finishAffinity()
        }

        dialog.show()
        // 窗口尺寸要等 show 出来再给，否则遮罩盖不满整屏
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // 大屏上卡片不该被拉得太宽
        binding.privacyCard.post {
            val lp = binding.privacyCard.layoutParams
            val avail = binding.privacyRoot.width - (48 * density).toInt()
            if (avail > 0) {
                lp.width = min((340 * density).toInt(), avail)
                binding.privacyCard.layoutParams = lp
            }
        }
        return dialog
    }

    private fun openUrl(activity: AppCompatActivity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // 机器上没有可打开的浏览器时保持原样，不该因此中断确认流程
        }
    }
}
