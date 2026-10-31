package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.data.BottomBarStyle
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.theme.MochiBaseline
import com.zhizhu.zhicode.compose.theme.THEME_PRESETS
import com.zhizhu.zhicode.compose.theme.ThemePreset
import com.zhizhu.zhicode.compose.theme.UI_SCALE_MAX
import com.zhizhu.zhicode.compose.theme.UI_SCALE_MIN
import com.zhizhu.zhicode.compose.theme.formatUiScale
import com.zhizhu.zhicode.compose.theme.presetById
import com.zhizhu.zhicode.compose.ui.PageFeedbackSlot
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsNumberDialog
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsSliderRow
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ThemePage(
    theme: ThemeSettings,
    onDismiss: () -> Unit,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val backdrop = rememberPageBlurBackdrop()
    var scaleDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                TopAppBar(
                    title = "主题设置",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painter = ZhiIcons.back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxHeight().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding() + 16.dp),
                ) {
                    item(key = "preview") {
                        Spacer(Modifier.height(32.dp))
                        ThemePreviewPhone(theme = theme, modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(Modifier.height(36.dp))
                    }
                    item(key = "mode") {
                        TabRow(
                            tabs = listOf("跟随系统", "浅色", "深色"),
                            selectedTabIndex = ThemeMode.entries.indexOf(theme.mode).coerceIn(0, 2),
                            onTabSelected = { index -> onChange { it.copy(mode = ThemeMode.entries[index]) } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            height = 48.dp,
                        )
                    }
                    item(key = "monet") {
                        SettingsGroup("颜色") {
                            SettingsToggle(
                                title = "Monet 动态取色",
                                checked = theme.dynamicColor,
                                onCheckedChange = { on -> onChange { it.copy(dynamicColor = on) } },
                                summary = "根据强调色生成整套动态色板",
                                icon = ZhiIcons.layers,
                                plate = SettingsPlateColors.purple,
                            )
                            PresetRow(
                                selectedId = theme.presetId,
                                onSelect = { preset -> onChange { it.copy(presetId = preset.id, customSeedArgb = null) } },
                            )
                            ColoredCascade(visible = theme.dynamicColor) {
                                Column {
                                    val paletteStyles = ThemePaletteStyle.entries
                                    OverlayDropdownPreference(
                                        title = "调色板风格", items = paletteStyles.map { it.name },
                                        selectedIndex = paletteStyles.indexOfFirst { it.name == theme.paletteStyle }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index -> paletteStyles.getOrNull(index)?.let { style -> onChange { it.copy(paletteStyle = style.name) } } },
                                    )
                                    val specs = ThemeColorSpec.entries
                                    OverlayDropdownPreference(
                                        title = "颜色规范", items = specs.map { it.name },
                                        selectedIndex = specs.indexOfFirst { it.name == theme.colorSpec }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index -> specs.getOrNull(index)?.let { spec -> onChange { it.copy(colorSpec = spec.name) } } },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "navigation") {
                        SettingsGroup("NavigationBar") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "NavigationBar 位置",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                ZhiSegmentedTabs(
                                    tabs = TabBarPosition.entries.map { it.label },
                                    selectedIndex = TabBarPosition.entries.indexOf(theme.tabBarPositionValue),
                                    onSelect = { index -> TabBarPosition.entries.getOrNull(index)?.let { pos -> onChange { it.copy(tabBarPosition = pos.name) } } },
                                    modifier = Modifier.width(160.dp), matchWidth = true,
                                )
                            }
                            ColoredCascade(visible = theme.tabBarPositionValue == TabBarPosition.BOTTOM) {
                                SettingsToggle(
                                    title = "悬浮 NavigationBar", checked = theme.tabBarFloating,
                                    onCheckedChange = { on -> onChange { it.copy(tabBarFloating = on) } },
                                    summary = "开：液态玻璃悬浮胶囊；关：Miuix 固定 NavigationBar",
                                    icon = ZhiIcons.chat, plate = SettingsPlateColors.blue,
                                )
                                ColoredCascade(visible = theme.tabBarFloating) {
                                    SettingsToggle(
                                        title = "悬浮栏背景模糊", checked = theme.floatingNavBlur,
                                        onCheckedChange = { on -> onChange { it.copy(floatingNavBlur = on) } },
                                        summary = "关闭后保留胶囊形态，改用纯色底",
                                        enabled = isRuntimeShaderSupported(), icon = ZhiIcons.layers, plate = SettingsPlateColors.purple,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "effects") {
                        SettingsGroup("效果") {
                            val shaderSupported = isRuntimeShaderSupported()
                            SettingsToggle(
                                title = "顶部与底部模糊", checked = theme.blurEnabled,
                                onCheckedChange = { on -> onChange { it.copy(blurEnabled = on) } },
                                summary = if (shaderSupported) "为导航栏和悬浮层启用背景模糊" else "需要 Android 13 RuntimeShader 支持",
                                enabled = shaderSupported, icon = ZhiIcons.layers, plate = SettingsPlateColors.blue,
                            )
                            if (android.os.Build.VERSION.SDK_INT >= 34) SettingsToggle(
                                title = "预测性返回手势", checked = theme.predictiveBack,
                                onCheckedChange = { on -> onChange { it.copy(predictiveBack = on) } },
                                summary = "系统返回手势拖动时预览上一页", icon = ZhiIcons.back, plate = SettingsPlateColors.green,
                            )
                        }
                    }
                    item(key = "scale") {
                        SettingsGroup("界面缩放") {
                            SettingsSliderRow(
                                title = "页面缩放", summary = "调整全局界面密度，不影响系统字体设置",
                                value = theme.uiScale, valueText = formatUiScale(theme.uiScale), valueTextFor = { formatUiScale(it) },
                                onValueChange = { }, commitValue = { scale -> onChange { it.copy(uiScale = scale) } },
                                onValueClick = { scaleDialogOpen = true }, valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                                showKeyPoints = true, keyPoints = listOf(UI_SCALE_MIN, 1f, UI_SCALE_MAX), magnetThreshold = 0.01f,
                            )
                        }
                    }
                    item(key = "bottomSpace") { Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)) }
                }
            }
            PageFeedbackSlot(modifier = Modifier.fillMaxWidth())
        }
        SettingsNumberDialog(
            show = scaleDialogOpen, title = "界面缩放", initial = Math.round(theme.uiScale * 100f),
            range = Math.round(UI_SCALE_MIN * 100f)..Math.round(UI_SCALE_MAX * 100f),
            onConfirm = { value -> scaleDialogOpen = false; onChange { it.copy(uiScale = value / 100f) } },
            onDismiss = { scaleDialogOpen = false }, summary = "输入整数百分比。",
        )
    }
}

@Composable
private fun PresetRow(selectedId: String, onSelect: (ThemePreset) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("预设强调色", color = scheme.onSurface, fontSize = MiuixTheme.textStyles.body2.fontSize, modifier = Modifier.padding(bottom = 8.dp))
        THEME_PRESETS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    Column(Modifier.weight(1f).clickable { onSelect(preset) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color(preset.seedArgb)).then(if (preset.id == selectedId) Modifier.border(2.dp, scheme.onSurface, RoundedCornerShape(9.dp)) else Modifier))
                        Text(preset.name, color = if (preset.id == selectedId) scheme.primary else scheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ColoredCascade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column { content() }
    }
}

/**
 * 主题页那张**微型手机示意图**里的字号。
 *
 * ## 为什么这组值不读 `MiuixTheme.textStyles`
 *
 * 那张图是「把整屏按比例缩到 40% 宽」的**插图**，不是真实界面：它的"屏幕宽度"只有
 * 一百多 dp，任何真实字阶（最小 11sp）都会把三行标签挤成两行。所以它必须用一套
 * 按插图比例算出来的字号。
 *
 * 规范里的「字号只读 textStyles」约束的是**真实界面的文字**；这类比例示意图属于
 * 规则 5 里「明确注明理由的例外」。把四个数字集中在这里（而不是散在 12 个 `Text`
 * 调用点）是为了让"插图缩小了几号"在源码里只有一个答案。
 */
private object PreviewType {
    /** 示意图顶部的应用名。 */
    val caption = 9.sp

    /** 顶部右侧的状态点。 */
    val dot = 8.sp

    /** 示意图里的正文（终端行、文件行、标签文字）。 */
    val body = 7.sp

    /** 悬浮胶囊里的标签（比 [body] 再小一号，因为胶囊内部还有 8dp 横向内边距）。 */
    val micro = 6.sp
}

@Composable
private fun ThemePreviewPhone(theme: ThemeSettings, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    val preset = remember(theme.presetId) { presetById(theme.presetId) }
    val pages = listOf("对话", "终端", "文件")

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        /*
         * 三台手机并排，**各占一屏**：对话 / 终端 / 文件。
         *
         * 改版前是**一台**手机 + 一个内部页码状态，点下面的标签切换里面显示哪一屏。
         * 那样有两个毛病：一是用户得逐个点才知道三套界面长什么样；二是
         * 「标签栏位置 / 是否悬浮」这类设置会影响三屏共同的导航形态，
         * 切换着看反而看不到"同一时刻三屏的导航是否一致"。
         *
         * 现在三台同时在场，一眼能比对导航栏在三屏上的位置与形态是否统一。
         * 宽度按 1:1:1 分配，所以每台比原来窄 —— 内部字号与间距也随之等比收窄，
         * 见 `PreviewType` 的注释。
         */
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            pages.forEachIndexed { index, label ->
                PreviewDevice(
                    page = index,
                    label = label,
                    theme = theme,
                    scheme = scheme,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(preset.name, color = scheme.onSurface, fontSize = MiuixTheme.textStyles.body1.fontSize, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
        Text(
            "${if (theme.dynamicColor) "Monet · ${theme.paletteStyle}" else "固定色板"} · " +
                if (theme.tabBarPositionValue == TabBarPosition.BOTTOM) {
                    if (theme.tabBarFloating) "悬浮导航" else "NavigationBar"
                } else {
                    "顶部标签"
                },
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * 单台"手机"预览：固定渲染一屏，不再带内部页码。
 *
 * 圆角、描边、底色都走主题，所以换预设色卡 / 明暗时三台一起变。
 */
@Composable
private fun PreviewDevice(
    page: Int,
    label: String,
    theme: ThemeSettings,
    scheme: Colors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                // 手机屏比例固定 9:19.5，不跟随设备 —— 三台并排时各自尺寸必须一致，
                // 否则"对比"这件事本身就不成立。
                .aspectRatio(0.46f)
                .clip(ZhiSquircle(MochiBaseline.cornerLarge))
                .background(scheme.background)
                .border(1.dp, scheme.outline, ZhiSquircle(MochiBaseline.cornerLarge)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ZhiCode", color = scheme.onBackground, fontSize = PreviewType.micro, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("••", color = scheme.primary, fontSize = PreviewType.micro)
                }
                if (theme.tabBarPositionValue == TabBarPosition.TOP) {
                    ThemePreviewTabs(listOf("对话", "终端", "文件"), page, {}, scheme, compact = true)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 5.dp, vertical = 3.dp)) {
                    PreviewScreenBody(page, scheme)
                }
                if (theme.tabBarPositionValue == TabBarPosition.BOTTOM && !theme.tabBarFloating) {
                    Row(
                        Modifier.fillMaxWidth().background(scheme.surfaceContainer),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf("对话", "终端", "文件").forEachIndexed { index, text ->
                            Text(
                                text,
                                color = if (index == page) scheme.primary else scheme.onSurfaceVariantSummary,
                                fontSize = PreviewType.micro,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)
                        .clip(if (theme.floatingBar && theme.bottomBarStyleValue == BottomBarStyle.IOS) CircleShape else ZhiSquircle(MochiBaseline.cornerExtraSmall))
                        .background(scheme.surfaceContainer.copy(alpha = if (theme.liquidGlassEffective) 0.65f else 1f))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.12f)))
                    Box(Modifier.padding(start = 4.dp).size(6.dp).clip(CircleShape).background(scheme.primary))
                }
            }
            if (theme.tabBarPositionValue == TabBarPosition.BOTTOM && theme.tabBarFloating) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer.copy(alpha = if (theme.floatingNavBlur && theme.blurEnabled) 0.56f else 1f))
                        .border(0.5.dp, scheme.onSurface.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("对话", "终端", "文件").forEachIndexed { index, text ->
                        Text(
                            text,
                            color = if (index == page) scheme.primary else scheme.onSurfaceVariantSummary,
                            fontSize = PreviewType.micro,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            label,
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 三屏各自的示意内容：气泡 / 终端输出 / 文件列表。 */
@Composable
private fun PreviewScreenBody(page: Int, scheme: Colors) {
    when (page) {
        0 -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth(0.76f).height(14.dp).align(Alignment.End).clip(CircleShape).background(scheme.primaryContainer))
            Box(Modifier.fillMaxWidth(0.9f).height(24.dp).clip(ZhiSquircle(MochiBaseline.cornerSmall)).background(scheme.surfaceContainer))
            Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(CircleShape).background(scheme.surfaceContainer))
        }
        1 -> Column(
            Modifier.fillMaxSize().clip(ZhiSquircle(MochiBaseline.cornerExtraSmall))
                .background(scheme.surfaceContainerHighest).padding(4.dp),
        ) {
            Text("$ cd project", color = scheme.primary, fontSize = PreviewType.micro, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text("› build ok", color = scheme.onSurface, fontSize = PreviewType.micro, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text("$ ls", color = scheme.primary, fontSize = PreviewType.micro, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("src", "build", "README").forEach {
                Text("▰  $it", color = scheme.onSurface, fontSize = PreviewType.micro, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ThemePreviewTabs(
    pages: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    scheme: Colors,
    compact: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 2.dp else 3.dp)
            .clip(ZhiSquircle(MochiBaseline.cornerExtraSmall))
            .background(scheme.surfaceContainerHigh)
            .padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        pages.forEachIndexed { index, label ->
            val active = selected == index
            Box(
                Modifier.weight(1f)
                    .clip(ZhiSquircle(MochiBaseline.cornerExtraSmall))
                    .background(if (active) scheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = if (compact) 2.dp else 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = if (compact) PreviewType.micro else PreviewType.body,
                    maxLines = 1,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
