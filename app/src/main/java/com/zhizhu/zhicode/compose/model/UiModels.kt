package com.zhizhu.zhicode.compose.model

import com.termux.app.zhicode.core.FileOps

// `ChatKind` 与 `ToolKind` 都在自己的文件里：它们在 `model` 包内


enum class RiskLevel { NORMAL, HIGH }

data class ToolActivity(
    val id: String,
    val toolName: String,
    val displayName: String,
    val summary: String = "",
    val completed: Boolean = false,
    val failed: Boolean = false,
    val exitCode: Int? = null,
    val elapsedMs: Long = 0L,
    val additions: Int = 0,
    val deletions: Int = 0,
    val output: String = "",
    val outputTruncated: Boolean = false,
    val diff: String = "",
    val previews: List<ChatImage> = emptyList(),
    val expanded: Boolean = false,
    val awaitingPermission: Boolean = false,
    val kind: ToolKind = ToolKind.OTHER,
    val startedAtMs: Long = 0L,
    val stdoutChars: Int = 0,
    val stderrChars: Int = 0,
    /** 命令类工具的**原始命令行**（未截断、保留换行）。 */
    val command: String = "",
    val hint: String = "",
    /** `ReadMany` 这一次读了几个文件（其余工具是 0）。 */
    val readRequests: Int = 0,
)

data class ChatImage(
    /** base64（不带 `data:` 前缀，与引擎写进会话数据库的形态一致）。 */
    val data: String,
    /** `image/png` 这类 MIME；解码只做兜底提示，不参与格式判断。 */
    val mimeType: String,
    /** 文件名，用于解码失败时回退显示与无障碍描述。 */
    val name: String,
    val origin: ImageOrigin? = null,
)

data class ChatItem(
    val id: String,
    val kind: ChatKind,
    val title: String = "",
    val body: String = "",
    val thinking: String = "",
    val thinkingExpanded: Boolean = false,
    val processSteps: List<String> = emptyList(),
    val tools: List<ToolActivity> = emptyList(),
    val groupCompleted: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    val contextTokens: Int = -1,
    val contextWindow: Int = 0,
    val streaming: Boolean = false,
    val images: List<ChatImage> = emptyList(),
    /** 这条消息带的**非图片**附件（文本文件 / 技能）。 */
    val attachments: List<ChatAttachment> = emptyList(),
    /** 这一轮里被改过的文件（相对项目根的路径）。 */
    val editedFiles: List<String> = emptyList(),
    /** 本轮从提问到完成的耗时（毫秒，< 0 表示未知）。 */
    val elapsedMs: Long = -1L,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val project: String,
    val projectPath: String = "",
    val messageCount: Int,
    val updatedAtLabel: String,
    val updatedAtMs: Long = 0L,
    val busy: Boolean = false,
    val note: String = "",
)

/** 侧栏注册的工作区目录项（含不可删除的「默认工作区」）。 */
data class WorkspaceEntry(
    val id: String,
    val path: String,
    val label: String,
    val isDefault: Boolean = false,
    val expanded: Boolean = true,
    val createdAt: Long = 0L,
)

/** 侧栏工作区与会话排列模式。 */
enum class SidebarOrderBy(val label: String) {
    UPDATED("按最近活跃"),
    MANUAL("按添加顺序"),
}

/** 侧栏工作区分组视图节点（对齐 DeepSeek Harness WorkspaceBrowser）。 */
data class WorkspaceGroup(
    val key: String,
    val title: String,
    val path: String,
    val isDefault: Boolean,
    val expanded: Boolean,
    val containsActive: Boolean,
    val totalCount: Int,
    val hiddenCount: Int,
    val sessions: List<SessionSummary>,
)

enum class WorkspaceTab(val label: String) {
    CHAT("对话"), TERMINAL("终端"), FILES("文件")
}

enum class PermissionMode(val label: String, val detail: String) {
    ASK("询问确认", "只读操作直接放行，写入与命令执行需你确认"),
    PLAN("仅规划", "只读探索模式，拦截所有文件修改与命令执行"),
    BYPASS("全部放行", "跳过工具确认，直接执行所有已启用工具"),
}

enum class EffortLevel(val label: String, val wire: String) {
    OFF("off", "off"),
    MINIMAL("minimal", "minimal"),
    LOW("low", "low"),
    MEDIUM("medium", "medium"),
    HIGH("high", "high"),
    XHIGH("xhigh", "xhigh"),
    MAX("max", "max"),
    ;

    fun toPiLevel(): com.zhizhu.zhicode.pi.PiThinkingLevel =
        com.zhizhu.zhicode.pi.PiThinkingLevel.fromId(wire)

    companion object {
        val DEFAULT_AVAILABLE: List<EffortLevel> = listOf(OFF, MINIMAL, LOW, MEDIUM, HIGH)
    }
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class SlashCommand(val name: String, val hint: String)

/** 附件条上的一个条目。 */
data class Attachment(
    val id: String,
    val label: String,
    val detail: String,
    val isImage: Boolean,
    val textBody: String? = null,
    /** 体积，形如「125KB」。 */
    val sizeLabel: String = "",
)

data class ChatAttachment(
    val id: String,
    val label: String,
    /** 体积等副标题，如「125KB」。空串则不画第二行。 */
    val detail: String = "",
)

data class PermissionRequest(
    val id: String,
    val tool: String,
    val subtitle: String,
    val detail: String,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
)

/** 计划审批的内容。 */
data class PlanApproval(
    val id: String,
    val title: String,
    val body: String,
    val revision: Int,
    /** 计划文件绝对路径，展示在正文上方。 */
    val path: String = "",
    /** 底部的权限提示，例如「批准只确认计划；后续操作仍按你的权限模式：跳过权限」。 */
    val permissionNote: String = "",
)

data class ChoiceOption(val label: String, val detail: String = "", val checked: Boolean = false)

/** 「自由复制」抽屉的内容。 */
data class CopySheetState(
    val title: String,
    val text: String,
)

data class ChoicePickerState(
    val title: String,
    val options: List<ChoiceOption>,
    val intent: ChoiceIntent = ChoiceIntent.GENERIC,
    val prompt: String = "",
    val allowFreeForm: Boolean = false,
    val freeFormHint: String = "其他回答…",
    val multiSelect: Boolean = false,
    val submitLabel: String = "提交",
    val cancelLabel: String = "取消",
    /** 长按动作菜单要**锚**到哪一项（消息 id / 会话 id）。 */
    val anchorId: String? = null,

) {
    /** 是否按**贴住长按项的下拉菜单**渲染，而不是居中对话框。 */
    val isActionMenu: Boolean
        get() = anchorId != null &&
            (intent == ChoiceIntent.MESSAGE_ACTION || intent == ChoiceIntent.SESSION_ACTION)
}

enum class ChoiceIntent {
    GENERIC,

    PERMISSION_MODE,
    EFFORT,

    MESSAGE_ACTION,
    SESSION_ACTION,


    /** 计划模式的目标澄清：选完（或自由回答）后才产出计划。 */
    PLAN_GOAL,

    /** 引擎 `AskUserQuestion` 发起的分步提问。 */
    QUESTION,

    /** 会话备注：没有选项，只靠自由输入提交（允许空串表示清除）。 */
    SESSION_NOTE,

    /** 会话重命名：和 [SESSION_NOTE] 一样没有选项，只靠自由输入提交。 */
    SESSION_RENAME,

    /** 删除会话的**二次确认**。 */
    SESSION_DELETE_CONFIRM,

    /** 删除**单条消息**的二次确认。 */
    MESSAGE_DELETE_CONFIRM,
}

/** 文件面板里的一行。 */
data class FileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long = 0L,
    val modifiedAt: Long = 0L,
)

/** 导出面板（底部抽屉）的内容。 */
data class ExportSheetState(
    val title: String,
    val items: List<ChatItem>,
    /** 导出内容里带不带思考过程。默认带 —— 它本来就是对话的一部分。 */
    val includeReasoning: Boolean = true,
    /** 图片里把思考块**展开**画出来（否则只画一个「思考」标记）。 */
    val expandReasoning: Boolean = false,
    /** 这条面板是从**单条消息**打开的（而不是多选）。 */
    val singleSource: Boolean = false,
    /** 单条来源时记住是哪一条，供「选择更多消息…」进入多选时打底。 */
    val seedId: String? = null,
)

/** 导出的目标格式。 */
enum class ExportKind { MARKDOWN, IMAGE }

data class ExportPayload(
    /** 每次导出唯一。界面拿它当 effect 的 key（清空请求不会让它变）。 */
    val nonce: Int,
    val kind: ExportKind,
    val mimeType: String,
    /** 文件管理器里的默认文件名，例如 `chat-export-2026-11-01_1230.md`。 */
    val suggestedName: String,
    /** [ExportKind.MARKDOWN] 用：已经拼好的全文。 */
    val markdown: String = "",
    /** [ExportKind.IMAGE] 用：要画进图里的消息。 */
    val items: List<ChatItem> = emptyList(),
    val title: String = "",
    val includeReasoning: Boolean = true,
    val expandReasoning: Boolean = false,
)

data class OpenFile(
    val name: String,
    val path: String,
    val language: String,
    val content: String,
    /** 预览只读了前缀，不能把这份不完整正文保存回原文件。 */
    val truncated: Boolean = false,
    /** 当前正文使用的字符编码；保存时必须沿用它。 */
    val charsetName: String = "UTF-8",
)

enum class FileRoot(val label: String) {
    HOME("HOME"),
    SHARED("共享存储"),
}

enum class FileSortKey(val label: String) {
    NAME("名称"), SIZE("大小"), MODIFIED("修改时间"), TYPE("类型")
}

data class FileListOptions(
    val query: String = "",
    val sortKey: FileSortKey = FileSortKey.NAME,
    val descending: Boolean = false,
)

/** 「新建 / 重命名」共用的名字表单。 */
data class FileNameForm(
    val title: String,
    /** 重命名时被改的那条；新建时为 null。 */
    val target: FileEntry? = null,
    val draft: String = "",
    val failure: String? = null,
) {
    /** 校验结果，直接显示给用户。 */
    val error: String? get() = failure ?: FileOps.nameError(draft)

    val saveable: Boolean get() = error == null
}

/** 删除确认。 */
data class FileDeletePrompt(
    val entries: List<FileEntry>,
    val count: Int,
) {
    /** 是不是"会带走别的东西"的那种删除。 */
    val destructive: Boolean get() = count > entries.size

    /** 确认文案里的操作对象。 */
    val label: String
        get() = if (entries.size == 1) "「${entries[0].name}」" else "选中的 ${entries.size} 项"
}

data class AttachBrowserState(
    /** 当前根：项目 / HOME / 共享存储。 */
    val root: FileRoot = FileRoot.HOME,
    /** 当前目录的绝对路径。 */
    val path: String = "",
    /** 当前目录的**一层**子项。 */
    val entries: List<FileEntry> = emptyList(),
    val filter: String = "",
    /** 目录读不出来时的原因（权限不足 / 不存在）。 */
    val note: String = "",
) {
    /** 按 [filter] 过滤后的可见子项。 */
    val visibleEntries: List<FileEntry> get() {
        val needle = filter.trim()
        if (needle.isEmpty()) return entries
        return entries.filter { it.name.contains(needle, ignoreCase = true) }
    }
}

data class TerminalLine(val text: String, val tone: TerminalTone = TerminalTone.NORMAL)

enum class TerminalTone { NORMAL, DIM, PROMPT, ERROR, SUCCESS }

/** Agent 任务状态。对应原版 `AgentProgressView` 里 running/completed 的分类。 */
/** Agent 任务状态。 */
enum class TaskState { DONE, RUNNING, PENDING }

/** Agent 任务清单里的一条。 */
data class AgentTask(
    val title: String,
    val detail: String = "",
    val state: TaskState = TaskState.PENDING,
)

/** 一条已排队等待执行的预输入消息。[chatItemId] 是它在对话流里那条用户气泡的 id。 */
data class QueuedPrompt(val chatItemId: String, val text: String)

data class WorkspaceUiState(
    val projectName: String = "home",
    val projectPath: String = com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH,
    val sessions: List<SessionSummary> = emptyList(),
    val activeSessionId: String = "",
    val transcript: List<ChatItem> = emptyList(),
    /** 对话流上面还有没有**没读进来**的更早内容。 */
    val transcriptHasOlder: Boolean = false,
    /** 上一页的上界：本页最早一行的 seq。翻页时用它，<= 0 表示没有更早的内容。 */
    val transcriptOldestSeq: Long = -1L,
    /** 正在补更早的一页。 */
    val transcriptLoadingOlder: Boolean = false,
    /** 正在**首次**加载这条会话的消息。 */
    val transcriptLoading: Boolean = false,
    val composerTextEpoch: Int = 0,
    /** `@` 文件补全的查询串（null = 没在补全）。 */
    val fileQuery: String? = null,
    /** `@` 补全的候选路径。 */
    val fileQueryMatches: List<String> = emptyList(),
    /** 对话流的**多选模式**。 */
    val multiSelectMode: Boolean = false,
    /** 多选模式下已勾选的消息 id。 */
    val multiSelectedIds: Set<String> = emptySet(),
    /** 导出面板内容（null = 关闭）。 */
    val exportSheet: ExportSheetState? = null,
    /** 待落盘的导出内容。 */
    val pendingExport: ExportPayload? = null,
    /** 往前补过多少页。 */
    val transcriptPrependRevision: Int = 0,
    /** 最近一次"往前补"**插入了几条**。 */
    val transcriptPrependCount: Int = 0,
    val transcriptHasNewer: Boolean = false,
    val transcriptJumpRevision: Int = 0,
    val transcriptRevision: Int = 0,
    val tab: WorkspaceTab = WorkspaceTab.CHAT,
    val composerText: String = "",
    val composerBusy: Boolean = false,
    /** 停止指令**已经发出**、但还没收到引擎的回合结束确认。 */
    val stopping: Boolean = false,
    val pendingInputs: List<QueuedPrompt> = emptyList(),
    val workingStatus: String? = null,
    /** 当前会话的 Agent 任务清单；悬浮卡只显示「当前窗口」，点开可看全部。 */
    val tasks: List<AgentTask> = emptyList(),
    /** 任务详情窗口是否打开。 */
    val taskListOpen: Boolean = false,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val effort: EffortLevel = EffortLevel.HIGH,
    val availableEfforts: List<EffortLevel> = EffortLevel.DEFAULT_AVAILABLE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeSettings: com.zhizhu.zhicode.compose.data.ThemeSettings =
        com.zhizhu.zhicode.compose.data.ThemeSettings(),
    val profileName: String = "未配置",
    /** 当前 API 配置是否已有密钥。顶栏据此提示"还不能用"，避免发出去才发现报错。 */
    val apiKeyConfigured: Boolean = false,
    val modelLabel: String = "未设置",
    val modelProfileId: String? = null,
    val contextTokens: Int = 0,
    val contextWindow: Int = 200_000,
    val deviceStatus: String = "",
    /** 内置 Termux 运行环境是否已安装就绪。由 [RuntimeInstaller.isInstalled] 真实探测，不再是常量。 */
    val runtimeReady: Boolean = false,
    /** 正在解压 / 配置内置 Termux 环境。 */
    val runtimeInstalling: Boolean = false,
    /** 初始化进度 0~100。 */
    val runtimeProgress: Int = 0,
    /** 初始化过程中的当前步骤文案。 */
    val runtimeMessage: String = "",
    /** 已注册的侧栏工作区目录清单。 */
    val workspaces: List<WorkspaceEntry> = emptyList(),
    /** 侧栏工作区排序方式。 */
    val sidebarOrderBy: SidebarOrderBy = SidebarOrderBy.UPDATED,
    /** 非空即「环境自检」弹窗打开。 */
    val environmentOpen: Boolean = false,
    /** 环境自检报告全文（可复制）。 */
    val environmentReport: String = "",
    val sidebarOpen: Boolean = false,
    /** 设置整页是否打开（像 miuix 示例的 SettingsPage：Scaffold+顶栏，非弹窗）。 */
    val settingsOpen: Boolean = false,
    val slashQuery: String? = null,
    val slashMatches: List<SlashCommand> = emptyList(),
    val permissionRequest: PermissionRequest? = null,
    val planApproval: PlanApproval? = null,
    val choicePicker: ChoicePickerState? = null,
    val copySheet: CopySheetState? = null,
    val attachments: List<Attachment> = emptyList(),
    val terminalLines: List<TerminalLine> = emptyList(),
    val filePath: String = "",
    val fileEntries: List<FileEntry> = emptyList(),
    /** 文件列表为空时要显示的说明。 */
    val fileNote: String = "",
    val fileListOptions: FileListOptions = FileListOptions(),
    /** 文件面板的根：项目 / HOME / 共享存储。 */
    val fileRoot: FileRoot = FileRoot.HOME,
    /** 待打开的编辑器目标（绝对路径）。 */
    val pendingEditorOpen: String? = null,
    /** 非空即「新建 / 重命名」表单打开。 */
    val fileNameForm: FileNameForm? = null,
    /** 非空即删除确认打开。 */
    val fileDeletePrompt: FileDeletePrompt? = null,
    val fileSelection: Set<String> = emptySet(),
    val fileSelectionMode: Boolean = false,
    /** 小米式文件操作暂存区：路径列表 + 是否在粘贴成功后删除源。 */
    val fileClipboard: List<String> = emptyList(),
    val fileClipboardMove: Boolean = false,
    /** 共享存储当前是否给过「所有文件访问权限」。界面据此提示怎么开。 */
    val sharedStorageGranted: Boolean = false,
    val attachPickerOpen: Boolean = false,
    /** 选择器的浏览位置与这一层的子项。 */
    val attachBrowser: AttachBrowserState = AttachBrowserState(),
    val message: String? = null,
    val messageIsError: Boolean = false,
    val busySessionIds: Set<String> = emptySet(),
    /** 设置页新增的持久化项（见 [AppSettings]）。 */
    val settings: AppSettings = AppSettings(),
    /** 非空即 API 配置窗口打开（列表或编辑表单）。 */
    val apiConfig: com.zhizhu.zhicode.compose.model.ApiConfigState? = null,
    val mcpConfig: com.zhizhu.zhicode.compose.model.McpConfigState? = null,
    /** 非空即「搜索服务」整页打开（RikkaHub 形态：多服务列表 + 当前生效项）。 */
    val searchServices: com.zhizhu.zhicode.compose.model.SearchServicesState? = null,
    val skills: com.zhizhu.zhicode.compose.model.SkillsState? = null,
    val roleCards: com.zhizhu.zhicode.compose.model.RoleCardsState? = null,
    val memory: com.zhizhu.zhicode.compose.model.MemoryState? = null,
    /** 提示词设置页是否打开。 */
    val promptsOpen: Boolean = false,
    /** 主题设置整页是否打开。 */
    val themePageOpen: Boolean = false,
    /** 「聊天显示」页（自定义字体 + 正文字号）是否打开。 */
    val chatDisplayOpen: Boolean = false,
    /** 关于页 / 第三方许可页。 */
    val aboutOpen: Boolean = false,
    val licensesOpen: Boolean = false,
    /** 隐私政策整页打开。入口在关于页里，与「第三方开源项目」并列。 */
    val privacyOpen: Boolean = false,
    /** 「检查更新」整页是否打开。 */
    val updatePageOpen: Boolean = false,
    /** 检查更新的状态。 */
    val update: UpdateUiState = UpdateUiState(),
    val modelPicker: com.zhizhu.zhicode.compose.model.ModelPickerState? = null,
    val modelThinkingPicker: com.zhizhu.zhicode.compose.model.ModelThinkingPickerState? = null,
    /** 非空即「UI 调试」整页打开（**仅 debug 构建**有入口，见设置页的「扩展」组）。 */
    val uiDebugOpen: Boolean = false,
    /** 「UI 调试」样例数据的重置令牌。 */
    val uiDebugResetToken: Int = 0,
    /** **全局调试浮层**是否启用（debug 构建的「UI 调试」页里开关）。 */
    val debugOverlayEnabled: Boolean = false,
    /** 浮层是否展开（false = 贴边窄药丸，只显示几个计数）。 */
    val debugOverlayExpanded: Boolean = false,
    /** **主体调试模式**：把应用主体本身变成调试面板（不另开一页）。 */
    val debugAppMode: Boolean = false,
    /** 打开主体调试模式**之前**生效的那条 API 配置 id。 */
    val debugPreviousProfileId: String = "",
    /** 非空即设置弹窗打开；所有编辑先落在这里，「保存」才写回上面的字段。 */
    val settingsDraft: SettingsDraft? = null,
    val scrollToBottomToken: Long = 0L,
    /** 正在跑的**页面动作**（保存 / 创建 / 删除 / 粘贴 / 测试连接…）的键集合。 */
    val inFlight: Set<String> = emptySet(),
    /** 「设置 → 扩展」组显示的数量摘要（MCP / 技能 / 角色卡）。 */
    val extensions: ExtensionSummary = ExtensionSummary(),
    /** 首次引导（OOBE）整页是否显示。 */
    val oobeOpen: Boolean = false,
    /** 当前 OOBE 步骤。 */
    val oobeStep: com.zhizhu.zhicode.compose.runtime.OobeStep =
        com.zhizhu.zhicode.compose.runtime.OobeStep.PERMISSIONS,
    /** 设备权限与可选增强能力实时快照。 */
    val oobePermissions: com.zhizhu.zhicode.compose.runtime.OobePermissionsSnapshot =
        com.zhizhu.zhicode.compose.runtime.OobePermissionsSnapshot(),
    /** Ubuntu 24.04 云端下载源标识（未选为空；选中后为 tuna / ustc / github）。 */
    val ubuntuMirrorId: String = "",
) {
    val activeSession: SessionSummary?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val contextFraction: Float
        get() = if (contextWindow <= 0) 0f else (contextTokens.toFloat() / contextWindow).coerceIn(0f, 1f)

    /** 某个页面动作是否在跑（见 [ActionBusy]）。 */
    fun isBusy(key: String): Boolean = key in inFlight
}

/** 与上游原版 IQ Code 一致的斜杠命令表（/help 显示，面板按前缀过滤）。 */
val SLASH_COMMANDS: List<SlashCommand> = listOf(
    SlashCommand("/help", "查看全部 ZhiCode 指令"),
    SlashCommand("/compact", "模型语义压缩；可追加摘要侧重点"),
    SlashCommand("/context", "查看或设置上下文窗口，例如 /context 1m"),
    SlashCommand("/clear", "清空当前对话并开始新会话"),
    SlashCommand("/new", "创建一个新的已保存会话"),
    SlashCommand("/resume", "恢复本地已保存的历史会话"),
    SlashCommand("/model", "切换当前模型"),
    SlashCommand("/effort", "设置推理强度"),
    SlashCommand("/permissions", "设置工具调用权限模式"),
    SlashCommand("/root", "Agent Root：on / off / check"),
    SlashCommand("/shizuku", "Agent Shizuku（无 Root 的 shell 身份）：on / off / status"),
    SlashCommand("/keepalive", "强制后台保活：on / off / check"),
    SlashCommand("/mcp", "配置和管理 MCP 服务器；/mcp list · add · enable · disable · remove <名称>"),
    SlashCommand("/web", "联网搜索设置；也可直接输入 /web 搜索词"),
    SlashCommand("/terminal", "打开内置 Ubuntu 24.04 终端"),
    SlashCommand("/sandbox", "打开沙箱：虚拟安卓运行环境，可安装/运行/调试 APK"),
    SlashCommand("/files", "打开项目文件与代码编辑器"),
    SlashCommand("/doctor", "检查 Ubuntu 24.04 / PRoot 运行环境"),
    SlashCommand("/repair", "修复 Ubuntu 容器配置与挂载链接"),
    SlashCommand("/skills", "为下一条任务附加本地 Skill；带名称直接附加，例如 /skills pdf"),
    SlashCommand("/status", "查看模型、项目、上下文、运行时和会话状态"),
    SlashCommand("/stats", "查看当前会话与运行状态"),
    SlashCommand("/usage", "查看当前上下文使用情况"),
    SlashCommand("/copy", "复制最近一条 ZhiCode 回复"),
    SlashCommand("/plan", "进入计划模式；/plan off 退出"),
    SlashCommand("/config", "打开 ZhiCode 设置"),
    SlashCommand("/memory", "打开项目或用户 ZhiCode.md 记忆"),
    SlashCommand("/init", "让 ZhiCode 初始化或完善项目 ZhiCode.md"),
    SlashCommand("/tasks", "查看 Android Agent 的任务与会话文件"),
    SlashCommand("/agents", "管理内置、项目、用户和正在运行的子 Agent"),
    SlashCommand("/cancel", "停止当前正在执行的 Agent 回合"),
    SlashCommand("/runtime", "打开内置 Ubuntu 24.04 运行环境控制"),
)

sealed interface UpdatePhase {
    /** 还没检查过（刚打开页面、还没触发）。 */
    data object Idle : UpdatePhase

    /** 正在请求 GitHub。 */
    data object Checking : UpdatePhase

    /** 检查过了，没有更新的版本。 */
    data object UpToDate : UpdatePhase

    /** 上游还没有发布任何 Release（不是错误，是事实）。 */
    data object NoReleases : UpdatePhase

    /** 有更新的版本，[UpdateUiState.release] 非空。 */
    data object Available : UpdatePhase

    /** 正在下载安装包。 */
    data object Downloading : UpdatePhase

    /** 检查或下载失败，[UpdateUiState.error] 是给用户看的原因。 */
    data object Failed : UpdatePhase
}

/** 更新页 / 关于页共用的更新状态。 */
data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val release: UpdateReleaseInfo? = null,
    val error: String? = null,
    val downloadPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val hasChecked: Boolean = false,
)

/** 界面上要用的一条 release。 */
data class UpdateReleaseInfo(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val body: String,
    val prerelease: Boolean,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long,
)
