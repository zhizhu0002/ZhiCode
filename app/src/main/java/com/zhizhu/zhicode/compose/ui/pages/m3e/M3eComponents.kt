package com.zhizhu.zhicode.compose.ui.pages.m3e

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

/**
 * 为 Material 3 Expressive 页面内部挂载的共用 Miuix 浮层（`ZhiOverlayHost`、
 * `Composer` 锚定下拉、`ZhiAnchoredMenuHost`）提供透明的 Miuix popupHost。
 *
 * Miuix 的弹窗/下拉通过 `LocalPopupStates` 注册到最近的 Miuix `Scaffold`，
 * 用零背景、无手势占位的 Miuix Scaffold 包裹即可让两套 UI 无缝共存在同一窗口内。
 */
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

/**
 * Material 3 Expressive 经典二级页脚手架：
 * 可折叠大标题 [LargeFlexibleTopAppBar] + Tonal 返回圆钮 + 底部 [PageFeedbackSlot]。
 */
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

/**
 * M3E 的固定尺寸，集中一处。
 *
 * ## 为什么要有这个对象
 *
 * 这些值原先散在各处（`52.dp`、`40.dp`、`14.dp` 内边距），而它们分属
 * M3E 规范的**不同档位**：行高是列表项的语义尺寸，图标块是控件档位，
 * 内边距是排版节奏。散着写就看不出"这个 52 应该是 56"。
 *
 * ## 数值来源
 *
 * 全部取自官方 tokens（`material3:1.5.0-alpha16`，反编译见 `docs/ui-m3e.md`）：
 *
 * | 项 | 值 | 依据 |
 * | --- | --- | --- |
 * | 单行列表项 | 56dp | M3E list item 单行 |
 * | 双行列表项 | 72dp | M3E list item 双行 |
 * | 三行列表项 | 88dp | M3E list item 三行 |
 * | 控件高度（Medium 档） | 56dp | `ButtonMediumTokens.ContainerHeight` |
 * | 图标块 | 40dp | Small 档视觉尺寸 |
 * | 最小触控热区 | 48dp | Android 无障碍下限，与视觉高度无关 |
 * | 行内水平内边距 | 16dp | M3E 列表项左右留白 |
 */
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
}

/**
 * 分组小标题。
 *
 * ## 为什么用 `labelLarge` 而不是更大的字号
 *
 * M3E 的分组小标题属于 Label 档，是「标签」不是「标题」。用 `titleSmall`
 * 会与行内的 `titleMedium` 形成两个几乎同级的标题层，反而看不出哪个是分组。
 */
@Composable
internal fun M3eSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * M3E 分段列表卡片的位置形状。
 *
 * ## 数值来源
 *
 * 外角取 M3E `CornerExtraLarge` = **28dp**，相邻内角取 `CornerSmall` = **8dp**
 * （官方 `ShapeTokens` 的字节码值，见 `docs/ui-m3e.md`）。
 *
 * ## 为什么原来是 24 / 6
 *
 * 24 与 6 都不在任何一档官方形状上 —— 24 介于 `CornerLargeIncreased`(20) 与
 * `CornerExtraLarge`(28) 之间，6 介于 `CornerExtraSmall`(4) 与 `CornerSmall`(8)
 * 之间。取中间值让这张卡**既不属于任何既有档位、也和外层的
 * `MaterialTheme.shapes` 对不上**：`extraLarge` 是 28，卡片却是 24，
 * 同一屏里两种大圆角并存。
 *
 * 现在外角与 `shapes.extraLarge` 一致，内角与 `shapes.small` 一致，
 * 于是「容器 / 内部块」的关系可以从主题形状表直接读出来，不必记特例。
 *
 * 内角 8 与外角 28 的差 20 恰好是行内水平内边距的两倍少一点 ——
 * 这不是同心公式的结果（同心要求内角 = 外角 − 内边距 = 28 − 16 = 12），
 * 而是分段卡片特有的"相邻两块之间要留出可见的缝"的取舍：
 * 12 会让缝隙看起来像一条被压扁的管子，8 才是 M3E 分段列表的实际观感。
 */
internal fun m3eSegmentShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(28.dp)
    index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    else -> RoundedCornerShape(8.dp)
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
    val shape = m3eSegmentShape(index, count)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Surface(
        // 视觉 40dp（M3E Small 档），字形 20dp。
        // 40 不是触控目标 —— 可点的行由外层整行承担点击，热区远大于此。
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
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    M3eSegmentedItem(index = index, count = count, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 双行列表项 72dp（M3E 规范：单行 56 / 双行 72 / 三行 88）。
                // 用 `heightIn(min)` 而不是固定 `height`：系统字号调大时文字要能长高，
                // 硬定高会裁字（`docs/ui-m3e.md` 与 `ui-miuix.md` 都记着这条）。
                .heightIn(min = M3eDimens.RowDouble)
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                M3eIconBadge(icon, tint = iconTint, containerColor = iconContainer)
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                val desc = valueText ?: summary
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
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = ZhiIcons.arrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
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
                // 有 summary 就是双行（72dp），没有就是单行（56dp）。
                // 这两档是 M3E 规范值，不是随手挑的 —— 原先这里只有 padding，
                // 行高由文字撑出 60 出头，与规范差 12dp，与其它行也对不齐。
                .heightIn(min = if (summary.isNullOrBlank()) M3eDimens.RowSingle else M3eDimens.RowDouble)
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                M3eIconBadge(
                    painter = icon,
                    tint = if (warn) scheme.onErrorContainer else scheme.onPrimaryContainer,
                    containerColor = if (warn) scheme.errorContainer else scheme.primaryContainer,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.45f),
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warn) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
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
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodySmall,
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
