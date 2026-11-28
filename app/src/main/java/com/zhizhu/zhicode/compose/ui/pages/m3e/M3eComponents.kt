package com.zhizhu.zhicode.compose.ui.pages.m3e
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.PageFeedbackSlot
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import kotlin.math.roundToInt

/** 为 M3E 页面内的 Miuix 浮层提供透明 popupHost，使两套 UI 共存于同一窗口。 */
@Composable
internal fun M3eMiuixPopupBridge(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = Color.Transparent,
        popupHost = {
            top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        content()
    }
}

/** 二级页脚手架：可折叠大标题 + Tonal 返回圆钮 + 底部反馈位。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun M3eSubPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, TopAppBarScrollBehavior) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.surface,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = subtitle?.let { sub ->
                    {
                        Text(
                            text = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            painter = ZhiIcons.back,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(innerPadding, scrollBehavior)
            PageFeedbackSlot(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            )
        }
    }
}

/** M3E 尺寸档位，数值取自 material3 tokens。 */
internal object M3eDimens {

    /** 单行列表项高度。 */
    val RowSingle = 56.dp

    /** 双行（标题 + 说明）列表项高度。 */
    val RowDouble = 72.dp

    /** 三行列表项高度。 */
    val RowTriple = 88.dp

    /** 控件默认高度（M3E Medium 档）。**不是 48dp** —— 那是旧的无障碍基准。 */
    val ControlHeight = 56.dp

    /** 行内水平内边距。 */
    val RowPaddingH = 16.dp

    /** 图标块边长（视觉尺寸）。 */
    val IconBadge = 40.dp

    /** 图标块里的字形。 */
    val IconGlyph = 20.dp

    /** 列表项上下留白。 */
    val RowSpaceV = 10.dp

    /** 图标与文字的间距。 */
    val LeadingSpace = 16.dp

    /** 内容与尾随元素的间距。 */
    val TrailingSpace = 16.dp

    /** 分段列表项之间的缝。 */
    val SegmentGap = 2.dp

    /** 分段列表外角。 */
    val SegmentOuter = 16.dp

    /** 分段列表内角。 */
    val SegmentInner = 4.dp

    /** 分段列表项按下时的圆角。 */
    val SegmentPressed = 16.dp
}

/** 分组小标题，与分段卡片的左边界对齐。 */
@Composable
internal fun M3eSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = M3eDimens.RowPaddingH,
            end = M3eDimens.RowPaddingH,
            top = 16.dp,
            bottom = 8.dp,
        ),
    )
}

/** 分段列表项形状：外角取 ContainerShape，内部角取 ItemContainerExpressiveShape。 */
internal fun m3eSegmentShape(index: Int, count: Int): Shape {
    val outer = M3eDimens.SegmentOuter
    val inner = M3eDimens.SegmentInner
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/** 与 [m3eSegmentShape] 对应的四角值，供按压形变插值。 */
private fun m3eSegmentCorners(index: Int, count: Int): List<Dp> {
    val outer = M3eDimens.SegmentOuter
    val inner = M3eDimens.SegmentInner
    return when {
        count <= 1 -> listOf(outer, outer, outer, outer)
        index == 0 -> listOf(outer, outer, inner, inner)
        index == count - 1 -> listOf(inner, inner, outer, outer)
        else -> listOf(inner, inner, inner, inner)
    }
}

/** 一组间距 `2.dp` 的 MD3 Expressive 分段卡片容器。 */
@Composable
internal fun M3eSegmentedColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
internal fun M3eSegmentedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val baseCorners = m3eSegmentCorners(index, count)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下时四角一起收到 CornerLarge，这是 M3E 列表项的按压形变。
    val cornerProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "segmentCorner",
    )
    val pressedCorner = M3eDimens.SegmentPressed
    val shape: Shape = if (cornerProgress == 0f) {
        m3eSegmentShape(index, count)
    } else {
        RoundedCornerShape(
            topStart = lerp(baseCorners[0], pressedCorner, cornerProgress),
            topEnd = lerp(baseCorners[1], pressedCorner, cornerProgress),
            bottomStart = lerp(baseCorners[2], pressedCorner, cornerProgress),
            bottomEnd = lerp(baseCorners[3], pressedCorner, cornerProgress),
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content()
    }
}

@Composable
internal fun M3eIconBadge(
    painter: Painter,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        // 视觉 40dp（ItemLeadingAvatarSize），字形 20dp（ItemLeadingIconSize）。
        modifier = Modifier.size(M3eDimens.IconBadge),
        shape = CircleShape,
        color = containerColor,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(M3eDimens.IconGlyph),
            )
        }
    }
}

@Composable
internal fun M3eNavigationRow(
    index: Int,
    count: Int,
    title: String,
    summary: String? = null,
    valueText: String? = null,
    icon: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val desc = valueText ?: summary
    M3eSegmentedItem(index = index, count = count, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = if (desc.isNullOrBlank()) M3eDimens.RowSingle else M3eDimens.RowDouble,
                )
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                M3eIconBadge(icon, tint = iconTint, containerColor = iconContainer)
                Spacer(Modifier.width(M3eDimens.LeadingSpace))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (valueText != null && !summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (showChevron) {
                Spacer(Modifier.width(M3eDimens.TrailingSpace))
                Icon(
                    painter = ZhiIcons.arrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(M3eDimens.IconGlyph),
                )
            }
        }
    }
}

@Composable
internal fun M3eSwitchRow(
    index: Int,
    count: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    warn: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    M3eSegmentedItem(
        index = index,
        count = count,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (summary.isNullOrBlank()) M3eDimens.RowSingle else M3eDimens.RowDouble)
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                M3eIconBadge(
                    painter = icon,
                    tint = if (warn) scheme.onErrorContainer else scheme.onPrimaryContainer,
                    containerColor = if (warn) scheme.errorContainer else scheme.primaryContainer,
                )
                Spacer(Modifier.width(M3eDimens.LeadingSpace))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (warn) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(M3eDimens.TrailingSpace))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eChoiceRow(
    index: Int,
    count: Int,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val selectedLabel = options.getOrNull(selectedIndex).orEmpty()
    M3eNavigationRow(
        index = index,
        count = count,
        title = title,
        valueText = selectedLabel,
        summary = summary,
        icon = icon,
        onClick = { sheetOpen = true },
    )
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                M3eSegmentedColumn(modifier = Modifier.padding(horizontal = 0.dp)) {
                    options.forEachIndexed { idx, label ->
                        val picked = idx == selectedIndex
                        M3eSegmentedItem(
                            index = idx,
                            count = options.size,
                            containerColor = if (picked) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            onClick = {
                                sheetOpen = false
                                onSelect(idx)
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = picked, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun M3eSliderRow(
    index: Int,
    count: Int,
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    summary: String? = null,
    icon: Painter? = null,
    onValueClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    M3eSegmentedItem(index = index, count = count) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    M3eIconBadge(icon)
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier,
                ) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun M3eTextFieldDialogRow(
    index: Int,
    count: Int,
    title: String,
    value: String,
    onCommit: (String) -> Unit,
    displayValue: String = value.ifBlank { "未设置" },
    summary: String? = null,
    icon: Painter? = null,
    singleLine: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    M3eNavigationRow(
        index = index,
        count = count,
        title = title,
        valueText = displayValue,
        summary = summary,
        icon = icon,
        onClick = { open = true },
    )
    if (open) {
        var draft by remember(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        open = false
                        onCommit(draft)
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
internal fun M3eIntDialogRow(
    index: Int,
    count: Int,
    title: String,
    value: Int,
    min: Int,
    max: Int,
    parse: (String) -> Int?,
    format: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
) {
    var open by remember { mutableStateOf(false) }
    M3eNavigationRow(
        index = index,
        count = count,
        title = title,
        valueText = format(value),
        summary = summary,
        icon = icon,
        onClick = { open = true },
    )
    if (open) {
        var draft by remember(value) { mutableStateOf(format(value)) }
        val parsed = parse(draft)
        val valid = parsed != null && parsed in min..max
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        isError = !valid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let {
                            open = false
                            onValueChange(it.coerceIn(min, max))
                        }
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("取消")
                }
            },
        )
    }
}
