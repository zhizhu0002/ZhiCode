package com.iqge.iqcode.compose.data

import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * 工作区路径。
 *
 * 为什么需要这个：应用原来把"当前项目"写死成 `~/projects/IQ-Code-Compose`，
 * 那是**开发本应用时用的**路径。新装的应用里它并不存在，后果是：
 * - 变更面板一直显示「项目目录不存在」；
 * - 终端的工作目录回退到 home（真机上 `pwd` 看到的就是 `files/home`）；
 * - 会话按项目分目录存储，项目不存在时会话全落在一个"幽灵项目"键下。
 *
 * 现在改成：默认工作区 = `$HOME/workspace`，在运行环境装好后**主动创建**。
 * 这样首次运行就是一个可用状态，而不是到处显示"不存在"。
 *
 * ⚠️ 工作区是"用户的数据目录"，不是应用私有目录 —— 删掉应用数据会连它一起没，
 * 所以只在这里创建一次、之后不再动它（不清理、不重建）。
 */
internal object WorkspacePaths {

    /** 默认工作区目录名。 */
    private const val DEFAULT_WORKSPACE_NAME = "workspace"

    /** 默认工作区的绝对路径。 */
    fun defaultProject(): String =
        TermuxConstants.TERMUX_HOME_DIR_PATH.trimEnd('/') + "/" + DEFAULT_WORKSPACE_NAME

    /** 侧栏/顶栏显示的项目名：取路径最后一段。 */
    fun nameOf(path: String): String =
        path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

    /**
     * 确保工作区存在，并放一个说明文件。
     *
     * 为什么要放说明文件：一个空目录在文件面板里看起来像"出错了"；
     * 变更面板也会显示"不是 git 仓库"。放一个 README 至少让用户看到
     * "这是你的工作区，把代码放这里"，而不是怀疑应用坏了。
     *
     * 返回是否可用（已存在或创建成功）。
     */
    fun ensure(projectPath: String): Boolean {
        val dir = File(projectPath)
        if (dir.isDirectory) return true
        if (!dir.mkdirs() && !dir.isDirectory) return false
        runCatching {
            File(dir, "README.md").writeText(
                """
                # 工作区

                这是 IQ Code 的默认工作区（`$projectPath`）。

                你可以把项目放进来，然后在设置里把「项目路径」改成对应目录；
                也可以直接在终端里 `cd` 到任意位置操作 —— 对话里的文件/变更面板
                会跟随「项目路径」设置。

                - 终端：切到「终端」页，里面是真正的 bash
                - 文件：切到「文件」页浏览当前项目
                - 变更：切到「变更」页查看 git 状态（先在这里 `git init` 才会有内容）
                """.trimIndent(),
            )
        }
        return dir.isDirectory
    }

    /** 当前项目路径是否已存在且是目录。 */
    fun exists(path: String): Boolean = path.isNotBlank() && File(path).isDirectory
}
