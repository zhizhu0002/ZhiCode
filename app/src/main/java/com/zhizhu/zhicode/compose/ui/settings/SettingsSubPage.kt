package com.zhizhu.zhicode.compose.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 二级页内部的一页。
 *
 * [id] 是跨导航保留状态的键（见 [SettingsPageStack]），[depth] 用来判断前进还是后退。
 * 用数据类而不是让每个页面各定义一个 sealed 层级：页面之间没有共享字段，
 * 各自定义只会让五个调用点各写一遍样板。
 */
internal data class SettingsPageKey(val id: String, val depth: Int)

/** 被覆盖页的最终透明度：与 `NavTransitions.MiuixDefault` 的 `1 - 0.1` 一致。 */
private const val CoveredPageAlpha = 0.9f

/** 被覆盖页的视差比例：与 `NavTransitions.MiuixDefault` 的 1/4 宽一致。 */
private const val CoveredPageParallax = 4

/**
 * 二级页内部的**页面栈动效**。
 *
 * ## 为什么需要它
 *
 * 二级页（API 配置 / MCP / 技能 / 角色卡 / 记忆）各自有好几态：列表 → 新建表单 →
 * 编辑器。这些态以前是调用方一个 `when {}` 里的内容**硬切** —— 而
 * `AppScaffold` 的 `desiredStack` 只反映「哪一页开着」（`state.skills != null`），
 * 栈深度不变，`NavDisplay` 就无事可做，于是**一点动画都没有**。
 * 用户报的「三级窗口动画非常不完整」就是这个。
 *
 * ## 动效照抄 `NavTransitions.MiuixDefault`
 *
 * 二级页之间的转场如果不跟整页转场同一套手感，连着用就会看出"两套节奏"。所以这里
 * 逐条对齐它：
 *
 * - 前进：新页全宽从**右缘**滑入；被覆盖页向左视差 **1/4 宽**、透明度降到 **0.9**。
 * - 后退：反向。
 * - 曲线用 [ZhiMotion] 里已有的令牌（进入位移 / 退出位移 / 淡入淡出），不新造曲线。
 *
 * ## 两个不能省的细节
 *
 * 1. **`rememberSaveableStateHolder` 必须在 `AnimatedContent` 外面**。
 *    `AnimatedContent` 会销毁离场页，`rememberLazyListState()` 随之丢失 ——
 *    表现就是「从详情返回列表，列表回到最顶上」。这个坑本仓库在
 *    `WorkspaceLayouts` 的工作区面板上已经踩过一次（那里的修法也是 `SaveableStateProvider`）。
 * 2. **`BackHandler` 让系统返回先退回上一层**。以前这里没有它，三级页按系统返回会
 *    直接**关掉整个二级页**（`AppScaffold` 的 `onBack` 只认「哪一页开着」）。
 *    只在 `depth > 0` 时启用，最外层那一页的返回仍交给 `NavDisplay`（关整页）。
 */
@Composable
internal fun SettingsPageStack(
    current: SettingsPageKey,
    onBack: () -> Unit,
    content: @Composable (SettingsPageKey) -> Unit,
) {
    // 必须在 AnimatedContent **外面**：它才是跨页保留状态的容器。
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = current.depth > 0, onBack = onBack)

    AnimatedContent(
        targetState = current,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                // 前进：新页从右缘滑入，旧页向左视差并微微变暗。
                slideInHorizontally(animationSpec = ZhiMotion.enterSpec) { width -> width } togetherWith
                    (
                        slideOutHorizontally(animationSpec = ZhiMotion.exitSpec) { width ->
                            -width / CoveredPageParallax
                        } + fadeOut(
                            animationSpec = ZhiMotion.fadeOutSpec,
                            targetAlpha = CoveredPageAlpha,
                        )
                    )
            } else {
                // 后退：反向 —— 旧页向右滑出，新页从左侧视差位回到正中并恢复亮度。
                (
                    slideInHorizontally(animationSpec = ZhiMotion.exitSpec) { width ->
                        -width / CoveredPageParallax
                    } + fadeIn(
                        animationSpec = ZhiMotion.fadeInSpec,
                        initialAlpha = CoveredPageAlpha,
                    )
                ) togetherWith slideOutHorizontally(animationSpec = ZhiMotion.enterSpec) { width -> width }
            }
        },
        label = "settingsPageStack",
    ) { key ->
        stateHolder.SaveableStateProvider(key.id) { content(key) }
    }
}

/**
 * 记住「最后一次非空的页面数据」。
 *
 * ## 为什么必须有它
 *
 * `AnimatedContent` 的退出动画期间**离场页仍在绘制**，而那时外部的当前状态已经变空
 * （退回列表 → `form` 被置 null）。若离场页直接读当前状态，它要么渲染成空白、
 * 要么在 `!!` 上崩掉 —— 动画越"完整"，这个问题越容易暴露。
 *
 * `AppScaffold` 对每个二级页都用了同一个策略（`lastApiConfig` / `lastSkills` …），
 * 这里把那套写法收成一个原语，免得五个二级页各写一遍。
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf<T?>(null) }
    if (value != null) last = value
    return last
}

/**
 * 设置的二级整页（API 配置 / MCP / 技能 / 角色卡 / 记忆），与设置主页同款骨架：
 * Miuix 原生 Scaffold + SmallTopAppBar（返回键 = MiuixIcons.Back）+ LazyColumn 滚动。
 *
 * 以前这些页面是 OverlayDialog 弹窗壳（DialogShell + 底部按钮排），和整页设置
 * 并存时观感割裂 —— 弹窗宽度封顶、滚动高度受限、按钮位置和整页不一致。
 * 收敛到一个壳之后，二级页和主页只有标题与动作差异。
 *
 * [action]（如「新增 / 保存」）放顶栏 TextButton。设置主页的顶栏**没有**动作按钮
 * （那一屏是自动保存，见 SettingsDialog 顶部说明），但二级页的动作是真的要提交一次
 * 的操作（新增一条 API 配置、保存一张角色卡），所以仍留在右上角。
 *
 * 页面内若有多态，外面要套 [SettingsPageStack] —— 只写多个 `SettingsSubPage`
 * 分支的话它们会被硬切，没有转场动画。
 */
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    action: Pair<String, (() -> Unit)?>? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            // 与设置主页同款顶栏：Miuix TopAppBar 自带 largeTitle（大标题随滚动折叠），
            // 对应 rikkahub 二级页的 LargeFlexibleTopAppBar —— 主页与二级页的
            // 标题层级一致，返回时不会出现「标题突然变小」的断层。
            TopAppBar(
                title = title,
                scrollBehavior = topAppBarScrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = scheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (action != null && action.second != null) {
                        TextButton(text = action.first, onClick = action.second!!)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "subPageBody") { content() }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }
}
