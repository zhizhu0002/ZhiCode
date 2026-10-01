package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏（UI 重构 S2）：容器迁到官方 [SmallTopAppBar]。
 *
 * 结构与 Miuix example 的 BlurredBar + AdaptiveTopAppBar 一致：
 * 玻璃模糊由调用方（AppScaffold/WideWorkspace）包在 ZhiTopBar 外层，
 * 这里只负责官方槽位：title / navigationIcon / actions / bottomContent。
 *
 * 官方默认 padding 与 50dp 高度全部保留（不再覆写）。
 */
@Composable
fun ZhiTopBar(
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
    tabs: List<WorkspaceTab>? = null,
    onSelectTab: (WorkspaceTab) -> Unit = {},
) {
    val scheme = MiuixTheme.colorScheme

    SmallTopAppBar(
        modifier = modifier,
        title = "ZhiCode",
        // 玻璃半透明底：SmallTopAppBar 内部 background(color)，
        // 让 blur 结果透过官方容器显示。
        color = glass.surfaceColor(scheme.surface),
        defaultWindowInsetsPadding = false,
        navigationIcon = {
            if (!wide) {
                ZhiIconButton(
                    icon = ZhiIcons.menu,
                    description = "打开侧栏",
                    onClick = onOpenSidebar,
                )
            }
        },
        actions = {
            if (wide) {
                Text(
                    text = if (state.composerBusy) "● 工作中 · ${state.modelLabel}" else "● ${state.modelLabel}",
                    color = if (state.composerBusy) scheme.primary else scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }

            // 上下文 chip 按占用率变色：≥90% 红底红字、≥72% 强调色、否则弱化色
            val ctxPct = if (state.contextWindow > 0) {
                Math.round(state.contextTokens * 100.0 / state.contextWindow).toInt()
            } else {
                0
            }
            ZhiChip(
                label = WorkspaceViewModel.formatTokens(state.contextTokens) + "/" +
                    WorkspaceViewModel.formatTokens(state.contextWindow),
                onClick = onContextClick,
                fontSize = ZhiTextScale.Footnote,
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
                    fontSize = ZhiTextScale.Footnote,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // 图标按钮包 Miuix TooltipBox，长按出说明气泡
            TooltipBox(text = "打开设置") {
                ZhiIconButton(
                    compact = 34.dp,
                    icon = ZhiIcons.settings,
                    description = "打开设置",
                    onClick = onSettings,
                )
            }
        },
        bottomContent = {
            // 服务中时是 Miuix 不确定进度条；空闲且无 Tab 时退化成极淡分隔线
            if (state.composerBusy) {
                ZhiIndeterminateBar()
            } else if (tabs == null) {
                ZhiHorizontalDivider(color = scheme.dividerLine)
            }
            // 工作区 Tab 归顺到头部：与标题同一块玻璃（官方 bottomContent 槽位）
            if (tabs != null) {
                WorkspaceTabs(
                    tabs = tabs,
                    selected = state.tab,
                    onSelect = onSelectTab,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = TopBarTabRowPadding / 2),
                )
            }
        },
    )
}
