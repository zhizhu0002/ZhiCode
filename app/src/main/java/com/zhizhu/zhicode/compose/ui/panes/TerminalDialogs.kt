package com.zhizhu.zhicode.compose.ui.panes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.overlay.OverlayDialog


private val QuickActions = listOf(
    "粘贴",
    "复制选中内容",
    "重置终端",
    "新建会话",
    "重命名会话",
    "关闭会话",
    "结束 shell",
    "字体变小",
    "字体变大",
    "重载 termux.properties",
    "切换常亮锁",
)

/** 十一项快捷动作。 */
@Composable
internal fun TerminalQuickActionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        maxWidth = ZhiDialogWidth.Compact,
        largeScreen = true,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        // 原对话框没有按钮区，只有一个可选列表 —— 动作本身就带关闭语义。
        // `DialogShell` 要求一个 actions 槽，给空的就是"没有按钮"。
        DialogShell(title = "终端操作", actions = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                QuickActions.forEachIndexed { index, label ->
                    BasicComponent(
                        title = label,
                        onClick = {
                            onAction(index)
                            onDismiss()
                        },
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 重命名会话。 */
@Composable
internal fun TerminalRenameDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 只在重新打开时重置草稿：关闭动画期间保留最后输入，避免退场帧先闪回空串。
    var draft by remember { mutableStateOf(initial) }
    LaunchedEffect(show) {
        if (show) draft = initial
    }
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        maxWidth = ZhiDialogWidth.Compact,
        largeScreen = true,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "重命名会话",
            footer = {
                ZhiTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "会话名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            actions = {
                SecondaryButton(text = "取消", onClick = onDismiss)
                PrimaryButton(
                    text = "重命名",
                    onClick = { onConfirm(draft) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {}
    }
}
