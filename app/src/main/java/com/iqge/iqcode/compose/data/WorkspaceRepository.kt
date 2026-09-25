package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.TerminalLine

/**
 * 尚未真实化的数据来源。
 *
 * 现状：
 * - 会话列表 / 对话历史 / 任务清单 → [SessionReader]（真实磁盘）
 * - 文件面板 → [FileBrowser]（真实文件系统）
 * - git 变更 → [GitChanges]（真实 git）
 * - 对话引擎 → `engine/IqEngineController`（真实模型调用）
 *
 * 只剩两项：
 * - [projectName] / [projectPath]：项目路径将来要支持用户手动切换；
 * - [terminalBanner]：真实 PTY 接上后这个占位横幅会被移除。
 *
 * ⚠️ 刻意不保留任何"返回假列表 / 假历史 / 假 diff"的方法：
 * 那类方法一旦存在就容易被误接回去，表现为"界面像在工作、其实数据是假的"。
 */
interface WorkspaceRepository {
    fun projectName(): String
    fun projectPath(): String
    fun terminalBanner(project: String): List<TerminalLine>
}
