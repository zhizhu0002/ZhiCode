package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.DiffState
import com.iqge.iqcode.compose.model.FileEntry
import com.iqge.iqcode.compose.model.OpenFile
import com.iqge.iqcode.compose.model.SessionSummary
import com.iqge.iqcode.compose.model.TerminalLine

/**
 * 数据来源抽象。
 *
 * 第一期由 [MockWorkspaceRepository] 提供内存假数据；
 * 后续接真实 IQ Code 引擎时，只需实现本接口并把实例交给 WorkspaceViewModel，
 * UI 层不需要改动。
 */
interface WorkspaceRepository {
    fun projectName(): String
    fun projectPath(): String
    fun sessions(): List<SessionSummary>
    /** 当前会话的 Agent 任务清单（悬浮卡显示"当前窗口"，详情窗口显示全部）。 */
    fun tasks(): List<AgentTask>
    fun transcript(sessionId: String): List<ChatItem>
    fun changes(): DiffState
    fun rootFiles(): List<FileEntry>
    fun childrenOf(path: String): List<FileEntry>
    fun readFile(path: String): OpenFile
    fun terminalBanner(project: String): List<TerminalLine>
    /** 模拟一次助手回复的流式分片。 */
    fun assistantReply(prompt: String): List<String>
    /** 模拟一次工具调用序列（名称、显示名、摘要、输出）。 */
    fun toolSequence(prompt: String): List<MockToolRun>
}

data class MockToolRun(
    val toolName: String,
    val displayName: String,
    val summary: String,
    val output: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val exitCode: Int = 0,
    val elapsedMs: Long = 900L,
)
