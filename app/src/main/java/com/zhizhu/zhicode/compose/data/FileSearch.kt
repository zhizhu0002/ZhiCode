package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.FileHit
import java.io.File

/**
 * 项目内的文件名搜索。
 *
 * 用途：输入器的 `+` →「附加项目文件」。用户敲几个字，从当前项目里挑文件，
 * 附加到下一条消息。
 *
 * 设计取舍：
 * - **有界**。项目目录里可能有 `node_modules`、`build` 这种几万文件的目录，
 *   无界递归会卡住界面（而且这是在 IO 线程上跑的，快了没用、卡了要命）。
 *   所以同时限制：递归深度、访问节点总数、返回条数。
 * - **广度优先**。浅层文件权重更高（用户想附的通常是自己刚写的源码，
 *   不是 `build/intermediates/...`），BFS 天然先给出浅层结果。
 * - **跳过噪声目录**。`.git`、`build`、`node_modules` 等既不是用户要附加的东西，
 *   又是文件数的大头。
 * - **不读文件内容**。这里只列名字与大小；真正读取在附加时做
 *   （[FileBrowser.read] 负责截断与二进制判定）。
 * - **纯 Kotlin，不依赖 Compose**。这样可以用 `kotlinc` 独立跑测试。
 */
internal object FileSearch {

    /** 递归深度上限。根目录算第 0 层。 */
    private const val MAX_DEPTH = 6

    /** 查询为空时的深度上限。空查询先给一份"可以看到"的浅层清单。 */
    private const val SHALLOW_DEPTH = 2

    /** 返回条数上限。 */
    const val MAX_RESULTS = 200

    /**
     * 最多访问多少个文件/目录节点。
     *
     * 深度上限挡不住"每一层都很宽"的情况（例如一个目录下 5 万个文件），
     * 所以再压一道总预算。
     */
    private const val MAX_VISITED = 20_000

    /**
     * 不进入的目录名。
     *
     * 判断按**名字**而不是路径：这些目录名在任何层级都只意味着构建产物或元数据。
     * 注意没有把"点开头"一网打尽 —— `.gitignore`、`.env.example` 这类是需要
     * 能附加上去的，所以只排除明确无用的几个。
     */
    private val SKIP_DIRS = setOf(
        ".git", ".gradle", ".idea", ".cxx", ".kotlin",
        "build", "node_modules", "__pycache__", "venv", ".venv",
        "target", "dist", "out", "bin", "obj",
        // 会话/技能/记忆都存在这里，对"附加给模型看的项目文件"没意义
        ".iq",
    )

    /**
     * 在 [root] 下搜索名字/路径包含 [query] 的文件。
     *
     * 匹配规则：把**相对路径**转小写后做子串匹配（所以 `main` 能命中
     * `app/src/main/java/...`，`kt` 不命中 `kt` 以外的扩展名以外的东西 ——
     * 扩展名也在相对路径里，所以 `kt` 实际上会命中所有 `.kt` 文件，这是符合直觉的）。
     *
     * [query] 为空时返回浅层文件清单（见 [SHALLOW_DEPTH]），而不是空列表 ——
     * 打开面板先看到东西，比看到一个空框好。
     *
     * 结果按「浅 → 深、同层按名字」排序，保证多次调用顺序稳定。
     * [root] 不存在或不是目录时返回空列表，不抛异常。
     */
    fun search(root: String, query: String, limit: Int = MAX_RESULTS): List<FileHit> {
        val rootDir = File(root)
        if (!rootDir.isDirectory) return emptyList()

        val needle = query.trim().lowercase()
        val depthCap = if (needle.isEmpty()) SHALLOW_DEPTH else MAX_DEPTH
        val cap = limit.coerceAtLeast(1)

        val out = ArrayList<FileHit>(minOf(cap, 64))
        // BFS：队列里存 (目录, 深度)。用索引游标而不是 removeFirst()，
        // 免得在 ArrayList 上反复搬数据。
        val queue = ArrayList<Pair<File, Int>>(64)
        queue += rootDir to 0
        var cursor = 0
        var visited = 0

        while (cursor < queue.size && out.size < cap && visited < MAX_VISITED) {
            val (dir, depth) = queue[cursor++]
            val children = dir.listFiles() ?: continue
            // 排序让结果稳定：同一层里按名字，大小写不敏感
            val sorted = children.sortedBy { it.name.lowercase() }
            for (file in sorted) {
                if (visited >= MAX_VISITED) break
                visited++
                if (file.isDirectory) {
                    if (depth < depthCap && file.name !in SKIP_DIRS) {
                        queue += file to (depth + 1)
                    }
                    continue
                }
                if (!file.isFile) continue
                val relative = file.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/')
                if (needle.isNotEmpty() && !relative.lowercase().contains(needle)) continue
                out += FileHit(
                    path = file.absolutePath,
                    relative = relative,
                    size = runCatching { file.length() }.getOrDefault(0L),
                )
                if (out.size >= cap) break
            }
        }
        return out
    }
}
