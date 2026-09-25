package com.iqge.iqcode.compose.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.TaskState
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.ui.IqHorizontalDivider
import com.iqge.iqcode.compose.ui.IqMotion
import com.iqge.iqcode.compose.ui.IqIcons
import com.iqge.iqcode.compose.ui.IqUsageBar
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
 */
internal fun List<AgentTask>.currentWindow(max: Int): List<AgentTask> {
    if (max <= 0 || size <= max) return this
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
            cornerRadius = IqRadius.card,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = CardDefaults.defaultColors(
                color = IqColors.cardSurface(),
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
            cornerRadius = IqRadius.floating,
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            colors = CardDefaults.defaultColors(
                color = Color.Transparent,
                contentColor = scheme.onSurface,
            ),
            pressFeedbackType = PressFeedbackType.Sink,
            holdDownState = true,
        ) {
            CardBody(status = status, tasks = tasks, maxTasks = maxTasks)
        }
    }
}

@Composable
private fun CardBody(status: String, tasks: List<AgentTask>, maxTasks: Int) {
    val scheme = MiuixTheme.colorScheme
    val done = tasks.count { it.state == TaskState.DONE }
    val visibleTasks = remember(tasks, maxTasks) { tasks.currentWindow(maxTasks) }
    val progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "任务进度",
                color = scheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "$done / ${tasks.size}",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
            )
        }
        // 进度条平滑推进
        val animatedFraction by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(IqMotion.MEDIUM, easing = FastOutSlowInEasing),
            label = "agentProgress",
        )
        IqUsageBar(fraction = animatedFraction, modifier = Modifier.padding(top = 6.dp))

        visibleTasks.forEach { task ->
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskStatusIndicator(state = task.state)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = task.title,
                        color = if (task.state == TaskState.PENDING) scheme.onSurfaceVariantSummary else scheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = if (task.state == TaskState.RUNNING) FontWeight.Medium else FontWeight.Normal,
                    )
                    // 卡片里只显示详情首行，完整 Markdown 在详情窗口里看
                    val firstLine = task.detail.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    if (firstLine.isNotBlank()) {
                        Text(
                            text = firstLine,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = 10.sp,
                        )
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
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        IqHorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "计划模式",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = status,
                color = scheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
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
            imageVector = IqIcons.done,
            contentDescription = state.contentDescription(),
            tint = IqColors.green(),
            modifier = Modifier.size(size),
        )
        TaskState.RUNNING -> InfiniteProgressIndicator(size = size, color = scheme.primary)
        TaskState.PENDING -> Icon(
            imageVector = IqIcons.pending,
            contentDescription = state.contentDescription(),
            tint = scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(size),
        )
    }
}
