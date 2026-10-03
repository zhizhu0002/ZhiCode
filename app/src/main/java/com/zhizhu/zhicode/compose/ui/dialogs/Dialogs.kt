package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.state.ToggleableState
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
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
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
 * - 选中指示 → Miuix [Checkbox]，单选与多选**共用**。不用 Miuix `RadioButton`：
 *   它在未选中时不画任何东西（只画那个勾），单选行会连"这里能点"都看不出来。
 *   单选/多选的差别在**行为**（点另一行是否清掉原来那行），不在控件长相。
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
    // 退场那 250~260ms 里 `request` 已经是 null，内容不能跟着空掉（否则看起来是
    // 弹窗先缩成一张空壳再淡出）—— 见 rememberLastNonNull。
    val shownRequest = rememberLastNonNull(request)
    // 也按 shownRequest 记：按 request 记的话，关闭那一刻「始终允许」会自己弹回去。
    var alwaysAllow by remember(shownRequest) { mutableStateOf(false) }

    OverlayDialog(
        show = request != null,
        onDismissRequest = onDeny,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val req = shownRequest ?: return@OverlayDialog
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
                title = req.tool,
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = req.subtitle,
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                startAction = {
                    Icon(
                        painter = ZhiIcons.tool(req.tool),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                endActions = {
                    if (req.riskLevel == RiskLevel.HIGH) {
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
                    text = req.detail,
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
    // 同 PermissionOverlay：退场时 `plan` 已经是 null，内容得靠「最后一次非空」兜住。
    val shownPlan = rememberLastNonNull(plan)
    var feedback by remember(shownPlan) { mutableStateOf("") }

    OverlayDialog(
        show = plan != null,
        onDismissRequest = onRevise,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Regular,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val p = shownPlan ?: return@OverlayDialog
        DialogShell(
            title = "${p.title} · revision ${p.revision}",
            // 计划文件路径是固定说明，放在底板外面
            prompt = if (p.path.isEmpty()) null else {
                {
                    Text(
                        text = p.path,
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
                if (p.permissionNote.isNotEmpty()) {
                    Text(
                        text = p.permissionNote,
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
                ZhiMarkdown(source = p.body)
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
    // 同 PermissionOverlay：退场时 `picker` 已经是 null。
    val shownPicker = rememberLastNonNull(picker)
    var freeForm by remember(shownPicker) { mutableStateOf("") }
    // 选项点击只改这个**本地**选中态，不回调 ViewModel。
    // 之前点击直接调 onSelect()，ViewModel 会立刻提交并把 choicePicker 置空，
    // 于是窗口一点就关、根本没机会按「提交」，也没法改主意。
    var localSelected by remember(shownPicker) { mutableStateOf(-1) }
    // 多选（引擎提问可能带 multiSelect）走这一份本地集合。
    var localMulti by remember(shownPicker) { mutableStateOf(emptySet<Int>()) }
    val multi = shownPicker?.multiSelect == true
    val selectedIndices: Set<Int> = if (multi) {
        if (localMulti.isNotEmpty()) localMulti
        else shownPicker?.options?.indices?.filter { shownPicker.options[it].checked }?.toSet() ?: emptySet()
    } else {
        val single = if (localSelected >= 0) localSelected else shownPicker?.options?.indexOfFirst { it.checked } ?: -1
        if (single >= 0) setOf(single) else emptySet()
    }
    val usingFreeForm = freeForm.isNotBlank()

    OverlayDialog(
        show = picker != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Regular,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val p = shownPicker ?: return@OverlayDialog
        DialogShell(
            title = p.title,
            prompt = if (p.prompt.isEmpty()) null else {
                {
                    Text(
                        text = p.prompt,
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
            footer = if (!p.allowFreeForm) null else {
                {
                    ZhiTextField(
                        value = freeForm,
                        onValueChange = { freeForm = it },
                        label = p.freeFormHint,
                        useLabelAsPlaceholder = true,
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            actions = {
                SecondaryButton(text = p.cancelLabel, onClick = onDismiss)
                // 提交按钮是**唯一**的提交入口；没选任何项也没填自由文本时置灰，
                // 让"还不能提交"这件事可见，而不是点了没反应。
                // 文案来自 [ChoicePickerState.submitLabel]：多问题的提问流程中途是「下一步」。
                PrimaryButton(
                    text = p.submitLabel,
                    enabled = usingFreeForm || selectedIndices.isNotEmpty(),
                    onClick = {
                        if (usingFreeForm) onSubmitFreeForm(freeForm)
                        else onSubmit(selectedIndices.sorted())
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            p.options.forEachIndexed { index, option ->
                val checked = index in selectedIndices && !usingFreeForm
                // 选中态是「整张卡底色 + 标题色」同时变，两处都走 150ms 淡变：
                // 只淡底或只淡字会出现「字已经变蓝、底还是灰的」中间态（同 ModelPicker 的推导）。
                val cardColor by animateColorAsState(
                    targetValue = if (checked) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "choiceRowCard",
                )
                val titleColor by animateColorAsState(
                    targetValue = if (checked) scheme.primary else scheme.onBackground,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "choiceRowTitle",
                )
                // 每项 = Card 包一个 Miuix BasicComponent：
                // 标题/说明/选中控件的排版完全交给 Miuix
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    cornerRadius = ZhiRadius.card,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = cardColor,
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
                        titleColor = BasicComponentDefaults.titleColor(color = titleColor),
                        summary = option.detail.ifEmpty { null },
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = scheme.onSurfaceVariantSummary,
                        ),
                        // 行首选择控件统一用 Miuix [Checkbox]，**单选与多选都一样**。
                        //
                        // 为什么不能按"单选用 RadioButton、多选用 Checkbox"来分：
                        // Miuix 0.9.4 的 `RadioButton` 只画那个勾（字节码里唯一的绘制路径
                        // 是 `drawTrimmedCheck`，未选中时 alpha 动画到 0），
                        // 也就是说**未选中时整行不画任何东西** —— 一列选项看起来就是普通文字，
                        // 用户看不出"这里能点"。而 `Checkbox` 有 checked/unchecked 两套
                        // 前景与底色（见 `CheckboxColors`），未选中态是画得出来的。
                        // 所以单选行也要它，否则同一个窗口里"能选/不能选"长得不一样。
                        //
                        // 单选与多选的**行为**差异仍然保留：单选点另一行会换掉原来那一行
                        // （见下面的 toggle），多选可以同时勾多个。控件只是"看得见的入口"。
                        //
                        // 关于尺寸：0.9.4 的 Checkbox 固定 26dp 且是圆的（字节码
                        // `requiredSize(26.dp)`，在调用方 modifier 之后，外面压不动），
                        // 行因此比自己画一个小方框要高一点 —— 这是跟库对齐的代价，认了。
                        //
                        // Checkbox 自己也接同一个 toggle：点框与点整行是同一个动作，
                        // 不会出现"点框没反应"（Compose 的点击会被消费，不会触发两遍）。
                        startAction = {
                            Checkbox(
                                state = if (checked) ToggleableState.On else ToggleableState.Off,
                                onClick = toggle,
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

/** 任务清单 sheet 的定高参数（含义与取值理由见 ModelPickerOverlay 的同名常量）。 */
private const val TaskSheetHeightFraction = 0.55f
private val TaskSheetHeightCap = 520.dp
private val TaskSheetHeightFloor = 300.dp

/**
 * 任务详情窗口：**全部** Agent 任务，点悬浮任务卡打开。
 *
 * 与悬浮卡的分工：悬浮卡只在输入器上方露出「当前窗口」两条，避免遮挡对话；
 * 窗口里给出完整清单，每条任务的 [AgentTask.detail] 是 **Markdown**，
 * 交给 `ZhiMarkdown` 渲染（标题 / 粗体 / 行内代码 / 列表 / 代码块）。
 *
 * 文本可自由选择：整块内容包在 [SelectionContainer] 里，长按即可选中复制。
 *
 * ## 为什么是底部 Sheet 而不是居中弹窗
 *
 * 原来是居中 [OverlayDialog]：内容是一份**可变长**的清单，弹窗只有 fade+spring
 * 进出，长清单在竖屏下又早早就触发滚动（可用高度比 sheet 小）。
 * 底部 sheet 自带上滑进场、下拉 / 点背板关闭，且与任务卡、模型、技能这些
 * "长列表面板"同一形态（完整理由见 `ModelPickerOverlay` 顶部注释）。
 *
 * 标题由 [OverlayBottomSheet] 自己渲染，**不再**套 `DialogShell`：那会出两行标题，
 * 且它的 `weight(1f)` 依赖有界高度，与 sheet"高度由内容决定"的语义对不上。
 */
@Composable
fun TaskListOverlay(
    tasks: List<AgentTask>,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    OverlayBottomSheet(
        show = open,
        onDismissRequest = onDismiss,
        title = "任务清单",
        // 其余参数一律用 Miuix 默认值（同 ModelPickerOverlay：backgroundColor 别动）。
    ) {
        // 「最后一位客人」常驻：`open` 翻成 false 时内容不能空掉，否则退场先缩成空壳。
        // `tasks` 是非空列表，关面板时 ViewModel 不清它，直接去掉原来那句早退即可。
        // 定高：任务从 1 条涨到 10 条时面板高度不跳；高度算的是**内容区**，
        // sheet 的标题行与内边距由组件自己叠加（ModelPickerBody 同款写法）。
        // ⚠️ 窗口高度取 LocalWindowInfo，不能取 BoxWithConstraints 的 maxHeight
        // （sheet 量内容时给的约束不是屏幕高度，见 ModelPickerBody 的实测注释）。
        val windowHeight = LocalWindowInfo.current.containerDpSize.height
        val sheetHeight = (windowHeight * TaskSheetHeightFraction)
            .coerceAtMost(TaskSheetHeightCap)
            .coerceAtLeast(TaskSheetHeightFloor)
        Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            val done = tasks.count { it.state == TaskState.DONE }
            Text(
                text = "$done / ${tasks.size} 已完成",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            // 清单区吃掉余量并内部滚动（`DialogShell` 中间区同款写法）：
            // 定高列里不 weight 的话，长清单会把「完成」按钮顶出屏幕。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
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

            PrimaryButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
