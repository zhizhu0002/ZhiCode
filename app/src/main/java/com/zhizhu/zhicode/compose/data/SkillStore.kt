package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Skill 的读写。
 *
 * ## 目录约定（必须与引擎一致）
 *
 * 引擎的 `SkillTool` 在这两个位置找 `SKILL.md`：
 * - 项目级：`<projectDirectory>/.zhicode/skills/<name>/SKILL.md`
 * - 用户级：`$HOME/.zhicode/skills/<name>/SKILL.md`
 *
 * 所以这里**不能**自己发明目录结构，否则界面上建好的技能 Agent 根本找不到
 * （`SkillTool` 会返回 `Skill not found: <name>`）。
 * 两边的根目录都取自 `TermuxConstants.dataDirIn`，是同一个来源。
 *
 * ## 命名约束
 *
 * 名字直接当目录名用，所以必须限制字符集：`SkillTool` 会拒绝含 `/` 或 `..` 的名字，
 * 界面这边更要在**创建时**就挡住——让非法名字连目录都建不出来，
 * 好过建完之后 Agent 拒绝加载（那种错误信息离用户很远）。
 */
internal object SkillStore {

    /** 名字规则与引擎侧一致：字母、数字、`.`、`_`、`-`，长度 1–64。 */
    private val NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")

    private const val FILE_NAME = "SKILL.md"
    private const val SKILLS_DIR = "skills"

    fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

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

    /**
     * 列出所有技能。
     *
     * 项目级与用户级都会扫；两层里同名时两份都保留并标明作用域
     * ——用户可能故意在项目里放一个覆盖版本，悄悄隐藏用户级那一条会让人以为它被删了。
     */
    fun list(projectPath: String): List<SkillEntry> {
        val out = mutableListOf<SkillEntry>()
        SkillScope.entries.forEach { scope ->
            val rootDir = root(projectPath, scope)
            val children = rootDir.listFiles() ?: return@forEach
            children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.forEach { dir ->
                val file = File(dir, FILE_NAME)
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

    /**
     * 取 SKILL.md 的 `description` 作为列表副标题。
     *
     * 只读文件开头若干行：技能文件可能很长，为了列表显示把整个文件读进来不值得。
     * 读不到就明确说"未写说明"，而不是显示空串让人以为是加载失败。
     */
    private fun describe(file: File): String = runCatching {
        val head = file.bufferedReader().use { reader ->
            val lines = mutableListOf<String>()
            while (lines.size < 40) {
                val line = reader.readLine() ?: break
                lines += line
            }
            lines.joinToString("\n")
        }
        val description = frontMatterValue(head, "description")
        description?.takeIf { it.isNotBlank() } ?: "未写说明"
    }.getOrElse { "无法读取：${it.message ?: "未知原因"}" }

    /**
     * 从 YAML 前置元数据里取一个字段。
     *
     * 只做最朴素的 `key: value` 匹配，不引 YAML 解析器：技能头部就那两三行，
     * 而且用户手写的内容各种不规范，严格解析反而更容易整个失败。
     */
    private fun frontMatterValue(text: String, key: String): String? {
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("$key:")) return trimmed.removePrefix("$key:").trim()
        }
        return null
    }

    fun read(projectPath: String, scope: SkillScope, name: String): Result<String> = runCatching {
        val file = fileOf(projectPath, scope, name)
        if (!file.isFile) throw IllegalArgumentException("找不到技能文件：${file.absolutePath}")
        file.readText()
    }

    /**
     * 新建技能。
     *
     * 若目录已存在**不覆盖**：用户可能已经写了内容，静默覆盖等于丢数据。
     * 返回 true 表示"新建了模板"，false 表示"已存在，直接打开编辑"。
     */
    fun create(projectPath: String, scope: SkillScope, name: String): Result<Boolean> = runCatching {
        require(isValidName(name)) { "名称只能包含字母、数字、. _ -（1–64 个字符）" }
        val dir = directoryOf(projectPath, scope, name)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建目录：${dir.absolutePath}")
        if (!dir.isDirectory) throw IllegalStateException("同名路径已存在且不是目录：${dir.absolutePath}")
        val file = File(dir, FILE_NAME)
        if (file.exists()) return@runCatching false
        file.writeText(template(name))
        true
    }

    /** 保存内容。文件不存在时也允许写入（编辑器里的"另存"路径）。 */
    fun save(projectPath: String, scope: SkillScope, name: String, body: String): Result<Unit> = runCatching {
        require(isValidName(name)) { "名称只能包含字母、数字、. _ -（1–64 个字符）" }
        val dir = directoryOf(projectPath, scope, name)
        if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("无法创建目录：${dir.absolutePath}")
        File(dir, FILE_NAME).writeText(body)
        Unit
    }

    /**
     * 删除技能。
     *
     * 用递归删除：技能目录里可能还有参考文档等附加文件，
     * 只删 `SKILL.md` 会留下一个空壳目录，列表里看起来像"技能还在但打不开"。
     */
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
        |在这里编写给蜘蛛的技能说明。
        |
    """.trimMargin()

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
