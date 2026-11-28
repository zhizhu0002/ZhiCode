package com.zhizhu.zhicode.compose.data

import com.termux.shared.termux.TermuxConstants
import java.io.File

internal object WorkspacePaths {

    /** 默认项目路径 = `$HOME`。 */
    fun defaultProject(): String = TermuxConstants.TERMUX_HOME_DIR_PATH.trimEnd('/')

    /** 侧栏/顶栏显示的项目名：取路径最后一段。 */
    fun nameOf(path: String): String =
        path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

    /** 确保项目目录存在。 */
    fun ensure(projectPath: String): Boolean {
        val dir = File(projectPath)
        if (dir.isDirectory) return true
        if (!dir.mkdirs() && !dir.isDirectory) return false
        return dir.isDirectory
    }

    /** 当前项目路径是否已存在且是目录。 */
    fun exists(path: String): Boolean = path.isNotBlank() && File(path).isDirectory
}
