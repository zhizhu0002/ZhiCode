package com.zhizhu.zhicode.compose.ui.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NewEntryDialog(
    form: FileNameForm?,
    onDraftChange: (String) -> Unit,
    onSubmit: (directory: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    // 官方示例让 OverlayDialog 自己管理进退场；这里只保留最后一份表单数据，
    // 避免 ViewModel 清空 form 后，官方中央动画的退场帧变成空壳。
    val shownForm = rememberLastNonNull(form)
    OverlayDialog(
        show = form != null,
        onDismissRequest = onCancel,
        // 与 Miuix 官方 `CenteredOverlayDialogDemo` 一致：手机也明确采用中央
        // scale/fade + folmeSpring 动画，而不是默认的小屏底部上滑动画。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        // 退场动画期间 form 已经是 null，而这一屏还要画完 —— 直接用它会在
        // 那几帧里把标题和输入框变成空的（表现为"弹窗先空掉再消失"）。
        val shown = shownForm ?: return@OverlayDialog
        DialogShell(
            title = shown.title,
            actions = {
                SecondaryButton(text = "取消", onClick = onCancel)
                // ⚠️ 两个提交按钮**必须都传出去**，而且传的是不同的值：
                // 建文件还是建文件夹由**按下的那个按钮**决定，不再靠扩展名猜
                // （`name.contains('.')` 会把 Makefile、LICENSE、.gitignore 建成目录）。
                PrimaryButton(
                    text = "文件",
                    onClick = { onSubmit(false) },
                    enabled = shown.saveable,
                    modifier = Modifier.padding(start = 8.dp),
                )
                PrimaryButton(
                    text = "文件夹",
                    onClick = { onSubmit(true) },
                    enabled = shown.saveable,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            NameField(form = shown, onDraftChange = onDraftChange, placeholder = "名称")
        }
    }
}

/** 重命名：同一个壳，只有一个「确定」。 */
@Composable
internal fun RenameEntryDialog(
    form: FileNameForm?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    // 与 Miuix `CenteredOverlayDialogDemo` 一样，show 只控制官方动画；内容在
    // 退场完成前不能跟着外部状态一起变空。
    val shownForm = rememberLastNonNull(form)
    OverlayDialog(
        show = form != null,
        onDismissRequest = onCancel,
        // 与 Miuix 官方 `CenteredOverlayDialogDemo` 一致：手机也明确采用中央
        // scale/fade + folmeSpring 动画，而不是默认的小屏底部上滑动画。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val shown = shownForm ?: return@OverlayDialog
        DialogShell(
            title = shown.title,
            actions = {
                SecondaryButton(text = "取消", onClick = onCancel)
                PrimaryButton(
                    text = "确定",
                    onClick = onSubmit,
                    enabled = shown.saveable,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            NameField(form = shown, onDraftChange = onDraftChange, placeholder = "新名字")
        }
    }
}

@Composable
private fun NameField(
    form: FileNameForm,
    onDraftChange: (String) -> Unit,
    placeholder: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ZhiTextField(
            value = form.draft,
            onValueChange = onDraftChange,
            singleLine = true,
            // 新建时名字是空的，用占位符提示"在这里填名字"；重命名时预填了原名，
            // 标签常驻更合适。
            useLabelAsPlaceholder = form.target == null,
            label = placeholder,
        )
        // 见文件头的「错误行的时机」：打过字或失败过才飘红。
        ZhiFieldError(form.error, touched = form.draft.isNotEmpty() || form.failure != null)
    }
}

/** 删除确认。 */
@Composable
internal fun DeleteConfirmDialog(
    prompt: FileDeletePrompt?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 删除确认也让官方 OverlayDialog 自己完成退场，内容缓存只负责保持最后一帧。
    val shownPrompt = rememberLastNonNull(prompt)
    OverlayDialog(
        show = prompt != null,
        onDismissRequest = onCancel,
        // 与 Miuix 官方 `CenteredOverlayDialogDemo` 一致：手机也明确采用中央
        // scale/fade + folmeSpring 动画，而不是默认的小屏底部上滑动画。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val shown = shownPrompt ?: return@OverlayDialog
        DialogShell(
            title = "删除",
            actions = {
                SecondaryButton(text = "取消", onClick = onCancel)
                PrimaryButton(
                    text = "删除",
                    onClick = onConfirm,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = if (shown.destructive) {
                        "删除${shown.label}？\n其中的 ${shown.count - shown.entries.size} 项内容会一起消失，无法撤销。"
                    } else {
                        "删除${shown.label}？无法撤销。"
                    },
                    color = if (shown.destructive) ZhiColors.red() else scheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                )
            }
        }
    }
}
