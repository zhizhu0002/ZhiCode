package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
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
 * | 纯数字选择 | [OverlaySpinnerPreference] |
 * | 布尔开关 | [SwitchPreference] |
 * | 入口（跳走做别的事） | [ArrowPreference] |
 * | 只读展示（色值） | [ArrowPreference] + `startAction` 色块 |
 * | 自由文本 | [BasicComponent] + `bottomAction` 里的 [TextField] |
 *
 * 这些组件内部都走 `BasicComponent`，所以标题/说明/按压态/圆角由 Miuix 统一负责。
 * 本文件只做一件事：把「蜘蛛 的语义」翻译成它们的参数。
 */

/** 分组标题。转发到 Miuix [SmallTitle]，强调色文字。 */
@Composable
internal fun SettingsGroupHeader(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier,
        textColor = MiuixTheme.colorScheme.primary,
    )
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
) {
    OverlayDropdownPreference(
        items = options,
        selectedIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
        onSelectedIndexChange = { index -> options.getOrNull(index)?.let { onSelect(index) } },
    )
}

/**
 * 数字型设置项，走 Miuix 滚轮选择器（[OverlaySpinnerPreference]）。
 *
 * [options] 是**候选值序列**（可为步长序列，如 5,10,…,60）；组件按下标工作，
 * 所以「步长 5」这种序列能被正确表达。
 * [options] 为空（如自动压缩已关闭）时整行不可点，只显示 [summary] 说明。
 */
@Composable
internal fun SettingsNumber(
    title: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
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
        maxHeight = 260.dp,
        enabled = enabled,
    )
}

/** 布尔开关。转发到 Miuix [SwitchPreference]（整行可点，右侧滑块）。 */
@Composable
internal fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    warn: Boolean = false,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        summaryColor = hintColors(warn),
    )
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
) {
    ArrowPreference(
        title = title,
        summary = summary ?: valueText,
        onClick = onClick,
    )
}

/**
 * 只读展示行（外观分类里的色值）。
 *
 * 用 [BasicComponent] 而不是 `ArrowPreference`：后者行尾**总会**画一个 `›`，
 * 即便 `onClick = null`。只读行带箭头会让人以为能点，所以这里换掉。
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
    /**
     * 是否单行。
     *
     * 默认单行（项目目录这类短值）；多行时给 [minLines] 一个可见的高度，
     * 否则空内容的多行框会塌成一条线，用户看不出那里能写字。
     */
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    BasicComponent(
        title = title,
        summary = summary,
        summaryColor = hintColors(warn = false),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        bottomAction = {
            ZhiTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
            )
        },
    )
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
