package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiRow
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 三个工作区面板共用的标题栏，对应原版 paneHeader()。
 *
 * ## 动作为什么是图标按钮而不是文字按钮
 *
 * 这里以前用 Miuix `TextButton` 配 `minHeight = 26.dp`，结果按钮被压扁、文字被裁。
 * 原因是 `ButtonDefaults` 写死 `MinWidth = 58dp` / `MinHeight = 40dp`
 * （从 0.9.4 字节码读出：`bipush 58` / `bipush 40`），
 * 强行传小值只会把内部布局压坏 —— 34dp 高的标题行根本容不下文字按钮。
 *
 * 所以动作改成 [ZhiIconButton] + `compact`：这是本项目里已验证能安全压到 30dp 的做法
 * （工具行就是 25dp），并且图标比中文两字标签更省横向空间。
 *
 * [onAction] 为空时**不显示按钮**：可点却什么都不发生比没有按钮更糟。
 *
 * ## 为什么还有一个 [actions] 槽
 *
 * 终端面板原来在这条头**下面**又叠了一条自己手写的工具栏（42dp，装着 ☰ / ⌨ / ⋮），
 * 于是切到终端时顶部有两行标题 —— 两个面板的头部长得不一样，这正是「每个面板一套头」
 * 的代价。收成一个头之后，终端多出来的那两个动作必须有地方放，所以这里开一个
 * **多动作**槽位（[actionIcon] 是单动作的老写法）。行内的顺序仍然是
 * 「标题 · 副标题 —— 弹性空白 —— 动作」。
 *
 * ## 现在谁还在用它
 *
 * - **终端面板**：标题 + 三个动作；
 * - **文件面板的查看/编辑态**：文件名 + `语言 · 只读/编辑中` + 编辑/保存/关闭
 *   —— 这几样都是**真信息**，不是重复。
 *
 * ⚠️ 文件面板的**列表态**本轮**不再用它**了：那里的 `title = "文件"` 与顶部
 * 标签栏正在高亮的那一项是同一件事，`subtitle = "N 项"` 也只是个可有可无的数字，
 * 而它下面还单独占了一行面包屑 —— 列表前叠了三层。现在列表态走
 * `FileChrome.kt` 的 [FilePathBar]（面包屑 + 三个动作合成一行）。
 */
@Composable
fun PaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: Painter? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
    /**
     * 行尾的多个动作。与 [actionIcon] 互斥使用（同时给会并排显示两个）。
     * 接收 `RowScope` 是为了让调用方能自己排间距。
     */
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ZhiRow.height).padding(start = ZhiSpace.m, end = ZhiSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = scheme.onBackground,
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 把动作按钮推到行尾
            Row(modifier = Modifier.weight(1f)) {}

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }

            if (actionIcon != null && onAction != null) {
                ZhiIconButton(
                    icon = actionIcon,
                    description = actionDescription ?: title,
                    onClick = onAction,
                    iconSize = 16.dp,
                    compact = 30.dp,
                )
            }
        }
        ZhiHorizontalDivider()
    }
}
