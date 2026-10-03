package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

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
    root: FileRoot = FileRoot.PROJECT,
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
            // 三种形态（目录列表 / 只读查看 / 编辑）之间的过渡。
            //
            // key 用**小枚举**，绝不把 `draft` 文本或 `openFile` 编进去：
            // 把 draft 编进去的话每敲一个键 targetState 都变，转场会被重放（等于没在写字）。
            // 载荷（`openFile`）走 rememberLastNonNull —— 退场那 200ms 里它可能已经变 null，
            // 直接用会让离场那一屏画成空白。
            val shownFile = rememberLastNonNull(openFile)
            val stage = when {
                openFile == null -> FileStage.LIST
                draft != null -> FileStage.EDIT
                else -> FileStage.VIEW
            }
            AnimatedContent(
                targetState = stage,
                // 进出文件＝进出**一层**，用横向推移；
                // 只读 ↔ 编辑＝同一层的模式切换，用淡变（横推会显得像换了个文件）。
                transitionSpec = {
                    if (targetState == FileStage.LIST || initialState == FileStage.LIST) {
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
            ) { st ->
                // ⚠️⚠️ 每个分支**必须只吐一个** composable —— 这里是外层那个 `Column(…)`。
                //
                // [AnimatedContent] 的容器是**叠放**语义（转场时它必须把新旧两屏放在同一个
                // 位置才能交叉淡变），它**不是** `Column`。所以一个分支里并列写几个
                // composable 时，它们会被放到**同一个原点**上互相盖住 ——
                // 实测表现就是「文件列表和面包屑重叠」。
                //
                // 对照本工程另外两处 `AnimatedContent`，它们的分支都只调一个组件：
                //   · `TerminalPane`：`TerminalStage.FAILURE -> TerminalFailure(...)`；
                //   · `ChangesPane`：每个分支一个 `Box` 或一个 `LazyColumn`。
                // 本文件是唯一破例的地方，而原因是这层**原先写的是裸 `when`** ——
                // 裸 `when` 挂在 `Column` 下时兄弟节点是**竖排**的；换成 `AnimatedContent`
                // 之后语义变成叠放，于是「表头 + 面包屑 + 列表」三者叠在了一起。
                // 包一层 `Column` 就把竖排语义找回来了，且不必把这一大坨参数拆成新函数。
                //
                // （测试里把这次重构标为「C-6 最容易改崩」，这就是它崩掉的那一处。）
                when (st) {
                    FileStage.LIST -> Column(modifier = Modifier.fillMaxSize()) {
                        PaneHeader(
                            title = "文件",
                            subtitle = "${entries.size} 项",
                            // 行尾三个动作：新建文件 / 新建文件夹 / 上一级。
                            // 用 actions 槽而不是 actionIcon —— 只给一个图标按钮的话
                            // 「上一级」和「新建」只能二选一。
                            actions = {
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
                        // 路径用 Miuix BreadcrumbBar 展示，点击任一层级都能直接跳转
                        FileBreadcrumbBar(filePath = filePath, onNavigate = onNavigate)
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
                                    FileRow(
                                        entry = entry,
                                        onOpen = { onOpen(entry) },
                                        onRename = { onRename(entry) },
                                        onDelete = { onRequestDelete(entry) },
                                        // 删除/重命名之后让行**滑过去**，而不是"啪"地整体上跳一位。
                                        // `key = it.path` 已给，所以 Compose 认得出是同一行换了位置
                                        // （重命名会换 path → 那是新 key，属于"新建"，不走这条）。
                                        modifier = Modifier.animateItem(),
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
                        FileBreadcrumbBar(filePath = file.path, onNavigate = onNavigate)
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
 * 根切换条。
 *
 * 三个根是三种不同的活儿（项目=代码、HOME=配置、共享存储=用户的文件），
 * 放在标题栏下方一行，而不是藏进菜单里 —— 换根是这一屏最常用的动作之一。
 */
@Composable
private fun FileRootSwitcher(selected: FileRoot, onSelect: (FileRoot) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpace.m, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ZhiSpace.xs),
    ) {
        FileRoot.entries.forEach { root ->
            val active = root == selected
            // 选中/未选中的底色与文字色都走淡变：选中时底色与文字是**同时**变的，
            // 只淡其中一个会出现「字已经变了、底还是旧色」的中间态，比不做动画更难看。
            // 令牌用 [ZhiMotion.colorSpec]（150ms + SinOut），与 ModelPicker 那几个
            // 选中行同一条曲线。对照 upstream `CardSection.kt:122`：可点的卡用 `Sink`。
            val segmentColor by animateColorAsState(
                targetValue = if (active) scheme.primaryContainer else ZhiColors.cardSurface(),
                animationSpec = ZhiMotion.colorSpec,
                label = "fileRootSegment",
            )
            val segmentContent by animateColorAsState(
                targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurface,
                animationSpec = ZhiMotion.colorSpec,
                label = "fileRootSegmentContent",
            )
            Card(
                onClick = { if (!active) onSelect(root) },
                modifier = Modifier.weight(1f),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(vertical = 6.dp),
                colors = CardDefaults.defaultColors(
                    color = segmentColor,
                    contentColor = segmentContent,
                ),
                // Miuix 的默认值是 `PressFeedbackType.None`（这是**超出手册默认**的一项，
                // 不是改回默认）：它是一个真的按钮，按下去要有下沉反馈。
                // 对应 upstream 官方示例 `CardSection.kt:122`「可点卡片用 Sink」。
                pressFeedbackType = PressFeedbackType.Sink,
            ) {
                Text(
                    text = root.label,
                    fontSize = ZhiTextScale.Footnote,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
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

/** 把绝对路径拆成 Miuix `BreadcrumbBar` 需要的层级列表。 */
@Composable
private fun FileBreadcrumbBar(
    filePath: String,
    onNavigate: (String) -> Unit,
) {
    val items = remember(filePath) { breadcrumbItems(filePath) }
    if (items.isEmpty()) return

    BreadcrumbBar(
        items = items,
        onItemClick = { index -> items.getOrNull(index)?.let { onNavigate(it.path) } },
        highlightIndex = items.lastIndex,
        // 面包屑只占一行的引导作用，不需要占满宽度：
        // 压小 insideMargin，给下面的文件列表让位。
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        // itemMaxWidth 用 Miuix 的默认值 160dp，不覆盖 —— 每一项都是单独的目录名，
        // 最长的是包名 `com.zhizhu.code`（15 字符，约 146dp），160dp 放得下。
        // ⚠️ 别改成把好几级拼成一条路径：那一定会被省略号截断，反而什么都看不见。
    )
}

/**
 * 生成面包屑层级：**一级目录一个项**，从「软件根目录」开始。
 *
 * `/data/user/0/com.zhizhu.code/files/home/workspace`
 *   → `com.zhizhu.code` `files` `home` `workspace`
 *
 * 为什么砍掉前面的 `/data/user/0`：它在应用沙箱里是 `drwx--x--x`
 * （other 只有 x 没有 r），**列不出来** —— 挂一个点进去只能看到
 * 「无法读取（权限不足）」的层级，纯粹是噪音。首项文字直接用包名，
 * 它确实就是软件根目录，语义也对得上。
 *
 * 路径在应用根**之外**（设置里把项目路径改到了 `/sdcard/...` 等）时，
 * 没有“包名”可以当起点，就逐级展示真实层级；此时首项是 `/`。
 *
 * 每一项的 `path` 都是真实层级，点哪一级就回到哪一级。
 */
private fun breadcrumbItems(filePath: String): List<BreadcrumbItem> {
    val normalized = filePath.trimEnd('/').ifEmpty { "/" }
    val appRoot = TermuxConstants.TERMUX_DATA_DIR_PATH.trimEnd('/')

    val start = if (normalized == appRoot || normalized.startsWith("$appRoot/")) appRoot else "/"
    val rest = when {
        normalized == start -> ""
        start == "/" -> normalized.removePrefix("/")
        else -> normalized.removePrefix("$start/")
    }

    val items = mutableListOf(
        BreadcrumbItem(path = start, text = start.substringAfterLast('/').ifEmpty { "/" }),
    )
    var accumulated = if (start == "/") "" else start
    rest.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        accumulated += "/$segment"
        items += BreadcrumbItem(path = accumulated, text = segment)
    }
    return items
}

/**
 * 文件行：此前 vertical = 20dp 导致行高约 60dp，一屏放不下几个文件（V2）。
 * 收到 11dp ≈ 40dp 行高，仍在 Material 触摸目标下限（48dp）附近，密度观感
 * 与 Miuix 设置列表行一致。
 *
 * <p>行尾两个动作（重命名 / 删除）用 [ZhiIconButton] 的 `compact` 压到 30dp ——
 * 与 `PaneHeader` 的做法一致：Miuix `TextButton` 写死 `MinWidth=58dp`/`MinHeight=40dp`，
 * 在这条 40dp 高的行里放不下，图标按钮才是能安全压小的那个。
 */
@Composable
private fun FileRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        // 行距由 LazyColumn 的 `spacedBy` 统一给（原来这里还有一个 `padding(vertical = 1.dp)`，
        // 两个地方都给间距会让以后调行距要改两处，而且那 1dp 几乎等于没有）。
        // `modifier` 里是 `animateItem()`，所以它在 fillMaxWidth **之前**：
        // 尺寸照旧铺满，动画交给 LazyColumn 记账。
        modifier = modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(start = ZhiSpace.m, end = ZhiSpace.xs, top = 4.dp, bottom = 4.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                painter = if (entry.directory) ZhiIcons.directory else ZhiIcons.file,
                contentDescription = null,
                tint = if (entry.directory) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = entry.name,
                fontSize = ZhiTextScale.Caption,
                fontWeight = if (entry.directory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!entry.directory) {
                Text(
                    text = formatSize(entry.size),
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Micro,
                )
            }
            ZhiIconButton(
                icon = ZhiIcons.edit,
                description = "重命名",
                onClick = onRename,
                tint = scheme.onSurfaceVariantSummary,
                iconSize = 14.dp,
                compact = 30.dp,
            )
            ZhiIconButton(
                icon = ZhiIcons.delete,
                description = "删除",
                onClick = onDelete,
                tint = ZhiColors.red(),
                iconSize = 14.dp,
                compact = 30.dp,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1_000L -> "$bytes B"
    bytes < 1_000_000L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1000f)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000f)
}
