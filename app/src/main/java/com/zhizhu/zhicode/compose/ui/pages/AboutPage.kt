package com.zhizhu.zhicode.compose.ui.pages

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

/**
 * 关于页。
 *
 * **逐字移植自 `ksu-style` 的 `ui/screen/about/AboutMiuix.kt`。**
 * 整页 OS3 渐变底 + 随滚动淡出的视差 Logo + `DstIn` 渐变遮罩大字 +
 * `textureBlur` 磨砂玻璃卡片。唯一改动是卡片内容 —— 那边是一串外链，
 * 这里换成 ZhiCode 自己的信息行。
 *
 * ## 抄的时候不能省的三个点
 *
 * 1. **`backdrop` 只挂在 `bgModifier` 上**（背景层）。采样它的文字在同一棵
 *    `BgEffectBackground` 的 content 里，二者互为兄弟。⚠️ 曾经把
 *    `layerBackdrop` 挂到包住文字的父节点上 —— 背板包含它自己，
 *    `libhwui` 的 `RenderNode::prepareTreeImpl` 递归三百多层后**原生崩溃**。
 * 2. **文字用 `DstIn`**：字形当遮罩，采到的是被模糊并混合过的渐变。
 *    混合色 `logoBlend`（`ColorDodge` + `LinearLight` + `Lab`）负责把它提亮，
 *    否则深色渐变上的字会跟着变深、几乎隐形。
 * 3. **卡片用 `textureBlur` 但不带 `DstIn`**：那是磨砂玻璃，不是遮罩；
 *    卡片底色同时给 `Color.Transparent`，否则实色会把模糊结果盖死。
 */
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
    val blurActive = barBlurBackdrop != null && scrollProgress == 1f
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        if (scrollProgress == 1f) colorScheme.surface else Color.Transparent
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

    /**
     * Hero 那块透明点击层的交互源。
     *
     * 只为传 `indication = null`（不要涟漪）—— 涟漪会让整片 Hero 看起来像按钮，
     * 而那里的语义是"点背景上的名字可以进检查更新"。见 `logoSpacer` 的注释。
     */
    val brandInteraction = remember { MutableInteractionSource() }

    val isInDark = LocalZhiDark.current
    val effectBackground = remember(enableBlur) {
        isRuntimeShaderSupported() && enableBlur
    }

    // ⚠️ 必须按明暗分开取（KSU 原文就是这样，之前漏了 `isInDark` 分支，
    // 深色主题下卡片变成一块灰白板、把渐变整个盖掉）。
    // `Pured_Regular_Light` 里有一条 `Color(0xB3FFFFFF) HardLight` 的强白混合，
    // 它只适合浅色底；深色底要用 `Overlay_Thin_Light`（Luminosity + PlusDarker）。
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
    //
    // 原来是三段（图标 → 名字 → 版本号），图标删掉之后（用户要求去掉那张矢量图）
    // 只剩两段。`iconY` / `iconProgress` 一并删掉 —— 留着它们的话，下面
    // 「锚点都量到才计算」的守卫会因为 `iconY` 永远为 0 而永不通过，
    // 表现就是视差彻底失效、名字一直不淡出。
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    /*
     * 进页面时把列表拉回顶部。
     *
     * 这是用户要的「退出关于页就重置关于页状态」：`NavDisplay` 的 saveable 状态
     * 会记住上次的滚动位置，于是二次进入时列表停在半中间、Logo 区不见了。
     * **只重置这一页**的滚动，设置页的状态一点不动（那是另一个 entry）。
     */
    LaunchedEffect(Unit) {
        lazyListState.scrollToItem(0)
    }

    LaunchedEffect(lazyListState) {
        /*
         * 观察的**不只是** offset：三个锚点是 `onGloballyPositioned` 陆续量到的，
         * 只盯 offset 的话，锚点到位那一刻不会触发重算，进度就停在初始值上。
         * 把五个值打包成一个快照一起观察，任一变化都重算。
         */
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

                /*
                 * 🔴 三个锚点都量到之前**不能**算进度。
                 *
                 * 它们来自 `onGloballyPositioned`，首帧还是 0；而恢复的滚动位置可能
                 * 已经是大值。此时 stage 长度全为 0，`offset / 1f` 直接钳到 1，
                 * `alpha = 1 - 1 = 0` —— 表现就是「进关于页，Hero 一片空白，
                 * 只剩下面的卡片」。这正是用户报的那个偶现。
                 */
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
            // 图标已去掉（用户要求删掉那张矢量图）：只剩名字与版本号，
            // 两者都居中 —— 名字原来没写 textAlign，单行时齐左，与下面的
            // 版本号对不齐，看起来像错位。
            //
            // ⚠️ 这里**故意没有** `clickable`。
            //
            // 「点这个字进检查更新页」的点击目标在下面 `LazyColumn` 的
            // `logoSpacer` 上，不在这里。原因是层级，不是写法：
            // `BgEffectBackground` 的 content 里，这个 `Column` 与 `LazyColumn`
            // 是**兄弟**，而 `LazyColumn` 后组合 → 画在上面；Compose 的命中测试
            // 按 z 序反向走，`LazyColumn` 是滚动容器、自带 pointer input，
            // 指头落在它边界内就被它接住，命中链在那里终止，
            // **根本不会问到下面这个 `Column`**。
            //
            // 而 `LazyColumn` 是 fillMaxSize，它的第一项 `logoSpacer` 又刻意做成
            // 与 Hero 等高 —— 于是"ZhiCode 这几个字"所在的整块区域实际归属于
            // 那个透明占位项。曾经在这里挂过 `clickable`，实测点不动，
            // 正是因为它是**被遮挡的那个节点**。
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
                // 原来这里是写死的 35.sp —— 那比 Miuix 最大的槽位还大 3sp，
                // 是整套字阶之外的例外：字阶统一调整时它必然被漏掉，
                // 而"官方组件 + 自己的字"正是本工程偏离 Miuix 规范的原因。
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
            // 与 Logo 等高的透明占位：让内容从 Logo 下方开始，同时给视差留长度。
            //
            // ⚠️ 它同时是「点 Hero 进检查更新页」的**点击目标**，这不是巧合：
            // 它本来就覆盖着上面那一整块 Hero（名字 + 版本号），而且它在
            // `LazyColumn` 里、位于上面那个 `Column` **之上**，所以只有它
            // 能真正接到触摸（原因见上面 `Text` 那段注释）。
            //
            // 把点击挂在这里而不是给 `Text` 挂 `clickable`，是为了修掉
            // "点了没反应"：位置不变、观感不变，只是换到了命中链真正经过的节点上。
            //
            // 无涟漪（`indication = null`）：这一块是背景，出涟漪会让人以为
            // 整片区域是一个按钮。语义上仍是个可点目标，所以补了
            // `contentDescription` —— 否则读屏用户完全不知道这里有入口。
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
                        // ⚠️ 不能省（KSU 原文就有）。它让这一项至少和视口一样高，
                        // 于是列表内容总高 = logoSpacer + 一屏，滚动范围才够。
                        // 少了它，内容不足一屏时最后一张卡片会被裁掉、滑不到底。
                        .fillParentMaxHeight()
                        .padding(
                            bottom = innerPadding.calculateBottomPadding() + 12.dp,
                        ),
                ) {
                    AboutGlassCard(enableBlur, backdrop, blendColors) {
                        SettingsReadOnly(title = "版本", valueText = BuildConfig.VERSION_NAME)
                        /*
                         * 「有新版本 x.y.z」只在**真检测到**新版本时出现。
                         *
                         * 三个状态要分清：
                         *  · `Available` → 显示这一行（用户能马上看到有新版并点进去）；
                         *  · `UpToDate` / `NoReleases` / `Idle` → **不显示**。
                         *    显示一个"已是最新"的行会让这张卡多一条永远不变的信息，
                         *    而那一句更适合放在更新页里（那里有完整语境）。
                         *    至于"还没查过"（Idle）更不能显示 —— 那时我们并不知道。
                         *
                         * 检查在 `openAbout()` 里触发（不是进了这一页才发请求），
                         * 所以这里读到的是已经落地的结果。
                         */
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

/**
 * 磨砂玻璃卡片 —— 照 KSU 的写法：
 * `textureBlur(shape = 16dp, blurRadius = 60f)`，**不带** `DstIn`（那会变成遮罩），
 * 底色同时给 [Color.Transparent]，否则实色会把模糊结果盖死、肉眼等于没有模糊。
 */
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
                        // `CardDefaults.CornerRadius`（16dp）。写死一个数迟早会和
                        // 卡体对不上，那圈模糊就会从圆角里漏出来。
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
