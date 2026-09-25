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
 * 现状：**对话已经接到真实引擎**（见 `engine/IqEngineController`），
 * 所以这里只剩「会话列表 / 变更 / 文件 / 终端横幅」这些尚未真实化的部分。
 *
 * `assistantReply` / `toolSequence` 这两个模拟流式回复与模拟工具序列的接口
 * 已经随接入真实引擎一并删除 —— 留着它们会让人误以为对话仍走 Mock，
 * 一旦被误接回去就会出现"界面像在正常工作、其实没调用模型"这种最难查的问题。
 *
 * 后续 Phase 会逐项把剩下的方法换成真实实现（文件系统 / git diff / 真实 PTY / 会话持久化），
 * 届时这里的方法会一个个减少，而不是被整体替换。
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
}
