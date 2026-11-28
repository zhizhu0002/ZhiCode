package com.zhizhu.zhicode.compose.model



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

/** 按筛选词过滤服务器（名称 / 命令 / 地址 / 传输方式都参与）。 */
fun List<McpServer>.filterByQuery(query: String): List<McpServer> {
    if (query.isBlank()) return this
    return filter {
        it.name.contains(query, ignoreCase = true) ||
            it.command.contains(query, ignoreCase = true) ||
            it.url.contains(query, ignoreCase = true) ||
            it.type.label.contains(query, ignoreCase = true)
    }
}

/** 新增 / 编辑 MCP 服务器的草稿。 */
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
    /** 打开表单那一刻的字段指纹（默认等于当前值 = 干净）。 */
    val savedFingerprint: String = fingerprintOf(name, type, command, argsText, url, envText, headersText, scope, enabled),
) {
    val isEditing: Boolean get() = originalName != null

    /** 与打开时相比有改动。 */
    val dirty: Boolean
        get() = fingerprintOf(name, type, command, argsText, url, envText, headersText, scope, enabled) != savedFingerprint

    /** 一行「连到哪」的摘要：stdio 显示命令，HTTP/SSE 显示地址。 */
    val endpointLabel: String
        get() = if (type.needsCommand) {
            buildString {
                // 命令为空时**不再拼参数**：没有可执行文件，`-y xxx` 这一串
                // 只会让人以为已经配好了。
                if (command.isBlank()) {
                    append("未填命令")
                } else {
                    append(command)
                    if (args.isNotEmpty()) append(" ").append(args.joinToString(" "))
                }
            }
        } else {
            url.ifBlank { "未填地址" }
        }

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
        /** 字段指纹。 */
        fun fingerprintOf(
            name: String,
            type: McpType,
            command: String,
            argsText: String,
            url: String,
            envText: String,
            headersText: String,
            scope: McpScope,
            enabled: Boolean,
        ): String = listOf(
            name, type.value, command, argsText, url, envText, headersText, scope.value, enabled.toString(),
        ).joinToString("\u0000")

        /** JSON 校验。 */
        fun jsonError(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return runCatching { org.json.JSONObject(trimmed) }
                .fold(onSuccess = { null }, onFailure = { "JSON 格式错误：${it.message ?: "无法解析"}" })
        }
    }
}
