package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.DiffState
import com.iqge.iqcode.compose.model.TerminalLine

/**
 * 尚未真实化的数据来源。
 *
 * 现状：**会话列表 / 对话历史 / 任务清单（[SessionReader]）、文件面板（[FileBrowser]）、
 * 对话引擎（`engine/IqEngineController`）都已接真实实现**，所以它们不再出现在这个接口里。
 *
 * 只剩两项：
 * - 变更面板（[changes]）——需要真实 git diff，依赖内置 Termux 环境里的 git；
 * - 终端横幅（[terminalBanner]）——真实 PTY 接上后这个占位横幅会被移除。
 *
 * ⚠️ 刻意不保留任何"返回假列表/假历史"的方法：那类方法一旦存在就容易被误接回去，
 * 表现为"界面像在工作、其实数据是假的"，是最难查的一类问题。
 */
interface WorkspaceRepository {
    fun projectName(): String
    fun projectPath(): String
    fun changes(): DiffState
    fun terminalBanner(project: String): List<TerminalLine>
}
