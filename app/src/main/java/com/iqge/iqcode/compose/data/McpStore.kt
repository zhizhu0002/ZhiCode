package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.McpConfigState
import com.iqge.iqcode.compose.model.McpScope
import com.iqge.iqcode.compose.model.McpServer
import com.iqge.iqcode.compose.model.McpServerDraft
import com.iqge.iqcode.compose.model.McpType
import com.termux.app.iqcode.storage.McpConfigStore as EngineStore
import org.json.JSONObject

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
