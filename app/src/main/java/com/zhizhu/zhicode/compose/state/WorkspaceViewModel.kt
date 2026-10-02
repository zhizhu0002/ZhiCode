package com.zhizhu.zhicode.compose.state

import android.net.Uri
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhizhu.zhicode.compose.data.ApiConfigStore
import com.zhizhu.zhicode.compose.data.AttachmentReader
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.data.DebugApiProfile
import com.zhizhu.zhicode.compose.data.FileBrowser
import com.zhizhu.zhicode.compose.data.FileSearch
import com.zhizhu.zhicode.compose.data.GitChanges
import com.zhizhu.zhicode.compose.data.McpStore
import com.zhizhu.zhicode.compose.data.MockWorkspaceRepository
import com.zhizhu.zhicode.compose.data.ModelCatalogStore
import com.zhizhu.zhicode.compose.data.SessionReader
import com.zhizhu.zhicode.compose.data.RoleCardStore
import com.zhizhu.zhicode.compose.data.MemoryStore
import com.zhizhu.zhicode.compose.data.SkillImport
import com.zhizhu.zhicode.compose.data.SkillStore
import com.zhizhu.zhicode.compose.data.WorkspacePaths
import com.zhizhu.zhicode.compose.data.WorkspaceRepository
import com.zhizhu.zhicode.compose.engine.EngineEvents
import com.zhizhu.zhicode.compose.engine.EngineOverrides
import com.zhizhu.zhicode.compose.engine.EnginePlanApproval
import com.zhizhu.zhicode.compose.engine.ZhiEngineController
import com.zhizhu.zhicode.compose.engine.ToolText
import com.zhizhu.zhicode.compose.engine.toEngineEffort
import com.zhizhu.zhicode.compose.engine.toEngineMode
import com.zhizhu.zhicode.compose.engine.toUiPlanApproval
import com.zhizhu.zhicode.compose.runtime.EnvDoctor
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.RuntimeInstaller
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.Attachment
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.ChoiceIntent
import com.zhizhu.zhicode.compose.model.ChoiceOption
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.DiffState
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpServerStatus
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.model.MemoryEditor
import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.ModelProfileTab
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.QueuedPrompt
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.RiskLevel
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.SLASH_COMMANDS
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SettingsCategory
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillDetail
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFileDraft
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillUrlDraft
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.data.SearchServiceStore
import com.zhizhu.zhicode.compose.model.SearchFieldName
import com.zhizhu.zhicode.compose.model.SearchService
import com.zhizhu.zhicode.compose.model.SearchServiceDraft
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.SlashCommand
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.model.WebSearchProvider
import com.zhizhu.zhicode.compose.ui.zhiFormatSize
import com.termux.app.zhicode.core.FileOps
import com.termux.app.zhicode.core.PlanApprovalGate
import com.termux.app.zhicode.core.StorageLinks
import com.termux.app.zhicode.storage.ApiSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面状态机。
 *
 * 数据来自 [WorkspaceRepository]（现在只剩终端占位横幅项）；对话流、工具执行、会话文件
 * 后续换成真实引擎实现即可，UI 层不用改。
 *
 * 继承 [AndroidViewModel] 而不是 `ViewModel`，是为了拿到 `Application`：
 * 内置 Termux 环境的安装与自检都需要 Context。
 * 这样做还有一个实际好处 —— `viewModel()` 的默认工厂本来就支持 `AndroidViewModel`，
 * 所以 `ZhiCodeApp()` 里那行默认参数不用改、也不用自己写 Factory。
 */
class WorkspaceViewModel(
    application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : AndroidViewModel(application), EngineEvents {

    /** 内置 Termux 环境安装器（第一次使用前会解压 32MB 的 bootstrap）。 */
    private val installer = RuntimeInstaller(application)

    /** 真实 蜘蛛 引擎。懒创建，第一次发消息时才初始化。 */
    private val engine = ZhiEngineController(application, this)

    // ------------------------------------------------------------------
    // 运行期状态（属于"这一轮正在发生什么"，不是界面状态，因此不进 StateFlow）
    // ------------------------------------------------------------------

    /** 当前正在流式输出的助手气泡 id。为 null 表示还没有任何 delta。 */
    private var streamingAssistantId: String? = null

    /** 当前回合的工具分组 id。一个工具批次 = 一张分组卡。 */
    private var currentGroupId: String? = null

    /** 引擎给的工具 id → 它落在哪个分组卡里。用于把结果/进度写回正确的位置。 */
    private data class LiveTool(val groupId: String, val name: String, val command: String)

    private val liveTools = mutableMapOf<String, LiveTool>()

    /**
     * 工具实时输出的合并缓冲。
     *
     * `onToolProgress` 的粒度是"每次进程写出一点"，Bash 跑一条 `apt install` 能到几千次。
     * 每次都改 StateFlow 会把界面拖垮，所以按 [PROGRESS_FLUSH_MS] 合并后再落盘。
     */
    private val progressBuffers = mutableMapOf<String, StringBuilder>()
    private val progressLastFlush = mutableMapOf<String, Long>()

    /** 正在等待用户回执的权限请求 id。 */
    private var pendingPermissionId: String? = null

    /**
     * 当前这一轮属于哪个会话（`activeSessionId` 的快照）。
     *
     * 用途只有一个：错误迟到时判断它该不该落到**现在**这个会话里。
     * 用户在等待回复时切走会话，这一轮会被 `engine.cancel()` 打断，但错误回调
     * 仍可能之后到达；没有这个标记就会把错误卡写进新会话（详见 [finishTurnWithError]）。
     *
     * 为 null 表示"当前没有归属待判定的回合"，此时不拦截。
     */
    private var turnSessionId: String? = null

    /**
     * 正在进行的「提问」流程。
     *
     * 引擎的 `AskUserQuestion` 可以一次带多个问题，界面是**分步**展示的，
     * 所以必须把请求 id、问题列表、当前步骤、已收集的答案都留着 ——
     * 只存"当前这一步"的话，走到第二步就丢了前面的答案。
     */
    private class PendingQuestions(
        val requestId: String,
        val questions: JSONArray,
        var index: Int = 0,
        val answers: JSONObject = JSONObject(),
    )

    private var pendingQuestions: PendingQuestions? = null




    /**
     * 会话操作的目标。
     *
     * `showSessionActions(session)` 只负责弹选择器，真正的动作在选择器回调里执行；
     * 那时只能拿到选项文案，拿不到是哪条会话。所以必须把目标记住 ——
     * 否则会出现"想删 A 结果删了当前会话"这类错删。
     */
    private var pendingSessionAction: SessionSummary? = null
    private var pendingMessageAction: ChatItem? = null

    private var modelCatalogJob: Job? = null

    /**
     * 模型选择的落盘任务。
     *
     * 单独一个（而不是共用 `modelCatalogJob`）：两者生命周期完全不同 —— 拉目录是
     * "开面板时一次、切配置时一次"，而选模型是"每点一行一次"，共用一个句柄会让
     * 选模型把正在进行的目录请求取消掉（列表就此停在加载中）。
     */
    private var modelSaveJob: Job? = null

    /**
     * 会话级模型覆盖。
     *
     * 为什么不只写配置记录：选模型与“本轮对话用哪个模型”是两个作用域。
     * 配置记录是跨会话的默认值，而 [modelOverride] 只影响当前这轮 ——
     * 用户临时换一下模型试效果，不应该把他的默认配置也改掉。
     *
     * 所以这里是“两边都写”：持久化那份写进配置记录（下次打开设置能看到），
     * 即时生效靠这个覆盖值再 `engine.configure()`。
     *
     * 切换 API 配置时清空——换了配置就该用那份配置自己的模型。
     */
    private var modelOverride: String? = null

    /**
     * 会话级角色卡覆盖。
     *
     * 与 [modelOverride] 同理：磁盘上的卡片清单与"实际生效内容"是两份数据，
     * 引擎的 SessionConfig 只认后者。空串 ≠ null —— 空串表示"确实停用了"（要覆盖掉
     * 配置里的旧值），null 表示"本轮不覆盖"（会退回旧值，于是停用看起来没生效）。
     */
    private var roleCardOverride: String? = null


    private val _state = MutableStateFlow(
        WorkspaceUiState(
            // 默认项目路径是 $HOME（Termux 约定），不再是开发本应用时用的那个路径。
            // 它由装环境时的 RuntimeInstaller 一并建好，见 [ensureWorkspace]。
            projectName = WorkspacePaths.nameOf(WorkspacePaths.defaultProject()),
            projectPath = WorkspacePaths.defaultProject(),
            // 会话列表 / 对话历史 / 任务清单都来自磁盘，这里先给空值，
            // 由 [initSessionState] 在 IO 线程读好后填上。
            // 不在构造器里同步读盘：会话 JSONL 可能有几千行，会拖慢启动。
            sessions = emptyList(),
            activeSessionId = "",
            transcript = emptyList(),
            tasks = emptyList(),
            contextTokens = 0,
            contextWindow = 200_000,
            deviceStatus = clockLabel(),
            // 变更面板首屏为空；git 是要真实执行命令的，等用户点「刷新」再读。
            diff = DiffState(),
            terminalLines = repo.terminalBanner(WorkspacePaths.defaultProject()),
            // 文件面板走真实文件系统。只列**一层**（不递归）：内置 Termux 环境装好后
            // home 下可能有几千个文件，递归会拖慢启动。
            filePath = WorkspacePaths.defaultProject(),
            fileEntries = emptyList(),
            busySessionIds = emptySet(),
            composerBusy = false,
            workingStatus = null,
            sidebarOpen = false,
            // 真实探测，不再是写死的 true
            runtimeReady = installer.isInstalled(),
        )
    )

    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        initSessionState()
        autoInstallRuntimeIfStale()
    }

    // ------------------------------------------------------------ 终端保活

    /**
     * 终端面板实例。**持有它就是为了跨 Tab 保活**。
     *
     * ## 为什么必须挂在这里
     *
     * 原来的写法是 `TerminalPane` 里 `remember { TermuxTerminalPane(...) }` +
     * `onDispose { pane.closeAll() }`，而 `PaneHost` 用的是 `when(tab) { … }`：
     * 切到别的 Tab，终端这段 Composable 直接离开组合 → `onDispose` 触发 →
     * **`closeAll()` 把每个会话（bash 进程 + PTY fd）都 finish 掉**。
     * 于是"切走再切回来"看到的永远是全新的空终端，之前跑的命令、工作目录全没了。
     *
     * 把实例放到 ViewModel 上就解决了：Composable 进出组合只影响"挂到界面上"这一步，
     * 会话本身一直在。
     *
     * ## 为什么放 ViewModel 不会泄漏 Activity
     *
     * `TermuxTerminalPane` 是 `FrameLayout`，构造时用的是 Activity 的 Context。
     * ViewModel 比 Activity 活得久本来是泄漏的经典形态，但本工程的 MainActivity
     * 声明了 `configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`，
     * **不会因旋转/换主题重建** —— Activity 实例与 ViewModel 同生共死，没有间隙。
     *
     * 真正要释放的时机是 [onCleared]（Activity 真正销毁）：那时必须 `closeAll()`，
     * 否则每开一次应用就漏一批 bash 进程。
     */
    private var terminalPane: TermuxTerminalPane? = null

    /** 取（或首次创建）终端面板实例。同一进程内只会有一个。 */
    fun terminalPane(context: Context): TermuxTerminalPane =
        terminalPane ?: TermuxTerminalPane(context, installer).also { terminalPane = it }

    /**
     * 关掉终端里的所有会话并丢弃实例。
     *
     * 只在两个地方调用：Activity 真正销毁（[onCleared]），以及**重装环境之前** ——
     * 重装会把整个 `usr` 目录 rename 换掉，正在跑的 bash 会指向一个已不存在的前缀。
     */
    fun releaseTerminalPane() {
        val pane = terminalPane ?: return
        terminalPane = null
        // closeAll() 会 finish 每个 session，并释放 WakeLock —— 属于 View 操作，回主线程做。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            pane.closeAll()
        } else {
            Handler(Looper.getMainLooper()).post { pane.closeAll() }
        }
    }

    override fun onCleared() {
        releaseTerminalPane()
        super.onCleared()
    }

    /**
     * 启动时检查内置 Termux 环境是否为**当前 APK 内置的那一版**，不是就自动重装。
     *
     * ## 为什么需要它
     *
     * marker 里一直存着"版本三元组"（版本 / sha256 / 来源），最初就是为这件事写的，
     * 但此前**没有任何地方读它**。后果是：换了内置 bootstrap 的 APK 装上去以后，
     * `usr` 永远停在旧的那份 —— `isInstalled()` 只查文件存不存在，照样返回 true，
     * 于是谁都不会去重装，只能手动卸载重来（会连 HOME 一起丢）。
     *
     * ## 与手动触发的区别
     *
     * 不弹「环境自检」窗口、也不问一句：用户装 APK 的意图就是要用新的那版。
     * 进度照常回灌到状态里，打开环境自检就能看到。
     *
     * ## HOME 不受影响
     *
     * [RuntimeInstaller.install] 只换 `usr`（`activatePrefix` 把旧前缀 rename 到
     * `usr-backup` 再删），`home` 从头到尾没动过 —— 会话历史、项目文件都在。
     * 但**用 apt 装过的包会丢**（它们装在 `usr` 里）。
     *
     * ## 为什么每一步都写日志
     *
     * 这条路径是**静默**的：没有弹窗、失败也只进状态。第一版就是这样被反馈
     * "启动后没有自动重装" 而无法判断到底走到了哪一步。现在每次判定与结果都追加到
     * `$HOME/.zhicode/runtime.log`（终端里 `cat ~/.zhicode/runtime.log` 就能看）。
     */
    private fun autoInstallRuntimeIfStale() {
        // IO 线程：读 marker、比对 sha256、写日志都是磁盘操作
        viewModelScope.launch(Dispatchers.IO) {
            val installed = runCatching { installer.installedVersion() }.getOrNull()
            val bundled = runCatching { installer.bundledVersion() }.getOrNull() ?: "?"
            val upToDate = runCatching { installer.isUpToDate() }.getOrElse { error ->
                appendRuntimeLog("检查版本时抛异常：${error.javaClass.simpleName}: ${error.message}")
                false
            }
            appendRuntimeLog(
                "启动检查：已装=${installed ?: "(无)"} 内置=$bundled 一致=$upToDate"
            )
            if (upToDate) return@launch

            val reason = if (installed == null) "正在初始化内置环境…" else "内置环境已更新，正在升级…"
            // "抢占"必须放在同一次 _state.update 里：启动自动装与用户手动点「初始化」
            // 可能同时到达，分开的 check-then-set 会让两边都跑一遍（第二次会把
            // staging 删掉再重建，前一次的进度全乱）。与 turnSessionId 是同一类竞态。
            var claimed = false
            _state.update { s ->
                if (s.runtimeInstalling) {
                    s
                } else {
                    claimed = true
                    s.copy(runtimeInstalling = true, runtimeProgress = 0, runtimeMessage = reason)
                }
            }
            if (!claimed) {
                appendRuntimeLog("已有安装在进行，本次跳过")
                return@launch
            }
            appendRuntimeLog("开始自动安装（$reason）")
            installRuntimeBlocking(reason)
        }
    }

    /**
     * 把一行诊断写进 `$HOME/.zhicode/runtime.log`。
     *
     * 只保留最后 [RUNTIME_LOG_MAX_LINES] 行：这是个排障用的滚动日志，
     * 不是审计记录，无限增长会把用户的家目录塞满。
     *
     * 绝不抛异常 —— 日志写不进去（磁盘满、权限）不该影响安装本身。
     */
    private fun appendRuntimeLog(line: String) {
        runCatching {
            val dir = TermuxConstants.dataDir()
            dir.mkdirs()
            val file = File(dir, "runtime.log")
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val kept = if (file.isFile) {
                file.readLines().takeLast(RUNTIME_LOG_MAX_LINES - 1)
            } else {
                emptyList()
            }
            file.writeText((kept + "$stamp  $line").joinToString("\n") + "\n")
        }
    }

    /**
     * 首次载入：列出磁盘上的真实会话，有历史就把最近一条打开。
     *
     * 「自动打开最近一条」是刻意保留的行为：否则用户每次启动都看到空对话流，
     * 会以为历史丢了（历史其实在侧栏里，但要自己去点）。
     */
    private fun initSessionState() {
        viewModelScope.launch(Dispatchers.IO) {
            restoreUiSettings()
            // 顶栏的模型名/密钥状态要反映**真实生效**的配置，
            // 否则用户会看到一个跟实际请求无关的模型名。
            syncActiveProfile()
            syncRoleCardFromStore()
            // 共享存储的授权状态要**实测**：文件面板切到「共享存储」时，
            // 没授权只会看到"0 项"，而原因（没给「所有文件访问权限」）必须说出来。
            refreshSharedStoragePermission()
            /*
             * `~/storage` 的六个链接**在每次启动的路径上**补一次。
             *
             * 幂等且只增不删（见 StorageLinks.setup），所以重复调用没有副作用；
             * 而它是"老环境缺这一块"的唯一补救途径 —— 之前只在手动点「修复」时才跑，
             * 结果是早先装好的环境永远没有 ~/storage，用户看到的是 `cd ~/storage/shared`
             * 失败，看不出是"这个版本才加的"。
             *
             * 门控在 isInstalled()：环境还没装出来时 HOME 可能都还不存在，
             * 这时候建链接没有意义（安装流程自己会建）。
             */
            if (runCatching { installer.isInstalled() }.getOrDefault(false)) {
                installer.setupStorageLinks()
            }
            ensureWorkspace()
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { it.copy(sessions = sessions) }
            sessions.firstOrNull()?.let { openSession(it) }
        }
    }

    /**
     * 读回**不在 SessionConfig 里**的那几项界面设置（主题、搜索服务密钥）。
     *
     * 其余设置（权限模式、推理档、上下文窗口、项目目录、联网搜索一整套、自动压缩、
     * 自定义提示词、沙箱全权、Root、保活）都是 [SessionConfig] 的字段，
     * 由引擎侧的持久化表负责，这里不重复。
     *
     * 密钥从**加密槽**读回，与 API 配置的密钥同一套保护。
     */
    private suspend fun restoreUiSettings() = withContext(Dispatchers.IO) {
        val stored = runCatching {
            ApiSettingsStore.getThemeMode(getApplication(), ThemeMode.SYSTEM.name.lowercase())
        }.getOrDefault(ThemeMode.SYSTEM.name.lowercase())
        val theme = ThemeMode.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            ?: ThemeMode.SYSTEM
        val keys = WebSearchProvider.entries
            .filter { it.needsKey }
            .associateWith { provider ->
                runCatching {
                    ApiSettingsStore.getWebSearchKey(getApplication(), provider.name.lowercase())
                }.getOrDefault("")
            }
            .filterValues { it.isNotBlank() }
        _state.update {
            it.copy(
                themeMode = theme,
                settings = it.settings.copy(
                    webSearchKeys = keys,
                    terminalCharMode = runCatching {
                        ApiSettingsStore.getTerminalCharMode(getApplication())
                    }.getOrDefault(false),
                ),
                settingsDraft = it.settingsDraft?.copy(
                    themeMode = theme,
                    webSearchKeys = keys,
                    terminalCharMode = runCatching {
                        ApiSettingsStore.getTerminalCharMode(getApplication())
                    }.getOrDefault(false),
                ),
            )
        }
    }

    /**
     * 确保当前项目目录存在。
     *
     * 两个时机都需要：运行环境刚装好（home 此时才出现）、以及每次启动
     * （环境是上次装的）。**必须在列会话之前调用** —— 会话按项目目录分键存储，
     * 目录不存在时会话会落在一个"幽灵项目"键下，之后换了路径就找不回来了。
     */
    private fun ensureWorkspace() {
        val path = _state.value.projectPath
        // 目录已存在：只需按真实文件系统算出列表与说明。
        if (WorkspacePaths.exists(path)) {
            reloadFiles()
            return
        }
        // 目录不存在，且运行环境还没装（home 目录都还没出现）：
        // 此时**不能**建目录（父目录不存在），但也**不能什么都不说** ——
        // 否则文件面板会显示"0 项"却没有任何解释。
        if (!installer.isInstalled()) {
            _state.update {
                it.copy(
                    fileEntries = emptyList(),
                    fileNote = "工作区还不存在：$path\n初始化内置 Termux 环境后会自动建立。",
                )
            }
            return
        }
        WorkspacePaths.ensure(path)
        reloadFiles()
    }

    /** 刷新文件面板当前目录的列表，并同步 [WorkspaceUiState.fileNote]。 */
    private fun reloadFiles() {
        val path = _state.value.filePath
        val entries = FileBrowser.children(path)
        val dir = java.io.File(path)
        val note = when {
            entries.isNotEmpty() -> ""
            !dir.isDirectory -> if (dir.exists()) "不是目录：$path" else "目录不存在：$path"
            // 目录存在但一个子项都没列出来 —— 区分「真的是空目录」和「没权限列」。
            // 面包屑可以从 `/` 一路点下来，而 `/`、`/data` 这类系统目录在应用沙箱里
            // 是 `drwx--x--x`（other 只有 x 没有 r），列不出来；这时如果只显示
            // 「0 项」，看起来像目录坏了。`File.list()` 返回 null 就代表列取失败。
            dir.list() == null -> "无法读取（权限不足）：$path"
            else -> ""
        }
        _state.update { it.copy(fileEntries = entries, fileNote = note) }
    }

    private var idCounter = 0L

    private fun nextId(prefix: String): String = "$prefix-${++idCounter}"

    // ---------- 输入器 ----------

    // ---------- 任务清单 ----------

    /** 点悬浮任务卡：打开任务详情窗口，查看**全部**任务（卡片只显示当前窗口）。 */
    fun openTaskList() = _state.update { it.copy(taskListOpen = true) }

    fun closeTaskList() = _state.update { it.copy(taskListOpen = false) }

    fun onComposerChange(text: String) {        val query = if (text.startsWith("/") && !text.contains(' ') && !text.contains('\n')) text else null
        _state.update {
            it.copy(
                composerText = text,
                slashQuery = query,
                slashMatches = if (query == null) emptyList()
                else SLASH_COMMANDS.filter { c -> c.name.startsWith(query.lowercase(Locale.US)) },
            )
        }
    }

    fun pickSlashCommand(command: SlashCommand) {
        _state.update {
            it.copy(composerText = command.name + " ", slashQuery = null, slashMatches = emptyList())
        }
    }

    fun dismissSlashPalette() {
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
    }

    /**
     * 附件载荷。
     *
     * **故意不放进 `WorkspaceUiState`**：图片是几 MB 的字节数组，塞进不可变状态里会让
     * 每次 `copy()` 都拖着它，而且 Compose 判断状态是否变化时要对它做 `equals`——
     * 每帧比较几 MB 是纯粹的浪费。界面只需要名字和大小，真正的字节留在这里，
     * 发送时再取。
     *
     * [image] 是 base64（界面显示缩略图用）。**在这里编码一次**而不是每次要用时现编：
     * 一张 10 MB 的图 base64 是 13 MB 的字符串，编码本身要遍历全部字节 ——
     * 缩略图与消息气泡、引擎请求三处都要它，各编一次就是三倍开销。
     * 它也不进 state（同样是不让几 MB 的字符串跟着每次 copy 走），
     * 由 [currentAttachmentImage] 按 id 取。
     */
    private class AttachmentPayload(val bytes: ByteArray?, val mimeType: String, val image: ChatImage?)

    private val attachmentPayloads = mutableMapOf<String, AttachmentPayload>()

    /**
     * 取某个待发附件的图片（给输入器的缩略图用）。
     *
     * 返回值里的大字符串与载荷共享**同一个引用**，不会复制字节；
     * 之所以不做成 `WorkspaceUiState` 的字段，理由见 [AttachmentPayload] 的注释。
     */
    fun currentAttachmentImage(id: String): ChatImage? = attachmentPayloads[id]?.image

    /**
     * 附加一张图片（来自系统选择器）。
     *
     * 读盘与大小校验都在 IO 线程：10 MB 的图片读进来是实打实的耗时。
     */
    fun attachImage(uri: android.net.Uri) = attachImages(listOf(uri))

    /**
     * 一次附加多张图片（多选）。
     *
     * ## 为什么是"全部读完再更新一次状态"而不是每张各更新一次
     *
     * 五张图各发一次 `_state.update` 会让附件条闪四下、每次都重新测量布局
     * （`animateContentSize` 会为每一次变化播一段动画）。攒起来一次性落地，
     * 用户看到的是"五张一起出现"。
     *
     * ## 为什么一张失败不放弃其它张
     *
     * 选五张图时有了一张超大/损坏的，把其余四张一起丢掉是最糟的结果 ——
     * 用户还得重新去相册挑一遍。所以逐张读、逐张记失败原因，
     * **成功的照样进附件条**，失败的在提示里报出来。
     *
     * ## 部分失败为什么只吃一条提示
     *
     * 复用 `message` 那条通道（同一时刻只显示一条），用「N 张失败」汇总而不是
     * 每张各弹一条：连弹五条提示会把真正成功的那几张也淹没掉。
     */
    fun attachImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.map { uri -> AttachmentReader.readImage(getApplication(), uri) }
            }
            val failures = loaded.filter { it.isFailure }
            val added = loaded.mapNotNull { it.getOrNull() }
            if (added.isNotEmpty()) {
                val newAttachments = added.map { image ->
                    val id = nextId("att")
                    // base64 在这里编一次，缩略图 / 气泡 / 引擎请求三处复用同一份。
                    attachmentPayloads[id] = AttachmentPayload(
                        bytes = image.bytes,
                        mimeType = image.mimeType,
                        image = ChatImage(
                            data = android.util.Base64.encodeToString(image.bytes, android.util.Base64.NO_WRAP),
                            mimeType = image.mimeType,
                            name = image.name,
                        ),
                    )
                    Attachment(
                        id = id,
                        label = image.name,
                        // 「PNG · 2.4 MB」两个信息都来自真实读数，不是写死的文案。
                        detail = "${image.formatLabel} · ${image.sizeLabel}",
                        isImage = true,
                    )
                }
                _state.update { s ->
                    s.copy(
                        attachments = s.attachments + newAttachments,
                        message = when {
                            failures.isEmpty() && added.size == 1 -> "已添加图片：${added[0].name}"
                            failures.isEmpty() -> "已添加 ${added.size} 张图片"
                            // 有失败时提示必须偏错误色：否则"已添加 4 张"配一个中性色，
                            // 用户根本不会注意到自己选的第 5 张没进来。
                            else -> "已添加 ${added.size} 张，${failures.size} 张失败：" +
                                (failures[0].exceptionOrNull()?.message ?: "未知原因")
                        },
                        messageIsError = failures.isNotEmpty(),
                    )
                }
            } else {
                val reason = failures[0].exceptionOrNull()?.message ?: "未知原因"
                _state.update {
                    it.copy(
                        message = if (failures.size == 1) "图片读取失败：$reason"
                        else "${failures.size} 张图片都读取失败：$reason",
                        messageIsError = true,
                    )
                }
            }
        }
    }

    /**
     * 把待发图片编成引擎要的内容块。
     *
     * 结构对齐原版 `buildImageBlocks()`：`{type:image, source:{type:base64, media_type, data}, name}`。
     * 是否真的发给模型由引擎按 `visionEnabled` 决定（`VisionMessageFilter`），
     * 这里不重复判断——两处都判会出现"界面拦了但引擎其实允许"这类不一致。
     */
    private fun buildImageBlocks(): JSONArray {
        val blocks = JSONArray()
        _state.value.attachments.filter { it.isImage }.forEach { attachment ->
            val payload = attachmentPayloads[attachment.id] ?: return@forEach
            val encoded = payload.image?.data ?: return@forEach
            if (encoded.isEmpty()) return@forEach
            runCatching {
                blocks.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", payload.mimeType)
                                .put("data", encoded),
                        )
                        .put("name", attachment.label),
                )
            }
        }
        return blocks
    }

    /**
     * 把待发图片编成**界面模型**（挂到用户气泡上）。
     *
     * 与 [buildImageBlocks] 读的是同一份 `attachmentPayloads`，但两者不能合并：
     *
     * - [buildImageBlocks] 给**引擎**，结构必须是 Anthropic 的 `source.base64`；
     * - 这里给**界面**，只要 base64 字符串本身。
     *
     * 合并成一个函数再各取所需，就得让界面层认识 `JSONObject` 结构；分开反而是
     * 两个都很小的纯函数。唯一要守住的是**调用时机**：必须在 `clearAttachments()`
     * **之前**调用，两处调用点都写了这条注释。
     */
    private fun currentImages(): List<ChatImage> = _state.value.attachments
        .filter { it.isImage }
        .mapNotNull { currentAttachmentImage(it.id) }

    /** 清空附件：界面条目与载荷必须一起走，否则载荷留在 map 里没人回收。 */
    private fun clearAttachments() {
        attachmentPayloads.clear()
        _state.update { it.copy(attachments = emptyList()) }
    }

    /**
     * 把待发的**文本**附件拼进提示词。
     *
     * 结构照抄原版 `buildPromptWithAttachments`：包成 `<attached_context>` 块，
     * 每个附件一个 `<attachment name="...">`。用 XML 风格的包裹是有意的——
     * 模型对"这段是附带资料、不是用户指令"的分界需要明确标记，
     * 直接把技能内容贴在提示词后面会让它读成用户要求。
     *
     * 只有存在文本附件时才加包裹层；一个都没有就原样返回，
     * 避免每条消息都拖着空标签。
     */
    private fun buildPromptWithTextAttachments(prompt: String): String {
        val texts = _state.value.attachments.filter { !it.isImage && it.textBody != null }
        if (texts.isEmpty()) return prompt
        return buildString {
            append(prompt).append("\n\n<attached_context>")
            texts.forEach { attachment ->
                append("\n<attachment name=\"").append(attachment.label.replace("\"", "'")).append("\">\n")
                val body = attachment.textBody.orEmpty()
                append(if (body.length > TEXT_ATTACHMENT_LIMIT) body.take(TEXT_ATTACHMENT_LIMIT) + "\n[…attachment truncated…]" else body)
                append("\n</attachment>")
            }
            append("\n</attached_context>")
        }
    }

    // ---------- Skill ----------

    /**
     * 导入 SKILL.md 的体积上限。
     *
     * 256 KB 对 SKILL.md 来说已经很宽松（真实技能通常几 KB），但足以挡住
     * "用户误选了一个几十兆的文件、`readText()` 把内存吃光"这条路径。
     */
    private val SKILL_IMPORT_LIMIT_BYTES = 256 * 1024

    fun openSkills() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        _state.update {
            // 子页永远坐在设置主页之上（rikkahub 的页面栈）：从侧栏入口进来时
            // 也把 hub 带起来，否则返回时子页关掉就直接回工作区，层断了。
            it.copy(
                skills = SkillsState(skills = SkillStore.list(it.projectPath)),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
    }

    fun closeSkills() = _state.update { it.copy(skills = null) }

    /** 列表页的筛选词。 */
    fun setSkillQuery(query: String) = _state.update {
        it.copy(skills = it.skills?.copy(query = query))
    }

    /**
     * 打开技能详情。
     *
     * 详情页同时承担两件事：看这个技能有哪些文件、以及从这里进编辑。
     * 文件列表每次进入都重读 —— 用户可能刚用 Agent 往目录里加了参考文档。
     */
    fun openSkillDetail(entry: SkillEntry) {
        val s = _state.value
        _state.update {
            it.copy(
                skills = it.skills?.copy(
                    createForm = null,
                    editing = null,
                    fileDraft = null,
                    urlDraft = null,
                    detail = SkillDetail(
                        entry = entry,
                        tree = SkillStore.listTree(s.projectPath, entry.scope, entry.name),
                    ),
                ),
            )
        }
    }

    fun closeSkillDetail() = _state.update {
        it.copy(skills = it.skills?.copy(detail = null))
    }

    /** 打开「新建文件」对话框。 */
    fun newSkillFile() = _state.update {
        it.copy(skills = it.skills?.copy(fileDraft = SkillFileDraft()))
    }

    fun updateSkillFileDraft(transform: (SkillFileDraft) -> SkillFileDraft) = _state.update {
        val skills = it.skills ?: return@update it
        val draft = skills.fileDraft ?: return@update it
        it.copy(skills = skills.copy(fileDraft = transform(draft)))
    }

    fun cancelSkillFileDraft() = _state.update {
        it.copy(skills = it.skills?.copy(fileDraft = null))
    }

    /**
     * 在技能目录里新建一个附加文件。
     *
     * 重名**直接拒绝**而不是覆盖：这里建的是参考文档、脚本这类手写内容，
     * 覆盖等于丢数据。技能本体（SKILL.md）也不允许用这个入口建 ——
     * 那是 [createSkill] 的事，而且空内容过不了名字一致性校验，
     * 所以 `SkillFileDraft.nameError` 在输入阶段就把它挡掉了。
     *
     * 文件名允许带子目录（`reference/api.md`）：技能本来就支持分组，
     * 界面不给出这个入口的话，树只能显示别处建出来的目录。
     */
    fun saveSkillFile() {
        val s = _state.value
        val skills = s.skills ?: return
        val draft = skills.fileDraft ?: return
        val detail = skills.detail ?: return
        val name = draft.fileName.trim()
        if (!draft.saveable) return
        // ⚠️ 落盘与重列目录都**不在主线程**上做（与 `initSessionState` 同一约定）。
        //
        // 用户点「创建」时这一下要写一整份草稿正文，写完还要重新 walk 一遍技能目录；
        // 技能文件是手写的参考文档，几百 KB 很常见 —— 在 Android 8 那类慢闪存设备上
        // 这就是一次肉眼可见的卡顿（点击反馈被冻住）。
        //
        // 存在性检查与写入必须在**同一个** IO 块里连着做：拆成两块就多出一个
        // 「检查过了但还没写」的窗口，双击能建出两份。
        viewModelScope.launch(Dispatchers.IO) {
            if (SkillStore.fileExists(s.projectPath, detail.entry.scope, detail.entry.name, name)) {
                _state.update { it.copy(message = "文件 $name 已存在", messageIsError = true) }
                return@launch
            }
            val result = SkillStore.writeFile(
                s.projectPath, detail.entry.scope, detail.entry.name, name, draft.content,
            )
            _state.update { state ->
                if (result.isFailure) {
                    state.copy(
                        message = "新建失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                        messageIsError = true,
                        skills = skills.copy(fileDraft = null),
                    )
                } else {
                    state.copy(
                        message = "已创建 $name",
                        skills = skills.copy(
                            fileDraft = null,
                            detail = detail.copy(
                                tree = SkillStore.listTree(state.projectPath, detail.entry.scope, detail.entry.name),
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 删除技能目录里的一个附加文件。
     *
     * `SKILL.md` 由 `SkillStore.deleteFile` 拒绝（它是技能本体，删掉会留下一个
     * 列表里看得见、点不开的幽灵技能），所以界面不额外挡一遍 —— 一处校验就够，
     * 两处会分叉。
     */
    fun deleteSkillFile(relativePath: String) {
        val s = _state.value
        val detail = s.skills?.detail ?: return
        // 删除本身是元数据操作，但它后面**紧接着**要重列整棵文件树（walk 目录 + 逐文件取大小），
        // 那一步才是真花钱的地方 —— 所以两块一起放到 IO 线程，别只挪一半。
        viewModelScope.launch(Dispatchers.IO) {
            val result = SkillStore.deleteFile(s.projectPath, detail.entry.scope, detail.entry.name, relativePath)
            _state.update { state ->
                state.copy(
                    skills = state.skills?.copy(
                        detail = detail.copy(
                            tree = SkillStore.listTree(state.projectPath, detail.entry.scope, detail.entry.name),
                        ),
                    ),
                    message = if (result.isSuccess) "已删除 $relativePath"
                    else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
        }
    }

    /**
     * 打开发起「手动添加」对话框。
     *
     * 表单**预填一份示例 SKILL.md**（见 `SkillStore.starterTemplate`）：用户改一改就能存，
     * 而不是面对一个空框猜 frontmatter 要写什么。里面的名字是占位符、过不了校验，
     * 所以「保存」一开始是禁用的 —— 必须先换成自己的名字，
     * 否则点两下就会建出一个没打算建的技能。
     *
     * 名字照常由内容解析得出（与 [updateSkillCreateDraft] 同一条规则），
     * 不在这里单独存一份：两份来源必然会在用户改内容时不一致。
     */
    fun newSkill() {
        val content = SkillStore.starterTemplate
        _state.update {
            it.copy(
                skills = it.skills?.copy(
                    createForm = SkillCreateDraft(
                        content = content,
                        name = SkillStore.nameFromContent(content).orEmpty(),
                    ),
                    detail = null,
                    editing = null,
                ),
            )
        }
    }

    fun updateSkillCreateDraft(transform: (SkillCreateDraft) -> SkillCreateDraft) = _state.update {
        val skills = it.skills ?: return@update it
        val form = skills.createForm ?: return@update it
        val next = transform(form)
        // 名字始终由内容**重新解析**，不单独存一份用户输入：
        // 两份来源会在用户改内容时不一致，而目录名只看这一份。
        it.copy(
            skills = skills.copy(
                createForm = next.copy(name = SkillStore.nameFromContent(next.content).orEmpty()),
            ),
        )
    }

    fun cancelSkillCreate() = _state.update {
        it.copy(skills = it.skills?.copy(createForm = null))
    }

    /**
     * 手动添加：把表单内容写进以 frontmatter 里的 `name` 命名的新目录。
     *
     * 名字解析不出来时**不提交**（`saveable` 为假）—— 猜一个目录名会建出
     * 用户没打算建的东西。同名目录不覆盖，直接打开现有内容（`SkillStore.create`
     * 的返回值即此语义）。
     *
     * ⚠️ 创建后只推**一层**页面（详情），**不能**同时设 `editing`：
     * 那样页面栈会一次加两页（列表→详情→编辑器），视觉上就是"出现一个重复的"
     * （用户报过的现象）。要改内容在详情页点 SKILL.md 即可。
     */
    fun createSkill() {
        val s = _state.value
        val form = s.skills?.createForm ?: return
        if (!form.saveable) return
        val name = form.name.trim()
        val result = SkillStore.create(s.projectPath, form.scope, name, form.content)
        result.fold(
            onSuccess = { created ->
                _state.update { state ->
                    val entry = SkillStore.list(state.projectPath)
                        .firstOrNull { it.name == name && it.scope == form.scope }
                        ?: SkillEntry(
                            name = name,
                            scope = form.scope,
                            path = SkillStore.fileOf(state.projectPath, form.scope, name).absolutePath,
                            summary = "未写说明",
                            sizeLabel = "",
                        )
                    state.copy(
                        skills = SkillsState(
                            skills = SkillStore.list(state.projectPath),
                            detail = SkillDetail(
                                entry = entry,
                                tree = SkillStore.listTree(state.projectPath, form.scope, name),
                            ),
                            // 刻意不设 editing：见函数开头那条警告。
                        ),
                        message = if (created) "已创建技能 $name" else "技能 $name 已存在，已为你打开",
                    )
                }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "创建失败：${error.message ?: "未知原因"}", messageIsError = true) }
            },
        )
    }

    /**
     * 从本机文件导入一份 SKILL.md。
     *
     * 读完**不直接建技能**，而是填进「手动添加」表单让用户确认：名字解析出来对不对、
     * 作用域选哪个，都还要用户看一眼。直接建会绕掉这两个决定。
     *
     * 加了一道体积上限：`readText()` 对超大文件会直接把内存吃光（用户完全可能误选一个
     * 几十兆的文件），而 SKILL.md 本身只有几 KB。
     */
    fun importSkillFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(SKILL_IMPORT_LIMIT_BYTES + 1)
                    var read = 0
                    while (read < buffer.size) {
                        val step = input.read(buffer, read, buffer.size - read)
                        if (step <= 0) break
                        read += step
                    }
                    if (read > SKILL_IMPORT_LIMIT_BYTES) {
                        throw IllegalStateException("文件太大（上限 ${SKILL_IMPORT_LIMIT_BYTES / 1024} KB）")
                    }
                    String(buffer, 0, read, Charsets.UTF_8)
                } ?: throw IllegalStateException("读不到这个文件")
            }
            result.fold(
                onSuccess = { text ->
                    // 读进来直接进「手动添加」对话框，名字照常由内容解析（不另外预填字段）：
                    // 表单只有一个内容框，与参考实现一致。
                    val name = SkillStore.nameFromContent(text).orEmpty()
                    _state.update {
                        it.copy(
                            skills = it.skills?.copy(
                                detail = null,
                                editing = null,
                                urlDraft = null,
                                createForm = SkillCreateDraft(content = text, name = name),
                            ),
                            message = if (name.isBlank()) {
                                "已读取文件，但 frontmatter 里没有 name 字段"
                            } else {
                                "已读取文件，确认后点「保存」"
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(message = "读取失败：${error.message ?: "未知原因"}", messageIsError = true)
                    }
                },
            )
        }
    }

    /**
     * 打开「从 URL 导入」对话框。
     */
    fun newSkillUrl() = _state.update {
        it.copy(skills = it.skills?.copy(urlDraft = SkillUrlDraft(), editing = null, createForm = null))
    }

    fun updateSkillUrlDraft(transform: (SkillUrlDraft) -> SkillUrlDraft) = _state.update {
        val skills = it.skills ?: return@update it
        val draft = skills.urlDraft ?: return@update it
        it.copy(skills = skills.copy(urlDraft = transform(draft)))
    }

    fun cancelSkillUrl() = _state.update {
        it.copy(skills = it.skills?.copy(urlDraft = null))
    }

    /**
     * 从 URL 导入。
     *
     * 两条分支（见 `SkillImport`）：
     * - **zip** → 把里面每个 `SKILL.md` 解开逐个建技能（子目录也认），一次可以导入一整包；
     * - **文本** → 不直接建，而是填进「手动添加」对话框让用户确认名字与作用域。
     *
     * 下载在 IO 线程，期间把表单标成 `loading` 并禁用按钮：不标的话用户会以为没反应，
     * 连点几下就是几次并发下载。
     *
     * 所有失败原因都如实上报（不是 http(s) / HTTP 非 200 / 太大 / 既不是文本也不是 zip），
     * 因为这四种的下一步动作完全不同。
     */
    fun importSkillFromUrl() {
        val s = _state.value
        val draft = s.skills?.urlDraft ?: return
        if (!draft.ready) return
        _state.update { it.copy(skills = it.skills?.copy(urlDraft = draft.copy(loading = true))) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SkillImport.download(draft.url) }
            result.fold(
                onSuccess = { bytes ->
                    when {
                        SkillImport.looksLikeZip(bytes) -> {
                            val parsed = runCatching { SkillImport.skillsFromZip(bytes) }
                            parsed.fold(
                                onSuccess = { imported -> createImportedSkills(imported, draft.scope) },
                                onFailure = { error -> failSkillUrl(error) },
                            )
                        }

                        SkillImport.looksLikeText(bytes) -> {
                            // 文本：进「手动添加」让用户看一眼名字与作用域再落盘。
                            val text = bytes.toString(Charsets.UTF_8)
                            val name = SkillStore.nameFromContent(text).orEmpty()
                            _state.update { state ->
                                state.copy(
                                    skills = state.skills?.copy(
                                        urlDraft = null,
                                        createForm = SkillCreateDraft(
                                            content = text,
                                            scope = draft.scope,
                                            name = name,
                                        ),
                                    ),
                                    message = if (name.isBlank()) {
                                        "已下载，但 frontmatter 里没有 name 字段"
                                    } else {
                                        "已下载，确认后点「保存」"
                                    },
                                )
                            }
                        }

                        else -> _state.update { state ->
                            state.copy(
                                skills = state.skills?.copy(urlDraft = draft.copy(loading = false)),
                                message = "这个地址既不是文本也不是 zip 压缩包",
                                messageIsError = true,
                            )
                        }
                    }
                },
                onFailure = { error -> failSkillUrl(error) },
            )
        }
    }

    private fun failSkillUrl(error: Throwable) = _state.update {
        it.copy(
            skills = it.skills?.copy(urlDraft = it.skills.urlDraft?.copy(loading = false)),
            message = "导入失败：${error.message ?: "未知原因"}",
            messageIsError = true,
        )
    }

    /**
     * 把 zip 里解出来的技能逐个落盘。
     *
     * 同名**不覆盖**（`SkillStore.create` 的既有语义）：用户可能已经在本地改过那份，
     * 静默覆盖等于丢数据。结果里分别报"新建了几个 / 已存在几个"，
     * 而不是笼统说一句"导入完成"——用户要能一眼看出哪个没进来。
     */
    private fun createImportedSkills(imported: List<SkillImport.ImportedSkill>, scope: SkillScope) {
        val path = _state.value.projectPath
        var created = 0
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        imported.distinctBy { it.name }.forEach { skill ->
            SkillStore.create(path, scope, skill.name, skill.content).fold(
                onSuccess = { isNew -> if (isNew) created++ else skipped += skill.name },
                onFailure = { failed += skill.name },
            )
        }
        _state.update { state ->
            state.copy(
                skills = state.skills?.copy(
                    urlDraft = null,
                    skills = SkillStore.list(state.projectPath),
                ),
                message = buildString {
                    append("已导入 $created 个技能")
                    if (skipped.isNotEmpty()) append("，${skipped.size} 个已存在（未覆盖）")
                    if (failed.isNotEmpty()) append("，${failed.size} 个失败：${failed.joinToString("、")}")
                },
                messageIsError = failed.isNotEmpty(),
            )
        }
    }

    /**
     * 打开某个技能文件的编辑器（对话框）。
     *
     * [relativePath] 默认 `SKILL.md`（列表页的快捷「编辑」直接进本体）；
     * 详情页里点某个附加文件时传那条相对路径（可能是 `reference/api.md`）。
     *
     * 还没进过详情时顺手把详情也置上：详情页是编辑对话框的**宿主页**
     * （浮层挂在它的 Scaffold 里，见 `SettingsSubPage` 的 overlay 参数），
     * 不置的话对话框没有宿主、点了也不会出现。
     */
    fun editSkill(entry: SkillEntry, relativePath: String = "SKILL.md") {
        val s = _state.value
        // 打开编辑器要**先把整个文件读进内存**（正文直接进 `SkillEditTarget.body`），
        // 这几百 KB 的读 + 文件名一致性校验都不该压在点击那一帧上。
        viewModelScope.launch(Dispatchers.IO) {
            val body = SkillStore.readFile(s.projectPath, entry.scope, entry.name, relativePath).getOrElse { error ->
                _state.update { it.copy(message = "打开失败：${error.message ?: "未知原因"}", messageIsError = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    skills = it.skills?.copy(
                        createForm = null,
                        urlDraft = null,
                        detail = it.skills.detail ?: SkillDetail(
                            entry = entry,
                            tree = SkillStore.listTree(s.projectPath, entry.scope, entry.name),
                        ),
                        editing = SkillEditTarget(
                            name = entry.name,
                            scope = entry.scope,
                            relativePath = relativePath,
                            body = body,
                            nameError = nameErrorFor(relativePath, entry.name, body),
                        ),
                    ),
                )
            }
        }
    }

    /** 只有 `SKILL.md` 有名字一致性约束；附加文件与目录名无关。 */
    private fun nameErrorFor(relativePath: String, skillName: String, body: String): String? =
        if (relativePath == "SKILL.md") SkillStore.nameConflict(skillName, body) else null

    fun updateSkillBody(body: String) = _state.update {
        val skills = it.skills ?: return@update it
        val editing = skills.editing ?: return@update it
        it.copy(
            skills = skills.copy(
                // 每次改动都重算名字冲突：对话框要能**实时**告诉用户"name 对不上"，
                // 而不是等他点了保存再报错（那时内容可能已经写了几百行）。
                editing = editing.copy(
                    body = body,
                    nameError = nameErrorFor(editing.relativePath, editing.name, body),
                ),
            ),
        )
    }

    fun cancelSkillEdit() = _state.update {
        it.copy(skills = it.skills?.copy(editing = null))
    }

    /**
     * 保存正在编辑的文件。
     *
     * 保存后**回到原来那一页**（详情页还在就回详情页并刷新文件树），
     * 而不是一路弹回列表 —— 那样用户想接着改第二个文件就得重新点进去。
     *
     * ⚠️ 失败时**不关对话框**：`SKILL.md` 的 `name` 与目录名不一致是这里最容易
     * 踩到的错误（`SkillStore.writeFile` 会拒绝），关掉对话框等于让用户重新输入一遍。
     * 保持打开、把原因显示出来，用户改一行就能再存。
     */
    fun saveSkill() {
        val s = _state.value
        val editing = s.skills?.editing ?: return
        if (!editing.saveable) return
        // 与 saveSkillFile 同理：写的是一整份正文 + 写完重列文件树。
        viewModelScope.launch(Dispatchers.IO) {
            val result = SkillStore.writeFile(
                s.projectPath, editing.scope, editing.name, editing.relativePath, editing.body,
            )
            _state.update { state ->
                val detail = state.skills?.detail
                state.copy(
                    skills = state.skills?.copy(
                        editing = if (result.isSuccess) null else editing,
                        detail = detail?.copy(
                            tree = SkillStore.listTree(state.projectPath, detail.entry.scope, detail.entry.name),
                        ),
                    ),
                    message = if (result.isSuccess) "${editing.relativePath} 已保存"
                    else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                    messageIsError = result.isFailure,
                )
            }
        }
    }

    fun deleteSkill(entry: SkillEntry) {
        val result = SkillStore.delete(_state.value.projectPath, entry.scope, entry.name)
        _state.update {
            it.copy(
                // 重新构造整个 SkillsState：正在浏览/编辑的若就是被删的那个，
                // 那些页必须一并收掉，否则界面会停在一个已经不存在的技能上。
                // 只保留列表页的筛选词。
                skills = SkillsState(
                    skills = SkillStore.list(it.projectPath),
                    query = it.skills?.query.orEmpty(),
                ),
                message = if (result.isSuccess) "已删除技能 ${entry.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * 把技能附加到下一步任务。
     *
     * 走**文本附件**路径而不是直接塞进输入框：输入框是用户的编辑区，
     * 往里面灌几百行技能内容会让人没法继续写自己的话。
     * 附加后内容由 [buildPromptWithTextAttachments] 在发送时包进 `<attached_context>`。
     */
    fun attachSkill(entry: SkillEntry) {
        val body = SkillStore.read(_state.value.projectPath, entry.scope, entry.name).getOrElse { error ->
            _state.update { it.copy(message = "附加失败：${error.message ?: "未知原因"}", messageIsError = true) }
            return
        }
        val id = nextId("skill")
        _state.update { s ->
            // 同名技能只留一条：连着点两次「附加」应该还是那一份，而不是叠两份进提示词。
            val kept = s.attachments.filterNot { it.label == "skill:${entry.name}" }
            s.copy(
                attachments = kept + Attachment(
                    id = id,
                    label = "skill:${entry.name}",
                    detail = "${entry.scope.label} · ${entry.sizeLabel}",
                    isImage = false,
                    textBody = body,
                ),
                message = "已附加技能：${entry.name}",
            )
        }
    }

    // ---------- 角色卡 ----------

    /**
     * 打开角色卡管理器。
     *
     * 角色卡与技能的区别值得说清楚：技能是"按需加载的资料"（Agent 主动调用
     * `Skill` 工具，或用户手动附加一次），角色卡是**长期生效的人设指令**——
     * 引擎把启用中的内容作为 `<role_card>` 块拼进每一次系统提示词。
     */
    fun openRoleCards() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        val context = getApplication<android.app.Application>()
        _state.update {
            // 同 openSkills：侧栏入口也要把设置主页垫在底下，保证返回层级完整。
            it.copy(
                roleCards = RoleCardsState(
                    cards = RoleCardStore.list(context),
                    activeId = RoleCardStore.activeId(context),
                ),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
    }

    fun closeRoleCards() = _state.update { it.copy(roleCards = null) }

    fun newRoleCard() = _state.update {
        it.copy(roleCards = it.roleCards?.copy(editor = RoleCardEditor()))
    }

    fun editRoleCard(card: RoleCard) = _state.update {
        it.copy(
            roleCards = it.roleCards?.copy(
                editor = RoleCardEditor(id = card.id, name = card.name, content = card.content),
            ),
        )
    }

    fun updateRoleCardDraft(transform: (RoleCardEditor) -> RoleCardEditor) = _state.update {
        val state = it.roleCards ?: return@update it
        val editor = state.editor ?: return@update it
        it.copy(roleCards = state.copy(editor = transform(editor)))
    }

    fun cancelRoleCardEditor() = _state.update {
        it.copy(roleCards = it.roleCards?.copy(editor = null))
    }

    /**
     * 保存角色卡并**立即启用**。
     *
     * 与原版一致：保存一张卡就意味着"我现在要用它"，所以同时写清单、写 activeId、
     * 并把内容灌进引擎配置。分三步做是必须的——只写清单不写 activeId 的话，
     * 用户会看到卡保存成功却没有任何效果。
     */
    fun saveRoleCard() {
        val editor = _state.value.roleCards?.editor ?: return
        if (!editor.saveable) return
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.upsert(
            context,
            RoleCard(id = editor.id, name = editor.name.trim(), content = editor.content),
        )
        result.fold(
            onSuccess = { saved ->
                roleCardOverride = saved.content
                refreshRoleCards(message = "角色卡「${saved.name}」已保存并启用")
                runCatching { engine.configure(engineOverrides()) }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "保存失败：${error.message ?: "未知原因"}", messageIsError = true) }
            },
        )
    }

    /** 启用一张已存在的卡。 */
    fun selectRoleCard(card: RoleCard) {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), card.id)
        if (result.isFailure) {
            _state.update { it.copy(message = "启用失败：${result.exceptionOrNull()?.message ?: "未知原因"}", messageIsError = true) }
            return
        }
        roleCardOverride = card.content
        refreshRoleCards(message = "已启用角色卡：${card.name}")
        runCatching { engine.configure(engineOverrides()) }
    }

    fun disableRoleCard() {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), "")
        if (result.isFailure) {
            _state.update { it.copy(message = "停用失败：${result.exceptionOrNull()?.message ?: "未知原因"}", messageIsError = true) }
            return
        }
        // 覆盖值置成空串（而不是 null）：null 表示"不覆盖"，那会退回配置里的旧值，
        // 于是"停用"看起来没生效——角色卡还挂在系统提示词里。
        roleCardOverride = ""
        refreshRoleCards(message = "角色卡已停用")
        runCatching { engine.configure(engineOverrides()) }
    }

    fun deleteRoleCard(card: RoleCard) {
        val context = getApplication<android.app.Application>()
        val wasActive = _state.value.roleCards?.activeId == card.id
        val result = RoleCardStore.delete(context, card.id)
        if (result.isFailure) {
            _state.update { it.copy(message = "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}", messageIsError = true) }
            return
        }
        if (wasActive) {
            roleCardOverride = ""
            runCatching { engine.configure(engineOverrides()) }
        }
        refreshRoleCards(message = "已删除角色卡：${card.name}")
    }

    private fun refreshRoleCards(message: String) {
        val context = getApplication<android.app.Application>()
        _state.update {
            it.copy(
                roleCards = RoleCardsState(
                    cards = RoleCardStore.list(context),
                    activeId = RoleCardStore.activeId(context),
                ),
                message = message,
            )
        }
    }

    /**
     * 把磁盘上启用中的角色卡内容读进会话覆盖值。
     *
     * 启动时调用一次：`SessionConfig.load()` 不带 roleCard（引擎只按 `applyProfile`
     * 填协议/地址/模型），所以不主动读的话，重启后角色卡在列表里显示"已启用"
     * 却不会被注入请求——正是那种"看起来配好了其实没生效"的状态。
     */
    private fun syncRoleCardFromStore() {
        val content = runCatching { RoleCardStore.activeContent(getApplication()) }.getOrDefault("")
        // 空串表示"确实没有启用的卡"，也要覆盖掉配置里的旧值，所以这里不能判空就跳过。
        roleCardOverride = content
    }

    // ---------- 记忆文件（ZhiCode.md） ----------

    fun openMemory() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        // 同 openSkills：侧栏入口也把设置主页垫在底下。
        _state.update {
            it.copy(
                memory = MemoryState(files = MemoryStore.list(it.projectPath)),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
    }

    fun closeMemory() = _state.update { it.copy(memory = null) }

    /**
     * 打开 ZhiCode 沙箱管理界面（`SandboxBoard`）。
     *
     * 与「环境弹窗」不同，这里**不能**只改本进程的 UI 状态：沙箱引擎整个跑在
     * `:zhisandbox` 进程里，管理界面通过 `${applicationId}.sandbox.control` 这个同 UID
     * 私有 provider 与它通信（见 SandboxRpcService）。所以这里必须真的
     * 启动那个 Activity，而不是弹一个本地的 Compose 面板 —— 后者会得到一个
     * "永远连不上引擎"的空壳界面。
     *
     * ViewModel 手里只有 Application 上下文，从 Application 启动 Activity
     * 必须带 FLAG_ACTIVITY_NEW_TASK，否则直接抛 AndroidRuntimeException。
     * 失败时如实上报，不静默吞掉（例如清单里少声明了该 Activity）。
     */
    fun openSandbox() {
        // 见 hideSidebarForNavigation。这一行尤其明显：沙箱是个**跨进程的 Activity**，
        // 不收侧栏的话用户回到本应用时看到的还是那层侧栏，会以为"点了根本没打开"。
        hideSidebarForNavigation()
        val context = getApplication<android.app.Application>()
        val started = runCatching {
            context.startActivity(
                android.content.Intent(context, com.zhizhu.zhicode.sandbox.SandboxBoard::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        started.onFailure { error ->
            appendInfo(
                "无法打开 ZhiCode 沙箱",
                "启动沙箱管理界面失败：${error.javaClass.simpleName}: ${error.message ?: "未知原因"}\n\n" +
                    "沙箱引擎运行在 :zhisandbox 进程，界面必须由系统拉起而无法在本进程内绘制。",
            )
        }
    }


    /** 打开某个记忆文件编辑。文件不存在时给一份空编辑器（即"新建"）。 */
    fun editMemory(file: MemoryFile) {
        val path = _state.value.projectPath
        val body = MemoryStore.read(path, file.scope).getOrElse { error ->
            _state.update { it.copy(message = "打开失败：${error.message ?: "未知原因"}", messageIsError = true) }
            return
        }
        _state.update {
            it.copy(
                memory = it.memory?.copy(
                    editing = MemoryEditor(scope = file.scope, path = file.path, body = body, exists = file.exists),
                ),
            )
        }
    }

    fun updateMemoryBody(body: String) = _state.update {
        val memory = it.memory ?: return@update it
        val editing = memory.editing ?: return@update it
        it.copy(memory = memory.copy(editing = editing.copy(body = body)))
    }

    fun cancelMemoryEdit() = _state.update {
        it.copy(memory = it.memory?.copy(editing = null))
    }

    fun saveMemory() {
        val state = _state.value
        val editing = state.memory?.editing ?: return
        val result = MemoryStore.save(state.projectPath, editing.scope, editing.body)
        _state.update {
            it.copy(
                memory = MemoryState(files = MemoryStore.list(it.projectPath)),
                message = if (result.isSuccess) "${editing.scope.label} 已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * `/init`：让 Agent 直接创建或完善项目 ZhiCode.md。
     *
     * 指令照抄原版：明确要求**不要**进计划模式、不建任务、不起子 Agent、不做大范围调研，
     * 只读现有的 ZhiCode.md / CLAUDE.md / README 与主构建清单，然后直接写 ZhiCode.md。
     *
     * 两个细节都保留：
     * - 先退出计划模式。计划模式下引擎只产出计划不落盘，`/init` 会变成"只给我一份计划"，
     *   而那显然不是用户想要的。
     * - 对话流里放一条可见的 `/init` 用户气泡：这条请求是应用自己发的，
     *   不显示出来的话用户会看到一条自己没写过的消息引发的回复。
     */
    /** 界面入口：记忆面板里的「让蜘蛛完善」按钮。 */
    fun runInitFromUi() = runInitPrompt()

    private fun runInitPrompt() {
        if (engine.isBusy()) {
            appendInfo("初始化项目说明", "智蛛正在执行任务，请先等待或停止当前任务。")
            return
        }
        runCatching { engine.cancelPlanModeFromUi("/init 使用快速直接模式") }

        _state.update {
            it.copy(
                transcript = it.transcript + ChatItem(id = nextId("u"), kind = ChatKind.USER, title = "你", body = "/init"),
                composerBusy = true,
                workingStatus = "正在整理项目说明…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
        startTurn(INIT_INSTRUCTION)
    }

    fun removeAttachment(id: String) {
        attachmentPayloads.remove(id)
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    // ---------- 发送 / 停止 ----------

    fun send() {
        val text = _state.value.composerText.trim()
        if (text.isEmpty()) return
        if (text.startsWith("/")) {
            _state.update { it.copy(composerText = "", slashQuery = null, slashMatches = emptyList()) }
            runSlashCommand(text)
            return
        }
        if (engine.isBusy()) {
            // 预输入，复刻原版 sendPrompt() 的 steering 分支（MainActivity.java:1425-1450）：
            //  1) 用户气泡**立刻**进对话流（原版同样 addChatView(userItem)），这就是可见的确认；
            //  2) 清空输入框与附件；
            //  3) 工作状态文案改成「已预输入，等待当前回复完成…」（原版 showWorkingIndicator）；
            //  4) 文案按当时在执行什么分三种（原版的 wasTool / wasModel / 其它）。
            // ⚠️ 追加到**队尾**（不是覆盖）：连发多条都要保留。
            //
            // 图片必须在 clearAttachments() **之前**取出来挂到气泡上：附件载荷存在
            // attachmentPayloads 里、由 clearAttachments() 整个清掉，而气泡一旦进了
            // 对话流就没法再回头找那张图 —— 这正是「发出去图片不见了」的成因。
            val userItem = ChatItem(
                id = nextId("u"),
                kind = ChatKind.USER,
                title = "你",
                body = text,
                images = currentImages(),
            )
            // 顺序很重要：先编好要发的内容块与拼好提示词，再把附件清掉。
            // 反过来的话队列里这条预输入就永远发不出那张图、也丢掉附加的技能了。
            val imageBlocks = buildImageBlocks()
            val promptWithTextAttachments = buildPromptWithTextAttachments(text)
            clearAttachments()
            _state.update { s ->
                val toolsRunning = s.transcript.lastOrNull()
                    ?.tools?.any { !it.completed } == true
                val queuedNote = when {
                    s.permissionRequest != null || toolsRunning -> "已预输入：当前工具完成后加载"
                    else -> "已预输入：当前回复完成后加载"
                }
                val queue = s.pendingInputs + QueuedPrompt(userItem.id, text)
                s.copy(
                    composerText = "",
                    slashQuery = null,
                    slashMatches = emptyList(),
                    transcript = s.transcript + userItem,
                    pendingInputs = queue,
                    workingStatus = queuedStatus(queue.size),
                    message = queuedNote,
                )
            }
            // 真正的排队交给引擎：它会在「当前工具执行完 / 当前模型回复结束」这个
            // 协议安全点把预输入并入对话，并回调 onQueuedPromptApplied。
            // 界面这边不再自己维护"跑完一轮再发下一条"的递归链（那会与引擎重复排队）。
            val steered = engine.steer(promptWithTextAttachments, userItem.id, imageBlocks)
            if (!steered) {
                // 极端竞态：这里判定为忙，但真正入队时回合已经结束。
                // 不静默丢弃 —— 把文字放回输入框，用户能直接重发。
                _state.update { s ->
                    s.copy(
                        pendingInputs = s.pendingInputs.filterNot { q -> q.chatItemId == userItem.id },
                        composerText = text,
                        message = "上一回合刚好结束，消息未能入队，已放回输入框",
                    )
                }
            }
            return
        }
        val userItem = ChatItem(
            id = nextId("u"),
            kind = ChatKind.USER,
            title = "你",
            body = text,
            // 同上：必须在下面的 clearAttachments() 之前取，否则拿到的是空列表。
            images = currentImages(),
        )
        // 同上：先编内容块再清附件。
        val imageBlocks = buildImageBlocks()
        clearAttachments()
        _state.update {
            it.copy(
                composerText = "",
                slashQuery = null,
                slashMatches = emptyList(),
                transcript = it.transcript + userItem,
                contextTokens = it.contextTokens + 1_100,
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
        startTurn(buildPromptWithTextAttachments(text), imageBlocks)
    }

    /**
     * 把一条提示词交给真实引擎。
     *
     * 会话配置**每一轮都重新下发**：用户在设置里改了权限模式/推理强度/上下文窗口后，
     * 不需要重启应用就能在下一轮生效。
     */
    private fun startTurn(prompt: String, extraContent: JSONArray? = null) {
        // 记下这一轮属于哪个会话。放在这里而不是各调用点：`startTurn` 是**唯一漏斗**
        // （普通消息与 /init 都走它），只需维护一处；错误迟到时靠它判断该不该落地。
        turnSessionId = _state.value.activeSessionId
        val configured = runCatching { engine.configure(engineOverrides()) }
        if (configured.isFailure) {
            // 配置阶段就失败（例如 SharedPreferences 损坏）时不要静默：
            // 否则界面会一直停在"正在思考…"，用户只能干等。
            finishTurnWithError("引擎配置失败：" + (configured.exceptionOrNull()?.message ?: "未知原因"))
            return
        }
        runCatching { engine.sendPrompt(prompt, extraContent) }.onFailure { error ->
            finishTurnWithError(ToolText.friendlyError(error.message, error))
        }
    }

    /**
     * 把当前界面状态翻译成引擎覆盖项。
     *
     * `model` **故意留空**：界面上的 `modelLabel` 目前还是演示标签，真正生效的模型
     * 来自 API 配置记录（`ApiSettingsStore`）。在这里覆盖会用一个不存在的模型名把请求打挂；
     * 等设置页接线（Phase 5）之后再把模型选择器接上。
     */
    private fun engineOverrides(): EngineOverrides {
        val s = _state.value
        // 搜索后端：**有生效的服务就用它**，否则回落到免费后端（auto）。
        //
        // 这是这次改造的关键接线：搜索服务页是"多服务列表 + 当前生效项"，
        // 而引擎只认「一个 provider + 一份配置」。同步一次之后两边就一致了 ——
        // 缺了这一段，用户在列表里切来切去，实际发出去的还是老配置（而界面不会报错）。
        val activeService = runCatching { SearchServiceStore.active(getApplication()) }.getOrNull()
        val serviceKey = activeService?.let {
            runCatching { ApiSettingsStore.getSearchServiceKey(getApplication(), it.id) }.getOrDefault("")
        }.orEmpty()
        val legacyKey = s.settings.webSearchKeys[s.settings.webSearchProvider] ?: ""
        return EngineOverrides(
            permissionMode = s.permissionMode.toEngineMode(),
            effort = s.effort.toEngineEffort(),
            contextWindow = s.contextWindow,
            projectDirectory = s.projectPath,
            customSystemPrompt = s.settings.customSystemPrompt,
            visionEnabled = s.settings.visionEnabled,
            autoCompact = s.settings.autoCompact,
            autoCompactPercent = s.settings.autoCompactPercent,
            webSearchEnabled = s.settings.webSearchEnabled,
            // 服务类型的小写名就是引擎侧的 provider（见 WebSearchTool.providerName）。
            webSearchProvider = activeService?.type?.name?.lowercase()
                ?: when (s.settings.webSearchProvider) {
                    WebSearchProvider.AUTO -> "auto"
                    WebSearchProvider.DUCKDUCKGO -> "duckduckgo"
                    WebSearchProvider.BING -> "bing"
                    WebSearchProvider.TAVILY -> "tavily"
                    WebSearchProvider.EXA -> "exa"
                    WebSearchProvider.BRAVE -> "brave"
                    WebSearchProvider.SEARXNG -> "searxng"
                },
            webSearchMaxResults = s.settings.webSearchMaxResults,
            webTimeoutSec = s.settings.webSearchTimeoutSec,
            // 密钥取**生效服务**的那一份；没有服务时回落到老配置的单键。
            webSearchApiKey = if (activeService != null) serviceKey else legacyKey,
            webSearchBaseUrl = activeService?.config?.get(SearchFieldName.BASE_URL)
                ?: s.settings.webSearchSearxngUrl,
            webSearchServiceConfig = activeService?.config?.let { config ->
                runCatching { org.json.JSONObject(config.toMap()).toString() }.getOrDefault("")
            } ?: "",
            rootExecutionEnabled = s.settings.rootExecutionEnabled,
            sandboxAgentFullAccess = s.settings.sandboxAgentFullAccess,
            forcedKeepAliveEnabled = s.settings.forcedKeepAliveEnabled,
            model = modelOverride,
            roleCard = roleCardOverride,
        )
    }

    fun stop() {
        // 引擎的 cancel() 会一并取消权限/提问/计划三个门控，
        // 所以这里不需要再单独回执一次权限，只要把界面状态收干净。
        engine.cancel()
        pendingPermissionId = null
        // 在途的流式残片与工具进度缓冲全部丢弃：它们属于已经作废的这一轮。
        progressBuffers.clear()
        progressLastFlush.clear()
        liveTools.clear()
        currentGroupId = null
        _state.update {
            // 停止时把还在排队的预输入**还原回输入框**（拼成多行逐条显示），
            // 而不是直接丢弃 —— 否则连发多条再按停止就会漏消息。
            val restored = it.pendingInputs.joinToString("\n") { q -> q.text }
            it.copy(
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = it.busySessionIds - it.activeSessionId,
                composerText = if (restored.isNotEmpty()) restored else it.composerText,
                pendingInputs = emptyList(),
                message = if (it.pendingInputs.isNotEmpty()) {
                    "已停止当前任务，${it.pendingInputs.size} 条预输入已放回输入框"
                } else {
                    "已停止当前任务"
                },
            )
        }
    }

    // ==================================================================
    // 引擎事件逐条落地
    //
    // 线程约定：这些方法**全部在主线程被调用**（ZhiEngineController 负责搬运），
    // 所以可以直接改 StateFlow、不需要再加 post/Handler。
    // 顺序约定：凡是"文本之后紧跟工具/结束"的地方，控制器都已先 flush 过文本缓冲，
    // 否则工具卡会插到还没显示的正文前面。
    // ==================================================================

    override fun onEngineSessionStarted(model: String) {
        streamingAssistantId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        _state.update {
            it.copy(
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
    }

    /**
     * 取当前流式气泡的 id；没有就建一个。
     *
     * <p>原本只有 [onEngineText] 会建气泡。现在思考也要走这里 ——
     * 工具批次结束后模型**先思考再说话**，如果思考不建气泡，那一段思考会被
     * 静默丢掉（[onEngineThinking] 拿不到 id 就直接 return）。
     *
     * <p>「首个 delta 才创建」这件事没变。原版是回合开始时就插一个空气泡、
     * 结束时再丢弃空的；Compose 里那样会先闪一个空白块，观感更差 ——
     * 语义等价（空回复同样不会留下气泡，见 [finalizeStreaming] 的 keepIfEmpty）。
     */
    private fun ensureStreamingAssistant(): String {
        streamingAssistantId?.let { return it }
        val id = nextId("a")
        streamingAssistantId = id
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = id,
                    kind = ChatKind.ASSISTANT,
                    title = "智蛛",
                    body = "",
                    streaming = true,
                    processSteps = listOf("开始分析请求"),
                ),
            )
        }
        return id
    }

    /**
     * 把当前流式气泡**封口**（定稿，不再往里追加），但不丢弃。
     *
     * <p>⚠️ 这是「正文与工具卡按时序穿插」的唯一支点：工具批次开始时必须先封口，
     * 否则工具结束后模型继续说的那段会被追加到**工具之前**的同一个气泡里 ——
     * 界面上的表现就是"正文全在最上面、工具卡全挤在下面"，时序完全是平的。
     *
     * <p>封口 ≠ 结束回合：正文气泡和工具卡都留在原位、顺序不变，
     * 后续正文会开一个**新的**气泡接在工具卡后面（[ensureStreamingAssistant]）。
     *
     * <p>还在转圈的东西不受影响：[currentGroupId] 归 [onEngineToolBatchCompleted]
     * 管，工具行自己的状态由 `ToolActivity.completed` 管。
     */
    private fun sealStreamingAssistant() {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s ->
            s.copy(
                transcript = s.transcript.mapNotNull { item ->
                    when {
                        item.id != id -> item
                        // 一个字都没说过、也没思考过：这是延迟创建出来的空壳，
                        // 留着就是一块空白卡，直接丢掉。
                        item.body.isBlank() && item.thinking.isBlank() -> null
                        else -> item.copy(streaming = false)
                    }
                },
            )
        }
    }

    override fun onEngineText(delta: String) {
        val id = ensureStreamingAssistant()
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id == id) it.copy(body = it.body + delta) else it
                },
                workingStatus = "正在回复…",
            )
        }
    }

    override fun onEngineThinking(delta: String) {
        val id = ensureStreamingAssistant()
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id != id) it
                    // 思考预览按 1200 字符上限保留尾部（原版 liveThinking 的做法），
                    // 否则长思考会把内存和渲染都拖住。
                    else {
                        val merged = foldWhitespace(it.thinking + delta)
                        it.copy(thinking = if (merged.length > THINKING_LIMIT) merged.takeLast(THINKING_LIMIT) else merged)
                    }
                },
            )
        }
    }

    override fun onEngineToolBatchStarted(toolIds: List<String>) {
        // ⚠️ 先封口正文气泡，再开新的工具分组卡。
        //
        // 一个批次 = 一张新的工具分组卡，批次边界由引擎给，界面不猜。
        // 但批次边界同时意味着「上一段正文到此为止」—— 模型在工具之后说的话
        // 是新的一段，必须有自己的气泡，否则时序就平了。
        //
        // 引擎在工具事件之前已经 flush 过文本缓冲（见 onToolUse 的 flushTextNow），
        // 所以这里封到的一定是**已经显示出来**的那部分，不会把字留在后面。
        sealStreamingAssistant()
        val groupId = nextId("g")
        currentGroupId = groupId
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = groupId,
                    kind = ChatKind.TOOL_GROUP,
                    groupLabel = "",
                    tools = emptyList(),
                ),
            )
        }
    }

    override fun onEngineToolUse(id: String, name: String, input: JSONObject?) {
        // 工作流类工具（任务清单/计划模式）不产生工具卡，只更新进度与步骤，
        // 与原版一致：它们的效果体现在任务面板和计划窗口上。
        if (ToolText.isWorkflowTool(name)) {
            _state.update { it.copy(workingStatus = "正在执行 $name…") }
            appendProcessStep("调用工具：$name")
            return
        }

        // 防御路径：引擎没发批次开始事件就来了工具调用，这里自己开一张分组卡。
        // 顺手把正文气泡封口 —— 理由与 onEngineToolBatchStarted 相同：
        // 工具卡之前的正文到此为止，之后的要另起新气泡，否则时序是平的。
        var groupId = currentGroupId
        if (groupId == null) {
            sealStreamingAssistant()
            groupId = nextId("g")
            currentGroupId = groupId
        }
        val summary = ToolText.summary(name, input) ?: ""
        val command = input?.optString("command", "") ?: ""
        val (added, deleted) = ToolText.delta(name, input)
        val activity = ToolActivity(
            id = id,
            toolName = name,
            displayName = ToolText.userFacingName(name),
            summary = summary,
            additions = added,
            deletions = deleted,
            kind = kindOf(name),
        )
        liveTools[id] = LiveTool(groupId, name, command)

        _state.update { s ->
            val text = if (s.transcript.any { it.id == groupId }) s.transcript
            else s.transcript + ChatItem(id = groupId, kind = ChatKind.TOOL_GROUP)
            s.copy(
                transcript = text.map { item ->
                    if (item.id != groupId) item
                    else {
                        val tools = item.tools + activity
                        item.copy(tools = tools, groupLabel = groupLabel(tools))
                    }
                },
                workingStatus = "正在执行 $name…",
            )
        }
        appendProcessStep("调用工具：$name")
    }

    override fun onEngineToolProgress(id: String, chunk: String, stderr: Boolean, elapsedMs: Long) {
        if (liveTools[id] == null) return
        val buffer = progressBuffers.getOrPut(id) { StringBuilder() }
        if (stderr) buffer.append("[stderr]\n")
        buffer.append(chunk)
        // 单条工具的实时输出上限，超出丢头部（原版 liveOutput 是 40000）。
        if (buffer.length > LIVE_OUTPUT_LIMIT) buffer.delete(0, buffer.length - LIVE_OUTPUT_LIMIT)

        val now = System.currentTimeMillis()
        val last = progressLastFlush[id] ?: 0L
        if (now - last < PROGRESS_FLUSH_MS) return
        progressLastFlush[id] = now
        val text = buffer.toString()
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map {
                            if (it.id == id) it.copy(output = text, elapsedMs = maxOf(it.elapsedMs, elapsedMs)) else it
                        },
                    )
                },
            )
        }
    }

    override fun onEngineToolResult(
        id: String,
        name: String,
        isError: Boolean,
        exitCode: Int,
        content: String,
        diff: String,
        addedLines: Int,
        deletedLines: Int,
        command: String,
    ) {
        val live = liveTools.remove(id)
        progressBuffers.remove(id)
        progressLastFlush.remove(id)

        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { tool ->
                            if (tool.id != id) tool
                            else tool.copy(
                                completed = true,
                                failed = isError,
                                exitCode = exitCode,
                                output = content,
                                elapsedMs = maxOf(tool.elapsedMs, 0L),
                                additions = if (addedLines > 0) addedLines else tool.additions,
                                deletions = if (deletedLines > 0) deletedLines else tool.deletions,
                                // 包管理器命令失败时默认展开：报错信息通常很长，
                                // 折叠着用户只能看到"退出码 1"，等于没说。
                                expanded = tool.expanded ||
                                    (isError && ToolText.isPackageManagerTool(command)),
                            )
                        },
                    )
                },
                workingStatus = "正在继续处理…",
            )
        }
        appendProcessStep("工具完成：$name")

        // 编辑类工具可能改动工作区，顺手刷新变更面板。
        if (live != null && kindOf(name) == ToolKind.EDIT) refreshDiff()
    }

    override fun onEngineToolBatchCompleted(toolIds: List<String>) {
        if (toolIds.isEmpty()) return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else {
                        val touched = item.tools.any { it.id in toolIds }
                        if (!touched) item
                        else item.copy(
                            groupCompleted = true,
                            // 批结束时仍没拿到结果的成员，原版会标记成失败并补一句话。
                            // 保留这个行为：静默留一个"永远转圈"的卡更难排查。
                            tools = item.tools.map { tool ->
                                if (tool.id in toolIds && !tool.completed) {
                                    tool.copy(
                                        completed = true,
                                        failed = true,
                                        output = tool.output.ifEmpty { "工具在返回结果前结束。" },
                                    )
                                } else tool
                            },
                        )
                    }
                },
            )
        }
        currentGroupId = null
    }

    override fun onEnginePermissionRequest(requestId: String, tool: String, summary: String, highRisk: Boolean) {
        pendingPermissionId = requestId
        _state.update {
            it.copy(
                permissionRequest = PermissionRequest(
                    id = requestId,
                    tool = tool,
                    subtitle = subtitleFor(tool),
                    detail = summary,
                    riskLevel = if (highRisk) RiskLevel.HIGH else RiskLevel.NORMAL,
                ),
            )
        }
    }

    override fun onEngineQuestionRequest(requestId: String, questionsJson: String) {
        val questions = runCatching { JSONArray(questionsJson) }.getOrDefault(JSONArray())
        if (questions.length() == 0) {
            // 空提问没有可问的内容，直接回执让引擎继续，别把用户晾在一个空窗口前。
            engine.respondQuestion(requestId, JSONObject())
            return
        }
        pendingQuestions = PendingQuestions(requestId = requestId, questions = questions)
        showQuestionStep()
    }

    /**
     * 展示提问流程的第 [step] 步。
     *
     * 引擎的 `AskUserQuestion` 有两种形态：单问题（直接提交）与多问题（分步，
     * 每步一个窗口，按钮是「下一步」，最后一步才是「提交」）。
     * 答案按**问题原文**为键累积（与引擎的约定一致）。
     */
    private fun showQuestionStep() {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        if (question == null) {
            // 该步不是合法对象：跳过它而不是整个流程失败。
            advanceQuestion(JSONObject())
            return
        }
        val options = question.optJSONArray("options")
        val choices = buildList {
            if (options != null) {
                for (i in 0 until options.length()) {
                    val option = options.optJSONObject(i) ?: continue
                    add(ChoiceOption(label = option.optString("label", ""), detail = option.optString("description", "")))
                }
            }
        }
        val isLast = flow.index >= flow.questions.length() - 1
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = question.optString("header", "问题"),
                    intent = ChoiceIntent.QUESTION,
                    prompt = question.optString("question", "智蛛应该怎么做？"),
                    options = choices,
                    allowFreeForm = true,
                    freeFormHint = "其他回答…",
                    multiSelect = question.optBoolean("multiSelect", false),
                    // 多问题时分步：中间步骤是「下一步」，最后一步才是「提交」。
                    submitLabel = if (isLast) "提交" else "下一步",
                ),
            )
        }
    }

    /** 记录这一步的答案并前进；已是最后一步则回执引擎。 */
    private fun advanceQuestion(answer: Any?) {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        val key = question?.optString("question", "") ?: ""
        if (key.isNotEmpty() && answer != null) {
            runCatching { flow.answers.put(key, answer) }
        }
        val next = flow.index + 1
        if (next >= flow.questions.length()) {
            pendingQuestions = null
            _state.update { it.copy(choicePicker = null) }
            engine.respondQuestion(flow.requestId, flow.answers)
            return
        }
        flow.index = next
        showQuestionStep()
    }

    /** 取消整个提问流程：回执空答案，引擎会按「未选择」继续。 */
    private fun cancelQuestions() {
        val flow = pendingQuestions ?: return
        pendingQuestions = null
        _state.update { it.copy(choicePicker = null) }
        engine.respondQuestion(flow.requestId, JSONObject())
        appendInfo("已跳过提问", "智蛛的提问被跳过，它会按「未选择」继续。")
    }

    override fun onEnginePlanApprovalRequest(request: EnginePlanApproval) {
        _state.update { it.copy(planApproval = request.toUiPlanApproval()) }
    }

    override fun onEngineTasksChanged(tasks: List<AgentTask>) {
        _state.update { it.copy(tasks = tasks) }
    }

    override fun onEngineProjectDirectoryChanged(directory: String) {
        _state.update {
            it.copy(
                projectPath = directory,
                projectName = directory.trimEnd('/').substringAfterLast('/').ifEmpty { directory },
                filePath = directory,
            )
        }
    }

    override fun onEngineQueuedPromptApplied() {
        // 引擎已经把队首预输入并入了对话。界面这边做的是：
        // 出队一条（FIFO），并把还在流的上一段回复收尾。
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            val rest = s.pendingInputs.drop(1)
            s.copy(
                pendingInputs = rest,
                workingStatus = "预输入已加载，正在继续…",
                message = if (rest.isEmpty()) "预输入已加载，正在继续" else "预输入已加载，还剩 ${rest.size} 条",
            )
        }
    }

    override fun onEngineResponseRetry() {
        // 重试会重发整段回复：把上一次失败尝试留下的半截气泡丢掉，
        // 否则界面上会出现"半句话 + 完整回复"。
        discardStreamingAssistant()
        _state.update { it.copy(workingStatus = "连接中断，正在重试模型请求…", composerBusy = true) }
    }

    override fun onEngineInterruptedBySteering() {
        discardStreamingAssistant()
        _state.update {
            it.copy(
                workingStatus = "旧响应已打断，正在按最新纠正重新规划…",
                composerBusy = true,
            )
        }
    }

    override fun onEngineUsage(inputTokens: Long, outputTokens: Long) {
        val used = maxOf(0L, inputTokens) + maxOf(0L, outputTokens)
        _state.update { s ->
            // 有效窗口下限 16000：配置里若是 0/异常值，也不能让上下文条除出 Inf。
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            val shown = if (used > 0) used.toInt() else engine.estimateContextTokens()
            s.copy(contextTokens = shown, contextWindow = window)
        }
    }

    override fun onEngineStatus(status: String) {
        val text = ToolText.displayStatus(status, "thinking", true)
        _state.update { it.copy(workingStatus = text) }
        appendProcessStep(text)
    }

    override fun onEngineTurnComplete(stopReason: String) {
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                composerBusy = false,
                workingStatus = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
                contextTokens = engine.estimateContextTokens().takeIf { it > 0 } ?: s.contextTokens,
                sessions = s.sessions.map { session ->
                    if (session.id == s.activeSessionId) session.copy(busy = false, updatedAtLabel = "刚刚") else session
                },
            )
        }
        // 第一轮结束后会话文件才真正落盘（引擎在 sendPrompt 里创建 SessionStore），
        // 所以这里必须重读列表，否则新会话在侧栏里不存在。
        syncActiveSessionFromDisk()
    }

    /**
     * 回合结束后把当前会话对齐到磁盘状态。
     *
     * 做两件事：
     * 1. 重读会话列表 —— 让刚创建的会话出现、让标题/消息数/时间刷新；
     * 2. 如果当前还没有 activeSessionId（刚 /new 过），把引擎刚写出的文件认领为当前会话。
     */
    private fun syncActiveSessionFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            val sessionFile = engine.sessionFile()
            _state.update { s ->
                val activeId = when {
                    s.activeSessionId.isNotEmpty() -> s.activeSessionId
                    sessionFile != null -> sessionFile.absolutePath
                    else -> ""
                }
                s.copy(sessions = sessions, activeSessionId = activeId)
            }
        }
    }

    override fun onEngineError(message: String, error: Throwable?) {
        finishTurnWithError(ToolText.friendlyError(message, error))
    }

    // ---------- 引擎事件用到的内部工具 ----------

    /**
     * 一轮以错误收尾：收尾气泡、落一条错误卡、把忙碌态清干净。
     *
     * ## 为什么开头要校验会话归属
     *
     * 报错可能是**迟到**的：用户在等待回复时切到了别的会话（或点了新会话），
     * 这一轮随即被 `engine.cancel()` 打断，而错误回调仍可能在之后到达。
     * `ZhiEngineController` 的代际校验能挡掉一部分，但 `startTurn` 这条路径是
     * 同步调用的、不经过代际门，而且 `_state` 此刻已经是**新会话**的 ——
     * 于是错误卡会落到新会话里，看起来像"串台"。
     *
     * 所以这里再按会话 id 兜一道：本轮所属的会话已经不是当前会话时，直接丢弃，
     * 不去污染新会话。（用户的预期是"错误应当出现在出问题的那个会话里"，
     * 但它已经不在前台、内存里也没有它的 transcript，落不进去；
     * 丢掉比串台正确 —— 那个会话下次打开时会从自己的文件重建。）
     */
    private fun finishTurnWithError(text: String) {
        val owner = turnSessionId
        turnSessionId = null
        if (owner != null && owner != _state.value.activeSessionId) return

        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = nextId("e"),
                    kind = ChatKind.ERROR,
                    title = "错误",
                    body = text,
                ),
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
            )
        }
        pendingPermissionId = null
    }

    /** 结束流式状态。[keepIfEmpty] 为 false 时，空白气泡会被丢弃（原版 discardEmptyStreamingMessage）。 */
    private fun finalizeStreaming(keepIfEmpty: Boolean) {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s ->
            s.copy(
                transcript = s.transcript.mapNotNull { item ->
                    if (item.id != id) item
                    else if (!keepIfEmpty && item.body.isBlank() && item.thinking.isBlank()) null
                    else item.copy(streaming = false)
                },
            )
        }
    }

    /** 整条丢弃当前流式气泡（重试 / 被纠偏打断时用）。 */
    private fun discardStreamingAssistant() {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s -> s.copy(transcript = s.transcript.filterNot { it.id == id }) }
    }

    /** 给当前助手气泡记一条过程步骤（最多 12 条，相邻重复去重，与原版一致）。 */
    private fun appendProcessStep(step: String) {
        val id = streamingAssistantId ?: return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id) item
                    else {
                        val merged = if (item.processSteps.lastOrNull() == step) item.processSteps
                        else (item.processSteps + step).takeLast(PROCESS_STEP_LIMIT)
                        item.copy(processSteps = merged)
                    }
                },
            )
        }
    }

    /** 思考文本的空白折叠：连续空白合成一个空格（原版 appendCollapsedWhitespace）。 */
    private fun foldWhitespace(text: String): String {
        val out = StringBuilder(text.length)
        var lastSpace = false
        for (ch in text) {
            val isSpace = ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r'
            if (isSpace) {
                if (!lastSpace) out.append(' ')
                lastSpace = true
            } else {
                out.append(ch)
                lastSpace = false
            }
        }
        return out.toString()
    }


    private fun queuedStatus(count: Int): String =
        if (count <= 1) "已预输入，等待当前回复完成…" else "已预输入 $count 条，等待当前回复完成…"

    /**
     * 回执一次权限请求。
     *
     * 回执对象是**引擎**而不是界面上的临时对象：引擎的工具线程正阻塞在
     * `PermissionGate.require()` 的闩锁上，只有真正 respond 过去它才会继续。
     * 若一直不回执，引擎会等满 30 分钟。
     */
    fun resolvePermission(allow: Boolean, alwaysAllow: Boolean = false) {
        val requestId = pendingPermissionId
        pendingPermissionId = null
        engine.respondPermission(requestId ?: "", allow)
        _state.update {
            it.copy(
                permissionRequest = null,
                permissionMode = if (alwaysAllow) PermissionMode.ACCEPT_EDITS else it.permissionMode,
                message = if (alwaysAllow) {
                    if (allow) "已允许本次调用，并切换为自动编辑" else "已拒绝本次调用，并切换为自动编辑"
                } else {
                    if (allow) "已允许本次调用" else "已拒绝本次调用"
                },
            )
        }
        // 切到自动编辑后要立刻下发，否则下一条工具调用还会弹窗。
        if (alwaysAllow) runCatching { engine.configure(engineOverrides()) }
    }

    private fun kindOf(toolName: String): ToolKind = when (toolName) {
        "Grep", "Glob", "Search" -> ToolKind.SEARCH
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> ToolKind.READ
        "Edit", "MultiEdit", "Write", "Move", "Delete", "Mkdir", "Copy" -> ToolKind.EDIT
        "Bash", "Root", "BashTool" -> ToolKind.COMMAND
        else -> ToolKind.OTHER
    }

    private fun groupLabel(tools: List<ToolActivity>): String {
        val parts = mutableListOf<String>()
        val searches = tools.count { it.kind == ToolKind.SEARCH }
        val reads = tools.count { it.kind == ToolKind.READ }
        val edits = tools.count { it.kind == ToolKind.EDIT }
        val commands = tools.count { it.kind == ToolKind.COMMAND }
        if (searches > 0) parts += "搜索 $searches 个模式"
        if (reads > 0) parts += "读取 $reads 个文件"
        if (edits > 0) parts += "修改 $edits 处代码"
        if (commands > 0) parts += "执行 $commands 条命令"
        if (parts.isEmpty()) parts += "调用 ${tools.size} 个工具"
        return parts.joinToString("、")
    }

    private fun subtitleFor(tool: String): String = when (tool) {
        "Root" -> "高风险：智蛛请求以 Android uid 0 执行系统命令"
        "Bash" -> "智蛛请求在本地 Termux 环境中执行以下命令"
        "AndroidIntent" -> "智蛛请求通过 Android 应用进程打开手机 App、网页或系统页面"
        "Write", "Edit", "MultiEdit" -> "智蛛请求修改项目中的文件"
        "Delete" -> "智蛛请求删除项目中的文件或目录"
        else -> "智蛛请求调用工具 $tool"
    }

    // ---------- 斜杠命令 ----------

    /**
     * 斜杠命令路由。
     *
     * ## 两条原则
     *
     * 1. **能接真功能的就接真功能**，不要保留"看起来执行了"的假回执。
     * 2. **没实现的就直说没实现**。原来兜底分支会打印"已执行 `xxx`（Mock）"，
     *    这是最糟的一种反馈：用户以为命令生效了，于是去做下一件事。
     *    现在的兜底是明确列出"这条还没移植"以及可用的替代指令。
     */
    private fun runSlashCommand(raw: String) {
        val command = raw.substringBefore(' ').lowercase(Locale.US)
        val arg = raw.substringAfter(' ', "").trim()
        when (command) {
            "/clear", "/new" -> newSession()
            "/terminal" -> selectTab(WorkspaceTab.TERMINAL)
            // 「变更」Tab 已移除：/diff 走普通消息输出 diff 文本
            "/changes", "/diff" -> {}
            "/files" -> selectTab(WorkspaceTab.FILES)
            "/plan" -> enterPlanMode(arg)
            "/permissions" -> showPermissionPicker()
            "/effort" -> showEffortPicker()
            "/model" -> showModelPicker()
            "/cancel" -> stop()
            "/config" -> openSettings()
            "/mcp" -> openMcpConfig()
            "/copy" -> copyLatestAssistantReply()
            "/compact" -> compactContext(arg)

            // 环境类：都落到真实的 Termux 环境弹窗上（含安装/修复入口），
            // 不再是把命令当普通消息发出去换一句假回复。
            "/doctor", "/runtime" -> openEnvironment()
            "/repair" -> {
                openEnvironment()
                repairRuntime()
            }

            "/tasks" -> openTaskList()
            "/skills" -> openSkills()
            "/agents" -> openRoleCards()
            "/memory" -> openMemory()
            "/init" -> runInitPrompt()

            // 沙箱是**跨进程**的：引擎在 :zhisandbox，界面必须交给系统启动。
            // 以前这条命令没有路由，会掉进下面的"指令尚未移植"兜底卡片。
            "/sandbox" -> openSandbox()

            "/web" -> handleWebSlash(arg)

            "/context" -> {
                if (arg.isEmpty()) {
                    showContextInfo()
                } else {
                    val tokens = parseTokenCount(arg)
                    if (tokens == null) {
                        _state.update { it.copy(message = "请输入 128k、200k、1m、1.5m 这类格式", messageIsError = true) }
                    } else {
                        _state.update { it.copy(contextWindow = tokens, message = "上下文窗口已设置为 ${formatTokens(tokens)}") }
                    }
                }
            }

            "/resume" -> {
                openSidebar()
                _state.update { it.copy(message = "在侧栏选择要恢复的会话") }
            }

            "/usage", "/stats", "/status" -> showContextInfo()
            "/help" -> appendInfo(
                "全部指令",
                SLASH_COMMANDS.joinToString("\n") { "` ${it.name} ` — ${it.hint}" },
            )

            else -> appendInfo(
                "指令尚未移植",
                buildString {
                    append("`").append(command).append("` 在当前 Compose 版本里还没有对应实现，")
                    append("本机也没有执行任何操作。\n\n")
                    append("可用的相关指令：\n")
                    append("` /doctor ` `/runtime` `/repair` — 内置 Termux 环境\n")
                    append("` /compact ` — 真正调用模型压缩上下文\n")
                    append("` /context ` — 查看或设置上下文窗口\n")
                    append("` /copy ` — 复制最近一条回复\n")
                    append("` /tasks ` — 查看 Agent 任务列表\n")
                    append("` /resume ` — 从侧栏恢复历史会话\n")
                    append("` /mcp ` — MCP 服务器配置\n")
                    append("` /model ` — 切换模型")
                },
            )
        }
    }

    /**
     * 重新估算上下文用量。
     *
     * 压缩之后必须重算：`contextTokens` 平时是**累加**出来的（每轮加上 API 上报的用量），
     * 压缩会真的丢掉一部分历史，累加值只会越走越大，不重算就会显示一个虚高的占用。
     */
    private fun syncContextUsage() {
        _state.update { s ->
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            s.copy(contextTokens = engine.estimateContextTokens(), contextWindow = window)
        }
    }

    /**
     * `/context 1m` 这类参数。
     *
     * 返回 null 表示格式不合法（而不是抛异常）——调用方要的是"给一句提示"，
     * 不是错误处理。边界与原版一致：16k–2m，超出范围会让引擎按无效窗口处理。
     */
    private fun parseTokenCount(raw: String): Int? {
        var s = raw.trim().lowercase(Locale.US).replace(",", "").replace("_", "")
        if (s.isEmpty()) return null
        var multiplier = 1.0
        when {
            s.endsWith("k") -> { multiplier = 1_000.0; s = s.dropLast(1) }
            s.endsWith("m") -> { multiplier = 1_000_000.0; s = s.dropLast(1) }
        }
        val value = s.toDoubleOrNull()?.times(multiplier) ?: return null
        if (!value.isFinite() || value < 16_000.0 || value > 2_000_000.0) return null
        return value.toInt()
    }

    /**
     * 「重试上一问」：把最近一条用户消息重新发一次。
     *
     * 注意重发**不会**撤回上一轮的结果，所以它会作为新一轮追加在对话末尾
     * （与原版行为一致：原版也是重发，而不是回滚历史）。
     */
    private fun retryLastUserPrompt() {
        val lastUser = _state.value.transcript.lastOrNull {
            it.kind == ChatKind.USER && it.body.isNotBlank()
        }
        if (lastUser == null) {
            _state.update { it.copy(message = "没有可重试的问题", messageIsError = true) }
            return
        }
        _state.update { it.copy(composerText = lastUser.body) }
        send()
    }

    /**
     * `/web`：联网搜索。
     *
     * 四种形态（与原版 `handleWebSlash` 一致）：
     * - 无参数 → 打开联网设置
     * - `on` / `off` → 开关，**真的写进设置并重配引擎**（否则下一轮请求还按旧值走）
     * - `fetch <url>` → 直接读网页
     * - 其它 → 当作搜索词直接搜
     *
     * 后两种走 [ZhiEngineController.manualWebSearch]，**不经过模型**：
     * 用户输入后立刻拿到结果，省掉一次模型往返与相应的上下文消耗。
     */
    private fun handleWebSlash(arg: String) {
        when {
            arg.isEmpty() -> {
                openSettings()
                _state.update { it.copy(settingsDraft = it.settingsDraft?.copy(category = SettingsCategory.NETWORK)) }
            }
            arg.equals("on", ignoreCase = true) || arg == "开启" -> setWebSearch(true)
            arg.equals("off", ignoreCase = true) || arg == "关闭" -> setWebSearch(false)
            arg.startsWith("fetch ", ignoreCase = true) -> {
                val url = arg.substring(6).trim()
                if (url.isEmpty()) appendInfo("网页读取", "用法：`/web fetch https://example.com`")
                else runWebAction("网页读取", url) { engine.manualWebFetch(url) }
            }
            else -> runWebAction("联网搜索", arg) { engine.manualWebSearch(arg) }
        }
    }

    private fun setWebSearch(enabled: Boolean) {
        _state.update { s ->
            s.copy(
                settings = s.settings.copy(webSearchEnabled = enabled),
                settingsDraft = s.settingsDraft?.copy(webSearchEnabled = enabled),
                message = if (enabled) "联网搜索已开启" else "联网搜索已关闭",
            )
        }
        // 立刻下发：这一步容易漏，漏了就会"开关看起来变了，但下一轮请求还用旧值"。
        runCatching { engine.configure(engineOverrides()) }
    }

    /**
     * 执行一次手动联网动作，并把文本结果落成一张助手卡片。
     *
     * 先放一张"进行中"的卡片：网络请求可能要几秒，没有反馈用户会以为没反应。
     * 结果出来后**原地替换**那张卡片，而不是追加一条——否则对话流里会留下
     * 一个永远停在"正在搜索…"的死卡片。
     */
    private fun runWebAction(title: String, subject: String, action: suspend () -> Result<String>) {
        val cardId = nextId("web")
        _state.update {
            it.copy(
                transcript = it.transcript + ChatItem(
                    id = cardId,
                    kind = ChatKind.ASSISTANT,
                    title = title,
                    body = "正在处理：`$subject` …",
                ),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            _state.update { s ->
                val body = result.getOrElse { error -> (error.message ?: "未知原因") }
                s.copy(
                    transcript = s.transcript.map { item ->
                        if (item.id != cardId) item else item.copy(body = body, streaming = false)
                    },
                )
            }
        }
    }

    /** `/context`（无参数）与 `/usage`、`/stats`、`/status` 共用。 */    private fun showContextInfo() {
        val s = _state.value
        val measured = engine.hasMeasuredContextUsage()
        appendInfo(
            "会话状态",
            buildString {
                append("模型：").append(s.modelLabel).append('\n')
                append("API 配置：").append(s.profileName)
                append(if (s.apiKeyConfigured) "（已配置密钥）" else "（未配置密钥）").append('\n')
                // 只显示项目名：完整路径在沙箱里是 /data/user/0/<宿主>/blackbox/...
                // 内部虚拟化路径，又长又吓人且用户无法据此操作（与 GitChanges 空态同理）。
                append("项目：").append(s.projectPath.trimEnd('/').substringAfterLast('/')).append('\n')
                append("上下文：").append(formatTokens(s.contextTokens))
                append(" / ").append(formatTokens(s.contextWindow))
                append(if (measured) "（API 实测用量）" else "（本地估算，尚未产生 API 用量）").append('\n')
                append("权限：").append(s.permissionMode.label).append('\n')
                append("推理强度：").append(s.effort.label).append('\n')
                append("运行环境：").append(if (s.runtimeReady) "已就绪" else "未初始化")
            },
        )
    }

    /**
     * `/compact`：真正调用模型做语义压缩。
     *
     * 引擎侧是同步阻塞的（会走网络），所以放 IO 线程；期间给一条进行中提示，
     * 因为大上下文压缩可能要几十秒，没有反馈用户会以为卡死了。
     *
     * ⚠️ 引擎**不保证**真的调用了模型：`compactContextSemantic` 在算不出可压缩区间时
     * 会直接返回 [ENGINE_NOTHING_TO_COMPACT] 那句话（见其 `cut <= 0` 分支），
     * 引擎自己的自动压缩也用同一个前缀判断。所以这里必须分流——
     * 不加区分地宣称"已用模型生成摘要"就是在编造一次并不存在的模型调用。
     */
    private fun compactContext(instructions: String) {
        if (engine.isBusy()) {
            appendInfo("上下文压缩", "任务正在运行，完成后再压缩上下文。")
            return
        }
        // 用中性的进行时措辞：此时还不知道引擎会不会真的调模型。
        appendInfo("上下文压缩", "正在整理上下文…")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.compactContext(instructions) }
            result.fold(
                onSuccess = { summary ->
                    syncContextUsage()
                    val usage = "现在上下文：${formatTokens(_state.value.contextTokens)} / ${formatTokens(_state.value.contextWindow)}"
                    if (summary.startsWith(ENGINE_NOTHING_TO_COMPACT)) {
                        appendInfo(
                            "无需压缩",
                            "引擎判断当前没有可压缩的历史，因此**没有**调用模型。\n$usage",
                        )
                        return@fold
                    }
                    appendInfo(
                        "上下文压缩完成",
                        buildString {
                            append("已用模型生成语义摘要，后续请求改用摘要后的上下文。\n")
                            append(usage).append('\n')
                            if (summary.isNotBlank()) {
                                append("\n摘要要点：\n").append(summary.take(COMPACT_SUMMARY_LIMIT))
                                if (summary.length > COMPACT_SUMMARY_LIMIT) append("…")
                            }
                        },
                    )
                },
                onFailure = { error -> appendInfo("上下文压缩失败", error.message ?: "未知原因") },
            )
        }
    }

    /**
     * `/plan` 进入计划模式。
     *
     * 参考原版流程：先弹「计划目标」窗口问清楚要做哪件事，再产出计划。
     * 直接把计划推给用户（旧行为）会跳过"目标澄清"这一步。
     */
    private fun enterPlanMode(arg: String) {
        if (arg == "off" || arg == "exit") {
            runCatching { engine.cancelPlanModeFromUi("") }
            _state.update { it.copy(planApproval = null, message = "已退出计划模式") }
            return
        }
        if (engine.isBusy()) {
            _state.update { it.copy(message = "当前回合还在进行，等它结束后再进入计划模式") }
            return
        }
        val result = runCatching { engine.enterPlanModeFromUi() }
        val entered = result.getOrNull()?.isError == false
        if (!entered) {
            // 计划模式进不去时必须说出来。默默什么都不做的话，用户会以为
            // 自己已经在只读模式里了 —— 而实际上下一条消息会照常执行。
            finishTurnWithError("进入计划模式失败：" + (result.exceptionOrNull()?.message ?: "引擎拒绝了该请求"))
            return
        }
        _state.update { it.copy(permissionMode = PermissionMode.PLAN, message = "已进入计划模式（只读）") }
        // 带了目标就当成一条普通提示词发出去：计划模式下引擎只会产出计划，不会执行。
        // 「问目标」这一步由引擎自己用「选择」窗口发起（等提问门控接线后生效）。
        if (arg.isNotEmpty()) {
            _state.update { it.copy(composerText = arg) }
            send()
        }
    }

    /**
     * 批准 / 退回计划。
     *
     * 回执必须发给引擎：`onPlanApprovalRequest` 对应的工具线程正阻塞在审批闩锁上，
     * 只有 `respondPlanApproval` 才会让它继续。原版这个闩锁**故意没有超时**——
     * 无人值守的请求永远不能变成"批准执行"。
     */
    fun resolvePlan(approved: Boolean) {
        val plan = _state.value.planApproval ?: return
        engine.respondPlanApproval(
            plan.id,
            if (approved) PlanApprovalGate.Decision.APPROVE else PlanApprovalGate.Decision.KEEP_PLANNING,
            "",
        )
        appendInfo(
            if (approved) "计划已批准" else "计划已退回",
            if (approved) "开始执行：${plan.title}" else "等待修改后的计划。",
        )
        _state.update { it.copy(planApproval = null) }
    }


    /** 「继续规划」并带上用户填写的反馈。 */
    fun resolvePlanWithFeedback(feedback: String) {
        val plan = _state.value.planApproval ?: return
        val trimmed = feedback.trim()
        // 空反馈等价于"退回"：把 KEEP_PLANNING 与空 feedback 一起发过去，
        // 引擎会重新进入规划并等待下一版计划。
        engine.respondPlanApproval(plan.id, PlanApprovalGate.Decision.KEEP_PLANNING, trimmed)
        appendInfo(
            if (trimmed.isEmpty()) "计划已退回" else "已提交反馈",
            if (trimmed.isEmpty()) "等待修改后的计划。" else trimmed,
        )
        _state.update { it.copy(planApproval = null) }
    }

    // ---------- API 配置 ----------

    /**
     * 打开 API 配置（列表页）。
     *
     * 顺带刷新 [WorkspaceUiState.profileName] / [WorkspaceUiState.modelLabel]：
     * 这两个字段原来写死成演示值，现在它们是**真实生效的配置摘要**，
     * 否则顶栏会显示一个跟实际请求无关的模型名。
     */
    fun openApiConfig() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        viewModelScope.launch(Dispatchers.IO) {
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                // 设置主页垫在子页之下（页面栈），返回才有回退目标。
                it.copy(
                    apiConfig = state,
                    settingsOpen = true,
                    settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun closeApiConfig() = _state.update { it.copy(apiConfig = null) }

    // ---------------------------------------------------------------- 搜索服务

    /**
     * 打开「搜索服务」页（形态对齐 RikkaHub：多服务列表 + 当前生效项）。
     *
     * 与 [openApiConfig] 同一套页面栈规矩：设置主页垫在底下，返回才有回退目标。
     */
    fun openSearchServices() {
        hideSidebarForNavigation()
        viewModelScope.launch(Dispatchers.IO) {
            // 先把老版「单个 provider」迁成一条服务，否则老用户的配置会在这次打开时
            // 看起来"全都消失了"（列表空、而搜索也回落到免费后端）。
            SearchServiceStore.migrateLegacyIfNeeded(
                getApplication(),
                _state.value.settings.webSearchProvider,
                _state.value.settings.webSearchSearxngUrl,
                _state.value.settings.webSearchKeys[_state.value.settings.webSearchProvider].orEmpty(),
            )
            val services = SearchServiceStore.read(getApplication())
            _state.update {
                it.copy(
                    searchServices = services,
                    settingsOpen = true,
                    settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
                )
            }
        }
    }

    fun closeSearchServices() = _state.update { it.copy(searchServices = null) }

    /** 编辑页返回：只关表单、留在列表（与 API/MCP 页的 cancelForm 同一语义）。 */
    fun cancelSearchServiceForm() = _state.update {
        it.copy(searchServices = it.searchServices?.copy(form = null))
    }

    fun newSearchService() = _state.update {
        it.copy(searchServices = it.searchServices?.copy(form = SearchServiceDraft.blank()))
    }

    fun editSearchService(service: SearchService) = _state.update {
        it.copy(searchServices = it.searchServices?.copy(form = SearchServiceDraft.from(service)))
    }

    fun updateSearchServiceDraft(transform: (SearchServiceDraft) -> SearchServiceDraft) = _state.update {
        it.copy(searchServices = it.searchServices?.copy(form = transform(it.searchServices.form ?: SearchServiceDraft.blank())))
    }

    fun saveSearchService() {
        val draft = _state.value.searchServices?.form ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = SearchServiceStore.save(getApplication(), draft)
            result.fold(
                onSuccess = {
                    val services = SearchServiceStore.read(getApplication())
                    _state.update { s ->
                        s.copy(
                            searchServices = services,
                            message = "已保存搜索服务：${draft.name}",
                            messageIsError = false,
                        )
                    }
                    syncSearchServiceToEngine()
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(message = failure.message ?: "保存失败", messageIsError = true)
                    }
                },
            )
        }
    }

    fun selectSearchService(serviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SearchServiceStore.select(getApplication(), serviceId).fold(
                onSuccess = {
                    val services = SearchServiceStore.read(getApplication())
                    val name = services.services.firstOrNull { it.id == serviceId }?.name.orEmpty()
                    _state.update {
                        it.copy(searchServices = services, message = "搜索服务已切换：$name", messageIsError = false)
                    }
                    syncSearchServiceToEngine()
                },
                onFailure = {
                    _state.update { s -> s.copy(message = "切换失败", messageIsError = true) }
                },
            )
        }
    }

    fun deleteSearchService(serviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SearchServiceStore.delete(getApplication(), serviceId).fold(
                onSuccess = {
                    val services = SearchServiceStore.read(getApplication())
                    _state.update {
                        it.copy(searchServices = services, message = "已删除搜索服务", messageIsError = false)
                    }
                    syncSearchServiceToEngine()
                },
                onFailure = {
                    _state.update { s -> s.copy(message = "删除失败", messageIsError = true) }
                },
            )
        }
    }

    /**
     * 把「当前生效的搜索服务」推给引擎。
     *
     * 与 [syncActiveProfile] 同一类操作：切换服务之后下一次请求就该走新服务，
     * 而不是等用户再进一次设置。
     */
    private fun syncSearchServiceToEngine() {
        runCatching { engine.configure(engineOverrides()) }
    }

    /** 新增：给一张空表单。 */
    fun newApiProfile() = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = ApiProfileDraft.blank()))
    }

    /** 编辑：**故意不回填密钥**（见 ApiConfigStore 的说明）。 */
    fun editApiProfile(profile: ApiProfile) = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = ApiProfileDraft.from(profile)))
    }

    fun updateApiProfileDraft(transform: (ApiProfileDraft) -> ApiProfileDraft) = _state.update {
        val config = it.apiConfig ?: return@update it
        val form = config.form ?: return@update it
        it.copy(apiConfig = config.copy(form = transform(form)))
    }

    fun cancelApiProfileForm() = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = null))
    }

    /**
     * 保存一条 API 配置。
     *
     * 密钥写入 AndroidKeyStore 加密存储；编辑已有记录时若密钥留空，
     * 传 `replaceKey=false` 沿用原密钥，而不是把它清掉。
     */
    fun saveApiProfile() {
        val form = _state.value.apiConfig?.form ?: return
        if (!form.saveable) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.save(getApplication(), form)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _state.update { it.copy(message = "保存失败：" + (error?.message ?: "未知原因"), messageIsError = true) }
                return@launch
            }
            val state = ApiConfigStore.read(getApplication())
            _state.update { it.copy(apiConfig = state, message = "API 配置已保存") }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun selectApiProfile(profileId: String) {
        // 换配置就用那份配置自己的模型，别把上一条配置的会话级覆盖带过去。
        modelOverride = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.select(getApplication(), profileId)
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(
                    apiConfig = state,
                    message = if (result.isSuccess) "已切换 API 配置" else "切换失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun deleteApiProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.delete(getApplication(), profileId)
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(
                    apiConfig = state,
                    message = if (result.isSuccess) "配置已删除" else "无法删除：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    // ---------- MCP 配置 ----------

    /**
     * 打开 MCP 服务器配置（列表页）。
     *
     * 与 API 配置一样共用"列表页 / 表单页"两态，`form` 非空即表单。
     * 这里的读写是**同步**的：`McpConfigStore` 只读一个本地小 JSON，
     * 没有网络也没有密钥解密，放 IO 线程反而让状态更新顺序更难推理。
     */
    fun openMcpConfig() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        _state.update {
            // 同 openApiConfig：hub 垫底，返回回设置主页。
            it.copy(
                mcpConfig = McpStore.read(),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
    }

    fun closeMcpConfig() = _state.update { it.copy(mcpConfig = null) }

    fun newMcpServer() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(form = McpServerDraft(originalName = null)))
    }

    /**
     * 编辑已有服务器：需要回读 `args` / `env` / `headers` 的原文，
     * 因为列表模型只带了参数个数（这些内容可能很大，不该进列表状态）。
     */
    fun editMcpServer(server: McpServer) {
        val raw = McpStore.rawOf(server.name)
        _state.update {
            val config = it.mcpConfig ?: return@update it
            val draft = if (raw != null) McpStore.draftOf(server, raw.first, raw.second, raw.third)
            else McpStore.draftOf(server, emptyList(), "", "")
            it.copy(mcpConfig = config.copy(form = draft))
        }
    }

    fun updateMcpDraft(transform: (McpServerDraft) -> McpServerDraft) = _state.update {
        val config = it.mcpConfig ?: return@update it
        val form = config.form ?: return@update it
        it.copy(mcpConfig = config.copy(form = transform(form)))
    }

    fun cancelMcpForm() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(form = null))
    }

    fun saveMcpServer() {
        val form = _state.value.mcpConfig?.form ?: return
        if (!form.saveable) return
        val result = McpStore.save(form)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = if (result.isSuccess) "MCP 配置已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /** 启用 / 停用：整行点击触发，等价于原版行尾的「启用/停用」按钮。 */
    fun toggleMcpServer(server: McpServer) {
        val result = McpStore.setEnabled(server.name, !server.enabled)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = when {
                    result.isFailure -> "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                    server.enabled -> "已停用：${server.name}"
                    else -> "已启用：${server.name}"
                },
            )
        }
    }

    fun deleteMcpServer(server: McpServer) {
        val result = McpStore.delete(server.name)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = if (result.isSuccess) "已删除 MCP 服务器：${server.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * 「测试连接」：真的去连这台服务器，拿回它的工具清单。
     *
     * ## 为什么必须由用户动作触发
     *
     * `ZhiEngineController.mcpStatus()` 是**阻塞且昂贵**的：每台服务器都要起一次
     * 子进程或发一轮 HTTP，单台超时 30 秒。如果在打开 MCP 页时自动跑一遍，
     * "打开设置看一眼"就会变成"把每台服务器都启动一次" —— 在用户还没决定要改什么
     * 之前就产生了副作用，而且页面会卡住。
     *
     * ## 为什么按名字单独测
     *
     * 引擎的接口是"列出所有启用的服务器"（没有单台的入口），所以这里测完之后
     * **只把那台的结果**并进状态：把其它服务器的结果也一起刷新会让人以为
     * "我只测了 A，为什么 B 的状态也变了"。
     */
    fun testMcpServer(name: String) {
        viewModelScope.launch {
            _state.update { state ->
                val config = state.mcpConfig ?: return@update state
                state.copy(mcpConfig = config.copy(testing = config.testing + name))
            }
            val result = withContext(Dispatchers.IO) { engine.mcpStatus() }
            _state.update { state ->
                val config = state.mcpConfig ?: return@update state
                val testing = config.testing - name
                result.fold(
                    onSuccess = { rows ->
                        val row = rows.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        state.copy(
                            mcpConfig = config.copy(
                                testing = testing,
                                // 没在结果里出现 = 这台被停用了（引擎只列启用的），
                                // 明确记一条而不是留着旧状态：留着会显示一个
                                // 与当前配置不符的"已连接"。
                                status = config.status + (name to (row ?: McpServerStatus(
                                    name = name,
                                    connected = false,
                                    error = "该服务器已停用，未被测试",
                                ))),
                            ),
                            message = when {
                                row == null -> "已停用，跳过测试：$name"
                                row.connected -> "$name 已连接，发现 ${row.tools.size} 个工具"
                                else -> "$name 连接失败"
                            },
                            messageIsError = row?.connected == false,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            mcpConfig = config.copy(testing = testing),
                            message = "测试失败：${error.message ?: "未知原因"}",
                            messageIsError = true,
                        )
                    },
                )
            }
        }
    }

    /**
     * 打开「从 JSON 导入」对话框。
     */
    fun openMcpImport() = _state.update {
        it.copy(
            mcpConfig = it.mcpConfig?.copy(importText = "", importError = null, form = null),
        )
    }

    fun updateMcpImportText(text: String) = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(importText = text, importError = null))
    }

    fun cancelMcpImport() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(importText = null, importError = null))
    }

    /**
     * 执行导入。同名**跳过不覆盖**（用户可能已经在本机改过那份配置），
     * 认不出的条目也一并报出来 —— 只说"导入完成"会让用户以为全都进来了。
     */
    fun importMcpJson() {
        val text = _state.value.mcpConfig?.importText ?: return
        val result = McpStore.importJson(text)
        result.fold(
            onSuccess = { imported ->
                _state.update {
                    it.copy(
                        mcpConfig = McpStore.read(),
                        message = buildString {
                            append("已导入 ${imported.added.size} 个服务器")
                            if (imported.skipped.isNotEmpty()) {
                                append("，${imported.skipped.size} 个同名已跳过")
                            }
                            if (imported.invalid.isNotEmpty()) {
                                append("，${imported.invalid.size} 个缺少 url/command 未导入")
                            }
                        },
                        messageIsError = imported.added.isEmpty(),
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        mcpConfig = it.mcpConfig?.copy(
                            importError = error.message ?: "无法解析",
                        ),
                    )
                }
            },
        )
    }

    /**
     * 写入某个工具的「启用 / 需要审批」。
     *
     * ⚠️ 写完之后必须把**状态里的那一行**也改掉：引擎侧的过滤要到下次
     * `mcpStatus()` 才会生效，而用户点完开关立刻要看的就是那一行的变化。
     * 只写磁盘不改状态的话，开关会弹回去 —— 看起来像"点了没反应"。
     */
    fun setMcpToolOptions(serverName: String, toolName: String, enabled: Boolean, approval: Boolean) {
        val result = McpStore.setToolOptions(serverName, toolName, enabled, approval)
        _state.update { state ->
            val config = state.mcpConfig ?: return@update state
            val status = config.status[serverName] ?: return@update state
            state.copy(
                mcpConfig = config.copy(
                    status = config.status + (serverName to status.copy(
                        tools = status.tools.map { tool ->
                            if (tool.name == toolName) tool.copy(enabled = enabled, approval = approval) else tool
                        },
                    )),
                ),
                message = if (result.isSuccess) {
                    val tool = status.tools.firstOrNull { it.name == toolName }
                    val label = tool?.let { if (enabled) "已启用" else "已停用" } ?: "已更新"
                    "$toolName：$label" + if (approval) "（调用前需要确认）" else ""
                } else {
                    "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                },
                messageIsError = result.isFailure,
            )
        }
    }

    /**
     * 把当前生效的 API 配置同步到界面摘要，并让引擎重新读取。
     *     * 必须让引擎重读：它内部持有配置副本，不重新下发的话
     * 用户"保存了密钥却仍然报 API key is not configured"。
     */
    private fun syncActiveProfile() {
        val active = ApiConfigStore.active(getApplication())
        _state.update {
            it.copy(
                profileName = active?.name ?: "未配置",
                modelLabel = modelOverride ?: active?.model ?: "未设置",
                apiKeyConfigured = active?.hasKey == true,
            )
        }
        runCatching { engine.configure(engineOverrides()) }
    }

    private fun appendInfo(title: String, body: String) {
        _state.update {
            it.copy(transcript = it.transcript + ChatItem(id = nextId("i"), kind = ChatKind.INFO, title = title, body = body))
        }
    }

    /**
     * 把 **Compose 树之外**发生的事件写进对话流。
     *
     * 目前唯一的来源是 `MainActivity.onResume`：`AndroidIntentBridge.resumePendingApkInstall`
     * 在用户从「安装未知应用」权限页返回后会继续 APK 安装，并可能返回错误
     * （例如 APK 已损坏、系统安装器不可用）。这类结果必须落进对话流，
     * 因为一次性的 Toast 很容易被漏掉，而失败恰恰是最不能漏的。
     */
    fun reportExternalEvent(title: String, body: String) = appendInfo(title, body)

    // ---------- 输入器下拉（权限 / 推理）----------

    /*
     * 输入器页脚的权限 / 推理两个 chip 现在是**下拉菜单**
     * （Miuix `OverlayDropdownPreference`），选中即生效 —— 不需要"选完再提交"
     * 那一层，所以这里只有两个 setter。
     *
     * 副作用与旧实现保持一致：写状态 + 在对话里留一句可追溯的记录。
     */

    fun setPermissionMode(mode: PermissionMode) {
        _state.update { it.copy(permissionMode = mode, message = "权限模式：${mode.label}") }
    }

    fun setEffort(level: EffortLevel) {
        _state.update { it.copy(effort = level, message = "推理强度：${level.label}") }
    }

    /*
     * 下面两个是**斜杠命令**用的（`/permissions`、`/effort`）。
     *
     * 为什么不共用下拉：斜杠命令是在终端/输入框里敲出来的，没有"点一下展开"
     * 的按钮可以挂下拉菜单，只能弹一个居中列表让人挑。所以这两条路都得留。
     */

    fun showPermissionPicker() {
        val values = PermissionMode.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "权限模式",
                    intent = ChoiceIntent.PERMISSION_MODE,
                    options = values.map { mode ->
                        ChoiceOption(
                            label = mode.label,
                            detail = mode.detail,
                            checked = mode == it.permissionMode,
                        )
                    },
                )
            )
        }
    }

    fun showEffortPicker() {
        val values = EffortLevel.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "推理强度",
                    intent = ChoiceIntent.EFFORT,
                    options = values.map { level -> ChoiceOption(level.label, checked = level == it.effort) },
                )
            )
        }
    }

    // ---------- 附加项目文件 ----------

    /**
     * 打开「附加项目文件」面板。
     *
     * 立即跑一次空查询：面板一打开就能看到浅层文件清单，而不是一个空框等用户打字。
     */
    fun openAttachPicker() {
        _state.update { it.copy(attachPickerOpen = true, attachQuery = "") }
        refreshAttachHits("")
    }

    fun closeAttachPicker() =
        _state.update { it.copy(attachPickerOpen = false, attachHits = emptyList()) }

    fun updateAttachQuery(query: String) {
        _state.update { it.copy(attachQuery = query) }
        refreshAttachHits(query)
    }

    /**
     * 在 IO 线程重算搜索结果。
     *
     * ⚠️ 结果是**异步**回来的，所以落回状态前必须确认 [query] 还是当前查询串：
     * 用户打得快时会有多个搜索在飞，慢的那个回来会把新的结果覆盖掉
     * （与「错误串台到新会话」是同一类竞态）。面板关掉后也不该再写。
     */
    private fun refreshAttachHits(query: String) {
        val root = _state.value.projectPath
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) { FileSearch.search(root, query) }
            _state.update { s ->
                if (s.attachQuery == query && s.attachPickerOpen) s.copy(attachHits = hits) else s
            }
        }
    }

    /**
     * 把一个项目文件附加到下一条消息。
     *
     * 走**文本附件**路径（与 [attachSkill] 一样）：内容由 [buildPromptWithTextAttachments]
     * 在发送时包进 `<attached_context>`，而不是灌进输入框 —— 输入框是用户的编辑区。
     *
     * 读取交给 [FileBrowser.readTextForAttachment]，它沿用界面预览那套 256 KB 截断与
     * NUL 二进制判定。所以附加一个几 MB 的日志不会把界面拖死，附加一个 `.so`
     * 也会被明确拒绝而不是塞一坨乱码进上下文。
     *
     * 附加**不关面板**：参考图的说明就是「可多次附加到下一条消息」。
     */
    fun attachProjectFile(path: String, relative: String) {
        viewModelScope.launch {
            val name = File(path).name
            val size = withContext(Dispatchers.IO) { runCatching { File(path).length() }.getOrDefault(0L) }
            val body = withContext(Dispatchers.IO) { FileBrowser.readTextForAttachment(path) }
            if (body == null) {
                _state.update { it.copy(message = "附加失败：$name 不是文本文件或读不了", messageIsError = true) }
                return@launch
            }
            val id = nextId("file")
            _state.update { s ->
                // 同一路径只留一条（用相对路径去重，它在项目内唯一）：
                // 连着点两次应该还是那一份，而不是叠两份进提示词。
                val kept = s.attachments.filterNot { it.detail == relative }
                s.copy(
                    attachments = kept + Attachment(
                        id = id,
                        label = name,
                        detail = relative,
                        isImage = false,
                        textBody = body,
                    ),
                    message = "已附加：$relative（${zhiFormatSize(size)}）",
                )
            }
        }
    }

    /**
     * 打开模型选择面板。
     *
     * 面板先立起来（显示"正在获取"），目录拉取在 IO 线程后台跑；回来后再填列表。
     * 原版就是这个顺序，否则网络慢时点按钮像没反应。
     *
     * 会话正在跑的时候换模型不会打断当前轮：模型写在配置记录里，下一轮 `configure`
     * 自然带上（原版的"下一完整轮生效"）。
     */
    fun showModelPicker() {
        // 直接从存储读那几份 API 记录，而**不是**从 `s.apiConfig` 读：
        // 后者的语义是"API 配置弹窗当前打开"，面板显示时它恰好是 null，
        // 所以那样取值会永远落到 `s.profileName` 兜底上、tab 栏也永远没有数据。
        val config = ApiConfigStore.read(getApplication())
        val active = config.profiles.firstOrNull { it.id == config.activeId }
        _state.update { s ->
            s.copy(
                modelPicker = ModelPickerState(
                    profileName = active?.name ?: s.profileName,
                    currentModel = active?.model?.takeIf { it.isNotBlank() } ?: s.modelLabel,
                    // 面板一打开就先把"要用的模型名"填成当前生效的那个：
                    // 高亮看的正是这个字段，不填的话刚打开时一行都不高亮。
                    query = active?.model?.takeIf { it.isNotBlank() } ?: s.modelLabel,
                    profiles = config.profiles.map { ModelProfileTab(it.id, it.name) },
                    activeProfileId = config.activeId,
                ),
            )
        }
        fetchModelCatalog()
    }

    fun closeModelPicker() {
        modelCatalogJob?.cancel()
        modelCatalogJob = null
        modelSaveJob?.cancel()
        modelSaveJob = null
        _state.update { it.copy(modelPicker = null) }
    }

    /**
     * 切换面板里 tab 选中的那份 API 记录，并重新拉它自己的模型目录。
     *
     * ## 为什么不复用 [selectApiProfile]
     *
     * 那个函数会写 `apiConfig = state`，而 `apiConfig != null` 在本工程里的语义是
     * **"API 配置弹窗当前打开"**（`AppScaffold` 用它决定显示哪个弹窗）。在模型面板里
     * 调它就会在面板上凭空弹出一张配置页。这里只更新面板自己那份状态。
     */
    fun selectModelPickerProfile(profileId: String) {
        // 换配置就用那份配置自己的模型，别把上一条配置的会话级覆盖带过去。
        // 与 selectApiProfile 同一处理。
        modelOverride = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.select(getApplication(), profileId)
            val config = ApiConfigStore.read(getApplication())
            val active = config.profiles.firstOrNull { it.id == config.activeId }
            _state.update { s ->
                // 面板可能已经被关掉了：那就只同步摘要，不要把状态又塞回去。
                val picker = s.modelPicker ?: return@update s
                s.copy(
                    modelPicker = picker.copy(
                        profileName = active?.name ?: picker.profileName,
                        currentModel = active?.model.orEmpty(),
                        // 换配置后旧配置的模型名不能留在框里 —— 那会让「完成」把
                        // A 家的模型名写到 B 家的配置上。清空比留错值安全。
                        query = active?.model.orEmpty(),
                        profiles = config.profiles.map { ModelProfileTab(it.id, it.name) },
                        activeProfileId = config.activeId,
                        loading = true,
                        status = "正在从当前 API 获取模型…",
                        models = emptyList(),
                    ),
                    message = if (result.isSuccess) "已切换 API 配置"
                    else "切换失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            // 面板还开着才需要重新拉目录；关掉了就不必再发一次请求。
            if (_state.value.modelPicker != null) fetchModelCatalog()
        }
        // 摘要与引擎配置要立刻跟上，否则底栏还显示旧配置名。
        syncActiveProfile()
        syncRoleCardFromStore()
    }

    fun setModelQuery(text: String) {
        _state.update { s -> s.copy(modelPicker = s.modelPicker?.copy(query = text)) }
    }

    /**
     * 重新拉一次模型目录（面板里额度卡片的「刷新」走这里）。
     *
     * 面板没开时直接返回：没有面板可更新，发出去的请求就只是白跑一趟。
     * 拉取本身复用 [fetchModelCatalog]，它已经把 loading 态与失败态都处理好了。
     */
    fun refreshModelCatalog() {
        if (_state.value.modelPicker == null) return
        _state.update { s ->
            s.copy(modelPicker = s.modelPicker?.copy(loading = true, status = "正在刷新模型与额度…"))
        }
        fetchModelCatalog()
    }

    private fun fetchModelCatalog() {
        modelCatalogJob?.cancel()
        modelCatalogJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ModelCatalogStore.fetch(getApplication()) }
            val fetched = result.getOrNull()
            _state.update { s ->
                // 面板可能已经被关掉了：这时不要把状态又塞回去。
                val picker = s.modelPicker ?: return@update s
                s.copy(
                    modelPicker = if (fetched == null) {
                        picker.copy(
                            loading = false,
                            status = ModelCatalogStore.friendlyError(result.exceptionOrNull()),
                            models = emptyList(),
                            // 列表没拿到，额度卡片也一并清掉：留着上一次的数字
                            // 会让人以为那是当前的（它可能已经是刷新前的旧值）。
                            quota = emptyList(),
                            quotaError = "",
                            modelsNote = "",
                        )
                    } else {
                        picker.copy(
                            loading = false,
                            status = if (fetched.models.isEmpty()) "API 未返回可用模型，可手动输入"
                            else "已获取 ${fetched.models.size} 个模型",
                            models = fetched.models,
                            quota = fetched.quota,
                            // 额度失败**不**让整次读取失败（见 ModelCatalogStore.Catalog）：
                            // 模型列表照旧可用，原因只写在额度卡片里。
                            quotaError = fetched.quotaError,
                            modelsNote = fetched.note,
                        )
                    },
                )
            }
        }
    }

    /**
     * 在列表里**点某一行**：立刻选中它并保存，但**不关面板**。
     *
     * 与 [applySelectedModel] 的区别只有"关不关面板"这一件事，但这一点决定了观感：
     * 之前只有点底部按钮才写配置，于是高亮要等那一下才动 —— 用户点了一行却看不到
     * 任何反馈，会以为没点上。现在点哪行哪行立刻变蓝，配置同时也已经存好了。
     *
     * 保存做成 **latest-wins**：连点几下时取消上一个写入任务。不取消的话多个 IO 写入
     * 并发进行、完成顺序不定，最后落盘的可能是中间那一次点到的模型 —— 而界面上显示的
     * 却是最后一次点的那个，两边就此分叉。
     */
    fun selectModel(model: String) {
        val target = model.trim()
        if (target.isEmpty()) return
        // 先把界面上那一份改掉，点击的反馈必须是**同步**的（等 IO 回来才变蓝会顿一下）。
        // 用于高亮的 `query` 就是"要用的模型名"，所以改它等于把高亮挪过去。
        _state.update { s -> s.copy(modelPicker = s.modelPicker?.copy(query = target)) }
        persistModel(target)
    }

    /**
     * 应用选定的模型：写进当前配置记录并重跑 `configure`，让下一次请求就用上新模型。
     *
     * 这里**不**走 `engineOverrides().model`：模型属于配置记录的持久化内容，
     * 只做一次内存覆盖会让"底栏显示的模型"和"实际请求的模型"在下一次启动后分叉。
     */
    fun applySelectedModel(model: String) {
        val target = model.trim()
        if (target.isEmpty()) return
        closeModelPicker()
        persistModel(target)
    }

    /** [selectModel] 与 [applySelectedModel] 共用的落盘部分（区别只在调用方关不关面板）。 */
    private fun persistModel(target: String) {
        modelOverride = target
        modelSaveJob?.cancel()
        modelSaveJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ApiConfigStore.setDefaultModel(getApplication(), target) }
            // 覆盖值先生效，所以即使写配置失败模型也已经切换了；把这一点如实说出来，
            // 而不是报一个"保存失败"让人以为模型没换。
            syncActiveProfile()
            syncRoleCardFromStore()
            _state.update {
                it.copy(
                    message = if (result.isSuccess) "模型：$target"
                    else "模型：$target（本次会话已生效，未能写入配置：${result.exceptionOrNull()?.message ?: "未知原因"}）",
                )
            }
        }
    }

    fun showMessageActions(item: ChatItem) {
        val options = when (item.kind) {
            ChatKind.USER -> listOf("复制", "再次发送", "编辑后发送")
            ChatKind.ASSISTANT, ChatKind.ERROR -> listOf("复制", "重试上一问")
            else -> listOf("复制", if (item.groupCompleted) "已全部完成" else "执行中")
        }
        // 与 showSessionActions 同理：动作在**选择器回调**里执行，那时拿到的只有选项文案，
        // 不记住来源就会"复制了别的消息"。
        pendingMessageAction = item
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "消息操作",
                    intent = ChoiceIntent.MESSAGE_ACTION,
                    // 锚到这条消息自己：列表里只有它的 Box 会认领这份菜单
                    anchorId = item.id,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    // ---------- 剪贴板 ----------

    /**
     * 复制一条消息的可见文本。
     *
     * 复制的是用户**看得到的内容**：助手消息取正文（没有正文时退回思考内容），
     * 工具组则是把各工具的标题拼起来——复制一个空串对用户毫无用处。
     */
    fun copyMessage(item: ChatItem) {
        val text = when (item.kind) {
            ChatKind.ASSISTANT, ChatKind.ERROR -> item.body.ifBlank { item.thinking }
            // 工具组没有正文，把各工具的展示名与摘要拼起来——复制空串对用户没用。
            ChatKind.TOOL_GROUP -> item.tools.joinToString("\n") { tool ->
                listOfNotNull(tool.displayName.takeIf { it.isNotBlank() }, tool.summary.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            }
            else -> item.body
        }
        copyText(text, "智蛛消息")
    }

    /** `/copy`：复制最近一条助手回复。 */
    fun copyLatestAssistantReply() {
        val latest = _state.value.transcript.lastOrNull {
            it.kind == ChatKind.ASSISTANT && it.body.isNotBlank()
        }
        if (latest == null) {
            _state.update { it.copy(message = "还没有可复制的回复", messageIsError = true) }
            return
        }
        copyText(latest.body, "智蛛回复")
    }

    private fun copyText(text: String, label: String) {
        val ok = Clipboard.copy(getApplication(), label, text)
        _state.update {
            it.copy(
                message = when {
                    !ok -> "复制失败：内容为空或剪贴板不可用"
                    // Android 13 起系统自己会弹「已复制」，应用再弹一次就是重复打扰。
                    Clipboard.systemShowsCopyToast() -> ""
                    else -> "已复制到剪贴板"
                },
            )
        }
    }

    fun showSessionActions(session: SessionSummary) {
        // 记住操作对象：动作是在**选择器回调**里执行的，那时只能拿到选项文案，
        // 拿不到是哪条会话 —— 不记住就会出现"删掉了当前会话"这种错删。
        pendingSessionAction = session
        val options = if (session.note.isEmpty()) listOf("编辑备注", "恢复会话", "删除会话")
        else listOf("编辑备注", "清除备注", "恢复会话", "删除会话")
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = session.title,
                    intent = ChoiceIntent.SESSION_ACTION,
                    // 锚到这条会话自己：侧栏只有它的 Box 会认领这份菜单
                    anchorId = session.id,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    /** 打开备注编辑：没有选项，只靠自由输入提交。 */
    private fun editSessionNote(session: SessionSummary) {
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "会话备注",
                    intent = ChoiceIntent.SESSION_NOTE,
                    prompt = "给这条会话写一句备注，方便以后在侧栏里认出来。",
                    options = emptyList(),
                    allowFreeForm = true,
                    freeFormHint = if (session.note.isEmpty()) "例如：修好了相册崩溃" else session.note,
                ),
            )
        }
    }

    /** 写入备注到磁盘。空字符串表示清除。 */
    private fun saveSessionNote(session: SessionSummary, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(session.id)
            // titleOverride 传空串 = 不覆盖标题，只改备注。
            val ok = SessionReader.updateMetadata(file, note, "")
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update {
                it.copy(
                    sessions = sessions,
                    message = if (ok) {
                        if (note.isEmpty()) "已清除备注" else "备注已保存"
                    } else {
                        "备注保存失败"
                    },
                )
            }
        }
    }

    fun dismissChoicePicker() {
        // 提问流程的「取消」必须把空答案回执给引擎，否则引擎会一直等
        // （`QuestionGate.ask` 的闩锁要等 30 分钟才超时）。
        if (_state.value.choicePicker?.intent == ChoiceIntent.QUESTION) {
            cancelQuestions()
            return
        }
        _state.update { it.copy(choicePicker = null) }
    }

    /**
     * 提交选择器里选中的项。
     *
     * [indices] 是选中的下标列表：单选选择器只会有 0 或 1 个，
     * 多选的提问流程会有多个。统一用列表是为了让两条路径共用一套 UI 回调。
     */
    fun onSubmitSelection(indices: List<Int>) {
        val picker = _state.value.choicePicker ?: return
        // 提问流程走自己的分步状态机，答案要按问题原文累积。
        if (picker.intent == ChoiceIntent.QUESTION) {
            val labels = indices.mapNotNull { picker.options.getOrNull(it)?.label }
            val answer: Any? = when {
                labels.isEmpty() -> null
                picker.multiSelect -> JSONArray(labels)
                else -> labels.first()
            }
            advanceQuestion(answer)
            return
        }
        // 单选选择器语义不变：等价于点第一项。
        onChoiceSelected(indices.firstOrNull() ?: -1)
    }

    fun onChoiceSelected(index: Int) {
        val picker = _state.value.choicePicker ?: return
        val option = picker.options.getOrNull(index) ?: return
        when (picker.intent) {
            // 这两条只服务斜杠命令 `/permissions`、`/effort`（见 showPermissionPicker）。
            // 输入器上的入口是下拉菜单，走 setPermissionMode / setEffort，不经过这里。
            ChoiceIntent.PERMISSION_MODE -> {
                val mode = PermissionMode.entries.getOrNull(index)
                if (mode != null) _state.update {
                    it.copy(choicePicker = null, permissionMode = mode, message = "权限模式：${mode.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.EFFORT -> {
                val level = EffortLevel.entries.getOrNull(index)
                if (level != null) _state.update {
                    it.copy(choicePicker = null, effort = level, message = "推理强度：${level.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            // 这里**没有** MODEL：模型面板要异步拉目录并写回配置记录，
            // 用 5 个固定选项的通用选择器表达不了，已换成 ui/dialogs/ModelPickerOverlay。
            ChoiceIntent.MESSAGE_ACTION -> {
                val target = pendingMessageAction
                pendingMessageAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到来源就不猜：宁可无反应，也不能复制到别的消息。
                    _state.update { it.copy(message = "找不到目标消息，操作已取消") }
                } else when (option.label) {
                    "复制" -> copyMessage(target)
                    "再次发送" -> {
                        // 复用用户消息的原文重发；工具组这类没有正文的目标不支持。
                        if (target.body.isBlank()) _state.update { it.copy(message = "这条消息没有可重发的内容", messageIsError = true) }
                        else {
                            _state.update { it.copy(composerText = target.body) }
                            send()
                        }
                    }
                    "编辑后发送" -> _state.update { it.copy(composerText = target.body, message = "已放回输入框，可编辑后发送") }
                    "重试上一问" -> retryLastUserPrompt()
                    // 这两种是状态展示项，点了不做任何事。
                    "已全部完成", "执行中" -> Unit
                    else -> _state.update { it.copy(message = "不支持的操作：${option.label}") }
                }
            }
            ChoiceIntent.SESSION_ACTION -> {
                val target = pendingSessionAction
                pendingSessionAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到操作对象就什么都不做。宁可无反应，也不能猜一条会话删掉。
                    _state.update { it.copy(message = "找不到目标会话，操作已取消") }
                } else when (option.label) {
                    "删除会话" -> deleteSession(target.id)
                    "恢复会话" -> openSession(target)
                    "编辑备注" -> editSessionNote(target)
                    "清除备注" -> saveSessionNote(target, "")
                    else -> _state.update { it.copy(message = "${option.label}（未接线）") }
                }
            }
            ChoiceIntent.SESSION_NOTE -> {
                // 这个分支正常不会走到（备注靠自由输入提交），留作兜底。
                _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.QUESTION -> {
                // 提问流程正常走 onSubmitSelection / dismissChoicePicker；
                // 单选回调真落到这里时按「跳过本步」处理，不要静默什么都不做。
                advanceQuestion(null)
            }
            // 通用选择器：没有任何附加语义，选中只意味着"用户做了选择"。
            // 这里**不能**再打印"已执行 xxx（Mock）"——那会让人以为某个动作发生了。
            // 附件也不再走这里：它已经有真正的系统图片选择器（attachImage）。
            ChoiceIntent.GENERIC -> _state.update {
                it.copy(choicePicker = null, message = "已选择：${option.label}")
            }
            // 计划模式下的「目标澄清」：用户选定的目标本身就是一条提示词。
            // 计划正文由引擎产出（不再由界面拼），所以这里只是把它发出去。
            ChoiceIntent.PLAN_GOAL -> {
                val goal = if (option.detail.isNotEmpty()) "${option.label} —— ${option.detail}" else option.label
                _state.update { it.copy(choicePicker = null, composerText = goal) }
                send()
            }
        }
    }

    /**
     * 选择窗口里的「其他回答…」提交：不走选项，直接把这段自由文本当作输入。
     * 在计划模式下它等价于"自定义目标"，会作为提示词发给引擎；
     * 在会话备注中它是备注正文。
     */
    fun onSubmitFreeForm(text: String) {
        val trimmed = text.trim()
        val picker = _state.value.choicePicker
        if (picker == null) return
        // 备注可以提交空字符串（= 清除），所以不能和"空输入就关闭"混在一起。
        if (picker.intent == ChoiceIntent.SESSION_NOTE) {
            val target = pendingSessionAction
            pendingSessionAction = null
            _state.update { it.copy(choicePicker = null) }
            if (target == null) {
                _state.update { it.copy(message = "找不到目标会话，备注未保存") }
            } else {
                saveSessionNote(target, trimmed)
            }
            return
        }
        if (trimmed.isEmpty()) {
            dismissChoicePicker()
            return
        }
        if (picker.intent == ChoiceIntent.PLAN_GOAL) {
            _state.update { it.copy(choicePicker = null, composerText = trimmed) }
            send()
            return
        }
        _state.update {
            it.copy(
                choicePicker = null,
                composerText = trimmed,
                slashQuery = null,
                slashMatches = emptyList(),
                message = null,
            )
        }
    }

    // ---------- 会话 ----------

    /**
     * 新建会话。
     *
     * 这里只重置引擎的对话上下文（新 workflowId），**不**立即建文件：
     * 引擎的 `SessionStore` 是在第一轮发消息时才创建的，提前建会留下
     * 一堆"0 条消息"的空会话文件，侧栏会很快被垃圾填满。
     * 所以界面上先插入一个占位条目，等第一轮结束后 [refreshSessions] 会用真实文件替换它。
     */
    fun newSession() {
        engine.resetConversation()
        discardStreamingAssistant()
        // 上一轮即使还在跑也已经不属于任何会话了，清掉归属标记避免误拦。
        turnSessionId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null
        _state.update {
            it.copy(
                // 占位条目的 id 用空串而不是伪造 id：它不是真实文件，
                // 若被当成路径去打开会失败，空串能让"打不开"这件事显式化。
                activeSessionId = "",
                transcript = emptyList(),
                tasks = emptyList(),
                contextTokens = 0,
                composerBusy = false,
                workingStatus = null,
                tab = WorkspaceTab.CHAT,
                sidebarOpen = false,
                message = "已开始新会话，第一条消息会创建会话文件",
            )
        }
    }

    /**
     * 打开一条历史会话。
     *
     * 两件事都要做，缺一不可：
     * 1. `engine.resumeConversation(file)` —— 让**引擎**把历史装回 provider 上下文，
     *    否则界面上看得到历史、模型却不知道，追问会答非所问；
     * 2. 从同一个 JSONL 重建界面 transcript。
     *
     * 文件读取放 IO 线程：一个长会话的 JSONL 可能有几千行，在主线程读会卡住界面。
     */
    fun openSession(session: SessionSummary) {
        val file = File(session.id)
        if (!file.isFile) {
            _state.update { it.copy(message = "会话文件不存在：${session.id}", messageIsError = true) }
            refreshSessions()
            return
        }
        // 切会话必须先把引擎这一轮停掉：否则旧会话的回调还会继续往新的
        // transcript 里写（引擎的 cancel 会连带取消三个门控）。
        engine.cancel()
        discardStreamingAssistant()
        // 切走会话：上一轮的迟到事件一律不应再落到新会话里。
        // 注意这里**不要**把 turnSessionId 置 null —— 置空等于关掉拦截。
        // 保留了旧值，[finishTurnWithError] 才能看出"归属 ≠ 当前"并丢弃。
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null

        _state.update { it.copy(sidebarOpen = false, tab = WorkspaceTab.CHAT, workingStatus = "正在载入会话…") }
        viewModelScope.launch(Dispatchers.IO) {
            val transcript = SessionReader.transcript(file)
            val tasks = SessionReader.tasks(file)
            // 引擎必须先配置好才有 config 可用，否则 resumeConversation 不会恢复项目/工作流。
            val resumed = runCatching {
                engine.configure(engineOverrides())
                engine.resumeConversation(file)
            }
            _state.update {
                it.copy(
                    activeSessionId = session.id,
                    transcript = transcript,
                    tasks = tasks,
                    contextTokens = engine.estimateContextTokens(),
                    contextWindow = engine.contextWindowTokens().takeIf { w -> w > 0 } ?: it.contextWindow,
                    composerBusy = false,
                    workingStatus = null,
                    message = resumed.exceptionOrNull()?.let { error ->
                        "会话历史已显示，但引擎恢复失败：" + (error.message ?: "未知原因")
                    },
                )
            }
        }
    }

    /**
     * 删除会话。
     *
     * 删除的是**磁盘上的文件**，不是列表里的一项 —— 只从列表移除的话，
     * 下次启动它又会回来，用户会以为删除没生效。
     */
    fun deleteSession(id: String) {
        if (id.isEmpty()) {
            // 尚未落盘的占位会话：直接清掉当前视图即可。
            _state.update { it.copy(activeSessionId = "", transcript = emptyList(), tasks = emptyList(), message = "会话已清空") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val removed = SessionReader.delete(File(id))
            val wasActive = _state.value.activeSessionId == id
            if (wasActive) {
                engine.resetConversation()
                discardStreamingAssistant()
            }
            val sessions = SessionReader.list(_state.value.projectPath)
            val next = sessions.firstOrNull()
            _state.update {
                it.copy(
                    sessions = sessions,
                    activeSessionId = if (wasActive) "" else it.activeSessionId,
                    transcript = if (wasActive) emptyList() else it.transcript,
                    tasks = if (wasActive) emptyList() else it.tasks,
                    message = if (removed) "会话已删除" else "删除失败：文件可能已被移除",
                )
            }
            // 删除后仍留历史时，顺手把最近一条打开，避免出现"删完一片空白"。
            if (wasActive && next != null) openSession(next)
        }
    }

    /** 从磁盘重新读取会话列表（新建/发消息/删除之后调用）。 */
    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { state ->
                // 保留"尚未落盘的新会话"这一状态：此刻 activeSessionId 为空且
                // 列表里还没有对应文件，不能被列表刷新覆盖掉。
                state.copy(sessions = sessions)
            }
        }
    }



    fun openSidebar() {
        // 每次拉开侧栏都重读一次文件列表：新会话是在**第一轮消息之后**才落盘的，
        // 不刷新的话"刚聊完的会话"在侧栏里看不到。
        refreshSessions()
        _state.update { it.copy(sidebarOpen = true) }
    }
    fun closeSidebar() = _state.update { it.copy(sidebarOpen = false) }

    /**
     * 侧栏里的每一行在打开目标页面之前都要调这个。
     *
     * ## 为什么它值得一个专门的函数
     *
     * 侧栏是画在内容**上层**的浮层（见 `AppScaffold` 的 Sidebar 分支），
     * 而各个目标的 `openXxx()` 只负责把自己那面页推上来，谁也没管侧栏。
     * 于是：点「设置」→ 设置页铺满，但**侧栏还盖在上面**；点「环境」同理；
     * 点「ZhiCode 沙箱」更明显（那是个跨进程的 Activity）。
     * 用户看到的是一层"点不动"的界面，得先返回一次才回到目标页 ——
     * 症状是"点了没反应"，根因只是少关一层。
     *
     * 唯一例外是「新会话」：它本来就自己设了 `sidebarOpen = false`（见 [newSession]）。
     *
     * ## 为什么不写成"每个 openXxx 里各加一行"
     *
     * 那正是它坏掉的原因：四个入口各写一遍，加第五个时必然漏。
     * 收口在这里之后，新增入口只有一条路可走。
     */
    private fun hideSidebarForNavigation() {
        _state.update { it.copy(sidebarOpen = false) }
    }


    // ---------- 工作区 ----------

    fun selectTab(tab: WorkspaceTab) {
        _state.update { it.copy(tab = tab) }
        // 进入「变更」页时才去读 git。切换 Tab 是明确的用户动作，
        // 每次切过去读一次是合理的；若挂在回合结束自动读，大仓库会明显拖慢对话。
        // 「变更」Tab 已移除
    }

    /**
     * 重新拉一次变更列表（变更面板的「刷新」）。
     *
     * 对应原版 `refreshChanges()`（重跑 `git status --short` + `git diff`）。
     * 这里数据来自 [repo]，所以"刷新"等价于重新读一次。
     */
    /**
     * 刷新 git 变更。
     *
     * 三条 git 命令（rev-parse / status / diff）+ 未跟踪文件的补丁，必须放 IO 线程，
     * 而且这是**唯一**会去读 git 的入口 —— 不在回合结束时自动跑：
     * 一个大仓库的 `git diff` 可能几秒，挂在每个回合结尾会让对话明显变卡。
     */
    fun refreshDiff() {
        val path = _state.value.projectPath
        _state.update { it.copy(diff = it.diff.copy(loading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { GitChanges.read(getApplication(), path) }
                .getOrElse { error ->
                    DiffState(note = "读取 git 变更失败：" + (error.message ?: "未知原因"))
                }
            _state.update {
                it.copy(
                    diff = result,
                    message = when {
                        result.note.isNotEmpty() -> result.note
                        result.files.isEmpty() -> "工作区没有未提交的变更"
                        else -> "已刷新变更列表（${result.files.size} 个文件）"
                    },
                )
            }
        }
    }

    fun cycleThemeMode() {
        var next = ThemeMode.SYSTEM
        _state.update {
            next = when (it.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            it.copy(themeMode = next, message = "主题：${next.label}")
        }
        persistTheme(next)
    }

    // ---------- 内置 Termux 运行环境 ----------

    /**
     * 追加「内置环境版本」一节到自检报告。
     *
     * 为什么要单独列出来：以前换 APK 之后 `usr` 会悄悄停在旧的那份，界面上完全看不出来
     * （`isInstalled()` 只查文件在不在）。现在自检报告里能直接读到"已装哪版 / 内置哪版"，
     * 不一致时还给出说明 —— 排障时一眼能定位。
     */
    private fun bootstrapVersionSection(): String {
        val installed = runCatching { installer.installedVersion() }.getOrNull()
        val bundled = runCatching { installer.bundledVersion() }.getOrNull() ?: return ""
        val same = runCatching { installer.isUpToDate() }.getOrDefault(false)
        return buildString {
            append("\n\n## 内置环境版本\n")
            append("- 已装：").append(installed ?: "（无标记文件，视为未初始化）").append('\n')
            append("- 内置：").append(bundled).append('\n')
            append(
                if (same) "- 状态：一致 ✅"
                else "- 状态：**不一致** — 启动时会自动重装 `usr`（`home` 不受影响，\n" +
                    "  但用 apt 装过的包会丢，因为它们在 `usr` 里）"
            )
        }
    }

    /**
     * 生成自检报告。
     *
     * 刻意**绝不抛异常**：这是排障入口，它自己崩掉或什么都不显示，就等于把唯一的诊断手段也弄没了。
     * 探测失败时把失败原因当作报告正文显示出来，至少还能看到是哪一项炸的。
     */
    private fun buildEnvironmentReport(): String = runCatching {
        EnvDoctor.report(getApplication()) + bootstrapVersionSection()
    }.getOrElse { error ->
        "# ZhiCode · 环境自检\n\n" +
            "自检本身失败了：\n" +
            "${error.javaClass.name}: ${error.message}\n\n" +
            "堆栈：\n" +
            error.stackTraceToString() +
            "\n— 报告结束，可整段复制"
    }

    /** 打开「环境自检」：现场跑一遍探测并生成可复制的报告。 */
    fun openEnvironment() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentOpen = true,
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    fun closeEnvironment() = _state.update { it.copy(environmentOpen = false) }

    /** 重新生成自检报告（安装前后各跑一次，便于对比）。 */
    fun refreshEnvironmentReport() {
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    /**
     * 安装（或重新初始化）内置 Termux 环境。
     *
     * 解压 32MB、落 3473 个文件、建 1177 个符号链接，必须放到 IO 线程，
     * 并且把进度实时回灌给 UI —— 整个过程在真机上要十几秒，不给反馈会被当成卡死。
     */
    fun installRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update {
            it.copy(runtimeInstalling = true, runtimeProgress = 0, runtimeMessage = "正在准备…")
        }
        viewModelScope.launch(Dispatchers.IO) { installRuntimeBlocking("正在准备…") }
    }

    /**
     * 真正干活的那一段。**必须在 IO 线程调用**（解压 33MB、建 2689 个符号链接）。
     *
     * 抽出来是因为有两处入口：用户手动点「初始化」（[installRuntime]）与启动时的
     * 版本过期自动重装（[autoInstallRuntimeIfStale]）。两处必须走同一段逻辑 ——
     * 分别写一份必然走样（一处忘了 `ensureWorkspace()`，另一处忘了更新报告）。
     */
    private suspend fun installRuntimeBlocking(preparing: String) {
        // 装之前必须先关终端：install() 的 activatePrefix() 会把整个 usr 目录 rename 换掉，
        // 正在跑的 bash 会立刻指向一个已不存在的前缀（`/bin/bash` 还在内存里，
        // 但它 fork 出的任何东西都会失败）。切换要在主线程做，所以这里显式切过去等它。
        withContext(Dispatchers.Main) { releaseTerminalPane() }

        _state.update { it.copy(runtimeMessage = preparing) }
        val failure = installRuntimeReporting()
        if (failure != null) {
            appendRuntimeLog("安装失败：$failure")
            _state.update { it.copy(runtimeInstalling = false, runtimeMessage = "初始化失败：$failure") }
            return
        }
        appendRuntimeLog("安装完成：已装=${runCatching { installer.installedVersion() }.getOrNull() ?: "(无)"}")
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                runtimeInstalling = false,
                runtimeProgress = 100,
                runtimeMessage = "内置 Termux 环境已就绪",
                runtimeReady = installer.isInstalled(),
                environmentReport = reportText,
            )
        }
        // home 目录是随运行环境一起出现的，所以工作区只能在这之后创建。
        // 不创建的话：变更面板永远显示"项目目录不存在"，
        // 终端的工作目录会回退到 home，会话也会落在一个不存在的项目键下。
        ensureWorkspace()
    }

    /**
     * 跑一次安装，失败时返回一句人话原因（成功返回 `null`）。
     *
     * 把"失败原因"作为返回值而不是就地写状态：调用方还需要把同一句话写进日志，
     * 两处分别拼一遍字符串必然走样。
     */
    private suspend fun installRuntimeReporting(): String? = runCatching {
        installer.install { message, percent ->
            _state.update { it.copy(runtimeMessage = message, runtimeProgress = percent) }
        }
    }.exceptionOrNull()?.let { error ->
        error.message ?: error.javaClass.simpleName
    }

    /** 幂等修复：清半成品目录、补齐 apt/dpkg 兼容层。适用于 apt 升级过或上次中断。 */
    fun repairRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update { it.copy(runtimeInstalling = true, runtimeMessage = "正在修复…") }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { installer.repairIfInstalled() }.isSuccess
            val reportText = buildEnvironmentReport()
            _state.update {
                it.copy(
                    runtimeInstalling = false,
                    runtimeMessage = if (ok) "已修复" else "修复失败",
                    runtimeReady = installer.isInstalled(),
                    environmentReport = reportText,
                )
            }
        }
    }

    /** 复制自检报告到剪贴板，便于把真机现状整段发出来定位问题。 */
    fun copyEnvironmentReport(): Boolean {
        val app = getApplication<Application>()
        val text = _state.value.environmentReport.ifBlank { buildEnvironmentReport() }
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("ZhiCode 环境自检", text))
        _state.update { it.copy(message = "环境自检报告已复制") }
        return true
    }

    // ---------- 设置 ----------

    /**
     * 打开设置弹窗：从当前 state 快照一份 draft。
     *
     * 之后的编辑全部落在 draft 上，「取消」丢弃 draft 即可，不需要回滚任何东西。
     */
    fun openSettings() = _state.update {
        it.copy(
            settingsOpen = true,
            // 见 hideSidebarForNavigation：点侧栏里的入口要先收起侧栏。
            sidebarOpen = false,
            settingsDraft = SettingsDraft.from(it),
        )
    }

    fun closeSettings() = _state.update { it.copy(settingsDraft = null, settingsOpen = false) }

    /**
     * 打开「UI 调试」整页（**仅 debug 构建**）。
     *
     * 刻意不动 [openSettings] 的 draft：从设置页进来时它已经垫在栈底，
     * 返回时 draft 原样还在，所以调试完回到设置页不会丢未保存的改动。
     */
    fun openUiDebug() {
        // 见 hideSidebarForNavigation。
        hideSidebarForNavigation()
        _state.update { it.copy(uiDebugOpen = true) }
    }

    /** 关闭 UI 调试页。若它是由设置页打开的，设置主页会随之露出来（栈自动回退一层）。 */
    fun closeUiDebug() = _state.update { it.copy(uiDebugOpen = false) }

    /**
     * 重置 UI 调试页的样例数据。
     *
     * 只递增令牌：页面用 `remember(token)` 重建自己那份样例（含秒表状态）。
     * 样例数据是纯调试物，不进持久化状态，也不该让 ViewModel 认识它的结构。
     */
    fun requestUiDebugReset() = _state.update { it.copy(uiDebugResetToken = it.uiDebugResetToken + 1) }

    /**
     * 开关**全局调试浮层**。
     *
     * 打开时默认**展开**（用户按下开关就是要看东西，先给他面板），
     * 关闭时把展开态一起复位，下次打开仍是展开 —— 免得留下"上次收起"的隐式记忆。
     */
    fun setDebugOverlayEnabled(enabled: Boolean) = _state.update {
        it.copy(debugOverlayEnabled = enabled, debugOverlayExpanded = enabled)
    }

    /** 展开 / 收起调试浮层（收起态是贴边的窄药丸）。 */
    fun setDebugOverlayExpanded(expanded: Boolean) = _state.update {
        it.copy(debugOverlayExpanded = expanded)
    }

    /**
     * 开关**主体调试模式**：真实界面就地显示调试信息（不另开页面）。
     *
     * 同时把当前 API 配置切到「调试 · 本地模拟」**并记住原来那条**：
     * 这条配置的协议是脚本化传输（不发网络、按脚本产回复、工具照常真实执行），
     * 于是"发一句话就能走完整条对话流"——包括流式正文、思考、工具行、
     * 权限确认、计划审批、任务卡。关掉时切回原来那条。
     *
     * 之所以要真的切配置而不是在引擎里加开关：引擎读的就是"当前生效的那条配置"，
     * 走真实链路才能保证调试出来的行为与真正用起来一致（见 [DebugApiProfile]）。
     */
    fun setDebugAppMode(enabled: Boolean) {
        // 双保险：开关本身在 debug 独有页面里，但这里再挡一道 ——
        // 发布包绝不该因为某处误接线而切到脚本化传输。
        if (!com.zhizhu.zhicode.compose.BuildConfig.DEBUG) return
        if (!enabled) {
            val previous = _state.value.debugPreviousProfileId
            _state.update { it.copy(debugAppMode = false, debugPreviousProfileId = "") }
            val restored = if (previous.isBlank()) {
                Result.failure(IllegalStateException("没有记录到原来的配置"))
            } else {
                DebugApiProfile.restore(getApplication(), previous)
            }
            syncActiveProfile()
            _state.update {
                it.copy(
                    message = if (restored.isSuccess) "已退出调试模式，API 配置还原为「${it.profileName}」"
                    else "已退出调试模式，但没能切回原配置（${restored.exceptionOrNull()?.message ?: "未知原因"}）；" +
                        "请在「API 配置记录」里手动选一条",
                )
            }
            return
        }
        // 先记下当前生效的那条，再切走 —— 顺序反了会记成调试配置自己。
        val previous = runCatching { ApiConfigStore.active(getApplication())?.profileId }.getOrNull().orEmpty()
        val activated = DebugApiProfile.activate(getApplication())
        _state.update {
            it.copy(
                debugAppMode = true,
                debugPreviousProfileId = if (activated.isSuccess) previous else "",
            )
        }
        syncActiveProfile()
        _state.update {
            it.copy(
                message = if (activated.isSuccess) {
                    "调试模式已开启：当前 API 切到「${DebugApiProfile.NAME}」，发消息不会出网，" +
                        "工具仍按真实链路执行"
                } else {
                    "调试模式已开启，但没能写入调试 API 配置（${activated.exceptionOrNull()?.message ?: "未知原因"}）；" +
                        "请到「API 配置记录」手动新增一条，协议选「调试 · 本地模拟（无网络）」"
                },
            )
        }
    }

    /**
     * 设置页的**唯一**改动入口：既更新 draft（页面显示的来源），又立刻写回 state。
     *
     * 设置页改成自动保存后就不再需要「保存 / 取消」两个按钮：改动即时生效，
     * 返回只是关页面。draft 仍然保留 —— 页面用它做显示源，
     * 某些字段（如项目目录留空）的「未改动」语义也靠它。
     *
     * ⚠️ 这里**必须**走 [SettingsDraft.applyTo] 而不是各自 `copy`：`applyTo` 里有
     * `clampWebResults` / `projectPath.ifBlank` 这些归一化，绕过它就等于把用户
     * 原始输入直接塞进引擎配置。
     */
    fun applySettingsDraft(draft: SettingsDraft) {
        _state.update { draft.applyTo(it, keepDraft = true) }
        // 主题与搜索密钥**不在** SessionConfig 里，configure() 那次落盘罩不到它们，
        // 各自在这里补一次（密钥走加密槽，见 ApiSettingsStore.setWebSearchKey）。
        persistTheme(draft.themeMode)
        persistWebSearchKey(draft)
        // 终端输入类型也不在 SessionConfig 里（终端是独立 Activity，不读会话配置），
        // 所以单独落盘。
        runCatching {
            ApiSettingsStore.setTerminalCharMode(getApplication(), draft.terminalCharMode)
        }
    }

    /**
     * 把主题写回磁盘。
     *
     * 主题是纯界面概念，但它「选完重启就丢」与设置不落盘是同一件事 ——
     * 用户的感受是「这个软件的设置根本记不住」。
     */
    private fun persistTheme(mode: ThemeMode) {
        runCatching { ApiSettingsStore.setThemeMode(getApplication(), mode.name.lowercase()) }
    }

    /** 持久化**当前选中服务**的密钥（界面只可能改这一个）。 */
    private fun persistWebSearchKey(draft: SettingsDraft) {
        val provider = draft.webSearchProvider
        if (!provider.needsKey) return
        val key = draft.webSearchKeys[provider].orEmpty()
        runCatching { ApiSettingsStore.setWebSearchKey(getApplication(), provider.name.lowercase(), key) }
    }

    /**
     * 分类页里的「入口类」条目：先关掉设置弹窗，再把对应目标打开。
     *
     * 这里只接**真的有实现**的目标。原来还给 `canvas` / `other` 留了分支，
     * 但它们分别指向"未移植的运行时画布"和"自定义头部提示词的第二个入口"，
     * 现在画布入口已撤、提示词并入「上下文与项目」，两条分支一并删掉——
     * 留着只会让后来的人以为这里有功能。
     */
    fun navigateFromSettings(target: String) {
        // rikkahub 式导航：hub 保留在下层，子页盖上来；返回回 hub 时 draft 原样。
        // 「保存」只在 hub 顶栏点（draft 语义不变）。
        when (target) {
            "apiProfiles" -> openApiConfig()
            "mcp" -> openMcpConfig()
            "skills" -> openSkills()
            "searchServices" -> openSearchServices()
            "roleCards" -> openRoleCards()
            "memory" -> openMemory()
            "uiDebug" -> openUiDebug()
            else -> Unit
        }
    }

    // ---------- 对话流交互 ----------

    fun toggleToolExpanded(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { if (it.id == id) it.copy(expanded = !it.expanded) else it },
                    )
                },
            )
        }
    }

    /**
     * 批量设置工具组里各成员的展开状态。
     *
     * [collapseIds] 是**要收起的成员 id 集合**：集合内的成员收起，其余展开。
     * 因此"全部展开"传空集，"全部收起"传全部成员 id。
     */
    fun toggleGroupExpanded(id: String, collapseIds: Set<String>) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id || item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(tools = item.tools.map { it.copy(expanded = it.id !in collapseIds) })
                },
            )
        }
    }

    fun toggleThinking(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { if (it.id == id) it.copy(thinkingExpanded = !it.thinkingExpanded) else it },
            )
        }
    }

    // ---------- 文件 ----------

    fun openDirectory(entry: FileEntry) {
        navigateTo(entry.path)
    }

    /** 刷新文件面板。Agent 改动文件后用它重新列目录。 */
    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadFiles()
            _state.update { it.copy(message = "已刷新文件列表") }
        }
    }

    fun navigateTo(path: String) {
        // 列目录要读盘，放 IO 线程：大目录（例如 node_modules）在主线程列会卡住界面。
        _state.update { it.copy(filePath = path, openFile = null) }
        viewModelScope.launch(Dispatchers.IO) { reloadFiles() }
    }

    fun navigateUp() {
        val current = _state.value.filePath
        val root = rootPath()
        if (current == root) return
        val parent = current.substringBeforeLast('/', "")
        if (parent.isEmpty() || parent.length < root.length) {
            // 已经到根（或再往上会越过根）：回到根，而不是继续往外走。
            navigateTo(root)
            return
        }
        navigateTo(parent)
    }

    fun openFile(entry: FileEntry) {
        if (entry.directory) {
            openDirectory(entry)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileBrowser.read(entry.path)
            _state.update { it.copy(openFile = file, message = "已打开 ${entry.name}（只读）") }
        }
    }

    /** 文件面板的根路径（内置 Termux home 不存在时回退到应用私有目录）。 */
    /**
     * 文件面板当前的根，取决于用户在界面上选的 [FileRoot]。
     *
     * 原先这里恒等于 `projectPath`，于是面板被关在项目里 —— HOME 与共享存储
     * 都走不到（面包屑点不出去，「上一级」也会被弹回来）。
     */
    private fun rootPath(): String = when (_state.value.fileRoot) {
        FileRoot.PROJECT -> _state.value.projectPath
        FileRoot.HOME -> TermuxConstants.TERMUX_HOME_DIR_PATH
        FileRoot.SHARED -> StorageLinks.EXTERNAL_ROOT
    }

    /** 切换文件面板的根。切过去时**回到该根的顶层**，而不是停在别的根里的路径上。 */
    fun switchFileRoot(root: FileRoot) {
        _state.update {
            it.copy(
                fileRoot = root,
                filePath = when (root) {
                    FileRoot.PROJECT -> it.projectPath
                    FileRoot.HOME -> TermuxConstants.TERMUX_HOME_DIR_PATH
                    FileRoot.SHARED -> StorageLinks.EXTERNAL_ROOT
                },
                openFile = null,
                fileDraft = null,
                fileNameForm = null,
                fileDeletePrompt = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { reloadFiles() }
    }

    /** 共享存储没授权时，跳去系统那个「所有文件访问权限」页面。 */
    fun refreshSharedStoragePermission() {
        val granted = runCatching {
            android.os.Environment.isExternalStorageManager()
        }.getOrDefault(false)
        _state.update { it.copy(sharedStorageGranted = granted) }
    }

    // ---------- 文件：编辑与保存 ----------

    /** 进入编辑态。只对**文本**文件有意义（二进制/读失败的预览不该被保存回去）。 */
    fun startEditingFile() {
        val open = _state.value.openFile ?: return
        if (!isEditablePreview(open)) {
            _state.update { it.copy(message = "这个文件不能编辑（二进制或读取失败）") }
            return
        }
        _state.update { it.copy(fileDraft = open.content) }
    }

    fun updateFileDraft(text: String) = _state.update { it.copy(fileDraft = text) }

    /** 放弃改动。没有这一步的话，误点「编辑」就只能靠保存来退出。 */
    fun cancelEditingFile() = _state.update {
        it.copy(fileDraft = null, message = "已放弃改动")
    }

    /**
     * 保存编辑中的内容。
     *
     * 成功后**重新读一遍**这个文件（而不是把草稿写进 openFile）：读回来的是磁盘上
     * 真实的样子。写盘可能被截断、可能有编码问题，用草稿冒充成功等于对用户说谎。
     */
    fun saveFile() {
        val open = _state.value.openFile ?: return
        val draft = _state.value.fileDraft ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val error = FileOps.write(File(open.path), draft)
            if (error != null) {
                _state.update { it.copy(message = "保存失败：$error") }
                return@launch
            }
            val reread = FileBrowser.read(open.path)
            _state.update {
                it.copy(openFile = reread, fileDraft = null, message = "已保存 ${open.name}")
            }
            reloadFiles()
        }
    }

    /**
     * 「可编辑」的判据。
     *
     * [FileBrowser.read] 对二进制返回的是一句给人看的提示（"（二进制文件，N 字节…）"），
     * 对读失败返回"（读取失败…）"。把这两句当正文保存回去会把文件**写坏** ——
     * 所以它们不能进入编辑态。判据是"内容是不是那两句提示"，
     * 而不是"文件大不大"，因为 4MB 的文本是合法的、80 字节的二进制不是。
     */
    private fun isEditablePreview(open: OpenFile): Boolean =
        !open.content.startsWith("（二进制文件，") && !open.content.startsWith("（读取失败")

    // ---------- 文件：新建 / 重命名 / 删除 ----------

    /** 打开「新建文件」表单。[directory] 为真时建目录。 */
    fun newFileForm(directory: Boolean) {
        val path = _state.value.filePath
        val base = if (directory) "新建文件夹" else "新建文件.txt"
        _state.update {
            it.copy(
                fileNameForm = FileNameForm(
                    title = if (directory) "新建文件夹" else "新建文件",
                    draft = FileOps.suggestName(File(path), base),
                ),
            )
        }
    }

    /** 打开「重命名」表单。 */
    fun renameForm(entry: FileEntry) {
        _state.update {
            it.copy(fileNameForm = FileNameForm(title = "重命名", target = entry, draft = entry.name))
        }
    }

    fun updateFileNameDraft(text: String) = _state.update { s ->
        s.copy(fileNameForm = s.fileNameForm?.copy(draft = text))
    }

    fun cancelFileNameForm() = _state.update { it.copy(fileNameForm = null) }

    /**
     * 提交「新建 / 重命名」。
     *
     * 新建之后**立刻打开它**（文件）或**走进去**（目录）—— 「新建了个文件然后还要自己找出来」
     * 是一步没必要的操作。重命名则刷新列表即可（当前内容还开着，路径没变）。
     */
    fun submitFileNameForm() {
        val form = _state.value.fileNameForm ?: return
        val dir = File(_state.value.filePath)
        viewModelScope.launch(Dispatchers.IO) {
            val name = form.draft
            val error = if (form.target != null) {
                FileOps.rename(File(form.target.path), name)
            } else {
                // 用扩展名猜是文件还是目录：表单里带 `.` 的当文件建。
                if (name.contains('.')) FileOps.createFile(dir, name)
                else FileOps.createDirectory(dir, name)
            }
            if (error != null) {
                _state.update { it.copy(message = error) }
                return@launch
            }
            _state.update { it.copy(fileNameForm = null) }
            val created = File(dir, name)
            if (form.target == null && created.isDirectory) {
                navigateTo(created.absolutePath)
            } else {
                reloadFiles()
                if (form.target == null && created.isFile) {
                    val opened = FileBrowser.read(created.absolutePath)
                    _state.update { it.copy(openFile = opened) }
                }
                _state.update { it.copy(message = if (form.target != null) "已重命名为 $name" else "已新建 $name") }
            }
        }
    }

    /** 打开删除确认（带"会一起消失多少条"）。 */
    fun requestDelete(entry: FileEntry) {
        _state.update {
            it.copy(fileDeletePrompt = FileDeletePrompt(entry = entry, count = FileOps.countForDelete(File(entry.path))))
        }
    }

    fun cancelDelete() = _state.update { it.copy(fileDeletePrompt = null) }

    fun confirmDelete() {
        val prompt = _state.value.fileDeletePrompt ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val error = FileOps.delete(File(prompt.entry.path))
            if (error != null) {
                _state.update { it.copy(message = error, fileDeletePrompt = null) }
                return@launch
            }
            _state.update { s ->
                s.copy(
                    fileDeletePrompt = null,
                    // 删掉的正是当前打开的文件时要把它关掉，否则面板会一直显示
                    // 一个已经不存在的文件的正文 —— 再点保存就会把它**建回来**。
                    openFile = if (s.openFile?.path == prompt.entry.path) null else s.openFile,
                    fileDraft = if (s.openFile?.path == prompt.entry.path) null else s.fileDraft,
                    message = "已删除 ${prompt.entry.name}",
                )
            }
            reloadFiles()
        }
    }

    fun closeFile() = _state.update { it.copy(openFile = null, fileDraft = null) }

    // ---------- 操作反馈 ----------
    //
    // 这段注释曾经写着"原先由 Snackbar 消费 message，但浮层会遮挡底部输入器，已移除"，
    // 于是 29 处 `copy(message = …)` 全都在往一个没人渲染的地方写：保存失败时界面
    // **一点反应都没有**。现在由 `ui/MessageBar.kt` 在悬浮输入器**上方**内联显示 ——
    // 既不遮输入器，也不再是死代码。

    /**
     * 手动关掉当前提示（点提示条本身，或自动超时）。
     *
     * <p>要判空再更新，而不是无条件 `copy(message = null)`：否则一次无关的点击会把
     * 刚刚弹出的**新**提示也抹掉（点旧位置、提示刚换成新的那种竞态）。
     */
    fun clearMessage(text: String? = null) {
        _state.update { current ->
            if (text != null && current.message != text) current
            else current.copy(message = null, messageIsError = false)
        }
    }

    companion object {
        fun clockLabel(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        fun formatTokens(value: Int): String = when {
            value >= 1_000_000 -> trimZero(value / 1_000_000f) + "m"
            value >= 1_000 -> trimZero(value / 1_000f) + "k"
            else -> "$value"
        }

        /** 200.0k → 200k，省出顶栏 chip 的横向空间。 */
        private fun trimZero(value: Float): String =
            if (value >= 100f || value % 1f == 0f) String.format(Locale.US, "%.0f", value)
            else String.format(Locale.US, "%.1f", value)

        /** 思考预览保留的尾部字符数（原版 `liveThinking` 上限 1200）。 */
        private const val THINKING_LIMIT = 1200

        /** 单条工具实时输出的上限（原版 `liveOutput` 上限 40000）。 */
        private const val LIVE_OUTPUT_LIMIT = 40_000

        /**
         * 内置环境安装日志保留的行数。
         *
         * `~/.zhicode/runtime.log` 是排障用的滚动日志，不是审计记录 ——
         * 自动重装这条路径是静默的，不写日志就完全无法判断它走到了哪一步。
         */
        private const val RUNTIME_LOG_MAX_LINES = 200

        /** 工具实时输出落盘间隔：每条进程输出都改一次 StateFlow 会把界面拖垮。 */
        private const val PROGRESS_FLUSH_MS = 200L

        /** 助手气泡上保留的过程步骤条数上限（原版 12）。 */
        private const val PROCESS_STEP_LIMIT = 12

        /** 压缩摘要回显上限：摘要是给人看要点的，整段贴进对话会把记录淹掉。 */
        private const val COMPACT_SUMMARY_LIMIT = 600

        /**
         * 引擎"没什么可压缩"时返回的前缀。
         *
         * 这不是我发明的约定：`ZhiCodeEngine.compactContextSemantic` 用这句话作为
         * 未调用模型的信号，引擎自己的自动压缩分支也用 `startsWith` 判断它。
         * 引擎没有给出结构化返回值，所以只能照它的契约来。
         */
        private const val ENGINE_NOTHING_TO_COMPACT = "上下文已经足够精简"

        /** 文本附件（技能）拼进提示词的长度上限，与原版一致。 */
        private const val TEXT_ATTACHMENT_LIMIT = 60_000

        /**
         * `/init` 的指令，逐字取自原版，只改了一处：目标文件名换成 `ZhiCode.md`。
         *
         * 全英文是刻意的（原版如此）：这段是要模型执行的指令，
         * 而它明确要求限制调研范围、禁止起子 Agent，措辞改动会改变实际行为，
         * 所以不翻译、不改写。
         *
         * 目标文件名必须是 `ZhiCode.md`：写别的名字会让界面在另一个文件上找它，
         * `/init` 看起来就成了「什么都不做」。
         */
        private const val INIT_INSTRUCTION =
            "This is the built-in fast /init maintenance command. Do not enter plan mode, create tasks, " +
                "launch subagents, or perform broad repository research. Read only the existing " +
                "ZhiCode.md/CLAUDE.md/README and primary build manifest or script when present, then directly " +
                "create or improve ZhiCode.md with concise build, test, architecture, conventions, and " +
                "repository-specific instructions. Preserve useful existing instructions and run at most " +
                "one small verification command."
    }
}
