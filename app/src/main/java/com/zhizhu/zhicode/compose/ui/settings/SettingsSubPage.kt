package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
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
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 二级页内部的一页。
 *
 * [id] 是页面栈里的路由键（它同时是状态保留的键），[depth] 只用来给调用方排序，
 * 转场方向由 Miuix 的 nav 自己按栈深算。用数据类而不是让每个页面各定义一个
 * sealed 层级：页面之间没有共享字段，各自定义只会让五个调用点各写一遍样板。
 *
 * 必须是**值相等的 data class**：Miuix 用 key 实例做 entry 的 `contentKey`
 * （页面状态的存档标识），`toString()` 必须由值派生，否则进程重启后状态会静默重置。
 */
internal data class SettingsPageKey(val id: String, val depth: Int) : NavKey

/**
 * 二级页内部的**页面栈**。
 *
 * ## 为什么需要它
 *
 * 二级页（API 配置 / MCP / 技能 / 角色卡 / 记忆）各自有好几态：列表 → 新建表单 →
 * 编辑器。这些态以前是调用方一个 `when {}` 里的内容**硬切** —— 而
 * `AppScaffold` 的页面栈只反映「哪一页开着」（`state.skills != null`），
 * 栈深度不变，外层 `NavDisplay` 就无事可做，于是**一点动画都没有**。
 * 用户报的「三级窗口动画非常不完整」就是这个。
 *
 * ## 为什么用 Miuix 自己的 `NavDisplay`，而不是 `AnimatedContent`
 *
 * 第一版是手搓的 `AnimatedContent` + `slideInHorizontally` / `slideOutHorizontally`，
 * 用户实测后报**两个毛病**，两个都出在它身上：
 *
 * 1. **卡**：`slideInHorizontally` 是**布局**型动画（改的是 `Modifier.offset`），
 *    整屏 `Scaffold` + `LazyColumn` 每帧都要重新测量与布局。Miuix 自己的转场走
 *    graphicsLayer，它的源码里明确写着这样 "cost zero recomposition"。
 * 2. **奇怪**：进入用的是 spring（`folmeSpring(0.9f, 0.3f)`，会过冲），退出用的是
 *    `tween(200)` —— 两条曲线时长不匹配；而且没有 Miuix 转场自带的 dim 与
 *    **跟随屏幕圆角的裁剪**，所以看着不像系统转场。
 *
 * 所以现在**逐字复用与外层 `AppScaffold` 相同的参数**：同一个 [NavTransitions.MiuixDefault]、
 * 同一套 [NavDisplayEffects]。内层与外层是同一套组件、同一套数值，手感自然一致。
 *
 * ## 两处与外层**刻意**不同
 *
 * - **不启用边缘滑动返回**（`entry(swipeDismiss = …)` 不传）：外层页面已经占了边缘
 *   手势，内外两层都开只会在边缘上打架。
 * - **`BackHandler` 只在 `depth > 0` 时生效**：最外层那一页的返回要交给外层
 *   `NavDisplay`（关掉整个二级页），这里不能抢。
 */
@Composable
internal fun SettingsPageStack(
    path: List<SettingsPageKey>,
    onBack: () -> Unit,
    content: @Composable (SettingsPageKey) -> Unit,
) {
    // 期望栈 = 调用方给的路径。调用方仍然是唯一的状态来源，这里只把它翻译成栈，
    // 增量 reconcile —— pop 触发弹出动画、push 触发推入动画。
    // 与外层 AppScaffold 完全同构（那里也是先算 desiredStack、再 reconcile）。
    //
    // ⚠️ 调用方必须传**整条路径**（含最底下那一页），不能让这里去合成一个"根"：
    // 根页的 id 只有调用方知道（`skills.list` / `api.list` …），
    // 合成出来的键与 entry 对不上，页面会渲染成空白。
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
        entry<SettingsPageKey> { key -> content(key) }
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
    /**
     * 右下角的悬浮操作按钮。
     *
     * ⚠️ 必须走 Miuix [Scaffold] 的这个**原生槽位**，不要在页面里套一层 `Box` +
     * `Modifier.align(BottomEnd)` 把 FAB 盖上去：那样 FAB 的**点击与布局都不归
     * Scaffold 管** —— 槽位里的 FAB 由 Scaffold 亲自摆放（还带 `FabPosition` /
     * `FabSpacing` 与 insets 处理），而手搓的那一层只是画在内容上面，
     * 位置、层级、点击都会被内容层影响。Miuix 官方 example 用的就是这个槽位
     * （`AppContent.kt` 的 `Scaffold(floatingActionButton = { FloatingActionButton(...) })`）。
     */
    floatingActionButton: (@Composable () -> Unit)? = null,
    /**
     * 这一页的**整屏浮层**（bottom sheet / 对话框）挂载点。
     *
     * ⚠️ 浮层必须挂在这里，不能写在调用方的顶层。
     *
     * Miuix 的弹层不是随便画在哪都行的：`DialogLayout` 只是把一个 `DialogState`
     * 注册进 **Scaffold 提供的**那个列表（`LocalDialogStates` / `LocalRootDialogStates`，
     * 见 `Scaffold.kt` 的 `CompositionLocalProvider`），真正的绘制由该 Scaffold 的
     * `MiuixPopupHost` 负责。所以浮层必须处在**某个 Scaffold 的 composition 之内**。
     *
     * 二级页（技能 / API 配置 / MCP …）是 `NavDisplay` 的 entry，与工作区那个
     * Scaffold 是**兄弟**关系 —— 它们在自己的层级上**没有任何 Scaffold**。
     * 用户报的「skill 的加号点不了」就是这个：`OverlayBottomSheet` 写在二级页的顶层，
     * 那里既没有 local 也没有 root 宿主，浮层根本没有地方可以挂，于是点了没反应。
     *
     * 挂在这里还顺带解决两件事：
     * 1. 它在本页 Scaffold 的 composition 里 → 宿主找得到；
     * 2. 它在 `LazyColumn` **之外**（下方那个 `Box` 直接是 Scaffold content）
     *    → 不会因为列表项滚出可视区被销毁而把浮层一起关掉。
     */
    overlay: (@Composable () -> Unit)? = null,
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
        // Miuix Scaffold 的原生 FAB 槽位：摆放、间距、insets 都由它负责。
        floatingActionButton = { floatingActionButton?.invoke() },
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
                    // FAB 是悬浮的，会盖住列表最后几行 —— 底部留出它的高度 + 间距，
                    // 否则最后一条永远被压着点不到。Scaffold 给的 bottom padding
                    // 已经算进了 FAB（见它的 FabSpacing 分支），这里再补一点余量。
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
            // 浮层挂在 Scaffold 的 composition 里、LazyColumn 之外 —— 见 overlay 参数的说明。
            overlay?.invoke()
        }
    }
}
