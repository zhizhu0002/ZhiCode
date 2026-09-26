package com.zhizhu.zhicode

import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * 目录树删除。
 *
 * <h3>为什么值得单独一个文件</h3>
 * 真机上出现过「内置环境装好、能正常用，但再点一次初始化就永远失败」，报的是
 * `Cannot create staging prefix: …/usr-staging`，看起来像「建不出目录」，实际是**删不干净**：
 *
 * 1. 暂存目录里有 `Os.symlink` 建出的链接，其中一部分是**悬空的**（目标要么是尚未换入的
 *    前缀下的绝对路径，要么是在 staging 里解析不到的相对路径）；
 * 2. `File.exists()` 与 `File.isDirectory()` **会跟随符号链接**：悬空链接 `exists()` 为
 *    false，于是老实现把它判成「不存在」直接跳过，这些链接**从来没被删过**；
 * 3. 父目录因此永远非空、永远删不掉，下一轮 `mkdirs()` 返回 false（它对已存在的目录也返回
 *    false），错误就被报成了「建不出来」——一个把原因说反的错误消息。
 *
 * 复现与实测输出记在 `docs/licensing.md` 的「暂存目录删不掉」一节。
 *
 * <h3>为什么不直接用 {@code java.nio.file}</h3>
 * 它要 API 26，而本工程要跑到 Android 7（同 `CopyTool` / `TextFiles` 里的说明）。
 * `Os.lstat` 在 API 21 就有，而且从语义上就是**不跟随**链接的那一个 —— 正是这里需要的。
 * 单测在 JVM 上跑，所以判定做成可注入的（[Kind]）：测试传一个用 NIO 实现的判定，
 * 递归与「不跟随」的规则本身两边共用同一份代码。
 */
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

    /**
     * 真机实现。`Os.lstat` 返回链接自身的状态（不是目标的），
     * 所以悬空链接在这里是 [SYMLINK] 而不是「不存在」。
     */
    internal val ANDROID_KIND = Kind { file ->
        val stat = runCatching { Os.lstat(file.absolutePath) }.getOrNull()
            ?: return@Kind ABSENT
        when {
            OsConstants.S_ISLNK(stat.st_mode) -> SYMLINK
            OsConstants.S_ISDIR(stat.st_mode) -> DIRECTORY
            else -> REGULAR
        }
    }

    /**
     * 递归删除 [file]。
     *
     * @return 是否已彻底删除；路径本来就不存在也算成功（幂等）。
     */
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

    /**
     * 残留项摘要。只在删不干净时用，目的是让失败消息说清「还剩什么」，
     * 而不是像原来那样把原因说反。
     */
    fun describe(file: File, kind: Kind = ANDROID_KIND): String {
        if (kind.of(file) == ABSENT) return "无残留"
        val children = file.listFiles().orEmpty()
        val shown = children.take(3).joinToString("、") { it.name }
        val more = if (children.size > 3) " 等" else ""
        return "残留 ${children.size} 项（$shown$more）"
    }
}
