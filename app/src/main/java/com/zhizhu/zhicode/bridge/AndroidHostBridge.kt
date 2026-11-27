package com.zhizhu.zhicode.bridge

import android.content.Context
import com.termux.app.zhicode.core.PermissionModePolicy
import com.termux.app.zhicode.core.RiskClassifier
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.model.ToolExecutionResult
import com.termux.app.zhicode.tools.ToolRegistry
import com.zhizhu.zhicode.ContainerPaths
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal class AndroidHostBridge(
    private val executeTool: (SessionConfig, String, JSONObject) -> ToolExecutionResult,
) {
    constructor(context: Context) : this(ToolRegistry(context.applicationContext)::execute)

    fun invoke(
        tool: String,
        input: JSONObject,
        currentConfig: () -> SessionConfig,
        confirm: (summary: String, highRisk: Boolean) -> Boolean,
        beginExecution: () -> Boolean,
    ): JSONObject {
        val initial = currentConfig()
        denialReason(initial, tool, input)?.let { return error(it) }
        val mapped = translateGuestPaths(tool, input)
        val asked = requiresConfirmation(initial, tool, input)
        if (asked && !confirm(summary(tool, input), isHighRisk(tool, input))) {
            return error("用户拒绝了本次操作，或确认已超时")
        }

        val latest = currentConfig()
        if (latest.projectDirectory != initial.projectDirectory || latest.workflowId != initial.workflowId) {
            return error("工作区已变更，请重新调用工具")
        }
        denialReason(latest, tool, input)?.let { return error(it) }
        if (!asked && requiresConfirmation(latest, tool, input)) {
            return error("权限模式已变化，请重新调用工具")
        }
        if (!beginExecution()) return error("宿主工具调用已取消")
        return serializeResult(executeTool(latest, tool, mapped))
    }

    private fun translateGuestPaths(toolName: String, input: JSONObject): JSONObject {
        val copy = JSONObject(input.toString())
        if (toolName == "AndroidIntent") {
            val apkPath = copy.optString("path", "").trim()
            if (apkPath.isNotEmpty() && !apkPath.startsWith("/storage/") && !apkPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(apkPath).absolutePath)
            }
        } else if (toolName == "Sandbox") {
            val action = copy.optString("action", "").trim().lowercase()
            val apkPath = copy.optString("path", "").trim()
            if (action == "install" && apkPath.isNotEmpty() && !apkPath.startsWith("/storage/") && !apkPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(apkPath).absolutePath)
            }
        } else if (toolName == "Debug") {
            val action = copy.optString("action", "").trim().lowercase()
            val libPath = copy.optString("path", "").trim()
            if (action == "load_library" && libPath.isNotEmpty() && !libPath.startsWith("/storage/") && !libPath.startsWith("/sdcard")) {
                copy.put("path", ContainerPaths.guestToHost(libPath).absolutePath)
            }
        }
        return copy
    }

    private fun serializeResult(result: ToolExecutionResult): JSONObject {
        val out = JSONObject()
            .put("ok", !result.isError)
            .put("isError", result.isError)
            .put("exitCode", result.exitCode)
            .put("text", result.content ?: "")

        val images = JSONArray()
        val extra: JSONArray? = result.additionalContent()
        if (extra != null) {
            for (i in 0 until extra.length()) {
                val block = extra.optJSONObject(i) ?: continue
                if ("image" != block.optString("type")) continue
                val source = block.optJSONObject("source") ?: continue
                val data = source.optString("data", "")
                val mime = source.optString("media_type", "image/png")
                if (data.isNotEmpty()) {
                    images.put(JSONObject().put("data", data).put("mimeType", mime))
                }
            }
        }
        if (images.length() > 0) out.put("images", images)
        return out
    }

    companion object {
        val allowedTools = setOf(
            "Root", "Shizuku", "AndroidIntent", "Debug", "Sandbox", "ui_canvas", "WebSearch", "WebFetch",
        )
        private val sandboxReadActions = setOf(
            "status", "list", "inspect", "screenshot", "dump_ui", "find_node", "debug_snapshot",
        )
        private val debugReadActions = setOf(
            "process_list", "process_info", "maps", "modules", "threads", "thread_dump", "frida_status", "frida_events",
        )
        private val canvasReadOperations = setOf("get", "preview", "export")

        fun error(message: String) = JSONObject().put("ok", false).put("isError", true)
            .put("exitCode", 1).put("text", message)

        fun denialReason(config: SessionConfig, tool: String, input: JSONObject): String? = when {
            tool !in allowedTools -> "宿主桥未开放工具 $tool"
            tool == "Root" && !config.rootExecutionEnabled -> "Agent Root 未在设置中启用"
            tool == "Shizuku" && !config.shizukuExecutionEnabled -> "Agent Shizuku 未在设置中启用"
            (tool == "WebSearch" || tool == "WebFetch") && !config.webSearchEnabled -> "联网搜索已在设置中关闭"
            !isReadOnly(tool, input) && effectiveMode(config) == PermissionModePolicy.PLAN ->
                "Plan 只读模式已拦截 $tool"
            else -> null
        }

        fun requiresConfirmation(config: SessionConfig, tool: String, input: JSONObject): Boolean =
            !isReadOnly(tool, input) && effectiveMode(config) != PermissionModePolicy.BYPASS

        private fun effectiveMode(config: SessionConfig): String =
            PermissionModePolicy.effective(config.permissionMode, config.planWorkflowState)

        private fun isReadOnly(tool: String, input: JSONObject): Boolean = when (tool) {
            "WebSearch", "WebFetch" -> true
            "ui_canvas" -> input.optString("operation", "get") in canvasReadOperations
            "Sandbox" -> action(input) in sandboxReadActions
            "Debug" -> input.optString("scope", "sandbox").trim().lowercase(Locale.ROOT) == "sandbox" &&
                action(input) in debugReadActions
            else -> false
        }

        private fun isHighRisk(tool: String, input: JSONObject): Boolean = when (tool) {
            "Debug" -> input.optString("scope", "sandbox").trim().equals("host", ignoreCase = true) ||
                RiskClassifier.isHighRisk(tool, input)
            "Sandbox" -> action(input) == "uninstall" || action(input) == "clear_data"
            "AndroidIntent" -> input.optString("operation").trim().equals("install_apk", ignoreCase = true)
            else -> RiskClassifier.isHighRisk(tool, input)
        }

        private fun action(input: JSONObject): String = input.optString("action").trim().lowercase(Locale.ROOT)

        private fun summary(tool: String, input: JSONObject): String = when (tool) {
            "Root", "Shizuku" -> buildString {
                append(input.optString("command"))
                val cwd = input.optString("cwd")
                if (cwd.isNotBlank()) append("\n工作目录 ").append(cwd)
            }
            else -> input.toString()
        }
    }
}
