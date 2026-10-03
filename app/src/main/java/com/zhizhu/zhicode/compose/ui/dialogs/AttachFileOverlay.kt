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
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalWindowInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 附加文件 sheet 的定高参数（含义与取值理由见 ModelPickerOverlay 的同名常量）。 */
private const val AttachSheetHeightFraction = 0.5f
private val AttachSheetHeightCap = 480.dp
private val AttachSheetHeightFloor = 280.dp

/**
 * 「附加项目文件」面板。输入器 `+` 的第一项。
 *
 * 搜索框 + 结果列表，**选中即附加、面板不关**（说明文字就是"可多次附加到下一条消息"）。
 *
 * 结果列表由 ViewModel 在 IO 线程算好（见 `WorkspaceViewModel.refreshAttachHits`），
 * 这里只负责画：面板里现搜的话，每敲一个字符都要同步遍历目录，会明显卡顿。
 *
 * ## 为什么是底部 Sheet 而不是居中弹窗
 *
 * 内容是"边输边挑"的可变长结果列表 —— 这正是居中弹窗最差的场景（完整理由见
 * `ModelPickerOverlay` 顶部注释）：竖屏可用高度小、挡住正在生成的回复、动画只有
 * fade。底部 sheet 自带上滑进场、下拉 / 点背板关闭。搜索框留在**不滚动**的头部
 * （原来就这条规则，sheet 里照样成立），结果区吃掉余量并内部滚动。
 *
 * 标题由 [OverlayBottomSheet] 自己渲染，**不再**套 `DialogShell`（两行标题 +
 * weight 语义对不上，见 ModelPickerOverlay 的说明）。
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
    // 退场时 `hits` 已经被 closeAttachPicker 清成空列表（而 `open` 也翻了 false），
    // 内容得靠「最后一次非空」兜住 —— 否则退场看到的是空的搜索面板。
    val shownHits = rememberLastNonNull(hits.ifEmpty { null })
    OverlayBottomSheet(
        show = open,
        onDismissRequest = onDismiss,
        title = "附加项目文件",
        // 其余参数一律用 Miuix 默认值（同 ModelPickerOverlay：backgroundColor 别动）。
    ) {
        // 原来这里有一句 `if (!open) return@OverlayBottomSheet`：它会在关窗那一瞬间
        // 把内容全拆掉，退场那 250~260ms 只是一张空壳在滑下去。
        val results = shownHits.orEmpty()
        val scheme = MiuixTheme.colorScheme
        // 定高（ModelPickerBody 同款写法）：结果从 0 条到几十条时面板高度不跳，
        // 搜索框位置稳定。⚠️ 窗口高度取 LocalWindowInfo，不能取 BoxWithConstraints。
        val windowHeight = LocalWindowInfo.current.containerDpSize.height
        val sheetHeight = (windowHeight * AttachSheetHeightFraction)
            .coerceAtMost(AttachSheetHeightCap)
            .coerceAtLeast(AttachSheetHeightFloor)
        Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            // 搜索框固定在**不滚动**的头部：结果列表很长时，输入框跟着滚上去
            // 就没法边看边改查询了。
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

            // 结果区吃掉余量并内部滚动；结果少时按内容自适应靠 Column 自身高度，
            // 定高面板下不会有大片空白（「完成」按钮都没有，底部就是列表末尾）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (results.isEmpty()) {
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
                    return@Column
                }

                SmallTitle(text = "${results.size} 个结果")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    results.forEach { hit -> AttachHitRow(hit = hit, onPick = { onPick(hit) }) }
                }
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
                painter = ZhiIcons.file,
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
