package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.TerminalLine
import com.iqge.iqcode.compose.model.TerminalTone

/** 内置环境未就绪时终端页的只读占位文案。 */
class MockWorkspaceRepository : WorkspaceRepository {

    override fun terminalBanner(project: String): List<TerminalLine> = listOf(
        TerminalLine("IQ Code Compose · 终端", TerminalTone.DIM),
        TerminalLine("工作目录：$project", TerminalTone.DIM),
        TerminalLine("内置 Termux 环境未就绪，终端暂时不可输入。", TerminalTone.DIM),
        TerminalLine("请在侧栏「准备内置 Termux 环境」里初始化。", TerminalTone.DIM),
    )
}
