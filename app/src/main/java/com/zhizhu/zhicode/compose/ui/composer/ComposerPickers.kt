package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.model.ModelThinkingPickerState
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiTextDropdownChip
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.pages.m3e.Material3ExpressivePageSet
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.clampThinkingLevel
import com.zhizhu.zhicode.pi.resolveMenuThinkingLevels
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PickerMaxHeight = 360.dp
private val PickerGap = 4.dp

@Composable
internal fun PermissionModePicker(mode: PermissionMode, onSelect: (PermissionMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = remember(mode, onSelect) {
        PermissionMode.entries.map { option ->
            ZhiMenuItem(
                text = option.label,
                summary = option.detail,
                selected = mode == option,
                onClick = {
                    expanded = false
                    onSelect(option)
                },
            )
        }
    }
    if (LocalZhiPageSet.current !== Material3ExpressivePageSet) {
        ZhiTextDropdownChip(label = mode.label, items = items)
        return
    }

    val keyboard = LocalSoftwareKeyboardController.current
    ComposerPickerAnchor { maxMenuHeight ->
        Surface(
            onClick = {
                keyboard?.hide()
                expanded = true
            },
            modifier = Modifier.height(24.dp),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painter = ZhiIcons.collapse,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp).size(11.dp),
                )
            }
        }
        ExpressiveComposerPicker(
            groups = listOf(items),
            show = expanded,
            maxMenuHeight = maxMenuHeight,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
internal fun ComposerPickerAnchor(modifier: Modifier = Modifier, content: @Composable (Dp) -> Unit) {
    if (LocalZhiPageSet.current !== Material3ExpressivePageSet) {
        Box(modifier) { content(PickerMaxHeight) }
        return
    }

    var anchorTop by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density)
    val height = with(density) { (anchorTop - safeTop).toDp() } - PickerGap * 2
    Box(modifier.onGloballyPositioned { anchorTop = it.positionInWindow().y.roundToInt() }) {
        content(if (anchorTop > 0) height.coerceIn(64.dp, PickerMaxHeight) else 0.dp)
    }
}

@Composable
internal fun ModelThinkingPopupMenu(
    picker: ModelThinkingPickerState?,
    maxMenuHeight: Dp,
    onDismiss: () -> Unit,
    onEnterSubmenu: (PiModelConfig) -> Unit,
    onBackToModels: () -> Unit,
    onSelectModelAndEffort: (String, EffortLevel) -> Unit,
    onConfigureModels: () -> Unit,
) {
    val shown = rememberLastNonNull(picker) ?: return
    val subModel = shown.expandedModel
    val groups = when {
        subModel != null -> {
            val levels = resolveMenuThinkingLevels(subModel)
            val active = if (subModel.id == shown.currentModelId) {
                clampThinkingLevel(levels, shown.currentEffort.toPiLevel())
            } else null
            listOf(
                listOf(
                    ZhiMenuItem(
                        text = "← 返回模型列表",
                        summary = subModel.name.ifBlank { subModel.id },
                        onClick = onBackToModels,
                    ),
                ),
                levels.map { level ->
                    val effort = EffortLevel.entries.firstOrNull { it.wire == level.id } ?: EffortLevel.HIGH
                    ZhiMenuItem(
                        text = effort.label,
                        summary = subModel.thinkingLevelMap[level]?.let { "映射：$it" },
                        selected = level == active,
                        onClick = { onSelectModelAndEffort(subModel.id, effort) },
                    )
                },
            )
        }
        shown.models.isEmpty() -> listOf(
            listOf(
                ZhiMenuItem(
                    text = "当前供应商暂无本地模型",
                    summary = "点击前往供应商设置添加模型",
                    onClick = onConfigureModels,
                ),
            ),
        )
        else -> {
            val expandIcon = ZhiIcons.expand
            val models = shown.models.map { model ->
                val levels = resolveMenuThinkingLevels(model)
                val multiTier = levels.size > 1
                val current = model.id == shown.currentModelId
                val summary = buildString {
                    append("${model.contextWindow / 1000}K")
                    if (model.supportsImage) append(" · 视觉")
                    if (multiTier) {
                        val active = if (current) {
                            clampThinkingLevel(levels, shown.currentEffort.toPiLevel()).id
                        } else {
                            levels.joinToString("/") { it.id }
                        }
                        append(" · ").append(active)
                    } else {
                        append(" · 非推理")
                    }
                }
                ZhiMenuItem(
                    text = model.name.ifBlank { model.id },
                    summary = summary,
                    selected = current,
                    icon = if (multiTier) expandIcon else null,
                    onClick = {
                        if (multiTier) {
                            onEnterSubmenu(model)
                        } else {
                            val effort = EffortLevel.entries.firstOrNull { it.wire == levels.firstOrNull()?.id }
                            onSelectModelAndEffort(model.id, effort ?: EffortLevel.OFF)
                        }
                    },
                )
            }
            listOf(models, listOf(ZhiMenuItem("配置此供应商模型", icon = ZhiIcons.edit, onClick = onConfigureModels)))
        }
    }
    ComposerPickerMenu(groups, picker != null, maxMenuHeight, onDismiss, collapseOnSelection = false, submenuId = subModel?.id)
}

@Composable
internal fun ProviderPopupMenu(
    picker: ModelPickerState?,
    maxMenuHeight: Dp,
    configurationOpen: Boolean,
    onDismiss: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProvider: () -> Unit,
    onManageProviders: () -> Unit,
) {
    val shown = rememberLastNonNull(picker) ?: return
    val providers = shown.profiles.map { profile ->
        val summary = buildList {
            if (profile.protocolLabel.isNotBlank()) add(profile.protocolLabel)
            if (profile.models.isNotEmpty()) add("${profile.models.size} 个模型")
        }.joinToString(" · ")
        ZhiMenuItem(
            text = profile.name,
            summary = summary.ifBlank { null },
            selected = profile.id == shown.activeProfileId,
            onClick = { onSelectProfile(profile.id) },
        )
    }
    val actions = listOf(
        ZhiMenuItem("新增供应商", icon = ZhiIcons.add, onClick = onAddProvider),
        ZhiMenuItem("供应商管理", icon = ZhiIcons.settings, onClick = onManageProviders),
    )
    val groups = if (providers.isEmpty()) listOf(actions) else listOf(providers, actions)
    ComposerPickerMenu(groups, picker != null && !configurationOpen, maxMenuHeight, onDismiss, collapseOnSelection = true)
}

@Composable
private fun ComposerPickerMenu(
    groups: List<List<ZhiMenuItem>>,
    show: Boolean,
    maxMenuHeight: Dp,
    onDismiss: () -> Unit,
    collapseOnSelection: Boolean,
    submenuId: String? = null,
) {
    if (LocalZhiPageSet.current === Material3ExpressivePageSet) {
        ExpressiveComposerPicker(groups, show, maxMenuHeight, onDismiss, submenuId)
        return
    }
    val scheme = MiuixTheme.colorScheme
    val expandIcon = ZhiIcons.expand
    val entries = groups.map { group ->
        DropdownEntry(
            items = group.map { item ->
                DropdownItem(
                    text = item.text,
                    summary = item.summary,
                    selected = item.selected,
                    icon = item.icon?.let { painter ->
                        { modifier ->
                            MiuixIcon(
                                painter = painter,
                                contentDescription = null,
                                tint = if (painter == expandIcon) scheme.onSurfaceVariantSummary else scheme.primary,
                                modifier = modifier,
                            )
                        }
                    },
                    onClick = item.onClick,
                )
            },
        )
    }
    OverlayDropdownPopup(
        entries = entries,
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = {},
        maxHeight = maxMenuHeight,
        dropdownColors = DropdownDefaults.dropdownColors(),
        renderInRootScaffold = true,
        collapseOnSelection = collapseOnSelection,
    )
}

@Composable
private fun ExpressiveComposerPicker(
    groups: List<List<ZhiMenuItem>>,
    show: Boolean,
    maxMenuHeight: Dp,
    onDismiss: () -> Unit,
    submenuId: String? = null,
) {
    if (maxMenuHeight == 0.dp) return
    val imeMotion = LocalImeMotion.current
    val interactions = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val gap = with(density) { PickerGap.roundToPx() }
    val safeTop = WindowInsets.safeDrawing.getTop(density) + gap
    val positionProvider = remember(gap, safeTop) { ComposerPickerPosition(gap, safeTop) }
    DropdownMenuPopup(
        expanded = show && !imeMotion.imeVisible,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 200.dp, max = 320.dp).heightIn(max = maxMenuHeight),
        popupPositionProvider = positionProvider,
    ) {
        val listScroll = rememberScrollState()
        val scroll = if (submenuId == null) listScroll else key(submenuId) { rememberScrollState() }
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(groupIndex, groups.size),
                    interactionSource = interactions,
                ) {
                    group.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = { Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingText = item.summary?.let { summary ->
                                { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            },
                            onClick = item.onClick,
                            modifier = Modifier.semantics { selected = item.selected },
                            shape = MenuDefaults.itemShape(index, group.size).shape,
                            leadingIcon = item.icon?.let { painter ->
                                { Icon(painter, contentDescription = null, Modifier.size(MenuDefaults.LeadingIconSize)) }
                            },
                            trailingContent = if (item.selected) ({
                                Icon(ZhiIcons.check, contentDescription = null, Modifier.size(MenuDefaults.TrailingIconSize))
                            }) else null,
                        )
                    }
                }
            }
        }
    }
}

private class ComposerPickerPosition(
    private val gap: Int,
    private val safeTop: Int,
) : DropdownMenuPopupPositionProvider {
    override var transformOrigin by mutableStateOf(TransformOrigin(0.5f, 1f))
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val maxX = (windowSize.width - width - gap).coerceAtLeast(0)
        val minX = minOf(gap, maxX)
        val leftAligned = anchorBounds.left
        val rightAligned = anchorBounds.right - width
        val (start, end) = if (layoutDirection == LayoutDirection.Ltr) {
            leftAligned to rightAligned
        } else {
            rightAligned to leftAligned
        }
        val x = if (start in minX..maxX) start else end.coerceIn(minX, maxX)
        val maxY = (windowSize.height - height - gap).coerceAtLeast(0)
        val y = (anchorBounds.top - height - gap).coerceIn(minOf(safeTop, maxY), maxY)
        val pivotX = (anchorBounds.center.x - x).toFloat() / width.coerceAtLeast(1)
        transformOrigin = TransformOrigin(pivotX.coerceIn(0f, 1f), 1f)
        return IntOffset(x, y)
    }
}

