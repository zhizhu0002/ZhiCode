package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.DiffFile
import com.zhizhu.zhicode.compose.model.DiffState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 变更面板，对应原版 renderChanges() + colorDiff() + updateDiffStats()。 */
@Composable
fun ChangesPane(
    diff: DiffState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    /**
     * 刷新动作。**默认 `null` = 不显示**「刷新」按钮。
     *
     * 原来默认是 `{}`，于是按钮永远画出来、点了却什么都不发生（调用方也没传）。
     * 改成可空后，没接线就不会出现"死按钮"；现已由 `AppScaffold` 接上
     * `viewModel::refreshDiff`。
     */
    onRefresh: (() -> Unit)? = null,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                title = "Git 变更",
                actionIcon = ZhiIcons.refresh,
                actionDescription = "刷新变更列表",
                onAction = onRefresh,
                subtitle = if (diff.files.isEmpty()) null else "+${diff.additions}  −${diff.deletions}",
            )
            // 「空态 / 列表」之间淡变。原来是 `if (...) { …; return@Surface }` ——
            // 刷新一次 git 会让整块内容一帧内换掉。
            // ⚠️ `return@Surface` 不能留：在 AnimatedContent 的 content lambda 里
            // 它是非局部返回，会跳过 AnimatedContent 自己的收尾（实测会漏帧）。
            AnimatedContent(
                targetState = diff.files.isEmpty(),
                transitionSpec = {
                    fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                },
                label = "changesEmpty",
            ) { empty ->
                if (empty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            // 空白态的文案必须区分「确实没有变更」与「没能读到变更」：
                            // 原来写死"工作区没有未提交的变更"，于是 git 失败时界面
                            // 会一口咬定没有变更 —— 明明什么都没查到却给了确定性结论。
                            text = diff.note.ifEmpty { "工作区没有未提交的变更" },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = ZhiTextScale.BodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 28.dp),
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        item { DiffStatBar(diff) }
                        items(diff.files, key = { it.name }) { file ->
                            DiffFileCard(
                                file = file,
                                isDark = isDark,
                                expanded = expanded[file.name] == true,
                                onToggle = { expanded[file.name] = expanded[file.name] != true },
                                // 展开某一项不会增删行，但**切分支/重新算 diff** 会换掉整个文件集：
                                // 那时剩下的同名行要滑到新位置，而不是瞬移。
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item { Box(modifier = Modifier.padding(bottom = 10.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffStatBar(diff: DiffState) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${diff.files.size} 个文件已修改",
                fontSize = ZhiTextScale.BodySmall,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "+${diff.additions}",
                color = ZhiColors.green(),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "  −${diff.deletions}",
                color = ZhiColors.red(),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DiffFileCard(
    file: DiffFile,
    isDark: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onToggle,
        // `modifier`（animateItem）在**最外层**：它管的是这一整张卡片在 LazyColumn
        // 里的位置变化。`animateContentSize` 在里面，管的是卡片自己展开/收起的高度。
        // 两个动画叠在一起是故意的 —— 展开时高度在变、同时上下邻居在滑动让位。
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .animateContentSize(
                animationSpec = ZhiMotion.sizeSpec,
            ),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 展开指示用 Miuix 图标，不再用 ⌄ / › 字形
            Icon(
                painter = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                contentDescription = if (expanded) "折叠该文件" else "展开该文件",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = file.name.substringAfterLast('/'),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            if (file.additions > 0) {
                Text(text = "+${file.additions}", color = ZhiColors.green(), fontSize = ZhiTextScale.Caption)
            }
            if (file.deletions > 0) {
                Text(text = " −${file.deletions}", color = ZhiColors.red())
            }
        }
        Text(
            text = file.name.substringBeforeLast('/', ""),
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 19.dp, top = 2.dp),
        )
        if (expanded) {
            DiffBlock(diff = file.diff, isDark = isDark, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * 逐行着色渲染 unified diff，对应原版 colorDiff()。
 *
 * <p>自带卡片外壳。**外面已经有卡片时用 [DiffLines]** —— 套两层实心卡会出现
 * 两个不同圆角的底板叠在一起（本工程为同类观感问题改过两次）。
 */
@Composable
fun DiffBlock(diff: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardInnerSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        DiffLines(diff)
    }
}

/**
 * 只做**逐行着色**，不画容器。
 *
 * <p>抽出来有两个理由，都不是"为了少写几行"：
 *
 * 1. **外面已经有一张卡片的调用点要能复用**。工具卡（`chat/MessageCards.kt` 展开的输出区）
 *    自己就是一张 `cardInnerSurface` 卡片，再套一层 `DiffBlock` 会出现两个不同圆角的
 *    实心底板叠在一起 —— 本工程同类观感问题已经改过两次。
 * 2. **着色规则只能有一份**。写在两处，迟早一边补了 `+++`/`---` 的特例而另一边没有，
 *    同一份 diff 在两处颜色不同，而那种差别没人会当成 bug 报上来。
 */
@Composable
fun DiffLines(diff: String) {
    val scheme = MiuixTheme.colorScheme
    val shown = boundedTextLines(diff)
    val lines = remember(shown) { shown.split('\n') }
    lines.forEach { line ->
        // 逐行背景是 diff 的语义着色（+绿/−红），不是装饰容器，因此保留 background
        val (foreground, background) = when {
            line.startsWith("+++") || line.startsWith("---") ->
                scheme.onSurfaceVariantSummary to Color.Transparent
            line.startsWith("+") -> ZhiColors.green() to ZhiColors.greenContainer()
            line.startsWith("-") -> ZhiColors.red() to ZhiColors.redContainer()
            line.startsWith("@@") -> scheme.primary to Color.Transparent
            line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file") ->
                scheme.onSurfaceVariantSummary to Color.Transparent
            else -> scheme.onSurface to Color.Transparent
        }
        Text(
            text = line.ifEmpty { " " },
            color = foreground,
            fontSize = ZhiTextScale.Footnote,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 8.dp),
        )
    }
}

/**
 * 一次最多渲染多少行。
 *
 * <p>为什么需要上限：这里**每一行是一个 `Text`** —— 一个 1000 行的 diff 就是 1000 个
 * 组合 + 1000 个 layout 节点，而且它们全在**同一个 LazyColumn item 里**，
 * 于是那个 item 比视口还高，懒加载的复用彻底失效（滚动时每帧都要处理全部 1000 行）。
 * 工具卡展开的输出是同一个道理，上限更高（40 000 字符 ≈ 2000 行）。
 *
 * <p>选 300 是因为它在"一屏能看多少"（约 40~60 行）之上留了足够余量：
 * 常见的 diff 根本碰不到上限，碰到的是那种"整文件重写"级别的输出 ——
 * 那种输出本来也没人会逐行读。
 *
 * <p>⚠️ 截断只影响**默认渲染**：下面给了一行「还有 N 行 · 点按显示全部」，
 * 点一下就把全量摊出来。数据一个字节都不丢 —— 这很重要，用户是靠这些输出
 * 判断工具到底做了什么，不能因为性能把它们藏没。
 */
internal const val MaxRenderedLines = 300

/**
 * 行数上限内的正文 + 「点按显示全部」。
 *
 * <p>状态放在这里而不是调用点：`DiffLines` 与 [OutputLines] 都在 LazyColumn 的 item 里
 * （有 key），所以展开状态能跟着 item 走；被回收再回来时回到折叠态，
 * 这对长输出是合理的默认。
 */
@Composable
private fun boundedTextLines(text: String): String {
    var showAll by remember(text) { mutableStateOf(false) }
    val limited = remember(text, showAll) {
        if (showAll) LimitedLines(text, 0) else limitLines(text, MaxRenderedLines)
    }
    if (limited.hidden > 0) {
        MoreLinesRow(hidden = limited.hidden, onClick = { showAll = true })
    }
    return limited.text
}

/**
 * 展开的工具输出正文（等宽、不画容器）。
 *
 * <p>与 [DiffLines] 用同一套上限与同一条「还有 N 行」的出路，只是不做逐行着色：
 * 非写文件类工具的输出是普通文本，逐行着色会把普通文本也画成彩色。
 */
@Composable
fun OutputLines(text: String) {
    val shown = boundedTextLines(text)
    Text(
        text = shown,
        fontSize = ZhiTextScale.Footnote,
        fontFamily = FontFamily.Monospace,
    )
}

/** 「还有 N 行 · 点按显示全部」。走 Miuix `Surface(onClick)`，与全工程其它可点行一致。 */
@Composable
private fun MoreLinesRow(hidden: Int, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = scheme.onSurfaceVariantSummary,
    ) {
        Text(
            text = "还有 $hidden 行 · 点按显示全部",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

/**
 * 截断结果：[text] 是要渲染的正文，[hidden] 是被藏起来的行数（0 表示没截）。
 *
 * 之所以带 `hidden` 而不是让调用方自己再数一遍：数行本身就是要避免的那件事
 * （`split('\n')` 会对 40 000 字符的输出建 2000 个 String）。这里在截断的同一次
 * 扫描里顺手把总数也算出来。
 */
internal class LimitedLines(val text: String, val hidden: Int)

/**
 * 只保留前 [max] 行，返回正文与被藏起来的行数。
 *
 * 纯函数（不在组合里、不碰 Compose），所以行为可以用 JVM 单测钉住 ——
 * 它算错的表现是"展开工具输出后少了几行"，而那看起来更像工具没输出，
 * 不像渲染 bug，最容易一路没人报。
 *
 * 边界：不截断时原样返回并 `hidden = 0`；`max <= 0` 表示"全部藏起来"。
 */
internal fun limitLines(text: String, max: Int): LimitedLines {
    if (text.isEmpty()) return LimitedLines(text, 0)
    if (max <= 0) return LimitedLines("", 1 + countNewlines(text, 0))
    var lines = 1
    var cut = -1
    for (i in text.indices) {
        if (text[i] == '\n') {
            lines++
            // 第 max 行在这里结束；从下一个字符开始就是被藏起来的部分。
            if (lines > max) {
                cut = i
                break
            }
        }
    }
    if (cut < 0) return LimitedLines(text, 0)
    // 被藏起来的是 cut 之后的那一整段（它自己也至少算一行）。
    //
    // ⚠️ 行数的定义就是 `split('\n')` 的分段数（换行数 + 1），**不去掉尾部空串** ——
    // Kotlin 的 `split` 与 Java 的 `String.split` 不同，它保留尾部的空串。
    // 渲染侧 `DiffLines` 正是 `shown.split('\n').forEach { Text(...) }`，
    // 所以输出末尾那个 `\n` 会多渲染一个空行；两边的口径必须完全一致，
    // 否则「还有 N 行」会与"点开后多出来几行"对不上。
    return LimitedLines(text.substring(0, cut), lines + countNewlines(text, cut + 1) - max)
}

private fun countNewlines(text: String, from: Int): Int {
    var count = 0
    for (i in from until text.length) {
        if (text[i] == '\n') count++
    }
    return count
}
