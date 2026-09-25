package com.iqge.iqcode.compose.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板写入。
 *
 * 抽出来是因为它有一个**很容易踩的坑**：Android 13 起系统会在复制时弹出自己的
 * 确认提示（"已复制"），此时应用再弹一次自己的提示就是重复打扰。
 * 所以 [copy] 返回"系统是否已经提示过"，让调用方决定还要不要给自己的反馈。
 *
 * 另一个坑是空内容：`setPrimaryClip` 一个空串会清空剪贴板，用户原来复制的东西
 * 就没了。宁可什么都不做，也不要无声地把别人的剪贴板毁掉。
 */
internal object Clipboard {

    /**
     * 写入剪贴板。
     *
     * @return 写入成功返回 true；[text] 为空或系统服务不可用返回 false（此时调用方应给出失败反馈）。
     */
    fun copy(context: Context, label: String, text: String): Boolean {
        if (text.isEmpty()) return false
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
    }

    /**
     * 系统是否会自动显示"已复制"提示。
     *
     * Android 13（API 33）起系统自带这个 UI，应用侧不该重复提示。
     */
    fun systemShowsCopyToast(): Boolean = android.os.Build.VERSION.SDK_INT >= 33
}
