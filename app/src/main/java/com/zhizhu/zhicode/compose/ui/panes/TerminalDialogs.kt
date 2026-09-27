package com.zhizhu.zhicode.compose.ui.panes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 终端面板的两个对话框：快捷动作与重命名会话。
 *
 * <p>它们以前是 Java 里现拼的 `AlertDialog`（`setItems` 十一项 + 一个带 `EditText` 的改名框），
 * 与全应用的 Miuix 弹窗风格不一致，也不跟随主题。功能与**文字逐字不变**。
 */

/**
 * 快捷动作清单。
 *
 * 顺序即下标，与 Java 侧原来的 `switch (w)` 一一对应 —— 调用方按这个下标分发，
 * **不要重排**：重排会让"字体变大"点成"杀掉 shell"这种不可逆动作。
 */
private val QuickActions = listOf(
    "Paste",
    "Copy selection",
    "Reset terminal",
    "New session",
    "Rename session",
    "Close session",
    "Kill shell",
    "Font smaller",
    "Font larger",
    "Reload termux.properties",
    "Toggle wake lock",
)

/** 十一项快捷动作。点中即执行并关闭。 */
@Composable
internal fun TerminalQuickActionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        // 原对话框没有按钮区，只有一个可选列表 —— 动作本身就带关闭语义。
        // `DialogShell` 要求一个 actions 槽，给空的就是"没有按钮"。
        DialogShell(title = "Terminal", actions = {}) {
            Column(modifier = Modifier.fillMaxWidth()) {
                QuickActions.forEachIndexed { index, label ->
                    Card(
                        onClick = {
                            onAction(index)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = ZhiRadius.inner,
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        BasicComponent(
                            title = label,
                            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 重命名会话。
 *
 * @param initial 打开时的初始名字（当前会话名）
 */
@Composable
internal fun TerminalRenameDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 只在"打开的那一次"取初值：否则用户打字时宿主推送新快照会把输入冲掉。
    var draft by remember(show) { mutableStateOf(initial) }
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "Rename session",
            footer = {
                ZhiTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "Session name",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            actions = {
                SecondaryButton(text = "Cancel", onClick = onDismiss)
                PrimaryButton(
                    text = "Rename",
                    onClick = { onConfirm(draft) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {}
    }
}
