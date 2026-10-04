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
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
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

/**
 * 文件面板自己的三个弹窗：新建 / 重命名 / 删除确认。
 *
 * ## 为什么从「行内卡片」改成弹窗
 *
 * 这三个表单原先画在文件列表**里面**（`FileNameFormCard` / `FileDeleteCard`，
 * 都是列表上方的一张卡）。那样有三个毛病，用户给的截图里都能看到：
 *
 *  1. **列表被推下去**：弹层出现时下面的文件整体下移，点击的那一条跑到屏幕外，
 *     关掉之后又弹回来 —— 在滚动过的长目录里尤其明显（列表跳两次）。
 *  2. **"新建"有两个入口却都不对**：面包屑行上并排放着「新建文件」「新建文件夹」
 *     两个图标，占地方又看不出区别；而小米文件管理器那一行只有**一个** `+`
 *     （`res/layout/phone_file_explorer_list.xml` 里就是单个 `@id/action_create`，
 *     `contentDescription` 是 `@string/create_folder`＝「新建」）。
 *  3. **失败无处显示**：提交失败原本只写 `state.message`，而 `MessageBar` 挂在
 *     `ChatArea` 里 —— 在文件页上提交失败时界面上**什么都没有**，弹层还照常关掉，
 *     看起来像"建成功了但列表里没有"。小米的 `textinput_dialog.xml` 是
 *     「标题 + 一个输入框 + 一行默认 `gone` 的错误行」，错误就地在弹窗里报。
 *
 * 所以收成弹窗，并且错误行**留在弹窗内**（[ZhiFieldError] 渲染 `FileNameForm.error`）。
 *
 * ## 为什么放在 `ui/panes/` 而不是 `ui/dialogs/`
 *
 * 与 `TerminalDialogs.kt` 同一约定：**某个面板自己的弹窗跟着那个面板走**。
 * `ui/dialogs/` 里放的是跨面板共用的那些（授权、计划审批、通用选择器）。
 *
 * ## 错误行的时机：用 `touched` 而不是"永远显示"
 *
 * `ZhiFieldError(message, touched)` 在 `!touched` 时**不渲染**（见 `FieldErrorAlignmentTest`）。
 * 新建弹窗一打开名字框是空的，如果无条件显示错误行，用户还没开始填就被一句
 * 「名字不能为空」指责 —— 那正是那张测试守着的"一打开表单就一片红字"。
 * 判据因此是：**打过字（draft 非空）或者上一次提交失败过（failure 非空）才飘红**。
 */
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

/**
 * 名字输入 + 错误行。新建与重命名共用 —— 两处的校验规则、错误行时机、
 * 输入框样式必须一模一样（[FileNameForm] 的注释里已经说了为什么合成一个模型）。
 */
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

/**
 * 删除确认。
 *
 * <p>逐字说清代价：删一个目录会带走里面的全部内容，而「确定删除 sub 吗？」
 * 等于没告诉用户这件事。条数来自 `FileOps.countForDelete`（不跟符号链接），
 * 多选时按条累加 —— 所以文案里的数字与"被删的条数"**不是一回事**，
 * 这一点原来那版也是这么写的（[FileDeletePrompt.destructive]）。
 */
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
                    fontSize = ZhiTextScale.Caption,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                )
            }
        }
    }
}
