package com.zhizhu.zhicode.compose.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.zhizhu.zhicode.compose.model.ToolKind
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme

/**
 * 全局图标集：绝大多数图标取自 Miuix 图标库（`top.yukonga.miuix.kmp.icon`），
 * 原来用字符字形（☰ ▣ ⚙ ＋ ↑ ■ ⌄ › 等）的位置全部换成这里的矢量图标。
 *
 * 库中确实没有的语义（上/右箭头、空心圆、终端、diff、历史）走 [ZhiVectorIcons] 自绘，
 * 不再拿不相干的图标凑合；详见该文件顶部对 Miuix 风格与填充规则的实测说明。
 */
object ZhiIcons {
    private val set = MiuixIcons.Regular

    // 顶栏
    val menu: ImageVector get() = set.Sidebar
    val theme: ImageVector get() = set.Theme
    val floatingBall: ImageVector get() = set.Layers
    val settings: ImageVector get() = set.Settings

    // 工作区
    val chat: ImageVector get() = set.Messages
    /** 变更 / diff。原先借 `Replace`（替换），不像 diff，现自绘。 */
    val changes: ImageVector get() = ZhiVectorIcons.Diff
    /** 终端。原先借 `Notes`（笔记），语义不符，现自绘。 */
    val terminal: ImageVector get() = ZhiVectorIcons.Terminal
    val files: ImageVector get() = set.Folder

    // 侧栏
    val newSession: ImageVector get() = set.AddCircle
    /** 项目历史。原先借 `Refresh`（刷新），语义偏了，现自绘时钟。 */
    val projectHistory: ImageVector get() = ZhiVectorIcons.History
    val projectPath: ImageVector get() = set.Home
    val roleCard: ImageVector get() = set.ContactsCircle

    /** Skill 管理器。用 &quot;Layers&quot; 之外的图标以免和悬浮球/沙箱撞脸。 */
    val skill: ImageVector get() = set.Tasks
    val sandbox: ImageVector get() = set.Layers
    val runtime: ImageVector get() = set.Ok

    // 输入器
    val attach: ImageVector get() = set.Add
    val send: ImageVector get() = set.Send
    val stop: ImageVector get() = set.Pause

    /** 发送键用的 `→` 箭头。库中没有 ArrowRight，原先借 `Forward`（转发），现自绘。 */
    val arrowRight: ImageVector get() = ZhiVectorIcons.ArrowRight

    // 通用
    val close: ImageVector get() = set.Close
    /** 编辑（API 配置等条目的修改入口）。 */
    val edit: ImageVector get() = set.Edit
    val collapse: ImageVector get() = set.ExpandMore
    val expand: ImageVector get() = set.ChevronForward
    val refresh: ImageVector get() = set.Refresh
    val info: ImageVector get() = set.Info

    /** 面板标题栏的「清屏」。 */
    val clear: ImageVector get() = set.Clear

    /** 面板标题栏的「上一级」。库中没有真箭头，原先借 `ExpandLess`（^ 形），现自绘。 */
    val upLevel: ImageVector get() = ZhiVectorIcons.ArrowUp

    /** 消息 / 工具行的「更多操作」（对应原版的 ⋯）。 */
    val more: ImageVector get() = set.More

    /** 列表筛选框的放大镜。技能列表用它。 */
    val search: ImageVector get() = set.Search

    /** 工具行右端的展开/收起箭头（对应原版的 ⌄ / ⌃）。 */
    val chevronDown: ImageVector get() = set.ExpandMore
    val chevronUp: ImageVector get() = set.ExpandLess

    /** 工具状态图标：完成 / 失败。运行中改用 Miuix 不确定进度指示器。 */
    val done: ImageVector get() = set.Ok
    val failed: ImageVector get() = set.Close

    /**
     * 任务清单里"还没开始"的空心圈。
     * 库中没有空心圆，原先借 `MoreCircle`（外圈带点），现自绘真正的空心圆环。
     */
    val pending: ImageVector get() = ZhiVectorIcons.RadioButtonUnchecked

    /** 等待授权：`Help`（圆圈里的问号）比 `Info` 更贴近"需要你决定"。 */
    val awaiting: ImageVector get() = set.Help

    // 文件类型
    val directory: ImageVector get() = set.Folder
    val file: ImageVector get() = set.File
    val delete: ImageVector get() = set.Delete

    /** 工具卡片图标，按工具类别映射。 */
    fun tool(kind: ToolKind): ImageVector = when (kind) {
        ToolKind.SEARCH -> set.Search
        ToolKind.READ -> set.File
        ToolKind.EDIT -> set.Edit
        ToolKind.COMMAND -> set.Tasks
        ToolKind.OTHER -> set.Ok
    }

    /** 按工具名取图标（授权弹窗等只知道工具名的场景）。 */
    fun tool(toolName: String): ImageVector = when (toolName) {
        "Grep", "Glob", "Search" -> set.Search
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> set.File
        "Write", "Edit", "MultiEdit" -> set.Edit
        "Delete" -> set.Delete
        "Move" -> set.MoveFile
        "Bash", "Root" -> set.Tasks
        else -> set.Ok
    }
}
