package com.iqge.iqcode.compose.data



import com.iqge.iqcode.compose.model.DiffState
import com.iqge.iqcode.compose.model.FileEntry
import com.iqge.iqcode.compose.model.OpenFile

import com.iqge.iqcode.compose.model.TerminalLine

/**
 * 尚未真实化的数据来源。
 *
 * 现状：**会话列表 / 对话历史 / 任务清单已接真实存储**（见 [SessionReader] 与
 * `engine/IqEngineController`），所以它们**不再**出现在这个接口里。
 *
 * 留在这里的是还没真实化的部分：变更面板（git diff）、文件面板、终端。
 * 它们会在后续 Phase 逐项换成真实实现，届时这个接口的方法会一个个减少，
 * 而不是被整体替换。
 *
 * ⚠️ 刻意不保留"返回假会话列表 / 假对话历史"的方法：那类方法一旦存在，
 * 就很容易被误接回去，表现为"界面像在工作、其实数据是假的"。
 */
interface WorkspaceRepository {
    fun projectName(): String
    fun projectPath(): String
    fun changes(): DiffState
    fun rootFiles(): List<FileEntry>
    fun childrenOf(path: String): List<FileEntry>
    fun readFile(path: String): OpenFile
    fun terminalBanner(project: String): List<TerminalLine>
}
