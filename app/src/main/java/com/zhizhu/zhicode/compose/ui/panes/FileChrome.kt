package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
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
 * 根切换条。
 *
 * 三个根是三种不同的活儿（项目=代码、HOME=配置、共享存储=用户的文件），
 * 放在标题栏下方一行，而不是藏进菜单里 —— 换根是这一屏最常用的动作之一。
 */
@Composable
internal fun FileRootSwitcher(
    selected: FileRoot,
    onSelect: (FileRoot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
    ) {
        FileRoot.entries.forEach { root ->
            val active = root == selected
            // 选中/未选中的底色与文字色都走淡变：选中时底色与文字是**同时**变的，
            // 只淡其中一个会出现「字已经变了、底还是旧色」的中间态，比不做动画更难看。
            // 令牌用 [ZhiMotion.colorSpec]（150ms + SinOut），与 ModelPicker 那几个
            // 选中行同一条曲线。对照 upstream `CardSection.kt:122`：可点的卡用 `Sink`。
            val segmentColor by animateColorAsState(
                targetValue = if (active) scheme.primaryContainer else ZhiColors.cardSurface(),
                animationSpec = ZhiMotion.colorSpec,
                label = "fileRootSegment",
            )
            val segmentContent by animateColorAsState(
                targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurface,
                animationSpec = ZhiMotion.colorSpec,
                label = "fileRootSegmentContent",
            )
            Card(
                onClick = { if (!active) onSelect(root) },
                modifier = Modifier.weight(1f),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(vertical = 6.dp),
                colors = CardDefaults.defaultColors(
                    color = segmentColor,
                    contentColor = segmentContent,
                ),
                // Miuix 的默认值是 `PressFeedbackType.None`（这是**超出手册默认**的一项，
                // 不是改回默认）：它是一个真的按钮，按下去要有下沉反馈。
                // 对应 upstream 官方示例 `CardSection.kt:122`「可点卡片用 Sink」。
                pressFeedbackType = PressFeedbackType.Sink,
            ) {
                Text(
                    text = root.label,
                    fontSize = ZhiTextScale.Footnote,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
