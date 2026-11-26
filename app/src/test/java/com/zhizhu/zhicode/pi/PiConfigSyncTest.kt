package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.storage.McpConfigStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PiConfigSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildModelsJson_mapsAllProtocolsCorrectly() {
        val anthropicCfg = SessionConfig().apply {
            protocol = "anthropic"
            baseUrl = "https://api.anthropic.com/v1/"
            model = "claude-sonnet-4-5"
            visionEnabled = true
        }
        val anth = PiConfigSync.buildModelsJson(anthropicCfg)
            .getJSONObject("providers")
            .getJSONObject("zhicode")
        assertEquals("https://api.anthropic.com", anth.getString("baseUrl"))
        assertEquals("anthropic-messages", anth.getString("api"))
        assertFalse(anth.getBoolean("authHeader"))
        assertEquals("\$ZHICODE_API_KEY", anth.getString("apiKey"))

        val codexCfg = SessionConfig().apply {
            protocol = "codex-responses"
            baseUrl = "https://chatgpt.com/backend-api/codex/"
            model = "gpt-5.4"
        }
        val codex = PiConfigSync.buildModelsJson(codexCfg)
            .getJSONObject("providers")
            .getJSONObject("zhicode")
        assertEquals("openai-codex-responses", codex.getString("api"))
        assertFalse(codex.getJSONObject("compat").getBoolean("supportsMaxOutputTokens"))

        val zcodeCfg = SessionConfig().apply {
            protocol = "zcode"
            baseUrl = "https://zcode.example.com/"
            model = "glm-5"
        }
        val zcode = PiConfigSync.buildModelsJson(zcodeCfg, deviceMid = "dev-123")
            .getJSONObject("providers")
            .getJSONObject("zhicode")
        assertEquals("https://zcode.example.com/anthropic", zcode.getString("baseUrl"))
        assertEquals("anthropic-messages", zcode.getString("api"))
        assertTrue(zcode.getBoolean("authHeader"))
        assertEquals("dev-123", zcode.getJSONObject("headers").getString("x-device-mid"))
    }

    @Test
    fun buildMcpJson_formatsStdioAndHttpDirectly() {
        val stdio = McpConfigStore.Server().apply {
            name = "fs"
            type = "stdio"
            command = "npx"
            args.addAll(listOf("-y", "@modelcontextprotocol/server-filesystem", "."))
            env = JSONObject().put("NODE_ENV", "production")
            enabled = true
        }
        val http = McpConfigStore.Server().apply {
            name = "docs"
            type = "http"
            url = "https://example.com/mcp"
            headers = JSONObject().put("Authorization", "Bearer abc")
            enabled = false
        }

        val root = PiConfigSync.buildMcpJson(listOf(stdio, http))
        val servers = root.getJSONObject("mcpServers")
        assertEquals("direct", servers.getJSONObject("fs").getString("exposure"))
        assertEquals("npx", servers.getJSONObject("fs").getString("command"))
        assertFalse(servers.getJSONObject("docs").getBoolean("enabled"))
        assertEquals("https://example.com/mcp", servers.getJSONObject("docs").getString("url"))
    }

    @Test
    fun buildAppendSystemPrompt_includesPrivilegesRoleCardAndProjectDoc() {
        val dir = tempFolder.newFolder("proj")
        File(dir, "ZhiCode.md").writeText("Build with ./gradlew test")
        val cfg = SessionConfig().apply {
            rootExecutionEnabled = true
            shizukuExecutionEnabled = false
            customSystemPrompt = "Always answer in Chinese."
            roleCard = "Android systems engineer"
        }

        val prompt = PiConfigSync.buildAppendSystemPrompt(cfg, dir)
        assertTrue(prompt.contains("Agent Root is ENABLED"))
        assertTrue(prompt.contains("Shizuku is DISABLED"))
        assertTrue(prompt.contains("Always answer in Chinese."))
        assertTrue(prompt.contains("<role_card>"))
        assertTrue(prompt.contains("Android systems engineer"))
        assertTrue(prompt.contains("Build with ./gradlew test"))
    }

    @Test
    fun cleanInaccessibleFiles_keepsWritableAndDeletesReadOnlyOnPosix() {
        val dir = tempFolder.newFolder("pi-agent")
        val writable = File(dir, "auth.json").apply { writeText("{}") }
        PiConfigSync.cleanInaccessibleFiles(dir, "auth.json", "models-store.json")
        assertTrue(writable.exists())

        val readOnly = File(dir, "models-store.json").apply {
            writeText("{}")
            setWritable(false)
        }
        PiConfigSync.cleanInaccessibleFiles(dir, "auth.json", "models-store.json")
        assertFalse(readOnly.canWrite() && readOnly.exists())
    }
}
