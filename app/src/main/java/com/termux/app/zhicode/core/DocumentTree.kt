package com.termux.app.zhicode.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/** 文档 ID ↔ Debian 容器根目录真实路径的映射，以及容器内软链接感知的边界校验。 */
object DocumentTree {

    const val ROOT_DOCUMENT_ID = "debian"
    const val ROOT_ID = "zhicode-debian"
    const val ROOT_TITLE = "ZhiCode Debian"

    private const val MAX_SYMLINK_DEPTH = 32

    private val CONTAINER_TOP_DIRS = setOf(
        "bin", "boot", "etc", "home", "lib", "lib64", "media",
        "mnt", "opt", "root", "run", "sbin", "srv", "tmp", "usr", "var",
    )

    private val HOST_ESCAPE_PREFIXES = listOf(
        "/storage/", "/sdcard", "/data/", "/system/", "/vendor/",
        "/apex/", "/product/", "/proc/", "/sys/", "/dev/",
    )

    /** 对文件管理器不可见的内部元数据目录名。 */
    val privateNames: Set<String> = linkedSetOf(
        "." + com.termux.shared.termux.TermuxConstants.BRAND_SLUG,
        ".termux",
    )

    fun isPrivateName(name: String?): Boolean = name != null && privateNames.contains(name)

    @Throws(IOException::class)
    fun documentIdFor(root: File, file: File): String {
        val base = root.canonicalFile
        val target = canonicalUnderRoot(root, file)
        if (target == base) return ROOT_DOCUMENT_ID
        var relative = target.path.substring(base.path.length)
        while (relative.startsWith(File.separator)) relative = relative.substring(1)
        return ROOT_DOCUMENT_ID + "/" + relative.replace(File.separatorChar, '/')
    }

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
        for (segment in relative.split('/')) {
            if (segment == "." || segment == "..") throw IOException("非法路径：$documentId")
            if (isPrivateName(segment)) throw IOException("受保护目录：$segment")
        }
        return canonicalUnderRoot(root, File(root, relative))
    }

    /** 确认 [file] 解析所有软链接后仍落在 [root] 内；兼容 Debian 容器内部的绝对路径软链接。 */
    @Throws(IOException::class)
    fun canonicalUnderRoot(root: File, file: File): File {
        val canonicalRoot = root.canonicalFile
        val rootPath = canonicalRoot.path
        val normalizedInput = normalizeAbsoluteSegments(file.absolutePath)
        val normalizedRoot = normalizeAbsoluteSegments(rootPath)

        val relativeFromLexical = when {
            normalizedInput == normalizedRoot -> emptyList()
            normalizedInput.startsWith("$normalizedRoot/") ->
                normalizedInput.substring(normalizedRoot.length + 1)
                    .split('/')
                    .filter { it.isNotEmpty() }
            else -> null
        }

        if (relativeFromLexical == null) {
            val canonical = file.canonicalFile
            if (canonical != canonicalRoot && !canonical.path.startsWith(rootPath + File.separator)) {
                throw IOException("路径越界：${file.path}")
            }
            return canonical
        }

        return resolveSegmentsInsideRoot(canonicalRoot, relativeFromLexical, file.path)
    }

    @Throws(IOException::class)
    private fun resolveSegmentsInsideRoot(
        canonicalRoot: File,
        initialSegments: List<String>,
        originalPath: String,
    ): File {
        val current = ArrayDeque<String>()
        val pending = ArrayDeque(initialSegments)
        var symlinksFollowed = 0

        while (pending.isNotEmpty()) {
            val segment = pending.removeFirst()
            when (segment) {
                "", "." -> continue
                ".." -> {
                    if (current.isEmpty()) throw IOException("路径越界：$originalPath")
                    current.removeLast()
                    continue
                }
            }
            if (isPrivateName(segment)) throw IOException("受保护目录：$segment")

            current.addLast(segment)
            val stepFile = toHostFile(canonicalRoot, current)
            if (!Files.isSymbolicLink(stepFile.toPath())) continue

            if (++symlinksFollowed > MAX_SYMLINK_DEPTH) {
                throw IOException("符号链接层数过多：$originalPath")
            }

            val rawTarget = Files.readSymbolicLink(stepFile.toPath()).toString().replace('\\', '/')
            current.removeLast()

            if (rawTarget == canonicalRoot.path.replace('\\', '/') ||
                rawTarget.startsWith(canonicalRoot.path.replace('\\', '/') + "/")
            ) {
                current.clear()
                val stripped = rawTarget.substring(canonicalRoot.path.length).trimStart('/')
                prependSegments(pending, stripped)
            } else if (rawTarget.startsWith("/")) {
                if (HOST_ESCAPE_PREFIXES.any { rawTarget == it.trimEnd('/') || rawTarget.startsWith(it) }) {
                    throw IOException("路径越界：$originalPath")
                }
                val trimmed = rawTarget.trimStart('/')
                val firstSeg = trimmed.substringBefore('/')
                if (firstSeg !in CONTAINER_TOP_DIRS) {
                    throw IOException("路径越界：$originalPath")
                }
                current.clear()
                prependSegments(pending, trimmed)
            } else if (rawTarget.length >= 2 && rawTarget[1] == ':') {
                throw IOException("路径越界：$originalPath")
            } else {
                prependSegments(pending, rawTarget)
            }
        }

        val resolved = toHostFile(canonicalRoot, current)
        // 无符号链接的普通前缀仍用宿主规范路径兜底。
        if (!Files.isSymbolicLink(resolved.toPath())) {
            val parent = resolved.parentFile
            if (parent == null) {
                if (resolved != canonicalRoot) throw IOException("路径越界：$originalPath")
            } else if (!Files.exists(resolved.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val canonical = resolved.canonicalFile
                if (canonical != canonicalRoot && !canonical.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw IOException("路径越界：$originalPath")
                }
                return canonical
            }
        }
        return resolved
    }

    private fun prependSegments(pending: ArrayDeque<String>, path: String) {
        val parts = path.split('/').filter { it.isNotEmpty() }
        for (i in parts.indices.reversed()) {
            pending.addFirst(parts[i])
        }
    }

    private fun toHostFile(root: File, segments: Collection<String>): File {
        var cur = root
        for (seg in segments) cur = File(cur, seg)
        return cur
    }

    private fun normalizeAbsoluteSegments(path: String): String {
        val parts = path.replace('\\', '/').split('/')
        val stack = ArrayList<String>(parts.size)
        for (p in parts) {
            when (p) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(p)
            }
        }
        return if (path.startsWith("/")) "/" + stack.joinToString("/") else stack.joinToString("/")
    }

    /** [child] 是否在 [parent] 之下或就是它。用于 SAF 的 `isChildDocument`。 */
    fun isChildOf(parent: File?, child: File?): Boolean {
        if (parent == null || child == null) return false
        return runCatching {
            val a = parent.canonicalFile
            val b = canonicalUnderRoot(a, child)
            b == a || b.path.startsWith(a.path + File.separator)
        }.getOrDefault(false)
    }

    @Throws(IOException::class)
    fun safeName(name: String?): String {
        FileOps.nameError(name)?.let { throw IOException(it) }
        val cleaned = name!!.trim()
        if (isPrivateName(cleaned)) throw IOException("受保护目录：$cleaned")
        return cleaned
    }

    /** 递归删除，不跟符号链接。 */
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
