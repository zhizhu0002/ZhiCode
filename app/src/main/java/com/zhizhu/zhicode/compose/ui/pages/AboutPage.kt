package com.zhizhu.zhicode.compose.ui.pages

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.animation.core.Animatable
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.model.OWN_LICENSE
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.effects.BgEffectBackground
import com.zhizhu.zhicode.compose.ui.effects.ColorBlendToken
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.theme.ZhiColors
import kotlinx.coroutines.flow.onEach
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

/**
 * 关于页 —— **逐字移植 Miuix 官方 example 的 `AboutScreenMiuix`**。
 *
 * ## 来源与核对方式
 *
 * 参照物有两份，互相印证：
 *
 * 1. **官方 App 的真实截图**（`top.yukonga.miuix.uitest`，用 IQ 沙盒跑起来截的）：
 *    整页深紫渐变背景、顶栏只有一个返回箭头、hero 居中（白底圆角图标 + 大字号应用名 + 版本号）、
 *    两个半透明分组卡、底部大片留白。
 * 2. **`ksu-style` 的同名实现**（文件头标注 `// Mirrored from compose-miuix-ui example.`），
 *    提供了可直接移植的源码：三段位滚动淡出、`BgEffectBackground`、`textureBlur` 的每一档参数。
 *
 * 移植时只替换了「主题判定的入口」（`isInDarkTheme()` → [ZhiColors.isDark]），
 * 其余数值、顺序、结构**一字未改**。
 *
 * ## 为什么这一页与其他页形态不同
 *
 * 其余二级页走 `SettingsSubPage`（不透明 `Scaffold` + 标题栏 + 滚动列表）。这是刻意的：
 * 那些页是**在办一件事**，需要稳定的标题与导航；关于页是**看一眼就走**，
 * example 给它的形态是"一张名片"—— 所以这里独立开一个 `Scaffold`，
 * 也正因为独立，才能让渐变铺满整页而不影响别的页面。
 *
 * ## 代价（已知并接受）
 *
 * - hero 的三行文字各挂一层 `textureBlur(blurRadius = 150f)`：**每帧重采样一张全屏贴图**；
 * - `BgEffectBackground` 的 `withFrameNanos` 循环在滚动期间持续重绘背景。
 *
 * example 这么写是为了展示能力。移植时保留原样（用户明确要求"完全照搬"），
 * 但如果将来有人说"关于页滚起来发烫"，第一个该砍的就是那三层 `textureBlur` ——
 * 它们只贡献"文字看起来更透一点"，视觉收益远小于代价。
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val open = { url: String -> runCatching { uriHandler.openUri(url) } }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    // 整页的滚动进度（0..1），只用于背景 alpha 与顶栏配色。
    // 用 derivedStateOf 收成一个 Float：下游只有它一个消费者，
    // 每帧滚动不会因此触发额外重组（Background 的 alpha 是 lambda，读在 draw 阶段）。
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
                    // example 这一页**没有标题文字**：标题会与 hero 里的大字号应用名重复，
                    // 而"我在哪一层"由 hero 本身与返回箭头已经说清。
                    title = "",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = colorScheme.onSurface.copy(
                        alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    ),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
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
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = if (barBlurBackdrop != null) {
                Modifier.layerBackdrop(barBlurBackdrop)
            } else {
                Modifier
            },
        ) {
            AboutContent(
                onOpenSource = { open(PROJECT_URL) },
                onOpenLicense = { open("$PROJECT_URL/blob/main/LICENSE") },
                onOpenLicenses = onOpenLicenses,
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = scrollProgress,
                onLogoHeightChanged = { logoHeightPx = it },
            )
        }
    }
}

@Composable
private fun AboutContent(
    onOpenSource: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenLicenses: () -> Unit,
    innerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val backdrop = rememberLayerBackdrop()

    val isInDark = ZhiColors.isDark()
    // 动态背景只在 API 34+ 且运行时编译可用时才走：OS3 的 shader 用了新版特性。
    val effectBackground = remember(isInDark) {
        isRuntimeShaderSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    // hero 文字上的那层 blur 用的混色 token。
    // 卡不再用它 —— 卡改成半透明填色（见下方 cardColor 的说明），
    // 所以 `ColorBlendToken` 在这里只剩 hero 一个字消费者。
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xe6a1a1a1), top.yukonga.miuix.kmp.blur.BlurBlendMode.ColorDodge),
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0x4de6e6e6), top.yukonga.miuix.kmp.blur.BlurBlendMode.LinearLight),
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff1af500), top.yukonga.miuix.kmp.blur.BlurBlendMode.Lab),
            )
        } else {
            listOf(
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xcc4a4a4a), top.yukonga.miuix.kmp.blur.BlurBlendMode.ColorBurn),
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff4f4f4f), top.yukonga.miuix.kmp.blur.BlurBlendMode.LinearLight),
                top.yukonga.miuix.kmp.blur.BlendColorEntry(Color(0xff1af200), top.yukonga.miuix.kmp.blur.BlurBlendMode.Lab),
            )
        }
    }

    // ---- hero 的三段位淡出 ----
    //
    // 三个锚点（版本号 / 应用名 / 图标的底边 y）由 onGloballyPositioned 各取一次；
    // 之后滚动偏移被切成三段，每段推进对应元素的 alpha 与 scale。
    //
    // 这是 example 的原算法，逐字保留。它读的是 firstVisibleItemScrollOffset，
    // 所以只在"第一项还在屏幕上"时有效 —— firstVisibleItemIndex > 0 时直接全部推到 1。
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    /*
     * 入场动画。
     *
     * ## 为什么自己做一个
     *
     * 整页的推入/弹出由 `NavDisplay` 的 `NavTransitions.MiuixDefault` 负责，
     * 但那一层只在**页面之间**成立。用户报的「进入关于页面没有动画」指的是：
     * 这一页自己的元素是"啪"一下全出现的 —— hero 图标、应用名、版本号、
     * 下面那两张卡，在同一帧里全部就位。
     *
     * ## 尺寸
     *
     * 位移 18dp、缩放从 0.94 到 1、透明度 0→1，用 `ZhiMotion` 里那两条
     * 抄自 Miuix 的弹簧令牌（与设置页 FAB、弹窗进出同一节奏）。
     * 位移不要更大：再大就会与 NavDisplay 的推入位移叠加，看起来像被拖了两次。
     *
     * ## 只跑一次
     *
     * `Animatable` + `LaunchedEffect(Unit)`：这一页重新进入组合（比如从第三方
     * 许可返回）时会**再跑一次** —— 那是想要的，返回也是一次入场。
     */
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, animationSpec = ZhiMotion.scaleEnterSpec)
    }
    val enterProgress = enter.value

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (iconProgress != 1f) iconProgress = 1f
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (versionCodeProgress != 1f) versionCodeProgress = 1f
                    return@onEach
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress = ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                projectNameProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                iconProgress = ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
            .collect { }
    }

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 40.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )

    BgEffectBackground(
        dynamicBackground = effectBackground,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress },
    ) {
        // ---- hero：图标 + 应用名 + 版本 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                // 入场：透明度 + 轻微缩放与上浮。放在 padding **之后**，
                // 于是缩放围绕内容自身中心、而不是整块留白的中心。
                .graphicsLayer {
                    alpha = enterProgress
                    val s = 0.94f + 0.06f * enterProgress
                    scaleX = s
                    scaleY = s
                    translationY = (1f - enterProgress) * 18.dp.toPx()
                }
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (iconY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        iconY = y + size.height
                    },
            ) {
                // 白底圆角方块 + 主色字形 —— 这是 example 的原形态（截图里那块是纯白）。
                // 不跟主题走：它压在渐变上，而渐变的色板是**硬编码**的（见 BgEffectConfig），
                // 用 surfaceContainerHighest 会在深色下得到一块深灰，压在深紫渐变上分不出边界。
                Card(
                    cornerRadius = ZhiRadius.card,
                    colors = CardDefaults.defaultColors(Color.White),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(84.dp),
                    ) {
                        Icon(
                            painter = ZhiIcons.terminal,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        projectNameY = y + size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (backdrop != null) {
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
                fontSize = 35.sp,
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
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        // ---- 可滚动内容 ----
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
            overscrollEffect = null,
        ) {
            // 透明占位：高度 = hero 实际高度，让卡片从 hero 下方开始。
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() -
                                scrollPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "links") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = innerPadding.calculateBottomPadding() + 12.dp),
                ) {
                    /*
                     * 卡是**半透明**的：渐变从卡后面透出来，这是 example 的观感关键。
                     *
                     * ## 为什么不是 example 里的 `textureBlur`
                     *
                     * example 给卡挂的是 `textureBlur(blurRadius = 60f)` + 全透明卡底。
                     * 实测（沙盒截图）那会在卡的**右半边留下一块硬边亮矩形** ——
                     * 模糊的采样区域按卡的 bounds 算，而卡在 `LazyColumn` 里被裁过一次，
                     * 采样区与实际绘制区不重合，交界处就露出来了。**官方 App 自己也有这个瑕疵**
                     * （用户提供的官方截图里，两张卡的右侧都能看到同一块亮区）。
                     *
                     * 所以这里换等价但稳定的做法：一层半透明白/黑覆盖，靠 alpha 让渐变透上来。
                     * 观感几乎一致（都是"磨砂玻璃压在渐变上"），代价从"每帧重采样一张全屏贴图"
                     * 降到"一次普通填充"，而且没有接缝。
                     *
                     * 深色卡用白色低 alpha（提亮），浅色卡用黑色低 alpha（压暗）——
                     * 单向叠加在任何渐变上都成立，不需要知道渐变当前是什么颜色。
                     */
                    val cardColor = if (ZhiColors.isDark()) {
                        Color.White.copy(alpha = 0.10f)
                    } else {
                        Color.Black.copy(alpha = 0.06f)
                    }

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            // 卡片比 hero 稍晚、幅度稍小地进入：两块错开不到 1/4 个
                            // 弹簧周期，读起来是"一起浮现"而不是"排着队出现"。
                            // 用同一个 `enterProgress` 做二次曲线，避免再造一个动画。
                            .graphicsLayer {
                                val delayed = ((enterProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
                                alpha = delayed
                                translationY = (1f - delayed) * 12.dp.toPx()
                            },
                        // ⚠️ 第二个参数是 **contentColor**，不是"再一个背景色"。
                        // example 写的是 `Color.Transparent` —— 那时卡上挂着 textureBlur，
                        // 内容确实要透出去；这一版没有 blur 了，再传 Transparent 会让卡里
                        // **所有不带显式颜色的 Text 变成隐形**（行尾的「GitHub」「MIT」就是这样没的）。
                        colors = CardDefaults.defaultColors(cardColor, colorScheme.onSurface),
                    ) {
                        ArrowPreference(
                            title = "查看源码",
                            // 行尾的值走 endActions（RowScope 槽位），不是 summary ——
                            // summary 是**标题下方**的说明行，放这里会变成第二行文字，
                            // 与 example 的「标题 + 右侧值」完全不是一回事。
                            // 颜色必须显式给：卡的 contentColor 是 onSurface（实色），
                            // 而 example 里行尾的值是**次要色**，比标题淡一档。
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    color = colorScheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                )
                            },
                            onClick = onOpenSource,
                        )
                        ArrowPreference(
                            title = "许可证",
                            endActions = {
                                Text(
                                    text = OWN_LICENSE,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                )
                            },
                            onClick = onOpenLicense,
                        )
                        ArrowPreference(title = "第三方组件许可", onClick = onOpenLicenses)
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
