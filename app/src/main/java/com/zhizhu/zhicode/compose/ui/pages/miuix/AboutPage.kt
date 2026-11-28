package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.model.DONATE_URL
import com.zhizhu.zhicode.compose.model.OWN_LICENSE
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.effects.BgEffectBackground
import com.zhizhu.zhicode.compose.ui.effects.ColorBlendToken
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsFootnote
import com.zhizhu.zhicode.compose.ui.settings.SettingsReadOnly
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
fun AboutPage(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenUpdate: () -> Unit,
    update: UpdateUiState,
) {
    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    // 顶栏标题与底色的淡入依赖"滚过 logo 高度"这个比例。
    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val enableBlur = LocalThemeSettings.current.blurEnabled
    val barBlurBackdrop = rememberPageBlurBackdrop()
    val barAlpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val blurActive = barBlurBackdrop != null && barAlpha > 0f
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        colorScheme.surface.copy(alpha = barAlpha)
    }

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = barBlurBackdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = "关于",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = colorScheme.onSurface.copy(
                        alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    ),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = ZhiIcons.back,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
    ) { innerPadding ->
        Box(modifier = if (barBlurBackdrop != null) Modifier.layerBackdrop(barBlurBackdrop) else Modifier) {
            AboutContent(
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = scrollProgress,
                enableBlur = enableBlur,
                onLogoHeightChanged = { logoHeightPx = it },
                openUri = openUri,
                onOpenLicenses = onOpenLicenses,
                onOpenPrivacy = onOpenPrivacy,
                onOpenUpdate = onOpenUpdate,
                update = update,
            )
        }
    }
}

@Composable
private fun AboutContent(
    innerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    enableBlur: Boolean,
    onLogoHeightChanged: (Int) -> Unit,
    openUri: (String) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenUpdate: () -> Unit,
    update: UpdateUiState,
) {
    val density = LocalDensity.current

    val backdrop = rememberLayerBackdrop()

    /** Hero 那块透明点击层的交互源。 */
    val brandInteraction = remember { MutableInteractionSource() }

    val isInDark = LocalZhiDark.current
    val effectBackground = remember(enableBlur) {
        isRuntimeShaderSupported() && enableBlur
    }

    val blendColors = remember(isInDark) {
        if (isInDark) ColorBlendToken.Overlay_Thin_Light
        else ColorBlendToken.Pured_Regular_Light
    }
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    // Hero 视差：滚动时**版本号先淡出，名字再淡出**（两段）。
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    /* 进页面时把列表拉回顶部。 */
    LaunchedEffect(Unit) {
        lazyListState.scrollToItem(0)
    }

    LaunchedEffect(lazyListState) {
        /* 观察的**不只是** offset：三个锚点是 `onGloballyPositioned` 陆续量到的 */
        snapshotFlow {
            listOf(
                lazyListState.firstVisibleItemIndex.toFloat(),
                lazyListState.firstVisibleItemScrollOffset.toFloat(),
                projectNameY,
                versionCodeY,
                logoAreaY,
            )
        }
            .collect { snapshot ->
                val index = snapshot[0].toInt()
                val offset = snapshot[1].toInt()
                val nameYNow = snapshot[2]
                val versionYNow = snapshot[3]
                val logoAreaYNow = snapshot[4]

                /* 🔴 三个锚点都量到之前**不能**算进度。 */
                if (nameYNow <= 0f || versionYNow <= 0f || logoAreaYNow <= 0f) {
                    if (projectNameProgress != 0f) projectNameProgress = 0f
                    if (versionCodeProgress != 0f) versionCodeProgress = 0f
                    return@collect
                }

                if (index > 0) {
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (versionCodeProgress != 1f) versionCodeProgress = 1f
                    return@collect
                }

                if (initialLogoAreaY == 0f && logoAreaYNow > 0f) {
                    initialLogoAreaY = logoAreaYNow
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaYNow

                // 两段：先版本号，再名字。`stage1` 是 Hero 底边到版本号，
                // `stage2` 是版本号到名字 —— 名字在上面，所以它后淡出。
                val stage1TotalLength = refLogoAreaY - versionYNow
                val stage2TotalLength = versionYNow - nameYNow

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress =
                    ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                projectNameProgress =
                    ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
            }
    }

    val scrollPadding = PaddingValues(top = innerPadding.calculateTopPadding())
    val logoPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 40.dp)

    BgEffectBackground(
        dynamicBackground = effectBackground,
        modifier = Modifier.fillMaxSize(),
        // 🔴 背板只登记背景层。挂到包住文字的父节点上会让背板包含自己 → 原生爆栈。
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress },
    ) {
        // ---- Logo 区（视差淡出） ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = logoPadding.calculateTopPadding() + 52.dp)
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY != 0f) return@onGloballyPositioned
                        projectNameY = coordinates.positionInWindow().y + coordinates.size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (enableBlur) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(0.dp),
                                blurRadius = 150f,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = ComposeBlendMode.DstIn,
                                enabled = true,
                            )
                        } else {
                            Modifier
                        },
                    ),
                text = "ZhiCode",
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                // 字号走字阶里最大的一档（`title1` = 32sp），不硬写 sp。
                fontSize = MiuixTheme.textStyles.title1.fontSize,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        versionCodeY = coordinates.positionInWindow().y + coordinates.size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = BuildConfig.VERSION_NAME,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                textAlign = TextAlign.Center,
            )
        }

        // ---- 可滚动内容 ----
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = scrollPadding,
            overscrollEffect = null,
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        )
                        .clickable(
                            interactionSource = brandInteraction,
                            indication = null,
                            onClick = onOpenUpdate,
                        )
                        .semantics { contentDescription = "检查更新" }
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) }
                        .onGloballyPositioned { coordinates ->
                            logoAreaY = coordinates.positionInWindow().y + coordinates.size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "groups") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(
                            bottom = innerPadding.calculateBottomPadding() + 12.dp,
                        ),
                ) {
                    AboutGlassCard(enableBlur, backdrop, blendColors) {
                        SettingsReadOnly(title = "版本", valueText = BuildConfig.VERSION_NAME)
                        /* 「有新版本 x.y.z」只在**真检测到**新版本时出现。 */
                        val newer = update.release
                        if (update.phase == UpdatePhase.Available && newer != null) {
                            SettingsEntry(
                                title = "有新版本 ${newer.tag}",
                                valueText = "",
                                summary = "点此查看更新内容并下载",
                                onClick = onOpenUpdate,
                            )
                        }
                        SettingsEntry(
                            title = "GitHub 开源页面",
                            valueText = "",
                            summary = "发行、问题与源码",
                            onClick = { openUri(PROJECT_URL) },
                        )
                        SettingsEntry(
                            title = "赞助",
                            valueText = "",
                            summary = "爱发电",
                            onClick = { openUri(DONATE_URL) },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    AboutGlassCard(enableBlur, backdrop, blendColors) {
                        SettingsReadOnly(title = "开发者", valueText = "zhizhu0002")
                        SettingsReadOnly(title = "开源许可证", valueText = "$OWN_LICENSE License")
                        SettingsEntry(
                            title = "第三方开源项目",
                            valueText = "",
                            summary = "站在这些项目的肩膀上",
                            onClick = onOpenLicenses,
                        )
                        SettingsEntry(
                            title = "隐私政策",
                            valueText = "",
                            summary = "本应用会碰什么、不碰什么",
                            onClick = onOpenPrivacy,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    AboutGlassCard(enableBlur, backdrop, blendColors) {
                        SettingsEntry(
                            title = "许可证全文",
                            valueText = "",
                            summary = "在浏览器打开 LICENSE",
                            onClick = { openUri("$PROJECT_URL/blob/main/LICENSE") },
                        )
                        SettingsFootnote(
                            text = "ZhiCode 基于开源软件构建。各依赖与随应用分发组件继续适用" +
                                "各自的许可证；完整归属、修改说明与许可文本请查看第三方声明。",
                        )
                    }

                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                        ),
                    )
                }
            }
        }
    }
}

/** 磨砂玻璃卡片 —— 照 KSU 的写法 */
@Composable
private fun AboutGlassCard(
    enableBlur: Boolean,
    backdrop: LayerBackdrop,
    blendColors: List<BlendColorEntry>,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .then(
                if (enableBlur) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        // 与卡体**同源**：`Card` 不传 `cornerRadius` 时用的就是
                        shape = RoundedCornerShape(CardDefaults.CornerRadius),
                        blurRadius = 60f,
                        colors = BlurColors(blendColors = blendColors),
                        enabled = true,
                    )
                } else {
                    Modifier
                },
            ),
        // 显式写出来而不是靠默认值：这一页是「16dp」这个基准的出处，
        // 别的页面（第三方开源项目 / 隐私政策）都对齐它。
        cornerRadius = CardDefaults.CornerRadius,
        colors = CardDefaults.defaultColors(
            if (enableBlur) Color.Transparent else colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        Column { content() }
    }
}
