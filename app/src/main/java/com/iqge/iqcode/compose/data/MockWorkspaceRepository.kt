package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.DiffState
import com.iqge.iqcode.compose.model.TerminalLine
import com.iqge.iqcode.compose.model.TerminalTone


/** 变更面板与终端尚未接线时的占位实现。 */
class MockWorkspaceRepository : WorkspaceRepository {

    override fun projectName(): String = "IQ-Code-Compose"

    override fun projectPath(): String =
        com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH + "/projects/IQ-Code-Compose"

    /**
     * 变更面板的示例数据。
     *
     * ⚠️ 这是**假的**，只为在真实 git diff 接上之前保持面板可渲染。
     * 真实的 git status/diff 需要内置 Termux 环境里的 git 可执行文件。
     */
    override fun changes(): DiffState = DiffState(
        files = listOf(
            com.iqge.iqcode.compose.model.DiffFile(
                name = "app/src/main/java/com/iqge/iqcode/compose/ui/TopBar.kt",
                additions = 12,
                deletions = 4,
                diff = """
                    @@ -18,9 +18,9 @@
                    -private val TopBarInset = 48.dp
                    +internal val TopBarTitleRowHeight = 48.dp
                    +internal val TopBarTabRowPadding = 12.dp
                    -private val TopBarInsetWithTabs = 96.dp
                    +private val TopBarInsetWithTabs =
                    +    TopBarTitleRowHeight + WorkspaceTabRowHeight + TopBarTabRowPadding
                """.trimIndent(),
            ),
        ),
        loading = false,
    )

    override fun terminalBanner(project: String): List<TerminalLine> = listOf(
        TerminalLine("IQ Code Compose · 终端", TerminalTone.DIM),
        TerminalLine("工作目录：$project", TerminalTone.DIM),
        TerminalLine("真实 PTY 尚未接入（需要先初始化内置 Termux 环境）。", TerminalTone.DIM),
    )
}
