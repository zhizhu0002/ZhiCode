package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * ## `trailing` 是必需而不是可选
 *
 * 文件面板要在这一行放三个动作（新建文件 / 新建文件夹 / 上一级），附加面板只放
 * 一个（上一级）。做成插槽之后两处是**同一段布局代码**；若各写一份，
 * 「上一级」在一边是 30dp 图标、在另一边是别的东西，迟早对不上 —— 而
 * [FileChrome] 文件头写的就是"两边必须一直是同一副样子"。
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 面包屑自己带横向滚动（Miuix `BreadcrumbBar` 的语义），所以给它 weight 占满，
        // 路径深了会在这块区域里滚，而不是把动作按钮挤出屏幕。
        Box(modifier = Modifier.weight(1f)) {
            FileBreadcrumbBar(filePath = filePath, onNavigate = onNavigate)
        }
        trailing?.invoke(this)
    }
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
 * 文件行：此前 vertical = 20dp 导致行高约 60dp，一屏放不下几个文件（V2）。
 * 收到 11dp ≈ 40dp 行高，仍在 Material 触摸目标下限（48dp）附近，密度观感
 * 与 Miuix 设置列表行一致。
 *
 * <p>尾部的动作**由调用方给**（[trailing]）：
 *
 *  · 文件面板塞「重命名 / 删除」，两个都用 [com.zhizhu.zhicode.compose.ui.ZhiIconButton]
 *    的 `compact` 压到 30dp —— 与 `PaneHeader` 的做法一致：Miuix `TextButton` 写死
 *    `MinWidth=58dp`/`MinHeight=40dp`，在这条 40dp 高的行里放不下，图标按钮才是能安全压小的那个。
 *  · 附加选择器塞「＋ 附加」，因为那里点整行就是附加，不需要再给两个破坏性动作。
 *
 * ⚠️ 尾部做成插槽而不是"给两个可空回调"：可空回调那种写法会让"这一行能不能改名"
 * 只能靠传 null 表达，而插槽直接把"这里放什么"交给调用方，读起来没有隐藏状态。
 */
@Composable
internal fun FileListRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        // 行距由 LazyColumn 的 `spacedBy` 统一给（原来这里还有一个 `padding(vertical = 1.dp)`，
        // 两个地方都给间距会让以后调行距要改两处，而且那 1dp 几乎等于没有）。
        // `modifier` 里是 `animateItem()`，所以它在 fillMaxWidth **之前**：
        // 尺寸照旧铺满，动画交给 LazyColumn 记账。
        modifier = modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(start = ZhiSpace.m, end = ZhiSpace.xs, top = 4.dp, bottom = 4.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                painter = if (entry.directory) ZhiIcons.directory else ZhiIcons.file,
                contentDescription = null,
                tint = if (entry.directory) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = entry.name,
                fontSize = ZhiTextScale.Caption,
                fontWeight = if (entry.directory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!entry.directory) {
                Text(
                    text = formatFileSize(entry.size),
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Micro,
                )
            }
            trailing()
        }
    }
}

/** 行尾的字节数文案。**共用**：两个界面里的同一行必须显示同一串字符。 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1_000L -> "$bytes B"
    bytes < 1_000_000L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1000f)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000f)
}
