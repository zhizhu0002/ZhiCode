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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.ui.ZhiSectionLabel
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
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
 * （见 Miuix 的 `TitleAndActionsRow`），与用户习惯的"按钮在底部"不是一回事。
 *
 * ## 结构全部照 Miuix 自己的示例，不自己拼
 *
 * 这个面板前后改过四版，踩的坑有个共同点：**都是在"自己拼结构 + 自己挑颜色"**。
 * 每一处都有 Miuix 官方示例或组件可以直接照，照了就不会错：
 *
 * | 问题 | 之前的错法 | 官方做法 |
 * | --- | --- | --- |
 * | 卡片与背板同色、卡片看不见 | 什么都不传，信"默认值总是搭好的" | 官方**显式**传 `secondaryContainer` |
 * | 选中行文字反而更暗 | 把 `onSecondaryContainer`(`#7C7C7C` 中灰) 当前景色 | 前景色用 `onSurfaceContainer`(近白) |
 * | 搜索框填充看不见 | 覆写成 `surfaceContainerHigh` | 用默认的 `secondaryContainer` |
 * | 高亮"选中项" | 自己拼卡片 + 手绘色块 | 官方 Card 示例的蓝卡：`primaryVariant` |
 *
 * 原因是 Miuix 的**默认值分属两套、并不保证互相搭**：`OverlayBottomSheet` 的背板取
 * `background`，而 `CardDefaults` 的卡片色取 `surfaceContainer` —— **深色下两者都是
 * `#242424`**。所以"全用默认值"在 sheet 里恰好会让卡片消失。这不是它设计得差，
 * 而是它的默认值面向"页面里直接放卡片"，不是"卡片放在 sheet 里"。
 *
 * ## 选中态为什么是蓝色卡片
 *
 * 直接照 Miuix 自己的 Card 示例（就是那个"ShowIndication: true"的蓝卡）：
 * `CardDefaults.defaultColors(color = primaryVariant)` + 文字 `onPrimaryVariant`。
 * 深色下分别是 `#0073DD` 和 `#99C7F1` —— 蓝底浅蓝字。
 *
 * ## 为什么不用 `RadioButtonPreference`
 *
 * 它是 Miuix 里"从列表单选一个"的原生组件，一度用过。换掉的原因很具体：
 * 它的选中表达是**单选圈 + 主色文字**，而这里要的是**整块蓝卡**；两者叠在一起时，
 * 单选圈用的是 `primary`（`#277AF7`），画在 `primaryVariant`（`#0073DD`）的蓝底上
 * **几乎看不见**，得把 `radioButtonColors` 和四组 `titleColor`/`summaryColor` 全部
 * 重写一遍 —— 那就又回到"自己拼"的老路上了。
 *
 * 无障碍语义没有丢：[BasicComponent] 本身有 `role` 参数，传 `Role.RadioButton`
 * 就还是"单选组里的一个选项"，读屏软件读得出来；只是不画那个圈。
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
        // 其余参数（圆角、底色、拖拽把手颜色、内外边距、最大宽度）一律用 Miuix 默认值。
        // ⚠️ `backgroundColor` 尤其不要动：默认的 `background` 就是官方示例里的背板色，
        // 卡片之所以要显式传 `secondaryContainer`，正是为了跟它**拉开**一档。
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
 * Sheet 里小标题的内边距。
 *
 * Miuix `SmallTitleDefaults.InsideMargin` 是 `PaddingValues(28.dp, 8.dp)`，而 sheet 自身
 * 已经有 24dp 横向内边距 —— 两者相加 52dp，标题会缩进得比卡片深一大截。官方
 * `BottomSheetSection` 示例在 sheet 里覆写成 16dp，这里跟随它。
 */
private val PickerSectionInsideMargin = PaddingValues(16.dp, 8.dp)

/** 组与组之间的间距，与官方示例一致（12dp）。 */
private val PickerGroupSpacing = 12.dp

/**
 * 列表里相邻两张卡片之间的间距。
 *
 * 官方示例里**互不相关**的卡片之间是 12dp；这里是同一份列表内的相邻项，取 8dp ——
 * 比 12dp 紧凑、又不至于像更小那样让卡片边界糊在一起。
 */
private val ModelRowSpacing = 8.dp

/**
 * 模型列表的高度上限。
 *
 * 不能删：目录最多 250 项（引擎侧 `MAX_MODELS`），不封顶的话长列表会把面板顶出屏幕，
 * 底部的「使用模型」按钮就点不到了。这也是 `DialogScrollNestingTest` 的硬性要求 ——
 * 没有它，无限高的 [LazyColumn] 会拿到 Infinity 高度约束并直接崩。
 */
private val ModelListMaxHeight = 420.dp

/**
 * 搜索框。
 *
 * 只在**目录已经拿到、且不止一条**时才出现：只有两三个模型时它占的位置比它省下的
 * 翻找更多，而目录拉失败时它更是一个筛不出任何东西的死控件（那种情况走下面的
 * 手动输入框）。上限 250 条（引擎侧 `MAX_MODELS`）才是它真正有用的场景。
 *
 * 颜色与圆角**都用 Miuix 默认**：默认底色 `secondaryContainer`（`#434343`）本来就比
 * sheet 背板亮一档、看得见填充，之前覆写成 `surfaceContainerHigh` 反而把它抹成了背板色。
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
        leadingIcon = {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = PickerGroupSpacing),
    )
}

/**
 * 目录里的一行：**每个模型一张 Miuix Card**，选中那张是蓝卡。
 *
 * 卡片颜色对照（深色方案的实际值），这也是整块面板的层级关系：
 *
 * | | 卡片底 | 标题 | 副标题 |
 * | --- | --- | --- | --- |
 * | sheet 背板 | `#242424` | — | — |
 * | 未选中行 | `secondaryContainer` `#434343` | `onBackground` | `onSurfaceVariantSummary` |
 * | **选中行** | `primaryVariant` `#0073DD` | `onPrimaryVariant` `#99C7F1` | 同左 |
 *
 * 三行文字色都必须**显式**传：`BasicComponentDefaults.titleColor()` 的默认值是
 * `onBackground`、`summaryColor()` 的默认值是 `onSurfaceVariantSummary` ——
 * 都不跟随卡片的 `contentColor`（这条和 Material3 的直觉相反），所以不传的话
 * 蓝卡上会出现深色字，几乎读不出来。官方 Card 示例同样两个文字色都显式写了
 * `onPrimaryVariant`。
 *
 * `role = Role.RadioButton`：这是同一个单选组里的一个选项，语义上要报给读屏软件。
 * 只是不画单选圈 —— 整张蓝卡就是选中标记（理由见文件顶部）。
 */
@Composable
private fun ModelRow(
    option: ModelOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // `displayName` 与 `id` 相同时只显示一次，否则每行会重复两遍同一个名字
    // （服务端常常不给 display_name，那种情况下它会被回落成 id）。
    val duplicated = option.displayName == option.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        // 官方 Card 示例用的就是这两个令牌；`color` 必须显式传而不是靠默认值 ——
        // `CardDefaults` 默认的 `surfaceContainer` 与 sheet 背板 `background` 同值
        // （都是 `#242424`），不传的话未选中的卡片在背板上完全看不出来。
        colors = CardDefaults.defaultColors(
            color = if (selected) scheme.primaryVariant else scheme.secondaryContainer,
        ),
    ) {
        BasicComponent(
            title = option.displayName,
            titleColor = BasicComponentDefaults.titleColor(
                color = if (selected) scheme.onPrimaryVariant else scheme.onBackground,
            ),
            summary = if (duplicated) null else option.id,
            summaryColor = BasicComponentDefaults.summaryColor(
                color = if (selected) scheme.onPrimaryVariant else scheme.onSurfaceVariantSummary,
            ),
            role = Role.RadioButton,
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
            // insideMargin 不传：走 `BasicComponentDefaults.InsideMargin`（16dp），
            // 与官方示例给卡片写的 `PaddingValues(16.dp)` 是同一个值。
        )
    }
}

/**
 * 模型列表：每个模型一张独立卡片（不是一张大卡装多行）。
 *
 * 这里刻意**不用**上面那条"一张 Card 装多行"的官方分组写法：那种写法是给
 * **设置项分组**用的（组内各行只是并列，没有"哪一行被选中"的概念）。而这里每行
 * 都有选中态，选中态要表达成一整张变色卡片 —— 装在同一张卡里的话，蓝底只能在
 * 大卡内部画一块，四角与卡片圆角对不上（这正是更早一版用裸色块时的毛病）。
 */
@Composable
private fun ModelList(
    visible: List<ModelOption>,
    currentModel: String,
    search: String,
    onPick: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = PickerGroupSpacing)
            .heightIn(max = ModelListMaxHeight),
        verticalArrangement = Arrangement.spacedBy(ModelRowSpacing),
    ) {
        items(visible, key = { it.id }) { model ->
            ModelRow(
                option = model,
                selected = model.id == currentModel,
                onPick = { onPick(model.id) },
            )
        }
        if (visible.isEmpty()) {
            // 搜不到时给一句话，而不是留一片空白 —— 空白与"还在加载"、
            // "目录是空的"三种情况看起来一模一样。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = scheme.secondaryContainer),
                ) {
                    Text(
                        text = "没有匹配「$search」的模型",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BasicComponentDefaults.InsideMargin),
                    )
                }
            }
        }
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
        // 状态行走 Miuix 的分组小标题（`SmallTitle`）。它本来就是干这个的，
        // 之前用裸 `Text` + 自己挑颜色，正是不必要的自绘。
        ZhiSectionLabel(
            text = picker.status,
            textColor = if (picker.models.isEmpty() && !picker.loading) {
                scheme.onSurfaceVariantSummary
            } else {
                scheme.primary
            },
            insideMargin = PickerSectionInsideMargin,
        )

        if (picker.models.size > 1) {
            ModelSearchField(value = picker.search, onValueChange = onSearchChange)
        }

        // 过滤规则见 `ModelPickerState.visibleModels`（派生属性，可脱离 Compose 单测）。
        val visible = picker.visibleModels

        if (picker.models.isNotEmpty()) {
            // 分组标题在卡片**外面**（官方示例就是这样），文案里带上筛完的条数，
            // 这样搜索时能立刻看到"还剩几个"。
            //
            // ## 为什么这里没有折叠箭头、没有吸顶分组头、没有底部提供方跳转条
            //
            // rikkahub 的面板这三样都有。它们成立的前提是它支持**多个提供方**：
            // 分组头要能折叠是为了收起不看的那些提供方，跳转条是为了快速跳到某一个，
            // 吸顶是为了在长列表里始终知道自己在哪个提供方下面。
            //
            // 而这个面板**永远只有一组** —— 一个 API 配置（profile）就对应一个提供方
            // 与一份目录，`ModelPickerState` 里也只有单个 `profileName`。所以：
            // 折叠 = 把整个列表收起来（等于关掉面板），跳转条没有任何目标可跳，
            // 吸顶也没有第二种分组需要区分。**画一个点了没用的控件比不画更糟** ——
            // 用户会去点它，然后以为应用坏了。
            //
            // 哪天真的支持多提供方了，这三样才谈得上加；那时应当先改 ModelPickerState
            // 的数据模型（单个 profileName → 一组），而不是先画控件。
            ZhiSectionLabel(
                text = "${picker.profileName} · ${visible.size} 个模型",
                insideMargin = PickerSectionInsideMargin,
            )
            ModelList(
                visible = visible,
                currentModel = picker.currentModel,
                search = picker.search,
                onPick = onQueryChange,
            )
        }

        ZhiTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            label = "模型名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
