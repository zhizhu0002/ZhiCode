package com.zhizhu.zhicode

import android.system.Os
import android.system.OsConstants
import java.io.File

internal object FileTree {

    /** 目录项的种类，判断时**不跟随**符号链接。 */
    internal const val ABSENT = 0
    internal const val REGULAR = 1
    internal const val DIRECTORY = 2
    internal const val SYMLINK = 3

    /** 「不跟随符号链接地看一个目录项」这件事本身。 */
    internal fun interface Kind {
        fun of(file: File): Int
    }

    /** 真机实现。 */
    internal val ANDROID_KIND = Kind { file ->
        val stat = runCatching { Os.lstat(file.absolutePath) }.getOrNull()
            ?: return@Kind ABSENT
        when {
            OsConstants.S_ISLNK(stat.st_mode) -> SYMLINK
            OsConstants.S_ISDIR(stat.st_mode) -> DIRECTORY
            else -> REGULAR
        }
    }

    /** 递归删除 [file]。 */
    fun deleteRecursive(file: File?, kind: Kind = ANDROID_KIND): Boolean {
        if (file == null) return true
        // 只问一次，避免文件系统在两次判断之间变化导致前后不一致。
        val what = kind.of(file)
        if (what == ABSENT) return true
        // 只有**真目录**才递归。若顺着符号链接递归进去，删掉的是链接指向的那个目录里的
        // 内容（等于删了别人的文件）；链接本身只需要 unlink。
        if (what == DIRECTORY) {
            for (child in file.listFiles().orEmpty()) deleteRecursive(child, kind)
        }
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /** 残留项摘要。 */
    fun describe(file: File, kind: Kind = ANDROID_KIND): String {
        if (kind.of(file) == ABSENT) return "无残留"
        val children = file.listFiles().orEmpty()
        val shown = children.take(3).joinToString("、") { it.name }
        val more = if (children.size > 3) " 等" else ""
        return "残留 ${children.size} 项（$shown$more）"
    }
}
