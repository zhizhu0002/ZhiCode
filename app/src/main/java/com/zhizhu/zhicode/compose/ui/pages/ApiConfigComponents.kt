package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiSectionLabel
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eIconBadge
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eSectionHeader
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eSliderRow
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eSubPageScaffold
import com.zhizhu.zhicode.compose.ui.pages.m3e.Material3ExpressivePageSet
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsIconPlate
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextDraft
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun useExpressiveComponents() = LocalZhiPageSet.current === Material3ExpressivePageSet

internal data class ProviderColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val secondaryContainer: Color,
    val surfaceContainer: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

@Composable
internal fun providerColors(): ProviderColors {
    if (useExpressiveComponents()) {
        val scheme = MaterialTheme.colorScheme
        return ProviderColors(
            scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.secondaryContainer,
            scheme.surfaceContainerLow, scheme.onSurface, scheme.onSurfaceVariant, scheme.outline,
        )
    }
    val scheme = MiuixTheme.colorScheme
    return ProviderColors(
        scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.secondaryContainer,
        scheme.surfaceContainer, scheme.onBackground, scheme.onSurfaceVariantSummary, scheme.outline,
    )
}

@Composable
internal fun providerFontSize(small: Boolean = false): TextUnit =
    if (useExpressiveComponents()) {
        if (small) MaterialTheme.typography.bodySmall.fontSize else MaterialTheme.typography.bodyMedium.fontSize
    } else {
        if (small) MiuixTheme.textStyles.footnote1.fontSize else MiuixTheme.textStyles.body2.fontSize
    }

@Composable
internal fun providerCheckIconSize(): Dp = if (useExpressiveComponents()) 16.dp else DropdownDefaults.CheckIconSize

@Composable
internal fun ProviderSubPage(
    title: String,
    onBack: () -> Unit,
    action: Pair<String, (() -> Unit)?>? = null,
    content: @Composable () -> Unit,
) {
    if (!useExpressiveComponents()) {
        SettingsSubPage(title = title, onBack = onBack, action = action, content = content)
        return
    }
    M3eSubPageScaffold(
        title = title,
        onBack = onBack,
        actions = {
            action?.let { (label, onClick) ->
                TextButton(onClick = { onClick?.invoke() }, enabled = onClick != null) { Text(label) }
            }
        },
    ) { padding, _ ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        ) { content() }
    }
}

@Composable
internal fun ProviderGroup(title: String, content: @Composable () -> Unit) {
    if (useExpressiveComponents()) {
        M3eSectionHeader(title)
        ProviderCard(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            content = content,
        )
    } else {
        SettingsGroup(title, content = content)
    }
}

@Composable
internal fun ProviderCard(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (useExpressiveComponents()) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = color)) {
            content()
        }
    } else {
        MiuixCard(modifier = modifier, colors = MiuixCardDefaults.defaultColors(color = color)) { content() }
    }
}

@Composable
internal fun ProviderText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    if (useExpressiveComponents()) {
        Text(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight)
    } else {
        MiuixText(text, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight)
    }
}

@Composable
internal fun ProviderIcon(painter: Painter, contentDescription: String?, tint: Color, modifier: Modifier = Modifier) {
    if (useExpressiveComponents()) {
        Icon(painter, contentDescription, modifier, tint)
    } else {
        MiuixIcon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}

@Composable
internal fun ProviderIconButton(icon: Painter, description: String, onClick: () -> Unit, iconSize: Dp) {
    if (useExpressiveComponents()) {
        IconButton(onClick = onClick) { Icon(icon, description, Modifier.size(iconSize)) }
    } else {
        ZhiIconButton(icon, description, onClick, iconSize = iconSize)
    }
}

@Composable
internal fun ProviderPlate(icon: Painter, color: Color) {
    if (useExpressiveComponents()) M3eIconBadge(icon) else SettingsIconPlate(icon, color)
}

@Composable
internal fun ProviderDivider(modifier: Modifier = Modifier) {
    if (useExpressiveComponents()) HorizontalDivider(modifier) else MiuixDivider(modifier)
}

@Composable
internal fun ProviderLabel(text: String, insideMargin: PaddingValues) {
    if (useExpressiveComponents()) {
        Text(text, Modifier.padding(insideMargin), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    } else {
        ZhiSectionLabel(text, insideMargin = insideMargin)
    }
}

@Composable
internal fun ProviderLoading() {
    if (useExpressiveComponents()) {
        Text("正在读取…", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
    } else {
        SettingsLoadingHint()
    }
}

@Composable
internal fun ProviderButton(text: String, onClick: () -> Unit) {
    if (useExpressiveComponents()) {
        FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(text, maxLines = 1)
        }
    } else {
        SecondaryButton(text, onClick)
    }
}

@Composable
internal fun ProviderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    useLabelAsPlaceholder: Boolean = true,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    if (useExpressiveComponents()) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier,
            label = if (label.isNotEmpty() && !useLabelAsPlaceholder) ({ Text(label) }) else null,
            placeholder = if (label.isNotEmpty() && useLabelAsPlaceholder) ({ Text(label) }) else null,
            singleLine = singleLine, minLines = minLines,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        )
    } else {
        ZhiTextField(value, onValueChange, modifier, label = label, singleLine = singleLine, minLines = minLines, useLabelAsPlaceholder = useLabelAsPlaceholder)
    }
}

@Composable
internal fun ProviderFieldError(message: String?, touched: Boolean) {
    if (useExpressiveComponents()) {
        if (touched && message != null) {
            Text(message, Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        ZhiFieldError(message, touched)
    }
}

@Composable
internal fun ProviderRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    titleColor: Color = Color.Unspecified,
    summaryColor: Color = Color.Unspecified,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!useExpressiveComponents()) {
        BasicComponent(
            title = title, modifier = modifier, summary = summary,
            titleColor = if (titleColor == Color.Unspecified) BasicComponentDefaults.titleColor() else BasicComponentDefaults.titleColor(color = titleColor),
            summaryColor = if (summaryColor == Color.Unspecified) BasicComponentDefaults.summaryColor() else BasicComponentDefaults.summaryColor(color = summaryColor),
            insideMargin = insideMargin, startAction = startAction,
            endActions = endActions,
            bottomAction = bottomAction,
            onClick = onClick,
        )
        return
    }
    Column(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(insideMargin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (startAction != null) {
                startAction()
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                summary?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        color = if (summaryColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else summaryColor,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            endActions?.invoke(this)
        }
        bottomAction?.invoke()
    }
}

@Composable
internal fun ProviderToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    plate: Color? = null,
) {
    if (!useExpressiveComponents()) {
        if (icon != null) {
            SettingsToggle(title, checked, onCheckedChange, summary = summary, icon = icon, plate = plate)
        } else {
            SwitchPreference(title = title, checked = checked, onCheckedChange = onCheckedChange, summary = summary)
        }
        return
    }
    ProviderRow(
        title = title,
        summary = summary,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        startAction = icon?.let { painter -> { M3eIconBadge(painter) } },
        endActions = { Switch(checked, onCheckedChange = null) },
    )
}

@Composable
internal fun ProviderSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    summary: String,
    startAction: @Composable () -> Unit,
    insideMargin: PaddingValues,
) {
    if (useExpressiveComponents()) {
        M3eSliderRow(0, 1, title, value, valueText, valueRange, onValueChange, steps, summary, icon = ZhiIcons.tune)
    } else {
        SliderPreference(
            title = title, value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            valueText = valueText, summary = summary, startAction = startAction, insideMargin = insideMargin,
        )
    }
}

@Composable
internal fun ProviderPromptField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String,
    singleLine: Boolean,
    minLines: Int,
    icon: Painter,
    plate: Color,
) {
    if (!useExpressiveComponents()) {
        SettingsTextField(
            title, value, onValueChange, summary, singleLine = singleLine, minLines = minLines, icon = icon, plate = plate,
        )
        return
    }
    SettingsTextDraft(value, onValueChange) { shown, onEdit ->
        ProviderRow(title, summary = summary, startAction = { M3eIconBadge(icon) }, bottomAction = {
            ProviderField(shown, onEdit, singleLine = singleLine, minLines = minLines, modifier = Modifier.fillMaxWidth())
        })
    }
}

@Composable
internal fun ProviderPromptLink(title: String, valueText: String, onClick: () -> Unit, icon: Painter, plate: Color) {
    if (useExpressiveComponents()) {
        ProviderRow(
            title = title,
            summary = valueText,
            startAction = { M3eIconBadge(icon) },
            endActions = { Icon(ZhiIcons.expand, contentDescription = null) },
            onClick = onClick,
        )
    } else {
        SettingsEntry(title, valueText, onClick, icon = icon, plate = plate)
    }
}

@Composable
internal fun ProviderProtocolChoice(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelectedIndexChange: (Int) -> Unit,
) {
    if (!useExpressiveComponents()) {
        OverlayDropdownPreference(
            items = items, selectedIndex = selectedIndex, title = title, summary = null,
            onSelectedIndexChange = onSelectedIndexChange,
        )
        return
    }
    val choices = items.mapIndexed { index, label ->
        ZhiMenuItem(text = label, selected = index == selectedIndex, onClick = { onSelectedIndexChange(index) })
    }
    ExpressiveProviderMenu(choices) { open ->
        ProviderRow(
            title = title,
            endActions = {
                Text(items.getOrElse(selectedIndex) { "" }, style = MaterialTheme.typography.bodyMedium)
                Icon(ZhiIcons.expand, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            onClick = open,
        )
    }
}

@Composable
internal fun ProviderDropdownMenu(
    items: List<ZhiMenuItem>,
    backgroundColor: Color,
    cornerRadius: Dp,
    minHeight: Dp,
    content: @Composable () -> Unit,
) {
    if (useExpressiveComponents()) {
        ExpressiveProviderMenu(items) { open -> FilledTonalButton(onClick = open) { content() } }
    } else {
        ZhiIconDropdownMenu(items, backgroundColor = backgroundColor, cornerRadius = cornerRadius, minHeight = minHeight, content = content)
    }
}

@Composable
private fun ExpressiveProviderMenu(items: List<ZhiMenuItem>, anchor: @Composable (() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Box {
        anchor {
            keyboard?.hide()
            expanded = true
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.text)
                            item.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    trailingIcon = if (item.selected) ({ Icon(ZhiIcons.check, contentDescription = null) }) else null,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}


