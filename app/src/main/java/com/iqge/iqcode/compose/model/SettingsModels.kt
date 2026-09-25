package com.iqge.iqcode.compose.model

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
    /** 参考图卡片第二行的 `openai-responses · gpt-5.6-sol` 形式。 */
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

/** 首次进入时的示例记录，对齐参考图的「IQ Code 官方 API / openai-responses · gpt-5.6-sol」。 */
val DEFAULT_API_PROFILES: List<ApiProfile> = listOf(
    ApiProfile(
        id = "api-official",
        name = "IQ Code 官方 API",
        protocol = ApiProtocol.OPENAI_RESPONSES,
        baseUrl = "https://api.iqcode.dev/v1",
        apiKey = "",
        model = "gpt-5.6-sol",
    ),
)

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
    /** API 配置记录（列表 + 当前选用）。 */
    val apiProfiles: List<ApiProfile> = DEFAULT_API_PROFILES,
    val activeProfileId: String = DEFAULT_API_PROFILES.first().id,
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
