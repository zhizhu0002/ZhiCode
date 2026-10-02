package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpImportResult
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpType
import com.termux.app.zhicode.storage.McpConfigStore as EngineStore
import org.json.JSONObject
import java.util.Locale

/**
 * MCP 配置的读写适配层。
 *
 * 引擎侧的 [EngineStore] 已经是完整的持久化实现（原子写入：先写 `.tmp` 再 rename），
 * 而且 `McpRuntime` 真的会去启这些服务器、`McpTool` 已经注册进 `ToolRegistry`，
 * 所以这里**不重新发明存储**，只做两件事：
 *
 * 1. 把 Java 的 `Server` 翻成界面模型（`type`/`scope` 是字符串，界面用枚举）；
 * 2. 在写入前做校验——引擎的 `Server` 字段全是可变的 public 字段，没有校验入口，
 *    而"stdio 没填命令"这种配置存进去只会在运行时才炸，那时错误信息离用户很远。
 */
internal object McpStore {

    fun read(): McpConfigState {
        val store = EngineStore()
        val servers = runCatching { store.load() }.getOrDefault(emptyList())
        return McpConfigState(
            servers = servers.map { it.toUi() },
            filePath = runCatching { store.file.absolutePath }.getOrDefault(""),
        )
    }

    /**
     * 保存（新增或按 [McpServerDraft.originalName] 覆盖）。
     *
     * 同名冲突的判定范围与原版一致：
     * - 新增时任何同名都拒绝；
     * - 编辑时忽略自己那一条，改个大小写不算冲突。
     */
    fun save(draft: McpServerDraft): Result<Unit> = runCatching {
        val store = EngineStore()
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "服务器名称不能为空" }
        if (draft.type.needsCommand) require(draft.command.isNotBlank()) { "stdio 服务器必须填写启动命令" }
        else require(draft.url.isNotBlank()) { "HTTP/SSE 服务器必须填写 URL" }
        require(draft.saveable) { draft.nameError ?: draft.commandError ?: draft.urlError ?: draft.envError ?: draft.headersError ?: "配置有误" }

        val all = store.load()
        val original = draft.originalName
        if (all.any { it.name.equals(name, ignoreCase = true) && !it.name.equals(original, ignoreCase = true) }) {
            throw IllegalArgumentException("已存在同名 MCP 服务器：$name")
        }
        val out = EngineStore.Server().also {
            it.name = name
            it.type = draft.type.value
            it.command = draft.command.trim()
            it.url = draft.url.trim()
            // 引擎侧 args 是可变 List，所以先清掉再逐条加，避免原地追加。
            it.args.clear()
            it.args.addAll(draft.args)
            it.env = parse(draft.envText)
            it.headers = parse(draft.headersText)
            it.scope = draft.scope.value
            it.enabled = draft.enabled
        }
        val index = original?.let { old -> all.indexOfFirst { it.name.equals(old, ignoreCase = true) } } ?: -1
        if (index >= 0) all[index] = out else all.add(out)
        store.save(all)
        Unit
    }

    fun setEnabled(name: String, enabled: Boolean): Result<Unit> = runCatching {
        val store = EngineStore()
        val all = store.load()
        val target = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("找不到 MCP 服务器：$name")
        target.enabled = enabled
        store.save(all)
        Unit
    }

    fun delete(name: String): Result<Unit> = runCatching {
        val store = EngineStore()
        val all = store.load()
        val remaining = all.filterNot { it.name.equals(name, ignoreCase = true) }
        if (remaining.size == all.size) throw IllegalArgumentException("找不到 MCP 服务器：$name")
        store.save(remaining)
        Unit
    }

    /**
     * 写入某一个工具的「启用 / 需要审批」。
     *
     * 走「读 → 改那一条 → 整份写回」的既有流程，而不是自己拼 JSON：
     * `McpConfigStore.save` 是原子写，绕开它就可能写出半份配置。
     *
     * ⚠️ 找到服务器之后必须显式 `setToolOptions`，不能让界面传一个"整份工具设置"
     * 覆盖过去：界面上的清单是**服务器答的**，而这里存的是**用户改过的**，
     * 用服务端清单整份覆盖会把用户之前对别的工具的设置抹掉。
     */
    fun setToolOptions(name: String, toolName: String, enabled: Boolean, approval: Boolean): Result<Unit> =
        runCatching {
            val store = EngineStore()
            val all = store.load()
            val server = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException("找不到 MCP 服务器：$name")
            server.setToolOptions(toolName, enabled, approval)
            store.save(all)
            Unit
        }

    /**
     * 从一段 JSON 导入服务器配置（Claude Desktop / 常见 MCP 文档的格式）。
     *
     * ```json
     * { "mcpServers": { "名字": { "url": "…", "headers": {…} },
     *                   "另一个": { "command": "…", "args": ["…"], "env": {…} } } }
     * ```
     *
     * ## 两条判定
     *
     * 1. **同名跳过，不覆盖**：用户可能已经在本机改过那份配置，
     *    静默覆盖等于丢数据（与技能页 zip 导入同一原则）。返回跳过了哪些名字，
     *    让界面能说清"哪个没进来"。
     * 2. **认不出的一律拒绝**，不做"猜一个默认值"：既没有 `url` 也没有 `command`
     *    的条目导入进来也跑不起来，那会在运行时才炸，错误信息离用户很远。
     *
     * 解析与落盘分开：判定逻辑全在 [parseImport] 里（纯函数、有单测），
     * 这里只负责把它算出来的条目写进配置。
     */
    fun importJson(text: String): Result<McpImportResult> = runCatching {
        val store = EngineStore()
        val all = store.load()
        val parsed = parseImport(text, all.map { it.name })
        if (parsed.added.isNotEmpty()) {
            all.addAll(parsed.added)
            store.save(all)
        }
        McpImportResult(
            added = parsed.added.map { it.name },
            skipped = parsed.skipped,
            invalid = parsed.invalid,
        )
    }

    /** [parseImport] 的结果。 */
    data class ParsedImport(
        val added: List<EngineStore.Server>,
        val skipped: List<String>,
        val invalid: List<String>,
    )

    /**
     * 解析导入用的 JSON，**不碰磁盘**。
     *
     * 已存在判定用 [existingNames]（不区分大小写）；同时也在本批内部去重，
     * 否则同一份 JSON 里的大小写变体会被加进去两次。
     *
     * 传输类型的判定与参考实现一致：显式写 `"type": "sse"` 才用 SSE，
     * 其余都按 Streamable HTTP —— 现在新写的服务器基本不填 `type`。
     * 有 `url` 才谈传输方式；只有 `command` 的一定是 stdio。
     */
    fun parseImport(text: String, existingNames: List<String>): ParsedImport {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "请粘贴 JSON 配置" }
        val root = runCatching { JSONObject(trimmed) }
            .getOrElse { throw IllegalArgumentException("不是合法的 JSON：${it.message ?: "无法解析"}") }
        val servers = root.optJSONObject("mcpServers")
            ?: throw IllegalArgumentException("找不到 mcpServers 字段")
        if (servers.length() == 0) throw IllegalArgumentException("mcpServers 里没有任何服务器")

        val seen = existingNames.map { it.lowercase(Locale.US) }.toMutableSet()
        val added = mutableListOf<EngineStore.Server>()
        val skipped = mutableListOf<String>()
        val invalid = mutableListOf<String>()

        servers.keys().asSequence().sorted().forEach { name ->
            val entry = servers.optJSONObject(name)
            val url = entry?.optString("url", "").orEmpty().trim()
            val command = entry?.optString("command", "").orEmpty().trim()
            when {
                name.isBlank() -> invalid += "(空名字)"
                !seen.add(name.lowercase(Locale.US)) -> skipped += name
                // 两者都没有 = 导入了也跑不起来。宁可现在报出来，不要留给运行时。
                url.isEmpty() && command.isEmpty() -> invalid += name
                else -> {
                    val server = EngineStore.Server().also {
                        it.name = name
                        it.type = if (url.isNotEmpty()) {
                            entry.optString("type", "http").trim().ifEmpty { "http" }
                        } else {
                            "stdio"
                        }
                        it.url = url
                        it.command = command
                        it.args.clear()
                        entry.optJSONArray("args")?.let { args ->
                            for (i in 0 until args.length()) it.args.add(args.optString(i, ""))
                        }
                        it.env = entry.optJSONObject("env") ?: JSONObject()
                        it.headers = entry.optJSONObject("headers") ?: JSONObject()
                        it.scope = entry.optString("scope", "user").trim().ifEmpty { "user" }
                        it.enabled = entry.optBoolean("enabled", true)
                    }
                    added += server
                }
            }
        }
        return ParsedImport(added = added, skipped = skipped, invalid = invalid)
    }

/** 从列表条目生成编辑草稿。参数重新拼成每行一个，与环境变量/请求头一样都是原始文本。 */
fun draftOf(server: McpServer, args: List<String>, env: String, headers: String): McpServerDraft = McpServerDraft(
    originalName = server.name,
    name = server.name,
    type = server.type,
    command = server.command,
    argsText = args.joinToString("\n"),
    url = server.url,
    envText = env,
    headersText = headers,
    scope = server.scope,
    enabled = server.enabled,
)

/** 列表条目不带 args/env/headers 的原文（那些体积可能很大），编辑时需要回读。 */
fun rawOf(name: String): Triple<List<String>, String, String>? {
    val server = runCatching { EngineStore().find(name) }.getOrNull() ?: return null
    return Triple(server.args.toList(), pretty(server.env), pretty(server.headers))
}

// ------------------------------------------------------------------

private fun parse(text: String): JSONObject {
    val trimmed = text.trim()
    // 空文本表示"不设置"。这里必须显式返回空对象，而不是 null：
    // 引擎的 toJson 会在 env 为 null 时兜底，但 find/load 回来仍然会变成空对象，
    // 索性在写入端就统一成空对象，省得两种空值形态到处传播。
    if (trimmed.isEmpty()) return JSONObject()
    return JSONObject(trimmed)
}

private fun pretty(value: JSONObject?): String =
    if (value == null || value.length() == 0) "" else value.toString(2)

private fun EngineStore.Server.toUi(): McpServer = McpServer(
    name = name,
    type = McpType.entries.firstOrNull { it.value == type } ?: McpType.STDIO,
    command = command,
    url = url,
    argCount = args.size,
    scope = McpScope.entries.firstOrNull { it.value == scope } ?: McpScope.USER,
    enabled = enabled,
)
}
