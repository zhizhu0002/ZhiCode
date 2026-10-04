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
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 终端面板的两个对话框：快捷动作与重命名会话。
 *
 * <p>它们以前是 Java 里现拼的 `AlertDialog`（`setItems` 十一项 + 一个带 `EditText` 的改名框），
 * 与全应用的 Miuix 弹窗风格不一致，也不跟随主题。功能不变，文案已从英文改为中文
 * （只有 `termux.properties` 这种文件名保留原文）。
 */

/**
 * 快捷动作清单。
 *
 * 顺序即下标，与 Java 侧原来的 `switch (w)` 一一对应 —— 调用方按这个下标分发，
 * **不要重排**：重排会让「字体变大」点成「结束 shell」这种不可逆动作。
 *
 * ⚠️ 上面这句注释里**不能**用半角引号去引那两条文案：守卫是按
 * `"标签"` 在文件里的出现次序检查顺序的，注释里先出现一次就会把它当成列表项，
 * 报「位置不对」而实际代码没错。这个坑 `stripComments` 与顺序检查都会踩，
 * 所以这里统一用「」。
 *
 * 文案原来全是英文（沿用上游 Termux 的 `AlertDialog.setItems`），现在改成中文；
 * 只有 `termux.properties` 保留原文 —— 那是**文件名**，翻掉就对不上了。
 */
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

/**
 * 十一项快捷动作。点中即执行并关闭。
 *
 * 每一行直接用 Miuix [BasicComponent]，**外面不再套 Card**：
 * 对话框本体的 [OverlayDialog] 已经是一层卡片，再给每项套一张卡就是
 * 「卡片套卡片」—— 侧栏当初删掉外层 Card 是同一个理由（层级噪音 + 多两层内边距）。
 */
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
        maxWidth = ZhiDialogWidth.Compact,
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
