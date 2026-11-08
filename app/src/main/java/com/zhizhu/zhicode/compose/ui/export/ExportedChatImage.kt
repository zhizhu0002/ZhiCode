package com.zhizhu.zhicode.compose.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.theme.ZhiAppTheme
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 导出图的固定宽度。540dp 在手机上按 3x 出图约 1620px，足够清晰而不至于几 MB。 */
internal val ExportImageWidth = 540.dp

/**
 * 导出的对话图片。
 *
 * ## ⚠️ 为什么这里必须自己包一层 `ZhiAppTheme`
 *
 * 这段内容由 `BitmapComposer` 放进一个**独立的 `ComposeView`** 里渲染 ——
 * 那是一个**全新的 composition**，拿不到主界面那棵树上的任何 `CompositionLocal`
 * （`MiuixTheme`、`LocalThemeSettings`、`LocalZhiDark` 全都读不到）。
 *
 * 不自己包一层的话，所有 `MiuixTheme.colorScheme.*` 都会取到库的**默认值**：
 * 深色主题下导出的是一张白底黑字的图，与界面完全两回事。
 *
 * 所以调用点必须把当前 `ThemeSettings` 传进来（见 `ImageExportLauncher`），
 * 而不是指望"跟随系统"。
 *
 * ## 内容取舍
 *
 * - 只导出**正文**与工具摘要；
 * - **不**导出思考过程：它往往比正文长几倍，导出图高度会失控；
 * - 不画卡片阴影/玻璃/模糊：导出图没有背景可采样，那些效果只会变成灰块。
 */
@Composable
internal fun ExportedChatImage(
    items: List<ChatItem>,
    title: String,
    theme: ThemeSettings,
    includeReasoning: Boolean = true,
    expandReasoning: Boolean = false,
) {
    ZhiAppTheme(theme = theme) {
        val scheme = MiuixTheme.colorScheme
        Column(
            modifier = Modifier
                .width(ExportImageWidth)
                .background(scheme.surface)
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = scheme.onSurface,
                fontSize = MiuixTheme.textStyles.title2.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ZhiCode · ${items.size} 条",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    // 分隔线用极低对比的描边色：它只是"这里换了一条"的提示，
                    // 不该比正文更抢眼。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .background(scheme.outline.copy(alpha = 0.18f))
                            .padding(top = 0.5.dp),
                    ) {}
                }
                ExportMessage(
                    item = item,
                    includeReasoning = includeReasoning,
                    expandReasoning = expandReasoning,
                )
            }
        }
    }
}

@Composable
private fun ExportMessage(
    item: ChatItem,
    includeReasoning: Boolean,
    expandReasoning: Boolean,
) {
    val scheme = MiuixTheme.colorScheme
    val isUser = item.kind == ChatKind.USER
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isUser) "你" else "ZhiCode",
                color = if (isUser) scheme.primary else scheme.onSurface,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Bold,
            )
            if (item.kind == ChatKind.ERROR) {
                Text(
                    text = "  ·  出错",
                    color = scheme.error,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                )
            }
        }
        /*
         * 思考过程：按面板上的两个开关决定"画不画"与"展不展开"。
         *
         * 默认折叠成一行标记：思考常常比正文长好几倍，全画出来会让图片主体
         * 变成推理过程而不是回答本身。
         */
        if (includeReasoning && item.thinking.isNotBlank()) {
            val thinking = item.thinking.take(if (expandReasoning) 4000 else 0)
            Text(
                text = "思考过程" + if (!expandReasoning) "（已折叠）" else "",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (expandReasoning) {
                Text(
                    text = thinking,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (item.body.isNotBlank()) {
            // 正文用**与界面同一个渲染器**：导出图的排版必须和用户看到的一致，
            // 另写一套简化的 Markdown 渲染必然在表格/代码块上分叉。
            ZhiMarkdown(
                source = item.body,
                bodyFontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (item.tools.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                item.tools.forEach { tool ->
                    Text(
                        // 工具只列一行名字与摘要：它们的完整输出动辄几千行，
                        // 放进图片既看不清也没人看。
                        text = "· " + tool.displayName +
                            if (tool.summary.isNotBlank()) "  " + tool.summary else "",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        maxLines = 2,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}
