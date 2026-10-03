package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AttachBrowserState
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.panes.FileBreadcrumbBar
import com.zhizhu.zhicode.compose.ui.panes.FileListRow
import com.zhizhu.zhicode.compose.ui.panes.FileRootSwitcher
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 附加文件 sheet 的定高参数（含义与取值理由见 ModelPickerOverlay 的同名常量）。 */
private const val AttachSheetHeightFraction = 0.5f
private val AttachSheetHeightCap = 480.dp
private val AttachSheetHeightFloor = 280.dp

/**
 * 「附加项目文件」面板。输入器 `+` 的第一项。
 *
 * ## 它是一台**浏览器**，不是一份搜索结果
 *
 * 这一屏原先长成「搜索框 + 递归搜索出来的扁平结果」。实测的失效很难看：
 * 搜索从 HOME 起递归，而 HOME 下有 Termux 的 `storage/{pictures,dcim,…}` 软链，
 * 空查询的深度上限刚好够到 `storage/pictures/` —— 于是**面板一打开、用户一个字都没敲**，
 * 列出来的是整屏设备截图；而且 BFS 浅层优先，那些照片还排在真正的项目代码前面。
 *
 * 现在改成**浏览当前目录**：一层一层走（`FileBrowser.children`），永远不递归，
 * 而且**共用文件面板那一套外壳**（根切换条 / 面包屑 / 行 / 尺寸文案）——
 * 两边必须长得一样，所以是同一份实现（见 `ui/panes/FileChrome.kt`），不是各写一份。
 *
 * ## 交互
 *
 * - 点**目录** → 进去（`onNavigate`）；
 * - 点**文件** → 附加（`onPick`），**面板不关**：可以连着附加好几个到下一条消息；
 * - 面包屑点任一级 / 「上一级」按钮 → 回上一层，到根就停；
 * - 根切换条 → 项目 / HOME / 共享存储（与文件面板同一套语义）。
 *
 * ## 为什么过滤框只筛当前目录
 *
 * 它是**纯内存过滤**，不重扫磁盘。敲字就递归搜索正是上面那个 bug 的成因，
 * 而且每个字符都要遍历目录，手机上会明显卡顿。
 * 想找别的目录，点面包屑或根切换条过去就行。
 *
 * ## 为什么是底部 Sheet 而不是居中弹窗
 *
 * 内容是可变长的目录列表 —— 这正是居中弹窗最差的场景（完整理由见
 * `ModelPickerOverlay` 顶部注释）：竖屏可用高度小、挡住正在生成的回复、动画只有
 * fade。底部 sheet 自带上滑进场、下拉 / 点背板关闭。过滤框留在**不滚动**的头部
 * （原来就这条规则，sheet 里照样成立），列表吃掉余量并内部滚动。
 *
 * 标题由 [OverlayBottomSheet] 自己渲染，**不再**套 `DialogShell`（两行标题 +
 * weight 语义对不上，见 ModelPickerOverlay 的说明）。
 */
@Composable
fun AttachFileOverlay(
    open: Boolean,
    browser: AttachBrowserState,
    onFilterChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onSwitchRoot: (FileRoot) -> Unit,
    onPick: (FileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // 退场时 `browser` 已经被 closeAttachPicker 清成空（而 `open` 也翻了 false），
    // 内容得靠「最后一次非空」兜住 —— 否则退场看到的是空的搜索面板。
    val shownBrowser = rememberLastNonNull(browser.entries.ifEmpty { null })
    OverlayBottomSheet(
        show = open,
        onDismissRequest = onDismiss,
        title = "附加项目文件",
        // 其余参数一律用 Miuix 默认值（同 ModelPickerOverlay：backgroundColor 别动）。
    ) {
        // 原来这里有一句 `if (!open) return@OverlayBottomSheet`：它会在关窗那一瞬间
        // 把内容全拆掉，退场那 250~260ms 只是一张空壳在滑下去。
        val visible = browser.visibleEntries.ifEmpty { shownBrowser.orEmpty() }
        val scheme = MiuixTheme.colorScheme
        // 定高（ModelPickerBody 同款写法）：目录项从 0 条到几十条时面板高度不跳，
        // 过滤框与面包屑的位置稳定。
        // ⚠️ 窗口高度取 LocalWindowInfo，不能取 BoxWithConstraints。
        val windowHeight = LocalWindowInfo.current.containerDpSize.height
        val sheetHeight = (windowHeight * AttachSheetHeightFraction)
            .coerceAtMost(AttachSheetHeightCap)
            .coerceAtLeast(AttachSheetHeightFloor)

        Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            // 1) 根切换条（与文件面板共用）。换根是这一屏最常用的动作之一，
            //    所以放在最上面一行，而不是藏进菜单。
            FileRootSwitcher(
                selected = browser.root,
                onSelect = onSwitchRoot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 2.dp),
            )

            // 2) 面包屑 + 「上一级」。两者都在**不滚动**的头部：
            //    目录走深了以后，回退入口跟着滚上去就等于没有。
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 面包屑自己带横向滚动（Miuix BreadcrumbBar 的语义），所以这里给 weight 让它占满。
                Box(modifier = Modifier.weight(1f)) {
                    FileBreadcrumbBar(filePath = browser.path, onNavigate = onNavigate)
                }
                ZhiIconButton(
                    icon = ZhiIcons.upLevel,
                    description = "上一级目录",
                    onClick = onUp,
                    iconSize = 16.dp,
                    compact = 30.dp,
                )
            }

            // 3) 目录内过滤（不滚动头部）。
            //
            // 代价说清：Miuix `InputField` 有 45dp 最小高度，常驻会少显示一行列表。
            // 这里仍然常驻 —— 它是"边看边改"的控件，跟着滚上去就没法用了；
            // 而文件面板那边不留过滤框是因为那一屏本身还要放表头 + 三个动作按钮。
            ZhiTextField(
                value = browser.filter,
                onValueChange = onFilterChange,
                label = "在当前目录里过滤",
                useLabelAsPlaceholder = true,
                colors = TextFieldDefaults.textFieldColors(
                    labelColor = scheme.onSurfaceVariantSummary,
                ),
                insideMargin = DpSize(10.dp, 2.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 4) 一层子项。空时把**原因**说出来，别让面板空着让人以为坏了。
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ZhiSpace.m),
                    verticalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
                ) {
                    items(visible, key = { it.path }) { entry ->
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
                                    whileClosing = browser.entries.isEmpty() && shownBrowser != null,
                                ),
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.BodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                            )
                        }
                    }
                    item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * 空态的文案。
 *
 * ⚠️ 三种原因必须分开说，不能都显示"0 项"：
 *
 * - **过滤没命中** —— 用户自己敲的字，提示他换一个词；
 * - **目录读不出来**（[note] 非空，典型是共享存储没给「所有文件访问权限」）——
 *   这时说"这个目录是空的"是**撒谎**，用户会以为是目录坏了；
 * - **目录真的是空的**。
 *
 * 还有一个只会在退场那 250ms 出现的中间态：`entries` 已被清空、而屏幕上还挂着
 * 最后一次的内容（[whileClosing]）—— 那时什么都不说，免得在滑下去的过程中
 * 闪出一行"这个目录下没有可附加的文件"。
 */
private fun emptyMessage(filter: String, note: String, whileClosing: Boolean): String = when {
    filter.isNotBlank() -> "这个目录里没有匹配「$filter」的文件"
    note.isNotEmpty() -> note
    whileClosing -> ""
    else -> "这个目录下没有可附加的文件"
}
