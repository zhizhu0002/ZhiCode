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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.data.BottomBarStyle
import com.zhizhu.zhicode.compose.data.TabBarPosition
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
import com.zhizhu.zhicode.compose.ui.settings.SettingsChoice
import com.zhizhu.zhicode.compose.ui.settings.SettingsNumberDialog
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsSliderRow
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.Colors
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
    // 精确输入弹窗的开关。放在页级而不是行内：弹窗要盖住整页，
    // 而行在 LazyColumn 里、滚出屏幕就会被回收 —— 挂在那里弹窗会跟着消失。
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
                                // 这一项的边界说清：它只作用于**顶栏与底栏**，
                                // 不是"整页虚化"。之前文案写"顶栏、底栏与侧栏"，
                                // 用户会以为整个界面都会变模糊。
                                summary = if (shaderSupported) {
                                    "让顶栏与底栏的背景虚化；内容区不受影响"
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
                            // 级联：「液态玻璃」只在**有悬浮层**时才有所指。
                            //
                            // 光隐藏开关不够 —— 底层 `Glass.surfaceColor` 仍照原值算，
                            // 于是"开关看不见了，效果还在"。所以门控落在数据层
                            // （`ThemeSettings.liquidGlassEffective`），这里同时隐藏入口。
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

                    item(key = "tabBar") {
                        SettingsGroup("标签栏") {
                            // 位置本身用分段而不是开关：两个互斥选项用开关表达，
                            // 用户得先判断"打开代表哪种"，多一次推理。
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text(
                                    text = "位置",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                )
                                ZhiSegmentedTabs(
                                    tabs = TabBarPosition.entries.map { it.label },
                                    selectedIndex = TabBarPosition.entries.indexOf(theme.tabBarPositionValue),
                                    onSelect = { index ->
                                        onChange { it.copy(tabBarPosition = TabBarPosition.entries[index].name) }
                                    },
                                    modifier = Modifier.width(160.dp),
                                    matchWidth = true,
                                )
                            }
                            if (theme.tabBarPositionValue == TabBarPosition.BOTTOM) {
                                SettingsToggle(
                                    title = "悬浮标签栏",
                                    checked = theme.tabBarFloating,
                                    onCheckedChange = { enabled -> onChange { it.copy(tabBarFloating = enabled) } },
                                    summary = "标签栏浮在内容之上、左右留边；关闭后贴满宽度、去圆角",
                                    icon = ZhiIcons.chat,
                                    plate = SettingsPlateColors.blue,
                                )
                                // 底部悬浮栏的形态：只有在真悬浮时才有"形态"可言。
                                if (theme.tabBarFloating) {
                                    SettingsChoice(
                                        title = "悬浮栏样式",
                                        options = BottomBarStyle.entries.map { it.label },
                                        selectedIndex = BottomBarStyle.entries.indexOf(theme.bottomBarStyleValue),
                                        onSelect = { index ->
                                            onChange { it.copy(bottomBarStyle = BottomBarStyle.entries[index].name) }
                                        },
                                        summary = "默认是 Miuix 卡片；iOS 风格把输入器与发送键并进一条胶囊",
                                        icon = ZhiIcons.tune,
                                        plate = SettingsPlateColors.purple,
                                    )
                                }
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
                                // 拖动中的实时值由 valueTextFor 算；valueText 只是静止时的同一结果。
                                valueText = formatUiScale(theme.uiScale),
                                valueTextFor = { formatUiScale(it) },
                                // 拖动中不落盘：`uiScale` 改的是 LocalDensity（整棵树），
                                // 每帧落盘 = 每帧全局重测量 + 同步写盘，滑杆会追不上手指。
                                // 实时值由行内的 draft 显示，所以这里不需要做任何事。
                                onValueChange = { },
                                commitValue = { scale -> onChange { it.copy(uiScale = scale) } },
                                onValueClick = { scaleDialogOpen = true },
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

            }

            // 操作反馈。原先由 `SettingsSubPage` 提供，这一页改用自有 Scaffold 之后
            // 必须自己补 —— 少了它，本页任何失败都不会在屏幕上出现。
            PageFeedbackSlot(modifier = Modifier.fillMaxWidth())
        }

        // 精确输入。区间按百分比取整 —— 滑杆的最小步进肉眼分不出，
        // 精确到手就能表达"我要正好 100%"。
        SettingsNumberDialog(
            show = scaleDialogOpen,
            title = "界面缩放",
            initial = Math.round(theme.uiScale * 100f),
            range = Math.round(UI_SCALE_MIN * 100f)..Math.round(UI_SCALE_MAX * 100f),
            onConfirm = { percent ->
                scaleDialogOpen = false
                onChange { it.copy(uiScale = percent / 100f) }
            },
            onDismiss = { scaleDialogOpen = false },
            summary = "输入整数百分比。只影响界面显示比例，不改系统设置。",
        )
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
 * 迷你手机预览：画**主界面的三页**（对话 / 终端 / 文件），可点击切换。
 *
 * ## 为什么是这三页，而不是色块
 *
 * 一块色条只能说明"主色是什么"，说不出"卡片与背板分不分得开""对话气泡谁在左谁在右"
 * "终端在浅色模式下是不是还黑底""文件夹图标与文件名有没有层次"。这些恰恰是换主题后
 * 最容易出问题的地方。
 *
 * 早先这里画的是 ksu-style 的抽象构图（强调块 + 两个容器块 + 底栏两态），它能说明
 * "配色关系"，但用户看不出**这个应用长什么样**。改成主界面的三页之后，预览与真实界面
 * 一一对应：看到的就是打开应用会看到的。
 *
 * ## 颜色一律读当前色板
 *
 * 所有颜色取自 `MiuixTheme.colorScheme`（它已经是当前主题**真实生效**的色板），
 * 不留任何硬编码色 —— 否则预览会在某个色板下与真实界面不一致，而那种偏差
 * 恰恰是预览唯一需要避免的。
 *
 * ## 切换是本地状态
 *
 * 预览里点哪一页只影响这个预览，不写进 `ThemeSettings`：它是一张"看看效果"的示意图，
 * 不是导航状态。`rememberSaveable` 让旋转后停在原页。
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
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf("对话", "终端", "文件")

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(screenRatio.coerceIn(0.3f, 1f))
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.background)
                .border(1.dp, scheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶栏：应用名 + 右上角两个圆点（菜单 / 设置的位置示意）。
                Row(
                    modifier = Modifier.height(24.dp).fillMaxWidth().padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ZhiCode",
                        fontSize = 9.sp,
                        color = scheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(4.dp).clip(CircleShape).background(scheme.onSurfaceVariantSummary.copy(alpha = 0.5f)))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(scheme.primary))
                }

                // 标签栏：按用户选的位置摆在内容上方或下方 —— 预览要能证明这个开关生效。
                if (theme.tabBarPositionValue == TabBarPosition.TOP) {
                    PreviewTabRow(pages = pages, selected = page, onSelect = { page = it }, scheme = scheme)
                }

                // 内容：三页各自的真实形态。
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    when (page) {
                        0 -> PreviewChatPage(scheme)
                        1 -> PreviewTerminalPage(scheme)
                        else -> PreviewFilesPage(scheme)
                    }
                }

                if (theme.tabBarPositionValue == TabBarPosition.BOTTOM) {
                    PreviewTabRow(pages = pages, selected = page, onSelect = { page = it }, scheme = scheme)
                }

                // 底部输入条：悬浮 / 贴边两种形态，以及两种悬浮样式。
                PreviewComposerBar(theme = theme, scheme = scheme)
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

/** 预览里的迷你标签栏。样式对齐真实的 `ZhiSegmentedTabs`：选中项一块高亮胶囊。 */
@Composable
private fun PreviewTabRow(
    pages: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    scheme: Colors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        pages.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) scheme.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 7.sp,
                    maxLines = 1,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 预览·对话页：一条用户气泡（右）+ 一条助手气泡（左）。 */
@Composable
private fun PreviewChatPage(scheme: Colors) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 用户：右对齐、次要容器色 —— 与真实 MessageCards 的配色一致。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primaryContainer)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PreviewTextLine(scheme.onPrimaryContainer, widthFraction = 0.85f)
                PreviewTextLine(scheme.onPrimaryContainer, widthFraction = 0.5f)
            }
        }
        // 助手：左对齐、容器色。多一行"代码块"块，因为对话里最常出现的就是它。
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainer)
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PreviewTextLine(scheme.onSurface, widthFraction = 0.9f)
            PreviewTextLine(scheme.onSurface, widthFraction = 0.65f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    // 代码块用 `secondaryContainer`，而终端页用 `surfaceContainerHighest`。
                    // 两处必须错开：都取同一档容器色时，切到终端页会觉得"画面没变"。
                    .background(scheme.secondaryContainer)
                    .padding(3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(scheme.primary.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

/** 预览·终端页：等宽提示符 + 一行输出 + 光标。终端在浅色模式下也应是深底，这里如实画。 */
@Composable
private fun PreviewTerminalPage(scheme: Colors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$", fontSize = 7.sp, color = scheme.primary, fontFamily = FontFamily.Monospace)
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .fillMaxWidth(0.55f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(scheme.onSurface.copy(alpha = 0.55f)),
            )
        }
        Box(
            Modifier
                .fillMaxWidth(0.75f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(scheme.onSurfaceVariantSummary.copy(alpha = 0.4f)),
        )
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(scheme.onSurfaceVariantSummary.copy(alpha = 0.4f)),
        )
        // 光标块：真实终端里它是主色，这里照做。
        Box(Modifier.width(4.dp).height(7.dp).background(scheme.primary.copy(alpha = 0.8f)))
    }
}

/** 预览·文件页：三行"文件夹图标 + 名称 + 元信息"。 */
@Composable
private fun PreviewFilesPage(scheme: Colors) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(0.78f, 0.55f, 0.66f).forEach { width ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.primary.copy(alpha = 0.85f)),
                )
                Column(
                    modifier = Modifier.padding(start = 3.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(width)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.onSurface.copy(alpha = 0.75f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(width * 0.5f)
                            .height(1.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.onSurfaceVariantSummary.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

/** 预览里的一行"文字"：用一条细色带表示，尺寸随预览缩放。 */
@Composable
private fun PreviewTextLine(color: Color, widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(color.copy(alpha = 0.6f)),
    )
}

/**
 * 预览·底部输入条。
 *
 * 三种形态各画一次，否则用户改开关时看不出区别：
 * - 不悬浮：满宽、方角、贴底；
 * - 悬浮 + 默认：一枚圆角卡片，左右留边；
 * - 悬浮 + iOS：单条胶囊，输入区与发送键在同一壳内。
 */
@Composable
private fun PreviewComposerBar(theme: ThemeSettings, scheme: Colors) {
    val translucent = if (theme.liquidGlassEffective) 0.55f else 1f
    if (!theme.floatingBar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.surfaceContainerHighest),
            )
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
        return
    }
    val ios = theme.bottomBarStyleValue == BottomBarStyle.IOS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(if (ios) CircleShape else RoundedCornerShape(7.dp))
                .background(scheme.surfaceContainer.copy(alpha = translucent))
                .border(
                    width = 0.5.dp,
                    color = scheme.onSurface.copy(alpha = if (theme.liquidGlassEffective) 0.16f else 0.06f),
                    shape = if (ios) CircleShape else RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!ios) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(scheme.onSurfaceVariantSummary.copy(alpha = 0.5f)))
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(scheme.onSurface.copy(alpha = 0.22f)),
                )
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(scheme.onSurface.copy(alpha = 0.22f)),
                )
            }
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
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
