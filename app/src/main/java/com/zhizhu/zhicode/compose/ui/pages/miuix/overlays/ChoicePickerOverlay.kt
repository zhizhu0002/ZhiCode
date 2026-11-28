package com.zhizhu.zhicode.compose.ui.pages.miuix.overlays

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
                // 提交按钮是**唯一**的提交入口；没选任何项也没填自由文本时置灰
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
