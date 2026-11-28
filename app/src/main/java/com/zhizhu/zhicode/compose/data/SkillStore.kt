package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFile
import com.zhizhu.zhicode.compose.model.SkillFileNode
import com.zhizhu.zhicode.compose.model.SkillScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

internal object SkillStore {

    /** 名字规则与引擎侧一致：字母、数字、`.`、`_`、`-`，长度 1–64。 */
    private val NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")

    private const val FILE_NAME = "SKILL.md"
    private const val SKILLS_DIR = "skills"

    /** 详情页文件树的深度上限与节点总数上限。 */
    private const val TREE_MAX_DEPTH = 4
    private const val TREE_MAX_FILES = 400

    fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

    fun isValidFileName(name: String): Boolean =
        NAME_PATTERN.matches(name) && name != "." && name != ".."

    /** 解析技能目录里的一个文件，并确认它**没有跑出技能目录**。 */
    private fun fileInSkillDir(dir: File, fileName: String): File {
        require(isValidFileName(fileName)) { "文件名只能包含字母、数字、. _ -（1–64 个字符）" }
        val target = File(dir, fileName)
        val root = dir.canonicalPath + File.separator
        require(target.canonicalPath.startsWith(root)) { "文件名不合法：$fileName" }
        return target
    }

    /** 解析技能目录里的一个**相对路径**（允许 `sub/doc.md` 这种子目录）。 */
    private fun fileInSkillDirByPath(dir: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "文件名不能为空" }
        var target = dir
        relativePath.split('/').forEach { segment ->
            require(segment.isNotEmpty()) { "路径不合法：$relativePath" }
            target = fileInSkillDir(target, segment)
        }
        return target
    }

    /** 某个作用域的技能根目录。 */
    private fun scopeDir(projectPath: String, scope: SkillScope): File = when (scope) {
        SkillScope.PROJECT -> File(projectPath)
        SkillScope.USER -> File(TermuxConstants.TERMUX_HOME_DIR_PATH)
    }

    /** 写入目标根目录。新建、保存、界面显示的路径都以它为准。 */
    fun root(projectPath: String, scope: SkillScope): File =
        File(TermuxConstants.dataDirIn(scopeDir(projectPath, scope)), SKILLS_DIR)

    fun directoryOf(projectPath: String, scope: SkillScope, name: String): File =
        File(root(projectPath, scope), name)

    fun fileOf(projectPath: String, scope: SkillScope, name: String): File =
        File(directoryOf(projectPath, scope, name), FILE_NAME)

    fun list(projectPath: String): List<SkillEntry> {
        val out = mutableListOf<SkillEntry>()
        // 按**规范路径**去重（不是名字）：软链接指到同一个目录也算同一个。
        val seen = mutableSetOf<String>()
        SkillScope.entries.forEach { scope ->
            val rootDir = root(projectPath, scope)
            val children = rootDir.listFiles() ?: return@forEach
            children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.forEach { dir ->
                if (!seen.add(dir.canonicalPath)) return@forEach
                // 走同一个解析点（而不是 File(dir, FILE_NAME)）：路径解析只留一处
                val file = fileInSkillDir(dir, FILE_NAME)
                if (!file.isFile) return@forEach
                out += SkillEntry(
                    name = dir.name,
                    scope = scope,
                    path = file.absolutePath,
                    summary = describe(file),
                    sizeLabel = humanSize(file.length()),
                )
            }
        }
        return out
    }

    /** 取 SKILL.md 的 `description` 作为列表副标题。 */
    private fun describe(file: File): String = runCatching {
        val head = file.bufferedReader().use { reader ->
            val lines = mutableListOf<String>()
            while (lines.size < 40) {
                val line = reader.readLine() ?: break
                lines += line
            }
            lines.joinToString("\n")
        }
        // 与 nameFromContent 同一条规则：只在 frontmatter 块里找。
        // 全文扫描会把正文里的 `description:` 当成元数据。
        val description = frontMatterBlock(head)?.let { bareValue(frontMatterValue(it, "description").orEmpty()) }
        description?.takeIf { it.isNotBlank() } ?: "未写说明"
    }.getOrElse { "无法读取：${it.message ?: "未知原因"}" }

    /** 从 YAML 前置元数据里取一个字段。 */
    private fun frontMatterValue(text: String, key: String): String? {
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("$key:")) return trimmed.removePrefix("$key:").trim()
        }
        return null
    }

    private fun frontMatterBlock(content: String): String? {
        val lines = content.lineSequence().toList()
        val start = lines.indexOfFirst { it.trim() == "---" }
        if (start < 0) return null
        val rest = lines.drop(start + 1)
        val end = rest.indexOfFirst { it.trim() == "---" }
        if (end < 0) return null
        return rest.take(end).joinToString("\n")
    }

    /** 去掉 YAML 里数值两侧的引号：`name: "my-skill"` 与 `name: my-skill` 等价。 */
    private fun bareValue(raw: String): String {
        val text = raw.trim()
        if (text.length >= 2) {
            val first = text.first()
            if ((first == '"' || first == '\'') && text.last() == first) {
                return text.substring(1, text.length - 1).trim()
            }
        }
        return text
    }

    /** 从**粘贴进来的 SKILL.md 内容**里解析技能名。 */
    fun nameFromContent(content: String): String? =
        frontMatterBlock(content)?.let { bareValue(frontMatterValue(it, "name").orEmpty()) }
            ?.takeIf { it.isNotBlank() }

    fun listTree(projectPath: String, scope: SkillScope, skillName: String): List<SkillFileNode> {
        val dir = directoryOf(projectPath, scope, skillName)
        if (!dir.isDirectory) return emptyList()
        return buildNodes(dir, prefix = "", depth = 0, seen = intArrayOf(0))
    }

    private fun buildNodes(dir: File, prefix: String, depth: Int, seen: IntArray): List<SkillFileNode> {
        val children = dir.listFiles().orEmpty()
        val dirs = mutableListOf<SkillFileNode>()
        val files = mutableListOf<SkillFile>()
        children.forEach { child ->
            if (seen[0] >= TREE_MAX_FILES) return@forEach
            val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            when {
                child.isDirectory && depth < TREE_MAX_DEPTH -> {
                    seen[0]++
                    dirs += SkillFileNode.DirNode(
                        name = child.name,
                        relativePath = relative,
                        children = buildNodes(child, relative, depth + 1, seen),
                    )
                }

                child.isFile -> {
                    seen[0]++
                    files += SkillFile(
                        name = child.name,
                        relativePath = relative,
                        sizeLabel = humanSize(child.length()),
                        primary = relative == FILE_NAME,
                    )
                }
                // 目录但已到深度上限：整棵子树不再展开（列出来也只会被截断成空目录）
            }
        }
        val sortedFiles = files.sortedWith(compareBy({ !it.primary }, { it.name.lowercase() }))
        return dirs + sortedFiles.map { SkillFileNode.FileNode(it) }
    }

    fun nameConflict(skillName: String, content: String): String? = when (val declared = nameFromContent(content)) {
        null -> "SKILL.md 的 frontmatter 里必须有一行 name: $skillName"
        skillName -> null
        else -> "name 必须是「$skillName」，现在写的是「$declared」；要改名请新建技能"
    }

    /** 列出技能目录里的文件（扁平，含子目录里的文件）。 */
    fun listFiles(projectPath: String, scope: SkillScope, skillName: String): List<SkillFile> =
        flatten(listTree(projectPath, scope, skillName))

    /** 把树摊平成文件列表（深度优先，保持树里的顺序）。 */
    fun flatten(nodes: List<SkillFileNode>): List<SkillFile> = nodes.flatMap { node ->
        when (node) {
            is SkillFileNode.FileNode -> listOf(node.file)
            is SkillFileNode.DirNode -> flatten(node.children)
        }
    }

    /** 读技能目录里的一个文件（[relativePath] 可以是 `sub/doc.md`）。 */
    fun readFile(projectPath: String, scope: SkillScope, skillName: String, relativePath: String): Result<String> =
        runCatching {
            val file = fileInSkillDirByPath(directoryOf(projectPath, scope, skillName), relativePath)
            if (!file.isFile) throw IllegalArgumentException("找不到文件：$relativePath")
            file.readText()
        }

    /** 写技能目录里的一个文件（不存在则创建，子目录会自动建）。 */
    fun writeFile(
        projectPath: String,
        scope: SkillScope,
        skillName: String,
        relativePath: String,
        body: String,
    ): Result<Unit> = runCatching {
        val dir = directoryOf(projectPath, scope, skillName)
        if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("无法创建目录：${dir.absolutePath}")
        if (relativePath == FILE_NAME) {
            nameConflict(skillName, body)?.let { throw IllegalArgumentException(it) }
        }
        val target = fileInSkillDirByPath(dir, relativePath)
        target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IllegalStateException("无法创建目录：${it.absolutePath}") }
        target.writeText(body)
        Unit
    }

    /** 删除技能目录里的一个文件。 */
    fun deleteFile(projectPath: String, scope: SkillScope, skillName: String, relativePath: String): Result<Unit> =
        runCatching {
            require(relativePath != FILE_NAME) { "SKILL.md 是技能本体，不能单独删除；要删请删整个技能" }
            val target = fileInSkillDirByPath(directoryOf(projectPath, scope, skillName), relativePath)
            if (!target.isFile) throw IllegalArgumentException("找不到文件：$relativePath")
            if (!target.delete()) throw IllegalStateException("无法删除：$relativePath")
            Unit
        }

    /** 技能目录里是否已有该文件（新建文件对话框用它挡重名）。 */
    fun fileExists(projectPath: String, scope: SkillScope, skillName: String, relativePath: String): Boolean =
        runCatching { fileInSkillDirByPath(directoryOf(projectPath, scope, skillName), relativePath).isFile }
            .getOrDefault(false)

    fun read(projectPath: String, scope: SkillScope, name: String): Result<String> =
        readFile(projectPath, scope, name, FILE_NAME)

    /** 新建技能。 */
    fun create(
        projectPath: String,
        scope: SkillScope,
        name: String,
        content: String? = null,
    ): Result<Boolean> = runCatching {
        require(isValidName(name)) { "名称只能包含字母、数字、. _ -（1–64 个字符）" }
        // 带内容建目录时也过一遍名字一致性：界面那边名字是由内容解析出来的，
        // 正常情况下必然一致；但"两个来源恰好一致"不该是**约定**，而是**校验过的**。
        if (content != null) nameConflict(name, content)?.let { throw IllegalArgumentException(it) }
        val dir = directoryOf(projectPath, scope, name)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建目录：${dir.absolutePath}")
        if (!dir.isDirectory) throw IllegalStateException("同名路径已存在且不是目录：${dir.absolutePath}")
        val file = fileInSkillDir(dir, FILE_NAME)
        if (file.exists()) return@runCatching false
        file.writeText(content ?: template(name))
        true
    }

    /** 删除技能。 */
    fun delete(projectPath: String, scope: SkillScope, name: String): Result<Unit> = runCatching {
        val dir = directoryOf(projectPath, scope, name)
        if (!dir.isDirectory) throw IllegalArgumentException("找不到技能目录：${dir.absolutePath}")
        if (!dir.deleteRecursively()) throw IllegalStateException("无法删除：${dir.absolutePath}")
        Unit
    }

    /** 新技能的起始内容。带上前置元数据，用户照着填即可。 */
    private fun template(name: String): String = """
        |---
        |name: $name
        |description: 请描述这个 Skill 的用途
        |---
        |
        |# $name
        |
        |在这里编写给 ZhiCode 的技能说明。
        |
    """.trimMargin()

    val starterTemplate: String = """
        |---
        |name: <技能名，只能用字母、数字、. _ ->
        |description: 一句话说明这个技能在什么时候该用
        |---
        |
        |# <技能名>
        |
        |写给 ZhiCode 的说明：什么情况下用它、要按什么步骤做、有哪些注意事项。
        |
    """.trimMargin()

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
