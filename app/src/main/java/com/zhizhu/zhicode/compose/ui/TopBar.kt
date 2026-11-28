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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur

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
    val scheme = MaterialTheme.colorScheme
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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .brandSignatureMarquee(),
                    )
                    M3eTooltip(
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
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.brandSignatureMarquee(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (state.composerBusy) "工作中 · ${state.modelLabel}" else state.modelLabel,
                            color = if (state.composerBusy) scheme.primary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Text(
                            text = state.deviceStatus,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        M3eTooltip(text = "打开设置") {
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
            if (state.composerBusy) ZhiIndeterminateBar() else ZhiHorizontalDivider(color = scheme.outlineVariant)
        }
    }
}
