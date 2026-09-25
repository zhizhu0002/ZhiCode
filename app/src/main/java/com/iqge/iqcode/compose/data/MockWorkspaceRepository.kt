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

    override fun projectPath(): String = "/data/user/0/com.iqge/files/home/projects/IQ-Code-Compose"

    /**
     * Agent 任务清单。`detail` 用 Markdown 写，详情窗口直接交给 `IqMarkdown` 渲染，
     * 用来验证标题 / 粗体 / 行内代码 / 列表 / 代码块这几类都能显示。
     */
    override fun tasks(): List<AgentTask> = listOf(
        AgentTask(
            title = "搭建工程与工具链",
            state = TaskState.DONE,
            detail = """
                **目标**：把 Gradle 与 AGP 打通，产出第一个 APK。

                ## 结论
                固定组合已验证可用，`aapt2` 必须指向本机二进制：

                ```
                android.aapt2FromMavenOverride=/usr/bin/aapt2
                ```

                ### 关键点
                - Gradle `9.3.1`、AGP `9.1.1`
                - AGP 9 内建 Kotlin 支持，**不再需要** `kotlin.android` 插件
            """.trimIndent(),
        ),
        AgentTask(
            title = "核对 Miuix 组件签名",
            state = TaskState.DONE,
            detail = """
                官方文档缺参数细节，改为**从字节码提取**真实 Kotlin 参数名。

                提取到的 `C(Name)N(params)` 签名串示例：

                ```
                C(Card)N(onClick, modifier, cornerRadius, insideMargin, colors)
                ```

                - `IconButton` 最小尺寸是 `40dp` 正圆 —— 窄行必须显式覆盖
                - 图标库**没有**纯右箭头（`Forward` 是带框分享箭头）
            """.trimIndent(),
        ),
        AgentTask(
            title = "建立状态模型与 ViewModel",
            state = TaskState.RUNNING,
            detail = """
                单一 `WorkspaceUiState` + `StateFlow`，所有改动走 `copy()`。

                ### 已完成
                - `ChatItem` / `ToolActivity` / `AgentTask` 模型
                - 预输入改为**队列**（`List<QueuedPrompt>`），修掉连发漏消息

                ### 待办
                1. 任务详情窗口（本轮）
                2. 设置页 `NumberPicker` 验证
            """.trimIndent(),
        ),
        AgentTask(
            title = "实现对话流与输入器",
            state = TaskState.PENDING,
            detail = "等待上一步完成。\n\n计划内容：\n- 悬浮输入器 + 方角发送键\n- 暂停键位于输入框与发送键之间",
        ),
        AgentTask(
            title = "回归验证与 README 同步",
            state = TaskState.PENDING,
            detail = "逐屏截图回归，并同步 README 的 Miuix 组件清单。",
        ),
    )

    override fun sessions(): List<SessionSummary> = listOf(
        SessionSummary(
            id = "s1",
            title = "用 Compose + Miuix 重做主界面",
            project = projectName(),
            messageCount = 48,
            updatedAtLabel = "12 分钟前",
        ),
        SessionSummary(
            id = "s2",
            title = "核对 Miuix 组件签名",
            project = projectName(),
            messageCount = 23,
            updatedAtLabel = "2 小时前",
            note = "从 AAR 字节码提取参数名",
        ),
        SessionSummary(
            id = "s3",
            title = "Gradle 离线构建排查",
            project = projectName(),
            messageCount = 17,
            updatedAtLabel = "昨天",
        ),
        SessionSummary(
            id = "s4",
            title = "对话流工具卡片聚合规则",
            project = projectName(),
            messageCount = 61,
            updatedAtLabel = "3 天前",
        ),
        SessionSummary(
            id = "s5",
            title = "初始工程脚手架",
            project = projectName(),
            messageCount = 9,
            updatedAtLabel = "9月20日",
        ),
    )

    override fun transcript(sessionId: String): List<ChatItem> = listOf(
        ChatItem(
            id = "m1",
            kind = ChatKind.USER,
            title = "你",
            body = "帮我做一个跟这个（UI一样的）软件，用compose＋miuix写",
        ),
        ChatItem(
            id = "m2",
            kind = ChatKind.ASSISTANT,
            title = "IQ",
            body = "我先进入计划模式做只读调研：现有界面是纯 Java 程序化构建的 MainActivity，" +
                "我会把主界面结构拆成 Compose 组件，再用 Miuix 组件重写。",
            thinking = "需要先确认：工具链是否可用、Miuix 组件签名、原 UI 的分区结构。",
            processSteps = listOf("开始分析请求", "读取 MainActivity.java", "调用工具：Read"),
            contextTokens = 24_500,
            contextWindow = 200_000,
        ),
        ChatItem(
            id = "m3",
            kind = ChatKind.TOOL_GROUP,
            groupLabel = "搜索 3 个模式、读取 4 个文件、查看 2 个位置",
            groupCompleted = true,
            tools = listOf(
                ToolActivity(
                    id = "t1", toolName = "Grep", displayName = "搜索代码",
                    summary = "buildGlobalBar|buildSidebar|buildComposer",
                    completed = true, elapsedMs = 240L, kind = ToolKind.SEARCH,
                    output = "MainActivity.java:1021: private View buildGlobalBar() {\n" +
                        "MainActivity.java:1055: private View buildSidebar() {\n" +
                        "MainActivity.java:1360: private View buildComposer() {",
                ),
                ToolActivity(
                    id = "t2", toolName = "Read", displayName = "读取文件",
                    summary = "app/src/main/java/com/iqge/MainActivity.java",
                    completed = true, elapsedMs = 1_180L, kind = ToolKind.READ,
                    output = "… 5158 行已读取，节选 1021-1260 行用于还原顶栏与侧栏。",
                ),
                ToolActivity(
                    id = "t3", toolName = "ReadMany", displayName = "批量读取",
                    summary = "AgentProgressView.java · MarkdownRenderer.java · +2",
                    completed = true, elapsedMs = 760L, kind = ToolKind.READ,
                    output = "AgentProgressView.java (13904 bytes)\n" +
                        "MarkdownRenderer.java (18657 bytes)\n" +
                        "UiMotion.java (11020 bytes)\n" +
                        "UiCanvasController.java (9023 bytes)",
                ),
                ToolActivity(
                    id = "t4", toolName = "Bash", displayName = "执行命令",
                    summary = "unzip -l classes.jar | grep 'kmp/basic'",
                    completed = true, elapsedMs = 1_640L, kind = ToolKind.COMMAND,
                    output = "top/yukonga/miuix/kmp/basic/ScaffoldKt.class\n" +
                        "top/yukonga/miuix/kmp/basic/TopAppBarKt.class\n" +
                        "top/yukonga/miuix/kmp/basic/TabRowKt.class\n" +
                        "top/yukonga/miuix/kmp/basic/TextFieldKt.class",
                ),
            ),
        ),
        ChatItem(
            id = "m4",
            kind = ChatKind.INFO,
            title = "计划模式",
            body = "已提交实现计划，等待批准。",
        ),
        ChatItem(
            id = "m5",
            kind = ChatKind.ASSISTANT,
            title = "IQ",
            body = "计划已批准。我先建工程并打通构建链，然后核对 Miuix 组件签名，" +
                "再逐块写主界面：顶栏、对话流、输入器、侧栏、三个工作区面板。",
            processSteps = listOf("完成回复"),
            contextTokens = 41_200,
            contextWindow = 200_000,
        ),
        ChatItem(
            id = "m6",
            kind = ChatKind.TOOL_GROUP,
            groupLabel = "写入 2 个文件、修改 1 个文件",
            groupCompleted = true,
            tools = listOf(
                ToolActivity(
                    id = "t5", toolName = "Write", displayName = "写入文件",
                    summary = "app/src/main/java/…/model/UiModels.kt",
                    completed = true, elapsedMs = 320L, additions = 168, kind = ToolKind.EDIT,
                    output = "已写入 8143 字节。",
                ),
                ToolActivity(
                    id = "t6", toolName = "MultiEdit", displayName = "批量修改",
                    summary = "theme/IqTheme.kt · 3 edits",
                    completed = true, elapsedMs = 480L, additions = 24, deletions = 6,
                    kind = ToolKind.EDIT,
                    output = "已应用 3/3 处修改。",
                ),
            ),
        ),
    )

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

    override fun assistantReply(prompt: String): List<String> = listOf(
        "已收到：", "\"", prompt, "\"", "\n\n",
        "我按计划把这件事拆成三步：\n",
        "1. 先确认工程与工具链可用；\n",
        "2. 再用 Miuix 组件重写主界面分区；\n",
        "3. 最后逐屏截图比对视觉。\n\n",
        "顶栏、对话流与输入器先落地，随后补齐侧栏与三个工作区面板。",
    )

    override fun toolSequence(prompt: String): List<MockToolRun> = listOf(
        MockToolRun(
            toolName = "Grep",
            displayName = "搜索代码",
            summary = "class MainActivity|SLASH_COMMANDS",
            output = "MainActivity.java:151: class MainActivity extends Activity\n" +
                "MainActivity.java:415: new SlashCommand(\"/help\", …)",
            elapsedMs = 210L,
        ),
        MockToolRun(
            toolName = "Read",
            displayName = "读取文件",
            summary = "app/src/main/java/com/iqge/MainActivity.java",
            output = "读取 120 行（offset=1353, limit=120）。",
            elapsedMs = 640L,
        ),
        MockToolRun(
            toolName = "Edit",
            displayName = "修改文件",
            summary = "ui/composer/Composer.kt",
            output = "已应用 1 处修改。",
            additions = 18,
            deletions = 5,
            elapsedMs = 380L,
        ),
        MockToolRun(
            toolName = "Bash",
            displayName = "执行命令",
            summary = "./gradlew :app:assembleDebug",
            output = "BUILD SUCCESSFUL in 47s\n1 actionable task: 1 executed",
            elapsedMs = 47_000L,
        ),
    )
}
