package com.zhizhu.zhicode.compose.state

import com.zhizhu.zhicode.compose.model.ExportPayload
import com.zhizhu.zhicode.compose.model.ExportKind
import com.zhizhu.zhicode.compose.model.ExportSheetState
import android.net.Uri
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhizhu.zhicode.compose.data.ApiConfigStore
import com.zhizhu.zhicode.background.KeepAliveService
import com.zhizhu.zhicode.compose.data.AttachmentReader
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.data.DebugApiProfile
import com.zhizhu.zhicode.compose.data.FileBrowser
import com.zhizhu.zhicode.compose.data.McpStore
import com.zhizhu.zhicode.compose.data.MockWorkspaceRepository
import com.zhizhu.zhicode.compose.data.ModelCatalogStore
import com.zhizhu.zhicode.compose.data.OobeStore
import com.zhizhu.zhicode.compose.runtime.OobePermissions
import com.zhizhu.zhicode.compose.runtime.OobeStep
import com.zhizhu.zhicode.compose.data.SessionReader
import com.zhizhu.zhicode.compose.data.ThemeStore
import com.zhizhu.zhicode.compose.data.RoleCardStore
import com.zhizhu.zhicode.compose.data.MemoryStore
import com.zhizhu.zhicode.compose.data.SkillImport
import com.zhizhu.zhicode.compose.data.SkillStore
import com.zhizhu.zhicode.compose.data.UpdateChecker
import com.zhizhu.zhicode.compose.data.UpdateStateStore
import com.zhizhu.zhicode.compose.data.VersionCompare
import com.zhizhu.zhicode.compose.data.WorkspacePaths
import com.zhizhu.zhicode.compose.data.WorkspaceRepository
import com.zhizhu.zhicode.compose.data.WorkspaceStore
import com.zhizhu.zhicode.compose.data.canonicalWorkspacePath
import com.zhizhu.zhicode.compose.engine.EngineEvents
import com.zhizhu.zhicode.compose.engine.EngineOverrides
import com.zhizhu.zhicode.compose.engine.EnginePlanApproval
import com.zhizhu.zhicode.compose.engine.ZhiEngineController
import com.zhizhu.zhicode.compose.engine.ToolText
// Shizuku 状态查询：斜杠命令与设置页都要报「授权到了哪一步」，
// 两处读的必须是同一个来源（Shizuku 服务的 Binder），否则显示会互相矛盾。
import com.zhizhu.zhicode.shizuku.ShizukuBridge
import com.zhizhu.zhicode.compose.engine.applyEngineOverrides
import com.zhizhu.zhicode.compose.engine.engineEffortToUi
import com.zhizhu.zhicode.compose.engine.engineModeToUi
import com.zhizhu.zhicode.compose.engine.readEngineSettings
import com.zhizhu.zhicode.compose.engine.toEngineEffort
import com.zhizhu.zhicode.compose.engine.toEngineMode
import com.zhizhu.zhicode.compose.engine.toUiPlanApproval
import com.zhizhu.zhicode.compose.runtime.EnvDoctor
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.RuntimeInstaller
import com.zhizhu.zhicode.UbuntuMirrorSource
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.model.ActionBusy
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.AttachBrowserState
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.Attachment
import com.zhizhu.zhicode.compose.model.ChatAttachment
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.ChatNavigation
import com.zhizhu.zhicode.compose.model.ChoiceIntent
import com.zhizhu.zhicode.compose.model.ChoiceOption
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.CopySheetState
import com.zhizhu.zhicode.compose.model.ExtensionSummary
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.pi.PiCatalogAndPresets
import com.zhizhu.zhicode.pi.PiConfigSync
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.PiProviderPreset
import com.zhizhu.zhicode.pi.PiThinkingLevel
import com.zhizhu.zhicode.pi.clampThinkingLevel
import com.zhizhu.zhicode.pi.resolveSupportedThinkingLevels
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.FileListOptions
import com.zhizhu.zhicode.compose.model.FileSortKey
import com.zhizhu.zhicode.compose.model.LiveOutput
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.ModelThinkingPickerState
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
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.PromptSlot
import com.zhizhu.zhicode.compose.model.QueuedPrompt
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.RiskLevel
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.SLASH_COMMANDS
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SidebarOrderBy
import com.zhizhu.zhicode.compose.model.WorkspaceEntry
import com.zhizhu.zhicode.compose.model.SettingsCategory
import com.zhizhu.zhicode.compose.model.DEFAULT_MAX_CONCURRENT_SUBAGENTS
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
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.model.FileFormat
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.SlashCommand
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolActions
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateReleaseInfo
import com.zhizhu.zhicode.compose.model.WebSearchProvider
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.sandbox.SandboxDashboardActivity
import com.termux.app.zhicode.core.FileOps
import com.termux.app.zhicode.core.PlanApprovalGate
import com.termux.app.zhicode.core.StorageLinks
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.termux.app.zhicode.storage.SessionStore
import com.termux.app.zhicode.storage.ZhiMemory
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 主界面状态机。 */
class WorkspaceViewModel(
    application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : AndroidViewModel(application), EngineEvents {

    /** 内置 Termux 环境安装器（第一次使用前会解压 32MB 的 bootstrap）。 */
    private val installer = RuntimeInstaller(application)

    private val workspaceStore = WorkspaceStore(application)

    /** 真实 ZhiCode 引擎。懒创建，第一次发消息时才初始化。 */
    private val engine = ZhiEngineController(application, this)

    /** 上次自动维护的墙上时间（毫秒）；只在打开侧栏时检查，避免把维护挂到热路径。 */
    private var lastStorageMaintenanceAt = 0L
    private val storageMaintenanceIntervalMs = 24L * 60L * 60L * 1000L

    /** 停止看门狗；每次按停止重建，收到引擎的回合结束确认时取消。 */
    private var stopWatchdog: Job? = null

    /** 这一轮已经按了几次停止。 */
    private var stopAttempts = 0

    /** 低频维护：打开侧栏时最多每天跑一次，重活始终在 IO 线程。 */
    private fun maybeRunStorageMaintenance() {
        val now = System.currentTimeMillis()
        if (now - lastStorageMaintenanceAt < storageMaintenanceIntervalMs) return
        lastStorageMaintenanceAt = now
        viewModelScope.launch(Dispatchers.IO) {
            SessionReader.maintain()
        }
    }


    /** 当前正在流式输出的助手气泡 id。为 null 表示还没有任何 delta。 */
    private var streamingAssistantId: String? = null

    /** 当前回合的工具分组 id。一个工具批次 = 一张分组卡。 */
    private var currentGroupId: String? = null

    /** 引擎给的工具 id → 它落在哪个分组卡里。用于把结果/进度写回正确的位置。 */
    private data class LiveTool(val groupId: String, val name: String, val command: String)

    private val liveTools = mutableMapOf<String, LiveTool>()

    /** 工具实时输出的合并缓冲。 */
    private val progressBuffers = mutableMapOf<String, LiveOutput.Buffer>()
    private val progressLastFlush = mutableMapOf<String, Long>()

    /** 正在等待用户回执的权限请求 id。 */
    private var pendingPermissionId: String? = null

    /** 当前这一轮属于哪个会话（`activeSessionId` 的快照）。 */
    private var turnSessionId: String? = null
    /** 这一轮的起点（`elapsedRealtime`）。0 = 当前没有进行中的回合。 */
    private var turnStartedAtElapsedMs: Long = 0L

    /** 导出请求序号（见 `ExportPayload.nonce`）。 */
    private var exportNonce: Int = 0
    /** 最近一次打开会话的序号，丢弃过时切换结果。 */
    @Volatile
    private var openSessionSeq: Long = 0L
    @Volatile
    private var restoringSession = false

    /** 正在进行的「提问」流程。 */
    private class PendingQuestions(
        val requestId: String,
        val questions: JSONArray,
        var index: Int = 0,
        val answers: JSONObject = JSONObject(),
    )

    private var pendingQuestions: PendingQuestions? = null




    /** 会话操作的目标。 */
    private var pendingSessionAction: SessionSummary? = null

    /** 待执行（去抖中）的设置落盘。 */
    private var settingsPersistJob: Job? = null
    private var pendingMessageAction: ChatItem? = null


    private var modelCatalogJob: Job? = null

    private var sessionModelSave: Job? = null
    private var fileReloadJob: Job? = null

    /** 会话级角色卡覆盖。 */
    private var roleCardOverride: String? = null


    private val _state = MutableStateFlow(
        WorkspaceUiState(
            // 默认项目路径是 $HOME（Termux 约定），不再是开发本应用时用的那个路径。
            // 它由装环境时的 RuntimeInstaller 一并建好，见 [ensureWorkspace]。
            projectName = WorkspacePaths.nameOf(WorkspacePaths.defaultProject()),
            projectPath = WorkspacePaths.defaultProject(),
            // 会话列表 / 对话历史 / 任务清单都来自存储，这里先给空值
            sessions = emptyList(),
            activeSessionId = "",
            transcript = emptyList(),
            tasks = emptyList(),
            contextTokens = 0,
            contextWindow = 200_000,
            deviceStatus = clockLabel(),
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
            ubuntuMirrorId = installer.savedMirrorOrNull()?.id ?: "",
        )
    )

    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    /** 页面动作的在飞登记表（见 [ActionBusy]）。 */
    private val actions = ActionBusy()

    /** 跑一个**有互斥语义**的页面动作。 */
    private fun runBusy(key: String, block: suspend () -> Unit) {
        if (!actions.acquire(key)) {
            if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG) {
                // 调试构建里留一行：双击被吞掉是**设计**，不是"点了没反应"。
                android.util.Log.d("ZhiCodeBusy", "skip re-entrant action: $key")
            }
            return
        }
        _state.update { it.copy(inFlight = actions.snapshot()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                actions.release(key)
                _state.update { it.copy(inFlight = actions.snapshot()) }
            }
        }
    }

    init {
        val oobeNeeded = OobeStore.isRequired(getApplication()) ||
            !runCatching { installer.isInstalled() }.getOrDefault(false)
        initSessionState()
        if (oobeNeeded) {
            _state.update {
                it.copy(
                    oobeOpen = true,
                    oobeStep = OobeStep.PERMISSIONS,
                    oobePermissions = OobePermissions.inspect(getApplication()),
                )
            }
        } else {
            autoInstallRuntimeIfStale()
        }
    }

    // ------------------------------------------------------------ 终端保活

    private var terminalPane: TermuxTerminalPane? = null

    /** 取（或首次创建）终端面板实例。同一进程内只会有一个。 */
    fun terminalPane(context: Context): TermuxTerminalPane =
        terminalPane ?: TermuxTerminalPane(context, installer).also { terminalPane = it }

    /** 关掉终端里的所有会话并丢弃实例。 */
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

    private fun autoInstallRuntimeIfStale() {
        // IO 线程：读 marker、比对 sha256、写日志都是磁盘操作
        viewModelScope.launch(Dispatchers.IO) {
            if (!installer.isInstalled()) return@launch
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

            val reason = "正在离线更新内置 Node.js 与 Pi…"
            var claimed = false
            _state.update { s ->
                if (s.runtimeInstalling) {
                    s
                } else {
                    claimed = true
                    s.copy(
                        runtimeInstalling = true,
                        runtimeProgress = 0,
                        runtimeMessage = reason,
                    )
                }
            }
            if (!claimed) return@launch
            appendRuntimeLog("开始离线更新 Node.js/Pi")
            withContext(Dispatchers.Main) { releaseTerminalPane() }
            val err = runCatching {
                installer.upgradeNodePiIfInstalled { msg, pct ->
                    _state.update { it.copy(runtimeMessage = msg, runtimeProgress = pct) }
                }
            }.exceptionOrNull()
            val reportText = buildEnvironmentReport()
            _state.update {
                it.copy(
                    runtimeInstalling = false,
                    runtimeProgress = if (err == null) 100 else 0,
                    runtimeMessage = if (err == null) "Ubuntu 24.04 + Node.js + Pi 已就绪" else "更新失败：${err.message ?: err.javaClass.simpleName}",
                    runtimeReady = installer.isInstalled(),
                    environmentReport = reportText,
                )
            }
        }
    }

    /** 把一行诊断写进 `$HOME/.zhicode/runtime.log`。 */
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

    /** 首次载入：列出磁盘上的真实会话，有历史就把最近一条打开。 */
    private fun initSessionState() {
        viewModelScope.launch(Dispatchers.IO) {
            restoreUiSettings()
            restoreRuntimeChoices()
            // 顶栏的模型名/密钥状态要反映**真实生效**的配置，
            // 否则用户会看到一个跟实际请求无关的模型名。
            syncActiveProfile()
            syncRoleCardFromStore()
            // 共享存储的授权状态要**实测**：文件面板切到「共享存储」时，
            // 没授权只会看到"0 项"，而原因（没给「所有文件访问权限」）必须说出来。
            refreshSharedStoragePermission()
            /* `~/storage` 的六个链接**在每次启动的路径上**补一次。 */
            if (runCatching { installer.isInstalled() }.getOrDefault(false)) {
                installer.setupStorageLinks()
            }
            val workspaces = workspaceStore.loadWorkspaces(WorkspacePaths.defaultProject())
            val orderBy = workspaceStore.loadOrderBy()
            val sessions = SessionReader.listAll()
            val latest = sessions.firstOrNull()
            val targetProject = latest?.projectPath?.takeIf { it.isNotBlank() && File(it).isDirectory }
                ?: workspaceStore.loadLastActivePath(WorkspacePaths.defaultProject())
            _state.update {
                it.copy(
                    workspaces = workspaces,
                    sidebarOrderBy = orderBy,
                    sessions = sessions,
                    projectPath = targetProject,
                    projectName = WorkspacePaths.nameOf(targetProject),
                    filePath = targetProject,
                )
            }
            ensureWorkspace()
            if (latest != null) {
                openSession(latest)
            }
        }
    }

    private suspend fun restoreUiSettings() = withContext(Dispatchers.IO) {
        val stored = runCatching {
            ApiSettingsStore.getThemeMode(getApplication(), ThemeMode.SYSTEM.name.lowercase())
        }.getOrDefault(ThemeMode.SYSTEM.name.lowercase())
        val theme = ThemeMode.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            ?: ThemeMode.SYSTEM
        // 上一次「检查更新」的结论。
        val restoredUpdate = UpdateStateStore.read(getApplication())
        val keys = WebSearchProvider.entries
            .filter { it.needsKey }
            .associateWith { provider ->
                runCatching {
                    ApiSettingsStore.getWebSearchKey(getApplication(), provider.name.lowercase())
                }.getOrDefault("")
            }
            .filterValues { it.isNotBlank() }
        val maxConcurrentSubagents = runCatching {
            ApiSettingsStore(getApplication()).load().maxConcurrentSubagents
        }.getOrDefault(DEFAULT_MAX_CONCURRENT_SUBAGENTS)
        _state.update {
            it.copy(
                themeMode = theme,
                // 整份主题设置也在这里读回来：预设 / Monet / 显示开关与深浅住在同一个存储里，
                // 分两次读会出现「深浅换了、预设没换」的中间态。
                themeSettings = runCatching { ThemeStore.read(getApplication()) }
                    .getOrDefault(it.themeSettings)
                    .let { stored -> stored.copy(mode = theme) },
                // 只在真的读到过结论时才覆盖默认的 `Idle`。读不到就保持
                // 「还没查过」，让 `openAbout` / `openUpdate` 去查一次。
                update = restoredUpdate ?: it.update,
                settings = it.settings.copy(
                    webSearchKeys = keys,
                    terminalCharMode = runCatching {
                        ApiSettingsStore.getTerminalCharMode(getApplication())
                    }.getOrDefault(false),
                    maxConcurrentSubagents = maxConcurrentSubagents,
                ),
                settingsDraft = it.settingsDraft?.copy(
                    themeMode = theme,
                    maxConcurrentSubagents = maxConcurrentSubagents,
                    webSearchKeys = keys,
                    terminalCharMode = runCatching {
                        ApiSettingsStore.getTerminalCharMode(getApplication())
                    }.getOrDefault(false),
                ),
            )
        }
    }

    /** 确保当前项目目录存在。 */
    private fun ensureWorkspace() {
        val path = _state.value.projectPath
        // 目录已存在：只需按真实文件系统算出列表与说明。
        if (WorkspacePaths.exists(path)) {
            reloadFiles()
            return
        }
        // 目录不存在，且运行环境还没装（home 目录都还没出现）
        if (!installer.isInstalled()) {
            _state.update {
                it.copy(
                    fileEntries = emptyList(),
                    fileNote = "工作区还不存在：$path\n初始化内置 Debian 环境后会自动建立。",
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
        val options = _state.value.fileListOptions
        val loaded = FileBrowser.children(path)
        val entries = loaded
            .asSequence()
            .filter { options.query.isBlank() || it.name.contains(options.query.trim(), ignoreCase = true) }
            .sortedWith(fileComparator(options))
            .toList()
        val dir = java.io.File(path)
        val note = when {
            entries.isNotEmpty() -> ""
            !dir.isDirectory -> if (dir.exists()) "不是目录：$path" else "目录不存在：$path"
            // 目录存在但一个子项都没列出来 —— 区分「真的是空目录」和「没权限列」。
            dir.list() == null -> "无法读取（权限不足）：$path"
            else -> ""
        }
        _state.update { state ->
            // 目录读取本身可能无法被底层 API 中断；旧 Job 即使被取消，
            // 也不能在新路径或新搜索词之后把旧结果写回来。
            if (state.filePath != path || state.fileListOptions != options) state
            else state.copy(fileEntries = entries, fileNote = note)
        }
    }

    private var idCounter = 0L

    private fun nextId(prefix: String): String = "$prefix-${++idCounter}"

    // ---------- 输入器 ----------

    // ---------- 任务清单 ----------

    /** 点悬浮任务卡：打开任务详情窗口，查看**全部**任务（卡片只显示当前窗口）。 */
    fun openTaskList() = _state.update { it.copy(taskListOpen = true) }

    fun closeTaskList() = _state.update { it.copy(taskListOpen = false) }

    /** 输入框里出现/消失 `@` 文件引用查询时由界面调用。 */
    fun onFileQueryChanged(query: String?) {
        if (query == null) {
            _state.update {
                if (it.fileQuery == null) it
                else it.copy(fileQuery = null, fileQueryMatches = emptyList())
            }
            return
        }
        val needle = query.lowercase(Locale.US)
        val matches = _state.value.fileEntries
            .asSequence()
            .filter { it.name.isNotBlank() }
            .filter { needle.isEmpty() || it.name.lowercase(Locale.US).contains(needle) }
            // 目录排在前面：引用一个目录比引用某个具体文件更常见（"看看这个目录"）。
            .sortedWith(compareByDescending<com.zhizhu.zhicode.compose.model.FileEntry> { it.directory })
            .map { it.name + if (it.directory) "/" else "" }
            .take(8)
            .toList()
        _state.update {
            if (it.fileQuery == query && it.fileQueryMatches == matches) it
            else it.copy(fileQuery = query, fileQueryMatches = matches)
        }
    }

    fun onSlashQueryChanged(query: String?) {
        _state.update {
            if (it.slashQuery == query) it
            else it.copy(
                slashQuery = query,
                slashMatches = if (query == null) emptyList() else slashMatchesFor(query),
            )
        }
    }

    /** 外部动作往输入框里写字（编辑后发送、恢复草稿、引用、命令补全…）。 */
    fun requestComposerText(text: String) {
        _state.update {
            it.copy(composerText = text, composerTextEpoch = it.composerTextEpoch + 1)
        }
    }

    private fun slashMatchesFor(query: String): List<SlashCommand> {
        val normalized = query.lowercase(Locale.US)
        return SLASH_COMMANDS.filter { it.name.startsWith(normalized) }
    }

    fun pickSlashCommand(command: SlashCommand) {
        _state.update {
            it.copy(composerText = command.name + " ", composerTextEpoch = it.composerTextEpoch + 1, slashQuery = null, slashMatches = emptyList())
        }
    }

    fun dismissSlashPalette() {
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
    }

    /** 附件载荷。 */
    private class AttachmentPayload(val bytes: ByteArray?, val mimeType: String, val image: ChatImage?)

    private val attachmentPayloads = mutableMapOf<String, AttachmentPayload>()

    /** 取某个待发附件的图片（给输入器的缩略图用）。 */
    fun currentAttachmentImage(id: String): ChatImage? = attachmentPayloads[id]?.image

    /** 附加一张图片（来自系统选择器）。 */
    fun attachImage(uri: android.net.Uri) = attachImages(listOf(uri))

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

    /** 把待发图片编成引擎要的内容块。 */
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

    /** 把待发图片编成**界面模型**（挂到用户气泡上）。 */
    private fun currentImages(): List<ChatImage> = _state.value.attachments
        .filter { it.isImage }
        .mapNotNull { currentAttachmentImage(it.id) }

    private fun currentFiles(): List<ChatAttachment> = _state.value.attachments
        .filter { !it.isImage }
        .map { ChatAttachment(id = it.id, label = it.label, detail = it.sizeLabel) }

    /** 清空附件：界面条目与载荷必须一起走，否则载荷留在 map 里没人回收。 */
    private fun clearAttachments() {
        attachmentPayloads.clear()
        _state.update { it.copy(attachments = emptyList()) }
    }

    /** 把待发的**文本**附件拼进提示词。 */
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

    /** 导入 SKILL.md 的体积上限。 */
    private val SKILL_IMPORT_LIMIT_BYTES = 256 * 1024

    fun openSkills() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:skills")
        _state.update {
            it.copy(
                skills = SkillsState(skills = emptyList(), loading = true),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = _state.value.projectPath
            val loaded = runCatching { SkillStore.list(path) }.getOrDefault(emptyList())
            _state.update { s ->
                // 读盘期间用户可能已经返回：那时载荷已置空，结果直接丢弃。
                val current = s.skills ?: return@update s
                s.copy(skills = current.copy(skills = loaded, loading = false))
            }
        }
    }

    fun closeSkills() {
        _state.update { it.copy(skills = null) }
        // 回到设置主页时刷新「扩展」组的数量：刚从技能页增删过，不刷会显示旧数字。
        refreshExtensions()
    }

    /** 列表页的筛选词。 */
    fun setSkillQuery(query: String) = _state.update {
        it.copy(skills = it.skills?.copy(query = query))
    }

    /** 打开技能详情。 */
    fun openSkillDetail(entry: SkillEntry) {
        val s = _state.value
        // 先推页、后读盘：`listTree` 要 walk 整个技能目录并逐文件取大小
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
        // 详情页的只读预览：`SKILL.md` 的正文。
        // 之前只有文件树，想知道这个技能教 Agent 做什么得先点进编辑器。
        viewModelScope.launch(Dispatchers.IO) {
            val body = SkillStore.read(s.projectPath, entry.scope, entry.name).getOrDefault("")
            _state.update { state ->
                val current = state.skills?.detail ?: return@update state
                // 期间用户可能已经关页或换了另一条技能：只更新同一条。
                if (current.entry.name != entry.name || current.entry.scope != entry.scope) return@update state
                state.copy(
                    skills = state.skills.copy(
                        detail = current.copy(
                            body = body,
                            tree = SkillStore.listTree(state.projectPath, entry.scope, entry.name),
                        ),
                    ),
                )
            }
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

    /** 在技能目录里新建一个附加文件。 */
    fun saveSkillFile() {
        val s = _state.value
        val skills = s.skills ?: return
        val draft = skills.fileDraft ?: return
        val detail = skills.detail ?: return
        val name = draft.fileName.trim()
        if (!draft.saveable) return
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

    /** 删除技能目录里的一个附加文件。 */
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

    fun newSkill() {
        _state.update {
            it.copy(
                skills = it.skills?.copy(
                    createForm = SkillCreateDraft(content = "", name = ""),
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

    fun createSkill() = runBusy("skills.create") {
        val s = _state.value
        val form = s.skills?.createForm ?: return@runBusy
        if (!form.saveable) return@runBusy
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

    /** 从本机文件导入一份 SKILL.md。 */
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

    /** 打开「从 URL 导入」对话框。 */
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

    /** 从 URL 导入。 */
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

    /** 把 zip 里解出来的技能逐个落盘。 */
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

    /** 打开某个技能文件的编辑器（对话框）。 */
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

    fun saveSkill() = runBusy("skills.save") {
        val s = _state.value
        val editing = s.skills?.editing ?: return@runBusy
        if (!editing.saveable) return@runBusy
        // 与 saveSkillFile 同理：写的是一整份正文 + 写完重列文件树。
        run {
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
                // 重新构造整个 SkillsState：正在浏览/编辑的若就是被删的那个
                skills = SkillsState(
                    skills = SkillStore.list(it.projectPath),
                    query = it.skills?.query.orEmpty(),
                ),
                message = if (result.isSuccess) "已删除技能 ${entry.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /** 把技能附加到下一步任务。 */
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
                    // 技能也是"文件"，气泡里同样给一行体积。
                    sizeLabel = entry.sizeLabel,
                ),
                message = "已附加技能：${entry.name}",
            )
        }
    }

    // ---------- 角色卡 ----------

    /** 打开角色卡管理器。 */
    fun openRoleCards() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:roleCards")
        // 同 openSkills：侧栏入口也要把设置主页垫在底下，保证返回层级完整。
        // 先同步推页、数据后读，理由见 openApiConfig。
        _state.update {
            it.copy(
                roleCards = RoleCardsState(cards = emptyList(), activeId = "", loading = true),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val cards = runCatching { RoleCardStore.list(context) }.getOrDefault(emptyList())
            val activeId = runCatching { RoleCardStore.activeId(context) }.getOrDefault("")
            _state.update { s ->
                val current = s.roleCards ?: return@update s
                s.copy(
                    roleCards = current.copy(cards = cards, activeId = activeId, loading = false),
                )
            }
        }
    }

    fun closeRoleCards() {
        _state.update { it.copy(roleCards = null) }
        refreshExtensions()
    }

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

    /** 保存角色卡并**立即启用**。 */
    fun saveRoleCard() = runBusy("roleCards.save") {
        val editor = _state.value.roleCards?.editor ?: return@runBusy
        if (!editor.saveable) return@runBusy
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
    fun selectRoleCard(card: RoleCard) = runBusy("roleCards.select") {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), card.id)
        if (result.isFailure) {
            _state.update { it.copy(message = "启用失败：${result.exceptionOrNull()?.message ?: "未知原因"}", messageIsError = true) }
            return@runBusy
        }
        roleCardOverride = card.content
        refreshRoleCards(message = "已启用角色卡：${card.name}")
        runCatching { engine.configure(engineOverrides()) }
    }

    fun disableRoleCard() = runBusy("roleCards.disable") {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), "")
        if (result.isFailure) {
            _state.update { it.copy(message = "停用失败：${result.exceptionOrNull()?.message ?: "未知原因"}", messageIsError = true) }
            return@runBusy
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

    /** 把磁盘上启用中的角色卡内容读进会话覆盖值。 */
    private fun syncRoleCardFromStore() {
        val content = runCatching { RoleCardStore.activeContent(getApplication()) }.getOrDefault("")
        // 空串表示"确实没有启用的卡"，也要覆盖掉配置里的旧值，所以这里不能判空就跳过。
        roleCardOverride = content
    }

    // ---------- 记忆文件（ZhiCode.md） ----------

    fun openMemory() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:memory")
        // 同 openSkills：侧栏入口也把设置主页垫在底下。
        _state.update {
            it.copy(
                memory = MemoryState(files = emptyList(), loading = true),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = _state.value.projectPath
            val files = runCatching { MemoryStore.list(path) }.getOrDefault(emptyList())
            _state.update { s ->
                val current = s.memory ?: return@update s
                s.copy(memory = current.copy(files = files, loading = false))
            }
        }
    }

    fun closeMemory() = _state.update { it.copy(memory = null) }

    fun openSandbox() {
        hideSidebarForNavigation()
        val context: android.content.Context = getApplication()
        runCatching {
            context.startActivity(
                SandboxDashboardActivity.intent(context)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            _state.update {
                it.copy(
                    message = "打不开沙箱：${error.message ?: error.javaClass.simpleName}",
                    messageIsError = true,
                )
            }
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

    fun saveMemory() = runBusy("memory.save") {
        val state = _state.value
        val editing = state.memory?.editing ?: return@runBusy
        val result = MemoryStore.save(state.projectPath, editing.scope, editing.body)
        _state.update {
            it.copy(
                memory = MemoryState(files = MemoryStore.list(it.projectPath)),
                message = if (result.isSuccess) "${editing.scope.label} 已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /** `/init`：让 Agent 直接创建或完善项目 ZhiCode.md。 */
    /** 界面入口：记忆面板里的「让 ZhiCode 完善」按钮（`ui/dialogs/MemoryOverlay.kt`）。 */
    fun runInitFromUi() = runInitPrompt()

    private fun runInitPrompt() {
        if (engine.isBusy()) {
            appendInfo("初始化项目说明", "ZhiCode 正在执行任务，请先等待或停止当前任务。")
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
        startTurn(PromptSlot.INIT.resolve(_state.value.settings.initPrompt))
    }

    /** 写入一个提示词槽位（空串 = 恢复默认）。 */
    fun setPromptSlot(slot: PromptSlot, value: String) {
        val draft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        val updated = when (slot) {
            PromptSlot.INIT -> draft.copy(initPrompt = value)
            PromptSlot.COMPACT_SYSTEM -> draft.copy(summaryPrompt = value)
            PromptSlot.COMPACT_HEAD -> draft.copy(compactPromptHead = value)
        }
        applySettingsDraft(updated)
    }

    /** 编辑页里「恢复默认」：把该槽位清成空串。 */
    fun resetPromptSlot(slot: PromptSlot) = setPromptSlot(slot, "")

    /** 打开「提示词」整页。 */
    fun openPrompts() {
        _state.update { it.copy(settingsOpen = true, sidebarOpen = false, promptsOpen = true) }
    }

    fun closePrompts() {
        _state.update { it.copy(promptsOpen = false) }
    }

    /** 打开主题设置整页（设置 hub 里那一行现在只做入口）。 */
    fun openThemePage() {
        _state.update { it.copy(settingsOpen = true, sidebarOpen = false, themePageOpen = true) }
    }

    fun closeThemePage() {
        // 关页时兑现主题设置的落盘：滑杆拖动会连续改状态但不写盘
        // （见 updateThemeSettings 的说明），这里补那一次。
        persistThemeSettings()
        _state.update { it.copy(themePageOpen = false) }
    }

    /** 打开「聊天显示」页（自定义字体 + 正文字号）。同样走设置栈，返回回到设置 hub。 */
    fun openChatDisplay() {
        _state.update { it.copy(settingsOpen = true, sidebarOpen = false, chatDisplayOpen = true) }
    }

    fun closeChatDisplay() {
        // 与主题页同理：字号滑杆连续改状态，关页时才落盘。
        persistThemeSettings()
        _state.update { it.copy(chatDisplayOpen = false) }
    }

    /** 打开「关于」。 */
    fun openAbout() {
        _state.update { it.copy(aboutOpen = true, sidebarOpen = false) }
        // 关于页要决定「有新版本 x.y.z」那一行显不显示，而这只有在真的查过之后才知道。
        checkForUpdatesIfStale()
    }
    fun closeAbout() {
        // 从关于页返回时把许可页、隐私页、更新页也收掉：它们是一条链
        // （关于 → 许可 / 隐私 / 更新），只关上面那层的话，返回一次会看到下层还在。
        _state.update {
            it.copy(
                aboutOpen = false,
                licensesOpen = false,
                privacyOpen = false,
                updatePageOpen = false,
            )
        }
    }

    // ---------- 检查更新 ----------

    /** 打开「检查更新」页。 */
    fun openUpdate() {
        _state.update { it.copy(updatePageOpen = true) }
        // 进页面时自动查一次（若还没查过）。这样"打开就该看到状态"，
        // 而不是让用户先点一下「检查更新」才有东西看。
        checkForUpdatesIfStale()
    }

    fun closeUpdate() {
        _state.update { it.copy(updatePageOpen = false) }
    }

    fun checkForUpdates(force: Boolean = true) {
        runBusy(ACTION_CHECK_UPDATE) {
            val current = _state.value.update
            if (!force && current.hasChecked) return@runBusy
            if (current.phase == UpdatePhase.Checking) return@runBusy

            _state.update {
                it.copy(update = it.update.copy(phase = UpdatePhase.Checking, error = null))
            }
            checkUpdateCancellation = UpdateChecker.Cancellation { false }
            val outcome = runCatching {
                UpdateChecker.fetch(checkUpdateCancellation ?: UpdateChecker.Cancellation { false })
            }
            checkUpdateCancellation = null

            _state.update { s ->
                val root = outcome.getOrNull()
                when {
                    outcome.isFailure -> s.copy(
                        update = s.update.copy(
                            phase = UpdatePhase.Failed,
                            error = outcome.exceptionOrNull()?.message ?: "检查更新失败",
                            hasChecked = true,
                        ),
                    )
                    // 仓库还没有发布过 Release。**不是错误**，也不能说成"已是最新"：
                    // 那样上游发布之后这句话仍然成立，用户会以为检查坏了。
                    root.isNullOrEmpty() -> s.copy(
                        update = s.update.copy(
                            phase = UpdatePhase.NoReleases,
                            release = null,
                            error = null,
                            hasChecked = true,
                        ),
                    )
                    else -> {
                        val newest = pickNewest(root)
                        val info = newest?.let(::toReleaseInfo)
                        // tag 可能是 `v0.3.0`，`VersionCompare` 自己会去掉 `v` 前缀。
                        val hasNewer = newest != null && VersionCompare.isNewer(
                            newest.tag,
                            com.zhizhu.zhicode.compose.BuildConfig.VERSION_NAME,
                        )
                        s.copy(
                            update = s.update.copy(
                                phase = if (hasNewer) UpdatePhase.Available else UpdatePhase.UpToDate,
                                release = info.takeIf { hasNewer },
                                error = null,
                                hasChecked = true,
                                downloadPercent = 0,
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                            ),
                        )
                    }
                }
            }
            // 结论落盘。
            UpdateStateStore.write(getApplication(), _state.value.update)
        }
    }

    /** [openAbout] / [openUpdate] 的自动检查：只在还没查过时发生。 */
    private fun checkForUpdatesIfStale() {
        if (_state.value.update.hasChecked) return
        checkForUpdates(force = false)
    }

    private fun pickNewest(releases: List<UpdateChecker.Release>): UpdateChecker.Release? =
        releases.filter { !it.prerelease }.maxWithOrNull { a, b ->
            if (VersionCompare.isNewer(a.tag, b.tag)) 1 else -1
        } ?: releases.firstOrNull()

    private fun toReleaseInfo(release: UpdateChecker.Release) = UpdateReleaseInfo(
        tag = release.tag,
        name = release.name,
        publishedAt = release.publishedAt,
        body = release.body,
        prerelease = release.prerelease,
        pageUrl = release.pageUrl,
        apkUrl = release.apkUrl,
        apkName = release.apkName,
        apkSize = release.apkSize,
    )

    /** 下载安装包。 */
    fun downloadUpdate() {
        runBusy(ACTION_DOWNLOAD_UPDATE) {
            val release = _state.value.update.release ?: return@runBusy
            val url = release.apkUrl ?: return@runBusy
            val target = updateApkFile(release)

            // 同一个 tag、体积又对得上，就不重复下 —— 用户点了取消再点下载、
            // 或者退出重进，都不该把同一份几百 MB 的文件重下一遍。
            if (target.isFile && release.apkSize > 0 && target.length() == release.apkSize) {
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            phase = UpdatePhase.Available,
                            downloadPercent = 100,
                            downloadedBytes = target.length(),
                            totalBytes = target.length(),
                            error = null,
                        ),
                    )
                }
                return@runBusy
            }

            _state.update {
                it.copy(
                    update = it.update.copy(
                        phase = UpdatePhase.Downloading,
                        downloadPercent = 0,
                        downloadedBytes = 0L,
                        totalBytes = release.apkSize,
                        error = null,
                    ),
                )
            }

            downloadUpdateCancellation = UpdateChecker.Cancellation { downloadCancelled }
            val outcome = runCatching {
                UpdateChecker.downloadApk(
                    url = url,
                    target = target,
                    onProgress = { done, total ->
                        _state.update { s ->
                            val percent = if (total > 0) ((done * 100) / total).toInt() else 0
                            s.copy(
                                update = s.update.copy(
                                    downloadedBytes = done,
                                    totalBytes = total,
                                    downloadPercent = percent.coerceIn(0, 100),
                                ),
                            )
                        }
                    },
                    cancellation = downloadUpdateCancellation
                        ?: UpdateChecker.Cancellation { false },
                )
            }
            downloadUpdateCancellation = null
            downloadCancelled = false

            _state.update { s ->
                if (outcome.isSuccess) {
                    s.copy(
                        update = s.update.copy(
                            phase = UpdatePhase.Available,
                            downloadPercent = 100,
                            downloadedBytes = target.length(),
                            totalBytes = target.length(),
                            error = null,
                        ),
                        // 不再自己弹安装器：交给页面在用户按下「安装」时调
                        // `installDownloadedUpdate()`。下载完就跳系统安装器太突然。
                        message = "安装包已下载",
                    )
                } else {
                    s.copy(
                        update = s.update.copy(
                            phase = UpdatePhase.Available,
                            error = outcome.exceptionOrNull()?.message ?: "下载失败",
                        ),
                    )
                }
            }
        }
    }

    /** 取消正在进行的下载（或检查）。 */
    fun cancelUpdateDownload() {
        downloadCancelled = true
        checkUpdateCancellation = null
        downloadUpdateCancellation = null
    }

    /** 把已下载的安装包交给系统安装器。 */
    fun installDownloadedUpdate() {
        val release = _state.value.update.release ?: return
        val apk = updateApkFile(release)
        if (!apk.isFile) {
            _state.update { it.copy(message = "安装包不存在，请重新下载", messageIsError = true) }
            return
        }
        val result = runCatching {
            com.termux.app.zhicode.tools.AndroidIntentBridge(getApplication())
                .installApkFile(apk)
        }
        val failure = result.exceptionOrNull()?.message
        val toolError = result.getOrNull()?.takeIf { it.isError }?.content
        val error = failure ?: toolError
        if (error != null) {
            _state.update { it.copy(message = "无法启动安装：$error", messageIsError = true) }
        }
    }

    /** 安装包在缓存里的落点。 */
    private fun updateApkFile(release: UpdateReleaseInfo): File {
        val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        val dir = File(getApplication<android.app.Application>().cacheDir, "update")
        val name = release.apkName
            ?.substringAfterLast('/')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "zhicode-$safeTag.apk"
        return File(dir, "${safeTag}-$name")
    }

    /** 检查更新的在飞登记键。与下载分开：两者可以同时登记，互不吞掉。 */
    private val ACTION_CHECK_UPDATE = "update.check"
    private val ACTION_DOWNLOAD_UPDATE = "update.download"

    /** 非空表示有一次检查请求在飞（取消时置空，但仍要保留一个可查询的取消源）。 */
    @Volatile
    private var checkUpdateCancellation: UpdateChecker.Cancellation? = null

    /** 非空表示有一次下载在飞。 */
    @Volatile
    private var downloadUpdateCancellation: UpdateChecker.Cancellation? = null

    /** 用户是否按下了「取消下载」。重置时机见 [downloadUpdate] 的收尾。 */
    @Volatile
    private var downloadCancelled = false

    fun openLicenses() {
        _state.update { it.copy(licensesOpen = true) }
    }

    fun closeLicenses() {
        _state.update { it.copy(licensesOpen = false) }
    }

    fun openPrivacy() {
        _state.update { it.copy(privacyOpen = true) }
    }

    fun closePrivacy() {
        _state.update { it.copy(privacyOpen = false) }
    }

    fun removeAttachment(id: String) {
        attachmentPayloads.remove(id)
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    // ---------- 发送 / 停止 ----------

    /** 从输入框的**当前文字**发送。 */
    fun sendFromComposerText(textState: com.zhizhu.zhicode.compose.ui.composer.ComposerTextState) {
        if (restoringSession) return
        val text = textState.text()
        if (text.isBlank()) return
        requestComposerText(text)
        // 界面侧可以先清空：`send()` 读的是状态层刚写进去的值。
        // 清空同时让斜杠面板收起（下一行）。
        textState.clear()
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
        send()
    }

    /** 从输入框发送，并**等待回复**（普通点按发送键）。 */
    fun removeQueuedPrompt(chatItemId: String) {
        _state.update { s ->
            val remaining = s.pendingInputs.filterNot { it.chatItemId == chatItemId }
            s.copy(
                pendingInputs = remaining,
                // 队列空了就把工作状态清掉，否则会一直挂着「已预输入…」。
                workingStatus = if (remaining.isEmpty()) null else s.workingStatus,
                transcript = if (remaining.size == s.pendingInputs.size) {
                    s.transcript
                } else {
                    s.transcript.filterNot { it.id == chatItemId }
                },
                transcriptRevision = if (remaining.size == s.pendingInputs.size) {
                    s.transcriptRevision
                } else {
                    s.transcriptRevision + 1
                },
                message = "已取消这条排队消息",
            )
        }
    }

    /** 把队列里的一条取回输入框（编辑后重新发送）。 */
    fun editQueuedPrompt(chatItemId: String) {
        val queued = _state.value.pendingInputs.firstOrNull { it.chatItemId == chatItemId } ?: return
        removeQueuedPrompt(chatItemId)
        _state.update {
            it.copy(
                composerText = queued.text,
                composerTextEpoch = it.composerTextEpoch + 1,
                message = "已取回输入框，可修改后重新发送",
            )
        }
    }

    fun send() = sendInternal(waitForReply = true)

    fun sendWithoutReply() = sendInternal(waitForReply = false)

    /** 长按发送键的入口：从界面持有的文字发送，**不**触发模型。 */
    fun sendWithoutReplyFromComposerText(
        textState: com.zhizhu.zhicode.compose.ui.composer.ComposerTextState,
    ) {
        if (restoringSession) return
        val text = textState.text()
        if (text.isBlank()) return
        requestComposerText(text)
        textState.clear()
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
        sendWithoutReply()
    }

    private fun sendInternal(waitForReply: Boolean) {
        if (restoringSession) return
        val text = _state.value.composerText.trim()
        if (text.isEmpty()) return
        if (text.startsWith("/")) {
            _state.update { it.copy(composerText = "", composerTextEpoch = it.composerTextEpoch + 1, slashQuery = null, slashMatches = emptyList()) }
            runSlashCommand(text)
            return
        }
        /* 「只发不回复」在引擎忙的时候**没有意义**：那时本来就不发 startTurn */
        if (!waitForReply && !engine.isBusy()) {
            val userItem = ChatItem(
                id = nextId("u"),
                kind = ChatKind.USER,
                title = "你",
                body = text,
                // 与下面两条分支同理：附件必须在 clearAttachments() 之前取。
                images = currentImages(),
                attachments = currentFiles(),
            )
            clearAttachments()
            _state.update {
                it.copy(
                    composerText = "",
                    composerTextEpoch = it.composerTextEpoch + 1,
                    slashQuery = null,
                    slashMatches = emptyList(),
                    transcript = it.transcript + userItem,
                    message = "已发送（不等待回复）",
                    scrollToBottomToken = it.scrollToBottomToken + 1,
                )
            }
            return
        }
        if (engine.isBusy()) {
            val userItem = ChatItem(
                id = nextId("u"),
                kind = ChatKind.USER,
                title = "你",
                body = text,
                images = currentImages(),
                attachments = currentFiles(),
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
                    composerTextEpoch = s.composerTextEpoch + 1,
                    slashQuery = null,
                    slashMatches = emptyList(),
                    transcript = s.transcript + userItem,
                    pendingInputs = queue,
                    workingStatus = queuedStatus(queue.size),
                    message = queuedNote,
                    // 发出去就必须看得见：用户可能正在翻历史，而此刻的意图很明确。
                    scrollToBottomToken = s.scrollToBottomToken + 1,
                )
            }
            // 真正的排队交给引擎：它会在「当前工具执行完 / 当前模型回复结束」这个
            val steered = engine.steer(promptWithTextAttachments, userItem.id, imageBlocks)
            if (!steered) {
                // 极端竞态：这里判定为忙，但真正入队时回合已经结束。
                // 不静默丢弃 —— 把文字放回输入框，用户能直接重发。
                _state.update { s ->
                    s.copy(
                        pendingInputs = s.pendingInputs.filterNot { q -> q.chatItemId == userItem.id },
                        composerText = text,
                        composerTextEpoch = s.composerTextEpoch + 1,
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
            attachments = currentFiles(),
        )
        // 同上：先编内容块再清附件。
        val imageBlocks = buildImageBlocks()
        clearAttachments()
        _state.update {
            it.copy(
                composerText = "",
                composerTextEpoch = it.composerTextEpoch + 1,
                slashQuery = null,
                slashMatches = emptyList(),
                transcript = it.transcript + userItem,
                contextTokens = it.contextTokens + 1_100,
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
                // 发送即恢复吸底：用户可能正在翻历史，气泡不能落在屏幕外。
                scrollToBottomToken = it.scrollToBottomToken + 1,
            )
        }
        startTurn(buildPromptWithTextAttachments(text), imageBlocks)
    }

    /** 把一条提示词交给真实引擎。 */
    private fun startTurn(prompt: String, extraContent: JSONArray? = null) {
        // 记下这一轮属于哪个会话。放在这里而不是各调用点：`startTurn` 是**唯一漏斗**
        // （普通消息与 /init 都走它），只需维护一处；错误迟到时靠它判断该不该落地。
        turnSessionId = _state.value.activeSessionId
        // 这一轮的起点，用于在回合结束时算出耗时（见 onEngineTurnComplete）。
        turnStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        runCatching { engine.sendPrompt(prompt, extraContent, engineOverrides()) }.onFailure { error ->
            finishTurnWithError(ToolText.friendlyError(error.message, error))
        }
    }

    /** 把当前界面状态翻译成引擎覆盖项。 */
    private fun engineOverrides(): EngineOverrides {
        val s = _state.value
        // 搜索后端：**有生效的服务就用它**，否则回落到免费后端（auto）。
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
            initPrompt = s.settings.initPrompt,
            summaryPrompt = s.settings.summaryPrompt,
            compactPromptHead = s.settings.compactPromptHead,
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
            shizukuExecutionEnabled = s.settings.shizukuExecutionEnabled,
            forcedKeepAliveEnabled = s.settings.forcedKeepAliveEnabled,
            maxConcurrentSubagents = s.settings.maxConcurrentSubagents,
            model = configuredModelIdOrNull(s.modelLabel)?.takeIf { s.modelProfileId != null },
            modelProfileId = s.modelProfileId,
            roleCard = roleCardOverride,
        )
    }

    fun stop() {
        val snapshot = _state.value
        // 什么都没跑时不进停止流程：否则会点亮一个"正在停止…"，
        // 三秒后再由看门狗自己收掉 —— 无中生有的一句状态。
        if (!snapshot.composerBusy && !snapshot.stopping
            && snapshot.workingStatus == null && !engine.isBusy()) {
            return
        }
        val attempt = stopAttempts + 1
        stopAttempts = attempt
        val firstAttempt = attempt == 1

        // 引擎的 cancel() 会一并取消权限/提问/计划三个门控，
        // 所以这里不需要再单独回执一次权限，只要把界面状态收干净。
        engine.cancel()
        if (firstAttempt) {
            pendingPermissionId = null
            // 在途的流式残片与工具进度缓冲全部丢弃：它们属于已经作废的这一轮。
            progressBuffers.clear()
            progressLastFlush.clear()
            liveTools.clear()
            currentGroupId = null
        }
        _state.update {
            // 停止时把还在排队的预输入**还原回输入框**（拼成多行逐条显示）
            val restored = it.pendingInputs.joinToString("\n") { q -> q.text }
            it.copy(
                stopping = true,
                // 保持忙碌态：真实状态就是"命令可能还在跑"。
                composerBusy = true,
                workingStatus = if (firstAttempt) {
                    "正在停止当前任务…"
                } else {
                    "正在停止当前任务…（第 $attempt 次）"
                },
                permissionRequest = null,
                composerText = if (restored.isNotEmpty()) restored else it.composerText,
                composerTextEpoch = it.composerTextEpoch + 1,
                pendingInputs = emptyList(),
                message = when {
                    firstAttempt && it.pendingInputs.isNotEmpty() ->
                        "正在停止当前任务，${it.pendingInputs.size} 条预输入已放回输入框"
                    firstAttempt -> "正在停止当前任务"
                    else -> "已再次发送停止指令"
                },
            )
        }
        armStopWatchdog()
    }

    /** 停止看门狗：等待引擎的收尾确认，并且**不把界面锁死**。 */
    private fun armStopWatchdog() {
        stopWatchdog?.cancel()
        stopWatchdog = viewModelScope.launch {
            delay(STOP_CONFIRM_MS)
            if (!isStopping()) return@launch
            if (!engine.isBusy()) {
                clearStopState("已停止当前任务")
                return@launch
            }
            _state.update {
                if (!it.stopping) it
                else it.copy(
                    workingStatus = "停止指令已发送，正在等待命令退出…",
                    message = "命令没有立刻退出，可再点一次停止键重发中断",
                )
            }
            delay(STOP_UNLOCK_MS)
            if (!isStopping()) return@launch
            if (!engine.isBusy()) {
                clearStopState("已停止当前任务")
                return@launch
            }
            // 见 KDoc 第 2 档：解锁输入，保留停止键。
            _state.update {
                if (!it.stopping) it
                else it.copy(
                    composerBusy = false,
                    workingStatus = null,
                    busySessionIds = it.busySessionIds - it.activeSessionId,
                    message = "停止指令已发送，但命令仍在运行；可再次点击停止键重发中断",
                    messageIsError = true,
                )
            }
        }
    }

    private fun isStopping(): Boolean = stopAttempts > 0 && _state.value.stopping

    /** 收掉停止流程的簿记（看门狗 + 计数）；不动界面状态本身。 */
    private fun resetStopTracking() {
        stopWatchdog?.cancel()
        stopWatchdog = null
        stopAttempts = 0
    }

    /** 停止流程结束（引擎确认收尾、或者看门狗判定它已经不忙了）。 */
    private fun clearStopState(message: String? = null) {
        resetStopTracking()
        _state.update {
            if (!it.stopping) it
            else it.copy(
                stopping = false,
                composerBusy = false,
                workingStatus = null,
                busySessionIds = it.busySessionIds - it.activeSessionId,
                message = message ?: it.message,
                messageIsError = if (message == null) it.messageIsError else false,
            )
        }
    }

    // ==================================================================

    override fun onEngineSessionStarted(model: String) {
        if (turnStartedAtElapsedMs <= 0L) {
            turnStartedAtElapsedMs = SystemClock.elapsedRealtime()
        }
        streamingAssistantId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        // 新回合开跑，上一轮的停止流程自动作废：看门狗若还挂着，
        // 它几秒后会去问"停了没有"，而那时候已经在跑新任务了。
        resetStopTracking()
        _state.update {
            it.copy(
                stopping = false,
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
    }

    /** 取当前流式气泡的 id；没有就建一个。 */
    private fun ensureStreamingAssistant(): String {
        streamingAssistantId?.let { return it }
        val id = nextId("a")
        streamingAssistantId = id
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = id,
                    kind = ChatKind.ASSISTANT,
                    title = "ZhiCode",
                    body = "",
                    streaming = true,
                    processSteps = listOf("开始分析请求"),
                ),
            )
        }
        return id
    }

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
        sealStreamingAssistant()
        val groupId = nextId("g")
        currentGroupId = groupId
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = groupId,
                    kind = ChatKind.TOOL_GROUP,
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
            startedAtMs = SystemClock.elapsedRealtime(),
            // 原始命令行（未截断）：折叠态用 summary，展开态要看完整的那一份。
            command = command,
            // 折叠组副行要显示的路径/模式。规则与 `summary` **不同**（见 ToolText.activityHint），
            // 所以在这里按参考实现的 `toolActivityHint` 算好存下来。
            hint = ToolText.activityHint(name, input),
            // `ReadMany` 读了几个文件 —— 组表头要按实际条数计入"读取 N 个文件"。
            readRequests = ToolText.readRequestCount(name, input),
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
                        item.copy(tools = tools)
                    }
                },
                workingStatus = "正在执行 $name…",
            )
        }
        appendProcessStep("调用工具：$name")
    }

    override fun onEngineToolProgress(id: String, chunk: String, stderr: Boolean, elapsedMs: Long) {
        if (liveTools[id] == null) return
        val buffer = LiveOutput.append(
            previous = progressBuffers[id] ?: LiveOutput.Buffer(),
            chunk = chunk,
            stderr = stderr,
            keep = LIVE_OUTPUT_LIMIT,
        )
        progressBuffers[id] = buffer

        val now = System.currentTimeMillis()
        val last = progressLastFlush[id] ?: 0L
        if (now - last < PROGRESS_FLUSH_MS) return
        progressLastFlush[id] = now
        val text = buffer.text
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map {
                            if (it.id == id) {
                                it.copy(
                                    output = text,
                                    // 运行标签要显示"错误输出有多少"，所以两个计数
                                    // 要跟着文本一起落进 state。
                                    stdoutChars = buffer.stdoutChars,
                                    stderrChars = buffer.stderrChars,
                                    elapsedMs = maxOf(it.elapsedMs, elapsedMs),
                                )
                            } else {
                                it
                            }
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
        previews: List<ChatImage>,
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
                                diff = diff,
                                // 同 `diff`：漏了这行，沙箱截图就停在这一层，工具卡里什么都不显示
                                // （模型那边照样看得到图），且**编译器不会提醒**。
                                previews = previews,
                                elapsedMs = maxOf(
                                    tool.elapsedMs,
                                    if (tool.startedAtMs > 0L) maxOf(0L, SystemClock.elapsedRealtime() - tool.startedAtMs) else 0L,
                                ),
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

        /* 收集"这一轮改了哪些文件"，写到最后一条助手消息上（元信息行用）。 */
        val changedFile = runCatching {
            if (diff.isNotBlank() || addedLines > 0 || deletedLines > 0) {
                val summary = _state.value.transcript
                    .asSequence()
                    .filter { it.kind == ChatKind.TOOL_GROUP }
                    .flatMap { it.tools.asSequence() }
                    .firstOrNull { it.id == id }
                    ?.summary
                    ?.trim()
                    .orEmpty()
                // summary 可能带着动词前缀，取最后一段更像路径的部分。
                summary.substringAfterLast(' ').ifBlank { null }
            } else null
        }.getOrNull()
        if (changedFile != null) {
            _state.update { s ->
                val index = s.transcript.indexOfLast { it.kind == ChatKind.ASSISTANT }
                if (index < 0) s
                else s.copy(
                    transcript = s.transcript.toMutableList().also { list ->
                        val item = list[index]
                        if (changedFile in item.editedFiles) {
                            // 同一个文件被改多次时只记一次。
                        } else {
                            list[index] = item.copy(
                                editedFiles = (item.editedFiles + changedFile).distinct(),
                            )
                        }
                    },
                    transcriptRevision = s.transcriptRevision + 1,
                )
            }
        }
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

    override fun onHostPermissionClosed(requestId: String) {
        if (pendingPermissionId != requestId) return
        pendingPermissionId = null
        _state.update { it.copy(permissionRequest = null) }
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

    /** 展示提问流程的第 [step] 步。 */
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
                    prompt = question.optString("question", "ZhiCode 应该怎么做？"),
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
        appendInfo("已跳过提问", "ZhiCode 的提问被跳过，它会按「未选择」继续。")
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
        turnStartedAtElapsedMs = SystemClock.elapsedRealtime()
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
        // 停止流程到此为止：引擎已经把这一轮收干净了（见 stop() 的说明）。
        // 这里取消看门狗，所以"等一下就好"那条兜底路径不会再多说一句话。
        resetStopTracking()
        val cancelled = "cancelled".equals(stopReason, ignoreCase = true)
        // 这一轮的耗时：只在**确有起点**时算，否则不写（保持 -1 = 未知）。
        val elapsed = turnStartedAtElapsedMs.takeIf { it > 0L }
            ?.let { android.os.SystemClock.elapsedRealtime() - it }
            ?: -1L
        turnStartedAtElapsedMs = 0L
        _state.update { s ->
            s.copy(
                // 只有"我们确实在等停止"且引擎报的是取消，才可以说已停止。
                // 正常跑完的回合也走这个回调，那时候说"已停止"就是假话。
                stopping = false,
                composerBusy = false,
                workingStatus = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
                contextTokens = engine.estimateContextTokens().takeIf { it > 0 } ?: s.contextTokens,
                sessions = s.sessions.map { session ->
                    if (session.id == s.activeSessionId) session.copy(busy = false, updatedAtLabel = "刚刚") else session
                },
                message = if (cancelled && s.stopping) "已停止当前任务" else s.message,
                messageIsError = if (cancelled && s.stopping) false else s.messageIsError,
                transcript = if (elapsed > 0) {
                    val index = s.transcript.indexOfLast { it.kind == ChatKind.ASSISTANT }
                    if (index < 0) s.transcript
                    else s.transcript.toMutableList().also { list ->
                        list[index] = list[index].copy(elapsedMs = elapsed)
                    }
                } else {
                    s.transcript
                },
                transcriptRevision = if (elapsed > 0) s.transcriptRevision + 1 else s.transcriptRevision,
            )
        }
        // 第一轮结束后会话才真正落库（引擎在 sendPrompt 里创建 SessionStore），
        // 所以这里必须重读列表，否则新会话在侧栏里不存在。
        syncActiveSessionFromDisk()
    }

    /** 回合结束后把当前会话对齐到库里状态。 */
    private fun syncActiveSessionFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.listAll()
            val sessionId = engine.sessionId()
            _state.update { s ->
                val activeId = when {
                    s.activeSessionId.isNotEmpty() -> s.activeSessionId
                    sessionId != null -> sessionId
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

    private fun finishTurnWithError(text: String) {
        val owner = turnSessionId
        turnSessionId = null
        turnStartedAtElapsedMs = 0L
        if (owner != null && owner != _state.value.activeSessionId) return

        finalizeStreaming(keepIfEmpty = false)
        // 以错误收尾也算"停止流程结束了"：不取消看门狗的话，它几秒后还会
        // 去问一个已经结束的回合，顺手把提示语改掉。
        resetStopTracking()
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = nextId("e"),
                    kind = ChatKind.ERROR,
                    title = "错误",
                    body = text,
                ),
                stopping = false,
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

    /** 回执一次权限请求。 */
    fun resolvePermission(requestId: String, allow: Boolean, alwaysAllow: Boolean) {
        if (pendingPermissionId != requestId) return
        pendingPermissionId = null
        val accepted = engine.respondPermission(requestId, allow)
        if (!accepted && requestId.startsWith("pi-host-")) {
            _state.update { it.copy(permissionRequest = null) }
            return
        }
        _state.update {
            it.copy(
                permissionRequest = null,
                permissionMode = if (alwaysAllow) PermissionMode.BYPASS else it.permissionMode,
                message = if (alwaysAllow) {
                    if (allow) "已允许本次调用，并切换为全部放行" else "已拒绝本次调用，并切换为全部放行"
                } else {
                    if (allow) "已允许本次调用" else "已拒绝本次调用"
                },
            )
        }
        if (alwaysAllow) {
            runCatching { engine.configure(engineOverrides()) }
            persistRuntimeChoice(mode = PermissionMode.BYPASS)
        }
    }

    private fun kindOf(toolName: String): ToolKind = when (toolName) {
        "Grep", "Glob", "Search" -> ToolKind.SEARCH
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> ToolKind.READ
        "Edit", "MultiEdit", "Write", "Move", "Delete", "Mkdir", "Copy" -> ToolKind.EDIT
        "Bash", "Root", "Shizuku", "BashTool" -> ToolKind.COMMAND
        else -> ToolKind.OTHER
    }

    private fun subtitleFor(tool: String): String = when (tool) {
        "Root" -> "高风险：ZhiCode 请求以 Android uid 0 执行系统命令"
        "Shizuku" -> "ZhiCode 请求以 shell 身份（uid 2000）执行 Android 系统命令"
        "Bash" -> "ZhiCode 请求在本地 Termux 环境中执行以下命令"
        "AndroidIntent" -> "ZhiCode 请求通过 Android 应用进程打开手机 App、网页或系统页面"
        "Write", "Edit", "MultiEdit" -> "ZhiCode 请求修改项目中的文件"
        "Delete" -> "ZhiCode 请求删除项目中的文件或目录"
        else -> "ZhiCode 请求调用工具 $tool"
    }

    // ---------- 斜杠命令 ----------

    private fun runSlashCommand(raw: String) {
        val command = raw.substringBefore(' ').lowercase(Locale.US)
        val arg = raw.substringAfter(' ', "").trim()
        when (command) {
            "/clear", "/new" -> newSession()
            "/terminal" -> selectTab(WorkspaceTab.TERMINAL)
            "/files" -> selectTab(WorkspaceTab.FILES)
            "/plan" -> enterPlanMode(arg)
            "/permissions" -> showPermissionPicker()
            "/effort" -> showEffortPicker()
            "/model" -> showModelPicker()
            "/cancel" -> stop()
            "/config" -> openSettings()
            // `/mcp` 与 IQ Code 0.30.4 的 `handleMcpSlash` 同一套子命令
            "/mcp" -> handleMcpSlash(arg)
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
            // 带参数 = 直接附加那个技能（见 handleSkillsSlash）。
            "/skills" -> handleSkillsSlash(arg)
            "/agents" -> openRoleCards()
            "/memory" -> openMemory()
            "/init" -> runInitPrompt()

            // 沙箱是**跨进程**的：引擎在 :zhisandbox，界面必须交给系统启动。
            // 以前这条命令没有路由，会掉进下面的"指令尚未移植"兜底卡片。
            "/sandbox" -> openSandbox()

            "/web" -> handleWebSlash(arg)
            "/shizuku" -> handleShizukuSlash(arg)
            // 这两个原先都**没有路由**，会掉进下面的"指令尚未移植"兜底卡片
            "/keepalive" -> handleKeepAliveSlash(arg)
            "/root" -> handleRootSlash(arg)

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
                    append("` /doctor ` `/runtime` `/repair` — 内置 Debian 容器环境\n")
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

    /** 重新估算上下文用量。 */
    private fun syncContextUsage() {
        _state.update { s ->
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            s.copy(contextTokens = engine.estimateContextTokens(), contextWindow = window)
        }
    }

    /** `/context 1m` 这类参数。 */
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

    /** 删除一条历史消息 —— 先二次确认。 */
    private fun confirmDeleteTranscriptMessage(item: ChatItem) {
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "删除这条消息",
                    intent = ChoiceIntent.MESSAGE_DELETE_CONFIRM,
                    // 锚在那条消息旁边：用户能看清自己要删的是哪一条。
                    anchorId = item.id,
                    prompt = "删除后这条消息的内容会从会话记录里移除，且无法撤销。",
                    options = listOf(ChoiceOption("确认删除")),
                    submitLabel = "删除",
                    cancelLabel = "取消",
                ),
            )
        }
    }

    private fun deleteTranscriptMessage(item: ChatItem,
    ) {
        val sessionId = _state.value.activeSessionId
        if (sessionId.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                SessionReader.deleteMessage(sessionId, item.id)
            }
            result.onSuccess {
                _state.update { s ->
                    s.copy(
                        transcript = s.transcript.filterNot { it.id == item.id },
                        // 就地修改：条数与首尾 id 可能都不变，只有内容少了。
                        // 不加这一项，下面的指纹会认为"什么都没变"，界面不更新。
                        transcriptRevision = s.transcriptRevision + 1,
                        message = "已删除这条消息",
                    )
                }
                // 让引擎的内存历史与库对齐。失败不致命：下一轮 sendPrompt 用的是
                // 界面这一份 transcript，只是模型可能多看见一条已删的消息。
                if (_state.value.activeSessionId == sessionId && !restoringSession) {
                    runCatching { engine.resumeConversation(sessionId, engineOverrides()).await() }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        message = "删除失败：" + (error.message ?: "未知原因"),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    /** 从消息菜单进入导出：先打开导出面板让人选格式与选项。 */
    private fun shareTranscriptMessage(item: ChatItem) {
        _state.update {
            it.copy(
                exportSheet = ExportSheetState(
                    title = if (item.kind == ChatKind.USER) "导出这条提问" else "导出这条回复",
                    items = listOf(item),
                    singleSource = true,
                    seedId = item.id,
                ),
            )
        }
    }

    // ---------- 导出面板（照 rikkahub 的 ChatExportSheet）----------

    /** 打开导出面板（多选导出走这里）。 */
    fun openExportSheet() {
        val snapshot = _state.value
        val selected = snapshot.transcript.filter { it.id in snapshot.multiSelectedIds }
        if (selected.isEmpty()) {
            _state.update { it.copy(message = "还没有勾选任何消息", messageIsError = true) }
            return
        }
        _state.update {
            it.copy(
                exportSheet = ExportSheetState(
                    title = "导出 ${selected.size} 条消息",
                    items = selected,
                ),
            )
        }
    }

    fun dismissExportSheet() = _state.update { it.copy(exportSheet = null) }

    /** 面板里点「选择更多消息」：关掉面板，进入多选并预先勾上那一条。 */
    fun exportSelectMore() {
        val seed = _state.value.exportSheet?.seedId ?: return
        _state.update { it.copy(exportSheet = null) }
        beginMultiSelect(seed)
    }

    fun setExportIncludeReasoning(include: Boolean) {
        _state.update { s -> s.copy(exportSheet = s.exportSheet?.copy(includeReasoning = include)) }
    }

    fun setExportExpandReasoning(expand: Boolean) {
        _state.update { s -> s.copy(exportSheet = s.exportSheet?.copy(expandReasoning = expand)) }
    }

    fun exportSheetAsMarkdown() {
        val sheet = _state.value.exportSheet ?: return
        /* 与其他导出一样**限制条数**。 */
        val capped = sheet.items.take(MaxExportItems)
        val markdown = buildString {
            append("# ").append(sheet.title).append("\n\n")
            append("*Exported on ").append(clockLabel()).append("*\n\n")
            capped.forEach { item ->
                append(buildMessageMarkdown(item, sheet.includeReasoning)).append("\n---\n\n")
            }
        }
        _state.update {
            it.copy(
                exportSheet = null,
                pendingExport = ExportPayload(
                    nonce = ++exportNonce,
                    kind = ExportKind.MARKDOWN,
                    mimeType = "text/markdown",
                    suggestedName = exportSuggestedName("md"),
                    markdown = markdown,
                    title = sheet.title,
                    includeReasoning = sheet.includeReasoning,
                ),
            )
        }
        cancelMultiSelect()
    }

    /** 面板里点「导出为图片」：只准备数据，渲染与落盘交给界面（SAF）。 */
    fun exportSheetAsImage() {
        val sheet = _state.value.exportSheet ?: return
        val capped = sheet.items.take(MaxExportItems)
        _state.update {
            it.copy(
                exportSheet = null,
                pendingExport = ExportPayload(
                    nonce = ++exportNonce,
                    kind = ExportKind.IMAGE,
                    mimeType = "image/png",
                    suggestedName = exportSuggestedName("png"),
                    items = capped,
                    title = sheet.title,
                    includeReasoning = sheet.includeReasoning,
                    expandReasoning = sheet.expandReasoning,
                ),
                message = if (sheet.items.size > capped.size) {
                    "只导出前 ${capped.size} 条（一次最多 $MaxExportItems 条）"
                } else {
                    "正在准备图片…"
                },
            )
        }
        cancelMultiSelect()
    }

    /** 界面落盘完成后清空请求。 */
    fun clearPendingExport() = _state.update { it.copy(pendingExport = null) }

    /** 与 rikkahub 一致的导出文件名：`chat-export-yyyy-MM-dd_HH-mm-ss.ext`。 */
    private fun exportSuggestedName(extension: String): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        return "chat-export-$stamp.$extension"
    }
    /** 进入多选模式，并把触发它的那条消息预先勾上。 */
    fun beginMultiSelect(anchorId: String) {
        _state.update {
            it.copy(
                multiSelectMode = true,
                multiSelectedIds = if (anchorId.isEmpty()) emptySet() else setOf(anchorId),
                choicePicker = null,
            )
        }
    }

    fun toggleMultiSelect(id: String) {
        _state.update {
            val next = if (id in it.multiSelectedIds) it.multiSelectedIds - id else it.multiSelectedIds + id
            it.copy(multiSelectedIds = next)
        }
    }

    /** 拖动对选：**整段替换**选中集合。 */
    fun setMultiSelectRange(ids: Set<String>) {
        _state.update { if (it.multiSelectedIds == ids) it else it.copy(multiSelectedIds = ids) }
    }

    /** 界面落盘完成后清空请求。 */

    /** 界面层发生、但需要显示成"应用提示条"的错误（例如图片导出失败）。 */
    fun showMessage(text: String, isError: Boolean = false) {
        _state.update { it.copy(message = text, messageIsError = isError) }
    }

    fun cancelMultiSelect() {
        _state.update { it.copy(multiSelectMode = false, multiSelectedIds = emptySet()) }
    }

    /** 全选 / 取消全选。 */
    fun toggleSelectAll() {
        _state.update {
            val all = it.transcript.map { item -> item.id }.toSet()
            if (all.isEmpty()) return@update it
            val allSelected = all.all { id -> id in it.multiSelectedIds }
            it.copy(multiSelectedIds = if (allSelected) emptySet() else all)
        }
    }

    /** 界面消费完分享请求后清空（见 `ShareLauncher`）。 */

    /** 一条消息的 Markdown 表示：正文 + 思考（折叠成引用块）+ 工具调用摘要。 */
    private fun buildMessageMarkdown(item: ChatItem, includeReasoning: Boolean = true): String = buildString {
        append("# ").append(if (item.kind == ChatKind.USER) "用户" else "ZhiCode").append("\n\n")
        if (item.body.isNotBlank()) {
            append(item.body).append("\n")
        }
        // 思考过程按开关决定带不带：它常常比正文长好几倍，
        // 分享给别人看结论时不该强制塞进去。
        if (includeReasoning && item.thinking.isNotBlank()) {
            append("\n> **思考过程**\n")
            item.thinking.lineSequence().forEach { line -> append("> ").append(line).append("\n") }
        }
        if (item.tools.isNotEmpty()) {
            append("\n## 工具调用\n")
            item.tools.forEach { tool ->
                append("\n### ").append(tool.displayName).append("\n\n")
                if (tool.summary.isNotBlank()) append("`").append(tool.summary).append("`\n\n")
                if (tool.output.isNotBlank()) {
                    append("```\n").append(tool.output.take(4000)).append("\n```\n")
                }
            }
        }
    }

    private fun retryLastUserPrompt(from: ChatItem?) {
        // 定位逻辑在纯逻辑层（`ChatNavigation.previousUserPrompt`，有单测）：
        // 这里只负责把对话流翻译成它需要的最小输入。
        val transcript = _state.value.transcript
        val previous = ChatNavigation.previousUserPrompt(
            entries = transcript.map { item ->
                ChatNavigation.Entry(
                    id = item.id,
                    isUser = item.kind == ChatKind.USER,
                    body = item.body,
                )
            },
            anchorId = from?.id,
        )
        if (previous == null) {
            _state.update { it.copy(message = "这条消息之前没有可重试的问题", messageIsError = true) }
            return
        }
        _state.update { it.copy(composerText = previous, composerTextEpoch = it.composerTextEpoch + 1) }
        send()
    }

    /** `/web`：联网搜索。 */
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

    private fun handleShizukuSlash(arg: String) {
        when {
            arg.isEmpty() -> {
                openSettings()
                _state.update { it.copy(settingsDraft = it.settingsDraft?.copy(category = SettingsCategory.AGENT_SECURITY)) }
            }
            arg.equals("on", ignoreCase = true) || arg.equals("enable", ignoreCase = true) ->
                setShizukuExecution(true)
            arg.equals("off", ignoreCase = true) || arg.equals("disable", ignoreCase = true) ->
                setShizukuExecution(false)
            else -> {
                val state = ShizukuBridge.state(getApplication())
                val enabled = _state.value.settings.shizukuExecutionEnabled
                _state.update {
                    it.copy(
                        message = "Shizuku ${ShizukuBridge.stateLabel(getApplication())}"
                            + if (enabled) "；Agent 可用" else "；Agent 不可用（/shizuku on 开启）",
                        messageIsError = enabled && state != ShizukuBridge.GRANTED,
                    )
                }
            }
        }
    }

    /** 开关 Shizuku 工具，并**立刻**落盘 + 重配引擎。 */
    fun setShizukuExecution(enabled: Boolean) {
        val draft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        applySettingsDraft(draft.copy(shizukuExecutionEnabled = enabled))
        val granted = ShizukuBridge.state(getApplication()) == ShizukuBridge.GRANTED
        _state.update {
            it.copy(
                message = when {
                    !enabled -> "Agent Shizuku 已关闭"
                    granted -> "Agent Shizuku 已开启（shell 身份已授权）"
                    else -> "Agent Shizuku 已开启；还需要在 设置 → Agent 与安全 里授权"
                },
            )
        }
        runCatching { engine.configure(engineOverrides()) }
    }

    /** 执行一次手动联网动作，并把文本结果落成一张助手卡片。 */
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
                // 内部虚拟化路径，又长又吓人且用户无法据此操作（与附加选择器读不到目录时的空态同理）。
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

    /** `/plan` 进入计划模式。 */
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
            _state.update { it.copy(composerText = arg, composerTextEpoch = it.composerTextEpoch + 1) }
            send()
        }
    }

    /** 批准 / 退回计划。 */
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

    /** 打开 API 配置（列表页）。 */
    fun openApiConfig() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:api")
        _state.update {
            it.copy(
                apiConfig = ApiConfigState(
                    profiles = emptyList(),
                    activeId = "",
                    loading = true,
                    autoCompact = it.settings.autoCompact,
                    autoCompactPercent = it.settings.autoCompactPercent,
                    customSystemPrompt = it.settings.customSystemPrompt,
                ),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { ApiConfigStore.read(getApplication()) }.getOrNull()
            _state.update { s ->
                val current = s.apiConfig ?: return@update s
                s.copy(
                    apiConfig = loaded?.copy(
                        autoCompact = s.settings.autoCompact,
                        autoCompactPercent = s.settings.autoCompactPercent,
                        customSystemPrompt = s.settings.customSystemPrompt,
                    ) ?: current.copy(loading = false),
                )
            }
            if (loaded != null) {
                syncActiveProfile()
                syncRoleCardFromStore()
            }
        }
    }

    fun closeApiConfig() = _state.update { it.copy(apiConfig = null) }

    fun openApiConfigManageFromPicker() {
        _state.update { it.copy(modelPicker = null) }
        openApiConfig()
    }

    fun openApiConfigEditActiveProvider() {
        val targetProfileId = _state.value.modelProfileId
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:api")
        _state.update {
            it.copy(
                modelThinkingPicker = null,
                apiConfig = ApiConfigState(
                    profiles = emptyList(),
                    activeId = "",
                    loading = true,
                    autoCompact = it.settings.autoCompact,
                    autoCompactPercent = it.settings.autoCompactPercent,
                    customSystemPrompt = it.settings.customSystemPrompt,
                ),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { ApiConfigStore.read(getApplication()) }.getOrNull()
            val active = loaded?.let { cfg ->
                cfg.profiles.firstOrNull { it.id == targetProfileId }
                    ?: cfg.profiles.firstOrNull { it.id == cfg.activeId }
                    ?: cfg.profiles.firstOrNull()
            }
            _state.update { s ->
                val current = s.apiConfig ?: return@update s
                s.copy(
                    apiConfig = loaded?.copy(
                        form = active?.let { ApiProfileDraft.from(it) } ?: ApiProfileDraft.blank(),
                        autoCompact = s.settings.autoCompact,
                        autoCompactPercent = s.settings.autoCompactPercent,
                        customSystemPrompt = s.settings.customSystemPrompt,
                    ) ?: current.copy(loading = false),
                )
            }
            if (loaded != null) {
                syncActiveProfile()
                syncRoleCardFromStore()
            }
        }
    }

    fun updateContextAndPromptFromApiConfig(
        autoCompact: Boolean,
        autoCompactPercent: Int,
        customSystemPrompt: String,
    ) {
        val baseDraft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        val nextDraft = baseDraft.copy(
            autoCompact = autoCompact,
            autoCompactPercent = autoCompactPercent,
            customSystemPrompt = customSystemPrompt,
        )
        applySettingsDraft(nextDraft)
        _state.update { s ->
            s.copy(
                apiConfig = s.apiConfig?.copy(
                    autoCompact = s.settings.autoCompact,
                    autoCompactPercent = s.settings.autoCompactPercent,
                    customSystemPrompt = s.settings.customSystemPrompt,
                ),
            )
        }
    }

    fun openApiProfileFormFromModelPicker() {
        hideSidebarForNavigation()
        _state.update {
            it.copy(
                apiConfig = ApiConfigState(
                    // 列表载荷与这条链路无关（页面不渲染列表），给空是安全的：
                    // `ApiConfigPage` 里只有列表分支会读它们，而 standalone 不渲染列表。
                    profiles = emptyList(),
                    activeId = "",
                    form = ApiProfileDraft.blank(),
                    standalone = true,
                ),
            )
        }
    }

    // ---------------------------------------------------------------- 搜索服务

    /** 打开「搜索服务」页（形态对齐 RikkaHub：多服务列表 + 当前生效项）。 */
    fun openSearchServices() {
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:search")
        // 先同步推页、数据后读 —— 理由见 openApiConfig。原来这里同样把
        // `SearchServiceStore.read()`（还带上迁移）夹在「读盘」和「翻页」之间。
        _state.update {
            it.copy(
                searchServices = SearchServicesState(loading = true),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 先把老版「单个 provider」迁成一条服务，否则老用户的配置会在这次打开时
            // 看起来"全都消失了"（列表空、而搜索也回落到免费后端）。
            runCatching {
                SearchServiceStore.migrateLegacyIfNeeded(
                    getApplication(),
                    _state.value.settings.webSearchProvider,
                    _state.value.settings.webSearchSearxngUrl,
                    _state.value.settings.webSearchKeys[_state.value.settings.webSearchProvider]
                        .orEmpty(),
                )
            }
            val loaded = runCatching { SearchServiceStore.read(getApplication()) }.getOrNull()
            _state.update { s ->
                // 读盘期间用户可能已经返回：载荷已置空，结果丢弃。
                val current = s.searchServices ?: return@update s
                s.copy(searchServices = loaded ?: current.copy(loading = false))
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

    /** 把「当前生效的搜索服务」推给引擎。 */
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

    /** 保存一条 API 配置。 */
    fun saveApiProfile() {
        val form = _state.value.apiConfig?.form ?: return
        if (!form.saveable) return
        // 这条链路是"从模型面板进来加一条配置"，保存完就该回去接着挑模型 ——
        // 所以先把标记抄出来，等写盘成功后再决定是关页还是留在列表页。
        val standalone = _state.value.apiConfig?.standalone == true
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.save(getApplication(), form)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _state.update { it.copy(message = "保存失败：" + (error?.message ?: "未知原因"), messageIsError = true) }
                return@launch
            }
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                if (standalone) {
                    /* standalone：关掉配置页（回到工作区），模型面板原样还开着。 */
                    it.copy(apiConfig = null, message = "API 配置已保存")
                } else {
                    it.copy(
                        apiConfig = state.copy(
                            autoCompact = it.settings.autoCompact,
                            autoCompactPercent = it.settings.autoCompactPercent,
                            customSystemPrompt = it.settings.customSystemPrompt,
                        ),
                        message = "API 配置已保存",
                    )
                }
            }
            syncActiveProfile()
            syncRoleCardFromStore()
            if (standalone) refreshModelPickerProfiles()
        }
    }

    fun applyApiPreset(preset: PiProviderPreset) = _state.update {
        val config = it.apiConfig ?: return@update it
        it.copy(apiConfig = config.copy(form = ApiProfileDraft.fromPreset(preset)))
    }

    suspend fun fetchDraftModelCatalog(draft: ApiProfileDraft): Result<List<ModelOption>> =
        withContext(Dispatchers.IO) {
            ModelCatalogStore.fetchForDraft(
                context = getApplication(),
                profileId = draft.id,
                protocolWire = when (draft.protocol) {
                    com.zhizhu.zhicode.compose.model.ApiProtocol.OPENAI_RESPONSES -> "openai-responses"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.OPENAI_CHAT -> "openai-chat"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.ANTHROPIC -> "anthropic"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.GOOGLE_GENERATIVE_AI -> "google-generative-ai"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.BEDROCK_CONVERSE_STREAM -> "bedrock-converse-stream"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.MISTRAL_CONVERSATIONS -> "mistral-conversations"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.CODEX_RESPONSES -> "codex-responses"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.ZCODE -> "zcode"
                    com.zhizhu.zhicode.compose.model.ApiProtocol.DEBUG_SCRIPTED -> "debug-scripted"
                },
                baseUrl = draft.baseUrl,
                rawApiKey = draft.apiKey,
                extraHeaders = draft.extraHeaders,
            ).map { it.models }.recoverCatching { err ->
                throw IllegalStateException(ModelCatalogStore.friendlyError(err))
            }
        }

    private suspend fun refreshModelPickerProfiles() {
        val config = withContext(Dispatchers.IO) { ApiConfigStore.read(getApplication()) }
        _state.update { s ->
            val picker = s.modelPicker ?: return@update s
            s.copy(
                modelPicker = picker.copy(
                    profiles = config.profiles.map { it.toPickerTab() },
                    activeProfileId = config.activeId,
                ),
            )
        }
    }

    fun selectApiProfile(profileId: String) {
        val sessionSeq = openSessionSeq
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.select(getApplication(), profileId)
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(
                    apiConfig = state.copy(
                        autoCompact = it.settings.autoCompact,
                        autoCompactPercent = it.settings.autoCompactPercent,
                        customSystemPrompt = it.settings.customSystemPrompt,
                    ),
                    message = if (result.isSuccess) "已切换 API 配置" else "切换失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            if (result.isSuccess && openSessionSeq == sessionSeq) {
                withContext(Dispatchers.Main) {
                    val profile = state.profiles.firstOrNull { it.id == profileId }
                    if (profile != null && openSessionSeq == sessionSeq && !restoringSession) {
                        selectSessionModel(profile, profile.model, _state.value.effort)
                    }
                }
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
                    apiConfig = state.copy(
                        autoCompact = it.settings.autoCompact,
                        autoCompactPercent = it.settings.autoCompactPercent,
                        customSystemPrompt = it.settings.customSystemPrompt,
                    ),
                    message = if (result.isSuccess) "配置已删除" else "无法删除：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    // ---------- MCP 配置 ----------

    /** 打开 MCP 服务器配置（列表页）。 */
    /** `/mcp` 的子命令（对齐 IQ Code 0.30.4 的 `handleMcpSlash`）。 */
    private fun handleMcpSlash(arg: String) {
        val a = arg.trim()
        if (a.isEmpty()) {
            openMcpConfig()
            return
        }
        val op = a.substringBefore(' ').lowercase(Locale.US)
        val name = a.substringAfter(' ', "").trim()
        when (op) {
            "list", "列表" -> showMcpSummary()
            "add", "添加" -> {
                openMcpConfig()
                // 数据是异步读的：表单的开合状态由 mcpConfig 承载，所以等它落地再开表单。
                // 用 ready 标志而不是延时猜：读盘慢的机器上延时一定会错。
                mcpOpenFormWhenReady = true
            }
            "enable", "disable" -> {
                if (name.isEmpty()) {
                    _state.update { it.copy(message = "用法：/mcp $op <服务器名称>", messageIsError = true) }
                    return
                }
                val target = _state.value.mcpConfig?.servers?.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: runCatching { McpStore.read().servers.firstOrNull { it.name.equals(name, ignoreCase = true) } }.getOrNull()
                if (target == null) {
                    _state.update { it.copy(message = "未找到 MCP 服务器：$name", messageIsError = true) }
                    return
                }
                val wantEnabled = op == "enable"
                if (target.enabled == wantEnabled) {
                    _state.update { it.copy(message = "${target.name} 已经是${if (wantEnabled) "启用" else "停用"}状态") }
                    return
                }
                val result = McpStore.setEnabled(target.name, wantEnabled)
                _state.update {
                    it.copy(
                        message = if (result.isSuccess) {
                            "${if (wantEnabled) "已启用" else "已停用"}：${target.name}"
                        } else {
                            "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                        },
                        messageIsError = result.isFailure,
                    )
                }
            }
            "remove", "delete", "删除" -> {
                if (name.isEmpty()) {
                    _state.update { it.copy(message = "用法：/mcp $op <服务器名称>", messageIsError = true) }
                    return
                }
                val result = McpStore.delete(name)
                _state.update {
                    it.copy(
                        message = if (result.isSuccess) "已删除 MCP 服务器：$name"
                        else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                        messageIsError = result.isFailure,
                    )
                }
            }
            else -> _state.update {
                it.copy(
                    message = "/mcp 支持：list、add、enable <名称>、disable <名称>、remove <名称>",
                    messageIsError = true,
                )
            }
        }
    }

    /** `/mcp add` 要等异步读盘结束后再打开表单（见 [handleMcpSlash]）。 */
    private var mcpOpenFormWhenReady = false

    /** `/mcp list`：把服务器清单写进对话流（与 IQ Code 的 `showMcpSummary` 一样 */
    private fun showMcpSummary() {
        val servers = runCatching { McpStore.read().servers }.getOrDefault(emptyList())
        val body = buildString {
            if (servers.isEmpty()) {
                append("当前没有配置 MCP 服务器。输入 `/mcp` 打开配置页。")
            } else {
                servers.forEach { server ->
                    append(if (server.enabled) "● " else "○ ")
                    append("**").append(server.name).append("** · `").append(server.type.value).append("`")
                    append(" · ").append(if (server.scope == com.zhizhu.zhicode.compose.model.McpScope.PROJECT) "项目" else "用户")
                    append('\n')
                    append("  `").append(if (server.type.needsCommand) server.command else server.url).append("`")
                    append("\n\n")
                }
            }
        }
        appendInfo("MCP 服务器", body.trimEnd())
    }

    fun openMcpConfig() {
        // 见 hideSidebarForNavigation：侧栏里点进来的行必须先收起侧栏。
        hideSidebarForNavigation()
        ZhiFrameTrace.begin("page:mcp")
        _state.update {
            it.copy(
                mcpConfig = McpConfigState(servers = emptyList(), filePath = "", loading = true),
                settingsOpen = true,
                settingsDraft = it.settingsDraft ?: SettingsDraft.from(it),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { McpStore.read() }.getOrNull()
            _state.update { s ->
                // 读盘期间用户可能已经返回：载荷已置空，结果丢弃。
                val current = s.mcpConfig ?: return@update s
                var next = loaded ?: current.copy(loading = false)
                // `/mcp add`：读盘完成后直接进「添加」表单（见 handleMcpSlash）。
                if (mcpOpenFormWhenReady) {
                    mcpOpenFormWhenReady = false
                    // 新建表单：`originalName = null` 才表示"新增"（见 McpServerDraft.isEditing）。
                    next = next.copy(form = McpServerDraft(originalName = null))
                }
                s.copy(mcpConfig = next)
            }
        }
    }

    fun closeMcpConfig() {
        _state.update { it.copy(mcpConfig = null) }
        refreshExtensions()
    }

    /** `/skills` 的子命令（对齐 IQ Code 0.30.4 的 `/skills` */
    private fun handleSkillsSlash(arg: String) {
        val name = arg.trim()
        if (name.isEmpty()) {
            openSkills()
            return
        }
        // 同名的项目级与用户级可以共存：优先项目级（更贴近当前工作目录）。
        val skills = runCatching { SkillStore.list(_state.value.projectPath) }.getOrDefault(emptyList())
        val target = skills.firstOrNull { it.name.equals(name, ignoreCase = true) && it.scope == SkillScope.PROJECT }
            ?: skills.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (target == null) {
            _state.update {
                it.copy(
                    message = "未找到技能：$name（输入 /skills 看清单）",
                    messageIsError = true,
                )
            }
            return
        }
        attachSkill(target)
    }

    fun newMcpServer() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(form = McpServerDraft(originalName = null)))
    }

    /** 编辑已有服务器：需要回读 `args` / `env` / `headers` 的原文 */
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

    fun setMcpQuery(query: String) = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(query = query))
    }

    fun saveMcpServer() = runBusy("mcp.save") {
        val form = _state.value.mcpConfig?.form ?: return@runBusy
        if (!form.saveable) return@runBusy
        val result = McpStore.save(form)
        _state.update {
            it.copy(
                // 成功时表单要关掉，否则用户会停在"已保存"的表单上再点一次。
                // 关闭动作在保存成功后做，失败时**保留表单**（内容不能丢）。
                mcpConfig = McpStore.read().let { fresh ->
                    if (result.isSuccess) fresh.copy(query = it.mcpConfig?.query.orEmpty()) else fresh
                },
                message = if (result.isSuccess) "MCP 配置已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                messageIsError = result.isFailure,
            )
        }
        refreshExtensions()
    }

    /** 启用 / 停用：整行点击触发，等价于原版行尾的「启用/停用」按钮。 */
    fun toggleMcpServer(server: McpServer) = runBusy("mcp.toggle") {
        val result = McpStore.setEnabled(server.name, !server.enabled)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read().copy(query = it.mcpConfig?.query.orEmpty()),
                message = when {
                    result.isFailure -> "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                    server.enabled -> "已停用：${server.name}"
                    else -> "已启用：${server.name}"
                },
                messageIsError = result.isFailure,
            )
        }
        refreshExtensions()
    }

    fun deleteMcpServer(server: McpServer) = runBusy("mcp.delete") {
        val result = McpStore.delete(server.name)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read().copy(query = it.mcpConfig?.query.orEmpty()),
                message = if (result.isSuccess) "已删除 MCP 服务器：${server.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                messageIsError = result.isFailure,
            )
        }
        refreshExtensions()
    }

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
                                // 没在结果里出现 = 这台被停用了（引擎只列启用的）
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

    /** 「全部测试」：一次把**所有启用的**服务器都连一遍。 */
    fun testAllMcpServers() {
        val config = _state.value.mcpConfig ?: return
        if (config.servers.none { it.enabled }) {
            _state.update { it.copy(message = "没有启用中的服务器可测试", messageIsError = true) }
            return
        }
        if (config.testing.isNotEmpty()) return
        val names = config.servers.filter { it.enabled }.map { it.name }
        viewModelScope.launch {
            _state.update { state ->
                val current = state.mcpConfig ?: return@update state
                state.copy(mcpConfig = current.copy(testing = names.toSet()))
            }
            val result = withContext(Dispatchers.IO) { engine.mcpStatus() }
            _state.update { state ->
                val current = state.mcpConfig ?: return@update state
                result.fold(
                    onSuccess = { rows ->
                        val connected = rows.count { it.connected }
                        state.copy(
                            mcpConfig = current.copy(
                                testing = emptySet(),
                                status = rows.associateBy { it.name },
                            ),
                            message = "测试完成：$connected / ${rows.size} 台已连接",
                            messageIsError = connected < rows.size,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            mcpConfig = current.copy(testing = emptySet()),
                            message = "测试失败：${error.message ?: "未知原因"}",
                            messageIsError = true,
                        )
                    },
                )
            }
            refreshExtensions()
        }
    }

    /** 打开「从 JSON 导入」对话框。 */
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

    /** 执行导入。 */
    fun importMcpJson() = runBusy("mcp.import") {
        val text = _state.value.mcpConfig?.importText ?: return@runBusy
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
        if (result.isSuccess) refreshExtensions()
    }

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

    private fun configuredModelIdOrNull(label: String): String? =
        label.trim().takeIf { it.isNotEmpty() && it != UNSET_MODEL_LABEL }

    /** 把当前生效的 API 配置同步到界面摘要，并让引擎重新读取。 */
    private fun syncActiveProfile() {
        val config = ApiConfigStore.read(getApplication())
        _state.update { s ->
            val scoped = config.profiles.firstOrNull { it.id == s.modelProfileId && !s.debugAppMode }
            val profile = scoped ?: config.profiles.firstOrNull { it.id == config.activeId }
            val selectedModel = if (scoped != null) {
                val current = configuredModelIdOrNull(s.modelLabel)
                current?.takeIf { scoped.effectiveModels().isEmpty() || scoped.resolveModel(it) != null }
            } else {
                null
            }
            s.withActiveModel(profile, selectedModel, keepProfileScope = scoped != null)
        }
        if (!restoringSession) runCatching { engine.configure(engineOverrides()) }
    }

    private fun WorkspaceUiState.withActiveModel(
        profile: ApiProfile?,
        selectedModelId: String? = null,
        selectedEffort: EffortLevel = effort,
        keepProfileScope: Boolean = selectedModelId != null,
    ): WorkspaceUiState {
        val activeModelId = selectedModelId?.let(::configuredModelIdOrNull) ?: profile?.model.orEmpty()
        val activeModelCfg = profile?.resolveModel(activeModelId)
            ?: activeModelId.takeIf { it.isNotBlank() }?.let { id ->
                PiCatalogAndPresets.inferModelConfig(id, id, profile?.protocol?.toPiFormat() ?: com.zhizhu.zhicode.pi.PiApiFormat.OPENAI_COMPLETIONS)
            }
        val supported = resolveSupportedThinkingLevels(activeModelCfg).map { it.toUiEffort() }
        val clampedEffort = clampThinkingLevel(activeModelCfg, selectedEffort.toPiLevel()).toUiEffort()
        val window = activeModelCfg?.contextWindow ?: contextWindow
        val hasKey = profile != null && runCatching {
            !ApiSettingsStore(getApplication()).resolveProfile(profile.id, null)?.apiKey.isNullOrBlank()
        }.getOrDefault(false)
        return copy(
            profileName = profile?.name ?: "未配置",
            modelProfileId = profile?.id?.takeIf { keepProfileScope },
            modelLabel = activeModelId.ifBlank { UNSET_MODEL_LABEL },
            apiKeyConfigured = hasKey,
            contextWindow = window,
            availableEfforts = supported,
            effort = clampedEffort,
            settingsDraft = settingsDraft?.copy(
                contextWindow = window,
                availableEfforts = supported,
                effort = clampedEffort,
            ),
        )
    }

    private fun PiThinkingLevel.toUiEffort(): EffortLevel =
        EffortLevel.entries.firstOrNull { it.wire == id } ?: EffortLevel.HIGH

    private fun appendInfo(title: String, body: String) {
        _state.update {
            it.copy(transcript = it.transcript + ChatItem(id = nextId("i"), kind = ChatKind.INFO, title = title, body = body))
        }
    }

    /** 把 **Compose 树之外**发生的事件写进对话流。 */
    fun reportExternalEvent(title: String, body: String) = appendInfo(title, body)

    // ---------- 输入器下拉（权限 / 推理）----------

    /* 输入器页脚的权限 / 推理两个 chip 现在是**下拉菜单** */

    private suspend fun restoreRuntimeChoices() = withContext(Dispatchers.IO) {
        val o = readEngineSettings(ApiSettingsStore(getApplication())) ?: return@withContext
        val mode = engineModeToUi(o.permissionMode)
        val effort = engineEffortToUi(o.effort)
        // 搜索后端只按名字回匹配；匹配不上（比如盘上留着旧版本写下的别名）就保持界面原值，
        // 不要拿一个猜出来的值覆盖 —— 那与"设置被冲掉"是同一类伤害。
        val provider = o.webSearchProvider?.let { stored ->
            WebSearchProvider.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        }
        _state.update { s ->
            s.copy(
                permissionMode = mode,
                effort = effort,
                contextWindow = o.contextWindow ?: s.contextWindow,
                projectPath = o.projectDirectory?.takeIf { it.isNotBlank() } ?: s.projectPath,
                settings = s.settings.copy(
                    visionEnabled = o.visionEnabled ?: s.settings.visionEnabled,
                    customSystemPrompt = o.customSystemPrompt ?: s.settings.customSystemPrompt,
                    initPrompt = o.initPrompt ?: s.settings.initPrompt,
                    summaryPrompt = o.summaryPrompt ?: s.settings.summaryPrompt,
                    compactPromptHead = o.compactPromptHead ?: s.settings.compactPromptHead,
                    autoCompact = o.autoCompact ?: s.settings.autoCompact,
                    autoCompactPercent = o.autoCompactPercent ?: s.settings.autoCompactPercent,
                    webSearchEnabled = o.webSearchEnabled ?: s.settings.webSearchEnabled,
                    webSearchProvider = provider ?: s.settings.webSearchProvider,
                    webSearchMaxResults = o.webSearchMaxResults ?: s.settings.webSearchMaxResults,
                    webSearchTimeoutSec = o.webTimeoutSec ?: s.settings.webSearchTimeoutSec,
                    webSearchSearxngUrl = o.webSearchBaseUrl ?: s.settings.webSearchSearxngUrl,
                    rootExecutionEnabled = o.rootExecutionEnabled ?: s.settings.rootExecutionEnabled,
                    shizukuExecutionEnabled = o.shizukuExecutionEnabled ?: s.settings.shizukuExecutionEnabled,
                    forcedKeepAliveEnabled = o.forcedKeepAliveEnabled ?: s.settings.forcedKeepAliveEnabled,
                    maxConcurrentSubagents = o.maxConcurrentSubagents ?: s.settings.maxConcurrentSubagents,
                ),
                // 设置页若已经开着（正常不会，这一步在启动路径上），草稿也要跟着对齐，
                // 否则页面显示的是旧值、点一下又把它写回去。
                settingsDraft = s.settingsDraft?.let { draft ->
                    draft.copy(
                        permissionMode = mode,
                        effort = effort,
                        contextWindow = o.contextWindow ?: draft.contextWindow,
                        projectPath = o.projectDirectory?.takeIf { p -> p.isNotBlank() } ?: draft.projectPath,
                        customSystemPrompt = o.customSystemPrompt ?: draft.customSystemPrompt,
                        initPrompt = o.initPrompt ?: draft.initPrompt,
                        summaryPrompt = o.summaryPrompt ?: draft.summaryPrompt,
                        compactPromptHead = o.compactPromptHead ?: draft.compactPromptHead,
                        visionEnabled = o.visionEnabled ?: draft.visionEnabled,
                        autoCompact = o.autoCompact ?: draft.autoCompact,
                        autoCompactPercent = o.autoCompactPercent ?: draft.autoCompactPercent,
                        webSearchEnabled = o.webSearchEnabled ?: draft.webSearchEnabled,
                        webSearchProvider = provider ?: draft.webSearchProvider,
                        webSearchMaxResults = o.webSearchMaxResults ?: draft.webSearchMaxResults,
                        webSearchTimeoutSec = o.webTimeoutSec ?: draft.webSearchTimeoutSec,
                        webSearchSearxngUrl = o.webSearchBaseUrl ?: draft.webSearchSearxngUrl,
                        rootExecutionEnabled = o.rootExecutionEnabled ?: draft.rootExecutionEnabled,
                        shizukuExecutionEnabled = o.shizukuExecutionEnabled ?: draft.shizukuExecutionEnabled,
                        forcedKeepAliveEnabled = o.forcedKeepAliveEnabled ?: draft.forcedKeepAliveEnabled,
                        maxConcurrentSubagents = o.maxConcurrentSubagents ?: draft.maxConcurrentSubagents,
                    )
                },
            )
        }
    }

    private fun persistRuntimeChoice(mode: PermissionMode? = null, effort: EffortLevel? = null) {
        if (mode == null && effort == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val store = ApiSettingsStore(getApplication())
                val config = store.load()
                mode?.let { config.permissionMode = it.toEngineMode() }
                effort?.let { config.effort = it.toEngineEffort() }
                store.saveGlobalSettings(config, false)
            }
        }
    }

    fun setPermissionMode(mode: PermissionMode) {
        _state.update { it.copy(permissionMode = mode, message = "权限模式：${mode.label}") }
        persistRuntimeChoice(mode = mode)
        if (engine.hasEngine() && !engine.isBusy()) {
            runCatching { engine.configure(engineOverrides()) }
        }
    }

    fun setEffort(level: EffortLevel) {
        applyEffortSelection(level, closeChoicePicker = false)
    }

    private fun applyEffortSelection(level: EffortLevel, closeChoicePicker: Boolean) {
        val snapshot = _state.value
        val config = ApiConfigStore.read(getApplication())
        val profileId = snapshot.modelProfileId ?: config.activeId
        val profile = config.profiles.firstOrNull { it.id == profileId }
        val updated = _state.updateAndGet { s ->
            val currentModel = configuredModelIdOrNull(s.modelLabel) ?: profile?.model.orEmpty()
            val clamped = clampThinkingLevel(profile?.resolveModel(currentModel), level.toPiLevel()).toUiEffort()
            s.copy(
                choicePicker = if (closeChoicePicker) null else s.choicePicker,
                effort = clamped,
                settingsDraft = s.settingsDraft?.copy(effort = clamped),
                message = "推理强度：${clamped.label}",
            )
        }
        val sessionId = updated.activeSessionId.ifEmpty {
            engine.sessionId()?.takeIf { updated.composerBusy }.orEmpty()
        }
        if (profile != null && sessionId.isNotEmpty()) {
            val modelId = configuredModelIdOrNull(updated.modelLabel) ?: profile.model
            sessionModelSave = engine.saveSessionModel(sessionId, profile.id, modelId, updated.effort.toEngineEffort())
        } else if (updated.modelProfileId == null) {
            persistRuntimeChoice(effort = updated.effort)
        }
        if (engine.hasEngine() && !engine.isBusy()) {
            runCatching { engine.configure(engineOverrides()) }
        }
    }

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
        val values = _state.value.availableEfforts.ifEmpty { EffortLevel.DEFAULT_AVAILABLE }
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

    // ---------- 附加项目文件（浏览器，不是搜索） ----------

    fun openAttachPicker() {
        val root = _state.value.projectPath
        _state.update {
            it.copy(
                attachPickerOpen = true,
                attachBrowser = AttachBrowserState(root = FileRoot.HOME, path = root),
            )
        }
        reloadAttachEntries(root)
    }

    fun closeAttachPicker() = _state.update {
        it.copy(attachPickerOpen = false, attachBrowser = AttachBrowserState())
    }

    fun updateAttachFilter(filter: String) = _state.update {
        it.copy(attachBrowser = it.attachBrowser.copy(filter = filter))
    }

    /** 进入一个目录（面包屑点某一级、或点某一行的目录）。 */
    fun attachNavigateTo(path: String) {
        _state.update {
            it.copy(attachBrowser = it.attachBrowser.copy(path = path, filter = ""))
        }
        reloadAttachEntries(path)
    }

    /** 上一级。 */
    fun attachUp() {
        val browser = _state.value.attachBrowser
        val root = attachRootPath(browser.root)
        if (browser.path == root) return
        val parent = browser.path.substringBeforeLast('/', "")
        if (parent.isEmpty() || parent.length < root.length) {
            attachNavigateTo(root)
            return
        }
        attachNavigateTo(parent)
    }

    /** 切换选择器的根。切过去时回到该根的顶层（同文件面板 `switchFileRoot`）。 */
    fun attachSwitchRoot(root: FileRoot) {
        val path = attachRootPath(root)
        _state.update {
            it.copy(attachBrowser = AttachBrowserState(root = root, path = path))
        }
        reloadAttachEntries(path)
    }

    /** 某个 [FileRoot] 的顶层路径。与文件面板的 `rootPath()` 同一套对应关系。 */
    private fun attachRootPath(root: FileRoot): String = when (root) {
        FileRoot.HOME -> TermuxConstants.TERMUX_HOME_DIR_PATH
        FileRoot.SHARED -> StorageLinks.EXTERNAL_ROOT
    }

    private fun reloadAttachEntries(path: String) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { FileBrowser.children(path) }
            // 空态要说清**为什么**空：与文件面板 reloadFiles 同一套判据，
            // 否则权限不足看起来和"这个目录是空的"一模一样。
            val dir = java.io.File(path)
            val note = when {
                entries.isNotEmpty() -> ""
                !dir.isDirectory -> if (dir.exists()) "不是目录：$path" else "目录不存在：$path"
                dir.list() == null -> "无法读取（权限不足）：$path"
                else -> ""
            }
            _state.update { s ->
                if (s.attachBrowser.path != path) return@update s
                s.copy(attachBrowser = s.attachBrowser.copy(entries = entries, note = note))
            }
        }
    }

    fun attachProjectFile(path: String) {
        viewModelScope.launch {
            val name = File(path).name
            if (!attachPath(path, name)) {
                _state.update { it.copy(message = "附加失败：$name 不是文本文件或读不了", messageIsError = true) }
            }
        }
    }

    /** 真正把一条文本附件放进状态。 */
    private suspend fun attachPath(path: String, label: String, detail: String? = null): Boolean {
        val body = withContext(Dispatchers.IO) { FileBrowser.readTextForAttachment(path) } ?: return false
        val size = withContext(Dispatchers.IO) { runCatching { File(path).length() }.getOrDefault(0L) }
        // 见 attachProjectFile 的 KDoc：项目根之下的给相对路径，否则给绝对路径。
        // SAF 那条路没有文件系统路径可算，由调用方直接把 detail 给进来。
        val key = detail ?: run {
            val projectRoot = _state.value.projectPath.trimEnd('/')
            if (projectRoot.isNotEmpty() && path.startsWith("$projectRoot/")) path.removePrefix("$projectRoot/") else path
        }
        _state.update { s ->
            // 同一路径只留一条（用 key 去重，它在项目内唯一）：
            // 连着点两次应该还是那一份，而不是叠两份进提示词。
            val kept = s.attachments.filterNot { it.detail == key }
            s.copy(
                attachments = kept + Attachment(
                    id = nextId("file"),
                    label = label,
                    detail = key,
                    isImage = false,
                    textBody = body,
                    sizeLabel = FileFormat.size(size),
                ),
                // 只说**几个**，不报体积。
                message = "已附加 ${kept.size + 1} 个文件",
                messageIsError = false,
            )
        }
        return true
    }

    fun attachDocument(uri: android.net.Uri) {
        viewModelScope.launch {
            val context = getApplication<android.app.Application>()
            val label = withContext(Dispatchers.IO) { documentDisplayName(context, uri) } ?: "文件"
            // 先拿到**字节**（而不是直接解成字符串）：长度要报给用户
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // 与 FileBrowser 同一套上限：256 KB 截断 + NUL 判定二进制。
                        // 各写一份的话，"附加一个 500 MB 的视频"迟早会把内存吃光。
                        readBounded(input, DocumentAttachLimit)
                    }
                }.getOrNull()
            }
            if (bytes == null || (bytes.isNotEmpty() && bytes.any { it == 0.toByte() })) {
                _state.update { it.copy(message = "附加失败：$label 不是文本文件或读不了", messageIsError = true) }
                return@launch
            }
            val body = String(bytes, Charsets.UTF_8)
            _state.update { s ->
                val kept = s.attachments.filterNot { it.detail == uri.toString() }
                s.copy(
                    attachments = kept + Attachment(
                        id = nextId("doc"),
                        label = label,
                        detail = uri.toString(),
                        isImage = false,
                        textBody = body,
                        sizeLabel = FileFormat.size(bytes.size.toLong()),
                    ),
                    message = "已附加 ${kept.size + 1} 个文件",
                    messageIsError = false,
                )
            }
        }
    }

    /** SAF 文档的显示名。取不到就返回 null，由调用方给个兜底标签。 */
    private fun documentDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 读满上限就停（与 `FileBrowser` 的 256 KB 同一口径）。 */
    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(64 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    /** 打开模型选择面板。 */
    private fun ApiProfile.toPickerTab(): ModelProfileTab = ModelProfileTab(
        id = id,
        name = name,
        protocolLabel = protocol.label,
        modelLabel = model.ifBlank { effectiveModels().firstOrNull()?.id ?: "未设置模型" },
        models = effectiveModels(),
    )

    /** 打开纯供应商切换面板（纯本地读取，不拉取在线模型目录）。 */
    fun showModelPicker() {
        val config = ApiConfigStore.read(getApplication())
        _state.update { s ->
            val activeId = s.modelProfileId ?: config.activeId
            s.copy(
                modelPicker = ModelPickerState(
                    profileName = s.profileName,
                    currentModel = s.modelLabel,
                    query = "",
                    loading = false,
                    status = "",
                    models = emptyList(),
                    profiles = config.profiles.map { it.toPickerTab() },
                    activeProfileId = activeId,
                ),
            )
        }
    }

    fun closeModelPicker() {
        modelCatalogJob?.cancel()
        modelCatalogJob = null
        _state.update { it.copy(modelPicker = null) }
    }

    /** 切换当前会话的供应商。 */
    fun selectModelPickerProfile(profileId: String) {
        if (_state.value.transcriptLoading) return
        val profile = ApiConfigStore.read(getApplication()).profiles.firstOrNull { it.id == profileId } ?: return
        closeModelPicker()
        selectSessionModel(profile, profile.model, _state.value.effort)
        _state.update { it.copy(message = "已切换供应商：${profile.name}", messageIsError = false) }
    }

    /** 打开本地模型与思考强度两级选择菜单（完全本地配置，不发起网络请求）。 */
    fun openModelThinkingPicker() {
        val config = ApiConfigStore.read(getApplication())
        _state.update { s ->
            val active = config.profiles.firstOrNull { it.id == (s.modelProfileId ?: config.activeId) }
            s.copy(
                modelThinkingPicker = ModelThinkingPickerState(
                    providerId = active?.id.orEmpty(),
                    providerName = active?.name ?: s.profileName,
                    models = active?.effectiveModels().orEmpty(),
                    currentModelId = s.modelLabel,
                    currentEffort = s.effort,
                    expandedModel = null,
                ),
            )
        }
    }

    fun closeModelThinkingPicker() {
        _state.update { it.copy(modelThinkingPicker = null) }
    }

    fun enterModelThinkingSubmenu(model: PiModelConfig) {
        _state.update { s ->
            s.copy(modelThinkingPicker = s.modelThinkingPicker?.copy(expandedModel = model))
        }
    }

    fun backToModelThinkingList() {
        _state.update { s ->
            s.copy(modelThinkingPicker = s.modelThinkingPicker?.copy(expandedModel = null))
        }
    }

    fun selectModelAndEffort(modelId: String, effort: EffortLevel) {
        val snapshot = _state.value
        val target = modelId.trim()
        if (target.isEmpty() || snapshot.transcriptLoading) return
        val config = ApiConfigStore.read(getApplication())
        val profileId = snapshot.modelProfileId ?: config.activeId
        val profile = config.profiles.firstOrNull { it.id == profileId } ?: return
        if (profile.resolveModel(target) == null) return
        closeModelThinkingPicker()
        selectSessionModel(profile, target, effort)
        _state.update { it.copy(message = "已切换：$target · ${it.effort.label}", messageIsError = false) }
    }

    private fun selectSessionModel(profile: ApiProfile, modelId: String, effort: EffortLevel) {
        val state = _state.updateAndGet {
            it.withActiveModel(profile, modelId, effort, keepProfileScope = true)
        }
        val sessionId = state.activeSessionId.ifEmpty {
            engine.sessionId()?.takeIf { state.composerBusy }.orEmpty()
        }
        if (sessionId.isNotEmpty()) {
            sessionModelSave = engine.saveSessionModel(sessionId, profile.id, modelId, state.effort.toEngineEffort())
        }
    }

    fun showMessageActions(item: ChatItem) {
        val options = when (item.kind) {
            // 用户消息：删除是明确需求（发错、打错又不想改）。
            ChatKind.USER -> listOf("复制", "自由复制", "再次发送", "编辑后发送", "导出", "删除")
            // 助手消息：补上「导出」与「删除」。
            ChatKind.ASSISTANT, ChatKind.ERROR ->
                listOf("复制", "自由复制", "重新生成", "导出", "删除")
            else -> listOf("复制概要", "自由复制")
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

    fun applyToolAction(itemId: String, toolId: String, label: String) {
        val target = _state.value.transcript.firstOrNull { it.id == itemId }
        if (target == null) {
            _state.update { it.copy(message = "找不到目标工具，操作已取消") }
            return
        }
        val tool = target.tools.firstOrNull { it.id == toolId } ?: run {
            _state.update { it.copy(message = "找不到这个工具，操作已取消") }
            return
        }

        if (ToolActions.isToggle(label)) {
            setToolExpanded(target.id, toolId, ToolActions.toggledTo(label))
            return
        }

        when (ToolActions.copySource(label)) {
            ToolActions.CopySource.COMMAND -> copyText(tool.summary, "工具命令")
            ToolActions.CopySource.OUTPUT -> copyText(tool.output, "工具输出")
            ToolActions.CopySource.LIVE_OUTPUT ->
                // 运行中的实时输出就是 `output` 此刻的内容（进度回调持续写进去的）。
                copyText(tool.output, "工具实时输出")
            // diff 是输出里的一段：整份输出已经是 diff 文本，单独给"复制 Diff"
            // 是为了让用户不必先展开、也不必在长文本里自己挑。
            ToolActions.CopySource.DIFF -> copyText(tool.output, "工具 Diff")
            ToolActions.CopySource.INPUT -> copyText(toolActionInput(target, toolId), "工具参数")
            null -> _state.update { it.copy(message = "不支持的操作：$label") }
        }
    }

    /** "复制参数"要复制的东西。 */
    private fun toolActionInput(item: ChatItem, toolId: String): String {
        val tool = item.tools.firstOrNull { it.id == toolId }
        if (tool == null) return ""
        return listOfNotNull(
            tool.displayName.takeIf { it.isNotBlank() },
            tool.summary.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    // ---------- 剪贴板 ----------

    /** 复制一条消息的可见文本。 */
    fun copyMessage(item: ChatItem) {
        copyText(item.copyText(), "ZhiCode 消息")
    }

    /** 一条消息对用户**可见的那份文本**。 */
    private fun ChatItem.copyText(): String = when (kind) {
        ChatKind.ASSISTANT, ChatKind.ERROR -> body.ifBlank { thinking }
        ChatKind.TOOL_GROUP -> tools.joinToString("\n") { tool ->
            listOfNotNull(
                tool.displayName.takeIf { it.isNotBlank() },
                tool.summary.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        else -> body
    }

    /** 打开「自由复制」抽屉：把这条消息的原文摊开，让用户自己划词取一段。 */
    fun openFreeCopy(item: ChatItem) {
        val text = item.copyText()
        if (text.isBlank()) {
            _state.update { it.copy(message = "这条消息没有可复制的文本", messageIsError = true) }
            return
        }
        val label = when (item.kind) {
            ChatKind.USER -> "你的消息"
            ChatKind.ASSISTANT -> "ZhiCode 回复"
            ChatKind.ERROR -> "错误信息"
            ChatKind.TOOL_GROUP -> "工具调用摘要"
            else -> "消息"
        }
        _state.update { it.copy(copySheet = CopySheetState(title = label, text = text)) }
    }

    fun closeCopySheet() {
        _state.update { it.copy(copySheet = null) }
    }

    /** 抽屉里的「复制全部」：复制的是抽屉里摊开的那一份，不是重新再算一遍。 */
    fun copySheetAll() {
        val sheet = _state.value.copySheet ?: return
        copyText(sheet.text, sheet.title)
        closeCopySheet()
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
        copyText(latest.body, "ZhiCode 回复")
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
        val options = listOf("重命名", "删除会话")
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
        // 二级弹窗也要记得操作对象：SESSION_ACTION 分派时已经把它清空了，
        // 不补回去的话 onSubmitFreeForm 拿到 null，「备注未保存」是必现结果。
        pendingSessionAction = session
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

    /** 打开重命名：同样只靠自由输入提交，输入框预填当前标题。 */
    private fun editSessionTitle(session: SessionSummary) {
        pendingSessionAction = session
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "重命名会话",
                    intent = ChoiceIntent.SESSION_RENAME,
                    prompt = "改一个你认得出的名字；只影响侧栏显示，不改会话内容本身。",
                    options = emptyList(),
                    allowFreeForm = true,
                    freeFormHint = session.title,
                ),
            )
        }
    }

    /** 写入备注到存储。空字符串表示清除。 */
    private fun saveSessionNote(session: SessionSummary, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // titleOverride 传空串 = 不覆盖标题，只改备注。
            val ok = SessionReader.updateMetadata(session.id, note, "")
            val sessions = SessionReader.listAll()
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

    /** 写入标题覆盖到存储，备注原样保留。 */
    private fun saveSessionTitle(session: SessionSummary, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SessionReader.updateMetadata(session.id, session.note, title)
            val sessions = SessionReader.listAll()
            _state.update {
                it.copy(
                    sessions = sessions,
                    message = if (ok) "标题已更新" else "标题保存失败",
                )
            }
        }
    }

    /** 用户主动关闭当前选择器（「取消」按钮 / 点空白 / 返回键）。 */
    fun closeChoicePicker() {
        if (_state.value.choicePicker?.intent == ChoiceIntent.QUESTION) {
            cancelQuestions()
            return
        }
        pendingSessionAction = null
        _state.update { it.copy(choicePicker = null) }
    }

    fun dismissChoicePicker() {
        // 提问流程的「取消」必须把空答案回执给引擎，否则引擎会一直等
        // （`QuestionGate.ask` 的闩锁要等 30 分钟才超时）。
        if (_state.value.choicePicker?.intent == ChoiceIntent.QUESTION) {
            cancelQuestions()
            return
        }
        val current = _state.value.choicePicker?.intent
        if (current == ChoiceIntent.SESSION_RENAME ||
            current == ChoiceIntent.SESSION_DELETE_CONFIRM ||
            current == ChoiceIntent.MESSAGE_DELETE_CONFIRM
        ) {
            return
        }
        // 同上一条：`beginMultiSelect` 已经主动关了菜单并进入多选
        if (_state.value.multiSelectMode && current == null) {
            return
        }
        _state.update { it.copy(choicePicker = null) }
    }

    /** 提交选择器里选中的项。 */
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
                if (mode != null) {
                    _state.update {
                        it.copy(choicePicker = null, permissionMode = mode, message = "权限模式：${mode.label}")
                    }
                    // 斜杠命令这条路与输入器下拉是**同一件事**，落盘也不能只做一边
                    persistRuntimeChoice(mode = mode)
                } else _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.EFFORT -> {
                val available = _state.value.availableEfforts.ifEmpty { EffortLevel.DEFAULT_AVAILABLE }
                val level = available.getOrNull(index)
                if (level != null) {
                    applyEffortSelection(level, closeChoicePicker = true)
                } else {
                    _state.update { it.copy(choicePicker = null) }
                }
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
                    // 「复制概要」而不是「复制」：工具组没有正文，复制的是各工具的
                    "复制概要" -> copyMessage(target)
                    "复制" -> copyMessage(target)
                    // 抽屉里摊开原文，交给自己划词。
                    "自由复制" -> openFreeCopy(target)
                    "再次发送" -> {
                        // 复用用户消息的原文重发；工具组这类没有正文的目标不支持。
                        if (target.body.isBlank()) _state.update { it.copy(message = "这条消息没有可重发的内容", messageIsError = true) }
                        else {
                            _state.update { it.copy(composerText = target.body, composerTextEpoch = it.composerTextEpoch + 1) }
                            send()
                        }
                    }
                    "编辑后发送" -> _state.update { it.copy(composerText = target.body, composerTextEpoch = it.composerTextEpoch + 1, message = "已放回输入框，可编辑后发送") }
                    "重试上一问", "重新生成" -> retryLastUserPrompt(target)
                    "分享", "导出" -> shareTranscriptMessage(target)
                    "删除" -> confirmDeleteTranscriptMessage(target)
                    else -> _state.update { it.copy(message = "不支持的操作：${option.label}") }
                }
            }
            // 单个工具的动作**不经过这里**：那一行的 `⋯` 是 Miuix 下拉菜单
            ChoiceIntent.SESSION_ACTION -> {
                val target = pendingSessionAction
                pendingSessionAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到操作对象就什么都不做。宁可无反应，也不能猜一条会话删掉。
                    _state.update { it.copy(message = "找不到目标会话，操作已取消") }
                } else when (option.label) {
                    "重命名" -> editSessionTitle(target)
                    // ⚠️ 不再直接删：先弹二次确认（用户要求「删除会话也要加个 dialog」）。
                    // 原来这一行是 `deleteSession(target.id)`，误触即不可恢复。
                    "删除会话" -> {
                        confirmDeleteSession(target)
                    }
                    // 「恢复会话」已经不在菜单里（点这一行就会打开）。这一支留着是为了
                    // 兼容**旧的**待办：选择器是异步的，理论上仍可能带着老选项回来。
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
            ChoiceIntent.SESSION_RENAME -> {
                // 同上：重命名也只靠自由输入提交，这里只是穷尽性兜底。
                _state.update { it.copy(choicePicker = null) }
            }
            // 删除确认的同理兜底：真正的执行在 `onSubmit`（选中第二层唯一的选项）。
            // 保留这个分支是因为 `when` 必须穷尽，且将来若改成按钮式确认会用到。
            ChoiceIntent.SESSION_DELETE_CONFIRM -> {
                val target = pendingSessionAction
                pendingSessionAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target != null) doDeleteSession(target.id)
            }
            ChoiceIntent.QUESTION -> {
                // 提问流程正常走 onSubmitSelection / dismissChoicePicker；
                // 单选回调真落到这里时按「跳过本步」处理，不要静默什么都不做。
                advanceQuestion(null)
            }
            /* 删除某条历史消息的确认。 */
            ChoiceIntent.MESSAGE_DELETE_CONFIRM -> {
                val targetId = picker.anchorId
                _state.update { it.copy(choicePicker = null) }
                val target = _state.value.transcript.firstOrNull { it.id == targetId }
                if (target == null) {
                    _state.update { it.copy(message = "这条消息已经不在对话里了") }
                } else {
                    deleteTranscriptMessage(target)
                }
            }
            // 通用选择器：没有任何附加语义，选中只意味着"用户做了选择"。
            ChoiceIntent.GENERIC -> _state.update {
                it.copy(choicePicker = null, message = "已选择：${option.label}")
            }
            // 计划模式下的「目标澄清」：用户选定的目标本身就是一条提示词。
            // 计划正文由引擎产出（不再由界面拼），所以这里只是把它发出去。
            ChoiceIntent.PLAN_GOAL -> {
                val goal = if (option.detail.isNotEmpty()) "${option.label} —— ${option.detail}" else option.label
                _state.update { it.copy(choicePicker = null, composerText = goal, composerTextEpoch = it.composerTextEpoch + 1) }
                send()
            }
        }
    }

    /** 选择窗口里的「其他回答…」提交：不走选项，直接把这段自由文本当作输入。 */
    fun onSubmitFreeForm(text: String) {
        val trimmed = text.trim()
        val picker = _state.value.choicePicker
        if (picker == null) return
        // 备注可以提交空字符串（= 清除），所以不能和"空输入就关闭"混在一起；
        // 重命名反过来：空标题无效，直接提示而不是当作"恢复原名"。
        if (picker.intent == ChoiceIntent.SESSION_NOTE || picker.intent == ChoiceIntent.SESSION_RENAME) {
            val rename = picker.intent == ChoiceIntent.SESSION_RENAME
            val target = pendingSessionAction
            pendingSessionAction = null
            _state.update { it.copy(choicePicker = null) }
            when {
                target == null ->
                    _state.update { it.copy(message = if (rename) "找不到目标会话，标题未保存" else "找不到目标会话，备注未保存") }
                rename && trimmed.isEmpty() ->
                    _state.update { it.copy(message = "标题不能为空") }
                rename -> saveSessionTitle(target, trimmed)
                else -> saveSessionNote(target, trimmed)
            }
            return
        }
        if (trimmed.isEmpty()) {
            dismissChoicePicker()
            return
        }
        if (picker.intent == ChoiceIntent.PLAN_GOAL) {
            _state.update { it.copy(choicePicker = null, composerText = trimmed, composerTextEpoch = it.composerTextEpoch + 1) }
            send()
            return
        }
        _state.update {
            it.copy(
                choicePicker = null,
                composerText = trimmed,
                composerTextEpoch = it.composerTextEpoch + 1,
                slashQuery = null,
                slashMatches = emptyList(),
                message = null,
            )
        }
    }

    // ---------- 会话 ----------

    /** 新建会话。 */
    fun newSession() {
        openSessionSeq++
        restoringSession = false
        engine.resetConversation()
        discardStreamingAssistant()
        // 上一轮即使还在跑也已经不属于任何会话了，清掉归属标记避免误拦。
        turnSessionId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null
        // 换了会话，上一轮的停止流程不再属于任何东西：留着它会让新会话
        // 一打开就显示"正在停止"，而且看门狗还会去问一个已经作废的回合。
        resetStopTracking()
        val defaultProfile = ApiConfigStore.activeProfile(getApplication())
        val defaultEffort = engineEffortToUi(readEngineSettings(ApiSettingsStore(getApplication()))?.effort)
        _state.update {
            it.withActiveModel(defaultProfile, selectedEffort = defaultEffort).copy(
                // 占位条目的 id 用空串而不是伪造 id：它不是真实文件，
                // 若被当成路径去打开会失败，空串能让"打不开"这件事显式化。
                activeSessionId = "",
                stopping = false,
                transcript = emptyList(),
                transcriptLoading = false,
                transcriptHasOlder = false,
                transcriptOldestSeq = -1L,
                transcriptLoadingOlder = false,
                transcriptPrependRevision = 0,
                transcriptHasNewer = false,
                transcriptJumpRevision = 0,
                tasks = emptyList(),
                contextTokens = 0,
                composerBusy = false,
                workingStatus = null,
                tab = WorkspaceTab.CHAT,
                sidebarOpen = false,
                modelPicker = null,
                modelThinkingPicker = null,
                message = "已开始新会话，第一条消息会创建会话记录",
            )
        }
    }

    /** 打开一条历史会话。 */
    fun openSession(session: SessionSummary) {
        viewModelScope.launch { restoreSession(session) }
    }

    private suspend fun restoreSession(session: SessionSummary) {
        if (session.id.isEmpty()) {
            _state.update { it.copy(message = "会话标识为空，无法打开", messageIsError = true) }
            refreshSessions()
            return
        }
        // 切会话必须先把引擎这一轮停掉：否则旧会话的回调还会继续往新的
        // transcript 里写（引擎的 cancel 会连带取消三个门控）。
        engine.cancel()
        discardStreamingAssistant()
        // 切走会话：上一轮的迟到事件一律不应再落到新会话里。
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null
        // 见 newSession()：换会话时停止流程一并作废。
        resetStopTracking()
        val seq = ++openSessionSeq
        restoringSession = true

        val targetPath = session.projectPath.takeIf { it.isNotBlank() }?.let { canonicalWorkspacePath(it) }
        val switchingProject = targetPath != null && targetPath != canonicalWorkspacePath(_state.value.projectPath)
        if (switchingProject && targetPath != null) {
            switchProjectPathInternal(targetPath)
        }

        _state.update {
            it.copy(
                sidebarOpen = false,
                tab = WorkspaceTab.CHAT,
                workingStatus = null,
                transcriptLoading = true,
                transcript = emptyList(),
                contextTokens = 0,
                transcriptHasOlder = false,
                transcriptHasNewer = false,
                transcriptLoadingOlder = false,
                modelPicker = null,
                modelThinkingPicker = null,
            )
        }
        coroutineScope {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val pageDeferred = async(Dispatchers.IO) { runCatching { SessionReader.openPage(session.id) } }
            val modelDeferred = async(Dispatchers.IO) {
                sessionModelSave?.join()
                val binding = SessionStore.loadProfileBinding(session.id)
                val history = engine.sessionHistory(session.id)
                val config = ApiConfigStore.read(getApplication())
                val usedKeys = HashSet<String>()
                val historyProfile = config.profiles.withIndex().firstOrNull { (index, profile) ->
                    val key = PiConfigSync.allocateUniqueKey(profile.providerKey.ifBlank { profile.name }, "provider-${index + 1}", usedKeys)
                    key == history.providerId
                }?.value
                val boundProfile = config.profiles.firstOrNull { it.id == binding?.profileId }
                val savedChoice = boundProfile != null && !binding?.effort.isNullOrBlank()
                val profile = if (savedChoice) boundProfile else historyProfile ?: boundProfile
                val model = when {
                    savedChoice -> binding?.model
                    historyProfile != null -> history.modelId
                    else -> binding?.model
                }?.takeIf { it.isNotBlank() }
                val effort = binding?.effort?.takeIf { savedChoice }
                    ?: history.thinkingLevel.takeIf { historyProfile != null && it.isNotBlank() }
                    ?: readEngineSettings(ApiSettingsStore(getApplication()))?.effort
                val fallback = config.profiles.firstOrNull { it.id == config.activeId }
                val modelState = _state.value.withActiveModel(
                    profile = profile ?: fallback,
                    selectedModelId = model.takeIf { profile != null },
                    selectedEffort = engineEffortToUi(effort),
                    keepProfileScope = profile != null,
                )
                modelState to history.contextTokens
            }
            val page = pageDeferred.await().getOrElse { error ->
                modelDeferred.cancel()
                if (seq != openSessionSeq) return@coroutineScope
                restoringSession = false
                _state.update {
                    it.copy(
                        transcriptLoading = false,
                        workingStatus = null,
                        message = "会话载入失败：" + (error.message ?: "未知原因"),
                        messageIsError = true,
                    )
                }
                return@coroutineScope
            }
            ZhiMemory.log(
                "openPage session=" + session.id +
                    " n=" + page.items.size +
                    " hasOlder=" + page.hasMore +
                    " ms=" + (android.os.SystemClock.elapsedRealtime() - startedAt),
            )
            val (modelState, fastTokens) = modelDeferred.await()
            if (seq != openSessionSeq) return@coroutineScope
            // SQLite 本地分页先到先展示，不阻塞用户阅读历史对话等待 PRoot / Pi 冷启动握手。
            _state.update {
                it.copy(
                    activeSessionId = session.id,
                    profileName = modelState.profileName,
                    modelProfileId = modelState.modelProfileId,
                    modelLabel = modelState.modelLabel,
                    apiKeyConfigured = modelState.apiKeyConfigured,
                    effort = modelState.effort,
                    availableEfforts = modelState.availableEfforts,
                    contextWindow = modelState.contextWindow,
                    settingsDraft = it.settingsDraft?.copy(
                        effort = modelState.effort,
                        availableEfforts = modelState.availableEfforts,
                        contextWindow = modelState.contextWindow,
                    ),
                    transcript = page.items,
                    transcriptHasOlder = page.hasMore,
                    transcriptOldestSeq = page.oldestSeq,
                    transcriptLoadingOlder = false,
                    transcriptPrependRevision = 0,
                    transcriptHasNewer = false,
                    transcriptJumpRevision = 0,
                    tasks = page.tasks,
                    contextTokens = fastTokens,
                    stopping = false,
                    composerBusy = false,
                    workingStatus = null,
                    transcriptLoading = false,
                )
            }
            val resumed = engine.resumeConversation(session.id, engineOverrides()).await()
            if (seq != openSessionSeq) return@coroutineScope
            restoringSession = false
            val footprint = engine.memoryFootprint()
            ZhiMemory.log(
                "resumeConversation session=" + session.id +
                    " engineMsgs=" + footprint.first +
                    " historyChars=" + ZhiMemory.size(footprint.second) +
                    " ms=" + (android.os.SystemClock.elapsedRealtime() - startedAt),
            )
            val measuredTokens = engine.estimateContextTokens()
            val finalTokens = if (measuredTokens > 0) measuredTokens else fastTokens
            _state.update {
                it.copy(
                    contextTokens = finalTokens,
                    message = resumed.exceptionOrNull()?.let { error ->
                        "会话历史已显示，但引擎恢复失败：" + (error.message ?: "未知原因")
                    },
                )
            }
        }
    }

    /** 往前补一页更早的历史（列表滚到顶时调用）。 */
    fun loadOlderTranscript() {
        val snapshot = _state.value
        val sessionId = snapshot.activeSessionId
        if (sessionId.isEmpty() || snapshot.transcriptLoadingOlder || !snapshot.transcriptHasOlder) return
        val before = snapshot.transcriptOldestSeq
        if (before <= 0L) {
            _state.update { it.copy(transcriptHasOlder = false) }
            return
        }
        _state.update { it.copy(transcriptLoadingOlder = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val page = SessionReader.olderPage(sessionId, before)
            _state.update { current ->
                // 读的过程中可能已经切走会话（或者又删了它）：那就把这一页丢掉，
                // 绝不能把上一个会话的历史接到新会话头上。
                if (current.activeSessionId != sessionId) {
                    current.copy(transcriptLoadingOlder = false)
                } else if (page == null || page.items.isEmpty()) {
                    current.copy(transcriptLoadingOlder = false, transcriptHasOlder = false)
                } else {
                    val merged = SessionReader.merge(page.items, current.transcript)
                    // 补页之后**必须**从尾部淘汰（见 trimResidentTail 的说明）：
                    // 只加不减的话，用户往上翻完整条会话就等于把整条会话又装回内存。
                    val kept = trimResidentTail(merged, current)
                    ZhiMemory.log(
                        "olderPage n=" + page.items.size +
                            " held=" + kept.items.size +
                            " trimmed=" + kept.trimmed +
                            " hasOlder=" + page.hasMore,
                    )
                    current.copy(
                        transcript = kept.items,
                        transcriptHasNewer = current.transcriptHasNewer || kept.trimmed,
                        transcriptHasOlder = page.hasMore,
                        transcriptOldestSeq = page.oldestSeq,
                        transcriptLoadingOlder = false,
                        // 让列表知道这次变长是**往前补**的（见 UiModels 里的说明）。
                        transcriptPrependRevision = current.transcriptPrependRevision + 1,
                        // 前插了几条：列表据此把视口还原到原来那条，做到"不动"。
                        transcriptPrependCount = page.items.size,
                    )
                }
            }
        }
    }

    /** 回到最新：用**最新一页**替换常驻窗口，并把列表落到底。 */
    fun jumpToLatest() {
        val sessionId = _state.value.activeSessionId
        if (sessionId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val page = SessionReader.openPage(sessionId)
            ZhiMemory.log("jumpToLatest session=" + sessionId + " n=" + page.items.size)
            _state.update { current ->
                if (current.activeSessionId != sessionId) current
                else current.copy(
                    transcript = page.items + current.transcript.filterNot { it.id.startsWith("session-") },
                    transcriptHasOlder = page.hasMore,
                    transcriptHasNewer = false,
                    transcriptOldestSeq = page.oldestSeq,
                    transcriptLoadingOlder = false,
                    // 计一次"往前补"，让列表**不要**为这次替换吸底 
                    transcriptPrependRevision = current.transcriptPrependRevision + 1,
                    transcriptJumpRevision = current.transcriptJumpRevision + 1,
                    tasks = page.tasks,
                )
            }
        }
    }

    /** [trimResidentTail] 的结果。 */
    private class Trimmed(val items: List<ChatItem>, val trimmed: Boolean)

    /** 常驻条数的上限。 */
    private val maxResidentItems = 600

    private fun trimResidentTail(items: List<ChatItem>, state: WorkspaceUiState): Trimmed {
        if (items.size <= maxResidentItems) return Trimmed(items, false)
        val busy = state.workingStatus != null || state.composerBusy ||
            state.permissionRequest != null || state.planApproval != null ||
            state.choicePicker != null || pendingPermissionId != null
        if (busy) return Trimmed(items, false)
        return Trimmed(ArrayList(items.subList(0, maxResidentItems)), true)
    }

    /** 弹出「删除会话」的**二次确认**。 */
    private fun confirmDeleteSession(session: SessionSummary) {
        pendingSessionAction = session
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "删除会话",
                    intent = ChoiceIntent.SESSION_DELETE_CONFIRM,
                    prompt = "「${session.title}」会被永久删除，包含 ${session.messageCount} 条消息。" +
                        "这个操作无法撤销。",
                    options = listOf(ChoiceOption("确认删除")),
                ),
            )
        }
    }

    private fun doDeleteSession(id: String) = deleteSession(id)

    /** 删除会话。 */
    fun deleteSession(id: String) {
        if (id.isEmpty()) {
            // 尚未落库的占位会话：直接清掉当前视图即可。
            _state.update {
                it.copy(
                    activeSessionId = "",
                    transcript = emptyList(),
                    transcriptHasOlder = false,
                    transcriptOldestSeq = -1L,
                    transcriptLoadingOlder = false,
                    transcriptPrependRevision = 0,
                    transcriptHasNewer = false,
                    transcriptJumpRevision = 0,
                    tasks = emptyList(),
                    message = "会话已清空",
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val removed = SessionReader.delete(id)
            val wasActive = _state.value.activeSessionId == id
            if (wasActive) {
                engine.resetConversation()
                discardStreamingAssistant()
            }
            val sessions = SessionReader.listAll()
            val next = sessions.firstOrNull()
            _state.update {
                it.copy(
                    sessions = sessions,
                    activeSessionId = if (wasActive) "" else it.activeSessionId,
                    transcript = if (wasActive) emptyList() else it.transcript,
                    transcriptHasOlder = if (wasActive) false else it.transcriptHasOlder,
                    transcriptOldestSeq = if (wasActive) -1L else it.transcriptOldestSeq,
                    transcriptLoadingOlder = if (wasActive) false else it.transcriptLoadingOlder,
                    transcriptPrependRevision = if (wasActive) 0 else it.transcriptPrependRevision,
                    transcriptHasNewer = if (wasActive) false else it.transcriptHasNewer,
                    transcriptJumpRevision = if (wasActive) 0 else it.transcriptJumpRevision,
                    tasks = if (wasActive) emptyList() else it.tasks,
                    message = if (removed) "会话已删除" else "删除失败：会话可能已被移除",
                )
            }
            // 删除后仍留历史时，顺手把最近一条打开，避免出现"删完一片空白"。
            if (wasActive && next != null) openSession(next)
        }
    }

    private fun switchProjectPathInternal(cleanPath: String) {
        val baseDraft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        applySettingsDraft(baseDraft.copy(projectPath = cleanPath))
        workspaceStore.saveLastActivePath(cleanPath)
        if (!restoringSession) runCatching { engine.configure(engineOverrides()) }
    }

    /** 在侧栏会话管理中切换项目目录并确保其已注册为一个工作区。 */
    fun updateProjectDirectory(rawPath: String) {
        val clean = canonicalWorkspacePath(rawPath)
        if (clean.isEmpty()) return
        addWorkspace(clean, WorkspacePaths.nameOf(clean), switchToIt = true)
    }

    /** 添加并持久化一个工作区目录。 */
    fun addWorkspace(rawPath: String, customLabel: String = "", switchToIt: Boolean = true) {
        val clean = canonicalWorkspacePath(rawPath)
        if (clean.isEmpty()) return
        WorkspacePaths.ensure(clean)
        val defaultPath = canonicalWorkspacePath(WorkspacePaths.defaultProject())
        val label = customLabel.trim().ifEmpty {
            if (clean == defaultPath) "默认工作区" else WorkspacePaths.nameOf(clean)
        }
        val current = _state.value.workspaces
        val exists = current.any { canonicalWorkspacePath(it.path) == clean }
        val updated = if (exists) {
            current.map {
                if (canonicalWorkspacePath(it.path) == clean) it.copy(expanded = true, label = if (customLabel.isNotBlank()) label else it.label)
                else it
            }
        } else {
            val fresh = WorkspaceEntry(
                id = clean,
                path = clean,
                label = label,
                isDefault = clean == defaultPath,
                expanded = true,
                createdAt = System.currentTimeMillis(),
            )
            listOf(fresh) + current
        }
        workspaceStore.saveWorkspaces(updated)
        _state.update { it.copy(workspaces = updated) }
        if (switchToIt) {
            switchProjectPathInternal(clean)
            _state.update { it.copy(message = "已切换工作区：$label", messageIsError = false) }
        }
        refreshSessions()
    }

    fun renameWorkspace(path: String, newLabel: String) {
        val clean = canonicalWorkspacePath(path)
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        val updated = _state.value.workspaces.map {
            if (canonicalWorkspacePath(it.path) == clean && !it.isDefault) it.copy(label = trimmed) else it
        }
        workspaceStore.saveWorkspaces(updated)
        _state.update { it.copy(workspaces = updated, message = "已重命名工作区：$trimmed", messageIsError = false) }
    }

    fun removeWorkspace(path: String) {
        val clean = canonicalWorkspacePath(path)
        val updated = _state.value.workspaces.filterNot {
            canonicalWorkspacePath(it.path) == clean && !it.isDefault
        }
        workspaceStore.saveWorkspaces(updated)
        val defaultPath = canonicalWorkspacePath(WorkspacePaths.defaultProject())
        if (canonicalWorkspacePath(_state.value.projectPath) == clean) {
            switchProjectPathInternal(defaultPath)
        }
        _state.update { it.copy(workspaces = updated, message = "已从侧栏移除工作区", messageIsError = false) }
    }

    fun toggleWorkspaceExpanded(path: String) {
        val clean = canonicalWorkspacePath(path)
        val current = _state.value.workspaces
        val hasEntry = current.any { canonicalWorkspacePath(it.path) == clean }
        val updated = if (hasEntry) {
            current.map {
                if (canonicalWorkspacePath(it.path) == clean) it.copy(expanded = !it.expanded) else it
            }
        } else {
            current + WorkspaceEntry(
                id = clean,
                path = clean,
                label = WorkspacePaths.nameOf(clean),
                expanded = false,
            )
        }
        workspaceStore.saveWorkspaces(updated)
        _state.update { it.copy(workspaces = updated) }
    }

    fun setAllWorkspacesExpanded(expanded: Boolean) {
        val updated = _state.value.workspaces.map { it.copy(expanded = expanded) }
        workspaceStore.saveWorkspaces(updated)
        _state.update { it.copy(workspaces = updated) }
    }

    fun setSidebarOrderBy(orderBy: SidebarOrderBy) {
        workspaceStore.saveOrderBy(orderBy)
        _state.update { it.copy(sidebarOrderBy = orderBy) }
    }

    /** 在指定工作区目录内直接开始新会话。 */
    fun newSessionInWorkspace(workspacePath: String) {
        val clean = canonicalWorkspacePath(workspacePath)
        if (clean.isNotEmpty() && clean != canonicalWorkspacePath(_state.value.projectPath)) {
            switchProjectPathInternal(clean)
        }
        newSession()
    }

    /** 从磁盘重新读取全量会话列表（跨所有工作区）。 */
    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.listAll()
            _state.update { state -> state.copy(sessions = sessions) }
        }
    }



    fun openSidebar() {
        // 每次拉开侧栏都重读一次会话列表：新会话是在**第一轮消息之后**才落库的，
        // 不刷新的话"刚聊完的会话"在侧栏里看不到。
        refreshSessions()
        maybeRunStorageMaintenance()
        _state.update { it.copy(sidebarOpen = true) }
    }

    /** 手动维护入口（设置/调试页用）。 */
    fun maintainStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = SessionReader.maintain()
            _state.update { it.copy(message = result, messageIsError = result.startsWith("维护失败")) }
        }
    }

    /** 读取真实的存储占用报告（设置/调试页用）。 */
    fun storageReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val report = SessionReader.storageReport()
            _state.update { it.copy(message = report, messageIsError = report.startsWith("存储信息读不出来")) }
        }
    }

    /** 把当前会话导出到系统文件选择器交给的 Uri。 */
    fun exportSessionTo(uri: Uri) {
        val sessionId = _state.value.activeSessionId
        if (sessionId.isEmpty()) {
            _state.update { it.copy(message = "没有打开的会话", messageIsError = true) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val temp = File(cacheDir(), SessionStore.suggestedArchiveName(sessionId))
                SessionStore.exportSession(sessionId, temp)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    temp.inputStream().use { input -> input.copyTo(output) }
                } ?: error("无法打开目标文件")
                temp.delete()
                "会话已导出"
            }.getOrElse { "导出失败：" + (it.message ?: "未知原因") }
            _state.update { it.copy(message = result, messageIsError = result.startsWith("导出失败")) }
        }
    }

    /** 从系统文件选择器得到的 Uri 导入一条新会话。 */
    fun importSessionFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val temp = File(cacheDir(), "import-" + System.currentTimeMillis() + ".jsonl.gz")
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取归档")
                val id = SessionStore.importSession(temp)
                temp.delete()
                refreshSessions()
                "已导入会话：" + id.take(8)
            }.getOrElse { "导入失败：" + (it.message ?: "未知原因") }
            _state.update { it.copy(message = result, messageIsError = result.startsWith("导入失败")) }
        }
    }

    private fun cacheDir(): File = getApplication<Application>().cacheDir
    fun closeSidebar() = _state.update { it.copy(sidebarOpen = false) }

    private fun hideSidebarForNavigation() {
        _state.update { it.copy(sidebarOpen = false) }
    }


    // ---------- 工作区 ----------

    /** 新 Activity 从启动器打开时回到对话页。 */
    fun resetTabForLaunch() {
        _state.update {
            it.copy(
                tab = WorkspaceTab.CHAT,
                fileSelection = emptySet(),
                fileSelectionMode = false,
            )
        }
    }

    fun selectTab(tab: WorkspaceTab) {
        _state.update {
            it.copy(
                tab = tab,
                fileSelection = if (tab == WorkspaceTab.FILES) it.fileSelection else emptySet(),
                fileSelectionMode = if (tab == WorkspaceTab.FILES) it.fileSelectionMode else false,
            )
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
            // 三个字段一起写：`themeSettings` 是真源，`themeMode` 是它的缓存
            it.copy(
                themeMode = next,
                themeSettings = it.themeSettings.copy(mode = next),
                settingsDraft = it.settingsDraft?.copy(themeMode = next),
                message = "主题：${next.label}",
            )
        }
        persistTheme(next)
    }

    fun updateThemeSettings(transform: (com.zhizhu.zhicode.compose.data.ThemeSettings) ->
        com.zhizhu.zhicode.compose.data.ThemeSettings) {
        _state.update { s ->
            val next = transform(s.themeSettings)
            s.copy(
                themeMode = next.mode,
                themeSettings = next,
                settingsDraft = s.settingsDraft?.copy(themeMode = next.mode),
            )
        }
        // 登记"有一笔主题改动还没落盘"。
        scheduleThemePersist()
    }

    /** 安排一次主题落盘（与 [scheduleSettingsPersist] 同一套去抖语义，但走主题自己的 */
    private fun scheduleThemePersist() {
        settingsPersistJob?.cancel()
        settingsPersistJob = viewModelScope.launch {
            delay(SettingsPersistDebounceMs)
            persistThemeSettings()
            settingsPersistJob = null
        }
    }

    /** 立刻把主题设置写盘（切主题、关设置页时用）。 */
    fun persistThemeSettings() {
        val settings = _state.value.themeSettings
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ThemeStore.write(getApplication(), settings) }
        }
    }

    // ---------- 内置 Termux 运行环境 ----------

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
                else "- 状态：**不一致** — 启动时会自动升级 Debian 容器系统目录（`/root` 工作区与个人数据会保留）"
            )
        }
    }

    /** 生成自检报告。 */
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
    // ---------------------------------------------------------------- OOBE 引导

    fun refreshOobePermissions() {
        val snap = OobePermissions.inspect(getApplication())
        _state.update { it.copy(oobePermissions = snap) }
        refreshSharedStoragePermission()
    }

    fun reopenOobeFromSettings() {
        hideSidebarForNavigation()
        val snap = OobePermissions.inspect(getApplication())
        _state.update {
            it.copy(
                settingsOpen = false,
                oobeOpen = true,
                oobeStep = OobeStep.PERMISSIONS,
                oobePermissions = snap,
            )
        }
    }

    fun dismissReopenedOobe() {
        _state.update { it.copy(oobeOpen = false) }
    }

    fun advanceOobeStep() {
        val current = _state.value
        when (current.oobeStep) {
            OobeStep.PERMISSIONS -> {
                val snap = OobePermissions.inspect(getApplication())
                if (!snap.allRequiredGranted) {
                    _state.update {
                        it.copy(
                            oobePermissions = snap,
                            message = "请先授予全部基础权限",
                            messageIsError = true,
                        )
                    }
                    return
                }
                val upToDate = runCatching { installer.isUpToDate() }.getOrDefault(false)
                val ready = runCatching { installer.isInstalled() }.getOrDefault(false)
                _state.update {
                    it.copy(
                        oobePermissions = snap,
                        oobeStep = OobeStep.RUNTIME,
                        runtimeReady = ready,
                        runtimeMessage = if (upToDate) "Ubuntu 24.04 运行环境已就绪" else if (ready) "正在校验内置 Node.js 与 Pi…" else "",
                    )
                }
                if (ready && !upToDate && !current.runtimeInstalling) {
                    autoInstallRuntimeIfStale()
                }
            }
            OobeStep.RUNTIME -> {
                if (!_state.value.runtimeReady || _state.value.runtimeInstalling) return
                _state.update { it.copy(oobeStep = OobeStep.MODEL) }
                viewModelScope.launch(Dispatchers.IO) { syncActiveProfile() }
            }
            OobeStep.MODEL -> completeOobe()
        }
    }

    fun goBackOobeStep() {
        val current = _state.value
        when (current.oobeStep) {
            OobeStep.PERMISSIONS -> Unit
            OobeStep.RUNTIME -> if (!current.runtimeInstalling) {
                refreshOobePermissions()
                _state.update { it.copy(oobeStep = OobeStep.PERMISSIONS) }
            }
            OobeStep.MODEL -> _state.update { it.copy(oobeStep = OobeStep.RUNTIME) }
        }
    }

    fun selectPreviousOobeStep(step: OobeStep) {
        val current = _state.value
        if (step.index >= current.oobeStep.index || current.runtimeInstalling) return
        if (step == OobeStep.PERMISSIONS) refreshOobePermissions()
        _state.update { it.copy(oobeStep = step) }
    }

    fun openApiConfigFromOobe() {
        _state.update {
            it.copy(apiConfig = ApiConfigState(profiles = emptyList(), activeId = "", loading = true))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { ApiConfigStore.read(getApplication()) }.getOrNull()
            _state.update { s ->
                val current = s.apiConfig ?: return@update s
                s.copy(apiConfig = loaded ?: current.copy(loading = false))
            }
            if (loaded != null) {
                syncActiveProfile()
                syncRoleCardFromStore()
            }
        }
    }

    fun toggleRootFromOobe(enable: Boolean) {
        if (!enable) {
            updateRootSetting(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val shell = com.termux.app.zhicode.termux.TermuxShellExecutor(getApplication())
                val res = shell.executeAsRoot("id -u", getApplication<Application>().filesDir.absolutePath, 10_000)
                res.exitCode == 0 && !res.timedOut && res.stdout.trim() == "0"
            }.getOrDefault(false)
            if (ok) {
                updateRootSetting(true)
                _state.update { it.copy(message = "已获取 Root 授权并启用") }
            } else {
                updateRootSetting(false)
                _state.update { it.copy(message = "未获得 Root 授权", messageIsError = true) }
            }
        }
    }

    fun completeOobe() {
        OobeStore.markCompleted(getApplication())
        _state.update { it.copy(oobeOpen = false) }
        viewModelScope.launch(Dispatchers.IO) {
            if (runCatching { installer.isInstalled() }.getOrDefault(false)) {
                installer.setupStorageLinks()
            }
            ensureWorkspace()
            syncActiveProfile()
        }
    }

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

    fun selectUbuntuMirror(mirrorId: String) {
        val mirror = UbuntuMirrorSource.findById(mirrorId) ?: return
        installer.setSelectedMirror(mirror)
        _state.update {
            it.copy(
                ubuntuMirrorId = mirror.id,
                runtimeMessage = if (!it.runtimeReady && it.runtimeMessage == "请先选择 Ubuntu 24.04 下载源") "" else it.runtimeMessage,
            )
        }
    }

    /** 安装（或重新初始化）Ubuntu 24.04 + 内置 Node.js + Pi 环境。 */
    fun installRuntime() {
        if (_state.value.runtimeInstalling) return
        val mirror = UbuntuMirrorSource.findById(_state.value.ubuntuMirrorId)
            ?: installer.savedMirrorOrNull()
        if (mirror == null) {
            _state.update {
                it.copy(runtimeMessage = "请先选择 Ubuntu 24.04 下载源")
            }
            return
        }
        val preparing = "正在从 ${mirror.displayName} 准备下载…"
        _state.update {
            it.copy(
                ubuntuMirrorId = mirror.id,
                runtimeInstalling = true,
                runtimeProgress = 0,
                runtimeMessage = preparing,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { installRuntimeBlocking(mirror, preparing) }
    }

    private suspend fun installRuntimeBlocking(mirror: UbuntuMirrorSource, preparing: String) {
        withContext(Dispatchers.Main) { releaseTerminalPane() }

        _state.update { it.copy(runtimeMessage = preparing) }
        val failure = installRuntimeReporting(mirror)
        if (failure != null) {
            appendRuntimeLog("安装失败：$failure")
            _state.update {
                it.copy(
                    runtimeInstalling = false,
                    runtimeMessage = "初始化失败：$failure",
                )
            }
            return
        }
        appendRuntimeLog("安装完成：已装=${runCatching { installer.installedVersion() }.getOrNull() ?: "(无)"}")
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                runtimeInstalling = false,
                runtimeProgress = 100,
                runtimeMessage = "Ubuntu 24.04 + Node.js + Pi 安装完成，请点击「下一步」",
                runtimeReady = installer.isInstalled(),
                environmentReport = reportText,
                message = if (it.oobeOpen) "Ubuntu 24.04 运行环境已安装完成" else it.message,
                messageIsError = if (it.oobeOpen) false else it.messageIsError,
            )
        }
        ensureWorkspace()
    }

    private suspend fun installRuntimeReporting(mirror: UbuntuMirrorSource): String? = runCatching {
        installer.install(mirror) { message, percent ->
            _state.update {
                it.copy(
                    runtimeMessage = message,
                    runtimeProgress = percent,
                )
            }
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

    /** 打开设置弹窗：从当前 state 快照一份 draft。 */
    fun openSettings() {
        _state.update {
            it.copy(
                settingsOpen = true,
                // 见 hideSidebarForNavigation：点侧栏里的入口要先收起侧栏。
                sidebarOpen = false,
                settingsDraft = SettingsDraft.from(it),
            )
        }
        refreshExtensions()
    }

    /** 统计「扩展」组要显示的数量（MCP / 技能 / 角色卡）。 */
    fun refreshExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            val projectPath = _state.value.projectPath
            val summary = ExtensionSummary(
                mcpTotal = runCatching { McpStore.read().servers.size }.getOrDefault(0),
                mcpEnabled = runCatching { McpStore.read().servers.count { it.enabled } }.getOrDefault(0),
                skillTotal = runCatching { SkillStore.list(projectPath).size }.getOrDefault(0),
                projectSkills = runCatching {
                    SkillStore.list(projectPath).count { it.scope == com.zhizhu.zhicode.compose.model.SkillScope.PROJECT }
                }.getOrDefault(0),
                userSkills = runCatching {
                    SkillStore.list(projectPath).count { it.scope == com.zhizhu.zhicode.compose.model.SkillScope.USER }
                }.getOrDefault(0),
                roleCardTotal = runCatching {
                    RoleCardStore.list(getApplication()).size
                }.getOrDefault(0),
                loaded = true,
            )
            _state.update { state ->
                val connected = state.mcpConfig?.status?.values?.count { it.connected } ?: 0
                state.copy(extensions = summary.copy(mcpConnected = connected))
            }
        }
    }

    /** 设置落盘的去抖窗口。 */
    private val SettingsPersistDebounceMs = 400L

    fun closeSettings() {
        // 关页面前把去抖窗口里那次改动兑现：用户"在提示词里打完字就走"时，
        // 那一次改动还没到 [SettingsPersistDebounceMs] 就会被丢掉。
        flushSettings()
        _state.update { it.copy(settingsDraft = null, settingsOpen = false) }
    }

    /** 打开「UI 调试」整页（**仅 debug 构建**）。 */
    fun openUiDebug() {
        // 见 hideSidebarForNavigation。
        hideSidebarForNavigation()
        _state.update { it.copy(uiDebugOpen = true) }
    }

    /** 关闭 UI 调试页。若它是由设置页打开的，设置主页会随之露出来（栈自动回退一层）。 */
    fun closeUiDebug() = _state.update { it.copy(uiDebugOpen = false) }

    /** 重置 UI 调试页的样例数据。 */
    fun requestUiDebugReset() = _state.update { it.copy(uiDebugResetToken = it.uiDebugResetToken + 1) }

    /** 开关**全局调试浮层**。 */
    fun setDebugOverlayEnabled(enabled: Boolean) = _state.update {
        it.copy(debugOverlayEnabled = enabled, debugOverlayExpanded = enabled)
    }

    /** 展开 / 收起调试浮层（收起态是贴边的窄药丸）。 */
    fun setDebugOverlayExpanded(expanded: Boolean) = _state.update {
        it.copy(debugOverlayExpanded = expanded)
    }

    /** 开关**主体调试模式**：真实界面就地显示调试信息（不另开页面）。 */
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

    fun applySettingsDraft(draft: SettingsDraft) {
        val previous = _state.value.settingsDraft
        _state.update { draft.applyTo(it, keepDraft = true) }
        /* 「强制后台保活」必须**立刻**落到真正的服务上。 */
        if (previous == null || previous.forcedKeepAliveEnabled != draft.forcedKeepAliveEnabled) {
            syncKeepAlive(draft.forcedKeepAliveEnabled)
        }
        val typingOnly = previous != null && differsOnlyInFreeText(previous, draft)
        scheduleSettingsPersist(debounce = typingOnly)
    }

    /** 把「强制后台保活」开关同步到真正的服务。 */
    private fun syncKeepAlive(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            KeepAliveService.start(context, _state.value.settings.rootExecutionEnabled)
        } else {
            KeepAliveService.stop(context)
        }
    }

    /** 回到前台时按**当前状态**补一次保活。 */
    fun resumeKeepAliveIfEnabled() {
        if (_state.value.settings.forcedKeepAliveEnabled) {
            syncKeepAlive(true)
        }
    }

    /** `/keepalive on|off|check` —— 与 IQ-Code-Android 的 `handleKeepAliveSlash` 同一套取值。 */
    private fun handleKeepAliveSlash(arg: String) {
        when (arg.lowercase(Locale.US)) {
            "on", "enable" -> {
                updateKeepAliveSetting(true)
                _state.update {
                    it.copy(message = "强制后台保活已开启；Root 可用时会自动强化系统后台策略")
                }
            }

            "off", "disable", "revoke" -> {
                updateKeepAliveSetting(false)
                _state.update { it.copy(message = "强制后台保活已关闭") }
            }

            else -> {
                val on = _state.value.settings.forcedKeepAliveEnabled
                _state.update {
                    it.copy(
                        message = if (on) {
                            "强制后台保活：已开启"
                        } else {
                            "强制后台保活：未开启；使用 /keepalive on 开启"
                        },
                    )
                }
            }
        }
    }

    /** 改写保活开关：走草稿 → 状态 → 服务 → 落盘这条同一条链路，避免两条真源。 */
    private fun updateKeepAliveSetting(enabled: Boolean) {
        val draft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        applySettingsDraft(draft.copy(forcedKeepAliveEnabled = enabled))
    }

    /** `/root on|off|check` —— Agent Root 开关。 */
    private fun handleRootSlash(arg: String) {
        val current = _state.value.settings.rootExecutionEnabled
        when (arg.lowercase(Locale.US)) {
            "on", "enable" -> {
                updateRootSetting(true)
                _state.update { it.copy(message = "Agent Root 已开启") }
            }

            "off", "disable" -> {
                updateRootSetting(false)
                _state.update { it.copy(message = "Agent Root 已关闭") }
            }

            else -> {
                _state.update {
                    it.copy(message = if (current) "Agent Root：已开启" else "Agent Root：未开启；使用 /root on 开启")
                }
            }
        }
    }

    private fun updateRootSetting(enabled: Boolean) {
        val draft = _state.value.settingsDraft ?: SettingsDraft.from(_state.value)
        applySettingsDraft(draft.copy(rootExecutionEnabled = enabled))
        // root 期望值变了，保活那边的加强/撤回也要跟着重算。
        if (_state.value.settings.forcedKeepAliveEnabled) syncKeepAlive(true)
    }

    /** 两次草稿是否**只**在自由文本字段上不同。 */
    private fun differsOnlyInFreeText(a: SettingsDraft, b: SettingsDraft): Boolean =
        a.copy(
            customSystemPrompt = b.customSystemPrompt,
            initPrompt = b.initPrompt,
            summaryPrompt = b.summaryPrompt,
            compactPromptHead = b.compactPromptHead,
            projectPath = b.projectPath,
            contextWindow = b.contextWindow,
            webSearchMaxResults = b.webSearchMaxResults,
            webSearchTimeoutSec = b.webSearchTimeoutSec,
            autoCompactPercent = b.autoCompactPercent,
        ) == b

    /** 安排一次设置落盘。 */
    private fun scheduleSettingsPersist(debounce: Boolean) {
        settingsPersistJob?.cancel()
        if (!debounce) {
            settingsPersistJob = null
            persistSettingsNow()
            return
        }
        settingsPersistJob = viewModelScope.launch {
            delay(SettingsPersistDebounceMs)
            persistSettingsNow()
        }
    }

    fun flushSettings() {
        if (settingsPersistJob == null) return
        settingsPersistJob?.cancel()
        settingsPersistJob = null
        // 两份存储，两条都要兑现（顺序无所谓，它们互不依赖）。
        persistThemeSettings()
        persistSettingsNow()
    }

    private fun persistSettingsNow() {
        val snapshot = _state.value
        val overrides = engineOverrides().forGlobalSettings()
        val draft = SettingsDraft.from(snapshot)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val store = ApiSettingsStore(getApplication())
                val config = store.load()
                applyEngineOverrides(config, overrides)
                store.saveGlobalSettings(config, true)
            }
            // 主题与搜索密钥**不在** SessionConfig 里，configure() 那次落盘罩不到它们，
            // 各自补一次（密钥走加密槽，见 ApiSettingsStore.setWebSearchKey）。
            persistTheme(draft.themeMode)
            // 主题设置是**另一份**存储（预设 / Monet / 显示开关），一起兑现，
            // 否则「改了预设就大退」会丢掉这一批改动。
            persistThemeSettings()
            persistWebSearchKey(draft)
            // 子 Agent 并发上限属于会话全局配置，已通过 applyEngineOverrides + saveDurable 落盘。
            // 终端输入类型也不在 SessionConfig 里（终端是独立 Activity，不读会话配置）。
            runCatching {
                ApiSettingsStore.setTerminalCharMode(getApplication(), draft.terminalCharMode)
            }
        }
    }

    /** 把主题写回磁盘。 */
    private fun persistTheme(mode: ThemeMode) {
        runCatching { ApiSettingsStore.setThemeMode(getApplication(), mode.name.lowercase()) }
        // 同一份语义住在两处存储里：`theme_mode` 是深浅三态（老键，编辑器 Activity
        persistThemeSettings()
    }

    /** 持久化**当前选中服务**的密钥（界面只可能改这一个）。 */
    private fun persistWebSearchKey(draft: SettingsDraft) {
        val provider = draft.webSearchProvider
        if (!provider.needsKey) return
        val key = draft.webSearchKeys[provider].orEmpty()
        runCatching { ApiSettingsStore.setWebSearchKey(getApplication(), provider.name.lowercase(), key) }
    }

    /** 分类页里的「入口类」条目：先关掉设置弹窗，再把对应目标打开。 */
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
            "prompts" -> openPrompts()
            "theme" -> openThemePage()
            "chatDisplay" -> openChatDisplay()
            // 从设置页进来时设置页仍然开着，于是返回会先回到设置主页 ——
            // 这正是从设置里点进去的人期望的。
            "about" -> openAbout()
            "oobe" -> reopenOobeFromSettings()
            "uiDebug" -> openUiDebug()
            else -> Unit
        }
    }

    // ---------- 对话流交互 ----------

    fun toggleToolExpanded(id: String) {
        // 先决定"要展开还是收起"：只有从收起变展开、且这条带的是截断预览时才需要取全文。
        val willExpand = _state.value.transcript
            .firstOrNull { it.kind == ChatKind.TOOL_GROUP }
            ?.tools?.firstOrNull { it.id == id }
            ?.expanded != true
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { if (it.id == id) it.copy(expanded = !it.expanded) else it },
                    )
                },
                // 结构指纹（条数/首尾/最后一条正文与工具数）都不变，只有内容原地变了 
                transcriptRevision = s.transcriptRevision + 1,
            )
        }
        if (willExpand) loadFullToolOutput(id)
    }

    /** 展开一条工具时把**完整输出**取回来。 */
    private fun loadFullToolOutput(toolUseId: String) {
        if (toolUseId.isEmpty()) return
        val sessionId = _state.value.activeSessionId
        if (sessionId.isEmpty()) return
        val already = _state.value.transcript
            .firstOrNull { it.kind == ChatKind.TOOL_GROUP }
            ?.tools?.firstOrNull { it.id == toolUseId }
        if (already == null || !already.outputTruncated) return

        viewModelScope.launch(Dispatchers.IO) {
            val full = SessionStore.readToolResult(sessionId, toolUseId)
            if (full.isEmpty()) return@launch
            _state.update { s ->
                s.copy(
                    transcript = s.transcript.map { item ->
                        if (item.kind != ChatKind.TOOL_GROUP) item
                        else item.copy(
                            tools = item.tools.map { tool ->
                                if (tool.id != toolUseId) tool
                                else tool.copy(output = full, outputTruncated = false)
                            },
                        )
                    },
                    transcriptRevision = s.transcriptRevision + 1,
                )
            }
        }
    }

    /** 把某一条工具的展开状态设成**指定值**（不是取反）。 */
    private fun setToolExpanded(itemId: String, toolId: String, expanded: Boolean) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != itemId || item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { if (it.id == toolId) it.copy(expanded = expanded) else it },
                    )
                },
                transcriptRevision = s.transcriptRevision + 1,
            )
        }
        // 与 toggleToolExpanded 同一条规则：展开时按需取全文。
        if (expanded) loadFullToolOutput(toolId)
    }

    fun toggleGroupExpanded(id: String, groupKey: String) {
        if (groupKey.isEmpty()) return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id || item.kind != ChatKind.TOOL_GROUP) {
                        item
                    } else {
                        val open = item.expandedGroups
                        item.copy(
                            expandedGroups = if (groupKey in open) open - groupKey else open + groupKey,
                        )
                    }
                },
                transcriptRevision = s.transcriptRevision + 1,
            )
        }
    }

    fun toggleThinking(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { if (it.id == id) it.copy(thinkingExpanded = !it.thinkingExpanded) else it },
                transcriptRevision = s.transcriptRevision + 1,
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
        _state.update { it.copy(filePath = path, pendingEditorOpen = null, fileSelection = emptySet(), fileSelectionMode = false, fileListOptions = FileListOptions()) }
        scheduleFileReload()
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

    /** 点开一条列表项：目录就进去，文件就交给**编辑器那个独立 Activity**。 */
    fun openFile(entry: FileEntry) {
        if (entry.directory) {
            openDirectory(entry)
            return
        }
        requestOpenEditor(entry.path)
    }

    /** 请求把某个文件交给编辑器打开（新建文件后立即打开也走它）。 */
    fun requestOpenEditor(path: String) = _state.update { it.copy(pendingEditorOpen = path) }

    /** 界面已经拿着这个路径去启动编辑器了，清掉标志，避免重组时重复启动。 */
    fun consumePendingEditorOpen() = _state.update { it.copy(pendingEditorOpen = null) }

    /** 文件面板的根路径（内置 Termux home 不存在时回退到应用私有目录）。 */
    /** 文件面板当前的根，取决于用户在界面上选的 [FileRoot]。 */
    private fun rootPath(): String = when (_state.value.fileRoot) {
        FileRoot.HOME -> TermuxConstants.TERMUX_HOME_DIR_PATH
        FileRoot.SHARED -> StorageLinks.EXTERNAL_ROOT
    }

    /** 切换文件面板的根。切过去时**回到该根的顶层**，而不是停在别的根里的路径上。 */
    fun switchFileRoot(root: FileRoot) {
        _state.update {
            it.copy(
                fileRoot = root,
                filePath = when (root) {
                    FileRoot.HOME -> TermuxConstants.TERMUX_HOME_DIR_PATH
                    FileRoot.SHARED -> StorageLinks.EXTERNAL_ROOT
                },
                pendingEditorOpen = null,
                fileNameForm = null,
                fileDeletePrompt = null,
                // 同 navigateTo：跨根之后旧路径完全没有意义。
                fileSelection = emptySet(),
                fileSelectionMode = false,
                fileEntries = emptyList(),
                fileNote = "",
                fileListOptions = FileListOptions(),
            )
        }
        scheduleFileReload()
    }

    private fun scheduleFileReload(debounceMs: Long = 0L) {
        fileReloadJob?.cancel()
        fileReloadJob = viewModelScope.launch(Dispatchers.IO) {
            if (debounceMs > 0) delay(debounceMs)
            reloadFiles()
        }
    }

    fun updateFileQuery(query: String) {
        _state.update { it.copy(fileListOptions = it.fileListOptions.copy(query = query)) }
        scheduleFileReload(debounceMs = 180L)
    }

    fun updateFileSort(key: FileSortKey, descending: Boolean = _state.value.fileListOptions.descending) {
        _state.update { it.copy(fileListOptions = it.fileListOptions.copy(sortKey = key, descending = descending)) }
        scheduleFileReload()
    }

    fun toggleFileSortDirection() {
        val current = _state.value.fileListOptions
        updateFileSort(current.sortKey, !current.descending)
    }

    /** 文件管理器式排序：目录永远在文件之前，正序/倒序只改变每组内部顺序。 */
    private fun fileComparator(options: FileListOptions): Comparator<FileEntry> {
        val inside = when (options.sortKey) {
            FileSortKey.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            FileSortKey.SIZE -> compareBy<FileEntry> { it.size }.thenBy { it.name.lowercase() }
            FileSortKey.MODIFIED -> compareBy<FileEntry> { it.modifiedAt }.thenBy { it.name.lowercase() }
            FileSortKey.TYPE -> compareBy<FileEntry> {
                it.name.substringAfterLast('.', "").lowercase()
            }.thenBy { it.name.lowercase() }
        }
        val orderedInside = if (options.descending) inside.reversed() else inside
        return compareBy<FileEntry> { if (it.directory) 0 else 1 }.then(orderedInside)
    }

    /** 共享存储没授权时，跳去系统那个「所有文件访问权限」页面。 */
    fun refreshSharedStoragePermission() {
        val granted = runCatching {
            android.os.Environment.isExternalStorageManager()
        }.getOrDefault(false)
        _state.update { it.copy(sharedStorageGranted = granted) }
    }


    // ---------- 文件：新建 / 重命名 / 删除 ----------

    /** 打开「新建」表单。 */
    fun newFileForm() {
        _state.update { it.copy(fileNameForm = FileNameForm(title = "新建", draft = "")) }
    }

    /** 打开「重命名」表单。 */
    fun renameForm(entry: FileEntry) {
        _state.update {
            it.copy(fileNameForm = FileNameForm(title = "重命名", target = entry, draft = entry.name))
        }
    }

    fun updateFileNameDraft(text: String) = _state.update { s ->
        // 改名字就把上一次的失败原因清掉：不清的话"已经存在"会一直挂在那里，
        // 用户明明已经换成另一个名字了，看到的还是上一个错。
        s.copy(fileNameForm = s.fileNameForm?.copy(draft = text, failure = null))
    }

    fun cancelFileNameForm() = _state.update { it.copy(fileNameForm = null) }

    /** 提交「新建 / 重命名」。 */
    fun submitFileNameForm(directory: Boolean = false) {
        val form = _state.value.fileNameForm ?: return
        val dir = File(_state.value.filePath)
        viewModelScope.launch(Dispatchers.IO) {
            val name = form.draft
            val error = if (form.target != null) {
                FileOps.rename(File(form.target.path), name)
            } else if (directory) {
                FileOps.createDirectory(dir, name)
            } else {
                FileOps.createFile(dir, name)
            }
            if (error != null) {
                _state.update { s ->
                    s.copy(fileNameForm = s.fileNameForm?.copy(failure = error))
                }
                return@launch
            }
            _state.update { it.copy(fileNameForm = null, message = if (form.target != null) "已重命名为 $name" else "已新建 $name") }
            val created = File(dir, name)
            if (form.target == null && created.isDirectory) {
                navigateTo(created.absolutePath)
            } else {
                reloadFiles()
                if (form.target == null && created.isFile) {
                    // 「新建了个文件然后还要自己找出来」是一步没必要的手续：直接交给编辑器打开。
                    requestOpenEditor(created.absolutePath)
                }
            }
        }
    }

    /** 打开单条删除确认（带"会一起消失多少条"）。 */
    fun requestDelete(entry: FileEntry) {
        openDeletePrompt(listOf(entry))
    }

    private fun openDeletePrompt(entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 数条数要递归读盘（`countForDelete`），放 IO 线程。
            val count = entries.sumOf { FileOps.countForDelete(File(it.path)) }
            _state.update { it.copy(fileDeletePrompt = FileDeletePrompt(entries = entries, count = count)) }
        }
    }

    fun cancelDelete() = _state.update { it.copy(fileDeletePrompt = null) }

    fun confirmDelete() {
        val prompt = _state.value.fileDeletePrompt ?: return
        val targets = prompt.entries
        viewModelScope.launch(Dispatchers.IO) {
            var failed: String? = null
            targets.forEach { entry ->
                val error = FileOps.delete(File(entry.path))
                if (error != null && failed == null) failed = error
            }
            if (failed != null) {
                _state.update { it.copy(fileDeletePrompt = null, message = failed, messageIsError = true) }
                return@launch
            }
            val removed = targets.map { it.path }.toSet()
            _state.update { s ->
                s.copy(
                    fileDeletePrompt = null,
                    // 选中的东西被删掉之后选择模式自然结束（空集合＝不在选择模式）。
                    fileSelection = s.fileSelection - removed,
                    fileSelectionMode = false,
                    message = if (targets.size == 1) "已删除 ${targets[0].name}" else "已删除 ${targets.size} 项",
                )
            }
            reloadFiles()
        }
    }

    // 退出确认与保存都不再在这里：编辑器是独立 Activity，它自己的 EditorViewModel

    // ---------- 文件：选择模式（长按多选） ----------

    /** 长按一行：**进入选择模式并选中它**。 */
    fun longPressFileEntry(entry: FileEntry) {
        _state.update { state ->
            val selection = if (state.fileSelectionMode && entry.path in state.fileSelection) {
                state.fileSelection - entry.path
            } else {
                state.fileSelection + entry.path
            }
            state.copy(fileSelectionMode = true, fileSelection = selection)
        }
    }

    fun setFileSelection(paths: Set<String>) {
        _state.update { s ->
            if (!s.fileSelectionMode) s else s.copy(fileSelection = paths.intersect(s.fileEntries.map { it.path }.toSet()))
        }
    }

    /** 点一行。 */
    fun toggleFileSelection(entry: FileEntry) {
        _state.update { s ->
            s.copy(
                fileSelection = if (s.fileSelection.contains(entry.path)) s.fileSelection - entry.path
                else s.fileSelection + entry.path,
            )
        }
    }

    /** 选择模式是否开着。 */
    private fun selectionOn(): Boolean = _state.value.fileSelectionMode

    fun clearFileSelection() = _state.update { it.copy(fileSelection = emptySet(), fileSelectionMode = false) }

    fun toggleSelectAllFiles() {
        val all = _state.value.fileEntries.map { it.path }.toSet()
        _state.update { s ->
            val allSelected = all.isNotEmpty() && s.fileSelection.containsAll(all)
            s.copy(fileSelection = if (allSelected) emptySet() else all)
        }
    }

    /** 选择模式下选中的那些条目（按当前列表里的条目还原）。 */
    private fun selectedEntries(): List<FileEntry> {
        val selection = _state.value.fileSelection
        return _state.value.fileEntries.filter { it.path in selection }
    }

    /** 将选中项暂存为复制任务；切换目录后使用「粘贴」放入目标目录。 */
    fun copySelectedEntries() = stageSelectedEntries(move = false)

    /** 将选中项暂存为移动任务；源文件只在目标副本成功后删除。 */
    fun moveSelectedEntries() = stageSelectedEntries(move = true)

    private fun stageSelectedEntries(move: Boolean) {
        val paths = selectedEntries().map { it.path }
        if (paths.isEmpty()) return
        _state.update {
            it.copy(
                fileClipboard = paths,
                fileClipboardMove = move,
                fileSelection = emptySet(),
                fileSelectionMode = false,
                message = if (move) "已选择 ${paths.size} 项移动，请打开目标目录后粘贴"
                else "已选择 ${paths.size} 项复制，请打开目标目录后粘贴",
                messageIsError = false,
            )
        }
    }

    /** 把暂存的文件粘贴到当前目录；目标冲突逐项保留原文件并反馈。 */
    fun pasteFilesIntoCurrentDirectory() = runBusy("files.paste") {
        val snapshot = _state.value
        val paths = snapshot.fileClipboard
        if (paths.isEmpty()) return@runBusy
        val destination = File(snapshot.filePath)
        run {
            val failures = mutableListOf<String>()
            val movedPaths = mutableSetOf<String>()
            var completed = 0
            paths.forEach { path ->
                val source = File(path)
                val error = if (snapshot.fileClipboardMove) FileOps.move(source, destination)
                else FileOps.copy(source, destination)
                if (error == null) {
                    completed++
                    if (snapshot.fileClipboardMove) movedPaths += path
                } else failures += "${source.name}：$error"
            }
            reloadFiles()
            _state.update { current ->
                val remainingMovePaths = current.fileClipboard.filterNot { it in movedPaths }
                val allSucceeded = failures.isEmpty()
                current.copy(
                    fileClipboard = if (snapshot.fileClipboardMove) remainingMovePaths else current.fileClipboard,
                    fileClipboardMove = if (snapshot.fileClipboardMove && remainingMovePaths.isEmpty()) false else current.fileClipboardMove,
                    // 粘贴完退出选择态：否则点条目仍是"切换选中"，看起来像进不去文件夹。
                    fileSelection = emptySet(),
                    fileSelectionMode = false,
                    message = if (allSucceeded) {
                        if (snapshot.fileClipboardMove) "已移动 $completed 项" else "已复制 $completed 项"
                    } else "已完成 $completed 项，${failures.size} 项失败：${failures.joinToString("；")}",
                    messageIsError = failures.isNotEmpty(),
                )
            }
        }
    }

    /** 重命名选中的那一条（只在**恰好选中一条**时可用）。 */
    fun renameSelectedEntry() {
        val entry = selectedEntries().singleOrNull() ?: return
        clearFileSelection()
        renameForm(entry)
    }

    /** 删除选中的那些（走同一个确认弹窗，代价按条累加）。 */
    fun deleteSelectedEntries() {
        val entries = selectedEntries()
        if (entries.isEmpty()) return
        openDeletePrompt(entries)
    }

    /** 把选中的文件附加到下一条消息。 */
    fun attachSelectedEntries() {
        val entries = selectedEntries()
        // 附加的语义是读取文件正文；目录或文件夹/文件混选时明确置灰，避免
        // 用户点下后悄悄忽略一部分选择。
        if (entries.isEmpty() || entries.any { it.directory }) return
        clearFileSelection()
        viewModelScope.launch(Dispatchers.IO) {
            var attached = 0
            val refused = mutableListOf<String>()
            entries.forEach { entry ->
                if (attachPath(entry.path, entry.name)) attached++ else refused += entry.name
            }
            // 附加完切到对话页：附件是给「下一条消息」的，留在文件页看不到它们，
            // 用户会以为没生效。
            _state.update {
                it.copy(
                    message = if (refused.isEmpty()) "已附加 $attached 个文件"
                    else "已附加 $attached 个，${refused.size} 个读不了（${refused.joinToString("、")}）",
                    messageIsError = refused.isNotEmpty(),
                    tab = if (attached > 0) WorkspaceTab.CHAT else it.tab,
                )
            }
        }
    }

    /** 进入选择模式时用：共享存储没授权时给一个真能点的入口。 */
    fun openSharedStorageSettings() {
        val context = getApplication<android.app.Application>()
        val pkg = context.packageName
        val launched = runCatching {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:$pkg"),
                )
            } else {
                // API 30 以下没有「所有文件访问权限」这一页，退到应用详情页。
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$pkg"),
                )
            }
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        launched.onFailure { error ->
            // ViewModel 手里只有 Application 上下文，必须带 NEW_TASK（见 openSandbox）。
            // 有些 ROM 把这两个页面也裁掉了 —— 失败就如实说，不静默吞掉。
            _state.update {
                it.copy(
                    message = "打不开系统设置页：${error.javaClass.simpleName}。请手动到「设置 → 应用 → ZhiCode → 权限 → 文件和媒体 → 允许管理所有文件」",
                    messageIsError = true,
                )
            }
        }
    }


    /** 手动关掉当前提示（点提示条本身，或自动超时）。 */
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

        /** SAF 文档附加的字节上限。 */
        private const val DocumentAttachLimit = 256 * 1024

        /** 思考预览保留的尾部字符数（原版 `liveThinking` 上限 1200）。 */
        private const val THINKING_LIMIT = 1200

        /** 单条工具实时输出的上限（原版 `liveOutput` 上限 40000）。 */
        private const val LIVE_OUTPUT_LIMIT = 40_000

        /** 内置环境安装日志保留的行数。 */
        private const val RUNTIME_LOG_MAX_LINES = 200

        /** 工具实时输出落盘间隔：每条进程输出都改一次 StateFlow 会把界面拖垮。 */
        private const val PROGRESS_FLUSH_MS = 200L

        /** 按下停止之后，等多久去问一次"到底停了没有"（第一档）。 */
        private const val STOP_CONFIRM_MS = 3_000L

        /** 第一档之后仍未确认时，再等多久就解锁输入框（第二档）。 */
        private const val STOP_UNLOCK_MS = 9_000L

        /** 助手气泡上保留的过程步骤条数上限（原版 12）。 */
        private const val PROCESS_STEP_LIMIT = 12

        /** 压缩摘要回显上限：摘要是给人看要点的，整段贴进对话会把记录淹掉。 */
        private const val COMPACT_SUMMARY_LIMIT = 600

        /** 引擎"没什么可压缩"时返回的前缀。 */
        private const val ENGINE_NOTHING_TO_COMPACT = "上下文已经足够精简"

        /** 文本附件（技能）拼进提示词的长度上限，与原版一致。 */
        private const val TEXT_ATTACHMENT_LIMIT = 60_000

        /** 图片导出一次最多几条。 */
        private const val MaxExportItems = 50

        private const val UNSET_MODEL_LABEL = "未设置"

        /** `/init` 的指令**搬走了**。 */

    }
}
