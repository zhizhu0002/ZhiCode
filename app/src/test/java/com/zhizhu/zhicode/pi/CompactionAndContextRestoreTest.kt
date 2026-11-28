package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.model.SessionConfig
import com.zhizhu.zhicode.compose.model.AppSettings
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.ui.composer.formatContextPercentLabel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

class CompactionAndContextRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `默认自动压缩阈值为 60 百分比`() {
        val config = SessionConfig()
        assertEquals(0.60, config.autoCompactRatio, 0.0001)
        assertEquals(60, AppSettings().autoCompactPercent)
    }

    @Test
    fun `PiConfigSync 按阈值计算 reserveTokens 并保留原有 settings 字段`() {
        val config = SessionConfig().apply {
            contextWindowTokens = 128_000
            autoCompact = true
            autoCompactRatio = 0.60
        }
        val reserve = PiConfigSync.computeCompactionReserveTokens(config)
        // 128000 * 0.60 = 76800 -> reserveTokens = 51200 -> window - reserveTokens = 76800 (60%)
        assertEquals(51_200, reserve)

        val existing = JSONObject().put("theme", "dark").put("compaction", JSONObject().put("keepRecentTokens", 20_000))
        val merged = PiConfigSync.buildSettingsJson(config, existing, reserve)
        assertEquals("dark", merged.optString("theme"))
        val compaction = merged.getJSONObject("compaction")
        assertTrue(compaction.getBoolean("enabled"))
        assertEquals(51_200, compaction.getInt("reserveTokens"))
        assertEquals(20_000, compaction.getInt("keepRecentTokens"))

        val disabled = SessionConfig().apply {
            contextWindowTokens = 200_000
            autoCompact = false
            autoCompactRatio = 0.60
        }
        val disabledJson = PiConfigSync.buildSettingsJson(disabled)
        assertFalse(disabledJson.getJSONObject("compaction").getBoolean("enabled"))
        assertEquals(80_000, disabledJson.getJSONObject("compaction").getInt("reserveTokens"))
    }

    @Test
    fun `从 Pi 会话 jsonl 正确恢复最后有效 usage 与后续新增消息用量`() {
        val file = tempFolder.newFile("session.jsonl")
        file.writeText(
            listOf(
                """{"type":"session","version":3,"id":"s1","timestamp":"2026-11-26T10:00:00Z"}""",
                """{"type":"message","id":"m1","parentId":null,"message":{"role":"user","content":[{"type":"text","text":"hello"}]}}""",
                """{"type":"message","id":"m2","parentId":"m1","message":{"role":"assistant","stopReason":"stop","usage":{"input":2000,"output":500,"cacheRead":1000,"cacheWrite":0,"totalTokens":3500},"content":[{"type":"text","text":"ok"}]}}""",
                """{"type":"message","id":"m3","parentId":"m2","message":{"role":"user","content":[{"type":"text","text":"12345678"}]}}""",
            ).joinToString("\n"),
        )
        // 3500 + ceil(8/4) = 3502
        assertEquals(3502, PiRpcBackend.estimateContextTokensFromSessionFile(file))
    }

    @Test
    fun `切换会话恢复各自最后实际使用的模型与推理强度`() {
        val deepSeek = tempFolder.newFile("deepseek.jsonl")
        val glm = tempFolder.newFile("glm.jsonl")
        deepSeek.writeText(
            """{"type":"thinking_level_change","id":"d1","parentId":null,"thinkingLevel":"max"}
               {"type":"message","id":"d2","parentId":"d1","message":{"role":"assistant","provider":"wb","model":"deepseek","stopReason":"stop"}}""".trimIndent(),
        )
        glm.writeText(
            """{"type":"thinking_level_change","id":"g1","parentId":null,"thinkingLevel":"high"}
               {"type":"message","id":"g2","parentId":"g1","message":{"role":"assistant","provider":"wb","model":"glm-5.3-flash","stopReason":"stop"}}
               {"type":"model_change","id":"g3","parentId":"g2","provider":"wb","modelId":"deepseek"}
               {"type":"thinking_level_change","id":"g4","parentId":"g3","thinkingLevel":"max"}""".trimIndent(),
        )

        assertEquals("deepseek", PiRpcBackend.readSessionHistory(deepSeek).modelId)
        val restored = PiRpcBackend.readSessionHistory(glm)
        assertEquals("glm-5.3-flash", restored.modelId)
        assertEquals("high", restored.thinkingLevel)
        assertEquals("deepseek", PiRpcBackend.readSessionHistory(deepSeek).modelId)
    }

    @Test
    fun `压缩之后的失效旧 usage 不应计入重进后的上下文用量`() {
        val file = tempFolder.newFile("compacted.jsonl")
        file.writeText(
            listOf(
                """{"type":"session","version":3,"id":"s2"}""",
                """{"type":"message","id":"m1","parentId":null,"message":{"role":"assistant","stopReason":"stop","usage":{"totalTokens":90000},"content":[{"type":"text","text":"old"}]}}""",
                """{"type":"compaction","id":"c1","parentId":"m1","summary":"12345678","firstKeptEntryId":"m1","tokensBefore":90000}""",
                """{"type":"message","id":"m2","parentId":"c1","message":{"role":"assistant","stopReason":"aborted","usage":{"totalTokens":88888},"content":[]}}""",
            ).joinToString("\n"),
        )
        // compaction summary (8 chars -> 2 tokens), aborted response ignored
        assertEquals(2, PiRpcBackend.estimateContextTokensFromSessionFile(file))
    }

    @Test
    fun `ChatItem 默认耗时为未知 -1 避免历史或未完成回复误显 0ms`() {
        val item = ChatItem(id = "a1", kind = ChatKind.ASSISTANT)
        assertEquals(-1L, item.elapsedMs)
    }

    @Test
    fun `挑选 Pi 会话文件时跳过未落盘或空白的临时会话记录`() {
        val valid = tempFolder.newFile("valid.jsonl")
        valid.writeText("""{"type":"message","id":"m1","message":{"role":"assistant","stopReason":"stop","usage":{"totalTokens":4260},"content":[{"type":"text","text":"hi"}]}}""")
        val emptyHeader = tempFolder.newFile("empty.jsonl")
        emptyHeader.writeText("""{"type":"session","version":3,"id":"empty"}""")

        val chosen = PiRpcBackend.selectLatestUsableSessionFile(
            listOf("/sessions/valid.jsonl", "/sessions/empty.jsonl", "/sessions/missing.jsonl"),
        ) { path ->
            when {
                path.endsWith("valid.jsonl") -> valid
                path.endsWith("empty.jsonl") -> emptyHeader
                else -> File(tempFolder.root, "nonexistent.jsonl")
            }
        }
        assertEquals("/sessions/valid.jsonl", chosen)
    }

    @Test
    fun `进程重启时跳过空白临时会话并复用有真实上下文的 Pi 文件`() {
        val boundEmpty = tempFolder.newFile("bound-empty.jsonl")
        boundEmpty.writeText("""{"type":"session","version":3,"id":"empty"}""")
        val storedValid = tempFolder.newFile("stored-valid.jsonl")
        storedValid.writeText("""{"type":"message","id":"m1","message":{"role":"user","content":[{"type":"text","text":"hi there"}]}}""")

        val picked = PiRpcBackend.selectLatestUsableSessionFile(
            listOf("/sessions/stored-valid.jsonl", "/sessions/bound-empty.jsonl"),
        ) { path ->
            if (path.endsWith("bound-empty.jsonl")) boundEmpty else storedValid
        }
        assertEquals("/sessions/stored-valid.jsonl", picked)
    }

    @Test
    fun `上下文百分比按一位小数格式化并在非零极小值时保底 0_1`() {
        assertEquals("0%", formatContextPercentLabel(0, 1_000_000))
        assertEquals("0.3%", formatContextPercentLabel(2_837, 1_000_000))
        assertEquals("0.1%", formatContextPercentLabel(120, 1_000_000))
        assertEquals("2.6%", formatContextPercentLabel(3_315, 128_000))
        assertEquals("60%", formatContextPercentLabel(600_000, 1_000_000))
        assertEquals("100%", formatContextPercentLabel(1_200_000, 1_000_000))
    }
}
