package com.zhizhu.zhicode.compose.ui.pages.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ExportSheetState
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 抽屉里小标题的内边距（与 ModelPickerOverlay 同款）。
 *
 * Miuix `SmallTitleDefaults.InsideMargin` 是 `PaddingValues(28.dp, 8.dp)`，
 * 而 sheet 自身已经有 24dp 横向内边距 —— 两者相加 52dp，标题会缩进得比卡片深一大截。
 * 官方 `BottomSheetSection` 示例在 sheet 里覆写成 16dp，这里跟随它。
 */
private val SheetSectionInsideMargin = PaddingValues(16.dp, 8.dp)

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

            /*
             * 两组内容各装进一张 Miuix `Card`，底色走 [ZhiSheetCardColors]。
             *
             * 原来这里是**裸行直接铺在抽屉背板上**：抽屉背板取 `background`，
             * 而它和卡片默认色在深色下同为 `#242424`（见 ZhiSheetCardColors 的说明），
             * 于是整块看起来就是一篇没有边界的文字，行与行之间只能靠分隔线区分。
             *
             * 现在按官方 `BottomSheetSection` 的做法：**同一组内容装进一张卡**，
             * 卡与卡之间留 12dp，卡内不再画分隔线（Miuix 的分组卡就是靠底色成组，
             * 组内行之间本来也不用线 —— 官方示例里的两个 `SwitchPreference`
             * 就是上下紧挨着、中间没有线的）。
             */
            SmallTitle(text = "内容", insideMargin = SheetSectionInsideMargin)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ZhiSheetCardColors(),
            ) {
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
            }

            // ---- 两个动作本身就是可点的行 ----
            SmallTitle(text = "导出为", insideMargin = SheetSectionInsideMargin)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ZhiSheetCardColors(),
            ) {
                BasicComponent(
                    title = "Markdown",
                    summary = "保存为 .md 文件，可编辑、可检索",
                    onClick = onExportMarkdown,
                )

                BasicComponent(
                    title = "图片",
                    summary = "保存为 PNG 图片",
                    onClick = onExportImage,
                )
            }

            // ---- 单条来源时给一个扩大范围的入口 ----
            //
            // 从多选进来时**不显示**：那时已经在选了，再给一个入口只会让人
            // 以为刚才的选择丢了。
            if (shown.singleSource) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = ZhiSheetCardColors(),
                ) {
                    BasicComponent(
                        title = "选择更多消息",
                        summary = "进入多选，勾选要一起导出的消息",
                        onClick = onSelectMore,
                    )
                }
            }
        }
    }
}
