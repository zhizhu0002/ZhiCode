package com.zhizhu.zhicode.compose.ui.chat

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiUsageBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 取最多 [max] 条「当前相关」的任务：
 * 跳过已完成的，从第一条未完成（通常是运行中）开始；若全部完成则回退到最后 [max] 条。
 * 条数受限后卡片高度可控，配合"悬浮卡 + 列表底部留白"避免遮挡对话。
 *
 * ⚠️ [max] **必须为正**。这里出过一次事故：有人用 `0` 表示"全部"，
 * 而当时的调用点又按"空"去渲染（`if (!compact)`），两边理解相反，
 * 结果悬浮任务卡既不画任务行、也算不出"还有 N 条"（因为 `size - size == 0`），
 * 界面上只剩「任务进度 3 / 7」和一根进度条 —— 用户看到的就是"任务怎么没显现出来"。
 * 现在 `max <= 0` 明确返回**空列表**：宁可让"还有 N 条 · 点按查看全部"兜住，
 * 也不要静默地把内容吞掉。
 */
internal fun List<AgentTask>.currentWindow(max: Int): List<AgentTask> {
    if (max <= 0) return emptyList()
    if (size <= max) return this
    val firstUnfinished = indexOfFirst { it.state != TaskState.DONE }
    if (firstUnfinished < 0) return takeLast(max)
    return drop(firstUnfinished).take(max)
}

/**
 * Agent 进度卡，对应原版 AgentProgressView：任务清单 + 计划状态 + 当前阶段三段。
 *
 * [tasks] 来自 state（不再写死在卡片里）；[onExpand] 是点卡片查看**全部**任务的入口。
 * [maxTasks] 限制同时展示的条数（悬浮形态下最多 2 条，避免卡片过高遮住对话），
 * 总进度仍由标题右侧的 `已完成 / 总数` 表达。
 */
@Composable
fun AgentProgressCard(
    status: String,
    tasks: List<AgentTask>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    maxTasks: Int = 2,
    /** 为真时表示外层已经有悬浮外壳（`FloatingToolbar`），本卡不再画自己的底板。 */
    embedded: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme

    // 嵌在悬浮外壳里时不再画自己的底板与外边距，
    // 否则一个圆角 20dp 的模糊面里再叠一个圆角 14dp 的实心卡，
    // 两层不同圆角会明显不齐。由调用方决定用哪种形态。
    if (!embedded) {
        Card(
            onClick = onExpand,
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
            cornerRadius = ZhiRadius.card,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = CardDefaults.defaultColors(
                color = ZhiColors.cardSurface(),
                contentColor = scheme.onSurface,
            ),
            pressFeedbackType = PressFeedbackType.Sink,
            holdDownState = true,
        ) {
            CardBody(status = status, tasks = tasks, maxTasks = maxTasks)
        }
    } else {
        // 悬浮形态：整块壳已经提供了圆角与背景，这里只铺内容。
        // 仍然套一层 Card 是为了保留点击与按压反馈，但底色透明、圆角与外壳一致，
        // 所以不会再出现第二层可见的圆角矩形。
        Card(
            onClick = onExpand,
            modifier = modifier.fillMaxWidth(),
            cornerRadius = ZhiRadius.floating,
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            colors = CardDefaults.defaultColors(
                color = Color.Transparent,
                contentColor = scheme.onSurface,
            ),
            pressFeedbackType = PressFeedbackType.Sink,
            holdDownState = true,
        ) {
            CardBody(status = status, tasks = tasks, maxTasks = maxTasks, compact = true)
        }
    }
}

@Composable
private fun CardBody(
    status: String,
    tasks: List<AgentTask>,
    maxTasks: Int,
    compact: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    val done = tasks.count { it.state == TaskState.DONE }
    // 悬浮形态同样要露任务行（只是条数少、每条一行）：它不是"只有总数"的进度条，
    // 而是"我现在做到哪一条了"。所以 compact 只改**条数**与**每条的详略**，不改"画不画"。
    val visibleTasks = remember(tasks, maxTasks) { tasks.currentWindow(maxTasks) }
    val progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size

        // 单行进度条铺在标题行下面（悬浮形态下卡片高度可控）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "任务进度",
                color = scheme.onSurface,
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "$done / ${tasks.size}",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
            )
        }
        // 进度条平滑推进
        val animatedFraction by animateFloatAsState(
            targetValue = progress,
            animationSpec = ZhiMotion.progressSpec,
            label = "agentProgress",
        )
        ZhiUsageBar(fraction = animatedFraction, modifier = Modifier.padding(top = 6.dp))

        visibleTasks.forEach { task ->
            Row(
                // 悬浮形态下行距收紧一档：同样露 2 条，紧凑形态少占约 8dp 的高度。
                modifier = Modifier.padding(top = if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskStatusIndicator(state = task.state)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = task.title,
                        color = if (task.state == TaskState.PENDING) scheme.onSurfaceVariantSummary else scheme.onSurface,
                        fontSize = ZhiTextScale.BodySmall,
                        fontWeight = if (task.state == TaskState.RUNNING) FontWeight.Medium else FontWeight.Normal,
                    )
                    // 悬浮形态不给详情：它是"贴一条注记"，不是详情面板（全量在任务清单窗口里）。
                    // 但**每条任务自己也要能看出状态**，所以状态指示器照画（上面那行）。
                    if (!compact) {
                        // 卡片里只显示详情首行，完整 Markdown 在详情窗口里看
                        val firstLine = task.detail.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                        if (firstLine.isNotBlank()) {
                            Text(
                                text = firstLine,
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Footnote,
                            )
                        }
                    }
                }
            }
        }

        // 还有被折叠掉的任务时提示一下，避免"以为只有这两条"
        val hidden = tasks.size - visibleTasks.size
        if (hidden > 0) {
            Text(
                text = "还有 $hidden 条 · 点按查看全部",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // 状态行为空时整段不画：否则会留下一个"计划模式"空标签 + 一条分隔线。
        // 悬浮卡现在只看 tasks 决定是否出现，所以 "有任务但此刻没有进行时状态"
        // （例如任务已全部完成）是正常组合，那时就该省掉这一行。
        if (status.isNotBlank()) {
            ZhiHorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "计划模式",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = status,
                    color = scheme.primary,
                    fontSize = ZhiTextScale.Caption,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
}

// ---------------------------------------------------------------- 任务状态指示器

internal fun TaskState.contentDescription(): String = when (this) {
    TaskState.DONE -> "已完成"
    TaskState.RUNNING -> "进行中"
    TaskState.PENDING -> "待开始"
}

/**
 * 任务状态指示器，任务卡与任务详情窗口共用。
 *
 * 状态不再用文字字形（`✓ ◐ ○`）表达：字形取决于字体是否有这些字符，
 * 缺字就是方块，而且拿不到主题色。"进行中"用 Miuix 不确定进度指示器，
 * 静止的字符本来也看不出"正在进行"。
 */
@Composable
internal fun TaskStatusIndicator(state: TaskState, size: Dp = 13.dp) {
    val scheme = MiuixTheme.colorScheme
    when (state) {
        TaskState.DONE -> Icon(
            imageVector = ZhiIcons.done,
            contentDescription = state.contentDescription(),
            tint = ZhiColors.green(),
            modifier = Modifier.size(size),
        )
        TaskState.RUNNING -> InfiniteProgressIndicator(size = size, color = scheme.primary)
        TaskState.PENDING -> Icon(
            imageVector = ZhiIcons.pending,
            contentDescription = state.contentDescription(),
            tint = scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(size),
        )
    }
}
