package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.PageFeedbackSlot
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 二级页内部的一页。 */
internal data class SettingsPageKey(val id: String, val depth: Int) : NavKey

@Composable
internal fun SettingsPageStack(
    path: List<SettingsPageKey>,
    onBack: () -> Unit,
    content: @Composable (SettingsPageKey) -> Unit,
) {
    val expected = path.distinctBy { it.id }
    require(expected.isNotEmpty()) { "SettingsPageStack 的路径不能为空" }

    val nav = remember { NavController(navBackStackOf(expected.first())) }

    LaunchedEffect(expected) {
        val stack = nav.backStack
        while (stack.size > expected.size) stack.removeAt(stack.lastIndex)
        for (i in stack.size until expected.size) stack.add(expected[i])
        // 同深度换页（比如从一个二级页直接跳到另一个同级页）：替换栈顶。
        if (stack.isNotEmpty() && stack.last() != expected.last()) {
            stack[stack.lastIndex] = expected.last()
        }
    }

    // 与外层同样的正交效果层（跟随屏幕圆角的裁剪 + 调暗 + backdrop），
    // 这样内层页之间的转场和外层整页转场看起来是同一件事。
    val navCornerRadius = rememberNavSystemCornerRadius()
    val navBackdrop = ZhiColors.backdrop()
    val effects = remember(navCornerRadius, navBackdrop) {
        NavDisplayEffects(
            cornerClipRadius = navCornerRadius,
            dimAmount = 0.5f,
            backdropColor = navBackdrop,
        )
    }

    // 边缘滑动返回：方向随布局方向，写法与外层 AppScaffold 一致（见顶部注释）。
    val swipeBack = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }

    NavDisplay(
        navController = nav,
        modifier = Modifier.fillMaxSize(),
        transition = NavTransitions.MiuixDefault,
        effects = effects,
        onBack = {
            // 只有真的还有上一层时才拦；最外层那一页的返回交给外层 NavDisplay（关整页）。
            if (nav.backStack.size > 1) onBack() else nav.pop()
        },
    ) {
        // 一个 entry 就覆盖全部页面：DSL 是**按 key 的类型**注册的，
        // key 实例本身通过 content 的参数传进来（所以 `when (key.id)` 在调用方那边）。
        entry<SettingsPageKey>(swipeDismiss = swipeBack) { key -> content(key) }
    }
}

@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf<T?>(null) }
    if (value != null) last = value
    return last
}

/** 设置的二级整页（API 配置 / MCP / 技能 / 角色卡 / 记忆），与设置主页同款骨架 */
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    action: Pair<String, (() -> Unit)?>? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    /** 向下滚动时把 FAB 收起来（参考实现的详情页就是这么做的）。 */
    hideFabOnScrollDown: Boolean = false,
    overlay: (@Composable () -> Unit)? = null,
    /** 顶栏动作是否可点。 */
    actionEnabled: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    /** 顶栏要不要走模糊采样。 */
    blurTopBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop? = null

    // FAB 的收放判定。`previousScroll` 用 IntArray(1) 避免在 derivedStateOf 求值期间写 Snapshot State。
    val previousScroll = remember { IntArray(1) }
    val fabVisible by remember {
        derivedStateOf {
            val current = listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset
            val delta = current - previousScroll[0]
            previousScroll[0] = current
            delta <= 0
        }
    }

    Scaffold(
        topBar = {
            /* 顶栏底色 `scheme.surface` **纯色**，不再包 `PageBlurBar`。 */
            run {
                // 与设置主页同款顶栏：Miuix TopAppBar 自带 largeTitle（大标题随滚动折叠）
                TopAppBar(
                    title = title,
                    // Miuix TopAppBar 原生就有 subtitle 槽位，它自己负责在标题下方
                    subtitle = subtitle ?: "",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = scheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = ZhiIcons.back,
                                contentDescription = "返回",
                                tint = scheme.onBackground,
                            )
                        }
                    },
                actions = {
                    if (action != null && action.second != null) {
                        TextButton(
                            text = action.first,
                            onClick = action.second!!,
                            enabled = actionEnabled,
                        )
                    }
                },
            )
            }
        },
        floatingActionButton = {
            floatingActionButton?.let { fab ->
                if (hideFabOnScrollDown) {
                    AnimatedVisibility(
                        visible = fabVisible,
                        // 与设置页 FAB 同款进出：淡变 + 缩放。
                        enter = fadeIn() + scaleIn(animationSpec = ZhiMotion.scaleEnterSpec),
                        exit = fadeOut() + scaleOut(animationSpec = ZhiMotion.scaleExitSpec),
                    ) { fab() }
                } else {
                    fab()
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (header != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 见 header 参数的说明：body 在 (0,0)，顶栏画在它上面，
                        // 不补这个 padding 的话 header 会被顶栏整个盖住。
                        .padding(top = padding.calculateTopPadding()),
                ) { header() }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        // header 自己已经顶掉了顶栏高度，这里再算一次会让内容
                        // 凭空多出一段与顶栏等高的空白。
                        top = if (header == null) padding.calculateTopPadding() else 0.dp,
                        // FAB 是悬浮的，会盖住列表最后几行 —— 底部留出它的高度 + 间距
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    item(key = "subPageBody", contentType = "settings-subpage-body") { content() }
                }
                // 浮层挂在 Scaffold 的 composition 里、LazyColumn 之外 —— 见 overlay 参数的说明。
                overlay?.invoke()
            }
            // 二级页的操作反馈（「保存失败」「已删除技能」…）。
            PageFeedbackSlot(modifier = Modifier.fillMaxWidth())
        }
    }
}
