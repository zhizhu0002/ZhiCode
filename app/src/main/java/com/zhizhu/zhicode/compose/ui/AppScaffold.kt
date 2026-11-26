package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
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
    // UI 收集与 Activity 生命周期绑定：后台时暂停快照消费，避免流式引擎仍在
    // 组合树不可见时持续驱动整棵 UI。
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 深/浅只有一处判定（见 ZhiThemeMode 的推导）：应用设置 `ThemeMode` + 系统深浅。
    // 原先这里和三处各写一份，其中沙箱页那份读的是系统而不是应用设置，于是
    // 「设置里选浅色、系统是深色」时两屏颜色不一致。
    val isDark = ZhiThemeMode.rememberCurrentDark(state.themeMode)
    // 主题栈只有一处（theme/ZhiAppTheme.kt）：编辑器那个独立 Activity 用的是同一个，
    // 所以"应用内切主题后两屏颜色不一致"这类问题不会再出现。
    ZhiAppTheme(theme = state.themeSettings) {
        /*
         * MIUIX 的 `WindowNavigationEventBridge()` **不挂**。
         *
         * 它原本挂在 `if (themeSettings.predictiveBack)` 里，配着一个设置开关。
         * 但那个 composable 在主窗口里的第一句有效逻辑就是：
         *
         *     if (windowDispatcher === inheritedDispatcher) return
         *
         * 主窗口里两者必然是同一个 dispatcher，所以它进来就返回 —— 挂与不挂
         * 完全等价。那个开关因此是个**什么都不做**的开关，已经连同字段一起删除
         * （见 `ThemeStore` 的类头注释）。
         *
         * 要真正打开预测性返回，需要装配 `OnBackInvokedDefaultInput`
         * 并把 `targetSdk` 提到 33+；后者会连带改变存储权限、前台服务类型、
         * 通知权限与返回键路径的语义，不是一次修补能承受的。
         */
        // 这里**不再**挂常开的帧泵。
        //
        // 原来这里是 `ZhiFrameTraceHost()`，内部是
        // `while (true) { withFrameNanos { ... } }` —— 等于**每一帧都主动申请一帧**，
        // 应用永不休眠。实测空闲 14 秒、完全不碰屏幕时仍是：
        //   idle frames=300 avg=8.3ms (=120.0fps)  ← 持续满帧
        // 后果不是"多画几帧"，而是主线程永远被占着：任何触摸到达时都得等当前帧
        // 画完，于是"点设置、点模型、点页签全都滞后"，与点哪里无关。
        //
        // 现在改成按需：只有 `ZhiFrameTrace.begin()` 之后才要帧（见那里的说明），
        // 测量结束就自然停下，空闲时一帧都不申请。
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
    // 之所以不只用 logcat：沙箱 guest 的日志不进宿主 logcat，而截图是唯一可靠的观察通道。
    //
    // 另外落一份到 filesDir 下的纯文本：排查「页签高亮与面板内容对不上」这类问题时
    // 对话面板本身就看不见，写进对话流的那些行也读不到。宿主机上可以直接读这个文件
    // （blackbox/data/user/0/<包名>/files/zhi-frame.log）。
    val traceContext = LocalContext.current
    LaunchedEffect(viewModel) {
        // 主落点：filesDir（沙箱里宿主机可直接读，路径见上面注释）。
        ZhiFrameTrace.bindLogFile(
            java.io.File(traceContext.filesDir, "zhi-frame.log"),
            // 第二落点：`Android/media/<包名>/`。
            //
            // 真机上 filesDir 读不到（release 包不是 debuggable、`Android/data` 也不给列），
            // 而 `Android/media/<包名>/` 既是本应用无权限可写、又能被文件管理器直接打开。
            // 目录可能还没建好，所以这里只做一次 mkdirs，失败就只留主落点。
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
    //
    // 这条通道是给「狂闪」那类问题用的：帧耗时探针**测不出来**它 ——
    // 实测页签转场 avg=9.6ms max=25.0ms janks=0，logcat 里一条 Skipped frames 都没有。
    // 因为那不是"单帧慢"，而是**每帧都在重建界面**；帧耗时区分不了这两种病，重组次数可以。
    //
    // 输出形如 `ZhiFrame: recompose/s ChatList=60 ChatArea=60 Composer=1`：
    // =60 就是每帧一次（60Hz）—— 那就是要找的元凶；=1 是正常的一次。
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

    // ---- 编辑器（独立 Activity）------------------------------------------------
    //
    // 主界面与编辑器之间只有两条线：
    //  · `state.pendingEditorOpen`：非空就打开它（然后立刻清标志，否则重组会重复启动）；
    //  · 返回结果：RESULT_OK 表示编辑器里至少保存过一次 → 刷一次文件列表
    //    （不刷的话列表上还是旧的大小/时间，用户会以为没存上）。
    //
    // 正文、脏状态、未保存确认全在编辑器那边（EditorViewModel），主界面一概不知道。
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


    // 玻璃对象分两层，因为捕获节点不能包含自己：
    //  · glassMain —— 捕获「整个工作区」；用于顶栏 / 侧栏抽屉 / 弹窗。
    //  · glassChat —— 捕获「对话列表」；用于底部悬浮的任务卡与输入器。
    //    这两层必须分开：输入器就在对话区里面，若共用整屏捕获，它会把自己
    //    录进背景里，模糊时出现自我叠影。
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    /*
     * 页面组的解析必须落在每个 NavDisplay entry 的内部（读取 `LocalZhiPageSet.current`），
     * 不能像以前那样在 NavDisplay 外面提前抓成局部变量 `val pages = LocalZhiPageSet.current`：
     * NavDisplay 会按 key 缓存/复用 entry provider，提前捕获会导致用户在主题页切换
     * `pageSetId` 后，栈上的页面仍停留在旧的 MiuixPageSet 上，看似切换无效。
     */

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
        if (state.oobeOpen) {
            add(AppKey.Oobe)
            if (state.apiConfig != null) add(SettingsKey.Api)
        } else {
            add(AppKey.Workspace)
            if (state.uiDebugOpen) {
                // UI 调试是**整页**而不是设置二级页：它显示的是全部组件与状态，
                // 与设置 draft 无关。放在 settingsOpen 之前判断，于是从设置页进来时
                // 栈是 [工作区, UI 调试]（设置主页留在 settingsOpen 里，返回即回到它）。
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
                    state.promptsOpen -> add(SettingsKey.Prompts)
                    state.themePageOpen -> add(SettingsKey.Theme)
                    state.chatDisplayOpen -> add(SettingsKey.ChatDisplay)
                }
            }
            /*
             * 「关于 / 许可 / 隐私」必须**串在设置分支之后**，不能放在它前面。
             */
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
    //
    // 用户可以在「主题设置 → 显示 → 边缘滑动返回」里关掉它。关掉时传 `null`：
    // `entry(swipeDismiss = null)` 让 Miuix **不挂**那一层手势识别器，
    // 而不是"挂上但不认领" —— 后者仍会参与命中测试，在输入框边缘滑动时会抢事件。
    val swipeBack: NavSwipeDirection? = if (state.themeSettings.edgeSwipeBack) {
        when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            else -> NavSwipeDirection.LeftToRight
        }
    } else {
        null
    }

    // 整页（设置 / 二级页 / UI 调试）底部的反馈条数据源。
    // 那些页各自持有 `SettingsDraft` / `SkillsState` …，都拿不到 WorkspaceUiState，
    // 所以在这里提供一次，页面用 PageFeedbackSlot() 消费（见 ui/PageFeedback.kt）。
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
        transition = NavTransitions.MiuixDefault,
        effects = navEffects,
        onBack = {
            // 系统返回：按当前栈顶逐级回退，并同步关掉 VM 的对应状态。
            // 只有 root（工作区）时把返回交还给系统（退出应用）。
            //
            // 读的是 `viewModel.state.value` 而不是闭包里捕获的 `state`：
            // NavDisplay 会把这份 lambda 一直留着，而捕获进去的 state 是**上一次
            // 组合的快照**，它会在"刚变脏、刚关闭"这类时刻过期。
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
                live.apiConfig != null -> viewModel.closeApiConfig()
                live.mcpConfig != null -> viewModel.closeMcpConfig()
                live.searchServices != null -> viewModel.closeSearchServices()
                live.skills != null -> viewModel.closeSkills()
                live.roleCards != null -> viewModel.closeRoleCards()
                live.memory != null -> viewModel.closeMemory()
                live.promptsOpen -> viewModel.closePrompts()
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
            /*
             * 页面栈**只认接口，不认具体页面**（`ZhiPageSet`）。
             *
             * 这是「一个主题 = 一组 page.kt」的接线点：默认组 `MiuixPageSet`
             * 是空实现、全部回落，所以这一行的行为与之前直接调 `WorkspacePage`
             * 完全一致 —— 换组时只换 `LocalZhiPageSet` 的值，这里不动。
             *
             * ⚠️ 参数里 `isDark` / `wide` / `glass` / `glassMain` 仍然由本层算，
             * 因为它们是**外壳的职责**（窗口尺寸、玻璃捕获节点不能包含自己），
             * 不该让每个主题组各算一遍 —— 那会出现两组玻璃对象互相看不到对方。
             */
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

/**
 * 应用根页。整页栈的 root = 工作区（顶栏/Tab/面板/输入器），
 * 设置与它的二级页作为栈上更深的一层压在上面。
 *
 * 用 [navBackStackOf] 建栈（内存态，不跨进程持久化），所以 key 不需要
 * `@Serializable` —— 栈的真源仍是 ViewModel 状态，这里只是把它渲染出来。
 */
private sealed interface AppKey : NavKey {
    data object Oobe : AppKey
    data object Workspace : AppKey

    /** UI 调试整页（debug 构建的设置页里有入口）。 */
    data object UiDebug : AppKey

    /**
     * 关于页与第三方许可页。
     *
     * <p>放在 [AppKey] 而不是 [SettingsKey]：它们的入口在侧栏也有一个，
     * 从侧栏进来时设置页并没有打开。放进 [SettingsKey] 会让"返回"落到一个
     * 用户从没打开过的设置主页上。
     */
    data object About : AppKey
    data object Licenses : AppKey

    /** 隐私政策。与 [Licenses] 平级：都从关于页进，压在关于之上。 */
    data object Privacy : AppKey

    /**
     * 「检查更新」。
     *
     * <p>与 [Licenses] / [Privacy] 一样是「关于」的子页，所以串在
     * `aboutOpen` 分支里。入口有两个：关于页 Hero 里那个大 ZhiCode 字（常驻），
     * 以及关于页卡片里的「有新版本 x.y.z」那一行（只在真有新版本时出现）。
     */
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

    /**
     * 聊天显示（自定义字体 + 正文字号）。
     *
     * 与 [Theme] 分开而不是并进主题页：主题页回答的是"整个应用长什么样"，
     * 这一页回答的是"聊天正文读起来舒不舒服"。混在一页会让主题页长到要滚三屏，
     * 而两者的改动目的也不同（一个是调色板，一个是字形与字号）。
     */
    data object ChatDisplay : SettingsKey
}

