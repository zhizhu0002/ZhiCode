package com.iqge.iqcode.compose.data

import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.ChatKind
import com.iqge.iqcode.compose.model.DiffFile
import com.iqge.iqcode.compose.model.DiffState
import com.iqge.iqcode.compose.model.FileEntry
import com.iqge.iqcode.compose.model.OpenFile
import com.iqge.iqcode.compose.model.SessionSummary
import com.iqge.iqcode.compose.model.TaskState
import com.iqge.iqcode.compose.model.TerminalLine
import com.iqge.iqcode.compose.model.TerminalTone
import com.iqge.iqcode.compose.model.ToolActivity
import com.iqge.iqcode.compose.model.ToolKind

/** 内存假数据源，用于在没有真实引擎时驱动 UI。 */
class MockWorkspaceRepository : WorkspaceRepository {

    override fun projectName(): String = "IQ-Code-Compose"

    override fun projectPath(): String =
        com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH + "/projects/IQ-Code-Compose"

    /**
     * 变更面板的示例数据（真实 git diff 在后续 Phase 接入）。
     * `diff` 用统一 diff 格式写，面板直接按行渲染。
     */
    override fun changes(): DiffState = DiffState(
        files = listOf(
            DiffFile(
                name = "app/src/main/java/com/iqge/iqcode/compose/ui/TopBar.kt",
                additions = 42,
                deletions = 3,
                diff = """
                    diff --git a/app/.../ui/TopBar.kt b/app/.../ui/TopBar.kt
                    index 3f2a1c0..9b4e771 100644
                    --- a/app/src/main/java/com/iqge/iqcode/compose/ui/TopBar.kt
                    +++ b/app/src/main/java/com/iqge/iqcode/compose/ui/TopBar.kt
                    @@ -1,9 +1,12 @@
                     package com.iqge.iqcode.compose.ui
                    -import androidx.compose.material3.TopAppBar
                    +import top.yukonga.miuix.kmp.basic.TopAppBar
                     import androidx.compose.runtime.Composable
                    +import top.yukonga.miuix.kmp.theme.MiuixTheme
 
                     @Composable
                     fun IqTopBar(state: WorkspaceUiState) {
                    -    TopAppBar(title = { Text("IQ Code") })
                    +    TopAppBar(
                    +        title = "IQ Code",
                    +        subtitle = state.workingStatus ?: "已就绪",
                    +        actions = { TopBarActions(state) },
                    +    )
                     }
                """.trimIndent(),
            ),
            DiffFile(
                name = "app/src/main/java/com/iqge/iqcode/compose/ui/chat/ChatList.kt",
                additions = 96,
                deletions = 0,
                diff = """
                    diff --git a/app/.../ui/chat/ChatList.kt b/app/.../ui/chat/ChatList.kt
                    new file mode 100644
                    --- /dev/null
                    +++ b/app/src/main/java/com/iqge/iqcode/compose/ui/chat/ChatList.kt
                    @@ -0,0 +1,8 @@
                    +package com.iqge.iqcode.compose.ui.chat
                    +
                    +import androidx.compose.foundation.lazy.LazyColumn
                    +import androidx.compose.foundation.lazy.items
                    +
                    +@Composable
                    +fun ChatList(state: WorkspaceUiState) {
                    +    LazyColumn { items(state.transcript) { ChatRow(it) } }
                    +}
                """.trimIndent(),
            ),
            DiffFile(
                name = "gradle.properties",
                additions = 4,
                deletions = 1,
                diff = """
                    diff --git a/gradle.properties b/gradle.properties
                    index 11ab90c..77c3d21 100644
                    --- a/gradle.properties
                    +++ b/gradle.properties
                    @@ -1,4 +1,7 @@
                     android.useAndroidX=true
                    -android.suppressUnsupportedCompileSdk=35
                    +android.suppressUnsupportedCompileSdk=37
                    +android.aapt2FromMavenOverride=/data/user/0/com.iqge/files/usr/bin/aapt2
                    +org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                     kotlin.daemon.jvmargs=-Xmx1536m
                """.trimIndent(),
            ),
        ),
    )

    override fun rootFiles(): List<FileEntry> = childrenOf(projectPath())

    override fun childrenOf(path: String): List<FileEntry> = when {
        path.endsWith("IQ-Code-Compose") -> listOf(
            FileEntry("app", "$path/app", true),
            FileEntry("gradle", "$path/gradle", true),
            FileEntry("build.gradle", "$path/build.gradle", false, 213),
            FileEntry("gradle.properties", "$path/gradle.properties", false, 305),
            FileEntry("gradlew", "$path/gradlew", false, 8_733),
            FileEntry("local.properties", "$path/local.properties", false, 53),
            FileEntry("settings.gradle", "$path/settings.gradle", false, 284),
            FileEntry("README.md", "$path/README.md", false, 1_024),
        )
        path.endsWith("app") -> listOf(
            FileEntry("build.gradle", "$path/build.gradle", false, 1_384),
            FileEntry("src", "$path/src", true),
        )
        path.endsWith("src") -> listOf(
            FileEntry("main", "$path/main", true),
        )
        path.endsWith("main") -> listOf(
            FileEntry("java", "$path/java", true),
            FileEntry("AndroidManifest.xml", "$path/AndroidManifest.xml", false, 816),
        )
        path.contains("java") -> listOf(
            FileEntry("com", "$path/com", true),
        )
        else -> listOf(
            FileEntry("MainActivity.kt", "$path/MainActivity.kt", false, 933),
            FileEntry("data", "$path/data", true),
            FileEntry("model", "$path/model", true),
            FileEntry("state", "$path/state", true),
            FileEntry("theme", "$path/theme", true),
            FileEntry("ui", "$path/ui", true),
        )
    }

    override fun readFile(path: String): OpenFile {
        val name = path.substringAfterLast('/')
        return OpenFile(
            name = name,
            path = path,
            language = languageFor(name),
            content = """
                // $name — Mock 只读视图
                package com.iqge.iqcode.compose

                import androidx.compose.runtime.Composable
                import top.yukonga.miuix.kmp.basic.Text

                @Composable
                fun Example() {
                    Text(text = "IQ Code Compose")
                }
            """.trimIndent(),
        )
    }

    private fun languageFor(name: String): String = when {
        name.endsWith(".kt") -> "kotlin"
        name.endsWith(".java") -> "java"
        name.endsWith(".gradle") -> "groovy"
        name.endsWith(".properties") -> "properties"
        name.endsWith(".xml") -> "xml"
        name.endsWith(".md") -> "markdown"
        else -> "text"
    }

    override fun terminalBanner(project: String): List<TerminalLine> = listOf(
        TerminalLine("IQ Code Compose · 内置终端（Mock）", TerminalTone.DIM),
        TerminalLine("工作目录：$project", TerminalTone.DIM),
        TerminalLine("本面板第一期只还原外观，未接入真实 PTY。", TerminalTone.DIM),
        TerminalLine(""),
        TerminalLine("~ $ ls", TerminalTone.PROMPT),
        TerminalLine("app  gradle  build.gradle  gradle.properties  gradlew  settings.gradle"),
        TerminalLine("~ $ ./gradlew :app:assembleDebug", TerminalTone.PROMPT),
        TerminalLine("BUILD SUCCESSFUL in 43s", TerminalTone.SUCCESS),
        TerminalLine("APK: app/build/outputs/apk/debug/IQCodeCompose-debug.apk", TerminalTone.DIM),
        TerminalLine("~ $ ", TerminalTone.PROMPT),
    )
}
