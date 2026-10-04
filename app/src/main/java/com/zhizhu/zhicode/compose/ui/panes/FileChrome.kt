package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileFormat
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
import com.zhizhu.zhicode.compose.ui.WorkspaceTabRowHeight
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 文件浏览的**共用外壳**：根切换条 / 面包屑 / 列表行。
 *
 * ## 为什么单独一个文件
 *
 * 这些零件原先私有在 `FilesPane.kt` 里（那一屏是"文件面板"）。
 * 现在有两个地方要长成同一副样子：
 *
 *  · **文件面板**（`FilesPane`）—— 完整版：可进可改，行尾带重命名/删除；
 *  · **附加项目文件选择器**（`AttachFileOverlay`）—— 挑一个文件附加到下一条消息。
 *
 * 选择器原先走的是**递归搜索 + 扁平结果**（`FileSearch`），实测**打开就灌满设备照片**：
 * 它从 HOME 起递归，而 HOME 下有 Termux 的 `storage/{pictures,dcim,…}` 软链，
 * 空查询的深度上限（2）又刚好够到 `storage/pictures/` —— 于是标题写着「附加项目文件」，
 * 列表里全是 `storage/pictures/END…` 的截图，而且 BFS 浅层优先让它们排在真正的代码前面。
 *
 * 改成浏览器就顺手治好了它：**一层一层走，不递归**，那六个软链只是"可以点进去的一个目录"。
 *
 * ⚠️ 抽出来的理由是"两边必须一直是同一副样子"，所以这里**共用同一份实现**，
 * 而不是各写一份长得像的。各写一份的话，改了一边另一边就会慢慢漂开 ——
 * 而那正是用户最容易一眼看出来的地方（面包屑的层级、行高、图标颜色）。
 *
 * ⚠️ 搬过来时**一个字节的行为都没改**（只有 [FileListRow] 的尾部动作从写死两个按钮
 * 改成了插槽 —— 选择器要的是「＋ 附加」，不是「重命名/删除」）。
 */

/**
 * 根切换条：**标签栏**（Miuix `TabRowWithContour`，与顶部工作区标签同一个组件）。
 *
 * ## 原先是一排自制卡片，用户报了「切换没有动画」
 *
 * 它原来是 `Row { FileRoot.entries.forEach { Card(…) } }`，选中态只有一次
 * `animateColorAsState` 的底色淡变 —— **没有滑动指示器**。
 * 所以观感是"高亮块硬跳"，而不是标签栏那种"指示器滑过去"。
 *
 * 现在转发到 [ZhiSegmentedTabs]（= 顶部工作区标签用的那一个）：
 *
 *  · 指示器由 Miuix 内部 `indicatorOffset.animateTo(target, tween(200))` 滑动（`TabRow.kt`）；
 *  · 字号、圆角、行高、按压反馈全部与顶部一致，不必再各调一套；
 *  · 两颗按钮等分整行（`matchWidth = true`）。
 *
 * ⚠️ 行高沿用 [WorkspaceTabRowHeight]（45dp）。这不是"顺手对齐"：换根这一行原先
 * 实测也在 ~44dp（外层 4dp 上下留白 + 卡片 6dp 内边距 + 一行 Footnote），
 * 所以对齐之后**面板高度没有变**，只是里面的东西换成了真标签栏。
 */
@Composable
internal fun FileRootSwitcher(
    selected: FileRoot,
    onSelect: (FileRoot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roots = FileRoot.entries
    Box(
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        ZhiSegmentedTabs(
            tabs = roots.map { it.label },
            selectedIndex = roots.indexOf(selected).coerceAtLeast(0),
            onSelect = { index ->
                // 点当前那一项不该回调：Miuix 会照常高亮，而我们这里的回调会触发
                // 一次多余的「回到该根顶层」（用户明明就在顶层，却被弹回顶上）。
                roots.getOrNull(index)?.let { if (it != selected) onSelect(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            matchWidth = true,
        )
    }
}

/**
 * 「当前路径」那一行：**面包屑 + 行尾动作**。
 *
 * ## 为什么要有这个组件（而不是各写一排）
 *
 * 这一行原先在三处各写一份，而且三份都不一样：
 *
 * | 位置 | 原先 | 毛病 |
 * |---|---|---|
 * | 文件面板 · 列表 | `PaneHeader("文件", "N 项")` + 单独一行面包屑 | 标题与**顶部标签栏**重复；列表前叠了三层 |
 * | 文件面板 · 查看/编辑 | `PaneHeader(文件名, "语言 · 只读")` + 面包屑 | 同上（文件名是真信息，保留） |
 * | 附加面板 | `Row { Box(weight) { 面包屑 }; 上一级 }` | 与文件面板的行高、内边距各不相同 |
 *
 * 用户的原话是「把文件页面重构吧」「这个也不是很好看」。所以这里收成一份：
 * **面包屑占满剩余宽度、动作靠右**，三处共用同一行几何。
 *
 * ## [summary]：第二行的小字统计
 *
 * 「文件夹 3 · 文件 12」这一行来自小米文件管理器（它的列表上方有一行
 * `文件夹: N 文件: M`，`res/layout/phone_file_explorer_list.xml` 上面的
 * storage 行与分组表头的 `@id/group_file_count` 都是这个用途）。
 *
 * 它取代了原先被删掉的那个 `PaneHeader("文件", "N 项")` 的位置价值：
 * 那个标题是**冗余的**（与顶部标签栏正在高亮的那一项同一件事），但"这个目录里
 * 有多少东西"不是冗余的 —— 一屏只放得下七八行，用户需要知道还有多少在下面。
 *
 * 统计**只数当前这一层**（不回递归）：递归数一个 `node_modules` 会卡住界面，
 * 而我们真正想回答的只是"这一层还有多少行没看到"。
 *
 * @param trailing 行尾动作。`null` 时不占位（查看/编辑态的面包屑行就是这样）。
 * ⚠️ 用**可空槽位**而不是 `= {}`（与 `PaneHeader` 的 `actions` 同一写法），
 * 顺带避开一个坑：测试按花括号配对取函数正文时，参数默认值的那对 `{}`
 * 会被当成函数体 —— `DebugHudStructureTest.bodyOf` 在 FilePathBar 上实测踩到过。
 */
@Composable
internal fun FilePathBar(
    filePath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String = "",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 面包屑自己带横向滚动（Miuix `BreadcrumbBar` 的语义），所以给它 weight 占满，
            // 路径深了会在这块区域里滚，而不是把动作按钮挤出屏幕。
            Box(modifier = Modifier.weight(1f)) {
                FileBreadcrumbBar(filePath = filePath, onNavigate = onNavigate)
            }
            trailing?.invoke(this)
        }
        if (summary.isNotEmpty()) {
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Micro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 与面包屑的 insideMargin（横向 6 + 8）大致对齐，让这行小字
                // 不贴着屏幕左边缘，也不与上一行错开太多。
                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
            )
        }
    }
}

/** 「文件夹 N · 文件 M」。两处（文件面板 / 附加面板）必须用同一串字符。 */
internal fun fileCountSummary(entries: List<FileEntry>): String {
    if (entries.isEmpty()) return ""
    val dirs = entries.count { it.directory }
    return "文件夹 $dirs · 文件 ${entries.size - dirs}"
}

/** 把绝对路径拆成 Miuix `BreadcrumbBar` 需要的层级列表。 */
@Composable
internal fun FileBreadcrumbBar(
    filePath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(filePath) { breadcrumbItems(filePath) }
    if (items.isEmpty()) return

    BreadcrumbBar(
        items = items,
        onItemClick = { index -> items.getOrNull(index)?.let { onNavigate(it.path) } },
        highlightIndex = items.lastIndex,
        // 面包屑只占一行的引导作用，不需要占满宽度：
        // 压小 insideMargin，给下面的文件列表让位。
        modifier = modifier.padding(horizontal = 6.dp, vertical = 0.dp),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        // itemMaxWidth 用 Miuix 的默认值 160dp，不覆盖 —— 每一项都是单独的目录名，
        // 最长的是包名 `com.zhizhu.code`（15 字符，约 146dp），160dp 放得下。
        // ⚠️ 别改成把好几级拼成一条路径：那一定会被省略号截断，反而什么都看不见。
    )
}

/**
 * 生成面包屑层级：**一级目录一个项**，从「软件根目录」开始。
 *
 * `/data/user/0/com.zhizhu.code/files/home/workspace`
 *   → `com.zhizhu.code` `files` `home` `workspace`
 *
 * 为什么砍掉前面的 `/data/user/0`：它在应用沙箱里是 `drwx--x--x`
 * （other 只有 x 没有 r），**列不出来** —— 挂一个点进去只能看到
 * 「无法读取（权限不足）」的层级，纯粹是噪音。首项文字直接用包名，
 * 它确实就是软件根目录，语义也对得上。
 *
 * 路径在应用根**之外**（设置里把项目路径改到了 `/sdcard/...` 等）时，
 * 没有“包名”可以当起点，就逐级展示真实层级；此时首项是 `/`。
 *
 * 每一项的 `path` 都是真实层级，点哪一级就回到哪一级。
 */
internal fun breadcrumbItems(filePath: String): List<BreadcrumbItem> {
    val normalized = filePath.trimEnd('/').ifEmpty { "/" }
    val appRoot = TermuxConstants.TERMUX_DATA_DIR_PATH.trimEnd('/')

    val start = if (normalized == appRoot || normalized.startsWith("$appRoot/")) appRoot else "/"
    val rest = when {
        normalized == start -> ""
        start == "/" -> normalized.removePrefix("/")
        else -> normalized.removePrefix("$start/")
    }

    val items = mutableListOf(
        BreadcrumbItem(path = start, text = start.substringAfterLast('/').ifEmpty { "/" }),
    )
    var accumulated = if (start == "/") "" else start
    rest.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        accumulated += "/$segment"
        items += BreadcrumbItem(path = accumulated, text = segment)
    }
    return items
}

/**
 * 文件行：**两行**（文件名 / 次要信息），40dp 图标。
 *
 * ## 为什么改成两行
 *
 * 原先是一行：`图标 · 文件名 · 字节数 · [重命名][删除]`。用户给的两张截图里
 * 最刺眼的就是这一行 —— 命名一长，右边的四个东西把文件名挤成两三个字加省略号，
 * 而"重命名/删除"两个图标在**每一行**都挂着，一屏十几行就是二十几个图标。
 *
 * 小米文件管理器的列表行是两行（`res/layout/file_item_list_layout.xml`）：
 * 第一行文件名（`@id/file_name`，`maxLines=2`）+ 第二行次要信息（`@id/file_size`），
 * 图标 40dp（`category_common_file_icon_size`），整行 `list_item_height=70dp`。
 * 我们按自己的令牌折算（不照搬 70dp —— 那会让我们 44dp 行的界面突然变胖）：
 * 图标 15dp 保持不变（超集：它同时也是"这是目录还是文件"的颜色信号），
 * 第二行用 `ZhiTextScale.Micro`（最小档），行高靠内容撑 + `heightIn(min = 44.dp)`
 * 保住原来的行距观感。
 *
 * ## 次要信息显示什么
 *
 * - **文件**：`1.2 KB · 今天 13:53`（大小 + 修改时间，与小米一致）；
 * - **目录**：只有一行小字（目录的 `length()` 是 0，"0 B" 是噪音），
 *   整行**垂直居中** —— 目录行少一行内容，不居中会看起来像"文字偏上了"。
 *
 * ## 行尾为什么不再挂动作
 *
 * 「重命名 / 删除」搬到**长按选择模式**里去了（见 `FilesPane` 的选择态底部栏），
 * 这与小米一致（它的长按是 action mode）。这里只留 [trailing] 插槽 ——
 * 附加选择器用它放「＋ 附加」（那里点整行就是附加，多一个动作是多余的）。
 */
@Composable
internal fun FileListRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    now: Long = System.currentTimeMillis(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    // 选中时整行换底色。**不能只靠左边的勾**：列表里勾与图标并排，
    // 一行里两个圆形/方形标记很难一眼分清哪个是"选中"、哪个是"这是目录"。
    val background = if (selected) scheme.primaryContainer else ZhiColors.cardSurface()
    Card(
        onClick = onOpen,
        onLongPress = onLongPress,
        // 行距由 LazyColumn 的 `spacedBy` 统一给（原来这里还有一个 `padding(vertical = 1.dp)`，
        // 两个地方都给间距会让以后调行距要改两处，而且那 1dp 几乎等于没有）。
        // `modifier` 里是 `animateItem()`，所以它在 fillMaxWidth **之前**：
        // 尺寸照旧铺满，动画交给 LazyColumn 记账。
        modifier = modifier.fillMaxWidth().heightIn(min = FileRowMinHeight),
        cornerRadius = ZhiRadius.inner,
        // 尾部的内边距比原先小（那是为两个 30dp 图标留的）：没有常驻动作之后，
        // 右边缘可以收到与 `ZhiSpace.s` 同级，文件名能多占几个字符。
        insideMargin = PaddingValues(start = ZhiSpace.m, end = ZhiSpace.m, top = 5.dp, bottom = 5.dp),
        colors = CardDefaults.defaultColors(
            color = background,
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // 选择模式下的勾。放在**图标左边**（小米在右边，但我们的行尾还留给
            // 附加面板的「＋ 附加」；插槽在右边，勾也在右边就会两个东西挤在一起）。
            if (selectionMode) {
                Icon(
                    painter = if (selected) ZhiIcons.done else ZhiIcons.pending,
                    contentDescription = null,
                    tint = if (selected) scheme.primary else scheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Icon(
                painter = if (entry.directory) ZhiIcons.directory else ZhiIcons.file,
                contentDescription = null,
                tint = if (entry.directory) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                // 目录只有一行小字，居中；文件两行，顶部对齐。
                verticalArrangement = if (entry.directory) Arrangement.Center else Arrangement.Top,
            ) {
                Text(
                    text = entry.name,
                    fontSize = ZhiTextScale.Caption,
                    fontWeight = if (entry.directory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val info = fileInfoLine(entry, now)
                if (info.isNotEmpty()) {
                    Text(
                        text = info,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

/** 两行行的高下限：比原来单行的 40dp 略高，但不照搬小米的 70dp。 */
internal val FileRowMinHeight = 44.dp

/**
 * 行的第二行小字。
 *
 * 目录给空串（目录的 `length()` 恒为 0，"0 B" 是噪音，而它已经有颜色区分了）；
 * 修改时间取不到时（[FileEntry.modifiedAt] 为 0）也不显示，而不是编一个时间。
 */
internal fun fileInfoLine(entry: FileEntry, now: Long): String {
    if (entry.directory) return ""
    val time = FileFormat.time(entry.modifiedAt, now)
    return if (time.isEmpty()) FileFormat.size(entry.size) else "${FileFormat.size(entry.size)} · $time"
}
