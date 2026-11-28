package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.data.ChatFontLoader
import com.zhizhu.zhicode.compose.engine.ToolText
import com.zhizhu.zhicode.compose.model.ToolActions
import com.zhizhu.zhicode.compose.model.ChatImage
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ErrorSummary
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolGrouping
import com.zhizhu.zhicode.compose.model.LiveOutput
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiAttachmentRow
import com.zhizhu.zhicode.compose.ui.ZhiImageRow
import com.zhizhu.zhicode.compose.ui.ZhiImageViewer
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiNoticeBar
import com.zhizhu.zhicode.compose.ui.ZhiNoticeTone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 对话流里的卡片全部改用 Miuix [Card] */
private val BubbleMargin = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
private val MessageMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
/** Markdown above this size is previewed first; the full body remains in the model. */
private const val MaxInitialMarkdownChars = 120_000

/** 用户气泡宽度占可用宽的比例（**上限**，不是固定值）。 */
private val BubbleMaxWidthFraction = 0.86f
/** 折叠组表头的内边距。 */
private val GroupMargin = PaddingValues(start = 7.dp, top = 3.dp, end = 4.dp, bottom = 3.dp)
private val RowMargin = PaddingValues(horizontal = 9.dp, vertical = 7.dp)

/** 空态，对应原版 addEmptyState()。 */
@Composable
fun EmptyState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 82.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "想让 ZhiCode 做什么？",
            color = scheme.onBackground,
            fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "ZhiCode 可以读取项目、编辑文件、运行命令，并在内置 Debian 容器中验证修改。",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
    }
}

/** 会话正在载入时的占位。 */
@Composable
fun TranscriptLoadingState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 82.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "正在载入会话…",
            color = scheme.onSurfaceVariant,
            fontSize = MiuixTheme.textStyles.title2.fontSize,
        )
        Text(
            text = "内容较多时需要几秒。正在读取最近的若干条消息。",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
    }
}

/** 列表顶部「上面还有更早的历史」的占位。 */
@Composable
fun OlderLoader(loading: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // 文案里写出**具体条数**：只说"更早的消息"时用户不知道一次补多少，
    // 也就无法判断"要不要为了找某一条一直往下拉"。
    val count = com.zhizhu.zhicode.compose.data.SessionReader.PAGE_MESSAGES
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (loading) "正在加载更早的 $count 条…" else "下拉或继续上滑，加载更早的 $count 条",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 「已经到会话开头」的终点提示。 */
@Composable
fun TranscriptStartHint() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "已经到会话开头",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 列表末尾的「回到最新」。 */
@Composable
fun JumpToLatest(onJumpToLatest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp, end = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onJumpToLatest,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.primary,
            shape = ZhiSquircle(LocalZhiPageSet.current.innerCorner),
        ) {
            Text(
                text = "回到最新",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 用户消息气泡，右对齐。长按触发操作菜单。 */
@Composable
fun UserBubble(item: ChatItem, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 放大查看的当前图。
    var viewing by remember { mutableStateOf<ChatImage?>(null) }

    // 气泡位于 LazyColumn item 内，不再用 BoxWithConstraints 触发测量期子组合。
    // widthIn 的固定上限交给父级列表宽度处理，窄屏/宽屏都保持内容自适应。
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            // 宽度 = 「内容自适应，上限 86%」：先按可用宽取 86% 作为**上限**
            modifier = Modifier
                .widthIn(max = 420.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            shape = RoundedCornerShape(ZhiRadius.floating),
            /* 用户气泡：**蓝底白字**（用户明确要求）。 */
            color = scheme.primary,
            contentColor = scheme.onPrimary,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // 附件画在正文**上方**：一条「图 + 一句话」的消息，图是主体
            ZhiAttachmentRow(
                attachments = item.attachments,
                images = item.images,
                onOpenImage = { viewing = it },
            )
            if (item.body.isNotBlank()) {
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                )
            }
            }
        }
    }

    // 放大查看：浮层挂在气泡之外（OverlayDialog 画在主窗口里）。
    ZhiImageViewer(image = viewing, onDismiss = { viewing = null })
}

/** 助手消息：与用户气泡一样套一层卡片底色，占满宽度、配色中性。 */
@Composable
fun AssistantCard(
    item: ChatItem,
    onToggleThinking: () -> Unit,
    onLongPress: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chatSettings = LocalThemeSettings.current
    val chatFontScale = chatSettings.chatFontScale
    val chatFontFamily = ChatFontLoader.rememberChatFontFamily(chatSettings)
    var showFullBody by remember(item.id) { mutableStateOf(false) }
    val bodyTooLong = item.body.length > MaxInitialMarkdownChars
    val markdownBody = if (bodyTooLong && !showFullBody) {
        item.body.take(MaxInitialMarkdownChars)
    } else {
        item.body
    }
    // 与 UserBubble 用同一套容器参数（同样的圆角与内边距）
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
        // 助手正文使用极轻的容器底，保留阅读连续性，同时与用户气泡形成清晰层级。
        color = LocalZhiPageSet.current.cardSurface(),
        contentColor = scheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                // clip 在 animateContentSize **外侧**：思考面板展开/收起时文字
                // 按自然高度绘制、被逐帧露出来（详见 ToolGroupCard 的注释）。
                .clipToBounds()
                .then(if (item.streaming) Modifier else Modifier.animateContentSize(animationSpec = ZhiMotion.sizeSpec)),
        ) {
            if (item.thinking.isNotEmpty() || item.processSteps.isNotEmpty()) {
                ThinkingPanel(item = item, onToggle = onToggleThinking)
            }
            // 正文走 Markdown（标题/列表/代码块/表格/引用/链接…）。
            ZhiMarkdown(
                source = markdownBody,
                // 字号与字体来自「聊天显示」页（`ThemeSettings.chatFontScale` /
                bodyFontSize = MiuixTheme.textStyles.main.fontSize * chatFontScale,
                bodyFontFamily = chatFontFamily,
                streaming = item.streaming && !bodyTooLong,
            )
            if (bodyTooLong && !showFullBody) {
                Surface(
                    onClick = { showFullBody = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Color.Transparent,
                    contentColor = scheme.primary,
                ) {
                    Text(
                        text = "正文较长，已显示前 ${MaxInitialMarkdownChars / 1000} KB · 点按展开全部",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (item.streaming) {
                val cursor = rememberInfiniteTransition(label = "stream-cursor")
                val cursorAlpha = cursor.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(ZhiMotion.FADE_IN_MILLIS, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "stream-cursor-alpha",
                )
                Text(
                    text = "▍",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .graphicsLayer { alpha = cursorAlpha.value },
                )
            }
            /* 元信息行：耗时 · 改动文件。 */
            val hasMeta = !item.streaming && (item.elapsedMs >= 0 || item.editedFiles.isNotEmpty())
            if (hasMeta) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.elapsedMs >= 0) {
                        Text(
                            text = formatElapsed(item.elapsedMs),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (item.editedFiles.isNotEmpty()) {
                        Text(
                            text = if (item.elapsedMs >= 0) " · " else "",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            // 只列前几个 + 计数：一屏里改二十个文件时，
                            // 把它们全写出来会把卡片撑得很长。
                            text = "改了 " + item.editedFiles.take(2).joinToString("、") +
                                if (item.editedFiles.size > 2) " 等 ${item.editedFiles.size} 个文件" else "",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
                // 上下文页脚随高度出现/收回，不让文字单独淡入淡出。
            AnimatedVisibility(
                visible = !item.streaming && item.contextTokens >= 0,
                enter = expandVertically(animationSpec = ZhiMotion.sizeSpec, expandFrom = Alignment.Top),
                exit = shrinkVertically(animationSpec = ZhiMotion.sizeSpec, shrinkTowards = Alignment.Top),
            ) {
                ContextFooter(
                    tokens = item.contextTokens,
                    window = item.contextWindow,
                )
            }
        }
    }
}

/** 上下文页脚，复刻原版 `addContextFooter`（`MainActivity.java:2152-2166`） */
@Composable
private fun ContextFooter(tokens: Int, window: Int) {
    val scheme = MaterialTheme.colorScheme
    val safeWindow = if (window > 0) window else 200_000
    val usedPct = Math.round(tokens * 100.0 / safeWindow).toInt().coerceIn(0, 999)
    val remaining = (100 - usedPct.coerceAtMost(100)).coerceAtLeast(0)
    val color = when {
        usedPct >= 90 -> ZhiColors.red()
        usedPct >= 72 -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    val label = "上下文 " + WorkspaceViewModel.formatTokens(tokens) + " / " +
        WorkspaceViewModel.formatTokens(safeWindow) + " · 剩余 " + remaining + "%"
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 5.dp, end = 3.dp),
    )
}

/** 思考过程折叠面板，对应原版 refreshAssistantThinking()。 */
@Composable
private fun ThinkingPanel(item: ChatItem, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 点击区域覆盖**整块**面板（表头 + 展开后的正文 + 折叠时的一行摘要）
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        color = Color.Transparent,
        contentColor = scheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = if (item.thinkingExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                    contentDescription = if (item.thinkingExpanded) "折叠思考过程" else "展开思考过程",
                    tint = scheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "思考过程" + (if (item.streaming) " · 进行中" else ""),
                    color = scheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            // 展开/收起只改变内容高度，不再交叉淡化两份文本；旧内容会从顶部
            // 逐帧收回，新内容也从同一条边缘展开，和工具组保持同一交互模型。
            AnimatedContent(
                targetState = item.thinkingExpanded,
                transitionSpec = {
                    expandVertically(animationSpec = ZhiMotion.sizeSpec, expandFrom = Alignment.Top) togetherWith
                        shrinkVertically(animationSpec = ZhiMotion.sizeSpec, shrinkTowards = Alignment.Top)
                },
                label = "thinking-panel",
            ) { expanded ->
            if (expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (item.processSteps.isNotEmpty()) {
                        item.processSteps.forEach { step ->
                            Text(
                                text = "· $step",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                            )
                        }
                    }
                    if (item.thinking.isNotEmpty()) {
                        Text(
                            text = item.thinking,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        )
                    }
                }
            } else if (item.thinking.isNotEmpty()) {
                Text(
                    text = item.thinking,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        }
    }
}

@Composable
fun ToolBatch(
    item: ChatItem,
    onToggleTool: (String) -> Unit,
    /** 参数是**那一组**的 groupKey（首成员 toolId）。 */
    onToggleGroup: (String) -> Unit,
    onToolAction: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val toolById = remember(item.tools) { item.tools.associateBy { it.id } }
    val segments = remember(item.tools) {
        ToolGrouping.group(item.tools.map { it.toGroupingEntry() })
    }

    /* 运行中的秒表。 */
    val runningClock = rememberRunningClock(item.tools.any { !it.completed })

    // 放大查看的当前图。
    var viewing by remember { mutableStateOf<ChatImage?>(null) }

    // 段与段之间的间隔：单条卡与组卡各自带垂直留白，所以这里不再额外加 padding
    // （两边都加会让"两条命令之间"的缝比"命令与它的输出之间"还宽）。
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is ToolGrouping.Segment.Single -> {
                    val tool = toolById[segment.entry.toolId] ?: return@forEach
                    key(tool.id) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
                            color = LocalZhiPageSet.current.cardSurface(),
                            contentColor = scheme.onSurface,
                        ) {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
                        ToolRow(
                            activity = tool,
                            nowMs = runningClock,
                            onToggle = { onToggleTool(tool.id) },
                            // 传**这一行**的 id：菜单内容与动作都按它算。
                            onToolAction = { label -> onToolAction(tool.id, label) },
                            onImageOpen = { viewing = it },
                        )
                        }
                        }
                    }
                }
                is ToolGrouping.Segment.Group -> {
                    val members = segment.members.mapNotNull { member -> toolById[member.toolId] }
                    if (members.isEmpty()) return@forEach
                    key(segment.key) {
                        ToolGroupCard(
                            members = members,
                            expanded = segment.key in item.expandedGroups,
                            onToggle = { onToggleGroup(segment.key) },
                            nowMs = runningClock,
                            onToggleTool = onToggleTool,
                            onRowAction = onToolAction,
                            onImageOpen = { viewing = it },
                        )
                    }
                }
            }
        }
    }

    // 放大查看：与 `UserBubble` 同一形态（全屏黑底 + `ContentScale.Fit` + 右上 ×）
    ZhiImageViewer(image = viewing, onDismiss = { viewing = null })
}

/** 界面模型 → 分组判据。字段一一对应，于是两边不会各判一次"算不算候选"。 */
private fun ToolActivity.toGroupingEntry(): ToolGrouping.Entry = ToolGrouping.Entry(
    toolId = id,
    name = toolName,
    hint = hint,
    readRequests = readRequests,
    completed = completed,
    failed = failed,
)

/** 折叠组卡片，对应原版 `addCollapsedToolActivity()` + `collapsedActivityLabel()`。 */
@Composable
private fun ToolGroupCard(
    members: List<ToolActivity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    nowMs: Long,
    onToggleTool: (String) -> Unit,
    /** 传给每一行的回调（**带 toolId**，见 [ToolBatch] 的说明）。 */
    onRowAction: (String, String) -> Unit,
    /** 某一行里的预览图被点开（需要全屏查看）。宿主在 [ToolBatch]。 */
    onImageOpen: (ChatImage) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 这张卡片只需要"这一组"自己的判据，所以在这里重建一份 Segment.Group —
    // 判据仍然出自 ToolGrouping，不在这里另写一套计数。
    val group = remember(members) {
        ToolGrouping.Segment.Group(
            key = members.first().id,
            members = members.map { it.toGroupingEntry() },
        )
    }
    val done = ToolGrouping.isDone(group)
    val failed = ToolGrouping.hasFailure(group)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
        color = LocalZhiPageSet.current.cardSurface(),
        contentColor = scheme.onSurface,
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        // 表头点击走 Miuix Surface(onClick)：不再手写 Modifier.clickable。
        // 传的是"切换"：展开态由 `ChatItem.expandedGroups` 记账，这一层不去推目标状态。
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            contentColor = scheme.onSurface,
        ) {
        Row(
            // 反编译版组头：`setPadding(dp(7), dp(3), dp(4), dp(3))`
            modifier = Modifier.fillMaxWidth().padding(GroupMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 行首是**状态字形**，与单条工具行同一个口径：有失败 → 红，跑完 → 绿
            Icon(
                painter = when {
                    failed -> ZhiIcons.failed
                    done -> ZhiIcons.done
                    else -> ZhiIcons.pending
                },
                contentDescription = null,
                tint = when {
                    failed -> ZhiColors.red()
                    done -> ZhiColors.green()
                    else -> scheme.primary
                },
                modifier = Modifier.size(13.dp),
            )
            // 标题直接更新；组本身的展开/收起由内容高度过渡负责，避免标题和
            // 内容同时保留两套淡变文本。
            Text(
                text = ToolGrouping.label(group, batchDone = done),
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
        }
        } // Surface(onClick) 表头
        Text(
            text = ToolGrouping.subtitle(group, expanded = expanded, batchDone = done),
            color = if (failed) ZhiColors.red() else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 21.dp, top = 2.dp),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = ZhiMotion.sizeSpec, expandFrom = Alignment.Top),
            exit = shrinkVertically(animationSpec = ZhiMotion.sizeSpec, shrinkTowards = Alignment.Top),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                members.forEach { tool ->
                    key(tool.id) {
                        ToolRow(
                            activity = tool,
                            nowMs = nowMs,
                            onToggle = { onToggleTool(tool.id) },
                            onToolAction = { label -> onRowAction(tool.id, label) },
                            onImageOpen = onImageOpen,
                        )
                    }
                }
            }
        }
    }
    } // Card
}

/** 单个工具行，复刻原版 `addToolCard()`（`MainActivity.java:2344-2430`）。 */
@Composable
private fun ToolRow(
    activity: ToolActivity,
    nowMs: Long,
    onToggle: () -> Unit,
    /** 这一行的菜单里选中了一项（参数是菜单文案）。 */
    onToolAction: (String) -> Unit,
    /** 展开态里的预览图被点开。 */
    onImageOpen: (ChatImage) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isCommand = activity.kind == ToolKind.COMMAND
    // 引擎单独算好的统一 diff（写文件类工具才有）。它是**另一块井**，
    // 和 output 并列显示，不是二选一 —— 见下面 EXPANDED 分支。
    val hasDiff = activity.diff.isNotBlank()
    // 预览也算"有详情"：一个只带截图、自述为空的工具同样应该能展开看那张图。
    val hasDetails = activity.output.isNotBlank() || hasDiff || activity.previews.isNotEmpty()
    val showChevron = activity.completed && hasDetails

    val isFileDiff = remember(activity.toolName, activity.output) {
        activity.isFileDiff()
    }
    // 命令行的折叠/展开两副面孔：展开时看**原始**命令，折叠时才用被截短的摘要。
    // 恢复出来的历史工具没有 `command`（旧记录里没存），那时退回摘要 —— 有总比空着好。
    val commandLine = if (activity.expanded && activity.command.isNotBlank()) activity.command
    else activity.summary
    val collapsedSummary = remember(
        activity.kind,
        activity.output,
        activity.additions,
        activity.deletions,
        activity.failed,
        activity.exitCode,
    ) {
        compactToolSummary(activity)
    }

    val menuFlags = ToolActions.flags(
        kind = activity.kind,
        summary = activity.summary,
        output = activity.output,
        isFileDiff = isFileDiff || hasDiff,
        completed = activity.completed,
        expanded = activity.expanded,
    )

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
            // 运行中不挂尺寸动画：这段时间里状态行每秒都在换文字（elapsed）
            .clipToBounds()
            .then(
                if (activity.completed) {
                    Modifier.animateContentSize(animationSpec = ZhiMotion.sizeSpec)
                } else {
                    Modifier
                },
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 命令类工具的命令单独成行，这里留空占位（原版行为）
            if (!isCommand && activity.summary.isNotEmpty()) {
                Text(
                    text = "  " + activity.summary,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
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

            val menuItems = remember(menuFlags, onToolAction) {
                ToolActions.options(menuFlags).map { label ->
                    ZhiMenuItem(text = label, onClick = { onToolAction(label) })
                }
            }

            ZhiIconDropdownMenu(
                items = menuItems,
                minHeight = 25.dp,
                minWidth = 25.dp,
                cornerRadius = 12.5.dp,
                // 透明底：底色是工具组那张 Card，触发按钮不该再画一层
                backgroundColor = Color.Transparent,
            ) {
                Icon(
                    painter = ZhiIcons.more,
                    contentDescription = "工具操作",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

        }

        if (isCommand && commandLine.isNotEmpty()) {
            Text(
                text = "  " + commandLine,
                color = if (activity.expanded) scheme.onSurface else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                // 展开时**不限制行数**：完整命令是用户主动要求看的，再截就等于没展开。
                maxLines = if (activity.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                // 反编译版：`setPadding(dp(22), 0, dp(4), dp(2))`
                modifier = Modifier.padding(start = 22.dp),
            )
        }

        // 状态区只有尺寸过渡：旧内容从顶部收回，新内容从同一边缘展开。
        // 不使用淡入淡出，避免长输出在收回时先淡掉、再让卡片缩小。
        AnimatedContent(
            targetState = toolStatusRegion(activity),
            transitionSpec = {
                expandVertically(animationSpec = ZhiMotion.sizeSpec, expandFrom = Alignment.Top) togetherWith
                    shrinkVertically(animationSpec = ZhiMotion.sizeSpec, shrinkTowards = Alignment.Top)
            },
            label = "tool-status",
        ) { region ->
            when (region) {
                // 运行中：**终端井**（对齐反编译版 `addToolCard` 的 `else if (!item.completed)` 分支）。
                ToolStatusRegion.RUNNING -> RunningPanel(activity, nowMs, isCommand = isCommand)
                ToolStatusRegion.COLLAPSED -> Text(
                    text = "  ⎿  " + collapsedSummary,
                    color = if (activity.failed) ZhiColors.red() else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 22.dp, bottom = 2.dp),
                )
                // 展开：**两块井**，与参考实现 `addToolCard` 一致 
                ToolStatusRegion.EXPANDED -> Column(modifier = Modifier.fillMaxWidth()) {
                    if (activity.previews.isNotEmpty()) {
                        ZhiImageRow(
                            images = activity.previews,
                            onOpen = onImageOpen,
                            modifier = Modifier.padding(start = 23.dp),
                        )
                    }
                    val diffText = if (hasDiff) activity.diff else if (isFileDiff) activity.output else ""
                    if (diffText.isNotBlank()) {
                        Surface(
                            modifier = Modifier.padding(start = 23.dp, top = 5.dp),
                            shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
                            color = ZhiColors.terminalSurface(),
                            contentColor = scheme.onSurfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                DiffLines(diffText)
                            }
                        }
                    }
                    if (activity.output.isNotBlank()) {
                        Surface(
                            modifier = Modifier.padding(start = 23.dp, top = 5.dp),
                            shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
                            color = ZhiColors.terminalSurface(),
                            contentColor = scheme.onSurfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                // OutputLines 与 DiffLines 共用同一套「最多渲染 300 行 + 点按显示全部」
                                OutputLines(activity.output)
                            }
                        }
                    }
                }
                ToolStatusRegion.QUIET -> Unit
            }
        }
    }
    } // Surface(onClick)
}

@Composable
private fun RunningPanel(activity: ToolActivity, nowMs: Long, isCommand: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val live = remember(activity.kind, activity.output) { liveOutputPreview(activity) }
    val label = runningToolLabel(activity, nowMs)

    if (!isCommand) {
        // 非命令类：计时一行 + 井里的输出尾部。它们多数只吐一段文本（读到的片段、
        // 搜索结果），没有"行"结构，所以按字符取尾部（见 liveOutputPreview）。
        Column(modifier = Modifier.padding(start = 23.dp, top = 2.dp)) {
            Text(
                text = label,
                color = if (activity.awaitingPermission) scheme.primary else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            if (live.isNotEmpty()) {
                Surface(
                    modifier = Modifier.padding(top = 3.dp, bottom = 3.dp),
                    shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
                    color = ZhiColors.terminalSurface(),
                    contentColor = scheme.onSurfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        text = live,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                    }
                }
            }
        }
        return
    }

    Surface(
        // margins(23, 4, 0, 4)
        modifier = Modifier.padding(start = 23.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
        color = ZhiColors.terminalSurface(),
        contentColor = scheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 7.dp, end = 10.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dotAlpha = if (activity.awaitingPermission) {
                    1f
                } else {
                    val breath = rememberInfiniteTransition(label = "runningDot")
                    val value by breath.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.45f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "runningDotAlpha",
                    )
                    value
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .background(
                            color = if (activity.awaitingPermission) scheme.onSurfaceVariant else scheme.primary,
                            // 7dp 的状态圆点：直径小于 10dp 时 G2 与圆弧在屏幕上无法分辨，
                            // 而每个 squircle 都要开一次离屏层。这里保持圆弧是有意的。
                            shape = RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    text = activity.displayName,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.dp),
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = if (activity.awaitingPermission) "等待授权…" else label,
                    color = if (activity.awaitingPermission) scheme.primary else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            // 命令行：未展开时截断（与完成态同一套 `truncateCommand`），
            // 这样"跑的是什么"在这块井里也看得见，不必先去点一下 ⌄。
            if (activity.command.isNotBlank()) {
                Text(
                    text = ToolText.truncateCommand(activity.command),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp, bottom = 7.dp),
                )
            }
            ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth())
            val body = if (live.isNotEmpty()) live
            else if (activity.awaitingPermission) "等待授权…"
            else "等待程序输出…"
            Text(
                text = body,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                // 实时区只按行数控制高度（见 `LiveOutput.preview`）
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .animateContentSize(ZhiMotion.sizeSpec),
            )
        }
    }
}

/** 这条工具的输出该不该按 diff 渲染。 */
private fun ToolActivity.isFileDiff(): Boolean = ToolText.isFileDiff(toolName, output)

/** 行首状态字形，复刻原版 `addToolCard()` 的 `statusGlyph` */
@Composable
private fun ToolStatusGlyph(activity: ToolActivity) {
    val scheme = MaterialTheme.colorScheme
    // 状态用 Miuix 图标 / 进度指示器表达，不再手写 ✓ × ○ ● 这些字符：
    // 字符字形依赖字体，等宽字体缺字时会显示成方块，而且颜色/粗细无法统一。
    Box(modifier = Modifier.width(23.dp), contentAlignment = Alignment.Center) {
        when {
            activity.completed && activity.failed -> Icon(
                painter = ZhiIcons.failed,
                contentDescription = "失败",
                tint = ZhiColors.red(),
                modifier = Modifier.size(13.dp),
            )
            activity.completed -> Icon(
                painter = ZhiIcons.done,
                contentDescription = "完成",
                tint = ZhiColors.green(),
                modifier = Modifier.size(13.dp),
            )
            activity.awaitingPermission -> Icon(
                painter = ZhiIcons.awaiting,
                contentDescription = "等待授权",
                tint = scheme.primary,
                modifier = Modifier.size(13.dp),
            )
            // 运行中：不确定态转圈，而不是一个静止的 ●（静止字符看不出"在进行"）
            else -> CircularProgressIndicator(modifier = Modifier.size(12.dp), color = scheme.primary, strokeWidth = 2.dp)
        }
    }
}

/** 运行中/等待授权的说明文字，对应原版 `runningToolLabel()`。 */
/** 运行中/等待授权的说明文字，对应原版 `runningToolLabel()`。 */
private fun runningToolLabel(activity: ToolActivity, nowMs: Long): String {
    if (activity.awaitingPermission) return "等待授权…"
    val display = ToolActions.displayElapsedMs(activity.elapsedMs, activity.startedAtMs, nowMs)
    val clock = if (display > 0) ToolText.formatElapsed(display) else ""
    return when {
        activity.kind != ToolKind.COMMAND ->
            if (clock.isEmpty()) "正在执行…" else "正在执行 $clock…"
        clock.isEmpty() -> "实时 · ${LiveOutput.volume(activity.stdoutChars, activity.stderrChars)} · 进程运行中"
        else -> "实时 $clock · ${LiveOutput.volume(activity.stdoutChars, activity.stderrChars)} · 进程运行中"
    }
}

/** 运行中要在行内摊出来的实时输出。 */
private fun liveOutputPreview(activity: ToolActivity): String {
    val text = activity.output
    if (text.isEmpty()) return ""
    return if (activity.kind == ToolKind.COMMAND) {
        LiveOutput.preview(text, maxChars = 7_000, maxLines = 7)
    } else {
        LiveOutput.tail(text, maxChars = 5_000)
    }
}

/** 运行中的刷新时钟：每 [periodMs] 返回一个新的「现在」。 */
@Composable
private fun rememberRunningClock(active: Boolean, periodMs: Long = 500L): Long {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(periodMs)
            // 与 `ToolActivity.startedAtMs` 同一个时钟：混用墙上时钟与单调时钟会让
            // "现在 - 起点"在改过系统时间之后变成一个无意义的差值。
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/** 工具行状态区（Crossfade 的目标态）：运行中 / 折叠摘要 / 展开输出 / 无内容。 */
private enum class ToolStatusRegion { RUNNING, COLLAPSED, EXPANDED, QUIET }

private fun toolStatusRegion(activity: ToolActivity): ToolStatusRegion = when {
    !activity.completed -> ToolStatusRegion.RUNNING
    activity.output.isBlank() && activity.previews.isEmpty() -> ToolStatusRegion.QUIET
    activity.expanded -> ToolStatusRegion.EXPANDED
    else -> ToolStatusRegion.COLLAPSED
}

/** 折叠态的紧凑结果摘要，复刻原版 `compactResult()`（`MainActivity.java:2764-2776`） */
private fun compactToolSummary(activity: ToolActivity): String {
    // 预览图**优先**说。
    if (activity.previews.isNotEmpty()) {
        return "${activity.previews.size} 张图 · 点按展开"
    }

    val changed = activity.additions + activity.deletions
    if (changed > 0) return "$changed 行已修改 · 点按展开"

    val text = activity.output
    var lo = 0
    var hi = text.length
    while (lo < hi && text[lo].isWhitespace()) lo++
    while (hi > lo && text[hi - 1].isWhitespace()) hi--
    if (lo >= hi) return "已完成"

    var lineCount = 1
    for (i in lo until hi) {
        if (text[i] == '\n') lineCount++
    }

    if (activity.kind == ToolKind.COMMAND) {
        val failedExit = activity.failed || (activity.exitCode != null && activity.exitCode != 0)
        if (failedExit) {
            val why = ErrorSummary.firstUsefulLine(text)
            val code = activity.exitCode ?: 1
            return buildString {
                append("退出码 ").append(code)
                if (why.isNotEmpty()) append(" · ").append(why.take(105))
                if (lineCount > 1) append(" · 点按展开")
            }
        }
        return "$lineCount 行 · 点按展开"
    }

    if (lineCount == 1 && hi - lo <= 96) return text.substring(lo, hi)
    return "$lineCount 行 · 点按展开"
}

/** 行内 diff 计数：原版是**纯文字着色**（9.5sp 等宽），没有底色胶囊。 */
@Composable
private fun DiffCount(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 5.dp),
    )
}

@Composable
fun ErrorCard(item: ChatItem) {
    ZhiNoticeBar(
        title = item.title.ifEmpty { "错误" },
        tone = ZhiNoticeTone.ERROR,
        modifier = Modifier.padding(vertical = 6.dp),
        // 正文用 Markdown 渲染，所以要自己给 slot 而不是让通知条画一行纯文本。
        // 见上面说明：错误正文里有 `代码` 与列表，纯文本会把它压平。
        content = {
            ZhiMarkdown(
                source = item.body,
                bodyFontSize = MiuixTheme.textStyles.body1.fontSize,
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    )
}

@Composable
fun InfoCard(item: ChatItem) {
    ZhiNoticeBar(
        title = item.title.ifEmpty { null },
        tone = ZhiNoticeTone.INFO,
        modifier = Modifier.padding(vertical = 4.dp),
        content = {
            // 提示正文也走 Markdown。
            ZhiMarkdown(
                source = item.body,
                bodyFontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(top = 3.dp),
            )
        },
    )
}




/** 把毫秒数格式化成"1.2s" / "3m 4s" 这种人读的短串。 */
private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)
    else -> {
        val totalSeconds = ms / 1000
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        "${m}m ${s}s"
    }
}
