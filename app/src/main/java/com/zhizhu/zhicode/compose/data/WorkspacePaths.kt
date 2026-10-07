package com.zhizhu.zhicode.compose.data

import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * 工作区路径。
 *
 * 为什么需要这个：应用原来把"当前项目"写死成开发本机上的一个固定路径
 * （`~/projects/…`），那是**开发本应用时用的**路径。新装的应用里它并不存在，后果是：
 * - 文件面板一直显示「目录不存在」；
 * - 终端的工作目录回退到 home；
 * - 会话按项目分目录存储，项目不存在时会话全落在一个"幽灵项目"键下。
 *
 * 默认值改过两次，现在的结论是 **`$HOME` 本身**：
 * - 第一版用了写死的开发路径（上面说的那些问题）；
 * - 第二版用 `$HOME/workspace` 并主动创建 —— 方向对了，但等于**架空**了 HOME：
 *   终端确实落在 `files/home`，文件面板却停在 `files/home/workspace`，
 *   那里只有一个自动生成的 README.md，看着像坏了；
 * - 现在直接用 `$HOME`，与 Termux 的约定一致（`cd` 不带参数就是 HOME），
 *   文件面板一打开就能看到 `.zhicode`（会话）、`projects`、`tmp` 这些真实内容。
 *
 * ⚠️ 项目路径**没有持久化**：每次启动都重新取 [defaultProject]，只有运行期
 * 能改（打开某个目录时）。所以换默认值不需要写迁移代码。
 */
internal object WorkspacePaths {

    /**
     * 默认项目路径 = `$HOME`。
     *
     * 不主动创建：HOME 由 [com.zhizhu.zhicode.RuntimeInstaller] 装环境时建好
     * （它会先 `mkdirs` 出 HOME 下的 `tmp`），比这里早。
     */
    fun defaultProject(): String = TermuxConstants.TERMUX_HOME_DIR_PATH.trimEnd('/')

    /** 侧栏/顶栏显示的项目名：取路径最后一段。 */
    fun nameOf(path: String): String =
        path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

    /**
     * 确保项目目录存在。
     *
     * 这里**只建目录**，不再往里写 README：
     * 默认值是 HOME，它本来就有内容；而这个函数也会被用户指定的路径调用，
     * 往别人自己的目录里塞一个"这是 ZhiCode 的默认工作区"的说明文件并不合适。
     *
     * 返回是否可用（已存在或创建成功）。
     */
    fun ensure(projectPath: String): Boolean {
        val dir = File(projectPath)
        if (dir.isDirectory) return true
        if (!dir.mkdirs() && !dir.isDirectory) return false
        return dir.isDirectory
    }

    /** 当前项目路径是否已存在且是目录。 */
    fun exists(path: String): Boolean = path.isNotBlank() && File(path).isDirectory
}
