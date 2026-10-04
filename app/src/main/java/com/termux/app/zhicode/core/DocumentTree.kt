package com.termux.app.zhicode.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * 文档 ID ↔ 私有目录真实路径的映射，以及配套的边界校验。
 *
 * ## 它解决的是什么问题
 *
 * 应用 HOME 在私有目录里（`0700` + SELinux 标签），**任何第三方文件管理器都进不去**，
 * 不给 root 没有例外。所以「让文件管理器看到 HOME」只有两条路：
 *
 * 1. 把内容真的搬到共享存储 —— 那一片挂载是 `noexec`
 *    （`/proc/mounts`: `/dev/fuse /storage/emulated fuse rw,…,noexec`），
 *    搬过去之后 `./gradlew` 这类脚本就不能直跑了；
 * 2. 把 HOME **发布**成一个 `DocumentsProvider`，文件管理器通过 SAF 读我们。
 *    HOME 一个字节都不搬，可执行权限、符号链接、apt 全部照旧。
 *
 * 本类是第 2 条路里唯一会算错的那一半。Android 那一层
 * （[com.zhizhu.zhicode.ZhiDocumentsProvider]）只负责拼 Cursor 与分发调用 ——
 * 那些是机械动作；会错的是 ID 与路径的互相转换、名字校验、越界判定。
 *
 * ## 为什么本文件不 import android.*
 *
 * 这里每一行都是字符串与路径运算。不依赖 Android 才能进
 * `test-jvm-fast.sh` 的秒级回路（`DocumentTreeTest`）—— 而这几类错在真机上
 * **全都不可诊断**：文件管理器里某一项打不开、新建报「操作失败」，
 * 或者最糟的一种，越界成功了却没人发现。
 *
 * ## 三个边界，每个都对应一种越界方式
 *
 * 1. [fileForId]：先校验 `home` 前缀，再逐段拒绝 `.` 与 `..`；
 * 2. [canonicalUnderRoot]：用 `getCanonicalPath()` 确认结果真的落在 HOME 之内。
 *    **只看字符串不够** —— HOME 里就有 `storage/shared -> /storage/emulated/0`
 *    这种链接，它的路径里没有 `..`、也不是私有目录名，逐段黑名单拦不住它，
 *    只有规范化之后才看得出它指到了外面；
 * 3. [safeName]：新建/重命名用的名字。带 `/` 的名字会写到**别的目录**去，
 *    而用户在原来的位置找不到时只会以为「操作失败」。
 */
object DocumentTree {

    /** 根文档的 ID。子项 ID 形如 `home/<相对路径>`。 */
    const val ROOT_DOCUMENT_ID = "home"

    /** 根在 SAF 里的标识（与文档 ID 是两个概念，别混用）。 */
    const val ROOT_ID = "zhicode-home"

    /** 根在文件管理器里显示的名字。 */
    const val ROOT_TITLE = "ZhiCode HOME"

    /**
     * 对文件管理器**不可见**的目录名。
     *
     * 应用自己的元数据（`.zhicode`：技能、子代理定义、任务、记忆）不出现在列表里、
     * 按 ID 也进不去。用户要的是自己的文件；把这些内部状态交给系统文件管理器随手改，
     * 坏了之后的现象（技能突然不生效、子代理不见了）离原因非常远。
     *
     * ⚠️ 这一条**只对文件管理器生效**。应用内自己的文件面板（`FileOps`）
     * 不该被挡住 —— 技能文件写错了就是要能就地修，那正是那个面板的用途。
     *
     * `.termux` 一并挡上：它是历史遗留的路径名，我们这边不写，
     * 但别让一个旧残留变成可写的入口。
     */
    val privateNames: Set<String> = linkedSetOf(
        "." + com.termux.shared.termux.TermuxConstants.BRAND_SLUG,
        ".termux",
    )

    fun isPrivateName(name: String?): Boolean = name != null && privateNames.contains(name)

    // ------------------------------------------------------------ ID ↔ 路径

    /**
     * 真实路径 → 文档 ID。
     *
     * 根自身是 `home`，其余是 `home/<相对路径>`（分隔符一律 `/`，
     * 与底层 `File.separator` 无关 —— 不同设备上它可能是 `\`）。
     *
     * @throws IOException 路径规范化后落在 [root] 之外
     */
    @Throws(IOException::class)
    fun documentIdFor(root: File, file: File): String {
        val base = canonicalUnderRoot(root, root)
        val target = canonicalUnderRoot(root, file)
        if (target == base) return ROOT_DOCUMENT_ID
        var relative = target.path.substring(base.path.length)
        while (relative.startsWith(File.separator)) relative = relative.substring(1)
        return ROOT_DOCUMENT_ID + "/" + relative.replace(File.separatorChar, '/')
    }

    /**
     * 文档 ID → 真实路径。
     *
     * 三道校验缺一不可：前缀必须是 `home`、每一段都不能是 `.`/`..`、
     * 每一段都不能是私有目录名；最后仍交给 [canonicalUnderRoot] 兜底
     * （符号链接只有那一步看得出来）。
     *
     * @throws IOException ID 非法或规范化后越界
     */
    @Throws(IOException::class)
    fun fileForId(root: File, documentId: String?): File {
        if (documentId == null ||
            (documentId != ROOT_DOCUMENT_ID && !documentId.startsWith("$ROOT_DOCUMENT_ID/"))
        ) {
            throw IOException("无效文档 ID：$documentId")
        }
        val relative = if (documentId == ROOT_DOCUMENT_ID) {
            ""
        } else {
            documentId.substring(ROOT_DOCUMENT_ID.length + 1)
        }
        // ⚠️ 按**段**判断，不是 `relative.contains("..")`。
        //
        // 子串判断会连合法名字一起拒掉：一个叫 `a..b.txt` 的文件完全合法，
        // 而我们自己生成的 ID 正是 `home/a..b.txt` —— 子串判断会让它**转不回来**，
        // 表现是文件管理器里这个文件打不开。所以只拒绝"整段就是 . 或 .."的。
        for (segment in relative.split('/')) {
            if (segment == "." || segment == "..") throw IOException("非法路径：$documentId")
            if (isPrivateName(segment)) throw IOException("受保护目录：$segment")
        }
        return canonicalUnderRoot(root, File(root, relative))
    }

    /**
     * 确认 [file] 规范化之后真的在 [root] 之内。
     *
     * 这是**唯一**能拦住符号链接越界的地方。比较时补了分隔符：
     * 只写 `startsWith(rootPath)` 会让 `/…/home-evil` 被误判成在 `/…/home` 之内
     * —— 它们是同一前缀下的两个不同目录。
     */
    @Throws(IOException::class)
    fun canonicalUnderRoot(root: File, file: File): File {
        val canonicalRoot = root.canonicalFile
        val canonical = file.canonicalFile
        if (canonical != canonicalRoot &&
            !canonical.path.startsWith(canonicalRoot.path + File.separator)
        ) {
            throw IOException("路径越界：${file.path}")
        }
        return canonical
    }

    /** [child] 是否在 [parent] 之下或就是它。用于 SAF 的 `isChildDocument`。 */
    fun isChildOf(parent: File?, child: File?): Boolean {
        if (parent == null || child == null) return false
        return runCatching {
            val a = parent.canonicalFile
            val b = child.canonicalFile
            b == a || b.path.startsWith(a.path + File.separator)
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- 名字

    /**
     * 新建 / 重命名用的名字校验，返回清洗过的名字。
     *
     * ⚠️ 名字规则**只有一处实现**：[FileOps.nameError]。这里只做转发，
     * 另加一条它不认识的规则（私有目录名）。
     *
     * 为什么必须共用：应用内文件面板与文件管理器写的是**同一个目录**。
     * 两处口径不同的结果是"一边能建、另一边打不开"——例如一边允许 `" ok "`、
     * 另一边把它 trim 成 `"ok"`，同一个操作在两处产生不同的名字。
     * `DocumentTreeTest.nameRulesAgreeWithFileOps` 钉的就是这一条
     * （它当初确实抓到过一次：`FileOps` 拒绝首尾空格，而这里在静默 trim）。
     *
     * @throws IOException 名字非法
     */
    @Throws(IOException::class)
    fun safeName(name: String?): String {
        FileOps.nameError(name)?.let { throw IOException(it) }
        val cleaned = name!!.trim()
        if (isPrivateName(cleaned)) throw IOException("受保护目录：$cleaned")
        return cleaned
    }

    // ---------------------------------------------------------------- 删除

    /**
     * 递归删除，**不跟符号链接**。
     *
     * 这里的 `storage/` 下的链接指向整块内部存储 —— 跟着链接递归删会把用户的照片、
     * 下载、所有应用数据一起删掉。判据必须是 `Files.isSymbolicLink` 而不是
     * `isDirectory()`：后者对链接返回的是**它指向的目标**的类型。
     *
     * @return 删除失败的条目名；全部成功时返回 null
     */
    fun deleteTree(target: File?): String? {
        if (target == null) return null
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(target.toPath())) {
            return if (target.delete()) null else target.name
        }
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteTree(child)?.let { return it }
            }
        }
        return if (target.delete()) null else target.name
    }

    // ---------------------------------------------------------------- 报告

    /**
     * 自检报告用：发布出去的根是什么、能不能读写、哪些目录被挡住。
     *
     * 与 `EnvDoctor` 的其余部分同一口径：每一项都是**实测值**。
     * 文件管理器里看不到东西时，用户唯一能提供的就是这几行。
     */
    fun describe(root: File?): List<String> {
        val lines = mutableListOf<String>()
        val exists = root != null && root.isDirectory
        lines += "根路径      : ${root?.absolutePath ?: "-"}"
        lines += "存在        : $exists"
        if (root != null && exists) {
            lines += "可读写      : ${root.canRead()} / ${root.canWrite()}"
            val children = root.list()
            lines += "可见条目数  : ${children?.size ?: "列取失败（权限）"}"
        }
        lines += "隐藏目录    : ${privateNames.joinToString(", ")}（列表里不出现、按 ID 也进不去）"
        return lines
    }
}
