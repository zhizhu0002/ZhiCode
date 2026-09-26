package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 对话流里的卡片全部改用 Miuix [Card]：
 * 圆角、按压下沉(pressFeedbackType)、按下态与主题色解析都交给 Miuix，
 * 不再手写 `Box + clip + background + iqPressScale`。
 *
 * 用 Card 的地方**不要**再叠 `iqPressScale`，否则会和 Miuix 的按压反馈打架。
 */
private val BubbleMargin = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
private val MessageMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val GroupMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
private val RowMargin = PaddingValues(horizontal = 9.dp, vertical = 7.dp)

/** 空态，对应原版 addEmptyState()。 */
@Composable
fun EmptyState() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 82.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "想让 IQ 做什么？",
            color = scheme.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "蜘蛛可以读取项目、编辑文件、运行命令，并在内置 Termux 环境中验证修改。",
            color = scheme.onBackgroundVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
    }
}

/** 用户消息气泡，右对齐。长按触发操作菜单。 */
@Composable
fun UserBubble(item: ChatItem, onLongPress: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Card(
            onClick = onLongPress,
            modifier = Modifier.fillMaxWidth(0.86f),
            cornerRadius = ZhiRadius.card,
            insideMargin = BubbleMargin,
            colors = CardDefaults.defaultColors(
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            ),
            pressFeedbackType = PressFeedbackType.Sink,
            holdDownState = true,
        ) {
            Text(
                text = item.body,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * 助手消息：与用户气泡一样套一层卡片底色，占满宽度、配色中性。
 *
 * 结构（自上而下）：思考面板 → 正文 → 上下文页脚。
 *
 * 原版 `renderAssistant` 只给用户气泡和工具行上底色，这里是**有意偏离**：
 * 只有一方有框时，AI 的消息看起来像没有归属的裸文本。操作入口是整块卡片的
 * 长按（原先那个独占一行的 ⋯ 已移除，见函数内注释）。
 */
@Composable
fun AssistantCard(
    item: ChatItem,
    onToggleThinking: () -> Unit,
    onLongPress: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 与 UserBubble 用同一套容器参数（同样的 ZhiRadius.card 圆角与 BubbleMargin 内边距），
    // 只是配色中性、占满宽度。
    //
    // 此前这里是 `Surface(color = Color.Transparent)`，也就是**没有框**：于是只有用户的
    // 消息有气泡、AI 的消息像裸文本贴在背景上，双方看起来不对等。
    //
    // 那个独占一行的 ⋯（"消息操作"）同时去掉：它是一个高 26dp 的满宽 Row，位置在每条
    // AI 消息的**最顶部**，所以看上去像粘在上一条消息的下沿、并和上一条的按压高亮连成
    // 一片。消息操作仍然可用 —— 整块卡片本身就是入口（onLongPress）。
    Card(
        onLongPress = onLongPress,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = BubbleMargin,
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        // ⚠️ 必须是 None，别改成 Sink。
        // 这张卡只有**长按**才有动作（打开消息操作菜单），短按什么都不发生。
        // 而 Sink 的反馈是"按下即缩放"，于是短按也会看到整块卡片缩放一下、
        // 松手却没有反应 —— 看起来像卡了点不动，也把阅读中的正文整块顶得晃动。
        pressFeedbackType = PressFeedbackType.None,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
                ),
        ) {
            if (item.thinking.isNotEmpty() || item.processSteps.isNotEmpty()) {
                ThinkingPanel(item = item, onToggle = onToggleThinking)
            }
            // 正文走 Markdown（标题/列表/代码块/表格/引用/链接…）。
            // 流式光标用一个独立的 Text 尾随，而不是拼进 Markdown 源里 ——
            // 拼进去的话光标会被当成行内内容参与解析（例如紧跟在 ` 后面会变成代码）。
            ZhiMarkdown(source = item.body, bodyFontSize = 14.sp)
            if (item.streaming) {
                Text(
                    text = "▍",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (!item.streaming && item.contextTokens >= 0) {
                ContextFooter(
                    tokens = item.contextTokens,
                    window = item.contextWindow,
                )
            }
        }
    }
}

/**
 * 上下文页脚，复刻原版 `addContextFooter`（`MainActivity.java:2152-2166`）：
 * `上下文 X / Y · 剩余 N%`，等宽小字，并按占用率换色（≥90% 红 / ≥72% 强调色）。
 */
@Composable
private fun ContextFooter(tokens: Int, window: Int) {
    val scheme = MiuixTheme.colorScheme
    val safeWindow = if (window > 0) window else 200_000
    val usedPct = Math.round(tokens * 100.0 / safeWindow).toInt().coerceIn(0, 999)
    val remaining = (100 - usedPct.coerceAtMost(100)).coerceAtLeast(0)
    val color = when {
        usedPct >= 90 -> ZhiColors.red()
        usedPct >= 72 -> scheme.primary
        else -> scheme.onSurfaceVariantSummary
    }
    val label = "上下文 " + WorkspaceViewModel.formatTokens(tokens) + " / " +
        WorkspaceViewModel.formatTokens(safeWindow) + " · 剩余 " + remaining + "%"
    Text(
        text = label,
        color = color,
        fontSize = 9.3.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 5.dp, end = 3.dp),
    )
}

/** 思考过程折叠面板，对应原版 refreshAssistantThinking()。 */
@Composable
private fun ThinkingPanel(item: ChatItem, onToggle: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        // 表头点击走 Miuix Surface(onClick)，不再手写 Modifier.clickable
        Surface(
            onClick = onToggle,
            color = Color.Transparent,
            contentColor = scheme.primary,
        ) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (item.thinkingExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                        contentDescription = if (item.thinkingExpanded) "折叠思考过程" else "展开思考过程",
                        tint = scheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "思考过程" + (if (item.streaming) " · 进行中" else ""),
                        color = scheme.primary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        if (item.thinkingExpanded) {
            // 展开/折叠走动画，高度平滑变化
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
                ),
            ) {
                if (item.processSteps.isNotEmpty()) {
                    item.processSteps.forEach { step ->
                        Text(
                            text = "· $step",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                        )
                    }
                }
                if (item.thinking.isNotEmpty()) {
                    Text(
                        text = item.thinking,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
            }
        } else if (item.thinking.isNotEmpty()) {
            Text(
                text = item.thinking,
                color = scheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** 折叠工具组卡片，对应原版 addCollapsedToolActivity() + collapsedActivityLabel()。 */
@Composable
fun ToolGroupCard(
    item: ChatItem,
    onToggleTool: (String) -> Unit,
    /** 参数是**目标状态**：true 表示点下去后应展开，false 表示应收起。 */
    onToggleGroup: (Boolean) -> Unit,
    onActions: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val anyExpanded = item.tools.any { it.expanded }
    val completed = item.tools.count { it.completed }
    val failed = item.tools.count { it.failed }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(
                animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
            ),
        cornerRadius = ZhiRadius.card,
        insideMargin = GroupMargin,
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        // 表头点击走 Miuix Surface(onClick)：不再手写 Modifier.clickable。
        // 传目标状态：已展开时点一下应收起（此前误传 anyExpanded，导致展开后收不回）
        Surface(
            onClick = { onToggleGroup(!anyExpanded) },
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            contentColor = scheme.onSurface,
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (anyExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                contentDescription = if (anyExpanded) "折叠工具列表" else "展开工具列表",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (item.groupCompleted && failed == 0) "已运行 ${item.tools.size} 个工具" else "正在运行工具",
                color = scheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 5.dp),
            )
            Box(modifier = Modifier.weight(1f))
            // 计数用 Miuix Badge：失败时换成红色容器
            Badge(
                containerColor = if (failed > 0) ZhiColors.red() else scheme.surfaceContainerHighest,
                contentColor = if (failed > 0) scheme.onPrimary else scheme.onSurfaceVariantSummary,
            ) {
                Text(
                    text = "$completed/${item.tools.size}" + if (failed > 0) " · $failed 失败" else "",
                    fontSize = 10.sp,
                )
            }
        }
        } // Surface(onClick) 表头
        if (item.groupLabel.isNotEmpty()) {
            Text(
                text = item.groupLabel,
                color = scheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp),
            )
        }
        if (anyExpanded) {
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
                ),
            ) {
                item.tools.forEach { tool ->
                    ToolRow(
                        activity = tool,
                        onToggle = { onToggleTool(tool.id) },
                        onActions = onActions,
                    )
                }
            }
        }
    }
}

/**
 * 单个工具行，复刻原版 `addToolCard()`（`MainActivity.java:2344-2430`）。
 *
 * 与原版一致的要点：
 * - 整行左缩进 20dp、行高 25dp；
 * - 行首是**状态字形**：`✓`(完成) / `×`(失败) / `●`(运行中) / `○`(等待授权)；
 * - 名称 11.5sp 粗体；摘要 10.5sp 等宽、单行省略、`weight(1f)`；
 * - `+N` / `−N` 是**纯文字着色**（9.5sp 等宽），不套底色胶囊；
 * - 右端 `⋯` 打开操作菜单，`⌄` / `⌃` 折叠展开（仅完成且有详情时出现）；
 * - Bash 的命令**不放标题行**（原版注释：否则命令会出现两次），改为独立下一行；
 * - 折叠态只显示 `⎿ 摘要`（等宽、缩进 23dp），展开态才渲染全量输出。
 */
@Composable
private fun ToolRow(
    activity: ToolActivity,
    onToggle: () -> Unit,
    onActions: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val isCommand = activity.kind == ToolKind.COMMAND
    val hasDetails = activity.output.isNotBlank()
    val showChevron = activity.completed && hasDetails

    // 整行点击：Miuix Surface(onClick)（color = Transparent 不填色，仅取按压反馈）
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = scheme.onSurface,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 1.dp, end = 2.dp, bottom = 4.dp)
            .animateContentSize(
                animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolStatusGlyph(activity)

            Text(
                text = activity.displayName,
                color = scheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 命令类工具的命令单独成行，这里留空占位（原版行为）
            if (!isCommand && activity.summary.isNotEmpty()) {
                Text(
                    text = "  " + activity.summary,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }

            if (activity.additions > 0) DiffCount("+${activity.additions}", ZhiColors.green())
            if (activity.deletions > 0) DiffCount("−${activity.deletions}", ZhiColors.red())

            // ⋯ 与 ⌄/⌃ 都转发到 Miuix IconButton（compact 覆盖其 40dp 最小尺寸）
            ZhiIconButton(
                icon = ZhiIcons.more,
                description = "工具操作",
                onClick = onActions,
                tint = scheme.onSurfaceVariantSummary,
                iconSize = 15.dp,
                compact = 25.dp,
            )

            if (showChevron) {
                ZhiIconButton(
                    icon = if (activity.expanded) ZhiIcons.chevronUp else ZhiIcons.chevronDown,
                    description = if (activity.expanded) "折叠输出" else "展开输出",
                    onClick = onToggle,
                    tint = scheme.onSurfaceVariantSummary,
                    iconSize = 14.dp,
                    compact = 28.dp,
                )
            }
        }

        // Bash 命令：等宽、缩进，与图片里「命令在名称下方」一致
        if (isCommand && activity.summary.isNotEmpty()) {
            Text(
                text = "  " + activity.summary,
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 23.dp),
            )
        }

        when {
            // 运行中 / 等待授权：底部一行状态说明
            !activity.completed -> Text(
                text = runningToolLabel(activity),
                color = if (activity.awaitingPermission) scheme.primary else scheme.onSurfaceVariantSummary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 23.dp, top = 2.dp),
            )
            // 折叠且有详情：只显示 ⎿ 紧凑摘要
            hasDetails && !activity.expanded -> Text(
                text = "  ⎿  " + compactToolSummary(activity),
                color = if (activity.failed) ZhiColors.red() else scheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 23.dp, bottom = 2.dp),
            )
            // 展开：全量输出
            activity.expanded && hasDetails -> Card(
                modifier = Modifier.padding(start = 23.dp, top = 5.dp),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(8.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardInnerSurface(),
                    contentColor = scheme.onSurfaceVariantSummary,
                ),
            ) {
                Text(
                    text = activity.output,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
    } // Surface(onClick)
}

/**
 * 行首状态字形，复刻原版 `addToolCard()` 的 `statusGlyph`：
 * 完成 `✓`（绿）/ 失败 `×`（红）/ 等待授权 `○`（强调色）/ 运行中 `●`（强调色）。
 * 固定 23dp 宽保证多行左对齐。
 */
@Composable
private fun ToolStatusGlyph(activity: ToolActivity) {
    val scheme = MiuixTheme.colorScheme
    // 状态用 Miuix 图标 / 进度指示器表达，不再手写 ✓ × ○ ● 这些字符：
    // 字符字形依赖字体，等宽字体缺字时会显示成方块，而且颜色/粗细无法统一。
    Box(modifier = Modifier.width(23.dp), contentAlignment = Alignment.Center) {
        when {
            activity.completed && activity.failed -> Icon(
                imageVector = ZhiIcons.failed,
                contentDescription = "失败",
                tint = ZhiColors.red(),
                modifier = Modifier.size(13.dp),
            )
            activity.completed -> Icon(
                imageVector = ZhiIcons.done,
                contentDescription = "完成",
                tint = ZhiColors.green(),
                modifier = Modifier.size(13.dp),
            )
            activity.awaitingPermission -> Icon(
                imageVector = ZhiIcons.awaiting,
                contentDescription = "等待授权",
                tint = scheme.primary,
                modifier = Modifier.size(13.dp),
            )
            // 运行中：不确定态转圈，而不是一个静止的 ●（静止字符看不出"在进行"）
            else -> InfiniteProgressIndicator(size = 12.dp, color = scheme.primary)
        }
    }
}

/** 运行中/等待授权的说明文字，对应原版 `runningToolLabel()`。 */
private fun runningToolLabel(activity: ToolActivity): String = when {
    activity.awaitingPermission -> "等待授权…"
    activity.elapsedMs > 0 -> "运行中 · ${formatElapsed(activity.elapsedMs)}"
    else -> "运行中…"
}

/**
 * 折叠态的紧凑结果摘要，复刻原版 `compactResult()`（`MainActivity.java:2764-2776`）
 * 与 `addToolCard()` 里 diff 优先的分支：
 * - 有 diff → `N 行已修改 · 点按展开`
 * - 命令类失败 → `退出码 N · 原因 · 点按展开`
 * - 命令类成功 → `N 行 · 点按展开`
 * - 其他工具：单行且够短就直接显示，否则 `N 行 · 点按展开`
 */
private fun compactToolSummary(activity: ToolActivity): String {
    val changed = activity.additions + activity.deletions
    if (changed > 0) return "$changed 行已修改 · 点按展开"

    val clean = activity.output.trim()
    if (clean.isEmpty()) return "已完成"
    val lineCount = clean.split('\n').size

    if (activity.kind == ToolKind.COMMAND) {
        val failedExit = activity.failed || (activity.exitCode != null && activity.exitCode != 0)
        if (failedExit) {
            val why = firstUsefulErrorLine(clean)
            val code = activity.exitCode ?: 1
            return buildString {
                append("退出码 ").append(code)
                if (why.isNotEmpty()) append(" · ").append(why.take(105))
                if (lineCount > 1) append(" · 点按展开")
            }
        }
        return "$lineCount 行 · 点按展开"
    }

    if (lineCount == 1 && clean.length <= 96) return clean
    return "$lineCount 行 · 点按展开"
}

/** 取第一条有用的错误行，对应原版 `firstUsefulErrorLine()`。 */
private fun firstUsefulErrorLine(text: String): String =
    text.split('\n').firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/** 行内 diff 计数：原版是**纯文字着色**（9.5sp 等宽），没有底色胶囊。 */
@Composable
private fun DiffCount(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 9.5.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 5.dp),
    )
}

/** 错误卡片。 */
@Composable
fun ErrorCard(item: ChatItem) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = MessageMargin,
        colors = CardDefaults.defaultColors(
            color = scheme.errorContainer,
            contentColor = scheme.onErrorContainer,
        ),
    ) {
        Text(text = item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        // 错误正文也走 Markdown：`ToolText.friendlyError` 会输出带 `代码` 与列表的
        // 可操作建议，与回复正文保持一致。
        ZhiMarkdown(
            source = item.body,
            bodyFontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 提示卡片（斜杠命令输出、计划审批结果等）。 */
@Composable
fun InfoCard(item: ChatItem) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = BubbleMargin,
        colors = CardDefaults.defaultColors(
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ),
    ) {
        if (item.title.isNotEmpty()) {
            Text(text = item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        // 提示正文也走 Markdown。这些内容里大量使用 `反引号` 标记命令与参数，
        // 原先那个"整段变等宽"的启发式太粗（一句里只要有反引号，全段都成等宽），
        // 现在由行内解析只给反引号包住的部分加等宽 + 底色。
        ZhiMarkdown(
            source = item.body,
            bodyFontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}


private fun formatElapsed(millis: Long): String {
    if (millis <= 0) return "0.0s"
    val seconds = millis / 1000.0
    if (seconds < 60) return String.format(java.util.Locale.US, "%.1fs", seconds)
    val minutes = (seconds / 60).toInt()
    return "$minutes:${String.format(java.util.Locale.US, "%04.1f", seconds % 60)}"
}
