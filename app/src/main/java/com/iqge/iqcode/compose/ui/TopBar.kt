package com.iqge.iqcode.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.ThemeMode
import com.iqge.iqcode.compose.model.WorkspaceTab
import com.iqge.iqcode.compose.model.WorkspaceUiState
import com.iqge.iqcode.compose.state.WorkspaceViewModel
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏，逐项对应原 IQ Code 的 buildGlobalBar()：
 * 侧栏入口 · 品牌名 · 模型状态 · 上下文 chip · 设备时间 · 主题切换 · 悬浮球 · 设置。
 *
 * 原版是手写 LinearLayout 行，这里保持同样的单行紧凑结构（48dp）；
 * 容器走 Miuix `Surface`，按钮走 `IconButton`，chip 走 `Surface`，
 * 底部分隔线/忙碌条走 `HorizontalDivider` 与 `LinearProgressIndicator`。
 * 全部图标来自 Miuix 图标库（`IqIcons`）。
 */
@Composable
fun IqTopBar(
    state: WorkspaceUiState,
    wide: Boolean,
    glass: Glass,
    onOpenSidebar: () -> Unit,
    onContextClick: () -> Unit,
    onCycleTheme: () -> Unit,
    onFloatingBall: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // 随头部一起悬浮的按键组；为 null 时不渲染（宽屏的按键组在右侧栏各自的位置）。
    // 放进同一个 Surface 里是为了共用一次 blur：分成两块会看到两条不同的模糊接缝。
    tabs: List<WorkspaceTab>? = null,
    onSelectTab: (WorkspaceTab) -> Unit = {},
) {
    val scheme = MiuixTheme.colorScheme
    // 下半圆角：悬浮顶栏两侧能看到底下的对话流过，玻璃质感更明确
    val shape = RoundedCornerShape(bottomStart = IqRadius.floating, bottomEnd = IqRadius.floating)

    Box(modifier = modifier.fillMaxWidth().then(glass.blur(Modifier, shape, radius = 24f))) {
        Surface(color = glass.surfaceColor(scheme.surface), shape = shape) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 高度必须用 AppScaffold 里的同一常量：
                    // 面板的首行留白是按它算出来的，两边不一致就会被悬浮头部压住。
                    .height(TopBarTitleRowHeight)
                    .padding(start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!wide) {
                    IqIconButton(
                        icon = IqIcons.menu,
                        description = "打开侧栏",
                        onClick = onOpenSidebar,
                    )
                }

                Text(
                    text = "IQ Code",
                    color = scheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )

                Box(modifier = Modifier.weight(1f))

                if (wide) {
                    Text(
                        text = if (state.composerBusy) "● 工作中 · ${state.modelLabel}" else "● ${state.modelLabel}",
                        color = if (state.composerBusy) scheme.primary else scheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }

                // 上下文 chip 按占用率变色，复刻原版 updateContextChip()
                // （MainActivity.java:4107-4114）：≥90% 红底红字、≥72% 强调色、否则弱化色。
                // 原版用暖色主题的固定红，这里改走 Miuix 语义色，保持"原生蓝"主题体系。
                val ctxPct = if (state.contextWindow > 0) {
                    Math.round(state.contextTokens * 100.0 / state.contextWindow).toInt()
                } else {
                    0
                }
                IqChip(
                    label = WorkspaceViewModel.formatTokens(state.contextTokens) + "/" +
                        WorkspaceViewModel.formatTokens(state.contextWindow),
                    onClick = onContextClick,
                    modifier = Modifier.widthIn(min = if (wide) 92.dp else 74.dp),
                    fontSize = 10.sp,
                    containerColor = if (ctxPct >= 90) scheme.errorContainer else Color.Unspecified,
                    contentColor = when {
                        ctxPct >= 90 -> scheme.error
                        ctxPct >= 72 -> scheme.primary
                        else -> Color.Unspecified
                    },
                )

                if (wide) {
                    Text(
                        text = state.deviceStatus,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                // 图标按钮都包 Miuix TooltipBox，长按出说明气泡
                TooltipBox(text = "切换主题，当前：${state.themeMode.label}") {
                    IqIconButton(
                        icon = IqIcons.theme,
                        description = "切换主题，当前：${state.themeMode.label}",
                        onClick = onCycleTheme,
                        tint = if (state.themeMode == ThemeMode.SYSTEM) Color.Unspecified else scheme.primary,
                    )
                }
                TooltipBox(text = "系统悬浮球") {
                    IqIconButton(
                        icon = IqIcons.floatingBall,
                        description = "系统悬浮球",
                        onClick = onFloatingBall,
                    )
                }
                TooltipBox(text = "打开设置") {
                    IqIconButton(
                        icon = IqIcons.settings,
                        description = "打开设置",
                        onClick = onSettings,
                    )
                }
            }

            // 服务中时是 Miuix 不确定进度条；空闲时退化成极淡的分隔线
            if (state.composerBusy) {
                IqIndeterminateBar()
            } else if (tabs == null) {
                IqHorizontalDivider(color = scheme.dividerLine)
            }

            // 工作区 Tab 归顺到头部：与标题行同一块玻璃，不再被悬浮顶栏遮住。
            if (tabs != null) {
                WorkspaceTabs(
                    tabs = tabs,
                    selected = state.tab,
                    onSelect = onSelectTab,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        // 上下留白取常量的一半，总高才等于 TopBarTabRowPadding 的假定值
                        vertical = TopBarTabRowPadding / 2,
                    ),
                )
            }
        }
        }
    }
}
