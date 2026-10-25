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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ThemeMode
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
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsSliderRow
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 主题设置**整页** —— 形态照 Miuix 官方 example / `ksu-style` 的
 * `ColorPaletteScreenMiuix`。
 *
 * ## 与上一版的差别（功能没变，只改形态与手感）
 *
 * | | 上一版 | 这一版 |
 * |---|---|---|
 * | 壳 | `SettingsSubPage`（普通顶栏） | 自有 Scaffold + **模糊顶栏**（`PageBlurBar`） |
 * | 预览 | 「标题 + 三块色条」 | **按屏幕比例画一台迷你手机**（顶栏条 / 强调块 / 两个容器块 / 底栏两态） |
 * | 深浅三态 | `SettingsChoice` 下拉 | **`TabRowWithContour` 分段**（HyperOS 那种一格一格） |
 * | 缩放 | 独立 `SliderPreference` | `SettingsSliderRow`（值胶囊 + 关键点磁吸 + 刻度触感） |
 *
 * ## 为什么预览要画成"迷你手机"
 *
 * 一块色条只能说明**主色是什么**，说不出"顶栏会不会脏""卡片与背板分不分得开"
 * "底栏那条会不会糊成一片"。ksu-style 的做法是把整页缩小成 40% 宽的手机轮廓，
 * 色块按真实层级摆放 —— 用户看到的**就是**这个主题下的界面，而不是一张配料表。
 *
 * 色值全部来自 `MiuixTheme.colorScheme`（它已经是对当前生成色板的真实读取），
 * 所以预览与真实界面永远一致，不存在"截图过期"的问题。
 *
 * ## 刻意没有的开关
 *
 * 「预测性返回手势」。Miuix 0.9.4 的 `NavDisplay` 把 predictive back 做成内置能力且
 * **没有关闭 API**，做成开关就是一个点了没用的假开关。
 */
@Composable
fun ThemePage(
    theme: ThemeSettings,
    onDismiss: () -> Unit,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val backdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                TopAppBar(
                    title = "主题设置",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = ZhiIcons.back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxHeight()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    item(key = "preview") {
                        Spacer(modifier = Modifier.height(20.dp))
                        ThemePreviewPhone(
                            theme = theme,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item(key = "modeTab") {
                        // 三态分段。用 `ZhiSegmentedTabs`（`TabRowWithContour`）而不是
                        // `TabRow`：后者未选中项也有可见描边，像一排独立按钮；
                        // 前者才是 HyperOS 的分段观感（见 `Common.kt` 里那段对比）。
                        ZhiSegmentedTabs(
                            tabs = ThemeMode.entries.map { it.label },
                            selectedIndex = ThemeMode.entries.indexOf(theme.mode),
                            onSelect = { index ->
                                onChange { it.copy(mode = ThemeMode.entries[index]) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            matchWidth = true,
                        )
                    }

                    item(key = "appearance") {
                        SettingsGroup("外观") {
                            SettingsToggle(
                                title = "纯黑（AMOLED）",
                                checked = theme.amoled,
                                onCheckedChange = { enabled -> onChange { it.copy(amoled = enabled) } },
                                summary = "深色模式下背板压成纯黑，省电且对比更高；浅色模式不受影响",
                                icon = ZhiIcons.sandbox,
                                plate = SettingsPlateColors.gray,
                            )
                        }
                    }

                    item(key = "color") {
                        SettingsGroup("颜色") {
                            SettingsToggle(
                                title = "启用 Monet 颜色",
                                checked = theme.dynamicColor,
                                onCheckedChange = { enabled -> onChange { it.copy(dynamicColor = enabled) } },
                                summary = "用种子色生成整套色板；关闭时用应用内置的固定色板",
                                icon = ZhiIcons.tune,
                                plate = SettingsPlateColors.blue,
                            )
                            // 预设色卡在两种模式下都有用：Monet 开着时它是"选种子色"，
                            // 关着时它是"选强调色的初值"。所以**不**按 dynamicColor 隐藏。
                            PresetRow(
                                selectedId = theme.presetId,
                                onSelect = { preset ->
                                    // 点预设 = 放弃自定义色（那是"我改主意了"的语义）。
                                    onChange { it.copy(presetId = preset.id, customSeedArgb = null) }
                                },
                            )
                        }
                    }

                    // 级联：只有 Monet 开着才有意义。收起时整块消失，
                    // 不留下"能点但没用"的灰控件。
                    item(key = "monetDetails") {
                        ColoredCascade(visible = theme.dynamicColor) {
                            SettingsGroup("Monet 细节") {
                                OverlayDropdownPreference(
                                    title = "调色板风格",
                                    items = ThemePaletteStyle.entries.map { it.name },
                                    selectedIndex = ThemePaletteStyle.entries.indexOf(
                                        ThemePaletteStyle.entries
                                            .firstOrNull { it.name == theme.paletteStyle }
                                            ?: ThemePaletteStyle.TonalSpot,
                                    ),
                                    onSelectedIndexChange = { index ->
                                        onChange { it.copy(paletteStyle = ThemePaletteStyle.entries[index].name) }
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = "颜色规范",
                                    items = ThemeColorSpec.entries.map { it.name },
                                    selectedIndex = ThemeColorSpec.entries.indexOf(
                                        ThemeColorSpec.entries
                                            .firstOrNull { it.name == theme.colorSpec }
                                            ?: ThemeColorSpec.Spec2021,
                                    ),
                                    onSelectedIndexChange = { index ->
                                        onChange { it.copy(colorSpec = ThemeColorSpec.entries[index].name) }
                                    },
                                )
                            }
                        }
                    }

                    item(key = "display") {
                        SettingsGroup("显示") {
                            val shaderSupported = isRuntimeShaderSupported()
                            SettingsToggle(
                                title = "模糊",
                                checked = theme.blurEnabled,
                                onCheckedChange = { enabled -> onChange { it.copy(blurEnabled = enabled) } },
                                summary = if (shaderSupported) {
                                    "启用顶栏、底栏与侧栏的背景虚化"
                                } else {
                                    "本机不支持（需要 Android 13 以上的 RuntimeShader），此项不生效"
                                },
                                // 不支持时置灰而不是隐藏：让"为什么没有虚化"在界面上有答案。
                                enabled = shaderSupported,
                                icon = ZhiIcons.layers,
                                plate = SettingsPlateColors.purple,
                            )
                            SettingsToggle(
                                title = "悬浮底栏",
                                checked = theme.floatingBar,
                                onCheckedChange = { enabled -> onChange { it.copy(floatingBar = enabled) } },
                                summary = "输入器与任务卡浮在内容之上；关闭后贴满宽度、去圆角",
                                icon = ZhiIcons.chat,
                                plate = SettingsPlateColors.blue,
                            )
                            // 级联：「液态玻璃」只在能虚化时才有意义
                            // （没有虚化时"更透"只会露出下层内容而看不清）。
                            ColoredCascade(visible = theme.floatingBar && shaderSupported) {
                                SettingsToggle(
                                    title = "液态玻璃",
                                    checked = theme.liquidGlass,
                                    onCheckedChange = { enabled -> onChange { it.copy(liquidGlass = enabled) } },
                                    summary = "悬浮层更透、带边缘高光；关闭后是不透明的实心面板",
                                    enabled = theme.blurEnabled,
                                    icon = ZhiIcons.layers,
                                    plate = SettingsPlateColors.green,
                                )
                            }
                        }
                    }

                    item(key = "gesture") {
                        SettingsGroup("手势") {
                            SettingsToggle(
                                title = "边缘滑动返回",
                                checked = theme.edgeSwipeBack,
                                onCheckedChange = { enabled -> onChange { it.copy(edgeSwipeBack = enabled) } },
                                summary = "从屏幕边缘向内滑动返回上一页；方向跟随布局方向",
                                icon = ZhiIcons.back,
                                plate = SettingsPlateColors.gray,
                            )
                        }
                    }

                    item(key = "scale") {
                        SettingsGroup("缩放") {
                            SettingsSliderRow(
                                title = "界面缩放",
                                summary = "调整全局显示比例；只影响界面，不改系统设置",
                                value = theme.uiScale,
                                valueText = formatUiScale(theme.uiScale),
                                onValueChange = { scale -> onChange { it.copy(uiScale = scale) } },
                                valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                                // 关键点用真实取值（85% / 100% / 115%），拖到附近会吸附 ——
                                // 用户想回到 100% 不必靠手感对准。
                                showKeyPoints = true,
                                keyPoints = listOf(UI_SCALE_MIN, 1.0f, UI_SCALE_MAX),
                                magnetThreshold = 0.01f,
                            )
                        }
                    }

                    item(key = "footnote") {
                        Text(
                            text = "改动立刻生效并落盘，不需要保存。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            // 操作反馈。原先由 `SettingsSubPage` 提供，这一页改用自有 Scaffold 之后
            // 必须自己补 —— 少了它，本页任何失败都不会在屏幕上出现。
            PageFeedbackSlot(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 级联收起/展开。
 *
 * 单独封成一层而不是直接调 `AnimatedVisibility`：本文件在 `LazyListScope.item {}`
 * 与 `Card` 的 `ColumnScope` 两种接收者里都要用它，而
 * `ColumnScope.AnimatedVisibility` 那个重载在"另一个 Column 的隐式接收者"处
 * **不可调用**（编译期报 "cannot be called in this context with an implicit receiver"）。
 * 把它收进一个不接收任何 receiver 的函数，两处调用就都不再有歧义。
 */
@Composable
private fun ColoredCascade(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column { content() }
    }
}

/**
 * 迷你手机预览。
 *
 * 尺寸与构图照 `ksu-style` 的 `ThemePreviewCardMiuix`：宽 40%、按屏幕比例定高、
 * 20dp 圆角 + 1dp 描边；内部固定三段（强调色大块 / 两个容器块）+ 顶栏条 + 底栏。
 *
 * 与 ksu-style 的**唯一差别**：它用 `rememberDynamicColorScheme` 自己算色板
 * （因为它要同时预览 Material 与 Miuix 两套），这里直接用 `MiuixTheme.colorScheme` ——
 * 那已经是当前主题真实生效的色板，不存在"预览与界面不一致"的可能。
 */
@Composable
private fun ThemePreviewPhone(
    theme: ThemeSettings,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val configuration = LocalConfiguration.current
    val screenRatio = if (configuration.screenHeightDp > 0) {
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    } else {
        0.46f
    }
    val preset = remember(theme.presetId) { presetById(theme.presetId) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio.coerceIn(0.3f, 1f))
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.background)
                .border(1.dp, scheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶栏条：只画一条色带，表达"顶栏底色与背板是否分得开"。
                Row(
                    modifier = Modifier.height(28.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ZhiCode",
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        color = scheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PreviewBlock(color = scheme.primary, height = 52.dp)
                    PreviewBlock(color = scheme.secondaryContainer, height = 22.dp)
                    PreviewBlock(color = scheme.surfaceContainerHigh, height = 26.dp)
                }

                // 底栏：悬浮开/关是两种完全不同的形态，各画一次才能说明这个开关。
                if (theme.floatingBar) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 6.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceContainer.copy(alpha = if (theme.liquidGlass) 0.5f else 1f))
                            .border(0.5.dp, scheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(scheme.primary))
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(scheme.onSurface))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(scheme.surfaceContainer)
                            .padding(top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RepeatMarkers(selected = scheme.primary, unselected = scheme.onSurfaceContainer.copy(alpha = 0.5f))
                    }
                }
            }
        }

        Text(
            text = preset.name,
            color = scheme.onSurface,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = if (theme.dynamicColor) {
                "Monet · ${theme.paletteStyle}"
            } else {
                "强调色取自预设 · 中性色固定"
            },
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RepeatMarkers(selected: Color, unselected: Color) {
    repeat(2) { index ->
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (index == 0) selected else unselected),
        )
    }
}

@Composable
private fun PreviewBlock(color: Color, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/**
 * 预设色卡：**卡内的一行自定义控件**。
 *
 * 为什么不用 `OverlayDropdownPreference`：那一行只显示当前项的名字，而这一项的语义是
 * "这些颜色长什么样" —— 名字（"暖阳橙"）远不如图块本身有用。
 *
 * 为什么手搓 `Box` 而不是 `RadioButton`／`Chip`：Miuix 的单选控件画的是一个勾，
 * 看不出色差。
 *
 * ⚠️ 这一块必须在 `SettingsGroup` 的 `Card` **里面**（调用点就在那）：卡片自带圆角、
 * 卡片底与左右 12dp 边距，自己排会与上下的行对不齐。
 */
@Composable
private fun PresetRow(
    selectedId: String,
    onSelect: (ThemePreset) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "预设主题",
            color = scheme.onSurface,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // 用固定行分组而不是 LazyVerticalGrid：只有 7 项，网格的滚动与测量开销不值得，
        // 而且在设置页的 LazyColumn 里嵌一个可滚动网格会让滑动打架。
        THEME_PRESETS.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { preset ->
                    PresetChip(
                        preset = preset,
                        selected = preset.id == selectedId,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 补齐最后一行的空位，让 4 列的宽度与前面几行一致。
                repeat(4 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val color = Color(preset.seedArgb)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = scheme.onSurface,
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickablePreset(onClick),
        )
        Text(
            text = preset.name,
            color = if (selected) scheme.primary else scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 色卡上的点击。
 *
 * 单独抽出来只是为了不在调用处写一长串 `Modifier.clickable(...)`：
 * 这里**必须**有 `indication = null` 且不带水波纹 —— 色块本身是实色，
 * 涟漪会被颜色吃掉，看起来像"点下去闪了一下但什么都没发生"。
 */
@Composable
private fun Modifier.clickablePreset(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
)
