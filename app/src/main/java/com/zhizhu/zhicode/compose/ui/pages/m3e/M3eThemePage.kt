package com.zhizhu.zhicode.compose.ui.pages.m3e
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.theme.THEME_PRESETS
import com.zhizhu.zhicode.compose.theme.ThemePreset
import com.zhizhu.zhicode.compose.theme.UI_SCALE_MAX
import com.zhizhu.zhicode.compose.theme.UI_SCALE_MIN
import com.zhizhu.zhicode.compose.theme.formatUiScale
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.pages.ZhiPageSets
import com.zhizhu.zhicode.compose.ui.pages.miuix.ThemePreviewPhone
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import kotlin.math.roundToInt

/** M3E 主题设置页，共享 ThemeSettings，可在三种页面组间切换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3eThemePage(
    theme: ThemeSettings,
    onDismiss: () -> Unit,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var scaleDialogOpen by remember { mutableStateOf(false) }

    M3eSubPageScaffold(
        title = "主题设置",
        subtitle = ZhiPageSets.byId(theme.pageSetId).name,
        onBack = onDismiss,
    ) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "preview") {
                Spacer(Modifier.height(16.dp))
                ThemePreviewPhone(
                    theme = theme,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(20.dp))
            }

            item(key = "pageSet") {
                M3eSectionHeader("界面风格（UI 体系）")
                val sets = ZhiPageSets.all
                M3eSegmentedColumn {
                    sets.forEachIndexed { idx, pageSet ->
                        val selected = pageSet.id == theme.pageSetId
                        val desc = when (pageSet.id) {
                            "miuix" -> "HyperOS / 小米风格 Miuix 组件与模糊效果"
                            "m3_expressive" -> "Google Material 3 Expressive 弹性动效、胶囊浮栏与分段圆角卡片"
                            "mochi" -> "麻薯（Mochi）iOS 26 风格轻页面体验"
                            else -> ""
                        }
                        M3eSegmentedItem(
                            index = idx,
                            count = sets.size,
                            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
                            onClick = { onChange { it.copy(pageSetId = pageSet.id) } },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pageSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                                    )
                                    if (desc.isNotEmpty()) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "mode") {
                M3eSectionHeader("明暗外观")
                val modes = listOf("跟随系统" to ThemeMode.SYSTEM, "浅色" to ThemeMode.LIGHT, "深色" to ThemeMode.DARK)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    modes.forEachIndexed { index, (label, mode) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            selected = theme.mode == mode,
                            onClick = { onChange { it.copy(mode = mode) } },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            item(key = "color") {
                M3eSectionHeader("颜色")
                M3eSegmentedColumn {
                    val count = if (theme.dynamicColor) 4 else 2
                    M3eSwitchRow(
                        index = 0,
                        count = count,
                        title = "Monet 动态取色",
                        summary = "根据强调色生成整套动态色板",
                        checked = theme.dynamicColor,
                        onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                        icon = ZhiIcons.layers,
                    )
                    M3ePresetPaletteCard(
                        index = 1,
                        count = count,
                        selectedId = theme.presetId,
                        onSelect = { preset ->
                            onChange { it.copy(presetId = preset.id, customSeedArgb = null) }
                        },
                    )
                    if (theme.dynamicColor) {
                        val paletteStyles = ThemePaletteStyle.entries
                        M3eChoiceRow(
                            index = 2,
                            count = count,
                            title = "调色板风格",
                            options = paletteStyles.map { it.name },
                            selectedIndex = paletteStyles.indexOfFirst { it.name == theme.paletteStyle }.coerceAtLeast(0),
                            onSelect = { index ->
                                paletteStyles.getOrNull(index)?.let { style ->
                                    onChange { it.copy(paletteStyle = style.name) }
                                }
                            },
                            icon = ZhiIcons.tune,
                        )
                        val specs = ThemeColorSpec.entries
                        M3eChoiceRow(
                            index = 3,
                            count = count,
                            title = "颜色规范",
                            options = specs.map { it.name },
                            selectedIndex = specs.indexOfFirst { it.name == theme.colorSpec }.coerceAtLeast(0),
                            onSelect = { index ->
                                specs.getOrNull(index)?.let { spec ->
                                    onChange { it.copy(colorSpec = spec.name) }
                                }
                            },
                            icon = ZhiIcons.theme,
                        )
                    }
                }
            }

            item(key = "navigation") {
                M3eSectionHeader("导航栏")
                val bottomMode = theme.tabBarPositionValue == TabBarPosition.BOTTOM
                val navCount = when {
                    !bottomMode -> 1
                    theme.tabBarFloating -> 3
                    else -> 2
                }
                M3eSegmentedColumn {
                    M3eSegmentedItem(index = 0, count = navCount) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
                        ) {
                            Text(
                                text = "导航栏位置",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            val positions = TabBarPosition.entries
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                positions.forEachIndexed { idx, pos ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = positions.size),
                                        selected = theme.tabBarPositionValue == pos,
                                        onClick = { onChange { it.copy(tabBarPosition = pos.name) } },
                                        label = { Text(pos.label) },
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = bottomMode,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            M3eSwitchRow(
                                index = 1,
                                count = navCount,
                                title = "悬浮导航栏",
                                summary = "开：MD3 Expressive 悬浮工具胶囊；关：底部导航栏",
                                checked = theme.tabBarFloating,
                                onCheckedChange = { on -> onChange { it.copy(tabBarFloating = on) } },
                                icon = ZhiIcons.chat,
                            )
                            if (theme.tabBarFloating) {
                                M3eSwitchRow(
                                    index = 2,
                                    count = navCount,
                                    title = "悬浮栏背景模糊",
                                    summary = "关闭后保留胶囊形态，改用 tonal 纯色底",
                                    checked = theme.floatingNavBlur,
                                    onCheckedChange = { on -> onChange { it.copy(floatingNavBlur = on) } },
                                    enabled = isRuntimeShaderSupported(),
                                    icon = ZhiIcons.layers,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "effects") {
                M3eSectionHeader("效果与缩放")
                val shaderSupported = isRuntimeShaderSupported()
                val effectCount = if (theme.pageSetId == "mochi") 3 else 2
                M3eSegmentedColumn {
                    M3eSwitchRow(
                        index = 0,
                        count = effectCount,
                        title = "顶部与底部模糊",
                        summary = if (shaderSupported) "为导航栏与悬浮层启用背景模糊" else "需要 Android 13 RuntimeShader 支持",
                        checked = theme.blurEnabled,
                        onCheckedChange = { on -> onChange { it.copy(blurEnabled = on) } },
                        enabled = shaderSupported,
                        icon = ZhiIcons.layers,
                    )
                    if (theme.pageSetId == "mochi") {
                        M3eSwitchRow(
                            index = 1,
                            count = effectCount,
                            title = "Mochi 液态玻璃",
                            summary = if (shaderSupported) "麻薯页面专用的折射玻璃，更耗电" else "需要 Android 13 RuntimeShader 支持",
                            checked = theme.mochiGlass,
                            onCheckedChange = { on -> onChange { it.copy(mochiGlass = on) } },
                            enabled = shaderSupported,
                            icon = ZhiIcons.layers,
                        )
                    }
                    M3eSliderRow(
                        index = effectCount - 1,
                        count = effectCount,
                        title = "页面缩放",
                        summary = "调整全局界面密度，不影响系统字体设置",
                        value = theme.uiScale,
                        valueText = formatUiScale(theme.uiScale),
                        valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                        onValueChange = { scale -> onChange { it.copy(uiScale = scale) } },
                        onValueClick = { scaleDialogOpen = true },
                        icon = ZhiIcons.tune,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (scaleDialogOpen) {
        val minPct = (UI_SCALE_MIN * 100f).roundToInt()
        val maxPct = (UI_SCALE_MAX * 100f).roundToInt()
        var text by remember { mutableStateOf((theme.uiScale * 100f).roundToInt().toString()) }
        val num = text.trim().toIntOrNull()
        val valid = num != null && num in minPct..maxPct
        AlertDialog(
            onDismissRequest = { scaleDialogOpen = false },
            title = { Text("界面缩放（$minPct%–$maxPct%）") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = !valid,
                    label = { Text("整数百分比") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = num != null,
                    onClick = {
                        num?.let {
                            val clamped = it.coerceIn(minPct, maxPct)
                            scaleDialogOpen = false
                            onChange { s -> s.copy(uiScale = clamped / 100f) }
                        }
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { scaleDialogOpen = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun M3ePresetPaletteCard(
    index: Int,
    count: Int,
    selectedId: String,
    onSelect: (ThemePreset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    M3eSegmentedItem(index = index, count = count) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
        ) {
            Text(
                text = "预设强调色",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            THEME_PRESETS.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { preset ->
                        val picked = preset.id == selectedId
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val corner by animateDpAsState(
                            targetValue = if (pressed) 20.dp else 16.dp,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "presetCorner",
                        )
                        val ringAlpha by animateFloatAsState(
                            targetValue = if (picked) 1f else 0f,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "presetRing",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(corner))
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(),
                                    onClick = { onSelect(preset) },
                                )
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.seedArgb))
                                    .then(
                                        if (ringAlpha > 0f) {
                                            Modifier.border(
                                                2.5.dp,
                                                scheme.onSurface.copy(alpha = ringAlpha),
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (picked) {
                                    Icon(
                                        painter = ZhiIcons.check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(M3eDimens.IconSmall),
                                    )
                                }
                            }
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                color = if (picked) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
