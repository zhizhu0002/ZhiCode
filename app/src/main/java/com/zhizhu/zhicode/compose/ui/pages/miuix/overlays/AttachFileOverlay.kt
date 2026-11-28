package com.zhizhu.zhicode.compose.ui.pages.miuix.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AttachBrowserState
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.ui.panes.FileListRow
import com.zhizhu.zhicode.compose.ui.panes.FilePathBar
import com.zhizhu.zhicode.compose.ui.panes.FileRootSwitcher
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
import com.zhizhu.zhicode.compose.ui.floatingNavSheetInset
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val AttachSheetHeightCap = 560.dp
private val AttachSheetHeightFloor = 320.dp

@Composable
fun AttachFileOverlay(
    open: Boolean,
    browser: AttachBrowserState,
    onFilterChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchRoot: (FileRoot) -> Unit,
    onPick: (FileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // 退场时 `browser` 已经被 closeAttachPicker 清成空（而 `open` 也翻了 false），
    // 只在 open 期间缓存最后一次非空 visibleEntries；打开时即便是空目录/无匹配也要如实展示空态。
    val shownVisible = rememberLastNonNull(if (open) browser.visibleEntries else null)
    OverlayBottomSheet(
        show = open,
        onDismissRequest = onDismiss,
        title = "附加项目文件",
        sheetMaxWidth = 720.dp,
        // 官方默认 insideMargin 为左右各 24dp；与共用文件行自身边距叠加后可读宽度过窄。
        // 由内容行自行控制留白，sheet 外壳不再额外吃掉可用宽度。
        outsideMargin = DpSize(0.dp, 0.dp),
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        // 原来这里有一句 `if (!open) return@OverlayBottomSheet`：它会在关窗那一瞬间
        // 把内容全拆掉，退场那 250~260ms 只是一张空壳在滑下去。
        val visible = if (open) browser.visibleEntries else shownVisible.orEmpty()
        val scheme = MiuixTheme.colorScheme
        /* 底部为悬浮导航胶囊让位。 */
        val navInset = floatingNavSheetInset()
        // 过滤框是否已展开。`InputField` 的必填参数，且承担 API ≤ 27 上的
        // 「先展开再聚焦」兼容职责（见下面那段注释），所以必须是真的状态。
        var filterExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 内容自适应：少时贴内容、多时才到上限。见两个常量的注释
                // （原先这里是按窗口比例算出来的**定高**）。
                // 上限要减掉悬浮胶囊的让位，否则面板会顶出屏幕。
                .heightIn(
                    min = AttachSheetHeightFloor,
                    max = (AttachSheetHeightCap - navInset).coerceAtLeast(AttachSheetHeightFloor),
                )
                // 底部再补同样多的留白：列表最后一行不能缩在胶囊后面。
                .padding(bottom = navInset),
        ) {
            // 1) 根切换条（与文件面板共用）。换根是这一屏最常用的动作之一，
            //    所以放在最上面一行，而不是藏进菜单。
            FileRootSwitcher(
                selected = browser.root,
                onSelect = onSwitchRoot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 2.dp),
            )

            // 2) 面包屑。点击面包屑中的任一级即可返回该目录，不再额外显示上一级图标。
            FilePathBar(
                filePath = browser.path,
                onNavigate = onNavigate,
            )

            // 3) 目录内过滤（不滚动头部）。Miuix `InputField` = 胶囊搜索框，
            //    与侧栏「搜索会话」同一形态。
            //
            // ⚠️ 横向内边距**必须**给：不给的话它会满幅横过去，比同屏的面包屑行、
            //    根标签栏和文件行都宽（用户截图里最刺眼的就是这一处）。
            //
            // ⚠️ `expanded` 必须接**真实状态**，不能传常量：API ≤ 27（本工程 minSdk 24）
            //    上 `hasFocusReassignBug` 为 true，未展开时组件是 **disabled** 的，
            //    靠 `onExpandedChange(true)` 先展开再聚焦。传常量的话那个回调是空的，
            //    Android 8.x 上这个框永远点不进去。（与 `Sidebar.kt` 同一处理由。）
            //
            // ⚠️ 代价说清：`InputField` 的 `label` 只在「未展开且为空」时当占位符显示
            //    （源码 `labelText = if (!(query.isNotEmpty() || expanded)) label else ""`），
            //    所以**第一次点开之后**占位文字就让位给放大镜图标。侧栏本来就是这个行为，
            //    两处一致 —— 要的是"同一个搜索框"，不是"每处一套占位符规则"。
            InputField(
                query = browser.filter,
                onQueryChange = onFilterChange,
                onSearch = { },
                expanded = filterExpanded,
                onExpandedChange = { filterExpanded = it },
                label = "在本目录里过滤",
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m),
            )

            // 4) 一层子项。空时把**原因**说出来，别让面板空着让人以为坏了。
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                insideMargin = PaddingValues(0.dp),
                colors = ZhiSheetCardColors(),
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ZhiSpace.m),
                    verticalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
                ) {
                    items(
                        items = visible,
                        key = { it.path },
                        contentType = { if (it.directory) "directory" else "file" },
                    ) { entry ->
                        FileListRow(
                            entry = entry,
                            // ⚠️ 点目录是**进去**，点文件才是附加。
                            //    这一条如果反了，用户会发现自己"附加"了一整个目录 ——
                            //    而目录根本读不出文本，只会得到一句"不是文本文件"。
                            onOpen = { if (entry.directory) onNavigate(entry.path) else onPick(entry) },
                            modifier = Modifier.animateItem(),
                            // 尾部不给「重命名/删除」：这里是挑选附件的场景，
                            // 摆两个破坏性动作既没用又危险。
                        )
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                text = emptyMessage(
                                    filter = browser.filter,
                                    note = browser.note,
                                    whileClosing = !open && shownVisible != null,
                                ),
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                            )
                        }
                    }
                    item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
                }
            }
        }
    }
}

private fun emptyMessage(filter: String, note: String, whileClosing: Boolean): String = when {
    filter.isNotBlank() -> "这个目录里没有匹配「$filter」的文件"
    note.isNotEmpty() -> note
    whileClosing -> ""
    else -> "这个目录下没有可附加的文件"
}
