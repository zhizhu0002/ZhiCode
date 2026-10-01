package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.isSystemInDarkTheme
import com.zhizhu.zhicode.compose.ui.dialogs.ApiConfigOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.McpConfigOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.MemoryOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.RoleCardsOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.SkillsOverlay
import com.zhizhu.zhicode.compose.ui.settings.SettingsDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.theme.zhiTextStyles
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 取（或创建）主 ViewModel。
 *
 * 不能用默认的 `viewModel()`：反射工厂需要精确的 `(Application)` 构造器，而
 * `WorkspaceViewModel` 的构造器带默认参数，Kotlin 不会生成那个重载，会直接抛
 * `NoSuchMethodException` 并让界面白屏。详见 [WorkspaceViewModelFactory]。
 *
 * 工厂里再注入 `WorkspaceRepository`。注意它现在只剩「终端占位横幅」一项：
 * 对话流、工具执行、会话文件都已由真实引擎负责，不再是模拟实现。
 */
@Composable
private fun rememberWorkspaceViewModel(): WorkspaceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { WorkspaceViewModelFactory(application) }
    return viewModel(factory = factory)
}

/** 应用入口：主题 + 主界面。 */
@Composable
fun ZhiCodeApp(viewModel: WorkspaceViewModel = rememberWorkspaceViewModel()) {
    val state by viewModel.state.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    // LocalZhiDark 必须**先**提供，之后才能求任何 ZhiColors 层级色：
    // 这些色函数靠 LocalZhiDark 判断深浅，读早了会拿到默认值 false（浅色），
    // 于是深色模式下背板被设成浅灰、弹窗整片发白。
    CompositionLocalProvider(LocalZhiDark provides isDark) {
        // 主背板 = 灰（深色 #242424 / 浅色 #EDEDED），侧栏与面板则用纯黑/纯白。
        // 两者是互换过的层级：背板退后，板块站出来。
        // 之所以要覆盖主题的 background：Miuix 浅色方案里 background / surface /
        // surfaceContainer 都是 #FFFFFF，纯白铺满后板块完全分不出来。
        val appColors = colors.copy(background = ZhiColors.backdrop())
        // textStyles 必须在这里给：Miuix 组件读的是主题样式（TextField→main、
        // Button→button、BasicComponent→headline1/body2、SmallTitle→subtitle），
        // 逐个传 fontSize 够不到它们。字阶与依据见 theme/ZhiTextStyles.kt。
        MiuixTheme(colors = appColors, textStyles = zhiTextStyles()) {
            ZhiCodeScreen(state = state, viewModel = viewModel, isDark = isDark)
        }
    }
}

@Composable
private fun ZhiCodeScreen(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600

    // 各整页在退出动画期间的「最后内容」缓存（见下方 settingsUi / apiUi 的说明）。
    var lastSettingsDraft by remember { mutableStateOf<SettingsDraft?>(null) }
    var lastApiConfig by remember { mutableStateOf<ApiConfigState?>(null) }
    var lastMcpConfig by remember { mutableStateOf<McpConfigState?>(null) }
    var lastSkills by remember { mutableStateOf<SkillsState?>(null) }
    var lastRoleCards by remember { mutableStateOf<RoleCardsState?>(null) }
    var lastMemory by remember { mutableStateOf<MemoryState?>(null) }

    // 每页保留「最后一次非空状态」：关闭动作会先把状态置空，而退出动画还要跑
    // 几百毫秒，没有保留值那段时间页面内容会整个闪没（只剩空背景）。
    val settingsUi = state.settingsDraft ?: lastSettingsDraft
    lastSettingsDraft = settingsUi
    val apiUi = state.apiConfig ?: lastApiConfig
    lastApiConfig = apiUi
    val mcpUi = state.mcpConfig ?: lastMcpConfig
    lastMcpConfig = mcpUi
    val skillsUi = state.skills ?: lastSkills
    lastSkills = skillsUi
    val roleCardsUi = state.roleCards ?: lastRoleCards
    lastRoleCards = roleCardsUi
    val memoryUi = state.memory ?: lastMemory
    lastMemory = memoryUi


    // 玻璃对象分两层，因为捕获节点不能包含自己：
    //  · glassMain —— 捕获「整个工作区」；用于顶栏 / 侧栏抽屉 / 弹窗。
    //  · glassChat —— 捕获「对话列表」；用于底部悬浮的任务卡与输入器。
    //    这两层必须分开：输入器就在对话区里面，若共用整屏捕获，它会把自己
    //    录进背景里，模糊时出现自我叠影。
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    // UI 重构（S1/S2）：顶栏迁入官方 `topBar` 槽位（与 Miuix example 的
    // BlurredBar + TopAppBar 同一结构）。此前顶栏是 Scaffold 内容里手排的
    // 悬浮 Box 层叠 —— 这让「+」下拉菜单等 Overlay 弹层的锚点坐标
    // （调用层的 positionInWindow）与根 MiuixPopupHost 的窗口原点不一致，
    // 菜单整体飞到锚点上方。迁入槽位后触发器与 PopupHost 同处 Scaffold
    // 根坐标系，弹出位置贴回锚点；手排 z 序与 TopBarInset 手工留白一并删除。
    // 抽屉必须画在 Scaffold **之外**：Miuix Scaffold 的绘制顺序是
    // bodyContent → topBar → popup，放在内容里的抽屉永远被顶栏压住。
    // ---- 页面栈：工作区（root）+ 设置页们（miuix-nav 的 NavDisplay）----
    //
    // 整页之间的推入/弹出交给官方导航容器，动效用它的 MiuixDefault 预设：
    //   推入：新页全宽从**右缘滑入**，被覆盖页向左视差 1/4 宽 + α 衰减 10%
    //   弹出：反向（返回时当前页向右滑出）
    // 这正是需求里「进入从右到左、退出反之」；之前的 AnimatedVisibility 是
    // 页面各自硬切，方向得手工标记、层级也得手工维护，还会出现「二级页返回
    // 直接回工作区」的断栈问题。栈由系统返回键驱动（NavDisplay 内部接
    // PredictiveBackHandler），层级天然正确：二级页 → 设置主页 → 工作区。
    val nav = remember { NavController(navBackStackOf(AppKey.Workspace)) }

    // 由 ViewModel 状态派生的「期望栈」：VM 仍是打开/关闭的唯一入口，
    // 这里只把它翻译成栈，增量 reconcile —— pop 触发弹出动画、push 触发推入动画。
    val desiredStack: List<NavKey> = buildList {
        add(AppKey.Workspace)
        if (state.settingsOpen) add(SettingsKey.Hub)
        when {
            state.apiConfig != null -> add(SettingsKey.Api)
            state.mcpConfig != null -> add(SettingsKey.Mcp)
            state.skills != null -> add(SettingsKey.Skills)
            state.roleCards != null -> add(SettingsKey.RoleCards)
            state.memory != null -> add(SettingsKey.Memory)
        }
    }
    LaunchedEffect(desiredStack) {
        val stack = nav.backStack
        // 先弹掉多余的（返回），再压入新增的（进入）；同深度换页走 replace。
        while (stack.size > desiredStack.size) stack.removeAt(stack.lastIndex)
        for (i in stack.size until desiredStack.size) stack.add(desiredStack[i])
        if (stack.isNotEmpty() && stack.last() != desiredStack.last()) {
            stack[stack.lastIndex] = desiredStack.last()
        }
    }

    // ---- 正交效果层（圆角裁剪 + 调暗）与手势返回方向 ----
    //
    // 照官方 example（`AppContent.kt`）的写法：
    //  · 圆角跟随**设备屏幕圆角** —— `rememberNavSystemCornerRadius()`，平台报 0
    //    （方角屏 / 非 Android）时就不裁。此前写死 16dp：在圆角更大的机器上会
    //    多切一块，在方屏上又白裁一圈，都不像系统。
    //  · `dimAmount` 保持官方滑动式转场的 0.5（卡片式才用 0.2/0.8）。
    //  · `backdropColor` 用**应用背板色**：被覆盖页向左视差 1/4 宽后露出的那一条
    //    读起来应当是"页面之外"，所以取与窗口底色同一层的颜色，而不是面板纯黑/纯白。
    val navCornerRadius = rememberNavSystemCornerRadius()
    val navBackdrop = ZhiColors.backdrop()
    val navEffects = remember(navCornerRadius, navBackdrop) {
        NavDisplayEffects(
            cornerClipRadius = navCornerRadius,
            dimAmount = 0.5f,
            backdropColor = navBackdrop,
        )
    }
    // 滑动关闭是 **opt-in**（Miuix 默认全关）。方向是**物理方向**、不随布局方向镜像，
    // 所以按当前布局方向在 LTR / RTL 之间选 —— 与官方 example 同一段逻辑。
    val swipeBack = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }

    NavDisplay(
        backStack = nav.backStack,
        modifier = Modifier.fillMaxSize(),
        transition = NavTransitions.MiuixDefault,
        effects = navEffects,
        onBack = {
            // 系统返回：按当前栈顶逐级回退，并同步关掉 VM 的对应状态。
            // 只有 root（工作区）时把返回交还给系统（退出应用）。
            when {
                state.apiConfig != null -> viewModel.closeApiConfig()
                state.mcpConfig != null -> viewModel.closeMcpConfig()
                state.skills != null -> viewModel.closeSkills()
                state.roleCards != null -> viewModel.closeRoleCards()
                state.memory != null -> viewModel.closeMemory()
                state.settingsOpen -> viewModel.closeSettings()
            }
        },
    ) {
        entry<AppKey.Workspace> {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (wide) {
                    // 宽屏：侧栏常驻，顶栏只覆盖右侧内容区 —— 由 WorkspaceLayouts
                    // 里的 WideWorkspace 自己排侧栏 + 分隔线 + 顶栏，这里不重复挂。
                } else {
                    ZhiTopBar(
                        state = state,
                        wide = false,
                        glass = glassMain,
                        onOpenSidebar = viewModel::openSidebar,
                        onContextClick = {
                            viewModel.onComposerChange("/usage")
                            viewModel.send()
                        },
                        onSettings = viewModel::openSettings,
                        tabs = WorkspaceTab.entries,
                        onSelectTab = viewModel::selectTab,
                    )
                }
            },
        ) { padding ->
            // 顶栏 blur 需要内容从它**底下滚过**才有东西可采样：
            // 去掉 padding.top，让内容 Box 从 y=0 铺满；各面板自己用
            // TopBarTotalInset 在内容头部留白（见 WorkspaceLayouts）。
            Box(
                modifier = Modifier.fillMaxSize().padding(
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                // 整块工作区先录进 glassMain 的背景（抽屉/弹窗的模糊来源）
                Box(modifier = Modifier.fillMaxSize().then(glassMain.capture(Modifier))) {
                    if (wide) {
                        // 宽屏的顶栏在右侧内容区内部（与常驻侧栏并列），见 WideWorkspace
                        WideWorkspace(
                            state = state,
                            viewModel = viewModel,
                            isDark = isDark,
                            glass = glass,
                            glassMain = glassMain,
                        )
                    } else {
                        CompactWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                    }
                }


                // ---- Overlay 系列 ----
                // 弹窗挂载与模态模糊背景在 OverlayHost.kt。
                // 必须仍处于 Scaffold 内容里才能找到 popupHost。
                ZhiOverlayHost(state = state, viewModel = viewModel, glassMain = glassMain)
            }
        }

        // ---- 侧边栏抽屉（窄屏，画在 Scaffold 之上）----
        if (!wide) {
            ZhiSideDrawer(
                open = state.sidebarOpen,
                onClose = viewModel::closeSidebar,
                width = (configuration.screenWidthDp * 0.82f).dp.coerceAtMost(320.dp),
                glass = glassMain,
            ) {
                ZhiSidebarHost(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        }
        }
        // 设置页与它的二级页开启边缘滑动返回（Miuix 的 opt-in）；root 工作区不开 ——
        // 它下面没有可回退的页，而内部已经有侧栏抽屉与面板切换在横向上处理手势。
        entry<SettingsKey.Hub>(swipeDismiss = swipeBack) {
            SettingsDialog(
                draft = settingsUi,
                onChange = viewModel::setSettingsDraft,
                onDismiss = viewModel::closeSettings,
                onSave = viewModel::saveSettings,
                onNavigate = viewModel::navigateFromSettings,
            )
        }
        entry<SettingsKey.Api>(swipeDismiss = swipeBack) {
            ApiConfigOverlay(
                config = apiUi,
                onDismiss = viewModel::closeApiConfig,
                onNew = viewModel::newApiProfile,
                onEdit = viewModel::editApiProfile,
                onSelect = viewModel::selectApiProfile,
                onDelete = viewModel::deleteApiProfile,
                onDraftChange = viewModel::updateApiProfileDraft,
                onSave = viewModel::saveApiProfile,
                onCancelForm = viewModel::cancelApiProfileForm,
            )
        }
        entry<SettingsKey.Mcp>(swipeDismiss = swipeBack) {
            McpConfigOverlay(
                config = mcpUi,
                onDismiss = viewModel::closeMcpConfig,
                onNew = viewModel::newMcpServer,
                onEdit = viewModel::editMcpServer,
                onToggle = viewModel::toggleMcpServer,
                onDelete = viewModel::deleteMcpServer,
                onDraftChange = viewModel::updateMcpDraft,
                onSave = viewModel::saveMcpServer,
                onCancelForm = viewModel::cancelMcpForm,
            )
        }
        entry<SettingsKey.Skills>(swipeDismiss = swipeBack) {
            SkillsOverlay(
                state = skillsUi,
                onDismiss = viewModel::closeSkills,
                onNew = viewModel::newSkill,
                onEdit = viewModel::editSkill,
                onAttach = viewModel::attachSkill,
                onDelete = viewModel::deleteSkill,
                onCreateDraftChange = viewModel::updateSkillCreateDraft,
                onCreate = viewModel::createSkill,
                onCancelCreate = viewModel::cancelSkillCreate,
                onBodyChange = viewModel::updateSkillBody,
                onSave = viewModel::saveSkill,
                onCancelEdit = viewModel::cancelSkillEdit,
            )
        }
        entry<SettingsKey.RoleCards>(swipeDismiss = swipeBack) {
            RoleCardsOverlay(
                state = roleCardsUi,
                onDismiss = viewModel::closeRoleCards,
                onNew = viewModel::newRoleCard,
                onEdit = viewModel::editRoleCard,
                onSelect = viewModel::selectRoleCard,
                onDisable = viewModel::disableRoleCard,
                onDelete = viewModel::deleteRoleCard,
                onDraftChange = viewModel::updateRoleCardDraft,
                onSave = viewModel::saveRoleCard,
                onCancelEditor = viewModel::cancelRoleCardEditor,
            )
        }
        entry<SettingsKey.Memory>(swipeDismiss = swipeBack) {
            MemoryOverlay(
                state = memoryUi,
                onDismiss = viewModel::closeMemory,
                onEdit = viewModel::editMemory,
                onRunInit = { viewModel.closeMemory(); viewModel.runInitFromUi() },
                onBodyChange = viewModel::updateMemoryBody,
                onSave = viewModel::saveMemory,
                onCancelEdit = viewModel::cancelMemoryEdit,
            )
        }
    }
}




// ------------------------------------------------------------------ 页面栈 key

/**
 * 应用根页。整页栈的 root = 工作区（顶栏/Tab/面板/输入器），
 * 设置与它的二级页作为栈上更深的一层压在上面。
 *
 * 用 [navBackStackOf] 建栈（内存态，不跨进程持久化），所以 key 不需要
 * `@Serializable` —— 栈的真源仍是 ViewModel 状态，这里只是把它渲染出来。
 */
private sealed interface AppKey : NavKey {
    data object Workspace : AppKey
}

/** 设置相关页面：hub 与五个二级页。 */
private sealed interface SettingsKey : NavKey {
    data object Hub : SettingsKey
    data object Api : SettingsKey
    data object Mcp : SettingsKey
    data object Skills : SettingsKey
    data object RoleCards : SettingsKey
    data object Memory : SettingsKey
}
