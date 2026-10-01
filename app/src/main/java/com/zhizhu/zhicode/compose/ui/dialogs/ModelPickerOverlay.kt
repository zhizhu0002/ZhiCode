package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模型选择面板。
 *
 * 对应原版 `showModelPanel()`：标题带当前配置名、状态行、异步填充的模型列表、
 * 手动输入框、底部「管理 API / 使用模型」。
 *
 * ## 为什么是底部 Sheet 而不是居中弹窗
 *
 * 这是**唯一**一个"内容是可变长列表 + 用户要边看边挑"的面板。居中弹窗在这个场景
 * 上有两个具体问题：
 *
 * 1. 它悬在屏幕中间，上方那条正在流式输出的回复被压掉一半 —— 而挑模型往往正是
 *    因为**看了正在生成的回复**才想换一个，这时最不该挡住的就是它；
 * 2. 手机竖屏下弹窗的可用高度比 sheet 小（上下都要留边距），长列表更早触发滚动。
 *
 * 底部 sheet 贴底、上不封顶（按内容涨到窗口高为止），正好把"被挡住的对话"留给
 * 用户看，这也是 rikkahub 的做法。
 *
 * ## 标题与按钮的分工
 *
 * 标题由 [OverlayBottomSheet] 自己渲染（它的 `title` 参数），所以这里**不再**套
 * `DialogShell` —— 那个外壳自带标题行与 `weight(1f)`，套进来会出现两行标题，
 * 而且它的 weight 依赖一个有界高度，sheet 的高度由内容决定，语义也对不上。
 * 底部两个按钮仍留在内容里：sheet 的 `endAction` 是画在**标题行**上的
 * （见 Miuix 的 `TitleAndActionsRow`），与参考图的"按钮在底部"不是一回事。
 *
 * ## 为什么列表是懒的
 *
 * 目录最多 250 项（引擎侧 `MAX_MODELS` 上限）。虽然不算多，但把这些行全部即时组合
 * 进面板不如用 [LazyColumn]；同时给一个 [heightIn] 上限，否则长列表会把面板顶出屏幕，
 * 底部的「使用模型」按钮就点不到了。
 * （这条 `heightIn` 还是 `DialogScrollNestingTest` 的硬性要求：没有它，
 * 无限高的 LazyColumn 会拿到 Infinity 高度约束并直接崩。）
 *
 * ## 列表为空不等于失败
 *
 * [ModelPickerState.status] 已经区分了「加载中 / 获取到 N 个 / 未返回 / 具体错误」，
 * 所以这里只负责把它显示出来；出现空列表时旁边的输入框就是兜底路径，
 * 这不是"错误态"而是"另一种用法"。
 */
@Composable
fun ModelPickerOverlay(
    picker: ModelPickerState?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onUse: (String) -> Unit,
    onOpenApiConfig: () -> Unit,
) {
    OverlayBottomSheet(
        show = picker != null,
        onDismissRequest = onDismiss,
        title = "选择模型 · ${picker?.profileName.orEmpty()}",
        // 其余参数（圆角、底色、拖拽把手颜色、内外边距、最大宽度）一律用 Miuix 默认值：
        // 它们本来就是从主题里取的，手挑一套只会在动态取色或深浅切换时失配。
    ) {
        val current = picker ?: return@OverlayBottomSheet
        ModelPickerBody(
            picker = current,
            onQueryChange = onQueryChange,
            onSearchChange = onSearchChange,
            onUse = onUse,
            onOpenApiConfig = onOpenApiConfig,
        )
    }
}

/**
 * 搜索框。
 *
 * 只在**目录已经拿到、且不止一条**时才出现：只有两三个模型时它占的位置比它省下的
 * 翻找更多，而目录拉失败时它更是一个筛不出任何东西的死控件（那种情况走下面的
 * 手动输入框）。上限 250 条（引擎侧 `MAX_MODELS`）才是它真正有用的场景。
 */
@Composable
private fun ModelSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    ZhiTextField(
        value = value,
        onValueChange = onValueChange,
        label = "输入模型名称搜索",
        useLabelAsPlaceholder = true,
        singleLine = true,
        // 圆角给足，与参考图里那种"胶囊搜索框"一致；Miuix 默认圆角偏小。
        cornerRadius = ZhiRadius.card,
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = scheme.surfaceContainerHigh,
            labelColor = scheme.onSurfaceVariantSummary,
        ),
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 分组标题：提供方名 + 条目数。
 *
 * ## 为什么没有首字母头像
 *
 * 这里曾经放过一个 `primaryContainer` 的首字母方块。删掉的理由：参考图那一行左边
 * 是**折叠箭头**，没有头像；而我们的面板永远只有一组，箭头是死控件（见下），
 * 于是留下的是一个既不对应参考图、信息量也为零的方块 —— 提供方名字就在它右边，
 * 同一个名字的首字母重复一遍没有任何作用。
 *
 * ## 为什么没有折叠箭头
 *
 * 参考图里这一行左边有个 `⌄`。但这个面板**永远只有一组** —— 一个 API 配置
 * （profile）就对应一个提供方与一份目录，`ModelPickerState` 里也只有单个
 * `profileName`。一组还要折叠的话，点下去就是把整个列表收起来，没有意义。
 * 所以这里不画那个箭头：画一个点了没用的控件比不画更糟。
 */
@Composable
private fun ModelGroupHeader(profileName: String, count: Int) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = profileName,
            // 与参考图一致：分组标题用主色。这是主题令牌（深浅色各自成立），不是写死的颜色。
            color = scheme.primary,
            fontSize = ZhiTextScale.BodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count 个模型",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
        )
    }
}

@Composable
private fun ModelPickerBody(
    picker: ModelPickerState,
    onQueryChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onUse: (String) -> Unit,
    onOpenApiConfig: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = picker.status,
            color = if (picker.models.isEmpty() && !picker.loading) {
                scheme.onSurfaceVariantSummary
            } else {
                scheme.primary
            },
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (picker.models.size > 1) {
            ModelSearchField(value = picker.search, onValueChange = onSearchChange)
        }

        // 过滤规则见 `ModelPickerState.visibleModels`（派生属性，可脱离 Compose 单测）。
        val visible = picker.visibleModels

        if (picker.models.isNotEmpty()) {
            ModelGroupHeader(profileName = picker.profileName, count = visible.size)
            // 外层不再套 Card：参考图里每个模型是**独立的一张卡**，行与行之间有缝。
            // 共用一个 Card 再靠分割线分开，视觉上是一整块面板，与参考图不是一回事。
            //
            // `heightIn(max = 420.dp)` 是硬性要求，不能删：LazyColumn 在竖直方向没有
            // 高度上限时会拿到 Infinity 高度约束，Compose 直接抛异常崩掉
            // （见 DialogScrollNestingTest）。
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.id }) { model ->
                    ModelRow(
                        option = model,
                        selected = model.id == picker.currentModel,
                        onPick = { onQueryChange(model.id) },
                    )
                }
                if (visible.isEmpty()) {
                    // 搜不到时给一句话，而不是留一片空白 —— 空白与"还在加载"、
                    // "目录是空的"三种情况看起来一模一样。
                    item {
                        Text(
                            text = "没有匹配「${picker.search}」的模型",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = ZhiTextScale.Footnote,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
            }
        }

        ZhiTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            label = "模型名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "点列表里的模型会填进上面的框；再点「使用模型」才生效。" +
                "任务正在运行时切换，会在下一轮完整请求生效。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        // 按钮照旧靠右，与居中弹窗时的位置一致：换容器不该顺手改按钮的排布，
        // 否则用户得重新找一遍「使用模型」在哪。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(text = "管理 API", onClick = onOpenApiConfig)
            PrimaryButton(
                text = "使用模型",
                enabled = picker.query.isNotBlank(),
                onClick = { onUse(picker.query) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * 目录里的一行（卡片式，与参考图一致）。
 *
 * 选中态用 `primaryContainer` 铺满整行，而不是只在左侧画一个勾：
 * 参考图里当前模型那一条是**整块高亮**的，一眼就能从二三十行里认出来；
 * 一个小勾在长列表里很容易被扫过去。
 *
 * `displayName` 与 `id` 相同时只显示一次，否则每行会重复两遍同一个名字
 * （服务端常常不给 display_name，那种情况下它会被回落成 id）。
 */
@Composable
private fun ModelRow(
    option: ModelOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val duplicated = option.displayName == option.id
    // 每行是**自己一张 Miuix Card**（参考图就是这样：模型之间有一条缝），
    // 而不是共用一个外层 Card 再靠分割线分开。
    //
    // 用 Miuix `Card` 而不是裸 `Surface`：Card 是这套设计系统里的行容器（自带形状裁剪
    // 与按压反馈），而且**它支持容器色** —— `BasicComponent` 没有颜色参数（只有 modifier），
    // 把底色写进 modifier 会画出一个**直角**色块、与卡片圆角对不上。
    // 这样既拿到了参考图那种"整块高亮"的观感，又没离开 Miuix 的组件。
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
    ) {
        BasicComponent(
            title = option.displayName,
            titleColor = BasicComponentDefaults.titleColor(
                color = if (selected) scheme.onPrimaryContainer else scheme.onBackground,
            ),
            summary = if (duplicated) null else option.id,
            summaryColor = BasicComponentDefaults.summaryColor(
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariantSummary,
            ),
            startAction = {
                // 选中标记跟 Miuix 下拉列表一致：Check 图标 + 它自己的尺寸常量。
                // 不用 Checkbox（固定 26dp 且是圆的，配 11~13sp 行文字偏大），
                // 理由详见 Dialogs.kt 里的同一处注释。
                if (selected) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(DropdownDefaults.CheckIconSize),
                    )
                }
            },
            onClick = onPick,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
