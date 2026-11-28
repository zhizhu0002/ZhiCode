package com.zhizhu.zhicode.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.zhizhu.zhicode.compose.model.ToolKind

object ZhiIcons {

    @Composable
    private fun vector(imageVector: ImageVector): Painter = rememberVectorPainter(imageVector)

    // ── 顶栏 ────────────────────────────────────────────────────────────────

    /** 打开侧栏（汉堡）。 */
    val menu: Painter @Composable get() = vector(ZhiMaterialIcons.Menu)

    /** 明暗主题：半明半暗的圆。 */
    val theme: Painter @Composable get() = vector(ZhiMaterialIcons.Contrast)

    /** 悬浮球：实心圆。与沙箱的箱子是两个不同的东西。 */
    val floatingBall: Painter @Composable get() = vector(ZhiMaterialIcons.Circle)

    val settings: Painter @Composable get() = vector(ZhiMaterialIcons.Settings)

    // ── 工作区页签 ──────────────────────────────────────────────────────────

    /** 对话：气泡。 */
    val chat: Painter @Composable get() = vector(ZhiMaterialIcons.ChatBubble)

    /** 变更 / diff：一个方框，里面上 `+` 下 `−`。 */
    val changes: Painter @Composable get() = vector(ZhiMaterialIcons.Difference)

    /** 终端：窗口 + 提示符 + 光标横杠。 */
    val terminal: Painter @Composable get() = vector(ZhiMaterialIcons.Terminal)

    /** 文件页签：**实心**文件夹。 */
    val files: Painter @Composable get() = vector(ZhiMaterialIcons.Folder)

    // ── 侧栏 ────────────────────────────────────────────────────────────────

    /** 新建会话：圆里的 `+`（与输入器那个裸 `+` 区分开）。 */
    val newSession: Painter @Composable get() = vector(ZhiMaterialIcons.AddCircle)

    /** 项目历史：时钟。 */
    val projectHistory: Painter @Composable get() = vector(ZhiMaterialIcons.History)

    /** 项目目录：房子（这是工作区的根）。 */
    val projectPath: Painter @Composable get() = vector(ZhiMaterialIcons.Home)

    /** 角色卡：人像。 */
    val roleCard: Painter @Composable get() = vector(ZhiMaterialIcons.Person)

    /** 技能：拼图块（`extension` 就是拼图）。 */
    val skill: Painter @Composable get() = vector(ZhiMaterialIcons.Extension)

    val sandbox: Painter @Composable get() = vector(ZhiMaterialIcons.DeployedCode)

    /** 运行环境：扳手（侧栏那一行是"内置 Termux 环境就绪"）。 */
    val runtime: Painter @Composable get() = vector(ZhiMaterialIcons.Build)

    // ── 输入器 ──────────────────────────────────────────────────────────────

    /** 附件：裸 `+`。 */
    val attach: Painter @Composable get() = vector(ZhiMaterialIcons.Add)

    val send: Painter @Composable get() = vector(ZhiMaterialIcons.ArrowUpward)

    val stop: Painter @Composable get() = vector(ZhiMaterialIcons.Pause)

    /** 向右的小箭头（"查看全部"这类跳转）。与 [expand] 同源，都是同一个 `›`。 */
    val arrowRight: Painter @Composable get() = vector(ZhiMaterialIcons.ChevronRight)

    // ── 通用 ────────────────────────────────────────────────────────────────

    val close: Painter @Composable get() = vector(ZhiMaterialIcons.Close)

    /** 编辑：铅笔。 */
    val edit: Painter @Composable get() = vector(ZhiMaterialIcons.Edit)

    val expand: Painter @Composable get() = vector(ZhiMaterialIcons.ChevronRight)
    val collapse: Painter @Composable get() = vector(ZhiMaterialIcons.ExpandMore)

    val collapseUp: Painter @Composable get() = vector(ZhiMaterialIcons.ExpandLess)

    // ── 沙箱动作专用（语义 ≠ 通用那一档）────────────────────────────────────

    val play: Painter @Composable get() = vector(ZhiMaterialIcons.PlayArrow)

    val forceStop: Painter @Composable get() = vector(ZhiMaterialIcons.StopCircle)

    /** 清空一个应用的数据（对应"清扫"，不是"删除记录"）。 */
    val clearData: Painter @Composable get() = vector(ZhiMaterialIcons.CleaningServices)

    /** 从沙箱卸载一个应用（把它连同数据一起移走）。 */
    val uninstall: Painter @Composable get() = vector(ZhiMaterialIcons.DeleteSweep)

    /** 进程 / 内存基址。 */
    val memory: Painter @Composable get() = vector(ZhiMaterialIcons.Memory)

    /** 动态调试（Frida / hook）。 */
    val debug: Painter @Composable get() = vector(ZhiMaterialIcons.BugReport)

    /** 沙箱里已安装的应用集合。 */
    val apps: Painter @Composable get() = vector(ZhiMaterialIcons.Apps)

    val refresh: Painter @Composable get() = vector(ZhiMaterialIcons.Refresh)
    val info: Painter @Composable get() = vector(ZhiMaterialIcons.Info)

    /** 清屏：橡皮擦（`ink_eraser`）。 */
    val clear: Painter @Composable get() = vector(ZhiMaterialIcons.InkEraser)

    /** 返回 / 返回上一级：左箭头。 */
    val back: Painter @Composable get() = vector(ZhiMaterialIcons.ArrowBack)

    /** 消息 / 工具行的「更多操作」：**横排**三点。 */
    val more: Painter @Composable get() = vector(ZhiMaterialIcons.MoreHoriz)

    /** 竖排三点（终端标题栏这类竖向排布的地方）。与 [more] 是不同的控件。 */
    val moreVert: Painter @Composable get() = vector(ZhiMaterialIcons.MoreVert)

    val search: Painter @Composable get() = vector(ZhiMaterialIcons.Search)

    val add: Painter @Composable get() = vector(ZhiMaterialIcons.Add)

    /** 新建：带圆环的加号，和附件动作区分开。 */
    val newEntry: Painter @Composable get() = vector(ZhiMaterialIcons.AddCircle)

    /** 文件操作：复制。 */
    val copy: Painter @Composable get() = vector(ZhiMaterialIcons.ContentCopy)

    /** 文件操作：粘贴。 */
    val paste: Painter @Composable get() = vector(ZhiMaterialIcons.ContentPaste)

    /** 文件页网格显示。 */
    val grid: Painter @Composable get() = vector(ZhiMaterialIcons.GridView)

    /** 编辑器自动换行。 */
    val wrapText: Painter @Composable get() = vector(ZhiMaterialIcons.WrapText)

    /** 文件页列表显示。 */
    val list: Painter @Composable get() = vector(ZhiMaterialIcons.List)

    /** 完成：圆里的对勾。 */
    val done: Painter @Composable get() = vector(ZhiMaterialIcons.CheckCircle)

    /** 保存：软盘。 */
    val save: Painter @Composable get() = vector(ZhiMaterialIcons.Save)

    /** 失败：圆里的感叹号。与 [close] 不是同一个字形。 */
    val failed: Painter @Composable get() = vector(ZhiMaterialIcons.Error)

    /** 尚未开始 / 进行中：空心圆环（注意不是带点的圆）。 */
    val pending: Painter @Composable get() = vector(ZhiMaterialIcons.RadioButtonUnchecked)

    /** 等待授权：圆圈问号（需要你决定）。 */
    val awaiting: Painter @Composable get() = vector(ZhiMaterialIcons.Help)

    /** 选中标记：裸对勾（列表行的单选态）。 */
    val check: Painter @Composable get() = vector(ZhiMaterialIcons.Check)

    // ── 文件 ────────────────────────────────────────────────────────────────

    /** 目录：**打开的**文件夹，与页签上那个合口的 [files] 区分开。 */
    val directory: Painter @Composable get() = vector(ZhiMaterialIcons.FolderOpen)

    /** 文件：文档（右上角折角）。 */
    val file: Painter @Composable get() = vector(ZhiMaterialIcons.Description)

    val delete: Painter @Composable get() = vector(ZhiMaterialIcons.Delete)

    /** 图片附件：照片。曾经借过 [floatingBall]，那是"悬浮球"，与图片无关。 */
    val image: Painter @Composable get() = vector(ZhiMaterialIcons.Image)

    // ── 设置行（彩色圆角块里的白字形） ────────────────────────────────────────

    /** 搜索服务列表（入口行）：云。 */
    val cloud: Painter @Composable get() = vector(ZhiMaterialIcons.Cloud)

    /** 搜索结果条数（数值项）：列表。 */
    val listCount: Painter @Composable get() = vector(ZhiMaterialIcons.List)

    /** 联网超时（数值项）：计时器。 */
    val timeout: Painter @Composable get() = vector(ZhiMaterialIcons.Timer)

    /** API 配置（入口行）：链接（接口地址 + 密钥）。 */
    val link: Painter @Composable get() = vector(ZhiMaterialIcons.Link)

    /** 推理强度（选项项）：滑杆。 */
    val tune: Painter @Composable get() = vector(ZhiMaterialIcons.Tune)

    /** 权限模式（选项项）：锁。 */
    val lock: Painter @Composable get() = vector(ZhiMaterialIcons.Lock)

    /** 上下文压缩（开关）：向内的双向箭头。 */
    val compress: Painter @Composable get() = vector(ZhiMaterialIcons.Compress)

    /** 终端字符模式输入（开关）：键盘。 */
    val keyboard: Painter @Composable get() = vector(ZhiMaterialIcons.Keyboard)

    val mindMap: Painter @Composable get() = vector(ZhiMaterialIcons.Hub)

    /** UI 调试页（入口行）：组件分层陈列。 */
    val layers: Painter @Composable get() = vector(ZhiMaterialIcons.Layers)

    // ── 工具类别的图标 ──────────────────────────────────────────────────────

    @Composable
    fun tool(kind: ToolKind): Painter = when (kind) {
        ToolKind.SEARCH -> vector(ZhiMaterialIcons.Search)
        ToolKind.READ -> vector(ZhiMaterialIcons.Description)
        ToolKind.EDIT -> vector(ZhiMaterialIcons.Edit)
        ToolKind.COMMAND -> vector(ZhiMaterialIcons.Terminal)
        ToolKind.OTHER -> vector(ZhiMaterialIcons.Code)
    }

    /** 按工具名取图标（授权弹窗等只知道工具名的场景）。 */
    @Composable
    fun tool(toolName: String): Painter = when (toolName) {
        "Grep", "Glob", "Search" -> vector(ZhiMaterialIcons.Search)
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> vector(ZhiMaterialIcons.Description)
        "Write", "Edit", "MultiEdit" -> vector(ZhiMaterialIcons.Edit)
        "Delete" -> vector(ZhiMaterialIcons.Delete)
        "Move" -> vector(ZhiMaterialIcons.DriveFileMove)
        "Bash", "Root", "Shizuku" -> vector(ZhiMaterialIcons.Terminal)
        else -> vector(ZhiMaterialIcons.Code)
    }
}
