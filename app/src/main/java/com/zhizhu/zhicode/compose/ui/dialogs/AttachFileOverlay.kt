package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.FileHit
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.zhiFormatSize
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 「附加项目文件」面板。输入器 `+` 的第一项。
 *
 * 搜索框 + 结果列表，**选中即附加、面板不关**（说明文字就是"可多次附加到下一条消息"）。
 *
 * 结果列表由 ViewModel 在 IO 线程算好（见 `WorkspaceViewModel.refreshAttachHits`），
 * 这里只负责画：面板里现搜的话，每敲一个字符都要同步遍历目录，会明显卡顿。
 */
@Composable
fun AttachFileOverlay(
    open: Boolean,
    query: String,
    hits: List<FileHit>,
    onQueryChange: (String) -> Unit,
    onPick: (FileHit) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = open,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Regular,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val scheme = MiuixTheme.colorScheme
        DialogShell(
            title = "附加项目文件",
            titleAction = {
                IconButton(
                    onClick = onDismiss,
                    minHeight = 30.dp,
                    minWidth = 30.dp,
                    cornerRadius = 15.dp,
                ) {
                    Icon(
                        imageVector = ZhiIcons.close,
                        contentDescription = "关闭",
                        tint = scheme.onBackgroundVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            // 搜索框固定在**不滚动**的头部：结果列表很长时，输入框跟着滚上去
            // 就没法边看边改查询了。
            header = {
                ZhiTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = "搜索文件名或路径片段",
                    useLabelAsPlaceholder = true,
                    colors = TextFieldDefaults.textFieldColors(
                        labelColor = scheme.onSurfaceVariantSummary,
                    ),
                    insideMargin = DpSize(10.dp, 2.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            // 结果少时按内容自适应（fill=false 只是设上限，内容多仍会撑满并可滚动），
            // 不再无论几条都占满全屏高 —— 空态/少量结果时弹窗孤零零的很难看。
            fillBody = false,
            actions = {},
        ) {
            if (hits.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    Text(
                        text = if (query.isBlank()) {
                            "这个项目下没有可附加的文件"
                        } else {
                            "没有匹配「$query」的文件"
                        },
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.BodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                return@DialogShell
            }

            SmallTitle(text = "${hits.size} 个结果")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                hits.forEach { hit -> AttachHitRow(hit = hit, onPick = { onPick(hit) }) }
            }
        }
    }
}

@Composable
private fun AttachHitRow(hit: FileHit, onPick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurfaceContainerHigh,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                imageVector = ZhiIcons.file,
                contentDescription = null,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                // 文件名单独一行，路径放在下面：长路径截断时至少还能看见文件名
                Text(
                    text = hit.relative.substringAfterLast('/'),
                    fontSize = ZhiTextScale.BodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val dir = hit.relative.substringBeforeLast('/', "")
                if (dir.isNotEmpty()) {
                    Text(
                        text = dir,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = zhiFormatSize(hit.size),
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.height(14.dp),
            )
        }
    }
}
