package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.TerminalLine

interface WorkspaceRepository {
    fun terminalBanner(project: String): List<TerminalLine>
}
