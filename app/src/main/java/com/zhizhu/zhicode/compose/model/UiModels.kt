package com.zhizhu.zhicode.compose.model

/** 对话流中的条目类型。对应原 蜘蛛 的 ChatItem 分类。 */
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
    AUTO("自动", "由智蛛判断哪些调用需要确认"),
    DONT_ASK("不询问", "不再弹出确认，高风险操作仍会提示"),
    BYPASS("跳过权限", "跳过全部权限检查，仅限受信环境"),
}

enum class EffortLevel(val label: String) {
    LOW("低"), MEDIUM("中"), HIGH("高"), MAX("最高"), AUTO("自动")
}

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class SlashCommand(val name: String, val hint: String)

/**
 * 附件条上的一个条目。
 *
 * 两种来源共用一个模型：
 * - 图片（`isImage = true`）：真正的字节放在 ViewModel 的载荷表里，不进不可变状态；
 *   发送时编成 base64 内容块。
 * - 文本（技能）：[textBody] 直接带内容。技能文件只有几 KB，放状态里代价可忽略，
 *   而它必须能被拼进提示词正文（见 `buildPromptWithTextAttachments`）。
 */
data class Attachment(
    val id: String,
    val label: String,
    val detail: String,
    val isImage: Boolean,
    val textBody: String? = null,
)

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
 * 按钮文案要变成「下一步」而不是「提交」。
 *
 * ⚠️ 权限模式与推理强度**不再走这里**：它们现在是输入器里的下拉菜单
 * （见 `OverlayDropdownPreference`），选中即生效，不需要弹窗。
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
    /**
     * 长按动作菜单要**锚**到哪一项（消息 id / 会话 id）。
     *
     * 只有 [ChoiceIntent.MESSAGE_ACTION] 与 [ChoiceIntent.SESSION_ACTION] 会写它，
     * 其余情形为 null（走居中对话框）。被长按的那一项按 id 认领它，菜单就在
     * 那一项自己的 Box 里弹出（见 `ui/Common.kt` 的 `ZhiAnchoredActionMenu`）——
     * 这样不必做任何坐标换算，也就不会因为滚动/内边距而锚偏。
     */
    val anchorId: String? = null,
) {
    /**
     * 是否按**贴住长按项的下拉菜单**渲染，而不是居中对话框。
     *
     * `anchorId` 也要求非空：万一哪天有调用点忘了传，宁可退回原来的对话框，
     * 也不能出现「菜单不显示、对话框也没有」的空洞。
     */
    val isActionMenu: Boolean
        get() = anchorId != null &&
            (intent == ChoiceIntent.MESSAGE_ACTION || intent == ChoiceIntent.SESSION_ACTION)
}

enum class ChoiceIntent {
    GENERIC,

    /**
     * 权限模式 / 推理强度。
     *
     * ⚠️ 界面上的入口已经是输入器里的**下拉菜单**（`OverlayDropdownPreference`，
     * 见 `setPermissionMode` / `setEffort`），不再走这个选择器。
     * 但 `/permissions`、`/effort` 两个斜杠命令仍然需要"弹一个列表让人挑"，
     * 所以这两条路径保留 —— 删掉就等于把斜杠命令一起废了。
     */
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
    /**
     * 空列表时要显示的说明。
     *
     * 为什么需要这个字段：变更面板的空白态原来写死「工作区没有未提交的变更」，
     * 但 git **失败**时（运行时未安装 / 不是 git 仓库 / 目录不存在）列表同样是空的。
     * 那样界面会一口咬定"没有变更" —— 明明什么都没查到，却给出了确定性结论。
     * 所以把原因带上来，空白态显示真实原因。
     */
    val note: String = "",
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

/**
 * 「附加项目文件」的一条搜索结果。
 *
 * 放在 model 包而不是作为 `FileSearch` 的内部类：它出现在公开的
 * [WorkspaceUiState.attachHits] 里，而 `FileSearch` 是 `internal` 工具对象
 * （与 `FileBrowser` 一样）—— Kotlin 不允许公开类型暴露 internal 类型参数。
 * 这也与 [FileEntry] / [OpenFile] 的位置保持一致。
 */
data class FileHit(
    /** 绝对路径，附加时用它读文件。 */
    val path: String,
    /** 相对项目根的路径。界面显示它更短也更容易认。 */
    val relative: String,
    val size: Long = 0L,
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
    val profileName: String = "未配置",
    /** 当前 API 配置是否已有密钥。顶栏据此提示"还不能用"，避免发出去才发现报错。 */
    val apiKeyConfigured: Boolean = false,
    val modelLabel: String = "未设置",
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
    /**
     * 文件列表为空时要显示的说明。
     *
     * 与 [DiffState.note] 同一个道理：目录**不存在**与目录**真的为空**
     * 在界面上都是"0 项"，但前者是故障、后者是正常。不区分的话，
     * 用户看到一个空的文件面板只会以为应用坏了。
     */
    val fileNote: String = "",
    val openFile: OpenFile? = null,
    /**
     * 非空即「附加项目文件」面板打开（输入器 `+` 的第一项）。
     *
     * 用 [attachHits] 承载搜索结果而不是在 Composable 里现搜：搜目录是 IO，
     * 放 recomposition 里会每个字符都卡一下。
     */
    val attachPickerOpen: Boolean = false,
    /** 附加面板的搜索串。 */
    val attachQuery: String = "",
    /** 附加面板当前的搜索结果（已由 ViewModel 在 IO 线程算好）。 */
    val attachHits: List<FileHit> = emptyList(),
    val message: String? = null,
    val busySessionIds: Set<String> = emptySet(),
    /** 设置页新增的持久化项（见 [AppSettings]）。 */
    val settings: AppSettings = AppSettings(),
    /** 非空即 API 配置窗口打开（列表或编辑表单）。 */
    val apiConfig: com.zhizhu.zhicode.compose.model.ApiConfigState? = null,
    val mcpConfig: com.zhizhu.zhicode.compose.model.McpConfigState? = null,
    val skills: com.zhizhu.zhicode.compose.model.SkillsState? = null,
    val roleCards: com.zhizhu.zhicode.compose.model.RoleCardsState? = null,
    val memory: com.zhizhu.zhicode.compose.model.MemoryState? = null,
    val modelPicker: com.zhizhu.zhicode.compose.model.ModelPickerState? = null,
    /** 非空即设置弹窗打开；所有编辑先落在这里，「保存」才写回上面的字段。 */
    val settingsDraft: SettingsDraft? = null,
) {
    val activeSession: SessionSummary?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val contextFraction: Float
        get() = if (contextWindow <= 0) 0f else (contextTokens.toFloat() / contextWindow).coerceIn(0f, 1f)
}

/** 与原 蜘蛛 一致的斜杠命令表（/help 显示，面板按前缀过滤）。 */
val SLASH_COMMANDS: List<SlashCommand> = listOf(
    SlashCommand("/help", "查看全部智蛛指令"),
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
    SlashCommand("/sandbox", "打开 ZhiCode 沙箱；Agent 可安装、运行和调试虚拟 APK"),
    SlashCommand("/diff", "打开 Claude Code 风格代码修改 Diff"),
    SlashCommand("/changes", "打开 Git 变更与 Diff"),
    SlashCommand("/files", "打开项目文件与代码编辑器"),
    SlashCommand("/doctor", "检查 Termux 运行环境"),
    SlashCommand("/repair", "修复中断的 apt/dpkg 状态"),
    SlashCommand("/skills", "为下一条任务附加本地 Skill"),
    SlashCommand("/status", "查看模型、项目、上下文、运行时和会话状态"),
    SlashCommand("/stats", "查看当前会话与运行状态"),
    SlashCommand("/usage", "查看当前上下文使用情况"),
    SlashCommand("/copy", "复制最近一条智蛛回复"),
    SlashCommand("/plan", "进入计划模式；/plan off 退出"),
    SlashCommand("/config", "打开 ZhiCode 设置"),
    SlashCommand("/canvas", "打开运行时 UI 画布自定义"),
    SlashCommand("/memory", "打开项目或用户 ZhiCode.md 记忆"),
    SlashCommand("/init", "让智蛛初始化或完善项目 ZhiCode.md"),
    SlashCommand("/tasks", "查看 Android Agent 的任务与会话文件"),
    SlashCommand("/agents", "管理内置、项目、用户和正在运行的子 Agent"),
    SlashCommand("/cancel", "停止当前正在执行的 Agent 回合"),
    SlashCommand("/runtime", "打开内置 Termux 运行环境控制"),
)
