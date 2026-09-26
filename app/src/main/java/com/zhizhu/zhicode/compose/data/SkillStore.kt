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
 * 引擎的 `SkillTool` 在这四个位置找 `SKILL.md`（顺序即优先级）：
 * - 项目级：`<projectDirectory>/.zhicode/skills/<name>/SKILL.md`
 * - 项目级旧名：`<projectDirectory>/.iq/skills/<name>/SKILL.md`
 * - 用户级：`$HOME/.zhicode/skills/<name>/SKILL.md`
 * - 用户级旧名：`$HOME/.iq/skills/<name>/SKILL.md`
 *
 * 所以这里**不能**自己发明目录结构，否则界面上建好的技能 Agent 根本找不到
 * （`SkillTool` 会返回 `Skill not found: <name>`）。
 *
 * ## 为什么是四个而不是两个
 *
 * 改名后写入一律落在新名目录；但用户项目目录里的 `.iq/skills` 属于用户的版本库，
 * 我们无权搬动，所以**读取**时两个名字都看，新名优先。
 * 这与引擎侧的 `dataDirCandidatesIn` 完全对应，两边必须同步改。
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

    /**
     * 某个作用域的候选目录，当前名在前、旧名兜底。
     *
     * 顺序来自 `TermuxConstants.dataDirCandidatesIn`，与 `SkillTool` 用的是同一个来源，
     * 这样"界面里排第一的技能"和"Agent 实际加载到的技能"不会是两个。
     */
    private fun candidateRoots(projectPath: String, scope: SkillScope): List<File> {
        val scopeDir = when (scope) {
            SkillScope.PROJECT -> File(projectPath)
            SkillScope.USER -> File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        }
        return TermuxConstants.dataDirCandidatesIn(scopeDir).map { File(it, SKILLS_DIR) }
    }

    /** 写入目标根目录（当前名）。新建、保存、界面显示的路径都以它为准。 */
    fun root(projectPath: String, scope: SkillScope): File = candidateRoots(projectPath, scope).first()

    fun directoryOf(projectPath: String, scope: SkillScope, name: String): File =
        File(root(projectPath, scope), name)

    fun fileOf(projectPath: String, scope: SkillScope, name: String): File =
        File(directoryOf(projectPath, scope, name), FILE_NAME)

    /** 某个技能实际所在的那份目录（旧名兜底）；两处都没有时返回写入目标。 */
    private fun readableDirectoryOf(projectPath: String, scope: SkillScope, name: String): File =
        candidateRoots(projectPath, scope)
            .map { File(it, name) }
            .firstOrNull { it.isDirectory }
            ?: directoryOf(projectPath, scope, name)

    private fun readableFileOf(projectPath: String, scope: SkillScope, name: String): File =
        candidateRoots(projectPath, scope)
            .map { File(File(it, name), FILE_NAME) }
            .firstOrNull { it.isFile }
            ?: fileOf(projectPath, scope, name)

    /**
     * 列出所有技能。
     *
     * 项目级与用户级都会扫；同一作用域下新旧两个目录里同名时，新名那份胜出
     * （与读取优先级一致，否则列表点开的是这个、保存改的是另一个）。
     * 两个作用域的同名技能都保留并标明作用域——用户可能故意在项目里放一个覆盖版本，
     * 悄悄隐藏用户级那一条会让人以为它被删了。
     */
    fun list(projectPath: String): List<SkillEntry> {
        val out = mutableListOf<SkillEntry>()
        val seen = mutableSetOf<Pair<SkillScope, String>>()
        SkillScope.entries.forEach { scope ->
            candidateRoots(projectPath, scope).forEach { rootDir ->
                val children = rootDir.listFiles() ?: return@forEach
                children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.forEach { dir ->
                    val file = File(dir, FILE_NAME)
                    if (!file.isFile) return@forEach
                    if (!seen.add(scope to dir.name)) return@forEach
                    out += SkillEntry(
                        name = dir.name,
                        scope = scope,
                        path = file.absolutePath,
                        summary = describe(file),
                        sizeLabel = humanSize(file.length()),
                    )
                }
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

    /** 读取内容。旧名目录里的技能也能打开，只是保存会落到新名目录。 */
    fun read(projectPath: String, scope: SkillScope, name: String): Result<String> = runCatching {
        val file = readableFileOf(projectPath, scope, name)
        if (!file.isFile) throw IllegalArgumentException("找不到技能文件：${file.absolutePath}")
        file.readText()
    }

    /**
     * 新建技能。
     *
     * 若目录已存在**不覆盖**：用户可能已经写了内容，静默覆盖等于丢数据。
     * 返回 true 表示"新建了模板"，false 表示"已存在，直接打开编辑"。
     *
     * 检查范围包括旧名目录：用户在 `.iq/skills` 里已经有一个同名技能时，
     * 再在新名目录里建一个空模板，界面上就会出现一条空白的同名技能。
     */
    fun create(projectPath: String, scope: SkillScope, name: String): Result<Boolean> = runCatching {
        require(isValidName(name)) { "名称只能包含字母、数字、. _ -（1–64 个字符）" }
        val existing = readableDirectoryOf(projectPath, scope, name)
        if (File(existing, FILE_NAME).isFile) return@runCatching false
        if (!existing.exists() && !existing.mkdirs()) {
            throw IllegalStateException("无法创建目录：${existing.absolutePath}")
        }
        if (!existing.isDirectory) throw IllegalStateException("同名路径已存在且不是目录：${existing.absolutePath}")
        File(existing, FILE_NAME).writeText(template(name))
        true
    }

    /**
     * 保存内容。文件不存在时也允许写入（编辑器里的"另存"路径）。
     *
     * 有旧名目录里的同名技能时写回那一份，而不是另起一个新文件——
     * 否则用户编辑完会发现列表里多了一条、Agent 读到的还是老内容。
     */
    fun save(projectPath: String, scope: SkillScope, name: String, body: String): Result<Unit> = runCatching {
        require(isValidName(name)) { "名称只能包含字母、数字、. _ -（1–64 个字符）" }
        val dir = readableDirectoryOf(projectPath, scope, name)
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
        val dir = readableDirectoryOf(projectPath, scope, name)
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
        |在这里编写给 蜘蛛 的技能说明。
        |
    """.trimMargin()

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
