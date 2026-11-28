package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eNavTransition
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Application
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhizhu.zhicode.compose.editor.EditorActivity
import com.zhizhu.zhicode.compose.theme.ZhiAppTheme
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.state.WorkspaceViewModelFactory
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/** 取（或创建）主 ViewModel。 */
@Composable
private fun rememberWorkspaceViewModel(): WorkspaceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { WorkspaceViewModelFactory(application) }
    return viewModel(factory = factory)
}

/** 应用入口：主题 + 主界面。 */
@Composable
fun ZhiCodeApp(viewModel: WorkspaceViewModel = rememberWorkspaceViewModel()) {
    // UI 收集与 Activity 生命周期绑定：后台时暂停快照消费，避免流式引擎仍在
    // 组合树不可见时持续驱动整棵 UI。
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 深/浅只有一处判定（见 ZhiThemeMode 的推导）：应用设置 `ThemeMode` + 系统深浅。
    val isDark = ZhiThemeMode.rememberCurrentDark(state.themeMode)
    // 主题栈只有一处（theme/ZhiAppTheme.kt）：编辑器那个独立 Activity 用的是同一个，
    // 所以"应用内切主题后两屏颜色不一致"这类问题不会再出现。
    ZhiAppTheme(theme = state.themeSettings) {
        /* MIUIX 的 `WindowNavigationEventBridge()` **不挂**。 */
        // 这里**不再**挂常开的帧泵。
        ZhiCodeScreen(state = state, viewModel = viewModel, isDark = isDark)
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

    // 帧耗时测量的出口：结果以一条 INFO 消息落在对话流里（仅 debug 构建会触发）。
    val traceContext = LocalContext.current
    LaunchedEffect(viewModel) {
        // 主落点：filesDir（沙箱里宿主机可直接读，路径见上面注释）。
        ZhiFrameTrace.bindLogFile(
            java.io.File(traceContext.filesDir, "zhi-frame.log"),
            // 第二落点：`Android/media/<包名>/`。
            shared = traceContext.getExternalMediaDirs()?.firstOrNull()?.let { dir ->
                runCatching {
                    dir.mkdirs()
                    java.io.File(dir, "zhi-frame.log")
                }.getOrNull()
            },
        )
        ZhiFrameTrace.sink = { line -> viewModel.reportExternalEvent("帧耗时", line) }
    }

    // 每秒汇总一次重组计数（仅 debug 构建真的执行；release 里 `ZhiFrameTrace.enabled` 为假）。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            ZhiFrameTrace.flushRecomposeCounts()
        }
    }

    // 各整页在退出动画期间的「最后内容」缓存（见下方 settingsUi / apiUi 的说明）。
    var lastSettingsDraft by remember { mutableStateOf<SettingsDraft?>(null) }
    var lastApiConfig by remember { mutableStateOf<ApiConfigState?>(null) }
    var lastMcpConfig by remember { mutableStateOf<McpConfigState?>(null) }
    var lastSearchServices by remember { mutableStateOf<SearchServicesState?>(null) }
    var lastSkills by remember { mutableStateOf<SkillsState?>(null) }
    var lastRoleCards by remember { mutableStateOf<RoleCardsState?>(null) }
    var lastMemory by remember { mutableStateOf<MemoryState?>(null) }

    // ---- 编辑器（独立 Activity）
    val context = LocalContext.current
    val editorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refreshFiles()
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri -> uri?.let(viewModel::exportSessionTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importSessionFrom) }
    LaunchedEffect(state.pendingEditorOpen) {
        val path = state.pendingEditorOpen ?: return@LaunchedEffect
        viewModel.consumePendingEditorOpen()
        editorLauncher.launch(EditorActivity.intent(context, path))
    }

    // 每页保留「最后一次非空状态」：关闭动作会先把状态置空，而退出动画还要跑
    // 几百毫秒，没有保留值那段时间页面内容会整个闪没（只剩空背景）。
    val settingsUi = state.settingsDraft ?: lastSettingsDraft
    lastSettingsDraft = settingsUi
    val apiUi = state.apiConfig ?: lastApiConfig
    lastApiConfig = apiUi
    val mcpUi = state.mcpConfig ?: lastMcpConfig
    lastMcpConfig = mcpUi
    val searchServicesUi = state.searchServices ?: lastSearchServices
    lastSearchServices = searchServicesUi
    val skillsUi = state.skills ?: lastSkills
    lastSkills = skillsUi
    val roleCardsUi = state.roleCards ?: lastRoleCards
    lastRoleCards = roleCardsUi
    val memoryUi = state.memory ?: lastMemory
    lastMemory = memoryUi


    // 玻璃对象分两层，因为捕获节点不能包含自己
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    /* 页面组的解析必须落在每个 NavDisplay entry 的内部（读取 `LocalZhiPageSet.current`） */

    // UI 重构（S1/S2）：顶栏迁入官方 `topBar` 槽位（与 Miuix example 的
    val nav = remember {
        NavController(
            navBackStackOf(if (state.oobeOpen) AppKey.Oobe else AppKey.Workspace)
        )
    }

    // 由 ViewModel 状态派生的「期望栈」：VM 仍是打开/关闭的唯一入口，
    // 这里只把它翻译成栈，增量 reconcile —— pop 触发弹出动画、push 触发推入动画。
    val desiredStack: List<NavKey> = buildList {
        if (state.oobeOpen) {
            add(AppKey.Oobe)
            if (state.apiConfig != null) add(SettingsKey.Api)
        } else {
            add(AppKey.Workspace)
            if (state.uiDebugOpen) {
                // UI 调试是**整页**而不是设置二级页：它显示的是全部组件与状态
                add(AppKey.UiDebug)
            } else {
                if (state.settingsOpen) add(SettingsKey.Hub)
                when {
                    state.apiConfig != null -> add(SettingsKey.Api)
                    state.mcpConfig != null -> add(SettingsKey.Mcp)
                    state.searchServices != null -> add(SettingsKey.Search)
                    state.skills != null -> add(SettingsKey.Skills)
                    state.roleCards != null -> add(SettingsKey.RoleCards)
                    state.memory != null -> add(SettingsKey.Memory)
                    state.themePageOpen -> add(SettingsKey.Theme)
                    state.chatDisplayOpen -> add(SettingsKey.ChatDisplay)
                }
                if (state.promptsOpen) add(SettingsKey.Prompts)
            }
            /* 「关于 / 许可 / 隐私」必须**串在设置分支之后**，不能放在它前面。 */
            if (state.aboutOpen) {
                add(AppKey.About)
                if (state.licensesOpen) add(AppKey.Licenses)
                if (state.privacyOpen) add(AppKey.Privacy)
                if (state.updatePageOpen) add(AppKey.Update)
            }
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


    // ---- 正交效果层（圆角裁剪 + 调暗）与手势返回方向 
    val navCornerRadius = rememberNavSystemCornerRadius()
    val navBackdrop = ZhiColors.backdrop()
    val navEffects = remember(navCornerRadius, navBackdrop) {
        NavDisplayEffects(
            cornerClipRadius = navCornerRadius,
            dimAmount = 0.5f,
            backdropColor = navBackdrop,
        )
    }
    // 滑动关闭是 **opt-in**（Miuix 默认全关）。
    val swipeBack: NavSwipeDirection? = if (state.themeSettings.edgeSwipeBack) {
        when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            else -> NavSwipeDirection.LeftToRight
        }
    } else {
        null
    }

    // 整页（设置 / 二级页 / UI 调试）底部的反馈条数据源。
    val pageFeedback = remember(state.message, state.messageIsError, viewModel) {
        PageFeedbackData(
            message = state.message,
            isError = state.messageIsError,
            onDismiss = viewModel::clearMessage,
        )
    }

    CompositionLocalProvider(
        LocalPageFeedback provides pageFeedback,
        LocalActionBusy provides state.inFlight,
    ) {
    NavDisplay(
        backStack = nav.backStack,
        modifier = Modifier.fillMaxSize(),
        transition = if (LocalZhiPageSet.current.id == "m3_expressive") {
            M3eNavTransition.transition
        } else {
            NavTransitions.MiuixDefault
        },
        effects = navEffects,
        onBack = {
            // 系统返回：按当前栈顶逐级回退，并同步关掉 VM 的对应状态。
            val live = viewModel.state.value
            when {
                live.oobeOpen -> when {
                    live.apiConfig != null -> viewModel.closeApiConfig()
                    live.oobeStep != com.zhizhu.zhicode.compose.runtime.OobeStep.PERMISSIONS -> viewModel.goBackOobeStep()
                    !com.zhizhu.zhicode.compose.data.OobeStore.isRequired(context) -> viewModel.dismissReopenedOobe()
                }
                live.uiDebugOpen -> viewModel.closeUiDebug()
                live.updatePageOpen -> viewModel.closeUpdate()
                live.privacyOpen -> viewModel.closePrivacy()
                live.licensesOpen -> viewModel.closeLicenses()
                live.aboutOpen -> viewModel.closeAbout()
                live.promptsOpen -> viewModel.closePrompts()
                live.apiConfig != null -> viewModel.closeApiConfig()
                live.mcpConfig != null -> viewModel.closeMcpConfig()
                live.searchServices != null -> viewModel.closeSearchServices()
                live.skills != null -> viewModel.closeSkills()
                live.roleCards != null -> viewModel.closeRoleCards()
                live.memory != null -> viewModel.closeMemory()
                live.themePageOpen -> viewModel.closeThemePage()
                live.chatDisplayOpen -> viewModel.closeChatDisplay()
                live.settingsOpen -> viewModel.closeSettings()
            }
        },
    ) {
        entry<AppKey.Oobe> {
            LocalZhiPageSet.current.Oobe(state = state, viewModel = viewModel)
        }
        entry<AppKey.Workspace> {
            LocalZhiPageSet.current.Workspace(
                state = state,
                viewModel = viewModel,
                isDark = isDark,
                wide = wide,
                glass = glass,
                glassMain = glassMain,
            )
        }
        // 设置页与它的二级页开启边缘滑动返回（Miuix 的 opt-in）；root 工作区不开 ——
        // 它下面没有可回退的页，而内部已经有侧栏抽屉与面板切换在横向上处理手势。
        entry<SettingsKey.Hub>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Settings(
                draft = settingsUi,
                viewModel = viewModel,
                extensions = state.extensions,
            )
        }
        entry<SettingsKey.Api>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.ApiConfig(config = apiUi, viewModel = viewModel)
        }
        entry<SettingsKey.Search>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.SearchServices(state = searchServicesUi, viewModel = viewModel)
        }
        entry<SettingsKey.Mcp>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.McpConfig(config = mcpUi, viewModel = viewModel)
        }
        entry<SettingsKey.Skills>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Skills(state = skillsUi, viewModel = viewModel)
        }
        entry<SettingsKey.RoleCards>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.RoleCards(state = roleCardsUi, viewModel = viewModel)
        }
        entry<SettingsKey.Memory>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Memory(state = memoryUi, viewModel = viewModel)
        }
        entry<SettingsKey.Theme>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Theme(theme = state.themeSettings, viewModel = viewModel)
        }
        entry<SettingsKey.ChatDisplay>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.ChatDisplay(theme = state.themeSettings, viewModel = viewModel)
        }
        entry<SettingsKey.Prompts>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Prompts(state = state, viewModel = viewModel)
        }
        entry<AppKey.About>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.About(update = state.update, viewModel = viewModel)
        }
        entry<AppKey.Update>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Update(state = state.update, viewModel = viewModel)
        }
        entry<AppKey.Licenses>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Licenses(viewModel = viewModel)
        }
        entry<AppKey.Privacy>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.Privacy(viewModel = viewModel)
        }
        // UI 调试整页（仅 debug 构建有入口）。它拿的是**真实 state 与 ViewModel**：
        // 页面里的输入器、各浮层入口都是真能用的，不是静态贴图。
        entry<AppKey.UiDebug>(swipeDismiss = swipeBack) {
            LocalZhiPageSet.current.UiDebug(
                state = state,
                viewModel = viewModel,
                glass = glass,
                onExportSession = { name -> exportLauncher.launch(name) },
                onImportSession = { importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*")) },
            )
        }
    }
    }
}




// ------------------------------------------------------------------ 页面栈 key

/** 应用根页。 */
private sealed interface AppKey : NavKey {
    data object Oobe : AppKey
    data object Workspace : AppKey

    /** UI 调试整页（debug 构建的设置页里有入口）。 */
    data object UiDebug : AppKey

    /** 关于页与第三方许可页。 */
    data object About : AppKey
    data object Licenses : AppKey

    /** 隐私政策。与 [Licenses] 平级：都从关于页进，压在关于之上。 */
    data object Privacy : AppKey

    /** 「检查更新」。 */
    data object Update : AppKey
}

/** 设置相关页面：hub 与各二级页。 */
private sealed interface SettingsKey : NavKey {
    data object Hub : SettingsKey
    data object Api : SettingsKey
    data object Mcp : SettingsKey
    data object Search : SettingsKey
    data object Skills : SettingsKey
    data object RoleCards : SettingsKey
    data object Memory : SettingsKey
    data object Prompts : SettingsKey
    data object Theme : SettingsKey

    /** 聊天显示（自定义字体 + 正文字号）。 */
    data object ChatDisplay : SettingsKey
}

