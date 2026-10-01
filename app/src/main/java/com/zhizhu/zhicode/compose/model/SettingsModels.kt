package com.zhizhu.zhicode.compose.model

import com.termux.app.zhicode.storage.ApiSettingsStore

/**
 * 设置页的 6 个分类，对齐原版 `MainActivity.showSettings()` 的分类 Tab。
 *
 * 顺序即 Tab 顺序，`entries` 直接拿来渲染，不要在 UI 里另写一份列表。
 */
enum class SettingsCategory(val label: String, val tabLabel: String) {
    APPEARANCE("外观", "外观"),
    MODEL_PERMISSION("模型与权限", "模型"),
    AGENT_SECURITY("Agent 与安全", "安全"),
    NETWORK("联网", "联网"),
    CONTEXT_PROJECT("上下文与项目", "上下文"),
    EXTENSIONS("扩展功能", "扩展"),
}

/** 联网搜索后端。对应原版 `webProviderValues` = {auto, duckduckgo, bing}。 */
enum class WebSearchProvider(val label: String, val detail: String) {
    AUTO("自动", "按顺序尝试 DuckDuckGo → Bing"),
    DUCKDUCKGO("DuckDuckGo", "只走 DuckDuckGo"),
    BING("Bing RSS", "只走 Bing 的 RSS 结果"),
}

/** API 协议。对应参考图表单里「协议」值框的 `OpenAI Responses`。 */
enum class ApiProtocol(val label: String) {
    OPENAI_RESPONSES("OpenAI Responses"),
    OPENAI_CHAT("OpenAI Chat"),
    ANTHROPIC("Anthropic"),

    /**
     * 调试用：不发网络请求，按脚本产出回复（含真实的工具调用）。
     *
     * 只在 debug 构建里出现在协议下拉与「新增配置」的候选里（见
     * [com.zhizhu.zhicode.compose.ui.dialogs.ApiConfigOverlay]）；发布包即使读到
     * 这样一条记录，引擎侧也会明确拒绝（见 `ModelProviders.debugScripted`）。
     */
    DEBUG_SCRIPTED("调试 · 本地模拟（无网络）"),
    ;

    /** 是否只在 debug 构建里可选。 */
    val debugOnly: Boolean get() = this == DEBUG_SCRIPTED
}

/**
 * 一条 API 配置记录。
 *
 * [apiKey] 会被保存，但按原版约定**不回填到编辑表单**（避免密钥在界面上暴露）；
 * 见 [ApiProfileDraft.from]。
 */
data class ApiProfile(
    val id: String,
    val name: String,
    val protocol: ApiProtocol,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    /** 参考图卡片第二行的 `协议名 · 模型名` 形式。 */
    val summary: String get() = "${protocol.label} · ${model.ifBlank { "未设置模型" }}"
}

/** 新增 / 编辑表单的草稿。 */
data class ApiProfileDraft(
    val id: String?,
    val name: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI_RESPONSES,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** 编辑已有记录时为真：密钥框留空表示"沿用原密钥"。 */
    val isEditing: Boolean = false,
) {
    val nameError: String? get() = if (name.isBlank()) "请填写名称" else null
    val baseUrlError: String? get() = if (baseUrl.isBlank()) "请填写 API Base URL" else null
    val saveable: Boolean get() = nameError == null && baseUrlError == null

    companion object {
        fun blank(): ApiProfileDraft = ApiProfileDraft(id = null)

        /** 编辑已有记录：**故意不回填 apiKey**。 */
        fun from(profile: ApiProfile): ApiProfileDraft = ApiProfileDraft(
            id = profile.id,
            name = profile.name,
            protocol = profile.protocol,
            baseUrl = profile.baseUrl,
            apiKey = "",
            model = profile.model,
            isEditing = true,
        )
    }
}

/**
 * API 配置页的状态。
 *
 * [form] 非空即为「新增 / 编辑」表单页，否则显示列表页——两张页面共用一个弹窗，
 * 对应参考图的两张截图。
 */
data class ApiConfigState(
    val profiles: List<ApiProfile>,
    val activeId: String,
    val form: ApiProfileDraft? = null,
)

/** 模型目录里的一项。[displayName] 可能与 [id] 相同，界面据此决定要不要重复显示。 */
data class ModelOption(val id: String, val displayName: String)

/** MCP 服务器的连接方式。取值必须与引擎 `McpConfigStore.Server.type` 一致。 */
enum class McpType(val value: String, val label: String) {
    STDIO("stdio", "标准输入输出（stdio）"),
    HTTP("http", "Streamable HTTP"),
    SSE("sse", "旧版 SSE"),
    ;

    val needsCommand: Boolean get() = this == STDIO
}

/** MCP 服务器的作用范围。 */
enum class McpScope(val value: String, val label: String) {
    USER("user", "用户级（所有项目）"),
    PROJECT("project", "项目级（当前项目）"),
}

/** MCP 配置列表里的一条。[argsText] 是编辑态用的多行文本，展示时不用它。 */
data class McpServer(
    val name: String,
    val type: McpType,
    val command: String,
    val url: String,
    val argCount: Int,
    val scope: McpScope,
    val enabled: Boolean,
) {
    /** 列表行的副标题：让用户一眼看出这是本地进程还是远端地址。 */
    val summary: String
        get() = buildString {
            append(type.label)
            if (type.needsCommand) {
                append(" · ").append(command.ifBlank { "未填命令" })
                if (argCount > 0) append("（").append(argCount).append(" 个参数）")
            } else {
                append(" · ").append(url.ifBlank { "未填地址" })
            }
            append(" · ").append(scope.label)
        }
}

/**
 * 新增 / 编辑 MCP 服务器的草稿。
 *
 * [argsText] / [envText] / [headersText] 都是**原始文本**而不是解析后的结构：
 * 用户正在打字时 JSON 经常是半截的，边打边解析会把人卡死（刚输入 `{` 就报错）。
 * 所以校验只发生在保存时，见 [envError] / [headersError]。
 */
data class McpServerDraft(
    val originalName: String?,
    val name: String = "",
    val type: McpType = McpType.STDIO,
    val command: String = "",
    val argsText: String = "",
    val url: String = "",
    val envText: String = "",
    val headersText: String = "",
    val scope: McpScope = McpScope.USER,
    val enabled: Boolean = true,
) {
    val isEditing: Boolean get() = originalName != null

    val nameError: String? get() = if (name.isBlank()) "请填写服务器名称" else null

    val commandError: String?
        get() = if (type.needsCommand && command.isBlank()) "stdio 服务器必须填写启动命令" else null

    val urlError: String?
        get() = if (!type.needsCommand && url.isBlank()) "HTTP/SSE 服务器必须填写 URL" else null

    val envError: String? get() = jsonError(envText)
    val headersError: String? get() = jsonError(headersText)

    val saveable: Boolean
        get() = nameError == null && commandError == null && urlError == null &&
            envError == null && headersError == null

    /** 每行一个参数，与引擎侧 `List<String> args` 对应。 */
    val args: List<String>
        get() = argsText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /**
         * JSON 校验。
         *
         * 空串是合法的（表示"不设置"）；非空但解析失败才报错，
         * 并把失败原因带出来——只说"格式不对"用户不知道哪不对。
         */
        fun jsonError(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return runCatching { org.json.JSONObject(trimmed) }
                .fold(onSuccess = { null }, onFailure = { "JSON 格式错误：${it.message ?: "无法解析"}" })
        }
    }
}

/** MCP 配置页状态：与 API 配置同样共用一个弹窗，`form` 非空即为表单页。 */
data class McpConfigState(
    val servers: List<McpServer>,
    val filePath: String,
    val form: McpServerDraft? = null,
)

/** Skill 的作用域。目录约定必须与引擎 `SkillTool` 一致，见 `SkillStore`。 */
enum class SkillScope(val label: String) {
    PROJECT("项目级"),
    USER("用户级"),
}

/** 列表里的一条技能。 */
data class SkillEntry(
    val name: String,
    val scope: SkillScope,
    val path: String,
    val summary: String,
    val sizeLabel: String,
)

/**
 * Skill 管理面板的状态。
 *
 * 三种形态共用一个弹窗，靠这两个字段区分：
 * - `createForm != null` → 新建表单（问名称与存放位置）
 * - `editing != null` → 编辑 SKILL.md
 * - 两者都为 null → 列表
 *
 * 用两个字段而不是一个 `mode` 枚举：新建与编辑需要携带的数据完全不同，
 * 塞进一个 sealed 层级只会让调用方多写一层 when。
 */
data class SkillsState(
    val skills: List<SkillEntry>,
    val createForm: SkillCreateDraft? = null,
    val editing: SkillEditTarget? = null,
)

/** 新建技能的表单。 */
data class SkillCreateDraft(
    val name: String = "",
    val scope: SkillScope = SkillScope.PROJECT,
) {
    val nameError: String? = when {
        name.isBlank() -> null // 还没开始输入，不要一上来就报错
        !Regex("[A-Za-z0-9._-]{1,64}").matches(name.trim()) -> "只能包含字母、数字、. _ -（1–64 个字符）"
        else -> null
    }

    val saveable: Boolean get() = name.isNotBlank() && nameError == null
}

/** 正在编辑的技能。 */
data class SkillEditTarget(
    val name: String,
    val scope: SkillScope,
    val path: String,
    /** 编辑中的内容。保存前只在内存里，不落盘。 */
    val body: String,
) {
    /** 内容长度提示用。空内容不算错误，只提示一句。 */
    val empty: Boolean get() = body.isBlank()
}

/**
 * 一张角色卡。
 *
 * 内容会作为 `<role_card>` 块注入系统提示词（见引擎 `SystemPromptBuilder`），
 * 所以它是"长期生效的人设指令"，不是一次性提示词。
 */
data class RoleCard(val id: String, val name: String, val content: String) {
    /** 列表副标题：显示内容摘要，让用户不必点进去就知道卡里写了什么。 */
    val summary: String
        get() {
            val flattened = content.replace('\n', ' ').trim()
            if (flattened.isEmpty()) return "空内容"
            return if (flattened.length <= 48) flattened else flattened.take(48) + "…"
        }
}

/** 角色卡面板状态：`editor != null` 即编辑页，否则列表页。 */
data class RoleCardsState(
    val cards: List<RoleCard>,
    val activeId: String,
    val editor: RoleCardEditor? = null,
)

/** 新增 / 编辑角色卡的草稿。[id] 为空表示新增。 */
data class RoleCardEditor(
    val id: String = "",
    val name: String = "",
    val content: String = "",
) {
    val isEditing: Boolean get() = id.isNotBlank()

    /** 名称是唯一必填项：内容留空是允许的（等于临时停用这张卡的效果）。 */
    val nameError: String? get() = if (name.isBlank()) "请填写角色名称" else null
    val saveable: Boolean get() = nameError == null
}

/** 记忆文件的作用域。路径约定见 `MemoryStore`（必须与 `/init` 指令的目标一致）。 */
enum class MemoryScope(val label: String) {
    PROJECT("项目 ZhiCode.md"),
    USER("用户 ZhiCode.md"),
}

/**
 * 一个记忆文件在列表里的展示信息。
 */
data class MemoryFile(
    val scope: MemoryScope,
    val path: String,
    val exists: Boolean,
    val sizeLabel: String,
)

/** 记忆面板状态：`editing != null` 即编辑器，否则列表。 */
data class MemoryState(
    val files: List<MemoryFile>,
    val editing: MemoryEditor? = null,
)

/** 正在编辑的记忆文件。新建一个尚不存在的文件时 [exists] 为 false。 */
data class MemoryEditor(
    val scope: MemoryScope,
    val path: String,
    val body: String,
    val exists: Boolean,
) {
    val title: String get() = if (exists) "编辑 ${scope.label}" else "新建 ${scope.label}"
    val empty: Boolean get() = body.isBlank()
}




/**
 * 模型选择面板的状态。
 *
 * 原版的这个面板是**异步填充**的：先开着窗显示"正在从当前 API 获取模型…"，
 * 目录回来后再把列表填进去。所以 [loading] / [status] / [models] 必须是状态而不是
 * 一次性参数——网络慢的时候界面得先立起来。
 *
 * [query] 是手动输入框的内容。目录拉不到时用户就靠它兜底，因此它不能只在弹窗内部
 * 存活（比如转屏或重组时被清掉）。
 */
data class ModelPickerState(
    val profileName: String,
    val currentModel: String,
    val query: String = "",
    val loading: Boolean = true,
    val status: String = "正在从当前 API 获取模型…",
    val models: List<ModelOption> = emptyList(),
)

/**
 * 首次进入时的 API 配置列表：**空**。
 *
 * <p>以前这里预置一条厂家的「官方 API」。那个做法已经取消，理由有两个：
 * 一是它等于替某个具体服务做默认入口（并且带推广参数），这该由用户自己选；
 * 二是任何内置地址都会让「请求到底发到哪里」变得不透明。
 *
 * <p>所以应用不再自带任何地址与模型名，全部由用户在设置里新增。
 * 列表允许为空：[AppSettings.apiProfiles] 与 [AppSettings.activeProfileId] 都跟着空，
 * 界面据此显示「还没有 API 配置」，而真正发请求前会由引擎侧拦下来并给出明确错误。
 */
val DEFAULT_API_PROFILES: List<ApiProfile> = emptyList()

/**
 * 原版设置页里**新增的**持久化项。
 *
 * ⚠️ 刻意**不**在这里放 `themeMode` / `permissionMode` / `effort` / `contextWindow`
 * / `projectPath` / `modelLabel` 等字段：它们已经是 `WorkspaceUiState` 的成员，
 * 再放一份就会出现"两份真相"，保存时到底谁覆盖谁很难说清。
 * 这些字段由 [SettingsDraft] 在打开弹窗时快照、保存时写回。
 */
data class AppSettings(
    val visionEnabled: Boolean = true,
    val sandboxAgentFullAccess: Boolean = false,
    val rootExecutionEnabled: Boolean = false,
    val forcedKeepAliveEnabled: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val webSearchProvider: WebSearchProvider = WebSearchProvider.AUTO,
    val webSearchMaxResults: Int = 5,
    val webSearchTimeoutSec: Int = 15,
    val autoCompact: Boolean = true,
    /** 自动压缩上限，取值 50..100（百分比）。 */
    val autoCompactPercent: Int = 80,
    val customSystemPrompt: String = "",
    /** API 配置记录（列表 + 当前选用）。列表可以为空 = 还没有配过。 */
    val apiProfiles: List<ApiProfile> = DEFAULT_API_PROFILES,
    val activeProfileId: String = "",
)

/** 结果数合法区间，原版为 1–10。 */
const val WEB_RESULTS_MIN = 1
const val WEB_RESULTS_MAX = 10

/** 联网超时合法区间（秒），原版为 5000–60000ms。 */
const val WEB_TIMEOUT_MIN_SEC = 5
const val WEB_TIMEOUT_MAX_SEC = 60

/** 自动压缩上限合法区间（百分比）。 */
const val COMPACT_PERCENT_MIN = 50
const val COMPACT_PERCENT_MAX = 100

/** 上下文窗口候选值，对应原版 hint "128k / 200k / 1m / 1.5m"。 */
val CONTEXT_WINDOW_PRESETS: List<Int> = listOf(128_000, 200_000, 1_000_000, 1_500_000)

fun clampWebResults(value: Int): Int = value.coerceIn(WEB_RESULTS_MIN, WEB_RESULTS_MAX)

fun clampWebTimeoutSec(value: Int): Int = value.coerceIn(WEB_TIMEOUT_MIN_SEC, WEB_TIMEOUT_MAX_SEC)

fun clampCompactPercent(value: Int): Int = value.coerceIn(COMPACT_PERCENT_MIN, COMPACT_PERCENT_MAX)

/**
 * 解析上下文窗口输入，支持原版的 `128k` / `200k` / `1m` / `1.5m` 写法。
 * 解析不出来或非正数时返回 null，由调用方决定保留旧值。
 */
fun parseTokenCount(raw: String): Int? {
    val text = raw.trim().lowercase()
    if (text.isEmpty()) return null
    val multiplier = when {
        text.endsWith("m") -> 1_000_000.0
        text.endsWith("k") -> 1_000.0
        else -> 1.0
    }
    val number = text.removeSuffix("m").removeSuffix("k").trim().toDoubleOrNull() ?: return null
    val tokens = (number * multiplier).toInt()
    return if (tokens > 0) tokens else null
}

/** 把 token 数格式化回 `200k` / `1m` / `1.5m` 这种紧凑写法。 */
fun formatTokenCountShort(tokens: Int): String {
    if (tokens <= 0) return "0"
    return when {
        tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}m"
        tokens >= 1_000_000 -> {
            val millions = tokens / 1_000_000.0
            // 去掉多余的小数位：1.5m 而不是 1.50m
            val rounded = String.format(java.util.Locale.US, "%.2f", millions).trimEnd('0').trimEnd('.')
            "${rounded}m"
        }
        tokens % 1_000 == 0 -> "${tokens / 1_000}k"
        else -> tokens.toString()
    }
}

/** 十进制百分比 ↔ 0..1 浮点，供 `SliderPreference` 使用。 */
fun percentToFraction(percent: Int): Float = (percent / 100f).coerceIn(0f, 1f)

fun fractionToPercent(fraction: Float): Int =
    clampCompactPercent(Math.round(fraction * 100f).toInt())

/**
 * 设置弹窗的编辑草稿。
 *
 * **draft 语义**：弹窗打开时用 [from] 从当前 state 快照一份，用户所有改动都落在
 * draft 上；「保存」才用 [applyTo] 写回 state，「取消」直接丢弃 draft。
 * 这样"取消"是真正无副作用的，而不是先改后回滚。
 *
 * 这里同时持有 state 里的既有字段（主题/权限/推理/上下文/项目路径/提示词）
 * 和 [AppSettings] 里的新增字段，是为了让弹窗只读一份数据、不必到处传 state。
 */
data class SettingsDraft(
    val themeMode: ThemeMode,
    val permissionMode: PermissionMode,
    val effort: EffortLevel,
    val contextWindow: Int,
    val projectPath: String,
    val customSystemPrompt: String,
    /** 只读展示用：当前 API 记录名 + 模型名（对应参考图的「deepseek · openai-chat · deepseek-flash」）。 */
    val profileName: String,
    val modelLabel: String,
    val visionEnabled: Boolean,
    val sandboxAgentFullAccess: Boolean,
    val rootExecutionEnabled: Boolean,
    val forcedKeepAliveEnabled: Boolean,
    val webSearchEnabled: Boolean,
    val webSearchProvider: WebSearchProvider,
    val webSearchMaxResults: Int,
    val webSearchTimeoutSec: Int,
    val autoCompact: Boolean,
    val autoCompactPercent: Int,
    /** 当前分类 Tab。放在 draft 里，取消时一并丢弃，重新打开总是回到第一类。 */
    val category: SettingsCategory = SettingsCategory.APPEARANCE,
) {
    companion object {
        fun from(state: WorkspaceUiState): SettingsDraft {
            val s = state.settings
            return SettingsDraft(
                themeMode = state.themeMode,
                permissionMode = state.permissionMode,
                effort = state.effort,
                contextWindow = state.contextWindow,
                projectPath = state.projectPath,
                customSystemPrompt = s.customSystemPrompt,
                profileName = state.profileName,
                modelLabel = state.modelLabel,
                visionEnabled = s.visionEnabled,
                sandboxAgentFullAccess = s.sandboxAgentFullAccess,
                rootExecutionEnabled = s.rootExecutionEnabled,
                forcedKeepAliveEnabled = s.forcedKeepAliveEnabled,
                webSearchEnabled = s.webSearchEnabled,
                webSearchProvider = s.webSearchProvider,
                webSearchMaxResults = s.webSearchMaxResults,
                webSearchTimeoutSec = s.webSearchTimeoutSec,
                autoCompact = s.autoCompact,
                autoCompactPercent = s.autoCompactPercent,
            )
        }
    }

    /** 保存时写回 state。数值字段在这里统一 clamp，UI 层不需要各自校验。 */
    fun applyTo(state: WorkspaceUiState): WorkspaceUiState = state.copy(
        themeMode = themeMode,
        permissionMode = permissionMode,
        effort = effort,
        contextWindow = contextWindow,
        projectPath = projectPath.ifBlank { state.projectPath },
        settings = AppSettings(
            visionEnabled = visionEnabled,
            sandboxAgentFullAccess = sandboxAgentFullAccess,
            rootExecutionEnabled = rootExecutionEnabled,
            forcedKeepAliveEnabled = forcedKeepAliveEnabled,
            webSearchEnabled = webSearchEnabled,
            webSearchProvider = webSearchProvider,
            webSearchMaxResults = clampWebResults(webSearchMaxResults),
            webSearchTimeoutSec = clampWebTimeoutSec(webSearchTimeoutSec),
            autoCompact = autoCompact,
            autoCompactPercent = clampCompactPercent(autoCompactPercent),
            customSystemPrompt = customSystemPrompt,
        ),
        settingsDraft = null,
    )
}
