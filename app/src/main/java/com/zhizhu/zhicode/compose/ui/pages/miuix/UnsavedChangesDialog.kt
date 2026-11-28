package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UnsavedChangesDialog(
    visible: Boolean,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    // 退出动画期间 visible 已经翻成 false，内容不能跟着空掉。
    var shown by remember { mutableStateOf(visible) }
    if (visible) shown = true
    OverlayDialog(
        show = visible,
        onDismissRequest = onKeepEditing,
        // 居中形态：确认框该出现在屏幕中央，而不是从底部升起（同 PermissionOverlay）。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        if (!shown) return@OverlayDialog
        DialogShell(
            title = "放弃未保存的修改？",
            prompt = {
                Text(
                    text = "这一页有还没保存的内容，返回会丢掉它们。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                )
            },
            actions = {
                SecondaryButton(text = "继续编辑", onClick = onKeepEditing)
                PrimaryButton(
                    text = "放弃修改",
                    onClick = onDiscard,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) { }
    }
}
