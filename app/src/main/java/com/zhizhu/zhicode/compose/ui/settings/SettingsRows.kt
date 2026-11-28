package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.effects.pressSink
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog


/** 分组 = [SettingsGroupHeader] 标题 + 圆角 [Card]，间距照官方 `SettingsPage` */
@Composable
internal fun SettingsGroup(
    /** 分组标题。 */
    title: String? = null,
    horizontalPadding: Dp = 12.dp,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    if (title != null) {
        SettingsGroupHeader(title, modifier = Modifier.padding(horizontal = horizontalPadding))
    }
    Card(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(0.dp),
        colors = if (containerColor != null) {
            CardDefaults.defaultColors(
                color = containerColor,
                contentColor = MiuixTheme.colorScheme.onSurface,
            )
        } else {
            CardDefaults.defaultColors()
        },
    ) {
        Column { content() }
    }
}

/** 分组标题。转发到 Miuix [SmallTitle]，强调色文字。 */
@Composable
internal fun SettingsGroupHeader(text: String, modifier: Modifier = Modifier) {
    // 官方 SettingsPage 的组标题用 SmallTitle 默认色（onBackgroundVariant），
    // 强调色整页刷满反而让"危险项"的 warn 色失去辨识度。
    SmallTitle(text = text, modifier = modifier)
}

/** 枚举 / 选项型设置项。 */
@Composable
internal fun SettingsChoice(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    summary: String? = null,
    warn: Boolean = false,
    icon: Painter? = null,
    plate: Color? = null,
) {
    OverlayDropdownPreference(
        items = options,
        selectedIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
        startAction = plateStartAction(icon, plate),
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let { onSelect(index) } },
    )
}

/** 数字型设置项，走 Miuix 滚轮选择器（[OverlaySpinnerPreference]）。 */
@Composable
internal fun SettingsNumber(
    title: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    plate: Color? = null,
) {
    val enabled = options.isNotEmpty()
    OverlaySpinnerPreference(
        items = options.map { index ->
            DropdownItem(
                text = index.toString(),
                selected = index == value,
                onClick = { onValueChange(index) },
            )
        },
        selectedIndex = options.indexOf(value).coerceAtLeast(0),
        title = title,
        summary = summary,
        summaryColor = hintColors(warn = false),
        startAction = plateStartAction(icon, plate),
        maxHeight = 260.dp,
        enabled = enabled,
    )
}

@Composable
internal fun SettingsIntField(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    parse: (String) -> Int?,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    /** 显示用的写法。上下文窗口要显示成 `200k` 而不是 `200000`。 */
    format: (Int) -> String = { it.toString() },
    icon: Painter? = null,
    plate: Color? = null,
) {
    // 文本框自己持有一份文本：真实值是被 clamp 过的，而用户打进来的字可能还没成型。
    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }

    // 外部重置或程序改写 value 时，若当前未在编辑，同步刷新文本框显示。
    LaunchedEffect(value) {
        if (!focused) text = format(value)
    }

    SettingsTextField(
        title = title,
        value = text,
        // 失焦时收敛。`value` 在每次重组时重新捕获，所以这里拿到的是**最新**的真实值。
        modifier = Modifier.onFocusChanged { focus ->
            focused = focus.isFocused
            if (!focus.isFocused) text = format(value)
        },
        onValueChange = { raw ->
            // 一律先如实回显用户打的字；输入过程中**绝不**改写文本框（见上面的说明）。
            text = raw
            // 解析不出来（空串、`1.`、超 Int 范围）就是不提交：等用户打完，或失焦时收敛。
            parse(raw)?.let { onValueChange(it.coerceIn(min, max)) }
        },
        summary = summary,
        icon = icon,
        plate = plate,
    )
}

@Composable
internal fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    warn: Boolean = false,
    /** 是否可用。 */
    enabled: Boolean = true,
    icon: Painter? = null,
    plate: Color? = null,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
        startAction = plateStartAction(icon, plate),
        enabled = enabled,
    )
}

@Composable
internal fun SettingsValuePill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            interactionSource = source,
                            indication = null,
                            onClick = onClick,
                        )
                        .pressSink(source)
                }
            ),
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            // `tnum` 在 Miuix 的 Text 上通过 TextStyle 的 fontFeatureSettings 传下去。
            style = androidx.compose.ui.text.TextStyle(
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    summary: String? = null,
    steps: Int = 0,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    magnetThreshold: Float = 0.02f,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    icon: Painter? = null,
    plate: Color? = null,
    commitValue: ((Float) -> Unit)? = null,
    /** 值胶囊是否可点（打开精确输入）。为 null 时胶囊不可点、也不会有箭头。 */
    onValueClick: (() -> Unit)? = null,
    /** 把浮点值格式化成胶囊文字的**唯一**实现。 */
    valueTextFor: ((Float) -> String)? = null,
) {
    val scheme = MiuixTheme.colorScheme

    // 拖动中的临时值。null = 没有正在进行的拖动，此时显示外部真值。
    var draft by remember { mutableStateOf<Float?>(null) }
    // 外部值变了（比如弹窗精确输入）就丢掉草稿，否则草稿会一直压着真值。
    LaunchedEffect(value) { draft = null }

    val shown = draft ?: value
    val endActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        SettingsValuePill(
            text = valueTextFor?.invoke(shown) ?: valueText,
            onClick = onValueClick,
        )
    }
    val bottom: @Composable () -> Unit = {
        Slider(
            value = shown,
            onValueChange = { next ->
                if (commitValue != null) {
                    draft = next
                    onValueChange(next)
                } else {
                    onValueChange(next)
                }
            },
            onValueChangeFinished = {
                val pending = draft
                if (commitValue != null && pending != null) commitValue(pending)
                draft = null
            },
            valueRange = valueRange,
            steps = steps,
            showKeyPoints = showKeyPoints,
            keyPoints = keyPoints,
            magnetThreshold = magnetThreshold,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (onClick != null) {
        ArrowPreference(
            title = title,
            summary = summary,
            summaryColor = hintColors(warn = false),
            startAction = plateStartAction(icon, plate),
            endActions = endActions,
            bottomAction = bottom,
            onClick = onClick,
            holdDownState = holdDownState,
        )
    } else {
        BasicComponent(
            title = title,
            titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
            summary = summary,
            summaryColor = hintColors(warn = false),
            startAction = plateStartAction(icon, plate),
            endActions = endActions,
            bottomAction = bottom,
        )
    }
}

/** 入口型设置项（点后离开设置页去做别的事）。 */
@Composable
internal fun SettingsEntry(
    title: String,
    valueText: String,
    onClick: () -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    plate: Color? = null,
) {
    ArrowPreference(
        title = title,
        summary = summary ?: valueText,
        startAction = plateStartAction(icon, plate),
        onClick = onClick,
    )
}

/** 只读展示行（外观分类里的色值）。 */
@Composable
internal fun SettingsReadOnly(
    title: String,
    valueText: String,
    swatch: Color? = null,
) {
    val scheme = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        summary = valueText,
        titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
        summaryColor = hintColors(warn = false),
        startAction = swatch?.let {
            {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = ZhiSquircle(ZhiRadius.inner),
                    color = it,
                    contentColor = scheme.onBackground,
                    content = {},
                )
            }
        },
    )
}

@Composable
internal fun SettingsNumberDialog(
    show: Boolean,
    title: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    suffix: String = "%",
    summary: String? = null,
) {
    // 打开时按当前值重置输入：关掉再打开不该留着上一轮的半截输入。
    var text by remember(show, initial) { mutableStateOf(initial.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in range
    val scheme = MiuixTheme.colorScheme

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = title,
            actions = {
                SecondaryButton(text = "取消", onClick = onDismiss)
                PrimaryButton(
                    text = "确定",
                    onClick = { parsed?.let { if (it in range) onConfirm(it) } },
                    enabled = valid,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            ZhiTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = suffix,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!valid) {
                // 只在非法时出现：合法时不重复一遍区间，省一行视觉噪音。
                Text(
                    text = "请输入 ${range.first}–${range.last} 之间的整数",
                    color = scheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null,
    modifier: Modifier = Modifier,
    /** 是否单行。 */
    singleLine: Boolean = true,
    minLines: Int = 1,
    icon: Painter? = null,
    plate: Color? = null,
) {
    // 行内草稿。null = 没有未提交的改动，此时显示外部真值。
    var local by remember { mutableStateOf<String?>(null) }
    val shown = local ?: value

    // 外部值变了（保存回滚、别处改了同一项）→ 丢掉草稿，否则草稿会一直压着真值。
    LaunchedEffect(value) { local = null }

    // 去抖提交：停手之后才写回一次。`local` 变 null（外部值到位）时就无事可做。
    LaunchedEffect(local) {
        val pending = local ?: return@LaunchedEffect
        kotlinx.coroutines.delay(CommitDebounceMillis)
        // 期间又改了（local 已被换掉）→ 这次不提交，让新一轮去抖负责。
        if (local == pending && pending != value) onValueChange(pending)
    }

    // 离开页面时若还处于 400ms 去抖窗口内，立即冲刷未提交的草稿，避免最后几个字丢失。
    val latestLocal by rememberUpdatedState(local)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    DisposableEffect(Unit) {
        onDispose {
            val pending = latestLocal
            if (pending != null && pending != latestValue) {
                latestOnValueChange(pending)
            }
        }
    }

    BasicComponent(
        title = title,
        summary = summary,
        summaryColor = hintColors(warn = false),
        startAction = plateStartAction(icon, plate),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        bottomAction = {
            ZhiTextField(
                value = shown,
                onValueChange = { local = it },
                // [modifier] 下发给 ZhiTextField 的输入框本体，
                // 调用点靠它挂 `onFocusChanged`（数值项失焦时要把文本收敛成真实值）。
                modifier = modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
            )
        },
    )
}

/** 文本输入行的去抖窗口（毫秒）。 */
private const val CommitDebounceMillis = 400L

/** 行首图标块的 `startAction` 槽位。 */
@Composable
private fun plateStartAction(icon: Painter?, plate: Color?): (@Composable () -> Unit)? {
    if (icon == null || plate == null) return null
    return { SettingsIconPlate(icon = icon, color = plate) }
}

/** 说明文字的颜色：[warn] 为真时用琥珀色，其余走 Miuix 的次级色。 */
@Composable
private fun hintColors(warn: Boolean) = BasicComponentDefaults.summaryColor(
    color = if (warn) ZhiColors.amber() else MiuixTheme.colorScheme.onSurfaceVariantSummary,
)

/** 供外观页脚注使用的纯说明文本（不属于任何设置行）。 */
@Composable
internal fun SettingsFootnote(text: String, modifier: Modifier = Modifier) {
    BasicComponent(
        modifier = modifier,
        title = "",
        summary = text,
        summaryColor = hintColors(warn = false),
        startAction = null,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    )
}
