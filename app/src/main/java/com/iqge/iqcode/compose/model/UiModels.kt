package com.iqge.iqcode.compose.model

/** 对话流中的条目类型。对应原 IQ Code 的 ChatItem 分类。 */
enum class ChatKind { USER, ASSISTANT, TOOL_GROUP, ERROR, INFO }

/** 工具类别，用于聚合卡的文案（"搜索 N 个模式 / 读取 N 个文件"）。 */
enum class ToolKind { SEARCH, READ, EDIT, COMMAND, OTHER }

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
    val expanded: Boolean = false,
    val awaitingPermission: Boolean = false,
    val kind: ToolKind = ToolKind.OTHER,
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
    val groupLabel: String = "",
    val groupCompleted: Boolean = false,
    val contextTokens: Int = -1,
    val contextWindow: Int = 0,
    val streaming: Boolean = false,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val project: String,
    val messageCount: Int,
    val updatedAtLabel: String,
    val busy: Boolean = false,
    val note: String = "",
)

enum class WorkspaceTab(val label: String) {
    CHAT("对话"), CHANGES("变更"), TERMINAL("终端"), FILES("文件")
}

enum class PermissionMode(val label: String, val detail: String) {
    ASK("每次询问", "每个工具调用都需要你确认"),
    ACCEPT_EDITS("自动编辑", "自动允许文件编辑，其他调用仍需确认"),
    PLAN("规划", "先产出计划，批准后再执行"),
    AUTO("自动", "由 IQ 判断哪些调用需要确认"),
    DONT_ASK("不询问", "不再弹出确认，高风险操作仍会提示"),
    BYPASS("跳过权限", "跳过全部权限检查，仅限受信环境"),
}

enum class EffortLevel(val label: String) {
    LOW("低"), MEDIUM("中"), HIGH("高"), MAX("最高"), AUTO("自动")
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class SlashCommand(val name: String, val hint: String)

data class Attachment(val id: String, val label: String, val detail: String, val isImage: Boolean)

data class PermissionRequest(
    val id: String,
    val tool: String,
    val subtitle: String,
    val detail: String,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
)

/**
 * 计划审批的内容。对应原版"提交计划"窗口。
 *
 * [body] 是 **Markdown**，由 `ui/Markdown.kt` 渲染（标题 / 粗体 / 行内代码 / 列表 / 代码块）。
 */
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

/**
 * 通用选择器的内容。对应原版"选择"窗口。
 *
 * 与旧版的区别：多了 [prompt]（弹窗顶部的加粗提问）与 [allowFreeForm]
 * （允许在选项之外写一段自由回答）。
 *
 * [multiSelect] / [submitLabel] / [cancelLabel] 是为「提问」门控加的：
 * 引擎的 `AskUserQuestion` 支持多选，并且在多问题时会分步展示，
 * 按钮文案要变成「下一步」而不是「提交」。这些字段有默认值，
 * 因此权限/推理/模型这些单选选择器完全不受影响。
 */
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
)

enum class ChoiceIntent {
    GENERIC,
    PERMISSION_MODE,
    EFFORT,
    MODEL,
    MESSAGE_ACTION,
    SESSION_ACTION,
    ATTACH,

    /** 计划模式的目标澄清：选完（或自由回答）后才产出计划。 */
    PLAN_GOAL,

    /** 引擎 `AskUserQuestion` 发起的分步提问。 */
    QUESTION,
}

data class DiffFile(
    val name: String,
    val additions: Int,
    val deletions: Int,
    val diff: String,
)

data class DiffState(
    val files: List<DiffFile> = emptyList(),
    val loading: Boolean = false,
) {
    val additions: Int get() = files.sumOf { it.additions }
    val deletions: Int get() = files.sumOf { it.deletions }
}

data class FileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long = 0L,
)

data class OpenFile(
    val name: String,
    val path: String,
    val language: String,
    val content: String,
)

data class TerminalLine(val text: String, val tone: TerminalTone = TerminalTone.NORMAL)

enum class TerminalTone { NORMAL, DIM, PROMPT, ERROR, SUCCESS }

/** Agent 任务状态。对应原版 `AgentProgressView` 里 running/completed 的分类。 */
/**
 * Agent 任务状态。
 *
 * 以前这里带一个 `glyph: String`（`✓ ◐ ○`），UI 直接把它当文字画出来。
 * 那种做法依赖字体有没有这些字符，缺字就是方块；而且颜色与粗细无法统一。
 * 现在由 UI 层按状态选 Miuix 图标 / 进度指示器，模型层只表达语义。
 */
enum class TaskState { DONE, RUNNING, PENDING }

/**
 * Agent 任务清单里的一条。[detail] 是 **Markdown**，由 `ui/Markdown.kt` 渲染
 * （任务详情窗口里支持标题/粗体/行内代码/列表/代码块）。
 */
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
    val tab: WorkspaceTab = WorkspaceTab.CHAT,
    val composerText: String = "",
    val composerBusy: Boolean = false,
    /**
     * 预输入**队列**（复刻原版 `engine.steerPrompt` 的排队语义）。
     *
     * 任务运行中按发送键时：用户气泡**立刻**进对话流，内容追加到队尾，
     * [workingStatus] 改成「已预输入，等待当前回复完成…」；
     * 当前回合结束后依次执行队列里的每一条（原版 `handleQueuedPromptApplied`）。
     *
     * ⚠️ 必须是队列而不是单个槽位：连着发多条时，单槽位会把前一条覆盖掉（漏消息）。
     */
    val pendingInputs: List<QueuedPrompt> = emptyList(),
    val workingStatus: String? = null,
    /** 当前会话的 Agent 任务清单；悬浮卡只显示「当前窗口」，点开可看全部。 */
    val tasks: List<AgentTask> = emptyList(),
    /** 任务详情窗口是否打开。 */
    val taskListOpen: Boolean = false,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val effort: EffortLevel = EffortLevel.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val profileName: String = "官方 API",
    val modelLabel: String = "IQ-Code-2.0-preview",
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
    /** 非空即「环境自检」弹窗打开。 */
    val environmentOpen: Boolean = false,
    /** 环境自检报告全文（可复制）。 */
    val environmentReport: String = "",
    val sidebarOpen: Boolean = false,
    val slashQuery: String? = null,
    val slashMatches: List<SlashCommand> = emptyList(),
    val permissionRequest: PermissionRequest? = null,
    val planApproval: PlanApproval? = null,
    val choicePicker: ChoicePickerState? = null,
    val attachments: List<Attachment> = emptyList(),
    val diff: DiffState = DiffState(),
    val terminalLines: List<TerminalLine> = emptyList(),
    val filePath: String = "",
    val fileEntries: List<FileEntry> = emptyList(),
    val openFile: OpenFile? = null,
    val message: String? = null,
    val busySessionIds: Set<String> = emptySet(),
    /** 设置页新增的持久化项（见 [AppSettings]）。 */
    val settings: AppSettings = AppSettings(),
    /** 非空即设置弹窗打开；所有编辑先落在这里，「保存」才写回上面的字段。 */
    val settingsDraft: SettingsDraft? = null,
) {
    val activeSession: SessionSummary?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val contextFraction: Float
        get() = if (contextWindow <= 0) 0f else (contextTokens.toFloat() / contextWindow).coerceIn(0f, 1f)
}

/** 与原 IQ Code 一致的斜杠命令表（/help 显示，面板按前缀过滤）。 */
val SLASH_COMMANDS: List<SlashCommand> = listOf(
    SlashCommand("/help", "查看全部 IQ Code Android 指令"),
    SlashCommand("/compact", "模型语义压缩；可追加摘要侧重点"),
    SlashCommand("/context", "查看或设置上下文窗口，例如 /context 1m"),
    SlashCommand("/clear", "清空当前对话并开始新会话"),
    SlashCommand("/new", "创建一个新的已保存会话"),
    SlashCommand("/resume", "恢复本地已保存的历史会话"),
    SlashCommand("/model", "切换当前模型"),
    SlashCommand("/effort", "设置推理强度"),
    SlashCommand("/permissions", "设置工具调用权限模式"),
    SlashCommand("/root", "Agent Root：on / off / check"),
    SlashCommand("/keepalive", "强制后台保活：on / off / check"),
    SlashCommand("/mcp", "配置和管理 MCP 服务器"),
    SlashCommand("/web", "联网搜索设置；也可直接输入 /web 搜索词"),
    SlashCommand("/terminal", "打开内置 Termux 终端"),
    SlashCommand("/sandbox", "打开 IQ 沙箱；Agent 可安装、运行和调试虚拟 APK"),
    SlashCommand("/diff", "打开 Claude Code 风格代码修改 Diff"),
    SlashCommand("/changes", "打开 Git 变更与 Diff"),
    SlashCommand("/files", "打开项目文件与代码编辑器"),
    SlashCommand("/doctor", "检查 Termux 运行环境"),
    SlashCommand("/repair", "修复中断的 apt/dpkg 状态"),
    SlashCommand("/skills", "为下一条任务附加本地 Skill"),
    SlashCommand("/status", "查看模型、项目、上下文、运行时和会话状态"),
    SlashCommand("/stats", "查看当前会话与运行状态"),
    SlashCommand("/usage", "查看当前上下文使用情况"),
    SlashCommand("/copy", "复制最近一条 IQ 回复"),
    SlashCommand("/plan", "进入计划模式；/plan off 退出"),
    SlashCommand("/config", "打开 IQ Code 设置"),
    SlashCommand("/canvas", "打开运行时 UI 画布自定义"),
    SlashCommand("/memory", "打开项目或用户 IQ.md 记忆"),
    SlashCommand("/init", "让 IQ 初始化或完善项目 IQ.md"),
    SlashCommand("/tasks", "查看 Android Agent 的任务与会话文件"),
    SlashCommand("/agents", "管理内置、项目、用户和正在运行的子 Agent"),
    SlashCommand("/cancel", "停止当前正在执行的 Agent 回合"),
    SlashCommand("/runtime", "打开内置 Termux 运行环境控制"),
)
