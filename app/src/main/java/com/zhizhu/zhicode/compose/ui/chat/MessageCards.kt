package com.zhizhu.zhicode.compose.ui.chat

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.zhizhu.zhicode.compose.theme.ZhiRadius
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
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.panes.DiffLines
import com.zhizhu.zhicode.compose.ui.panes.OutputLines
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiImageRow
import com.zhizhu.zhicode.compose.ui.ZhiImageViewer
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiNoticeBar
import com.zhizhu.zhicode.compose.ui.ZhiNoticeTone
import top.yukonga.miuix.kmp.anim.SinOutEasing
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
 * 不再手写 `Box + clip + background + pressScale`。
 *
 * 用 Card 的地方**不要**再叠按压缩放修饰符，否则会和 Miuix 的按压反馈打架。
 */
private val BubbleMargin = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
private val MessageMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

/**
 * 用户气泡宽度占可用宽的比例（**上限**，不是固定值）。
 *
 * 用 `widthIn(max = …)` 而不是 `fillMaxWidth(0.86f)`：后者是**强制**占 86%，
 * 于是内容只有「1」这种短消息也会被撑成几乎整行宽的蓝条，看起来像一条色带
 * 而不是一个气泡。改成上限之后，短消息收成合适宽度、长消息仍然封顶。
 */
private val BubbleMaxWidthFraction = 0.86f
private val GroupMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
private val RowMargin = PaddingValues(horizontal = 9.dp, vertical = 7.dp)

/**
 * 单条工具那张卡的内边距。
 *
 * 比 [GroupMargin] 紧一档：单条卡里只有一行标题 + 最多一个输出块，而组卡还要装下
 * 表头与副行。两张卡在同一屏里挨着出现时，内边距差一档才看得出"这张装的是
 * 一条工具、那张装的是一组"。
 */
private val SingleMargin = PaddingValues(horizontal = 8.dp, vertical = 5.dp)

/** 空态，对应原版 addEmptyState()。 */
@Composable
fun EmptyState() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 82.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "想让智蛛做什么？",
            color = scheme.onBackground,
            fontSize = ZhiTextScale.Title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "智蛛可以读取项目、编辑文件、运行命令，并在内置 Termux 环境中验证修改。",
            color = scheme.onBackgroundVariant,
            fontSize = ZhiTextScale.BodySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
    }
}

/** 用户消息气泡，右对齐。长按触发操作菜单。 */
@Composable
fun UserBubble(item: ChatItem, onLongPress: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    // 放大查看的当前图。放在**气泡内部**而不是提到 AppScaffold：
    // 它是一个纯本地 UI 状态（点了哪张图），提升上去只会让上层多一个字段，
    // 而这个浮层本身是 OverlayDialog，画在主窗口里，不存在被气泡裁掉的问题。
    var viewing by remember { mutableStateOf<ChatImage?>(null) }

    // 需要可用宽度才能把气泡宽度表达成「上限 = 可用宽 × 比例」。
    // BoxWithConstraints 是 Compose 布局原语（skill 决策顺序第 4 条），
    // 比手写 onSizeChanged 更直接，也不会多一次重组。
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * BubbleMaxWidthFraction
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
        Card(
            // ⚠️ 必须是 onLongPress，**不要**写回 onClick。
            // 这里曾经是 `onClick = onLongPress`，于是短按一下就弹菜单，
            // 而函数文档与 AssistantCard 都是「长按」—— 同一段代码自相矛盾。
            // Miuix `Card` 内部用 combinedClickable 且 `isClickable = hasOnClick || hasLongPress`
            // （核过 v0.9.4 源码），所以只给 onLongPress 是被正确支持的。
            onLongPress = onLongPress,
            // 宽度 = 「内容自适应，上限 86%」：先按可用宽取 86% 作为**上限**，
            // 再让内容自己决定实际宽度。
            // 之前是 `fillMaxWidth(0.86f)` —— 那是**强制** 86%，于是像「1」这样的
            // 短消息也会撑成一条几乎整行宽的蓝条。见根部的 BoxWithConstraints。
            modifier = Modifier.widthIn(max = maxBubbleWidth),
            cornerRadius = 18.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.defaultColors(
                color = scheme.primary,
                contentColor = scheme.onPrimary,
            ),
            // ⚠️ 必须是 None，理由与 AssistantCard 完全相同：
            // 这张卡只有**长按**才有动作，而 Sink 是「按下即缩放」，
            // 短按也会看到整块气泡缩放一下、松手却没有反应 —— 像卡了点不动。
            pressFeedbackType = PressFeedbackType.None,
        ) {
            // 图片画在正文**上方**：一条「图 + 一句话」的消息，图是主体，
            // 说明文字在下面；反过来会让图看起来像附注。
            //
            // 空 body 时不要画那个空 Text：`Text("")` 仍会占一行行高，
            // 于是"只发图"的气泡下面会多出一条空隙。
            ZhiImageRow(images = item.images, onOpen = { viewing = it })
            if (item.body.isNotBlank()) {
                Text(
                    text = item.body,
                    fontSize = ZhiTextScale.Subheading,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        }
    }

    // 放大查看：浮层挂在气泡之外（OverlayDialog 画在主窗口里）。
    // 放在 BoxWithConstraints 之后而不是里面，是为了不让它参与气泡的宽度测量 ——
    // 否则 `maxWidth` 会把浮层也算进去，气泡宽度可能被它影响。
    ZhiImageViewer(image = viewing, onDismiss = { viewing = null })
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        // 透明底：不再是"一块卡片"，只剩长按入口 + 左侧竖线的身份标记
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.None,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(2.dp)
                .height(34.dp)
                .background(scheme.primary, RoundedCornerShape(1.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                // clip 在 animateContentSize **外侧**：思考面板展开/收起时文字
                // 按自然高度绘制、被逐帧露出来（详见 ToolGroupCard 的注释）。
                .clipToBounds()
                // ⚠️ 流式期间**不挂** animateContentSize。
                //
                // 正文每 32ms（DELTA_MERGE_MS）长高一次，而尺寸动画每次变化都会被重新
                // 触发 —— 结果是整张卡在整条回复期间一直在做"测量→布局→动画"，
                // 而它就在 LazyColumn 的一个 item 里。老设备上这就是"流式一顿一顿"
                // 最直接的来源。定稿后（streaming = false）再挂上：那时它只动一次，
                // 用来平滑"思考面板展开/收起"这类真实的一次性尺寸变化。
                .then(
                    if (item.streaming) {
                        Modifier
                    } else {
                        Modifier.animateContentSize(animationSpec = ZhiMotion.sizeSpec)
                    },
                ),
        ) {
            if (item.thinking.isNotEmpty() || item.processSteps.isNotEmpty()) {
                ThinkingPanel(item = item, onToggle = onToggleThinking)
            }
            // 正文走 Markdown（标题/列表/代码块/表格/引用/链接…）。
            // 流式光标用一个独立的 Text 尾随，而不是拼进 Markdown 源里 ——
            // 拼进去的话光标会被当成行内内容参与解析（例如紧跟在 ` 后面会变成代码）。
            //
            // `streaming` 传下去，Markdown 层据此只在**跨过块边界**时重解析前缀
            // （见 settledPrefixLength）：不传的话每 32ms 会重建整篇。
            ZhiMarkdown(
                source = item.body,
                bodyFontSize = 14.sp,
                streaming = item.streaming,
            )
            if (item.streaming) {
                // 流式光标呼吸闪烁：之前是一块静止的字符，文本区里唯一「活着」的
                // 记号却不动。「只有卡片在动」的观感有一半来自这里。
                // 周期 = 淡入 300ms 去程 + 300ms 回程；alpha 走 draw 层，不重组。
                val cursor = rememberInfiniteTransition(label = "stream-cursor")
                val cursorAlpha by cursor.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(ZhiMotion.FADE_IN_MILLIS, easing = SinOutEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "stream-cursor-alpha",
                )
                Text(
                    text = "▍",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.BodySmall,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .graphicsLayer { alpha = cursorAlpha },
                )
            }
            // 上下文页脚淡入：流式一结束它就出现，之前是瞬间蹦出来的。
            AnimatedVisibility(
                visible = !item.streaming && item.contextTokens >= 0,
                enter = fadeIn(ZhiMotion.fadeInSpec),
                exit = fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                ContextFooter(
                    tokens = item.contextTokens,
                    window = item.contextWindow,
                )
            }
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
        fontSize = ZhiTextScale.Micro,
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
                        fontSize = ZhiTextScale.Caption,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        // 展开/收起：高度由 AssistantCard 外层的 animateContentSize 平滑过渡，
        // 两份文字本身再用 Crossfade 淡变 —— 之前只有卡片高度在动，
        // 文字是瞬间蹦出来/消失的。
        Crossfade(
            targetState = item.thinkingExpanded,
            animationSpec = ZhiMotion.fadeOutSpec,
            label = "thinking-panel",
        ) { expanded ->
            if (expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (item.processSteps.isNotEmpty()) {
                        item.processSteps.forEach { step ->
                            Text(
                                text = "· $step",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Caption,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                            )
                        }
                    }
                    if (item.thinking.isNotEmpty()) {
                        Text(
                            text = item.thinking,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = ZhiTextScale.Caption,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        )
                    }
                }
            } else if (item.thinking.isNotEmpty()) {
                Text(
                    text = item.thinking,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * 一个**工具批次**在对话流里的样子。
 *
 * ## 一个批次不是一张卡片
 *
 * 之前这里是"每个批次套一张「已运行 N 个工具」卡片"，于是单独一条 `Bash` 也被包进
 * 一张**带标题、带子标签、带计数徽章**的大卡里 —— 那三点是与参考实现不一致的地方
 * （IQ Code 的组标题直接说干了什么，而且只有"连续的 read/search 且 ≥2"才有组）。
 *
 * 现在的分工：
 * - 一个批次先按 [ToolGrouping] 切成若干段；
 * - **单条工具**各自一张卡（视觉上仍是一个框），但**没有标题行、没有子标签、没有徽章**
 *   —— 卡片只负责"这一条工具自成一块"，不冒充整批的汇总；
 * - **连续的 read/search 且 ≥2** → 折成一张卡，标题是
 *   「正在搜索 2 个模式、读取 3 个文件」这种**说明干了什么**的话。
 */
@Composable
fun ToolBatch(
    item: ChatItem,
    onToggleTool: (String) -> Unit,
    /** 参数是**那一组**的 groupKey（首成员 toolId）。 */
    onToggleGroup: (String) -> Unit,
    /**
     * 某一行的 `⋯` 菜单里**选中了一项**。两个参数：那一行的 toolId + 菜单文案。
     *
     * 这里以前是 `onActions: () -> Unit`，来自整组那一层 —— 于是点单个工具的 `⋯`
     * 弹出的是整组菜单，而且回调里根本不知道用户点的是哪一行。
     * 单个工具的动作（复制命令 / 复制输出 / 展开这一条）都作用在某一行上，
     * 所以行号必须传出去。
     *
     * 菜单**本身**由这一行自己画（Miuix 下拉菜单，见 [ToolRow]），这里只负责
     * 把"选中了哪一行的哪一项"交给上层去执行。
     */
    onToolAction: (String, String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val segments = remember(item.tools) {
        ToolGrouping.group(item.tools.map { it.toGroupingEntry() })
    }

    /*
     * 运行中的秒表。
     *
     * `elapsedMs` 是跟着输出块推过来的（引擎按 chunk 回调进度），所以一个跑很久
     * 都不吐字的命令，标签会冻在最后一次进度的值上，看起来像卡死。
     * 这里按 500ms 续走一次（与参考实现的 `scheduleToolElapsedTicker` 同频），
     * 取「起点至今」与「引擎推送值」的**较大者**（见 `ToolActions.displayElapsedMs`，
     * 规则本身在纯逻辑层、有单测）。
     *
     * 整个批次共用同一个 `nowMs` 而不是每行各起一个 ticker：一次重组足够，而且同屏里
     * 各行的秒数不会因为 ticker 相位不同而看起来错开。
     *
     * **没有运行中的工具时 ticker 不排队** —— 否则一个后台死循环会一直持有重组。
     */
    val runningClock = rememberRunningClock(item.tools.any { !it.completed })

    // 段与段之间的间隔：单条卡与组卡各自带垂直留白，所以这里不再额外加 padding
    // （两边都加会让"两条命令之间"的缝比"命令与它的输出之间"还宽）。
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is ToolGrouping.Segment.Single -> {
                    val tool = item.tools.firstOrNull { it.id == segment.entry.toolId } ?: return@forEach
                    // ⚠️ 每一行都要 `key`，否则行内的 `remember`（展开态、下拉菜单的
                    // 展开态）是按**位置**归属的：工具是边跑边追加的，新工具插进来之后
                    // 位置会挪，于是"打开的菜单"和"展开的输出"会串到另一行上。
                    key(tool.id) {
                        // 单条工具**也给它一个框**（用户要求：「给调用工具加个框」）。
                        // 框里只有这一条工具自己 —— 没有批次标题、没有被批次计数冒充的
                        // 子标签、没有徽章。之前那张大卡的问题不在于"有框"，而在于框顶上
                        // 多了一行「已运行 N 个工具 / 修改 1 处代码」：那是**整批的汇总**，
                        // 挂在单独一条命令上面就成了假信息。
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            cornerRadius = ZhiRadius.card,
                            insideMargin = SingleMargin,
                            colors = CardDefaults.defaultColors(
                                color = ZhiColors.cardSurface(),
                                contentColor = scheme.onSurface,
                            ),
                        ) {
                            ToolRow(
                                activity = tool,
                                nowMs = runningClock,
                                onToggle = { onToggleTool(tool.id) },
                                // 传**这一行**的 id：菜单内容与动作都按它算。
                                onToolAction = { label -> onToolAction(tool.id, label) },
                            )
                        }
                    }
                }
                is ToolGrouping.Segment.Group -> {
                    val members = segment.members.mapNotNull { member ->
                        item.tools.firstOrNull { it.id == member.toolId }
                    }
                    if (members.isEmpty()) return@forEach
                    key(segment.key) {
                        ToolGroupCard(
                            members = members,
                            expanded = segment.key in item.expandedGroups,
                            onToggle = { onToggleGroup(segment.key) },
                            nowMs = runningClock,
                            onToggleTool = onToggleTool,
                            onRowAction = onToolAction,
                        )
                    }
                }
            }
        }
    }
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

/**
 * 折叠组卡片，对应原版 `addCollapsedToolActivity()` + `collapsedActivityLabel()`。
 *
 * 只由 [ToolBatch] 在"连续的 read/search 且 ≥2"时调用 —— 单条工具绝不走这里。
 */
@Composable
private fun ToolGroupCard(
    members: List<ToolActivity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    nowMs: Long,
    onToggleTool: (String) -> Unit,
    /** 传给每一行的回调（**带 toolId**，见 [ToolBatch] 的说明）。 */
    onRowAction: (String, String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            // 展开/收起的高度动画**只有这一处**，而裁剪层必须写在它**外侧**
            // （clipToBounds 在 animateContentSize 之前）。
            //
            // ⚠️ 之前这里写成 `if (item.groupCompleted) 无动画 else 动画` —— 反了。
            // 于是「点开一个已跑完的工具组」这个最常见的动作恰恰没有卡片动画：
            // 外框瞬间撑开，内层各自挂着自己的 animateContentSize 单独滑动，用户
            // 看到的就是「文字没跟着卡片的动画展开/缩回」。门控本意是"运行期间不挂"
            // （工具输出每 200ms（PROGRESS_FLUSH_MS）长一次会把尺寸动画反复重新
            // 触发，整张组卡持续重测量），所以跑完之后才该挂上。
            //
            // clip 在外的理由：裁剪层拿到的是**动画中的高度**，内层文字按自然高度
            // 绘制、被逐帧露出来。反过来写（动画在外）裁剪层拿到的是自然高度，
            // 一点也裁不到，文字仍然是瞬间全部出现。
            .clipToBounds()
            .then(
                // 门控取**这一组自己**是否跑完（不是整批）：一批里可能既有已读完的一组、
                // 又有还在跑的命令，用整批的状态会让跑完的那组也一直不挂动画。
                if (done) {
                    Modifier.animateContentSize(animationSpec = ZhiMotion.sizeSpec)
                } else {
                    Modifier
                },
            ),
        cornerRadius = ZhiRadius.card,
        insideMargin = GroupMargin,
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 行首是**状态字形**，与单条工具行同一个口径：有失败 → 红，跑完 → 绿，
            // 否则强调色（参考实现：`failed>0 ? RED : (done ? GREEN : ACCENT)`）。
            //
            // 这里原来显示的是「正在运行工具 / 已运行 N 个工具」加一个计数徽章 ——
            // 那是**计数**，回答了"有几个"，却没回答"在干什么"。参考实现的组标题
            // 直接说干了什么（「正在搜索 2 个模式、读取 3 个文件」），计数也就在里面了。
            Icon(
                imageVector = when {
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
            // 组标题同样是**会变的文字**（「正在读取 2 个文件」→「已读取 2 个文件」，
            // 计数与失败数也在涨），所以走 Crossfade 淡变 —— 只让卡片高度动、
            // 文字瞬间跳变正是被点名过的观感问题。
            Crossfade(
                targetState = ToolGrouping.label(group, batchDone = done),
                animationSpec = ZhiMotion.fadeOutSpec,
                label = "group-label",
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            ) { text ->
                Text(
                    text = text,
                    color = scheme.onSurface,
                    fontSize = ZhiTextScale.BodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 折叠箭头放右端：与单条工具行的 `⌄`/`⌃` 同一侧、同一含义，
            // 于是"点哪儿会展开"在这一屏里只有一种解释。
            Icon(
                imageVector = if (expanded) ZhiIcons.chevronUp else ZhiIcons.chevronDown,
                contentDescription = if (expanded) "收起这一组" else "展开这一组",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp),
            )
        }
        } // Surface(onClick) 表头
        Text(
            text = ToolGrouping.subtitle(group, expanded = expanded, batchDone = done),
            color = if (failed) ZhiColors.red() else scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 19.dp, top = 2.dp),
        )
        if (expanded) {
            // ⚠️ 这里**不再**挂 animateContentSize。卡片本身已经在动（上面那处），
            // 两层各挂一次的结果是：外框按动画高度走、内层按自己的动画滑动，
            // 两者曲线不同步 —— 看起来就是文字在卡片里"自己飘"。高度的单一来源
            // 是卡片，内层只负责按自然高度绘制、由卡片外层裁剪。
            Column(modifier = Modifier.fillMaxWidth()) {
                members.forEach { tool ->
                    key(tool.id) {
                        ToolRow(
                            activity = tool,
                            nowMs = nowMs,
                            onToggle = { onToggleTool(tool.id) },
                            onToolAction = { label -> onRowAction(tool.id, label) },
                        )
                    }
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
 * - 右端 `⋯` 打开操作菜单（**Miuix 下拉菜单**，与输入器底排同一组件），
 *   `⌄` / `⌃` 折叠展开（仅完成且有详情时出现）；
 * - Bash 的命令**不放标题行**（原版注释：否则命令会出现两次），改为独立下一行；
 * - 折叠态只显示 `⎿ 摘要`（等宽、缩进 23dp），展开态才渲染全量输出。
 */
@Composable
private fun ToolRow(
    activity: ToolActivity,
    nowMs: Long,
    onToggle: () -> Unit,
    /** 这一行的菜单里选中了一项（参数是菜单文案）。 */
    onToolAction: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val isCommand = activity.kind == ToolKind.COMMAND
    val hasDetails = activity.output.isNotBlank()
    val showChevron = activity.completed && hasDetails

    /*
     * 这两个都是 O(输出长度) 的扫描（展开输出上限 40 000 字符，见
     * `WorkspaceViewModel.LIVE_OUTPUT_LIMIT`），而这一行每次重组都要用它们 ——
     * 状态切换、滚动复用、展开/收起都会触发。所以先算一次并缓存。
     *
     * ⚠️ key **不能**用 `activity` 整体：它带 `elapsedMs`，运行中每秒都在变，
     * 那样等于每秒白算一次。key 只取真正参与计算的那几个字段。
     *
     * 输出字符串没变时 `remember` 的相等判断走的是同一实例的 `String.equals`
     * 快路径（O(1)）；真变了才付一次 memcmp —— 都比重新 split 一遍便宜。
     */
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

    // 这一行菜单的判据。构造规则只在 `ToolActions.flags` 一处实现（VM 分派动作时用的是
    // 同一份），所以这里**不算**"有没有 diff"这类结论，只把"输出是不是 diff"的结论喂进去
    // —— 那个结论来自 `ToolText.isFileDiff`（见上面的 isFileDiff）。
    val menuFlags = ToolActions.flags(
        kind = activity.kind,
        summary = activity.summary,
        output = activity.output,
        isFileDiff = isFileDiff,
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
            // 运行中不挂尺寸动画：这段时间里状态行每秒都在换文字（elapsed），
            // 而「运行中… → ⎿ 摘要 → 全量输出」的切换已经由 Crossfade 负责过渡。
            // clip 在动画**外侧**：展开输出时文字按自然高度绘制、被逐帧露出，
            // 而不是整段先冒出来再等卡片长高。
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
                fontSize = ZhiTextScale.Caption,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 命令类工具的命令单独成行，这里留空占位（原版行为）
            if (!isCommand && activity.summary.isNotEmpty()) {
                Text(
                    text = "  " + activity.summary,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
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

            // ⋯ 的菜单内容只由 `ToolActions` 决定（判据的构造也在那里，界面与 VM 共用）：
            // 这样"哪个状态该有哪些动作"只有一份实现，界面这边只负责把它渲染出来。
            //
            // ⚠️ `items` 必须 `remember`：`ZhiIconDropdownMenu` 内部按 `remember(items, …)`
            // 缓存整份 `DropdownEntry`，而写在参数位置上的 `map { … }` 每次重组都是新的
            // List 实例 —— 身份不等，那份缓存永远命不中，等于没做。
            // key 取旗标与回调：旗标是 data class（按值比较），回调在 ToolGroupCard 里
            // 捕获的是稳定值，两者跨重组都能命中。
            val menuItems = remember(menuFlags, onToolAction) {
                ToolActions.options(menuFlags).map { label ->
                    ZhiMenuItem(text = label, onClick = { onToolAction(label) })
                }
            }

            // ⋯ 与 ⌄/⌃ 都转发到 Miuix IconButton。
            //
            // `⋯` 这一个走 **`ZhiIconDropdownMenu`**（= Miuix `OverlayIconDropdownMenu`），
            // 与输入器底排的 `+`、权限、推理**同一个组件**：触发按钮自己持有展开态、
            // 按下时进入 hold-down 态、面板由 Miuix 贴着按钮弹出。
            //
            // ⚠️ 以前这里是一个普通 `ZhiIconButton` + VM 里的一份 `choicePicker` 状态，
            // 于是点它弹出来的是**屏幕中央的对话框**（`ChoiceIntent.TOOL_ACTION` 不在
            // `isActionMenu` 的名单里，选择器被交给居中的 `ChoicePickerOverlay`）——
            // 动作明明只作用于这一行，弹窗却出现在屏幕中央，位置与语义对不上。
            // 现在展开态就在这一行自己的组合里，不需要任何坐标换算，也不会锚偏。
            ZhiIconDropdownMenu(
                items = menuItems,
                minHeight = 25.dp,
                minWidth = 25.dp,
                cornerRadius = 12.5.dp,
                // 透明底：底色是工具组那张 Card，触发按钮不该再画一层
                backgroundColor = Color.Transparent,
            ) {
                Icon(
                    imageVector = ZhiIcons.more,
                    contentDescription = "工具操作",
                    tint = scheme.onSurfaceVariantSummary,
                    // ⚠️ 转 90° 才是**横排**的 `⋯`。
                    //
                    // Miuix 的 `More` 图标是竖排三点（三个点的 x 坐标完全相同，见
                    // `miuix-icons/.../extended/More.kt`），而参考实现那一行用的是横排
                    // 省略号 —— 它在标题行右端、旁边紧挨着 `⌄`，竖排三点在视觉上会和
                    // 那个折叠箭头撞在一起。
                    //
                    // 用 `rotate` 而不是换成 `Text("⋯")`：字符字形依赖字体，
                    // 等宽字体缺字时会显示成方块（这也是本工程放弃手写 ✓/×/○/● 的原因）。
                    modifier = Modifier.size(15.dp).rotate(90f),
                )
            }

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

        // Bash 命令：等宽、缩进，与图片里「命令在名称下方」一致。
        //
        // ⚠️ 展开态必须显示**完整**命令（`activity.command`），折叠态才用 `summary`
        // （那是 `truncateCommand` + `shorten(190)` 的结果，只保证标题行不撑破）。
        // 参考实现同样是 `item.expanded ? command : truncateCommand(command)` ——
        // 一条 `&&` 串起来的多行脚本被截成前两行之后，用户没法核对它到底跑了什么。
        if (isCommand && commandLine.isNotEmpty()) {
            Text(
                text = "  " + commandLine,
                color = if (activity.expanded) scheme.onSurface else scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                fontFamily = FontFamily.Monospace,
                // 展开时**不限制行数**：完整命令是用户主动要求看的，再截就等于没展开。
                maxLines = if (activity.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 23.dp),
            )
        }

        // 状态区切换（运行中 → 折叠摘要 → 展开输出）整块 Crossfade 淡变：
        // 高度仍由外层 Column 的 animateContentSize 管，文字不再瞬间跳变。
        // 目标态收敛成枚举：运行中 elapsedMs 一直在变，但 region 不变，
        // Crossfade 就不会被打断重放。
        Crossfade(
            targetState = toolStatusRegion(activity),
            animationSpec = ZhiMotion.fadeOutSpec,
            label = "tool-status",
        ) { region ->
            when (region) {
                // 运行中：一行标签 + **实时输出**。
                //
                // 对齐参考实现（`MainActivity.addToolCard` 的 `else if (!item.completed)` 分支）：
                // 只显示一个"运行中 · 00:12"是不够的 —— 一条跑两分钟都不吐字的命令与
                // 一条正在刷日志的命令在界面上长得一模一样，用户没法判断它在干什么。
                // 所以这里把最新几行实时摊出来（Bash 取尾部 7 行，其他工具取尾部一段字符），
                // 并在还没有任何输出时明确写一句"等待程序输出…"，而不是留一片空白。
                ToolStatusRegion.RUNNING -> Column(modifier = Modifier.padding(start = 23.dp, top = 2.dp)) {
                    Text(
                        text = runningToolLabel(activity, nowMs),
                        color = if (activity.awaitingPermission) scheme.primary else scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        fontFamily = FontFamily.Monospace,
                    )
                    val live = remember(activity.kind, activity.output) { liveOutputPreview(activity) }
                    if (live.isNotEmpty()) {
                        Card(
                            modifier = Modifier.padding(top = 3.dp, bottom = 3.dp),
                            cornerRadius = ZhiRadius.inner,
                            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = CardDefaults.defaultColors(
                                color = ZhiColors.cardInnerSurface(),
                                contentColor = scheme.onSurfaceVariantSummary,
                            ),
                        ) {
                            Text(
                                text = live,
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Micro,
                                fontFamily = FontFamily.Monospace,
                                // 实时区**不换行裁剪**，只按行数控制高度（见 liveOutputPreview）：
                                // 一行很长的编译命令折成三行会把工具行顶得很高，而这几行的
                                // 用途只是"看见它在动"。
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (isCommand && !activity.awaitingPermission) {
                        Text(
                            text = "等待程序输出…",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = ZhiTextScale.Micro,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                ToolStatusRegion.COLLAPSED -> Text(
                    text = "  ⎿  " + collapsedSummary,
                    color = if (activity.failed) ZhiColors.red() else scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 23.dp, bottom = 2.dp),
                )
                // 展开：全量输出。
                //
                // 写文件类工具（Write / Edit / MultiEdit / Delete）的输出是**统一 diff**，
                // 逐行着色渲染：+绿 / −红 / @@ 用强调色 / 文件头弱化。与「变更」面板共用
                // `DiffLines`（`ui/panes/ChangesPane.kt`）—— 着色规则只有那一份，
                // 否则同一份 diff 在对话里与变更面板里会长得不一样。
                //
                // 左右不留给外层 Card：着色条要顶到卡片两边（像 diff 该有的样子），
                // 所以 insideMargin 只给上下；横向留白由每一行自己出（见 DiffLines）。
                ToolStatusRegion.EXPANDED -> Card(
                    modifier = Modifier.padding(start = 23.dp, top = 5.dp),
                    cornerRadius = ZhiRadius.inner,
                    insideMargin = if (isFileDiff) PaddingValues(vertical = 6.dp) else PaddingValues(8.dp),
                    colors = CardDefaults.defaultColors(
                        color = ZhiColors.cardInnerSurface(),
                        contentColor = scheme.onSurfaceVariantSummary,
                    ),
                ) {
                    if (isFileDiff) {
                        DiffLines(activity.output)
                    } else {
                        // OutputLines 与 DiffLines 共用同一套「最多渲染 300 行 + 点按显示全部」
                        // 的上限（见 ChangesPane.kt 的 MaxRenderedLines）：展开的输出最多
                        // 40 000 字符，整段当一个 Text 放在单个 LazyColumn item 里，
                        // 那个 item 会比视口还高，懒加载复用彻底失效。
                        OutputLines(activity.output)
                    }
                }
                ToolStatusRegion.QUIET -> Unit
            }
        }
    }
    } // Surface(onClick)
}

/**
 * 这条工具的输出该不该按 diff 渲染。
 *
 * <p>判据在 [ToolText.isFileDiff]（纯函数、有单测）：既要工具是写文件的，
 * **也要**输出真的是 diff —— 写入失败时输出是一行错误文本，
 * 按工具名着色会把那行错误画成 diff 配色，比不着色更误导。
 */
private fun ToolActivity.isFileDiff(): Boolean = ToolText.isFileDiff(toolName, output)

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
/**
 * 运行中/等待授权的说明文字，对应原版 `runningToolLabel()`。
 *
 * `nowMs` 是界面侧时钟（见 [rememberRunningClock]）。耗时取「起点至今」与「引擎推送值」
 * 的较大者 —— 引擎那个值只在有输出时更新，光用它会让长时间不吐字的命令看起来卡死。
 * 取值规则在 [ToolActions.displayElapsedMs]（纯逻辑、有单测）。
 *
 * 命令类工具额外报出**输出体量**（`实时 00:12 · 标准输出 12.3 KB · 错误输出 0 B · 进程运行中`）：
 * 字符数是单调递增的，即使屏幕上那几行没变，这一项也在动 —— 它同时回答了
 * "到底有没有在产出"和"错误输出是不是在涨"。
 */
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

/**
 * 运行中要在行内摊出来的实时输出。
 *
 * 两种取法对应参考实现的两个函数（`MainActivity.liveOutputPreview` / `liveOutputTail`）：
 * - **命令类**（Bash / Root）按"最后 7 行、最多 7000 字符"取 —— 命令的输出是**行**结构，
 *   按行取才看得出跑到哪一步了；
 * - **其他工具**按"尾部 5000 字符"取 —— 它们多数只吐一段文本（读到的片段、搜索结果），
 *   没有"行"的概念，硬按行裁会把内容切碎。
 *
 * 参考实现在宽屏下按 10 行取。这里固定 7 行：`wide` 要一路从 `ChatArea` 穿过
 * `ChatList` / `ToolGroupCard` / `ToolRow` 四层，而差别只是横屏多三行 ——
 * 横向空间的价值在别处，不值得为它铺一条参数通道。
 *
 * 裁剪与"省略提示"都在 [LiveOutput]（纯逻辑、有单测）。
 */
private fun liveOutputPreview(activity: ToolActivity): String {
    val text = activity.output
    if (text.isEmpty()) return ""
    return if (activity.kind == ToolKind.COMMAND) {
        LiveOutput.preview(text, maxChars = 7_000, maxLines = 7)
    } else {
        LiveOutput.tail(text, maxChars = 5_000)
    }
}

/**
 * 运行中的刷新时钟：每 [periodMs] 返回一个新的「现在」。
 *
 * [active] 为 false 时**不排队下一次** —— 参考实现也是这么做的
 * （`refreshToolElapsed` 只有在仍存在未完成工具时才重新 post），
 * 否则一个常驻的定时器会一直触发重组。
 */
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

/**
 * 工具行状态区（Crossfade 的目标态）：运行中 / 折叠摘要 / 展开输出 / 无内容。
 *
 * 收敛成枚举而不是拿布尔组合当 key：运行中的 `elapsedMs` 每秒都在变，
 * 但只要 completed/expanded 没翻转，region 就不变 —— Crossfade 不会被打断重放。
 */
private enum class ToolStatusRegion { RUNNING, COLLAPSED, EXPANDED, QUIET }

private fun toolStatusRegion(activity: ToolActivity): ToolStatusRegion = when {
    !activity.completed -> ToolStatusRegion.RUNNING
    activity.output.isBlank() -> ToolStatusRegion.QUIET
    activity.expanded -> ToolStatusRegion.EXPANDED
    else -> ToolStatusRegion.COLLAPSED
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

    // ⚠️ 这里曾经是 `val clean = activity.output.trim()` 再 `clean.split('\n').size`。
    //
    // 两步都在**每次重组**时按输出全长的代价做一次：trim 会复制整份文本（上限
    // 40 000 字符），split 会再建出所有行的数组 —— 而结果只有三个数：
    // 去掉首尾空白后的边界、行数、以及"单行且不超 96 字符"时的那段文本。
    // 改成按下标算：`trim()` 的边界是一个双向扫描，行数数的是区间里的换行符，
    // 那段文本只在真要显示时才 substring（一行，很短）。
    //
    // 语义与原来逐字一致，包括：区间内部的空行**计入**行数
    // （`"a\n\nb".trim().split('\n').size == 3`）。
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
        fontSize = ZhiTextScale.Micro,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 5.dp),
    )
}

/**
 * 错误卡片。
 *
 * ## 外观与沙箱页的后端错误同款
 *
 * 原来是「左边一根 3dp 红竖条 + 红标题 + 正文」，而沙箱页那处是「深灰卡片配红字」，
 * 输入器上方那处又是「带投影的浮动工具栏」—— 同一件"出错了"三种长相。
 * 现在三处都走 [ZhiNoticeBar] 的通知条（整宽红底、无阴影、小圆角）。
 *
 * 正文仍然走 [ZhiMarkdown]：`ToolText.friendlyError` 会输出带 `代码` 与列表的
 * 可操作建议，与回复正文保持一致 —— 这一条是功能，不因为换外观而丢。
 */
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
                bodyFontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    )
}

/**
 * 提示卡片（斜杠命令输出、计划审批结果等）。
 *
 * 与 [ErrorCard] 同一个通知条分量，只有配色不同（琥珀 = [ZhiNoticeTone.WARN]）：
 * 它和错误是同一层级的信息块，形状本就该一致。
 */
@Composable
fun InfoCard(item: ChatItem) {
    ZhiNoticeBar(
        title = item.title.ifEmpty { null },
        tone = ZhiNoticeTone.WARN,
        modifier = Modifier.padding(vertical = 4.dp),
        content = {
            // 提示正文也走 Markdown。这些内容里大量使用 `反引号` 标记命令与参数，
            // 原先那个"整段变等宽"的启发式太粗（一句里只要有反引号，全段都成等宽），
            // 现在由行内解析只给反引号包住的部分加等宽 + 底色。
            ZhiMarkdown(
                source = item.body,
                bodyFontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        },
    )
}


// 这里曾经有一个私有的 `formatElapsed`，输出的是 `7.0s` / `1:04.2` —— 现在已经删掉：
// 参考实现（IQ Code `MainActivity.formatElapsed`）是 `01:04`（分:秒，≥1 小时才带小时位），
// 而 `ToolText.formatElapsed` 早就是那一份了，只是没人调用它（于是两版截图对不上秒数格式）。
// 耗时显示统一走 `ToolText.formatElapsed`，不再留第二份实现。

