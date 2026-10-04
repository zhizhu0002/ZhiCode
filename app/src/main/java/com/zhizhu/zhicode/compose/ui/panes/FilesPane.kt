package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 文件面板此刻画的是三种形态中的哪一种。
 *
 * 单独列成枚举、而不是直接拿 `openFile == null` / `draft != null` 拼一个
 * `AnimatedContent` 的 key：`draft` 是**正在编辑的文本**，把它编进 key 等于
 * 每敲一个键都换一次 targetState，转场会被不停重放（表现为打字时整块在闪）。
 * 枚举只表达"是哪一态"，态内变化不再是转场。
 */
private enum class FileStage { LIST, VIEW, EDIT }

/**
 * 文件面板，对应原版 renderFiles() / showFileBrowser() / showFileEditor()。
 *
 * <h3>从「只读查看」改成可读写</h3>
 *
 * 这一屏原先是「只读浏览 + 只读查看，不做写入」，而且根被钉死在项目目录上
 * （`rootPath() = projectPath`）—— 于是 HOME 与共享存储都走不到，
 * 「上一级」也会被弹回项目顶部。用户要的「挂载 home 目录（需要可读写）」
 * 在应用内的对应物就是这一屏：**能改**、而且**能去三个根**。
 *
 * 写操作全部走 `FileOps`（纯 Java、有 26 条单测）：名字校验、原子保存、
 * 删除不跟符号链接。这里只负责把它的结果如实画出来。
 */
@Composable
fun FilesPane(
    filePath: String,
    entries: List<FileEntry>,
    openFile: OpenFile?,
    onOpen: (FileEntry) -> Unit,
    onUp: () -> Unit,
    onNavigate: (String) -> Unit,
    onCloseFile: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 列表为空时的说明（目录不存在等）。
     * 默认空串 = 目录真的为空，此时不显示任何提示。
     */
    emptyNote: String = "",
    // ---- 可读写相关（都来自 FileOps 封装好的操作） ----
    root: FileRoot = FileRoot.HOME,
    onSwitchRoot: (FileRoot) -> Unit = {},
    draft: String? = null,
    onStartEdit: () -> Unit = {},
    onDraftChange: (String) -> Unit = { _ -> },
    onSave: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    nameForm: FileNameForm? = null,
    onNewFile: () -> Unit = {},
    onNewDirectory: () -> Unit = {},
    onRename: (FileEntry) -> Unit = {},
    onRequestDelete: (FileEntry) -> Unit = {},
    onNameDraftChange: (String) -> Unit = { _ -> },
    onSubmitName: () -> Unit = {},
    onCancelName: () -> Unit = {},
    deletePrompt: FileDeletePrompt? = null,
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    sharedStorageGranted: Boolean = true,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FileRootSwitcher(selected = root, onSelect = onSwitchRoot)
            // 过渡有两个轴：**形态**（目录列表 / 只读查看 / 编辑）与**根**（HOME / 共享存储）。
            //
            // key 用**小枚举**，绝不把 `draft` 文本或 `openFile` 编进去：
            // 把 draft 编进去的话每敲一个键 targetState 都变，转场会被重放（等于没在写字）。
            // 载荷（`openFile`）走 rememberLastNonNull —— 退场那 200ms 里它可能已经变 null，
            // 直接用会让离场那一屏画成空白。
            //
            // 根也编进来，是因为用户报的「标签栏切换没有动画」在文件面板这一侧就是"换根时整屏硬切"。
            // 两条轴共用同一个 AnimatedContent（而不是再嵌一层）：
            // 嵌套要把下面两百行整体缩进一遍，纯属给 diff 添噪音，而判据本来就分得开 ——
            // `stageMoved` 为 false 就是纯换根，走淡变。
            val shownFile = rememberLastNonNull(openFile)
            val stage = when {
                openFile == null -> FileStage.LIST
                draft != null -> FileStage.EDIT
                else -> FileStage.VIEW
            }
            AnimatedContent(
                targetState = stage to root,
                // 进出文件＝进出**一层**，用横向推移；
                // 只读 ↔ 编辑＝同一层的模式切换，用淡变（横推会显得像换了个文件）。
                transitionSpec = {
                    // 换根 = 同一层的**另一个地方** → 淡变。
                    // 不横推，是因为横推在本文件里已经表达了「进出**一层**目录」；
                    // 两个语义共用一个动画，用户就分不清"我换了个根"还是"我进了个目录"。
                    val stageMoved = targetState.first != initialState.first
                    val listInvolved = targetState.first == FileStage.LIST || initialState.first == FileStage.LIST
                    if (stageMoved && listInvolved) {
                        (slideInHorizontally(ZhiMotion.enterSpec) { it / 3 } + fadeIn(ZhiMotion.fadeInSpec))
                            .togetherWith(
                                slideOutHorizontally(ZhiMotion.exitSpec) { -it / 3 } +
                                    fadeOut(ZhiMotion.fadeOutSpec),
                            )
                    } else {
                        fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                    }
                },
                label = "fileStage",
            ) { key ->
                val st = key.first
                // ⚠️⚠️ 每个分支**必须只吐一个** composable —— 这里是外层那个 `Column(…)`。
                //
                // [AnimatedContent] 的容器是**叠放**语义（转场时它必须把新旧两屏放在同一个
                // 位置才能交叉淡变），它**不是** `Column`。所以一个分支里并列写几个
                // composable 时，它们会被放到**同一个原点**上互相盖住 ——
                // 实测表现就是「文件列表和面包屑重叠」。
                //
                // 对照本工程另一处 `AnimatedContent`，它的分支只调一个组件：
                //   · `TerminalPane`：`TerminalStage.FAILURE -> TerminalFailure(...)`。
                // （原先这里还举了 `ChangesPane` 当第二个例子 —— 那个「变更」面板
                //   已经删掉了，例子跟着撤掉，免得指向一个不存在的文件。）
                // 本文件是唯一破例的地方，而原因是这层**原先写的是裸 `when`** ——
                // 裸 `when` 挂在 `Column` 下时兄弟节点是**竖排**的；换成 `AnimatedContent`
                // 之后语义变成叠放，于是「表头 + 面包屑 + 列表」三者叠在了一起。
                // 包一层 `Column` 就把竖排语义找回来了，且不必把这一大坨参数拆成新函数。
                //
                // （测试里把这次重构标为「C-6 最容易改崩」，这就是它崩掉的那一处。）
                when (st) {
                    FileStage.LIST -> Column(modifier = Modifier.fillMaxSize()) {
                        // ⚠️ 这里原先是一个 `PaneHeader(title = "文件", subtitle = "N 项")`，
                        // **下面**再单独一行面包屑 —— 于是列表前叠了三层
                        // （根标签栏 + 表头 + 面包屑），而那个标题"文件"与**顶部标签栏
                        // 正在高亮的那一项**完全是同一件事，纯重复。
                        //
                        // 现在合成一行：面包屑 + 三个动作，共用 [FilePathBar]
                        // （附加面板那一行也是它，两处的行几何因此严格一致）。
                        //
                        // 代价说清：**「N 项」不再显示**。空目录本来就走空态文案
                        // （`emptyNote`），不缺这个数字；条数很多时也确实没有再回来的必要。
                        FilePathBar(
                            filePath = filePath,
                            onNavigate = onNavigate,
                            trailing = {
                                // 三个动作：新建文件 / 新建文件夹 / 上一级。
                                ZhiIconButton(
                                    icon = ZhiIcons.file,
                                    description = "新建文件",
                                    onClick = onNewFile,
                                    iconSize = 16.dp,
                                    compact = 30.dp,
                                )
                                ZhiIconButton(
                                    icon = ZhiIcons.directory,
                                    description = "新建文件夹",
                                    onClick = onNewDirectory,
                                    iconSize = 16.dp,
                                    compact = 30.dp,
                                )
                                ZhiIconButton(
                                    icon = ZhiIcons.upLevel,
                                    description = "上一级目录",
                                    onClick = onUp,
                                    iconSize = 16.dp,
                                    compact = 30.dp,
                                )
                            },
                        )
                        // 表头下面那条分隔线不能丢：查看/编辑分支的 `PaneHeader` 自带一条，
                        // 少了它，切形态时那条线会忽隐忽现。
                        ZhiHorizontalDivider()
                        nameForm?.let { form ->
                            FileNameFormCard(
                                form = form,
                                onDraftChange = onNameDraftChange,
                                onSubmit = onSubmitName,
                                onCancel = onCancelName,
                            )
                        }
                        deletePrompt?.let { prompt ->
                            FileDeleteCard(
                                prompt = prompt,
                                onConfirm = onConfirmDelete,
                                onCancel = onCancelDelete,
                            )
                        }
                        // 共享存储没授权时说清怎么开 —— 否则用户看到的是一个空列表，
                        // 而原因（「所有文件访问权限」）在界面上完全没有痕迹。
                        if (root == FileRoot.SHARED && !sharedStorageGranted) {
                            Text(
                                text = "共享存储需要「所有文件访问权限」：系统设置 → 应用 → ZhiCode → 权限 → 文件和媒体 → 允许管理所有文件。",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Caption,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 6.dp),
                            )
                        }
                        // 不做常驻过滤框：Miuix InputField 有 45dp 最小高度，
                        // 常驻会把文件列表挤下去，与"文件 UI 紧凑"的要求冲突。
                        // `weight(1f)` 而不是 `fillMaxSize()`：这里是 `Column` 的孩子，
                        // 要的是**剩余**高度（表头与面包屑已经占掉一部分）。`fillMaxSize()`
                        // 依赖「Column 会替后面的孩子扣掉已用高度」这条隐式行为，写 weight
                        // 是显式的，也与 `TerminalPane` 里那一处保持同一写法。
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                // 左右留白走工程统一的间距令牌 `ZhiSpace.m`（12dp，它的注释写的就是
                                // "列表左右留白"）。之前这里是 4dp 并注明"文件名要尽可能宽" ——
                                // 那个取舍换来的是**整列贴着屏幕边**，实测太挤；文件名长一点本来
                                // 也会被省略号截掉，多这 8dp 并不会更早截断，但观感差别很大。
                                modifier = Modifier.fillMaxSize().padding(horizontal = ZhiSpace.m),
                                verticalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
                            ) {
                                items(entries, key = { it.path }) { entry ->
                                    FileListRow(
                                        entry = entry,
                                        onOpen = { onOpen(entry) },
                                        // 删除/重命名之后让行**滑过去**，而不是"啪"地整体上跳一位。
                                        // `key = it.path` 已给，所以 Compose 认得出是同一行换了位置
                                        // （重命名会换 path → 那是新 key，属于"新建"，不走这条）。
                                        modifier = Modifier.animateItem(),
                                        // 尾部的两个动作：面板是**可读写**的那一屏，所以给它们。
                                        // 附加选择器用的是同一个 [FileListRow]，尾部换成「＋ 附加」。
                                        trailing = {
                                            ZhiIconButton(
                                                icon = ZhiIcons.edit,
                                                description = "重命名",
                                                onClick = { onRename(entry) },
                                                tint = scheme.onSurfaceVariantSummary,
                                                iconSize = 14.dp,
                                                compact = 30.dp,
                                            )
                                            ZhiIconButton(
                                                icon = ZhiIcons.delete,
                                                description = "删除",
                                                onClick = { onRequestDelete(entry) },
                                                tint = ZhiColors.red(),
                                                iconSize = 14.dp,
                                                compact = 30.dp,
                                            )
                                        },
                                    )
                                }
                                // 目录不存在时把原因说出来，而不是让面板空着（"0 项"）
                                // 让用户以为应用坏了。
                                if (entries.isEmpty() && emptyNote.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = emptyNote,
                                            color = scheme.onSurfaceVariantSummary,
                                            fontSize = ZhiTextScale.Caption,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                                        )
                                    }
                                }
                                item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
                            }
                            // Miuix 滚动条
                            VerticalScrollBar(
                                adapter = rememberScrollBarAdapter(listState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                    }

                    // 同上：这一支也并列了「表头 + 面包屑 + 内容」三块（编辑态是输入框、
                    // 查看态是行号列表），不包起来同样会叠在一起。
                    FileStage.VIEW, FileStage.EDIT -> Column(modifier = Modifier.fillMaxSize()) {
                        // 退场期间 `openFile` 可能已为 null，而这一屏还要画完。
                        val file = shownFile ?: return@AnimatedContent
                        // 正在写的文本同理：退出编辑那一瞬间 `draft` 已经是 null，
                        // 直接读会让退场那 200ms 里的输入框突然变空。
                        val editText = rememberLastNonNull(draft)
                        // 编辑与否由**正在渲染的那一态**决定，不看外部 draft：
                        // 转场时 st 还是旧值而 draft 已经变了，否则看不到"保存/编辑"两个按钮的切换。
                        val editing = st == FileStage.EDIT
                        PaneHeader(
                            title = file.name,
                            subtitle = if (editing) "${file.language} · 编辑中" else "${file.language} · 只读",
                            actions = {
                                if (editing) {
                                    ZhiIconButton(
                                        icon = ZhiIcons.done,
                                        description = "保存",
                                        onClick = onSave,
                                        iconSize = 16.dp,
                                        compact = 30.dp,
                                    )
                                    ZhiIconButton(
                                        icon = ZhiIcons.close,
                                        description = "放弃改动",
                                        onClick = onCancelEdit,
                                        iconSize = 16.dp,
                                        compact = 30.dp,
                                    )
                                } else {
                                    ZhiIconButton(
                                        icon = ZhiIcons.edit,
                                        description = "编辑",
                                        onClick = onStartEdit,
                                        iconSize = 16.dp,
                                        compact = 30.dp,
                                    )
                                    ZhiIconButton(
                                        icon = ZhiIcons.close,
                                        description = "关闭文件",
                                        onClick = onCloseFile,
                                        iconSize = 16.dp,
                                        compact = 30.dp,
                                    )
                                }
                            },
                        )
                        // 同样走 [FilePathBar]：这一行没有行尾动作，但**行高与内边距**
                        // 必须与列表分支、附加面板完全一致，否则切形态时面包屑会跳一下。
                        FilePathBar(filePath = file.path, onNavigate = onNavigate)
                        if (editing) {
                            // 编辑态：整块可滚动的多行输入框。用等宽字体 —— 缩进与列对齐
                            // 在读代码时是有意义的信息，换了比例字体就没法看了。
                            ZhiTextField(
                                value = editText.orEmpty(),
                                onValueChange = onDraftChange,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .padding(horizontal = ZhiSpace.m, vertical = 4.dp),
                                minLines = 12,
                                textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                            )
                        } else {
                            val lines = remember(file.path) { file.content.split('\n') }
                            Surface(modifier = Modifier.weight(1f).fillMaxWidth(), color = ZhiColors.panelSurface()) {
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 3.dp)) {
                                    // ⚠️ 这里**不加** `key = { it }`。行号就是下标，下标当 key 看着
                                    // 像稳定标识、其实是位置别名：换一个文件时「第 400 行」这个 key
                                    // 还存在，Compose 会把它当同一项而只替内容，位置变化/增删反而
                                    // 不产生任何动画；而换到短文件时又会凭空冒出一批淡出。
                                    // 保持默认（按下标）并只挂 animateItem：能拿到的是「行内容变化」
                                    // 的淡入与尾部增删的过渡，这是这个列表真会出现的变化。
                                    // 整屏的「换文件」过渡由外层的 AnimatedContent 负责（C-4）。
                                    items(lines.size) { index ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp)
                                                .animateItem(),
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = scheme.onSurfaceVariantSummary,
                                                fontSize = ZhiTextScale.Footnote,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.width(26.dp),
                                            )
                                            Text(
                                                text = lines[index].ifEmpty { " " },
                                                color = scheme.onSurface,
                                                fontSize = ZhiTextScale.Footnote,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「新建 / 重命名」表单。
 *
 * 错误行用 [ZhiFieldError] 而不是自己写 Text：标题栏下方那一条要和其他表单
 * 「左边缘对齐、只在有错时才出现」的规则一致（见 `FieldErrorAlignmentTest`）。
 */
@Composable
private fun FileNameFormCard(
    form: FileNameForm,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 4.dp)) {
        Text(
            text = form.title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = ZhiTextScale.Caption,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        ZhiTextField(
            value = form.draft,
            onValueChange = onDraftChange,
            singleLine = true,
            label = if (form.target == null) "名字" else "新名字",
        )
        ZhiFieldError(form.error)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
        ) {
            SecondaryButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "确定",
                onClick = onSubmit,
                enabled = form.saveable,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 删除确认。
 *
 * 逐字说清代价：删一个目录会带走里面的全部内容，而「确定删除 sub 吗？」
 * 等于没告诉用户这件事。条数来自 `FileOps.countForDelete`。
 */
@Composable
private fun FileDeleteCard(
    prompt: FileDeletePrompt,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 4.dp)) {
        Text(
            text = if (prompt.destructive) {
                "删除「${prompt.entry.name}」？\n其中的 ${prompt.count - 1} 项内容会一起消失，无法撤销。"
            } else {
                "删除「${prompt.entry.name}」？无法撤销。"
            },
            color = if (prompt.destructive) ZhiColors.red() else scheme.onSurface,
            fontSize = ZhiTextScale.Caption,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
        ) {
            SecondaryButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(text = "删除", onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

