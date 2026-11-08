package com.zhizhu.zhicode.compose.ui.pages.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ExportSheetState
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 导出面板（底部抽屉）。
 *
 * ## 为什么是「列表」而不是「卡片」
 *
 * 前一版自己拼了三张圆角 `Card`，还塞了一个底部大按钮 —— 那是 Material3 的写法。
 * Miuix 的规范里，**列表就是操作面**：一行 = 一个动作，整行可点，分隔线用
 * [HorizontalDivider]，开关挂在行尾。设置页、选择器、权限面板全是这一套。
 * 混进自绘卡片会让这个抽屉看起来像另一个应用的弹窗。
 *
 * ## 为什么「包含」与「展开」是两个开关
 *
 * 前者决定导出内容里**有没有**思考块；后者只在图片里生效，决定它**画不画开**。
 * 合成一个的话，"带上但折叠"就没法表达 —— 而折叠往往才好看。
 */
@Composable
fun ExportSheetOverlay(
    sheet: ExportSheetState?,
    onDismiss: () -> Unit,
    onIncludeReasoningChange: (Boolean) -> Unit,
    onExpandReasoningChange: (Boolean) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportImage: () -> Unit,
    onSelectMore: () -> Unit,
) {
    // 退场期间仍需要一份内容，否则抽屉会先空成一条再滑走（同 CopySheetOverlay）。
    val shown = rememberLastNonNull(sheet) ?: return

    OverlayBottomSheet(
        show = sheet != null,
        title = "导出",
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            // 导的是哪几条：一行说明就够，多余的视觉重量会把它变成标题。
            Text(
                text = shown.title,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )

            // ---- 内容选项 ----
            BasicComponent(
                title = "包含思考过程",
                summary = "关闭后只导出正文与工具调用",
                endActions = {
                    Switch(
                        checked = shown.includeReasoning,
                        onCheckedChange = onIncludeReasoningChange,
                    )
                },
            )

            HorizontalDivider()

            // ---- 两个动作本身就是可点的行 ----
            BasicComponent(
                title = "导出为 Markdown",
                summary = "保存为 .md 文件，可编辑、可检索",
                onClick = onExportMarkdown,
            )

            HorizontalDivider()

            BasicComponent(
                title = "导出为图片",
                summary = "保存为 PNG 图片",
                onClick = onExportImage,
            )

            HorizontalDivider()

            // ---- 图片专用：把思考块画开 ----
            BasicComponent(
                title = "展开思考过程",
                summary = if (shown.includeReasoning) {
                    "在图片中展开显示思考内容"
                } else {
                    // 依赖关系必须写出来，否则用户会以为这个开关坏了。
                    "需要先打开「包含思考过程」"
                },
                enabled = shown.includeReasoning,
                endActions = {
                    Switch(
                        checked = shown.expandReasoning && shown.includeReasoning,
                        onCheckedChange = onExpandReasoningChange,
                        enabled = shown.includeReasoning,
                    )
                },
            )

            // ---- 单条来源时给一个扩大范围的入口 ----
            //
            // 从多选进来时**不显示**：那时已经在选了，再给一个入口只会让人
            // 以为刚才的选择丢了。
            if (shown.singleSource) {
                HorizontalDivider()
                BasicComponent(
                    title = "选择更多消息",
                    summary = "进入多选，勾选要一起导出的消息",
                    onClick = onSelectMore,
                )
            }
        }
    }
}
