package com.zhizhu.zhicode.compose.ui.chat

/**
 * **工具输出**的行数上限与渲染（工具卡片专用）。
 *
 * ## 为什么单独一个文件
 *
 * 这两件事原先和「变更」面板挤在同一个文件里（`ui/panes/ChangesPane.kt`）：
 * 面板是**死代码**（`WorkspaceTab` 早已只有 对话/终端/文件，`ChangesPane()` 没有任何调用方），
 * 而下面这些还**活着** —— `MessageCards` 的两处在用：
 *
 *  · [DiffLines]：工具卡里展开的 diff（`MessageCards` 的 ToolRow）
 *  · [OutputLines]：工具卡里展开的原始输出（同上）
 *
 * 混在一个文件里的后果很具体：面板删掉时这半活代码**差点被一起删掉**，
 * 而且没人看得清它还活着（文件名、注释、测试常量全都指着"变更面板"）。
 * 所以把它搬进 `ui/chat` —— 它服务的是工具卡，住在 `panes` 下本来就是历史错位。
 *
 * ⚠️ `MaxRenderedLines` / [limitLines] 的上限是**故意共用**的：diff 与原始输出走同一条
 * "最多渲染 300 行 + 点按显示全部"，两处各定一个数字迟早对不上。
 * `LimitLinesTest` 与 `ToolOutputBoundTest` 钉着这条。
 */

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
