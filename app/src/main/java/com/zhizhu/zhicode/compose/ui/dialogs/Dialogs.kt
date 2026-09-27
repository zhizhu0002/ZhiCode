package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.ui.chat.TaskStatusIndicator
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.RiskLevel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 所有模态窗口都走 Miuix `overlay` 包的 [OverlayDialog]。
 *
 * 为什么不用 `window` 包的 `WindowDialog`：它会创建**独立 Android Window**，
 * 既不会继承 `Scaffold` 的 `popupHost`（内部再用 Miuix 弹层组件就会失效），
 * 也无法参与主窗口的 `LayerBackdrop` 背景采样 —— 弹窗背后的模糊会做不出来。
 * 两者参数集完全一致，换过来无需改调用方形状。
 *
 * ## 居中
 *
 * 位置由 `largeScreen` 决定：true → `Alignment.Center`（居中），
 * false → `Alignment.BottomCenter`（贴底）。默认值由 `DialogDefaults.isLargeScreen`
 * 按窗口宽度是否 ≥ 600dp 推断，手机上是 false 会贴底，
 * 所以这里**显式传 `largeScreen = true`** 让窗口居中。
 *
 * ## 手写降到最低
 *
 * - 列表行 → Miuix [BasicComponent]（`title` / `summary` / `startAction` / `endActions`）
 * - 选中指示 → Miuix [Icon] + `MiuixIcons.Basic.Check`（与库自己的下拉列表一致）。
 *   不用 [Checkbox]：它固定 26dp 且是圆的，配 11~13sp 的行文字明显偏大。
 * - 主按钮 → Miuix [Button] + [ButtonDefaults.buttonColorsPrimary]
 * - 次要按钮 → Miuix [Button] + `buttonColors`（与主按钮同形状、只有配色不同）
 * - 开关 → Miuix [Switch]
 *
 * 仅保留一处自定义：左对齐标题。因为 Miuix 的 `title` 参数会把标题强制居中并染色，
 * 与参考图的白字左对齐不一致。
 *
 * 弹窗底色是 `DialogDefaults.backgroundColor` = `Colors.background`，
 * 而深色方案的 `surfaceContainerHigh` **也是同一个值**（从 0.9.4 字节码
 * `darkColorScheme$default` 读出的默认值），卡片直接放在弹窗底上会完全隐身。
 * 解决办法是 `DialogShell(groupBody = true)`：把内容区包进 Miuix `Card`，
 * 由主题提供与弹窗底色的对比度 —— 用纯黑底板会在浅色模式下直接坏掉。
 */


// ---------------------------------------------------------------- 工具授权

@Composable
fun PermissionOverlay(
    request: PermissionRequest?,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    var alwaysAllow by remember(request) { mutableStateOf(false) }

    OverlayDialog(
        show = request != null,
        onDismissRequest = onDeny,
        largeScreen = true,
        maxWidth = 560.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (request == null) return@OverlayDialog
        DialogShell(
            title = "工具授权",
            groupBody = true,
            actions = {
                SecondaryButton(text = "拒绝", onClick = onDeny)
                PrimaryButton(
                    text = "允许",
                    onClick = { if (alwaysAllow) onAlwaysAllow() else onAllowOnce() },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            // 工具名 + 说明 + 风险徽标：一个 Miuix BasicComponent 搞定，不再手拼 Row/Column
            BasicComponent(
                title = request.tool,
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = request.subtitle,
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                startAction = {
                    Icon(
                        imageVector = ZhiIcons.tool(request.tool),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                endActions = {
                    if (request.riskLevel == RiskLevel.HIGH) {
                        // Miuix Badge：容器/文字色走主题，浅色模式自动变浅红底 + 暗红字。
                        // 之前是手写 Surface + Box + Text，且用固定红叠 18% alpha，
                        // 叠在任何底色上都会变成"另一种红"，也不随主题。
                        Badge(
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.error,
                        ) {
                            Text(text = "高风险", fontSize = ZhiTextScale.Footnote)
                        }
                    }
                },
                insideMargin = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            )

            // 详情：Card 提供比弹窗底色更亮的容器
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .heightIn(min = 44.dp, max = 180.dp),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(10.dp),
                colors = CardDefaults.defaultColors(
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(
                    text = request.detail,
                    fontSize = ZhiTextScale.Caption,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = alwaysAllow, onCheckedChange = { alwaysAllow = it })
                Text(
                    text = "始终允许",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.BodySmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------ 提交计划窗口

@Composable
fun PlanApprovalOverlay(
    plan: PlanApproval?,
    onApprove: () -> Unit,
    onRevise: () -> Unit,
    onReviseWithFeedback: (String) -> Unit = { onRevise() },
) {
    val scheme = MiuixTheme.colorScheme
    var feedback by remember(plan) { mutableStateOf("") }

    OverlayDialog(
        show = plan != null,
        onDismissRequest = onRevise,
        largeScreen = true,
        maxWidth = 640.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (plan == null) return@OverlayDialog
        DialogShell(
            title = "${plan.title} · revision ${plan.revision}",
            // 计划文件路径是固定说明，放在底板外面
            prompt = if (plan.path.isEmpty()) null else {
                {
                    Text(
                        text = plan.path,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            groupBody = true,
            // 反馈输入框不随计划正文滚动，固定在底板下方
            footer = {
                ZhiTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = "继续规划时填写反馈（可选）",
                    useLabelAsPlaceholder = true,
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (plan.permissionNote.isNotEmpty()) {
                    Text(
                        text = plan.permissionNote,
                        color = ZhiColors.amber(),
                        fontSize = ZhiTextScale.Footnote,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            },
            actions = {
                SecondaryButton(
                    text = "继续规划",
                    onClick = { onReviseWithFeedback(feedback) },
                )
                PrimaryButton(
                    text = "批准并开始",
                    onClick = onApprove,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ZhiMarkdown(source = plan.body)
            }
        }
    }
}

// -------------------------------------------------------------- 选择窗口

@Composable
fun ChoicePickerOverlay(
    picker: ChoicePickerState?,
    onSubmit: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    onSubmitFreeForm: (String) -> Unit = { onDismiss() },
) {
    val scheme = MiuixTheme.colorScheme
    var freeForm by remember(picker) { mutableStateOf("") }
    // 选项点击只改这个**本地**选中态，不回调 ViewModel。
    // 之前点击直接调 onSelect()，ViewModel 会立刻提交并把 choicePicker 置空，
    // 于是窗口一点就关、根本没机会按「提交」，也没法改主意。
    var localSelected by remember(picker) { mutableStateOf(-1) }
    // 多选（引擎提问可能带 multiSelect）走这一份本地集合。
    var localMulti by remember(picker) { mutableStateOf(emptySet<Int>()) }
    val multi = picker?.multiSelect == true
    val selectedIndices: Set<Int> = if (multi) {
        if (localMulti.isNotEmpty()) localMulti
        else picker?.options?.indices?.filter { picker.options[it].checked }?.toSet() ?: emptySet()
    } else {
        val single = if (localSelected >= 0) localSelected else picker?.options?.indexOfFirst { it.checked } ?: -1
        if (single >= 0) setOf(single) else emptySet()
    }
    val usingFreeForm = freeForm.isNotBlank()

    OverlayDialog(
        show = picker != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 640.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (picker == null) return@OverlayDialog
        DialogShell(
            title = picker.title,
            prompt = if (picker.prompt.isEmpty()) null else {
                {
                    Text(
                        text = picker.prompt,
                        color = scheme.onBackground,
                        fontSize = ZhiTextScale.Subheading,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            groupBody = true,
            // 自由文本输入框不随选项列表滚动，固定在底板下方
            footer = if (!picker.allowFreeForm) null else {
                {
                    ZhiTextField(
                        value = freeForm,
                        onValueChange = { freeForm = it },
                        label = picker.freeFormHint,
                        useLabelAsPlaceholder = true,
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            actions = {
                SecondaryButton(text = picker.cancelLabel, onClick = onDismiss)
                // 提交按钮是**唯一**的提交入口；没选任何项也没填自由文本时置灰，
                // 让"还不能提交"这件事可见，而不是点了没反应。
                // 文案来自 [ChoicePickerState.submitLabel]：多问题的提问流程中途是「下一步」。
                PrimaryButton(
                    text = picker.submitLabel,
                    enabled = usingFreeForm || selectedIndices.isNotEmpty(),
                    onClick = {
                        if (usingFreeForm) onSubmitFreeForm(freeForm)
                        else onSubmit(selectedIndices.sorted())
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            picker.options.forEachIndexed { index, option ->
                val checked = index in selectedIndices && !usingFreeForm
                // 每项 = Card 包一个 Miuix BasicComponent：
                // 标题/说明/选中控件的排版完全交给 Miuix
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    cornerRadius = ZhiRadius.card,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (checked) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                        contentColor = scheme.onBackground,
                    ),
                    pressFeedbackType = PressFeedbackType.None,
                ) {
                    val toggle = {
                        freeForm = ""
                        if (multi) {
                            localMulti = if (index in localMulti) localMulti - index else localMulti + index
                        } else {
                            localSelected = index
                        }
                    }
                    BasicComponent(
                        title = option.label,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = if (checked) scheme.primary else scheme.onBackground,
                        ),
                        summary = option.detail.ifEmpty { null },
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = scheme.onSurfaceVariantSummary,
                        ),
                        // 左侧选择控件。Miuix `RadioButton` 未选中时不画任何东西，
                        // 选择标记用 Miuix 自己的 Check 图标，和 Miuix 的做法对齐 ——
                        // 它的下拉列表（`DropdownImpl`）用的就是 `MiuixIcons.Basic.Check`
                        // 配 `DropdownDefaults.CheckIconSize`，不是 Checkbox。
                        //
                        // 为什么不用 Checkbox：stable 0.9.4 里它**固定 26dp 而且是圆的**
                        // （源码 `requiredSize(26.dp)` + `clip(CircleShape)`；`requiredSize`
                        // 在调用方 modifier 之后，所以外面也压不动）。26dp 配 11~13sp
                        // 的行文字会明显偏大。
                        //
                        // 整行的点击由 BasicComponent 的 onClick 负责，这个图标只是状态
                        // 指示、不承载交互语义；未选中时用透明 tint 占住同一位置，
                        // 免得行高随选中状态跳动。
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                tint = if (checked) scheme.primary else Color.Transparent,
                                modifier = Modifier.size(DropdownDefaults.CheckIconSize),
                            )
                        },
                        onClick = toggle,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 任务清单

/**
 * 任务详情窗口：**全部** Agent 任务，点悬浮任务卡打开。
 *
 * 与悬浮卡的分工：悬浮卡只在输入器上方露出「当前窗口」两条，避免遮挡对话；
 * 窗口里给出完整清单，每条任务的 [AgentTask.detail] 是 **Markdown**，
 * 交给 `ZhiMarkdown` 渲染（标题 / 粗体 / 行内代码 / 列表 / 代码块）。
 *
 * 文本可自由选择：整块内容包在 [SelectionContainer] 里，长按即可选中复制。
 * 容器用 [OverlayDialog]：它画在主窗口里，能参与 `ZhiCodeScreen` 的模态背景模糊。
 */
@Composable
fun TaskListOverlay(
    tasks: List<AgentTask>,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    OverlayDialog(
        show = open,


        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 620.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (!open) return@OverlayDialog
        DialogShell(
            title = "任务清单",
            // 任务清单内容多，让中间区撑满剩余高度（DialogShell 内部已自带滚动）
            fillBody = true,
            titleAction = {
                IconButton(
                    onClick = onDismiss,
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                    cornerRadius = ZhiRadius.floating,
                ) {
                    Icon(
                        imageVector = ZhiIcons.close,
                        contentDescription = "关闭任务清单",
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            actions = {
                PrimaryButton(text = "完成", onClick = onDismiss)
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val done = tasks.count { it.state == TaskState.DONE }
                Text(
                    text = "$done / ${tasks.size} 已完成",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                // 整块内容可选中：长按选中、拖动扩展选区
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        tasks.forEachIndexed { index, task ->
                            if (index > 0) {
                                ZhiHorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TaskStatusIndicator(state = task.state, size = 14.dp)
                                Text(
                                    text = task.title,
                                    color = if (task.state == TaskState.PENDING) {
                                        scheme.onSurfaceVariantSummary
                                    } else {
                                        scheme.onSurface
                                    },
                                    fontSize = ZhiTextScale.Subheading,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (task.detail.isNotBlank()) {
                                ZhiMarkdown(
                                    source = task.detail,
                                    modifier = Modifier.padding(start = 21.dp, top = 6.dp),
                                    bodyFontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
