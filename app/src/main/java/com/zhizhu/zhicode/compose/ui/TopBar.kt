package com.zhizhu.zhicode.compose.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiGlass
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏**真正读到的**那六个字段。
 *
 * <h2>为什么要单独抽一个类型，而不是直接传 `WorkspaceUiState`</h2>
 *
 * 顶栏只用到 `composerBusy` / `modelLabel` / `contextTokens` / `contextWindow` /
 * `deviceStatus` / `tab` 这六项，而 `WorkspaceUiState` 有五十多个字段 ——
 * 其中包含**整条对话流**（`transcript`）与全部工具输出。
 *
 * 流式回复期间 `_state` 每 32ms 换一次新实例（`ZhiEngineController.DELTA_MERGE_MS`），
 * 于是顶栏每次都要重来一遍：
 *
 * 1. 参数变了 → 顶栏整体重组（虽然它一个像素都不会变）；
 * 2. 判等本身也要钱 —— `List.equals` 是**逐个元素**比的，比到那条正在流式的消息
 *    才会因正文字符串不同而停下，也就是说前面每一条消息的所有字段都被逐个走过。
 *
 * 换成这六个值之后，两个代价同时消失：参数没变就是没变。
 *
 * <h2>为什么这个类型是 @Immutable</h2>
 *
 * 全部字段都是 `val` 且都是 String / Int / Boolean / enum ——
 * 没有 List、没有可变对象，所以这个承诺是**可核对的**（不是靠"看起来没问题"）。
 * 编译器据此把顶栏的跳过判断简化成逐字段比较，而不是退化成整体 "unstable"。
 */
@Immutable
data class TopBarState(
    val composerBusy: Boolean,
    val modelLabel: String,
    val contextTokens: Int,
    val contextWindow: Int,
    val deviceStatus: String,
    val tab: WorkspaceTab,
) {
    companion object {
        /** 从整份 UiState 里取出顶栏要用的那几项。 */
        fun from(state: WorkspaceUiState) = TopBarState(
            composerBusy = state.composerBusy,
            modelLabel = state.modelLabel,
            contextTokens = state.contextTokens,
            contextWindow = state.contextWindow,
            deviceStatus = state.deviceStatus,
            tab = state.tab,
        )
    }
}

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
    state: TopBarState,
    wide: Boolean,
    glass: Glass,
    onOpenSidebar: () -> Unit,
    onContextClick: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // 随头部一起悬浮的按键组；为 null 时不渲染（宽屏的按键组在右侧栏各自的位置）。
    tabs: List<WorkspaceTab>? = null,
    onSelectTab: (WorkspaceTab) -> Unit = {},
    selectedTab: WorkspaceTab = state.tab,
) {
    val scheme = MiuixTheme.colorScheme

    // 官方 BlurredBar 模式（miuix example/utils/PageUtils.kt:127）：
    // textureBlur 挂在**包裹 Box** 上并混入一层 surface(0.8) 做磨砂底色，
    // TopAppBar 自身底色取透明 —— 之前把 surface(0.72) 直接叠在 blur 修饰符上，
    // 半透明底色把模糊结果盖死，肉眼等于没有 blur。
    ZhiFrameTrace.countRecompose("TopBar")
    val blurActive = glass.supported
    Box(
        modifier = modifier.then(
            if (blurActive) {
                Modifier.textureBlur(
                    backdrop = glass.backdrop!!,
                    shape = RectangleShape,
                    blurRadius = ZhiGlass.FloatingBlur,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(color = if (ZhiColors.isDark()) scheme.surface.copy(alpha = 0.8f) else Color.White)),
                    ),
                )
            } else {
                Modifier
            },
        ),
    ) {
    SmallTopAppBar(
        modifier = Modifier,
        title = "ZhiCode",
        color = if (blurActive) Color.Transparent else scheme.surfaceContainer,
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
                    text = if (state.composerBusy) "工作中 · ${state.modelLabel}" else state.modelLabel,
                    color = if (state.composerBusy) scheme.primary else scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                containerColor = when {
                    ctxPct >= 90 -> scheme.errorContainer
                    ctxPct >= 72 -> scheme.primary.copy(alpha = 0.12f)
                    else -> Color.Unspecified
                },
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
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
                    tint = scheme.onSurface,
                )
            }
        },
        bottomContent = {
            Column {
                if (tabs != null) {
                    WorkspaceTabs(
                        tabs = tabs,
                        selected = selectedTab,
                        onSelect = onSelectTab,
                    )
                }
                // 服务中时是 Miuix 不确定进度条；空闲时以分隔线结束顶栏（与可选标签行一起）。
                if (state.composerBusy) ZhiIndeterminateBar() else ZhiHorizontalDivider(color = scheme.dividerLine)
            }
        },
    )
    }
}
