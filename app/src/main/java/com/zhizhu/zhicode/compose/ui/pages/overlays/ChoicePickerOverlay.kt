package com.zhizhu.zhicode.compose.ui.pages.overlays

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
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
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
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 模态窗口默认走 Miuix `overlay` 包的 [OverlayDialog]。
 *
 * ## 本文件里那一处例外：会话重命名 / 删除确认用 `WindowDialog`
 *
 * 历史结论「不要用 `window` 包的 `WindowDialog`」有它成立的前提：
 * `WindowDialog` 创建**独立 Android Window**，既不继承 `Scaffold` 的 `popupHost`
 * （内部再用 Miuix 弹层组件会失效），也不参与主窗口的 `LayerBackdrop` 背景采样。
 *
 * 但**侧栏抽屉**这个场景把前提反了过来：抽屉必须画在主 Scaffold 之后才盖得住
 * 顶栏/底栏（那两者是 Scaffold 的独立槽位，在 body 之后 place），
 * 于是任何走 `popupHost` 的对话框都会被抽屉压住。此时代价换成收益：
 *  · 这个对话框内部**不需要**再嵌弹层组件；
 *  · 背景模糊早已按用户要求移除（见 `ZhiOverlayHost` 里 `modalOpen` 的说明），
 *    没有 backdrop 采样需求。
 * 所以「窗口级」正是这里要的特性 —— 详见下面 `WindowDialog` 处的注释。
 *
 * ## 位置与动画都交给 Miuix
 *
 * ⚠️ `largeScreen` 只在确实需要官方中央形态的调用点显式传入；它会同时决定
 * DialogContentLayout 的定位与动画，调用点不再额外叠加 AnimatedVisibility。
 *
 * ```
 * if (isLargeScreen) {                // true：大屏规格
 *     scale = 0.8f + 0.2f * progress   //   0.8→1 缩放
 *     alpha = progress
 *     // 进场 folmeSpring(damping = 0.9, response = 0.3)
 * } else {                            // false：手机规格
 *     translationY = (1 - progress) * 屏高   // 从下往上滑入
 *     // 进场 spring(dampingRatio = 0.88, stiffness = 450)
 * }
 * ```
 *
 * 文件面板需要中央形态时，直接按官方 `CenteredOverlayDialogDemo` 传
 * `largeScreen = true`；其它长内容/列表型弹窗不强制中央，继续使用 Miuix 的自动规格。
 *
 * ## 手写降到最低
 *
 * - 列表行 → Miuix [CheckboxPreference]（`checkboxLocation = Start`）。
 *   选中指示与整行的点击语义都由它自己处理，本文件不再手搓控件。
 *   单选与多选**共用同一个控件**，差别只在行为（点另一行是否清掉原来那行）。
 *   不用 Miuix `RadioButton`：它在未选中时不画任何东西（只画那个勾），
 *   单选行会连"这里能点"都看不出来。
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

    /*
     * ⚠️ 关键：`renderInRootScaffold = false`。
     *
     * 这条与本文件顶部那段历史结论相反，是因为**结构变了**：侧栏抽屉必须画在
     * 主 Scaffold 之后才盖得住顶栏/底栏，而抽屉内部自带一个 `Scaffold`
     * （为了渲染长按菜单，见 `ZhiSideDrawer`）。
     *
     * `renderInRootScaffold = true` 时对话框注册进**最外层** Scaffold 的 root 宿主，
     * 那个宿主在抽屉**下面** → 对话框被抽屉整块遮住，表现就是「点了没反应」。
     *
     * 传 `false` 后走 `LocalPopupStates`（离调用点最近的那个 Scaffold），
     * 也就是抽屉内部那个 —— 于是对话框和长按菜单一样，画在抽屉**之上**。
     * 抽屉关闭时最近的宿主是主 Scaffold，同样成立。
     */
    OverlayDialog(
        show = picker != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Regular,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
        renderInRootScaffold = false,
    ) {
        val p = shownPicker ?: return@OverlayDialog
        DialogShell(
            title = p.title,
            prompt = if (p.prompt.isEmpty()) null else {
                {
                    Text(
                        text = p.prompt,
                        color = scheme.onBackground,
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
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
                    /*
                     * 行首选择控件用官方 [CheckboxPreference]（`checkboxLocation = Start`）。
                     *
                     * ## 为什么不按"单选用 RadioButton、多选用 Checkbox"来分
                     *
                     * Miuix 的 `RadioButton` 只画那个勾（字节码里唯一的绘制路径是
                     * `drawTrimmedCheck`，未选中时 alpha 动画到 0），也就是**未选中时
                     * 整行不画任何东西** —— 一列选项看起来就是普通文字，用户看不出
                     * "这里能点"。`Checkbox` 有 checked/unchecked 两套前景与底色，
                     * 未选中态画得出来，所以单选行也要它，否则同一个窗口里
                     * "能选 / 不能选"长得不一样。
                     *
                     * ## 为什么现在能直接用官方组件了
                     *
                     * 早先这里是手搓 `BasicComponent` + `startAction` 里塞一个
                     * **缩到 0.78** 的 `Checkbox`（用 `graphicsLayer` 缩放，因为
                     * `Checkbox` 内部是 `requiredSize(26.dp)` 且排在调用方 modifier
                     * 之后，`Modifier.size()` 压不动它）。
                     *
                     * 那条限制只在"要把它缩小"时才成立。`CheckboxPreference` 自己
                     * 管排版、勾选圈用库的原始尺寸，于是**不需要缩放**，
                     * 也就不需要绕过任何东西 —— 直接用官方组件即可。
                     *
                     * ## 单选与多选的行为差异仍然保留
                     *
                     * 差别在**行为**（点另一行是否清掉原来那行），不在控件长相：
                     * 见下面 `toggle` 里 `multi` 的那个分支。控件只是"看得见的入口"。
                     */
                    CheckboxPreference(
                        title = option.label,
                        titleColor = BasicComponentDefaults.titleColor(color = titleColor),
                        summary = option.detail.ifEmpty { null },
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = scheme.onSurfaceVariantSummary,
                        ),
                        checked = checked,
                        // 点整行与点勾选圈是同一个动作：`CheckboxPreference` 内部
                        // 已经把手势统一到这一处，不会出现"点框没反应"或触发两遍。
                        onCheckedChange = { toggle() },
                        checkboxLocation = CheckboxLocation.Start,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
