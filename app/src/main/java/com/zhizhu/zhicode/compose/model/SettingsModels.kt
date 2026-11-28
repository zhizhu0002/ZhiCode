package com.zhizhu.zhicode.compose.model

import com.zhizhu.zhicode.pi.PiApiFormat
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.PiProviderPreset
import com.zhizhu.zhicode.pi.sanitizePiProviderKey

import com.termux.app.zhicode.storage.ApiSettingsStore

/** 设置页的 6 个分类，对齐原版 `MainActivity.showSettings()` 的分类 Tab。 */
enum class SettingsCategory(val label: String, val tabLabel: String) {
    APPEARANCE("外观", "外观"),
    MODEL_PERMISSION("模型与权限", "模型"),
    AGENT_SECURITY("Agent 与安全", "安全"),
    NETWORK("联网", "联网"),
    CONTEXT_PROJECT("上下文与项目", "上下文"),
    EXTENSIONS("扩展功能", "扩展"),
}

/** 搜索服务（形态参考 RikkaHub 的「搜索服务」页：免费后端与密钥制后端并列 */
/** 搜索服务配置里的字段名（**持久化契约**：改一个名字等于丢掉用户已填的配置）。 */
object SearchFieldName {
    const val API_KEY = "apiKey"
    const val BASE_URL = "baseUrl"
    const val DEPTH = "depth"
    const val TOPIC = "topic"
    const val MODEL = "model"
    const val LANGUAGE = "language"
    const val SUMMARY = "summary"
    const val URL_TEMPLATE = "urlTemplate"
    const val HEADERS = "headers"
    const val JSON_ITEMS_PATH = "itemsPath"
    const val JSON_TITLE_PATH = "titlePath"
    const val JSON_URL_PATH = "urlPath"
    const val JSON_TEXT_PATH = "textPath"
}

enum class SearchServiceType(
    val label: String,
    val detail: String,
    /** 申请 Key 的页面；免密钥服务为 null。 */
    val keyUrl: String? = null,
    /** 这个类型需要的输入字段（除「名称」之外）。 */
    val fields: List<SearchField> = emptyList(),
) {
    DUCKDUCKGO("DuckDuckGo", "免费公开入口，无需密钥；偶尔会限流"),
    BING("Bing RSS", "免费走 Bing 的 RSS 结果，无需密钥"),
    SEARXNG(
        "SearXNG",
        "自建元搜索，实例需开启 JSON 输出（settings.yml 里 format=json）",
        fields = listOf(
            SearchField(SearchFieldName.BASE_URL, "实例地址", "如 https://searx.example.com", required = true),
            SearchField(SearchFieldName.LANGUAGE, "语言", "如 zh-CN / en（留空为实例默认）"),
        ),
    ),
    TAVILY(
        "Tavily",
        "为 LLM 优化的 AI 搜索，支持检索深度与主题",
        keyUrl = "https://tavily.com",
        fields = listOf(
            SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true),
            SearchField(SearchFieldName.DEPTH, "检索深度", "basic / advanced（advanced 更慢更贵但更全）", options = listOf("basic", "advanced")),
            SearchField(SearchFieldName.TOPIC, "主题", "general / news / finance", options = listOf("general", "news", "finance")),
        ),
    ),
    EXA(
        "Exa",
        "面向 AI 的语义搜索",
        keyUrl = "https://exa.ai",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),
    BRAVE(
        "Brave",
        "Brave Search API，独立索引",
        keyUrl = "https://brave.com/search/api/",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),
    PERPLEXITY(
        "Perplexity",
        "搜索增强模型，能直接给出带引用的回答",
        keyUrl = "https://www.perplexity.ai/settings/api",
        fields = listOf(
            SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true),
            SearchField(SearchFieldName.MODEL, "模型", "如 sonar / sonar-pro（留空用 sonar）"),
        ),
    ),
    LINKUP(
        "LinkUp",
        "可配检索深度的搜索 API",
        keyUrl = "https://www.linkup.so",
        fields = listOf(
            SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true),
            SearchField(SearchFieldName.DEPTH, "检索深度", "standard / deep", options = listOf("standard", "deep")),
        ),
    ),
    JINA(
        "Jina",
        "Jina Reader 的检索接口（s.jina.ai）",
        keyUrl = "https://jina.ai/reader/",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),
    FIRECRAWL(
        "Firecrawl",
        "抓取型服务，返回结构化正文",
        keyUrl = "https://firecrawl.dev",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),
    BOCHA(
        "博查 Bocha",
        "中文搜索服务，可选 AI 摘要",
        keyUrl = "https://open.bochaai.com",
        fields = listOf(
            SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true),
            SearchField(SearchFieldName.SUMMARY, "AI 摘要", "true / false", options = listOf("false", "true")),
        ),
    ),
    METASO(
        "秘塔 Metaso",
        "中文 AI 搜索",
        keyUrl = "https://metaso.cn",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),
    ZHIPU(
        "智谱 Zhipu",
        "智谱的联网搜索工具",
        keyUrl = "https://open.bigmodel.cn",
        fields = listOf(SearchField(SearchFieldName.API_KEY, "API Key", required = true, secret = true)),
    ),

    /** 自定义 HTTP 检索。 */
    CUSTOM_HTTP(
        "自定义 HTTP",
        "自己填检索地址与结果字段名；%s 处会被替换成查询词",
        fields = listOf(
            SearchField(SearchFieldName.URL_TEMPLATE, "检索地址模板", "如 https://my.api/search?q=%s&n=%d", required = true),
            SearchField(SearchFieldName.HEADERS, "请求头 JSON", "如 {\"Authorization\":\"Bearer xxx\"}"),
            SearchField(SearchFieldName.JSON_ITEMS_PATH, "结果数组路径", "如 data.items（点号分隔；留空视为根数组）"),
            SearchField(SearchFieldName.JSON_TITLE_PATH, "标题字段", "默认 title"),
            SearchField(SearchFieldName.JSON_URL_PATH, "链接字段", "默认 url"),
            SearchField(SearchFieldName.JSON_TEXT_PATH, "摘要字段", "默认 text / content / snippet 依次尝试"),
        ),
    ),
    ;

    /** 是否需要密钥（决定编辑页要不要渲染 Key 输入框）。 */
    val needsKey: Boolean get() = fields.any { it.name == SearchFieldName.API_KEY && it.required }

}

/** 搜索服务的一个输入字段。见 [SearchServiceType.fields]。 */
data class SearchField(
    val name: String,
    val label: String,
    val hint: String = "",
    val required: Boolean = false,
    /** 非空即「只能从这几个值里选」，界面用下拉而不是文本框。 */
    val options: List<String> = emptyList(),
    /** 敏感值（Key）：走加密存储，读回时**不回填**到表单。 */
    val secret: Boolean = false,
)

/** 一条搜索服务配置（RikkaHub 的「已添加的服务」一条）。 */
data class SearchService(
    val id: String,
    val name: String,
    val type: SearchServiceType,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap(),
) {
    /** 列表第二行：类型名 + 关键配置摘要。 */
    val subtitle: String
        get() = buildString {
            append(type.label)
            config[SearchFieldName.BASE_URL]?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            config[SearchFieldName.MODEL]?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
}

/** 搜索服务编辑表单草稿。 */
data class SearchServiceDraft(
    val id: String?,
    val name: String,
    val type: SearchServiceType,
    val enabled: Boolean,
    val config: Map<String, String>,
    /** 用户这次输入的密钥；编辑既有记录时留空 = 沿用原密钥。 */
    val apiKey: String = "",
) {
    val isEditing: Boolean get() = !id.isNullOrBlank()

    val nameError: String? get() = if (name.isBlank()) "请填写名称（用于在列表里区分）" else null

    /** 缺必填字段时要指出**是哪一项**，而不是只说「配置有误」。 */
    val fieldError: String? get() = type.fields
        .firstOrNull { it.required && it.name != SearchFieldName.API_KEY && config[it.name].isNullOrBlank() }
        ?.let { "请填写${it.label}" }

    val keyError: String?
        get() = if (type.needsKey && !isEditing && apiKey.isBlank()) "请填写 ${type.label} 的 API Key" else null

    val saveable: Boolean get() = nameError == null && fieldError == null && keyError == null

    companion object {
        fun from(service: SearchService) = SearchServiceDraft(
            id = service.id,
            name = service.name,
            type = service.type,
            enabled = service.enabled,
            config = service.config,
            // 密钥**不**回填：界面上不该出现已保存的密钥（与 API 配置同一条规矩）。
            apiKey = "",
        )

        fun blank() = SearchServiceDraft(
            id = null,
            name = "",
            type = SearchServiceType.TAVILY,
            enabled = true,
            config = emptyMap(),
        )
    }
}

/** 搜索服务页的状态。[form] 非空即编辑页，否则列表页。 */
data class SearchServicesState(
    val services: List<SearchService> = emptyList(),
    val activeId: String = "",
    val form: SearchServiceDraft? = null,
    /** 载荷还没读完。 */
    val loading: Boolean = false,
)

/** 联网搜索后端（历史类型）。 */
enum class WebSearchProvider(
    val label: String,
    val detail: String,
    val needsKey: Boolean = false,
    val needsBaseUrl: Boolean = false,
) {
    AUTO("自动（免费）", "按顺序尝试 DuckDuckGo → Bing"),
    DUCKDUCKGO("DuckDuckGo", "免费，无需密钥"),
    BING("Bing RSS", "免费，无需密钥"),
    TAVILY("Tavily", "为 LLM 优化的 AI 搜索，Key 从 tavily.com 获取", needsKey = true),
    EXA("Exa", "面向 AI 的语义搜索，Key 从 exa.ai 获取", needsKey = true),
    BRAVE("Brave", "Brave Search API，Key 从 brave.com/search/api 获取", needsKey = true),
    SEARXNG("SearXNG", "自建元搜索，填实例地址（需开 JSON 输出）", needsBaseUrl = true),
}

/** API 协议。对应参考图表单里「协议」值框的 `OpenAI Responses`。 */
enum class ApiProtocol(val label: String) {
    OPENAI_RESPONSES("OpenAI Responses"),
    OPENAI_CHAT("OpenAI Chat Completions"),
    ANTHROPIC("Anthropic Messages"),
    GOOGLE_GENERATIVE_AI("Google Generative AI"),
    BEDROCK_CONVERSE_STREAM("Amazon Bedrock"),
    MISTRAL_CONVERSATIONS("Mistral"),
    CODEX_RESPONSES("Codex Responses"),
    ZCODE("ZCode"),
    DEBUG_SCRIPTED("调试 · 本地模拟（无网络）"),
    ;

    /** 是否只在 debug 构建里可选。 */
    val debugOnly: Boolean get() = this == DEBUG_SCRIPTED

    fun toPiFormat(): PiApiFormat = when (this) {
        OPENAI_RESPONSES -> PiApiFormat.OPENAI_RESPONSES
        OPENAI_CHAT -> PiApiFormat.OPENAI_COMPLETIONS
        ANTHROPIC -> PiApiFormat.ANTHROPIC_MESSAGES
        GOOGLE_GENERATIVE_AI -> PiApiFormat.GOOGLE_GENERATIVE_AI
        BEDROCK_CONVERSE_STREAM -> PiApiFormat.BEDROCK_CONVERSE_STREAM
        MISTRAL_CONVERSATIONS -> PiApiFormat.MISTRAL_CONVERSATIONS
        CODEX_RESPONSES -> PiApiFormat.OPENAI_CODEX_RESPONSES
        ZCODE -> PiApiFormat.ZCODE
        DEBUG_SCRIPTED -> PiApiFormat.DEBUG_SCRIPTED
    }
}

/** 一条 API 供应商配置记录（对齐 Pi 多供应商与多模型结构）。 */
data class ApiProfile(
    val id: String,
    val name: String,
    val protocol: ApiProtocol,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val extraHeaders: String = "",
    val providerKey: String = "",
    val compatJson: String = "",
    val models: List<PiModelConfig> = emptyList(),
) {
    val summary: String get() = "${protocol.label} · ${model.ifBlank { models.firstOrNull()?.id ?: "未设置模型" }}"

    fun effectiveProviderKey(fallbackIndex: Int = 0): String =
        sanitizePiProviderKey(providerKey.ifBlank { name }, "provider-${fallbackIndex + 1}")

    fun effectiveModels(): List<PiModelConfig> {
        if (models.isNotEmpty()) return models
        val m = model.trim()
        return if (m.isEmpty()) emptyList() else listOf(PiModelConfig(id = m, name = m))
    }

    fun resolveModel(selectedModelId: String? = null): PiModelConfig? {
        val target = selectedModelId?.trim()?.takeIf { it.isNotEmpty() } ?: model.trim()
        val list = effectiveModels()
        if (target.isEmpty()) return list.firstOrNull()
        return list.firstOrNull { it.id == target }
    }
}

/** 新增 / 编辑供应商表单的草稿。 */
data class ApiProfileDraft(
    val id: String?,
    val presetId: String = "custom",
    val pickingPreset: Boolean = false,
    val providerKey: String = "",
    val name: String = "",
    val protocol: ApiProtocol = ApiProtocol.OPENAI_CHAT,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val extraHeaders: String = "",
    val compatJson: String = "",
    val models: List<PiModelConfig> = emptyList(),
    /** 编辑已有记录时为真：密钥框留空表示"沿用原密钥"。 */
    val isEditing: Boolean = false,
) {
    val nameError: String? get() = if (name.isBlank()) "请填写名称" else null
    val baseUrlError: String? get() = if (baseUrl.isBlank() && protocol != ApiProtocol.BEDROCK_CONVERSE_STREAM && protocol != ApiProtocol.DEBUG_SCRIPTED) "请填写 API Base URL" else null
    val saveable: Boolean get() = !pickingPreset && nameError == null && baseUrlError == null

    companion object {
        fun blank(): ApiProfileDraft = ApiProfileDraft(id = null, pickingPreset = true)

        fun fromPreset(preset: PiProviderPreset): ApiProfileDraft {
            val proto = when (preset.api) {
                PiApiFormat.OPENAI_RESPONSES -> ApiProtocol.OPENAI_RESPONSES
                PiApiFormat.OPENAI_COMPLETIONS -> ApiProtocol.OPENAI_CHAT
                PiApiFormat.ANTHROPIC_MESSAGES -> ApiProtocol.ANTHROPIC
                PiApiFormat.GOOGLE_GENERATIVE_AI -> ApiProtocol.GOOGLE_GENERATIVE_AI
                PiApiFormat.BEDROCK_CONVERSE_STREAM -> ApiProtocol.BEDROCK_CONVERSE_STREAM
                PiApiFormat.MISTRAL_CONVERSATIONS -> ApiProtocol.MISTRAL_CONVERSATIONS
                PiApiFormat.OPENAI_CODEX_RESPONSES -> ApiProtocol.CODEX_RESPONSES
                PiApiFormat.ZCODE -> ApiProtocol.ZCODE
                PiApiFormat.DEBUG_SCRIPTED -> ApiProtocol.DEBUG_SCRIPTED
            }
            return ApiProfileDraft(
                id = null,
                presetId = preset.id,
                pickingPreset = false,
                providerKey = if (preset.isCustom) "" else preset.providerKey,
                name = if (preset.isCustom) "" else preset.name,
                protocol = proto,
                baseUrl = preset.baseUrl,
                extraHeaders = preset.extraHeaders,
                compatJson = preset.compatJson,
                models = preset.models,
                model = preset.models.firstOrNull()?.id.orEmpty(),
            )
        }

        /** 编辑已有记录：**故意不回填 apiKey**。 */
        fun from(profile: ApiProfile): ApiProfileDraft = ApiProfileDraft(
            id = profile.id,
            presetId = "custom",
            pickingPreset = false,
            providerKey = profile.providerKey,
            name = profile.name,
            protocol = profile.protocol,
            baseUrl = profile.baseUrl,
            apiKey = "",
            model = profile.model,
            extraHeaders = profile.extraHeaders,
            compatJson = profile.compatJson,
            models = profile.effectiveModels(),
            isEditing = true,
        )
    }
}

/** API 配置页的状态（含已整合的上下文压缩与提示词入口）。 */
data class ApiConfigState(
    val profiles: List<ApiProfile>,
    val activeId: String,
    val form: ApiProfileDraft? = null,
    val loading: Boolean = false,
    val standalone: Boolean = false,
    val autoCompact: Boolean = true,
    val autoCompactPercent: Int = 60,
    val customSystemPrompt: String = "",
)

/** 模型目录里的一项。[displayName] 可能与 [id] 相同，界面据此决定要不要重复显示。 */
data class ModelOption(val id: String, val displayName: String)

/** 模型选择面板里的一行套餐额度。 */
data class QuotaRow(
    val name: String,
    val remaining: String,
    val total: String,
    /** 剩余比例 0..1，用于进度条。 */
    val fraction: Float,
    val resetLabel: String,
)

/** 「测试连接」的结果：一台服务器 + 它提供的工具。 */
data class McpServerStatus(
    val name: String,
    val connected: Boolean,
    /** 失败原因（`connected` 为 true 时是空串）。 */
    val error: String,
    val tools: List<McpToolInfo> = emptyList(),
)

/** 服务器提供的一个工具。 */
data class McpToolInfo(
    val name: String,
    val description: String,
    /** JSON Schema 里的属性名，用来提示这个工具要哪些参数。 */
    val parameters: List<String> = emptyList(),
    /** 需要参数的子集（渲染时用不同颜色标出来）。 */
    val required: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val approval: Boolean = false,
) {
    companion object {
        /** 从引擎 `listServers()` 里的一项解析。字段缺失一律用安全的缺省值。 */
        fun parse(tools: org.json.JSONArray?): List<McpToolInfo> = buildList {
            if (tools == null) return@buildList
            for (index in 0 until tools.length()) {
                val tool = tools.optJSONObject(index) ?: continue
                val name = tool.optString("name", "")
                if (name.isEmpty()) continue
                val schema = tool.optJSONObject("inputSchema")
                val properties = schema?.optJSONObject("properties")
                val required = schema?.optJSONArray("required")
                add(
                    McpToolInfo(
                        name = name,
                        description = tool.optString("description", ""),
                        parameters = properties?.keys()?.asSequence()?.toList().orEmpty(),
                        required = buildSet {
                            if (required == null) return@buildSet
                            for (i in 0 until required.length()) {
                                required.optString(i, "").takeIf { it.isNotEmpty() }?.let { add(it) }
                            }
                        },
                        enabled = tool.optBoolean("enabled", true),
                        approval = tool.optBoolean("approval", false),
                    ),
                )
            }
        }
    }
}

/** 从 JSON 导入的结果。[added] 真正加进去的，[skipped] 同名跳过的，[invalid] 认不出的。 */
data class McpImportResult(
    val added: List<String>,
    val skipped: List<String>,
    val invalid: List<String>,
)

/** MCP 配置页状态：与 API 配置同样共用一个弹窗，`form` 非空即为表单页。 */
data class McpConfigState(
    val servers: List<McpServer>,
    val filePath: String,
    val form: McpServerDraft? = null,
    /** 「测试连接」的结果，按服务器名索引。 */
    val status: Map<String, McpServerStatus> = emptyMap(),
    /** 正在测试的服务器名（空集表示没有在测）。 */
    val testing: Set<String> = emptySet(),
    /** 「从 JSON 导入」对话框的文本。`null` = 对话框没开。 */
    val importText: String? = null,
    /** 导入对话框的错误提示（解析失败时才有）。 */
    val importError: String? = null,
    /** 载荷还没读完，见 `WorkspaceViewModel.openMcpConfig`。 */
    val loading: Boolean = false,
    /** 列表页的筛选词（名称 / 命令 / 地址都参与匹配）。 */
    val query: String = "",
) {
    /** 按 [query] 过滤后的服务器。 */
    val visibleServers: List<McpServer>
        get() {
            if (query.isBlank()) return servers
            return servers.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.command.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true) ||
                    it.type.label.contains(query, ignoreCase = true)
            }
        }

    /** 启用中的台数（列表页顶部汇总用）。 */
    val enabledCount: Int get() = servers.count { it.enabled }
}

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

/** 技能目录里的一个文件。 */
data class SkillFile(
    val name: String,
    val relativePath: String,
    val sizeLabel: String,
    /** 是否是技能本体（`SKILL.md`）。列表里排最前，并用不同颜色标出来。 */
    val primary: Boolean,
)

sealed interface SkillFileNode {
    /** 一个文件。 */
    data class FileNode(val file: SkillFile) : SkillFileNode

    /** 一个子目录，[children] 是它下面的内容（递归）。 */
    data class DirNode(
        val name: String,
        val relativePath: String,
        val children: List<SkillFileNode>,
    ) : SkillFileNode
}

/** Skill 管理面板的状态。 */
data class SkillsState(
    val skills: List<SkillEntry>,
    /** 列表页的筛选词。技能会越攒越多，找起来需要一个入口。 */
    val query: String = "",
    /** 正在查看的技能（详情页）。 */
    val detail: SkillDetail? = null,
    val createForm: SkillCreateDraft? = null,
    val editing: SkillEditTarget? = null,
    /** 「新建文件」表单，只在详情页里有意义。 */
    val fileDraft: SkillFileDraft? = null,
    /** 「从 URL 导入」表单。 */
    val urlDraft: SkillUrlDraft? = null,
    /** 载荷还没读完，见 `WorkspaceViewModel.openSkills`。 */
    val loading: Boolean = false,
) {
    /** 按 [query] 过滤后的列表。名称与说明都命中。 */
    val visibleSkills: List<SkillEntry>
        get() = if (query.isBlank()) skills
        else skills.filter {
            it.name.contains(query, ignoreCase = true) || it.summary.contains(query, ignoreCase = true)
        }
}

/** 技能详情页的数据：技能本身 + 它目录下的文件树。 */
data class SkillDetail(
    val entry: SkillEntry,
    val tree: List<SkillFileNode>,
    /** `SKILL.md` 的正文（打开详情时读好的那份）。 */
    val body: String = "",
)

/** 「手动添加技能」的表单（**对话框**，不是整页）。 */
data class SkillCreateDraft(
    val content: String = "",
    val scope: SkillScope = SkillScope.PROJECT,
    /** 由 `SkillStore.nameFromContent` 从 [content] 的 frontmatter 解析得出。 */
    val name: String = "",
) {
    /** 内容非空但解析不出名字。空内容不算错（用户还没开始粘贴）。 */
    val nameMissing: Boolean get() = content.isNotBlank() && name.isBlank()

    /** 解析出来了、但字符集不合法（见 `SkillStore.isValidName`）。 */
    val nameInvalid: Boolean get() = name.isNotBlank() && !Regex("[A-Za-z0-9._-]{1,64}").matches(name)

    val saveable: Boolean get() = name.isNotBlank() && !nameInvalid
}

/** 「新建文件」表单（详情页里往技能目录加一个附加文件）。 */
data class SkillFileDraft(
    val fileName: String = "",
    val content: String = "",
) {
    /** 拆成每一段；末段的判断与 `SkillStore.isValidFileName` 保持一致。 */
    private val segments: List<String> get() = fileName.trim().split('/')

    val nameError: String? = when {
        fileName.isBlank() -> null // 还没开始输入
        fileName.startsWith("/") || fileName.endsWith("/") -> "路径不能以 / 开头或结尾"
        segments.any { it.isEmpty() } -> "路径里不能有连续的 //"
        segments.any { it == "." || it == ".." } -> "路径里不能有 . 或 .. 这样的段"
        segments.any { !FILE_NAME_SEGMENT.matches(it) } -> "每一段只能包含字母、数字、. _ -（1–64 个字符）"
        // SKILL.md 是技能本体，走「编辑」改它；用「新建文件」建它会走名字一致性校验
        // （空内容没有 frontmatter 的 name），到时候报的错离用户很远。
        fileName.trim().equals("SKILL.md", ignoreCase = true) -> "SKILL.md 是技能本体，请用「编辑」改它"
        else -> null
    }

    val saveable: Boolean get() = fileName.isNotBlank() && nameError == null

    private companion object {
        val FILE_NAME_SEGMENT = Regex("[A-Za-z0-9._-]{1,64}")
    }
}

/** 「从 URL 导入」表单。 */
data class SkillUrlDraft(
    val url: String = "",
    val scope: SkillScope = SkillScope.PROJECT,
    /** 下载中。这段时间要禁用「导入」，否则连点会并发拉好几份。 */
    val loading: Boolean = false,
) {
    /** 只挡明显不是地址的输入；真正的合法性由下载那一步判（并给出具体原因）。 */
    val ready: Boolean
        get() {
            val text = url.trim().lowercase(java.util.Locale.US)
            return !loading && (text.startsWith("http://") || text.startsWith("https://")) &&
                text.length > "https://".length
        }
}

/** 正在编辑的文件（**对话框**，不是整页）。 */
data class SkillEditTarget(
    val name: String,
    val scope: SkillScope,
    /** 技能目录里的相对路径，通常是 `SKILL.md`，也可以是 `reference/api.md`。 */
    val relativePath: String,
    /** 编辑中的内容。保存前只在内存里，不落盘。 */
    val body: String,
    /** 写 `SKILL.md` 时的名字冲突原因（null = 没问题）。 */
    val nameError: String? = null,
    /** 打开编辑器时磁盘上的那份正文（默认等于 [body] = 干净）。 */
    val savedBody: String = body,
) {
    /** 内容长度提示用。空内容不算错误，只提示一句。 */
    val empty: Boolean get() = body.isBlank()

    /** 有过未保存的改动（返回 / 关闭前需要确认）。 */
    val dirty: Boolean get() = body != savedBody

    val fileName: String get() = relativePath.substringAfterLast('/')

    val saveable: Boolean get() = nameError == null
}

/** 一张角色卡。 */
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
    /** 载荷还没读完，见 `WorkspaceViewModel.openRoleCards`。 */
    val loading: Boolean = false,
)

/** 新增 / 编辑角色卡的草稿。[id] 为空表示新增。 */
data class RoleCardEditor(
    val id: String = "",
    val name: String = "",
    val content: String = "",
    /** 打开编辑器时那一份**已保存**的名字与内容（默认等于当前值 = 干净）。 */
    val savedName: String = name,
    val savedContent: String = content,
) {
    val isEditing: Boolean get() = id.isNotBlank()

    /** 与打开时相比有过改动（返回前需要确认）。 */
    val dirty: Boolean get() = name != savedName || content != savedContent

    /** 名称是唯一必填项：内容留空是允许的（等于临时停用这张卡的效果）。 */
    val nameError: String? get() = if (name.isBlank()) "请填写角色名称" else null
    val saveable: Boolean get() = nameError == null
}

/** 记忆文件的作用域。路径约定见 `MemoryStore`（必须与 `/init` 指令的目标一致）。 */
enum class MemoryScope(val label: String) {
    PROJECT("项目 ZhiCode.md"),
    USER("用户 ZhiCode.md"),
}

/** 一个记忆文件在列表里的展示信息。 */
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
    /** 载荷还没读完，见 `WorkspaceViewModel.openMemory`。 */
    val loading: Boolean = false,
)

/** 正在编辑的记忆文件。新建一个尚不存在的文件时 [exists] 为 false。 */
data class MemoryEditor(
    val scope: MemoryScope,
    val path: String,
    val body: String,
    val exists: Boolean,
    /** 打开时磁盘上的正文（默认等于 [body] = 干净）。见 [dirty]。 */
    val savedBody: String = body,
) {
    val title: String get() = if (exists) "编辑 ${scope.label}" else "新建 ${scope.label}"
    val empty: Boolean get() = body.isBlank()

    /** 有过未保存的改动（返回 / 关闭前需要确认）。 */
    val dirty: Boolean get() = body != savedBody
}




/** 模型选择面板的状态。 */
data class ModelPickerState(
    val profileName: String,
    val currentModel: String,
    val query: String = "",
    val loading: Boolean = false,
    val status: String = "",
    val models: List<ModelOption> = emptyList(),
    val quota: List<QuotaRow> = emptyList(),
    val quotaError: String = "",
    val modelsNote: String = "",
    val profiles: List<ModelProfileTab> = emptyList(),
    val activeProfileId: String = "",
) {
    val activeProfileIndex: Int
        get() = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)

    /** 是否该显示切换 tab 栏：只有一条时它是死控件（切不了任何东西）。 */
    val showProfileTabs: Boolean get() = profiles.size > 1
}

/** 供应商切换与本地模型展示列表项（不含密钥）。 */
data class ModelProfileTab(
    val id: String,
    val name: String,
    val protocolLabel: String = "",
    val modelLabel: String = "",
    val models: List<PiModelConfig> = emptyList(),
)

/** 对话底栏「本地模型 + 推理二级菜单」状态（纯本地读取，不发起网络请求）。 */
data class ModelThinkingPickerState(
    val providerId: String,
    val providerName: String,
    val models: List<PiModelConfig>,
    val currentModelId: String,
    val currentEffort: EffortLevel,
    /** 非空即正在展示该模型的二级推理档位子菜单；null 显示一级本地模型列表。 */
    val expandedModel: PiModelConfig? = null,
)

/** 首次进入时的 API 配置列表：**空**。 */
val DEFAULT_API_PROFILES: List<ApiProfile> = emptyList()

data class AppSettings(
    val visionEnabled: Boolean = true,
    val rootExecutionEnabled: Boolean = false,
    /** Shizuku 工具开关：以 shell 身份（uid 2000）跑 pm/am/dumpsys 这类系统命令。与 Root 独立。 */
    val shizukuExecutionEnabled: Boolean = false,
    val forcedKeepAliveEnabled: Boolean = false,
    /** 主引擎可同时运行的子 Agent 上限；范围 1–8。 */
    val maxConcurrentSubagents: Int = DEFAULT_MAX_CONCURRENT_SUBAGENTS,
    /** 终端强制字符模式输入（默认关：走正常软件输入法）。见 ApiSettingsStore。 */
    val terminalCharMode: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val webSearchProvider: WebSearchProvider = WebSearchProvider.AUTO,
    val webSearchMaxResults: Int = 5,
    val webSearchTimeoutSec: Int = 15,
    /** 密钥制搜索服务的 Key，按服务各存一份；切换服务不丢。 */
    val webSearchKeys: Map<WebSearchProvider, String> = emptyMap(),
    /** SearXNG 实例地址（如 https://searx.example.com），仅 SearXNG 服务用到。 */
    val webSearchSearxngUrl: String = "",
    val autoCompact: Boolean = true,
    /** 自动压缩上限，取值 50..100（百分比）。 */
    val autoCompactPercent: Int = 60,
    val customSystemPrompt: String = "",
    /** 可自定义的提示词槽位，三份都遵循「空串 = 用默认」（见 [PromptSlot]）。 */
    val initPrompt: String = "",
    val summaryPrompt: String = "",
    val compactPromptHead: String = "",
    /** API 配置记录（列表 + 当前选用）。列表可以为空 = 还没有配过。 */
    val apiProfiles: List<ApiProfile> = DEFAULT_API_PROFILES,
    val activeProfileId: String = "",
)

const val DEFAULT_MAX_CONCURRENT_SUBAGENTS = 4
const val MIN_CONCURRENT_SUBAGENTS = 1
const val MAX_CONCURRENT_SUBAGENTS = 8
fun clampConcurrentSubagents(value: Int): Int = value.coerceIn(MIN_CONCURRENT_SUBAGENTS, MAX_CONCURRENT_SUBAGENTS)

/** 结果数合法区间。原版为 1–10；应用户要求放宽到 50（密钥制服务扛得住这个量）。 */
const val WEB_RESULTS_MIN = 1
const val WEB_RESULTS_MAX = 50

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

/** 解析上下文窗口输入，支持原版的 `128k` / `200k` / `1m` / `1.5m` 写法。 */
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

/** 设置弹窗的编辑草稿。 */
data class SettingsDraft(
    val themeMode: ThemeMode,
    val permissionMode: PermissionMode,
    val effort: EffortLevel,
    val contextWindow: Int,
    val projectPath: String,
    val customSystemPrompt: String,
    /** 可自定义的提示词槽位（空串 = 用默认，见 [PromptSlot]）。 */
    val initPrompt: String,
    val summaryPrompt: String,
    val compactPromptHead: String,
    /** 只读展示用：当前 API 记录名 + 模型名（对应参考图的「deepseek · openai-chat · deepseek-flash」）。 */
    val profileName: String,
    val modelLabel: String,
    val visionEnabled: Boolean,
    val rootExecutionEnabled: Boolean,
    val forcedKeepAliveEnabled: Boolean,
    val maxConcurrentSubagents: Int,
    val terminalCharMode: Boolean,
    val webSearchEnabled: Boolean,
    val webSearchProvider: WebSearchProvider,
    val shizukuExecutionEnabled: Boolean,
    val webSearchMaxResults: Int,
    val webSearchTimeoutSec: Int,
    val webSearchKeys: Map<WebSearchProvider, String>,
    val webSearchSearxngUrl: String,
    val autoCompact: Boolean,
    val autoCompactPercent: Int,
    /** 当前模型可用推理档位。 */
    val availableEfforts: List<EffortLevel> = EffortLevel.DEFAULT_AVAILABLE,
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
                availableEfforts = state.availableEfforts,
                contextWindow = state.contextWindow,
                projectPath = state.projectPath,
                customSystemPrompt = s.customSystemPrompt,
                initPrompt = s.initPrompt,
                summaryPrompt = s.summaryPrompt,
                compactPromptHead = s.compactPromptHead,
                profileName = state.profileName,
                modelLabel = state.modelLabel,
                visionEnabled = s.visionEnabled,
                rootExecutionEnabled = s.rootExecutionEnabled,
                shizukuExecutionEnabled = s.shizukuExecutionEnabled,
                forcedKeepAliveEnabled = s.forcedKeepAliveEnabled,
                maxConcurrentSubagents = clampConcurrentSubagents(s.maxConcurrentSubagents),
                terminalCharMode = s.terminalCharMode,
                webSearchEnabled = s.webSearchEnabled,
                webSearchProvider = s.webSearchProvider,
                webSearchMaxResults = s.webSearchMaxResults,
                webSearchTimeoutSec = s.webSearchTimeoutSec,
                webSearchKeys = s.webSearchKeys,
                webSearchSearxngUrl = s.webSearchSearxngUrl,
                autoCompact = s.autoCompact,
                autoCompactPercent = s.autoCompactPercent,
            )
        }
    }

    /** 保存时写回 state。数值字段在这里统一 clamp，UI 层不需要各自校验。 */
    /** 把 draft 写回 state。 */
    fun applyTo(state: WorkspaceUiState, keepDraft: Boolean = false): WorkspaceUiState = state.copy(
        themeMode = themeMode,
        // 深浅是 `themeSettings.mode` 的缓存值（见 WorkspaceUiState.themeSettings 的说明）
        themeSettings = state.themeSettings.copy(mode = themeMode),
        permissionMode = permissionMode,
        effort = effort,
        contextWindow = contextWindow,
        projectPath = projectPath.ifBlank { state.projectPath },
        settings = AppSettings(
            visionEnabled = visionEnabled,
            rootExecutionEnabled = rootExecutionEnabled,
            shizukuExecutionEnabled = shizukuExecutionEnabled,
            forcedKeepAliveEnabled = forcedKeepAliveEnabled,
            maxConcurrentSubagents = clampConcurrentSubagents(maxConcurrentSubagents),
            terminalCharMode = terminalCharMode,
            webSearchEnabled = webSearchEnabled,
            webSearchProvider = webSearchProvider,
            webSearchMaxResults = clampWebResults(webSearchMaxResults),
            webSearchTimeoutSec = clampWebTimeoutSec(webSearchTimeoutSec),
            webSearchKeys = webSearchKeys,
            webSearchSearxngUrl = webSearchSearxngUrl.trim(),
            autoCompact = autoCompact,
            autoCompactPercent = clampCompactPercent(autoCompactPercent),
            customSystemPrompt = customSystemPrompt,
            initPrompt = initPrompt,
            summaryPrompt = summaryPrompt,
            compactPromptHead = compactPromptHead,
        ),
        settingsDraft = if (keepDraft) this else null,
    )
}

data class ExtensionSummary(
    val mcpTotal: Int = 0,
    val mcpEnabled: Int = 0,
    /** 已测过且连上的台数；0 表示还没测过任何一台（不是"全挂了"）。 */
    val mcpConnected: Int = 0,
    val skillTotal: Int = 0,
    val projectSkills: Int = 0,
    val userSkills: Int = 0,
    val roleCardTotal: Int = 0,
    /** 是否已经统计过一次（false 时界面显示"正在统计"而不是 0）。 */
    val loaded: Boolean = false,
)
