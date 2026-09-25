package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.TerminalLine
import com.iqge.iqcode.compose.model.TerminalTone

/**
 * 尚未接线部分的占位实现。
 *
 * 现在只剩两项：项目名/路径，以及终端横幅。
 * 会话历史、任务清单、文件面板、git 变更都已接真实实现。
 */
class MockWorkspaceRepository : WorkspaceRepository {

    override fun projectName(): String = "IQ-Code-Compose"

    override fun projectPath(): String =
        com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH + "/projects/IQ-Code-Compose"

    override fun terminalBanner(project: String): List<TerminalLine> = listOf(
        TerminalLine("IQ Code Compose · 终端", TerminalTone.DIM),
        TerminalLine("工作目录：$project", TerminalTone.DIM),
        TerminalLine("真实 PTY 尚未接入（需要先初始化内置 Termux 环境）。", TerminalTone.DIM),
    )
}
