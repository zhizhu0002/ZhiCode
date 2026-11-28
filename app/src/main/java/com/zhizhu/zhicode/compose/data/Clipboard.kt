package com.zhizhu.zhicode.compose.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 剪贴板写入。 */
internal object Clipboard {

    /** 写入剪贴板。 */
    fun copy(context: Context, label: String, text: String): Boolean {
        if (text.isEmpty()) return false
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
    }

    /** 系统是否会自动显示"已复制"提示。 */
    fun systemShowsCopyToast(): Boolean = android.os.Build.VERSION.SDK_INT >= 33
}
