package com.deviceledger.app.ui

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import com.deviceledger.app.R

/**
 * 输入框的兼容性加固：躲开两处只在个别厂商 ROM 上才发作的系统行为。
 *
 * 一、文本选择手柄（光标下面那个"水滴"）
 *     点击输入框时系统要把它画出来，会去取 textSelectHandle 指向的 Drawable 并直接解引用。
 *     拿不到可绘制对象时，AOSP 原生会容错跳过，但各家 ROM 的处理并不一致，
 *     所以这里不能留空。主题里已统一配了透明占位 drawable/text_select_handle.xml，
 *     这里再对每个输入框显式指定一次作为双保险
 *     （个别 ROM 不总是照主题里那一份取值；这一步在 API 29 以上才做）。
 *
 * 二、自动填充
 *     设备账是纯本地应用，没有手机号、地址、支付这类信息，自动填充没有收益；
 *     而各家 ROM 的填充服务实现差异较大（含 HarmonyOS 的安卓兼容层），
 *     遇到没有填充满题意图的字段时表现不一。这里直接关掉，少走一条分支。
 */
object FieldCompat {

    fun apply(vararg fields: EditText) {
        for (f in fields) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                f.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            // 这三个 setter 是 API 29 才公开的，低于 29 的系统上调用会直接 NoSuchMethodError，
            // 所以必须挡在数据线之下 —— 那些版本的 TextView 从构造阶段读主题属性，
            // 主题里那一份已经生效，不会因为没有这一步而漏掉。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 每个位置都要一份独立实例：三个手柄各有自己的绘制区域，
                // 共用一个 Drawable 会导致它们被画到同一处。
                val handle = newHandle(f) ?: continue
                val handleLeft = newHandle(f) ?: continue
                val handleRight = newHandle(f) ?: continue
                f.setTextSelectHandle(handle)
                f.setTextSelectHandleLeft(handleLeft)
                f.setTextSelectHandleRight(handleRight)
            }
        }
    }

    private fun newHandle(v: View): Drawable? =
        ResourcesCompat.getDrawable(
            v.resources,
            R.drawable.text_select_handle,
            v.context.theme
        )?.mutate()
}
