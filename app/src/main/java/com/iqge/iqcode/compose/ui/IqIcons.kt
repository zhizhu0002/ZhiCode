package com.iqge.iqcode.compose.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.iqge.iqcode.compose.model.ToolKind
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
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme

/**
 * 全局图标集：统一走 Miuix 图标库（`top.yukonga.miuix.kmp.icon`）。
 * 原来用字符字形（☰ ▣ ⚙ ＋ ↑ ■ ⌄ › 等）的位置全部换成这里的矢量图标。
 */
object IqIcons {
    private val set = MiuixIcons.Regular

    // 顶栏
    val menu: ImageVector get() = set.Sidebar
    val theme: ImageVector get() = set.Theme
    val floatingBall: ImageVector get() = set.Layers
    val settings: ImageVector get() = set.Settings

    // 工作区
    val chat: ImageVector get() = set.Messages
    val changes: ImageVector get() = set.Replace
    val terminal: ImageVector get() = set.Notes
    val files: ImageVector get() = set.Folder

    // 侧栏
    val newSession: ImageVector get() = set.AddCircle
    val projectHistory: ImageVector get() = set.Refresh
    val projectPath: ImageVector get() = set.Home
    val roleCard: ImageVector get() = set.ContactsCircle
    val sandbox: ImageVector get() = set.Layers
    val runtime: ImageVector get() = set.Ok

    // 输入器
    val attach: ImageVector get() = set.Add
    val send: ImageVector get() = set.Send
    val stop: ImageVector get() = set.Pause

    /** 发送键用的 `→` 箭头（图标库里没有 ArrowRight，`Forward` 就是右向箭头）。 */
    val arrowRight: ImageVector get() = set.Forward

    // 通用
    val close: ImageVector get() = set.Close
    val collapse: ImageVector get() = set.ExpandMore
    val expand: ImageVector get() = set.ChevronForward
    val refresh: ImageVector get() = set.Refresh
    val info: ImageVector get() = set.Info

    /** 面板标题栏的「清屏」。 */
    val clear: ImageVector get() = set.Clear

    /**
     * 面板标题栏的「上一级」。
     * 图标库没有 ArrowUp，`ExpandLess`（^ 形）是语义最接近的现成图标。
     */
    val upLevel: ImageVector get() = set.ExpandLess

    /** 消息 / 工具行的「更多操作」（对应原版的 ⋯）。 */
    val more: ImageVector get() = set.More

    /** 工具行右端的展开/收起箭头（对应原版的 ⌄ / ⌃）。 */
    val chevronDown: ImageVector get() = set.ExpandMore
    val chevronUp: ImageVector get() = set.ExpandLess

    /** 工具状态图标：完成 / 失败。运行中改用 Miuix 不确定进度指示器。 */
    val done: ImageVector get() = set.Ok
    val failed: ImageVector get() = set.Close

    /**
     * 任务清单里"还没开始"的空心圈。
     * 图标库没有 RadioButtonUnchecked 这类现成空心圆，`MoreCircle` 是唯一的外圆圈图形。
     */
    val pending: ImageVector get() = set.MoreCircle

    /** 等待授权：`Help`（圆圈里的问号）比 `Info` 更贴近"需要你决定"。 */
    val awaiting: ImageVector get() = set.Help

    // 文件类型
    val directory: ImageVector get() = set.Folder
    val file: ImageVector get() = set.File

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
