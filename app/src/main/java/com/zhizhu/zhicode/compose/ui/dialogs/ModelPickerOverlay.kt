package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
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
 * 目录里的一行：**布局照搬 rikkahub，组件仍是 Miuix 原生**。
 *
 * ## 几何直接借鉴 rikkahub 的 `ModelItem`
 *
 * 它也是 Compose（`androidx.compose.foundation` + `material3`），而 Miuix 建在同一套
 * Compose 原语上，所以**布局数字可以照搬**、只需要把颜色令牌换掉：
 *
 * | 项 | rikkahub | 这里 |
 * | --- | --- | --- |
 * | 行内边距 | 16dp 横 / 12dp 纵 | 同 |
 * | 头像与文字间距 | 12dp | 同（由 `startAction` 侧提供） |
 * | 头像 | `Surface` 内 32dp 内容 + 4dp 内边距（共 40dp） | 同 |
 * | 选中底色 | `primaryContainer` | `secondaryContainer`（见下） |
 *
 * ## 组件为什么仍用 `BasicComponent` 而不是照抄它的 `Row`
 *
 * rikkahub 那一段是自己拼 `Row` + `Column`，因为它还要塞能力标签的 `FlowRow`
 * （我们没有那类数据）。`BasicComponent` 是 Miuix 原生的"一行：前置槽 + 标题 +
 * 副标题 + 尾部槽"，**本来就带 `startAction`** —— 头像放进去刚好对上 rikkahub 的
 * "头像在左、文字在右"结构，同时保留 Miuix 的按压反馈与行高规则。
 * 照抄 `Row` 反而会丢掉这些，那才是"失了 Miuix 的原生框架"。
 *
 * ## 选中色为什么是 `secondaryContainer` 而不是 `primaryContainer`
 *
 * rikkahub 用的确实是 `primaryContainer`，但那是 Material3 的**动态取色**结果，
 * 在它的暗色截图里是低调的暗红/暗蓝。Miuix 的 `primaryContainer` 在暗色下是**高饱和蓝**，
 * 直接套用会得到"整行亮蓝、白字、很扎眼"——实测反馈就是"太亮"。
 * 所以换 `secondaryContainer`（同族的次级容器色，暗色下明显更收敛），
 * 前景相应换成 `onSecondaryContainer`。**要更醒目就把这两个令牌换回 primary 那对**，
 * 只改这两行。
 *
 * ## 为什么没有对勾
 *
 * rikkahub 的选中只靠整块底色表达，没有对勾。这里按它来；如果哪天觉得"
 * 一屏几十行里光靠底色不够醒目"，`endActions` 槽就是放对勾的位置。
 */
@Composable
private fun ModelRow(
    option: ModelOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val duplicated = option.displayName == option.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(0.dp),
        // ⚠️ 未选中用 `surfaceContainerHigh` 而不是 rikkahub 的 `surface`：
        // Miuix 里 `surface` 与面板底色同色，卡片会"消失"、行与行之间没有界线。
        colors = CardDefaults.defaultColors(
            color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        ),
    ) {
        BasicComponent(
            title = option.displayName,
            titleColor = BasicComponentDefaults.titleColor(
                color = if (selected) scheme.onSecondaryContainer else scheme.onBackground,
            ),
            summary = if (duplicated) null else option.id,
            summaryColor = BasicComponentDefaults.summaryColor(
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariantSummary,
            ),
            startAction = { ModelAvatar(option.id) },
            onClick = onPick,
            // 与 rikkahub 一致的 16 / 12dp。之前是 Miuix 默认（12 / 10dp），
            // 行显得挤；改这个是因为参考图的行明显更"透气"。
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 模型头像：首字方块。
 *
 * ## 几何与回落路径都照搬 rikkahub
 *
 * 它的 `AutoAIIcon` 先按名字查 `assets/icons/<name>.svg`，**查不到就回落到
 * `TextAvatar`** —— 一个 `secondaryContainer` 底、首字大写、自动缩字号的方块
 * （`UIAvatar.kt`）。我们没有任何品牌资产、也不该为此内置一批厂商 logo
 * （等于替各家做标识，还会过期），所以直接走它的**回落路径**：
 * 尺寸同样是「32dp 内容 + 4dp 内边距」，底色同样取自容器的次级色。
 *
 * ## 为什么圆角走 `ZhiRadius.inner`
 *
 * rikkahub 用的是 `MaterialTheme.shapes.small`，Miuix 没有对应的 shapes 别名，
 * 而我们自己的圆角令牌里 `inner`（10dp）就是这个层级 —— 换令牌、不换观感。
 */
@Composable
private fun ModelAvatar(modelId: String) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(ZhiRadius.inner),
        color = scheme.secondaryContainer,
    ) {
        Box(
            modifier = Modifier.padding(4.dp).size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // 首字大写，与 rikkahub 的 `text.take(1).uppercase()` 一致。
                // 空 id 理论上不会到这里（ModelCatalogClient 会丢弃空 id），
                // 但真遇到也不要画一个空白方块。
                text = modelId.trim().take(1).uppercase().ifEmpty { "?" },
                color = scheme.onSecondaryContainer,
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
