package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的行式组件，**全部是 `miuix-preference` 组件的薄转发**。
 *
 * 这里以前是一整套手写的东西：`Row + Text + Surface` 拼出来的「标签在上、整宽值框在下」
 * 布局，外加自建的内联下拉和 `NumberPicker`。手写的结果是这套行和 Miuix 自己的
 * `*Preference` 系列观感、按压反馈、无障碍 role 都不一致。
 *
 * 现在改成直接采用 Miuix 的设置行（左标题 + 右控件 + 下方 summary 说明）：
 *
 * | 设置项类型 | 采用的 Miuix 组件 |
 * |---|---|
 * | 枚举 / 选项选择 | [OverlayDropdownPreference] |
 * | 数值滑杆 | [SliderPreference]（有固定整数范围的设置项） |
 * | 自由数值输入 | [BasicComponent] + `bottomAction` 里的 [TextField]（见 [SettingsIntField]） |
 * | 纯数字滚轮 | [OverlaySpinnerPreference]（设置页已不用，只剩调试页的组件陈列） |
 * | 布尔开关 | [SwitchPreference] |
 * | 入口（跳走做别的事） | [ArrowPreference] |
 * | 只读展示（色值） | [ArrowPreference] + `startAction` 色块 |
 * | 自由文本 | [BasicComponent] + `bottomAction` 里的 [TextField] |
 *
 * 这些组件内部都走 `BasicComponent`，所以标题/说明/按压态/圆角由 Miuix 统一负责。
 * 本文件只做一件事：把「ZhiCode 的语义」翻译成它们的参数。
 *
 * ## 行首图标：行式组件都收 `icon` + `plate` 一对参数
 *
 * 每一行都可以带一块行首图标（见 [SettingsIconPlate] 与 [SettingsPlateColors]），
 * 这是「借鉴小米自带的设置」那一条的落点：参考物里每一行的行首都是一块彩色圆角方块，
 * 而不是一个裸字形。
 *
 * 之所以做成**两个成对的参数**而不是一个 `startAction: @Composable` 槽位：
 * 槽位形式下"这一行该有图标"这件事没有任何地方记录，漏掉一行也看不出来；
 * 成对的 `icon` + `plate` 可以被 `SettingsIconPlateTest` 逐行数出来 ——
 * 「有图标没底色」（退回改版前那个裸单色字形）与「有底色没图标」（一块空色块）
 * 都能在守卫里被拦住。
 */

/**
 * 分组 = [SettingsGroupHeader] 标题 + 圆角 [Card]，间距照官方 `SettingsPage`：
 * 标题与卡片各自水平 12dp，卡片之间靠标题的高度自然留白。
 *
 * 一张卡里放多行 preference（行间**不画分隔线**）是 Miuix 官方 example 的写法，
 * 也是 rikkahub 的 CardGroup 观感 —— 每行各套一张卡会让整页碎成一堆便签。
 * 主页与五个二级页共用这一个分组原语。
 *
 * [horizontalPadding] 供**已经自己留了页边距**的页面传 0：沙箱页的 `LazyColumn`
 * 上挂着整页的 12dp 内边距，分组再各加一次就变成 24dp。默认值不变，老调用点无感。
 */
@Composable
internal fun SettingsGroup(
    title: String,
    horizontalPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    SettingsGroupHeader(title, modifier = Modifier.padding(horizontal = horizontalPadding))
    Card(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

/** 分组标题。转发到 Miuix [SmallTitle]，强调色文字。 */
@Composable
internal fun SettingsGroupHeader(text: String, modifier: Modifier = Modifier) {
    // 官方 SettingsPage 的组标题用 SmallTitle 默认色（onBackgroundVariant），
    // 强调色整页刷满反而让"危险项"的 warn 色失去辨识度。
    SmallTitle(text = text, modifier = modifier)
}

/**
 * 枚举 / 选项型设置项。
 *
 * [options] 是候选文本，[selectedIndex] 是当前项；Miuix 会在行尾显示当前值并带下拉箭头，
 * 点整行弹出选择列表。`summary` 对应原来手写的「框下说明」，[warn] 为真时用琥珀色
 * （Magisk/Root 那两项的风险提示）。
 */
@Composable
internal fun SettingsChoice(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    summary: String? = null,
    warn: Boolean = false,
    icon: Painter? = null,
    plate: Color? = null,
) {
    OverlayDropdownPreference(
        items = options,
        selectedIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
        startAction = plateStartAction(icon, plate),
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let { onSelect(index) } },
    )
}

/**
 * 数字型设置项，走 Miuix 滚轮选择器（[OverlaySpinnerPreference]）。
 *
 * [options] 是**候选值序列**（可为步长序列，如 5,10,…,60）；组件按下标工作，
 * 所以「步长 5」这种序列能被正确表达。
 * [options] 为空时整行不可点，只显示 [summary] 说明。
 *
 * 设置页的数值项已经换成 [SettingsIntField]（滚轮只能挑枚举出来的候选值，
 * 用户想要别的值就只能改代码）。这里留着是给调试页的组件陈列用。
 */
@Composable
internal fun SettingsNumber(
    title: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    plate: Color? = null,
) {
    val enabled = options.isNotEmpty()
    OverlaySpinnerPreference(
        items = options.map { index ->
            DropdownItem(
                text = index.toString(),
                selected = index == value,
                onClick = { onValueChange(index) },
            )
        },
        selectedIndex = options.indexOf(value).coerceAtLeast(0),
        title = title,
        summary = summary,
        summaryColor = hintColors(warn = false),
        startAction = plateStartAction(icon, plate),
        maxHeight = 260.dp,
        enabled = enabled,
    )
}

/**
 * 数值型设置项（自由输入）。
 *
 * 用于没有适合滑杆固定范围、需要自由输入的数值项（例如上下文窗口）。有限整数范围
 * 且适合连续逐值调整的选项应优先使用 Miuix [SliderPreference]，不要在这里造输入框。
 *
 * ## 输入过程中**绝不回写**文本框
 *
 * 直觉做法是"发现越界就立刻把文本改成合法值"，但那样最小值为 5 的项根本没法输入：
 * 打 `15` 的第一个字符 `1` 会被判成越界、clamp 成 `5`、文本被改成 `5`，
 * 接着那个 `5` 就变成 `55`。最小值越大（压缩上限是 50）越离谱。
 *
 * 所以分两步：
 *
 * 1. **输入时**：解析不出来（`1.`、空串、超 Int 范围）就只回显、不提交；
 *    能解析就按边界 clamp 后提交，但**文本框保持用户打进去的样子**。
 * 2. **失焦时**：把文本框收敛成真实值。这一步才是"屏幕上那个数字 == 真实值"
 *    的保证 —— 用户打了 `999`（范围 1-10）会看到它变成 `10`。
 *
 * 单纯只做第 1 步的话，屏幕上会留着一个跟真实值不一致的数（`999` 而实际是 10），
 * 用户会以为 999 生效了 —— 那比不画这个控件更糟。所以第 2 步不能省。
 */
@Composable
internal fun SettingsIntField(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    parse: (String) -> Int?,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    /** 显示用的写法。上下文窗口要显示成 `200k` 而不是 `200000`。 */
    format: (Int) -> String = { it.toString() },
    icon: Painter? = null,
    plate: Color? = null,
) {
    // 文本框自己持有一份文本：真实值是被 clamp 过的，而用户打进来的字可能还没成型。
    var text by remember { mutableStateOf(format(value)) }

    SettingsTextField(
        title = title,
        value = text,
        // 失焦时收敛。`value` 在每次重组时重新捕获，所以这里拿到的是**最新**的真实值。
        modifier = Modifier.onFocusChanged { focus ->
            if (!focus.isFocused) text = format(value)
        },
        onValueChange = { raw ->
            // 一律先如实回显用户打的字；输入过程中**绝不**改写文本框（见上面的说明）。
            text = raw
            // 解析不出来（空串、`1.`、超 Int 范围）就是不提交：等用户打完，或失焦时收敛。
            parse(raw)?.let { onValueChange(it.coerceIn(min, max)) }
        },
        summary = summary,
        icon = icon,
        plate = plate,
    )
}

/**
 * 布尔开关。转发到 Miuix [SwitchPreference]（右侧滑块）。
 *
 * ## 整行是否可点，由 Miuix 决定，本工程不加参数
 *
 * 曾经考虑过加一个 `rowClickable` 让整行都能切换。核实之后放弃：
 * `switchpreference.md` 的变更日志里有一条 **"Remove `onClick` from some composite
 * components"** —— Miuix 明确把 `onClick` 从 `SwitchPreference` 这类组合组件上**删掉**了。
 * 它现在只收 `onCheckedChange` 与 `holdDownState`，点击语义由组件内部统一处理。
 *
 * 所以这里**不能**再传 `onClick`（参数不存在，传了编译就过不去），
 * 也不该在行外面套一层 `Modifier.clickable` —— 那会与组件内部的交互源重复，
 * 得到"点一次切换两次"或按压态错乱。整行点不点得动是库的行为，
 * 本工程只负责把 `onCheckedChange` 接对。
 */
@Composable
internal fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    warn: Boolean = false,
    /**
     * 是否可用。
     *
     * 不可用时 Miuix 会把整行压成"点不动"的观感（滑块也会变灰），**不要再自己吞掉点击**
     * 当兜底 —— 那会得到"看着能点、点了没反应"，比一个明显的灰开关更难查。
     */
    enabled: Boolean = true,
    icon: Painter? = null,
    plate: Color? = null,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
        startAction = plateStartAction(icon, plate),
        enabled = enabled,
    )
}

/**
 * 数值胶囊：行尾那枚显示当前值的圆角小标签。
 *
 * 借鉴 rikkahub 的 `CardGroupValue`，但**形状与配色走 Miuix 语义色**，
 * 不搬它的 `MaterialTheme.colorScheme.secondaryContainer`（那是 M3 的）。
 *
 * ## 为什么必须等宽数字
 *
 * 拖动滑块时值一直在变（`85%` → `100%`）。比例数字默认不等宽，`1` 比 `0` 窄，
 * 于是胶囊会随数值**左右抖**——滑杆的拇指在动、旁边那个数字的宽度也在动，
 * 两处都在动会显得界面不稳。
 *
 * `fontFeatureSettings = "tnum"`（tabular numbers）让每个数字占同样宽度，
 * 胶囊宽度只随**字符个数**变化（`9%` → `10%` 会变一次，这是对的，也应该变）。
 *
 * 单位跟随小数点的写法不在这里处理：调用方给什么字符串就显示什么，
 * 格式化属于业务（`formatUiScale` 之类），这一层只负责"画一枚值"。
 */
@Composable
internal fun SettingsValuePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            // `tnum` 在 Miuix 的 Text 上通过 TextStyle 的 fontFeatureSettings 传下去。
            style = androidx.compose.ui.text.TextStyle(
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 滑杆行：标题在上一行、滑杆在下一行，右侧是当前值胶囊。
 *
 * ## 为什么用 `ArrowPreference` 的 `bottomAction`
 *
 * Miuix 的 `SliderPreference` 是把滑杆**画在行内**的一条紧凑控件：
 * 标题、说明、滑杆挤在同一行的垂直空间里，可点面积小、拖动时也容易误触整行。
 *
 * 官方给的扩展点是 `bottomAction` 槽位（`BasicComponent` 与 `ArrowPreference` 都有），
 * 语义正是"标题行下方接一块自定义内容"。ksu-style 的主题页缩放项就是这么写的：
 * 标题行走 `ArrowPreference`（自带 `›`、整行可点开弹窗），滑杆占据下方整行宽度。
 *
 * ## `onClick` 可以是 null
 *
 * 主题页那处点标题会打开 `ScaleDialog`（精确输入），所以传 `onClick`；
 * 但绝大多数滑杆行没有"第二层"，那时给 `null` —— 但 `ArrowPreference` 会**照画箭头**，
 * 所以无 `onClick` 时改用 `BasicComponent`（没有箭头），避免"有箭头却点不动"。
 */
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    summary: String? = null,
    steps: Int = 0,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    magnetThreshold: Float = 0.02f,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    icon: Painter? = null,
    plate: Color? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val endActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        SettingsValuePill(text = valueText)
    }
    val bottom: @Composable () -> Unit = {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            showKeyPoints = showKeyPoints,
            keyPoints = keyPoints,
            magnetThreshold = magnetThreshold,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (onClick != null) {
        ArrowPreference(
            title = title,
            summary = summary,
            summaryColor = hintColors(warn = false),
            startAction = plateStartAction(icon, plate),
            endActions = endActions,
            bottomAction = bottom,
            onClick = onClick,
            holdDownState = holdDownState,
        )
    } else {
        BasicComponent(
            title = title,
            titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
            summary = summary,
            summaryColor = hintColors(warn = false),
            startAction = plateStartAction(icon, plate),
            endActions = endActions,
            bottomAction = bottom,
        )
    }
}

/**
 * 入口型设置项（点后离开设置页去做别的事）。
 *
 * 转发到 Miuix [ArrowPreference]：行尾自带 `›`，不再手写那个字符。
 * [valueText] 作为 summary，对应参考图里「标签在上、值框里是动作名」。
 */
@Composable
internal fun SettingsEntry(
    title: String,
    valueText: String,
    onClick: () -> Unit,
    summary: String? = null,
    icon: Painter? = null,
    plate: Color? = null,
) {
    ArrowPreference(
        title = title,
        summary = summary ?: valueText,
        startAction = plateStartAction(icon, plate),
        onClick = onClick,
    )
}

/**
 * 只读展示行（外观分类里的色值）。
 *
 * 用 [BasicComponent] 而不是 `ArrowPreference`：后者行尾**总会**画一个 `›`，
 * 即便 `onClick = null`。只读行带箭头会让人以为能点，所以这里换掉。
 *
 * 这一行的行首是 [swatch] 色块本身，不是 [SettingsIconPlate]：色值行的行首就该是那个颜色，
 * 再套一层彩色方块反而把要展示的颜色盖掉。所以它不收 `icon` / `plate`。
 */
@Composable
internal fun SettingsReadOnly(
    title: String,
    valueText: String,
    swatch: Color? = null,
) {
    val scheme = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        summary = valueText,
        titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
        summaryColor = hintColors(warn = false),
        startAction = swatch?.let {
            {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = RoundedCornerShape(ZhiRadius.inner),
                    color = it,
                    contentColor = scheme.onBackground,
                    content = {},
                )
            }
        },
    )
}

/**
 * 自由文本设置项（项目目录）。
 *
 * Miuix 没有「偏好行里塞输入框」的现成组件，但它给了 [BasicComponent] 的 `bottomAction`
 * 槽位，这就是官方的扩展点：标题/说明仍由组件负责，输入框只占底部一行。
 */
@Composable
internal fun SettingsTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null,
    modifier: Modifier = Modifier,
    /**
     * 是否单行。
     *
     * 默认单行（项目目录这类短值）；多行时给 [minLines] 一个可见的高度，
     * 否则空内容的多行框会塌成一条线，用户看不出那里能写字。
     */
    singleLine: Boolean = true,
    minLines: Int = 1,
    icon: Painter? = null,
    plate: Color? = null,
) {
    BasicComponent(
        title = title,
        summary = summary,
        summaryColor = hintColors(warn = false),
        startAction = plateStartAction(icon, plate),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        bottomAction = {
            ZhiTextField(
                value = value,
                onValueChange = onValueChange,
                // [modifier] 下发给 ZhiTextField 的输入框本体，
                // 调用点靠它挂 `onFocusChanged`（数值项失焦时要把文本收敛成真实值）。
                modifier = modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
            )
        },
    )
}

/**
 * 行首图标块的 `startAction` 槽位。
 *
 * `icon` 与 `plate` **必须成对**给出（见文件顶部）。只给一半时这里返回 `null`，
 * 界面会静默退回"没有图标的行" —— 这种退一半的情况由 `SettingsIconPlateTest`
 * 逐行拦（它数的是调用点上的参数，不是这里的返回值），所以这里不做任何补救。
 */
@Composable
private fun plateStartAction(icon: Painter?, plate: Color?): (@Composable () -> Unit)? {
    if (icon == null || plate == null) return null
    return { SettingsIconPlate(icon = icon, color = plate) }
}

/** 说明文字的颜色：[warn] 为真时用琥珀色，其余走 Miuix 的次级色。 */
@Composable
private fun hintColors(warn: Boolean) = BasicComponentDefaults.summaryColor(
    color = if (warn) ZhiColors.amber() else MiuixTheme.colorScheme.onSurfaceVariantSummary,
)

/** 供外观页脚注使用的纯说明文本（不属于任何设置行）。 */
@Composable
internal fun SettingsFootnote(text: String, modifier: Modifier = Modifier) {
    BasicComponent(
        modifier = modifier,
        title = "",
        summary = text,
        summaryColor = hintColors(warn = false),
        startAction = null,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    )
}
