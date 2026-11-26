package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiGlass
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
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
/** 限制顶栏品牌显示宽度并在超长时横向连续滚动。 */
internal fun Modifier.brandSignatureMarquee(): Modifier = this
    .widthIn(max = 188.dp)
    .clipToBounds()
    .basicMarquee(iterations = Int.MAX_VALUE)

@Immutable
data class TopBarState(
    val composerBusy: Boolean,
    val modelLabel: String,
    val deviceStatus: String,
    val tab: WorkspaceTab,
) {
    companion object {
        /** 从整份 UiState 里取出顶栏要用的那几项。 */
        fun from(state: WorkspaceUiState) = TopBarState(
            composerBusy = state.composerBusy,
            modelLabel = state.modelLabel,
            deviceStatus = state.deviceStatus,
            tab = state.tab,
        )
    }
}

@Composable
fun ZhiTopBar(
    state: TopBarState,
    wide: Boolean,
    glass: Glass,
    onOpenSidebar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<WorkspaceTab>? = null,
    onSelectTab: (WorkspaceTab) -> Unit = {},
    selectedTab: WorkspaceTab = state.tab,
    pagerPage: Int? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val brandTitle = LocalThemeSettings.current.resolvedTopBrand

    ZhiFrameTrace.countRecompose("TopBar")
    val blurActive = glass.supported
    val containerColor = if (blurActive) Color.Transparent else scheme.surfaceContainer
    Box(
        modifier = modifier.then(
            if (blurActive) {
                Modifier.textureBlur(
                    backdrop = glass.backdrop!!,
                    shape = RectangleShape,
                    blurRadius = ZhiGlass.FloatingBlur,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(
                                color = if (ZhiColors.isDark()) {
                                    scheme.surface.copy(alpha = ZhiGlass.TopBarAlpha)
                                } else {
                                    Color.White.copy(alpha = 0.78f)
                                },
                            ),
                        ),
                    ),
                )
            } else {
                Modifier
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
            ) {
                if (!wide) {
                    ZhiIconButton(
                        icon = ZhiIcons.menu,
                        description = "打开侧栏",
                        onClick = onOpenSidebar,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Text(
                        text = brandTitle,
                        color = scheme.onSurface,
                        fontSize = MiuixTheme.textStyles.title3.fontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .brandSignatureMarquee(),
                    )
                    TooltipBox(
                        text = "打开设置",
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        ZhiIconButton(
                            compact = 34.dp,
                            icon = ZhiIcons.settings,
                            description = "打开设置",
                            onClick = onSettings,
                            tint = scheme.onSurface,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = brandTitle,
                            color = scheme.onSurface,
                            fontSize = MiuixTheme.textStyles.title3.fontSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.brandSignatureMarquee(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (state.composerBusy) "工作中 · ${state.modelLabel}" else state.modelLabel,
                            color = if (state.composerBusy) scheme.primary else scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Text(
                            text = state.deviceStatus,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        TooltipBox(text = "打开设置") {
                            ZhiIconButton(
                                compact = 34.dp,
                                icon = ZhiIcons.settings,
                                description = "打开设置",
                                onClick = onSettings,
                                tint = scheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (tabs != null) {
                WorkspaceTabs(
                    tabs = tabs,
                    selected = selectedTab,
                    onSelect = onSelectTab,
                    pagerPage = pagerPage,
                )
            }
            if (state.composerBusy) ZhiIndeterminateBar() else ZhiHorizontalDivider(color = scheme.dividerLine)
        }
    }
}
