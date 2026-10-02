package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalWindowInfo
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.ui.ZhiLoadingIndicator
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSectionLabel
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
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
 * 对应原版 `showModelPanel()`：API 切换 tab 栏、状态行、异步填充的模型列表、
 * 手动输入框、底部「添加 API / 完成」。
 *
 * ## 为什么是底部 Sheet 而不是居中弹窗
 *
 * 这是**唯一**一个"内容是可变长列表 + 用户要边看边挑"的面板。居中弹窗在这个场景
 * 上有两个具体问题：
 *
 * 1. 它悬在屏幕中间，上方那条正在流式输出的回复被压掉一半 —— 而挑模型往往正是
 *    因为**看了正在生成的回复**才想换一个，这时最不该挡住的就是它；
 * 2. 手机竖屏下弹窗的可用高度比弹窗小（上下都要留边距），长列表更早触发滚动。
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
 * 这个面板前后改过五版，踩的坑有个共同点：**都是在"自己拼结构 + 自己挑颜色"**。
 * 每一处都有 Miuix 官方示例或组件可以直接照：
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
 * 直接照 Miuix 自己的 Card 示例（那个"ShowIndication: true"的蓝卡）：
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
    onPickModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onUse: (String) -> Unit,
    onAddApi: () -> Unit,
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
            onPickModel = onPickModel,
            onSelectProfile = onSelectProfile,
            onUse = onUse,
            onAddApi = onAddApi,
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
 * 面板高度策略。
 *
 * ## ⚠️ 窗口高度必须取 `LocalWindowInfo`，不能取 `BoxWithConstraints.maxHeight`
 *
 * 这一条是**实测**出来的，不是猜的：最初在面板内容外面套了一层 `BoxWithConstraints`，
 * 想用它的 `maxHeight` 算比例。结果是面板反而**变矮了**，而且那个 `heightIn(min = …)`
 * 完全没生效 —— 因为 sheet 测量内容时给的约束**不是屏幕高度**（它要先量一次内容才能
 * 决定自己多高，量的时候约束是松的/无穷大），拿它乘比例只会得到 0 或无穷。
 *
 * Miuix 自己在 `BottomSheetContentLayout` 里用的就是
 * `LocalWindowInfo.current.containerDpSize.height`（那正是它的布局数学所依据的值），
 * 这里跟随同一个来源。
 *
 * ## 空间怎么给
 *
 * `OverlayBottomSheet` **没有高度参数**（0.9.4 的签名里只有 `sheetMaxWidth`），
 * 高度完全由内容决定。所以"把 sheet 做高一点"= **把内容做高**：
 * - 列表上限按窗口高度取 [ModelListHeightFraction]，长目录能占满该占的地方；
 * - 加载态给 [LoadingHeightFraction] 的高度，否则加载圈会挤在一条缝里，
 *   而且"加载中很矮 → 加载完突然长高"会跳一下。
 *
 * 不给整块内容强加最小高度（那样会在按钮下面留一片空白，很难看）。
 */
private const val ModelListHeightFraction = 0.45f
private val ModelListMaxHeightCap = 480.dp
private const val LoadingHeightFraction = 0.3f
private val LoadingMinHeight = 140.dp

/**
 * API 切换 tab 栏（在模型名输入框的正上方）。
 *
 * 用既有的 [ZhiSegmentedTabs]（Miuix `TabRowWithContour` 的转发）：`matchWidth = false`
 * 时项数超出一屏会自动横向滚动 —— API 记录多了也放得下。
 *
 * ## 为什么只有多于一条时才画
 *
 * 只有一条时它是**死控件**：点了不会切到任何别的地方。这与下面"没有折叠箭头"是同一条
 * 判断标准（画一个点了没用的控件比不画更糟）。判断放在 `ModelPickerState.showProfileTabs`
 * 里，那样它可脱离 Compose 单测。
 */
@Composable
private fun ProfileTabs(
    picker: ModelPickerState,
    onSelectProfile: (String) -> Unit,
) {
    if (!picker.showProfileTabs) return
    ZhiSegmentedTabs(
        tabs = picker.profiles.map { it.name },
        selectedIndex = picker.activeProfileIndex,
        onSelect = { index ->
            // 越界保护：下标来自 Miuix 的回调，而 profiles 是状态，两者之间隔了一次重组。
            picker.profiles.getOrNull(index)?.let { onSelectProfile(it.id) }
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = PickerGroupSpacing),
    )
}

/**
 * 加载态：居中的加载圈 + 状态文字。
 *
 * 之前加载时只有顶部一行小字，列表位置是空的 —— 看起来像"面板坏了"。
 * 现在把圈和文字放在列表本来的位置居中，一眼就知道是在等东西。
 *
 * 高度按窗口高度给（理由见上面的常量注释），这样面板在加载时就有像样的高度，
 * 不会"加载中很矮 → 加载完突然长高"跳一下。
 */
@Composable
private fun PickerLoading(status: String) {
    val scheme = MiuixTheme.colorScheme
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (windowHeight * LoadingHeightFraction).coerceAtLeast(LoadingMinHeight)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ZhiLoadingIndicator()
            Text(
                text = status,
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
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
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    // `displayName` 与 `id` 相同时只显示一次，否则每行会重复两遍同一个名字
    // （服务端常常不给 display_name，那种情况下它会被回落成 id）。
    val duplicated = option.displayName == option.id

    // 三个颜色都走淡变，而不是硬切。
    //
    // 为什么三个都要：选中时**底色与文字是同时变**的，只淡化其中一个会出现
    // "字已经变浅蓝、底还是灰的"这种中间态，比不做动画更难看。
    // 用的是 [ZhiMotion.colorSpec]（150ms + SinOut），与 Miuix 弹窗的淡出同一条曲线 ——
    // 不是为了好看，而是为了**手感与 Miuix 组件一致**（这一点在 `Animations.kt` 里有完整说明）。
    val cardColor by animateColorAsState(
        targetValue = if (selected) scheme.primaryVariant else scheme.secondaryContainer,
        animationSpec = ZhiMotion.colorSpec,
        label = "modelRowCard",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryVariant else scheme.onBackground,
        animationSpec = ZhiMotion.colorSpec,
        label = "modelRowTitle",
    )
    val subColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryVariant else scheme.onSurfaceVariantSummary,
        animationSpec = ZhiMotion.colorSpec,
        label = "modelRowSummary",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        // 官方 Card 示例用的就是这两个令牌；`color` 必须显式传而不是靠默认值 ——
        // `CardDefaults` 默认的 `surfaceContainer` 与 sheet 背板 `background` 同值
        // （都是 `#242424`），不传的话未选中的卡片在背板上完全看不出来。
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        BasicComponent(
            title = option.displayName,
            titleColor = BasicComponentDefaults.titleColor(color = textColor),
            summary = if (duplicated) null else option.id,
            summaryColor = BasicComponentDefaults.summaryColor(color = subColor),
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
 * 这里刻意**不用**"一张 Card 装多行"的官方分组写法：那种写法是给**设置项分组**用的
 * （组内各行只是并列，没有"哪一行被选中"的概念）。而这里每行都有选中态，选中态要
 * 表达成一整张变色卡片 —— 装在同一张卡里的话，蓝底只能在大卡内部画一块，四角与卡片
 * 圆角对不上（这正是更早一版用裸色块时的毛病）。
 *
 * ## 高亮跟随**点击**而不是跟随已保存的值
 *
 * 判据是 `model.id == picker.query`，而 `query` 就是"要用的模型名"。点一行会立刻
 * 把 `query` 改过去（并同时存盘），所以点哪行哪行马上变蓝。
 *
 * 早先这里判的是 `picker.currentModel`（**已保存**的那个），于是必须点底部按钮
 * 之后高亮才动 —— 用户点了一行却看不到任何反馈，会以为没点上。
 */
@Composable
private fun ModelList(
    models: List<ModelOption>,
    picked: String,
    listMaxHeight: Dp,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = PickerGroupSpacing)
            // ⚠️ 高度必须**有界**：无界高度会让 LazyColumn 拿到 Infinity 约束直接崩
            // （DialogScrollNestingTest）。上限由调用方按窗口高度算好传进来。
            .heightIn(max = listMaxHeight),
        verticalArrangement = Arrangement.spacedBy(ModelRowSpacing),
    ) {
        items(models, key = { it.id }) { model ->
            ModelRow(
                option = model,
                selected = model.id == picked,
                onPick = { onPick(model.id) },
                // 目录重排（切了 API、或服务端顺序变了）时让行**滑过去**而不是瞬移。
                // 用 key 才有意义（上面 items 已给 key = id），否则 Compose 认不出
                // "还是那一行、只是位置变了"，会当成整批新建。
                modifier = Modifier.animateItem(),
            )
        }
        // 这里原本还有一个"没有匹配「…」的模型"的空态：它是给**搜索**用的，
        // 而调用方保证了 `models` 非空，所以搜索一去掉它就是一段死分支。
        // "目录是空的"那种情况由上面的状态行负责说明（见 PickerLoading / status）。
    }
}

@Composable
private fun ModelPickerLoaded(
    picker: ModelPickerState,
    listMaxHeight: Dp,
    onPickModel: (String) -> Unit,
) {
    // 列表直接铺 `models`。这里曾经有一层搜索过滤（`visibleModels`），已按用户要求
    // 去掉；去掉之后 `search` / `visibleModels` / `setModelSearch` 就是死代码，
    // 一并删除，不留"没人用但还留着"的字段。
    if (picker.models.isEmpty()) return

    // 分组标题在卡片**外面**（官方示例就是这样）。
    //
    // ## 为什么这里没有折叠箭头、没有吸顶分组头、没有底部提供方跳转条
    //
    // rikkahub 的面板这三样都有。它们成立的前提是它支持**多个提供方**：
    // 分组头要能折叠是为了收起不看的那些提供方，跳转条是为了快速跳到某一个，
    // 吸顶是为了在长列表里始终知道自己在哪个提供方下面。
    //
    // 而这个面板的每一份 API 记录**各自**就是一组（上面的 tab 栏负责在
    // 它们之间切换），切进来的这一组永远只有一组：折叠 = 把列表收起来，
    // 跳转条没有任何目标可跳，吸顶也没有第二种分组要区分。
    // **画一个点了没用的控件比不画更糟** —— 用户会去点它，然后以为应用坏了。
    ZhiSectionLabel(
        text = "${picker.profileName} · ${picker.models.size} 个模型",
        insideMargin = PickerSectionInsideMargin,
    )
    ModelList(
        models = picker.models,
        picked = picker.query,
        listMaxHeight = listMaxHeight,
        onPick = onPickModel,
    )
}

@Composable
private fun ModelPickerBody(
    picker: ModelPickerState,
    onQueryChange: (String) -> Unit,
    onPickModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onUse: (String) -> Unit,
    onAddApi: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme

    // 面板高度完全由内容决定（sheet 没有高度参数），所以"做高一点"只能靠给列表更多空间。
    // ⚠️ 窗口高度取 LocalWindowInfo（Miuix 自己的布局数学也用这个），
    // 不能用 BoxWithConstraints 的 maxHeight —— 实测那一个是无效的，见上面的常量注释。
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val listMaxHeight = (windowHeight * ModelListHeightFraction).coerceAtMost(ModelListMaxHeightCap)

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

        // 加载态与列表之间淡变过渡。之前是硬切：目录一回来，一整块内容"啪"地换掉 ——
        // 而这两块**高度不同**，所以看起来既是变色又是跳高，这就是"生硬"的来源。
        //
        // 只把这一块包进 AnimatedContent（下面的 tab 栏、输入框、按钮都留在外面）：
        // 外面那些在两种状态下是同一批控件，让它们跟着淡变反而会闪。
        AnimatedContent(
            targetState = picker.loading,
            transitionSpec = {
                fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
            },
            label = "modelPickerContent",
        ) { loading ->
            if (loading) {
                PickerLoading(status = picker.status)
            } else {
                ModelPickerLoaded(
                    picker = picker,
                    listMaxHeight = listMaxHeight,
                    onPickModel = onPickModel,
                )
            }
        }

        // tab 栏在模型名输入框的**正上方**：换一份 API 记录，下面那个框里的模型名
        // 通常也要跟着换，两者挨着才看得出关系。
        ProfileTabs(picker = picker, onSelectProfile = onSelectProfile)

        ZhiTextField(
            value = picker.query,
            onValueChange = onQueryChange,
            label = "模型名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = PickerGroupSpacing),
        )

        // 这里原本有一行说明文字（"点列表里的模型会立刻选中并保存…"），已按用户要求去掉。
        // 交互本身已经是自解释的：点一行立刻变蓝并落盘，按钮只剩「完成」。

        // 按钮照旧靠右，与居中弹窗时的位置一致：换容器不该顺手改按钮的排布，
        // 否则用户得重新找一遍主按钮在哪。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(text = "添加 API", onClick = onAddApi)
            PrimaryButton(
                // 选择已经在点击那一刻存好了，这个按钮只是"收起面板"。
                // 文案因此是「完成」而不是「使用模型」—— 后者会让人以为不点它就不生效。
                // 它同时也是**手动输入**那条路的重试点：在框里改了名字后点它就写回配置。
                text = "完成",
                enabled = picker.query.isNotBlank(),
                onClick = { onUse(picker.query) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
