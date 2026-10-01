package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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

    // ---- 返回键路由（最优先的页面在最上面）----
    // 预测性返回手势来到 Compose 层时，谁在最上层谁消费：二级页 → 设置主页 → 沙箱/环境页。
    // 之前没有任何 BackHandler，手势直接落到 Activity，整页设置被一把关掉——
    // 用户感觉是「从二级页返回却退到了主页」，实际是退到了应用之外/主界面。
    BackHandler(enabled = state.apiConfig != null) { viewModel.closeApiConfig() }
    BackHandler(enabled = state.mcpConfig != null) { viewModel.closeMcpConfig() }
    BackHandler(enabled = state.skills != null) { viewModel.closeSkills() }
    BackHandler(enabled = state.roleCards != null) { viewModel.closeRoleCards() }
    BackHandler(enabled = state.memory != null) { viewModel.closeMemory() }
    BackHandler(enabled = state.settingsOpen) { viewModel.closeSettings() }

    
    // ---- 设置整页（K4：像 miuix 示例的 SettingsPage，覆盖全屏）----
    // 画在 Scaffold/抽屉之后 = 最上层；打开时整页盖住工作区。
    // 动效对齐侧栏抽屉（ZhiMotion）：淡入 + 底部轻微上移，关闭时反向。
    // `settingsUi` 保留最后一次非空的 draft：closeSettings 会先把状态置空，
    // 没有 retained 值的话退出动画的那几百毫秒里页面内容会整个闪没。
    val settingsUi = state.settingsDraft ?: lastSettingsDraft
    lastSettingsDraft = settingsUi
    AnimatedVisibility(
        visible = state.settingsOpen,
        enter = fadeIn(tween(ZhiMotion.MEDIUM)) + slideInVertically(tween(ZhiMotion.MEDIUM)) { it / 12 },
        exit = fadeOut(tween(ZhiMotion.FAST)) + slideOutVertically(tween(ZhiMotion.FAST)) { it / 12 },
    ) {
        SettingsDialog(
            draft = settingsUi,
            onChange = viewModel::setSettingsDraft,
            onDismiss = viewModel::closeSettings,
            onSave = viewModel::saveSettings,
            onNavigate = viewModel::navigateFromSettings,
        )
    }

    // ---- 设置二级页（K6：整页化后必须挂在根层）----
    // 它们现在是 SettingsSubPage（自带 Scaffold+顶栏）。若留在 ZhiOverlayHost
    // （主 Scaffold 的 bodyContent 里），会被主顶栏/Tab 压住（Miuix Scaffold
    // 绘制顺序 bodyContent → topBar）。挂在根层、设置主页之后 = 盖住一切。
    //
    // 每个页都保留「最后一次非空状态」：关闭动作会立刻把状态置空，而退出动画
    // 还要跑 160ms，没有保留值的话那段时间页面内容会整个闪没（只剩空背景）。
    val apiUi = state.apiConfig ?: lastApiConfig
    lastApiConfig = apiUi
    SubPageHost(visible = state.apiConfig != null) {
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

    val mcpUi = state.mcpConfig ?: lastMcpConfig
    lastMcpConfig = mcpUi
    SubPageHost(visible = state.mcpConfig != null) {
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

    val skillsUi = state.skills ?: lastSkills
    lastSkills = skillsUi
    SubPageHost(visible = state.skills != null) {
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

    val roleCardsUi = state.roleCards ?: lastRoleCards
    lastRoleCards = roleCardsUi
    SubPageHost(visible = state.roleCards != null) {
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

    val memoryUi = state.memory ?: lastMemory
    lastMemory = memoryUi
    SubPageHost(visible = state.memory != null) {
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

/** 二级页的动效外壳：visible 由「状态非空」驱动，内容保留最后一次非空值。 */
@Composable
private fun SubPageHost(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(ZhiMotion.MEDIUM)) + slideInVertically(tween(ZhiMotion.MEDIUM)) { it / 14 },
        exit = fadeOut(tween(ZhiMotion.FAST)) + slideOutVertically(tween(ZhiMotion.FAST)) { it / 14 },
    ) {
        content()
    }
}
