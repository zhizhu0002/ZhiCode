package com.zhizhu.zhicode.compose.ui.dialogs
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.theme.ZhiRadius

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 所有 Miuix [OverlayDialog] 共用的外壳、按钮与紧凑文字样式。
 *
 * 抽到单独文件是因为设置页（`ui/settings/SettingsDialog.kt`）也要用同一套外壳；
 * 放在 `Dialogs.kt` 里会因为 `private` 而无法复用，而重复一份必然走样。
 *
 * 这些常量和函数是 `internal` 而非 `private`：只在本 module 内共享，
 * 不对外暴露。
 */

/**
 * 把弹窗撑大。
 *
 * Miuix `DialogDefaults` 的边距很保守（实测 `outsideMargin` = 12dp × 12dp、
 * `insideMargin` = 24dp × 24dp），在 360dp 宽的手机上窗口只剩 336dp、
 * 内容区再被吃掉 48dp，实际可用宽度只有 288dp。
 * 这里把两侧边距各收掉一些，窗口与内容区都明显变宽。
 *
 * **横向 6dp 是走过头了**：在 450dpi 手机上只有 17px，弹窗几乎左右顶到屏幕边，
 * 看起来不像弹窗而像整屏页面。18dp 是常规弹窗内缩（Material 系默认 16~24dp），
 * 既留出与屏幕的呼吸感，又比 Miuix 默认宽了 6dp。
 * 纵向保持 12dp：手机竖屏下高度本来就紧张，多留白会把内容区挤没。
 */
internal val DialogWideOutsideMargin = DpSize(18.dp, 12.dp)
internal val DialogWideInsideMargin = DpSize(14.dp, 14.dp)

/**
 * 弹窗内容的外壳：左对齐标题 + 固定说明 + 中间内容区 + 底部固定按钮区。
 *
 * 这里以前还嵌套了一层 `MiuixTheme`，用一套弹窗专用的紧凑字阶把字号整体收小
 * （当时 Miuix 默认 `main` = 17sp，弹窗里要压到 14sp）。字阶现在已在根级统一
 * （见 `theme/ZhiTextStyles.kt`），那一层的作用就**反转**了 —— 它会把弹窗文字
 * 顶回 14sp，比主界面的 13sp 还大。所以整层连同那个函数一起删掉，弹窗直接继承
 * 根字阶。（它当时传的 `colors = MiuixTheme.colorScheme` 本来就是恒等的。）
 *
 * [groupBody] 为真时，中间内容区会被包进一层 Miuix [Card]。
 *
 * 这一层以前是手写的：传 `contentBackdrop = Color.Black` 再自己套 `Surface`。
 * 理由是深色方案的 `background` 与 `surfaceContainerHigh` 都是 `#242424`，
 * 弹窗内的卡片与弹窗底色同色、看不出边界，所以把内容区压成纯黑。
 * 但**纯黑在浅色模式下直接坏掉**（白底弹窗里一块黑板）。
 *
 * 官方做法就是在 `Card` 里放设置行（见 Miuix 官方的 `SwitchPreferenceDemo`），
 * 所以改成用 [Card]：它的配色由主题给出，深浅两套都自带与弹窗底色的对比度。
 *
 * [prompt] 是**固定不滚动**的说明文字，刻意放在底板**外面**：
 * 它是提问语境，不属于可选项列表，不应该跟着选项一起滚动，也不该被框进底板里。
 * [footer] 同理，用于**不随内容滚动**的输入控件。
 *
 * [header] 用于标题下方的整块自定义头部（设置页的分类 Tab 行走这里），
 * 同样固定不滚动。
 *
 * [fillBody] 为真时中间区**撑满**剩余高度（`weight(fill = true)`），否则按内容自适应。
 * 设置页需要撑满：参考图里内容区是固定满高的（最后一项下面留大片空白），
 * 而且这样滚动视口高度确定，滚动位置不会出现"内容顶到面板上边缘"的错觉。
 */
@Composable
internal fun DialogShell(
    title: String,
    titleColor: Color = MiuixTheme.colorScheme.onBackground,
    titleAction: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    prompt: (@Composable () -> Unit)? = null,
    /** 把中间内容区包进 Miuix [Card]，用于设置/选项这类需要卡片分组的弹窗。 */
    groupBody: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    fillBody: Boolean = false,
    actions: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // 标题行：标题靠左吃剩余宽度，右侧留一个可选动作位（设置页放 × 关闭）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = ZhiTextScale.TitleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            if (titleAction != null) titleAction()
        }

        if (header != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { header() }
        }

        if (prompt != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { prompt() }
        }

        // 中间区域吃掉"剩余高度"（weight(fill = false)），并内部滚动。
        //
        // 这里**不能**写死 `heightIn(max = ...)`：弹窗总高还要加上标题、
        // 说明、底部输入框和按钮，固定值一叠加就会超出屏幕，把按钮挤出可视区。
        // weight 让滚动区自动收缩到真正剩下的空间，所以无论哪一层多高，
        // 按钮区始终留在屏幕内。
        val middleModifier = Modifier.fillMaxWidth().weight(1f, fill = fillBody)

        val scrollableBody: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                body()
            }
        }

        if (!groupBody) {
            Column(modifier = middleModifier.padding(top = 8.dp)) { scrollableBody() }
        } else {
            // 显式给一层比弹窗底更明显的容器色：Miuix 的浅色方案里
            // `background` 与 `surfaceContainer` **都是 #FFFFFF**，
            // 用默认卡片色会与弹窗底完全同色、分组看不出来。
            Card(
                modifier = middleModifier.padding(top = 10.dp),
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    scrollableBody()
                }
            }
        }

        if (footer != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { footer() }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = { actions() },
        )
    }
}

/**
 * 高亮的"确定"类按钮：主色实心，对应参考图的「提交」「批准并开始」「允许」。
 *
 * `enabled = false` 时 Miuix 会换成 `disabledPrimaryButton` 配色，
 * 用来表达"还不能提交"（例如选择窗口里一个选项都没选）。
 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        cornerRadius = ZhiRadius.button,
        colors = ButtonDefaults.buttonColorsPrimary(
            color = scheme.primary,
            contentColor = scheme.onPrimary,
        ),
        modifier = modifier,
    ) {
        Text(text = text, fontSize = ZhiTextScale.BodySmall)
    }
}

/**
 * 次要按钮：直接走 Miuix [TextButton] 的**默认配色**
 * （填充 `secondaryVariant`、文字 `onSecondaryVariant`，即深色方案下的
 * `#434343` 灰底 + `#D9D9D9` 文字），与 [PrimaryButton] 是同一套尺寸，
 * 只有配色不同。
 *
 * 之前这里传 `color = Color.Transparent` 做成纯文字按钮，是想贴合参考图，
 * 但结果两个按钮观感完全不统一（一个像按钮、一个像标签），所以改回默认配色。
 * 也不要自己挑 `surfaceContainerHigh` 之类的颜色：Miuix 的默认值就是为
 * 这套调色板配好的，手挑容易在动态取色下失配。
 */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        text = text,
        onClick = onClick,
        cornerRadius = ZhiRadius.button,
        modifier = modifier,
    )
}
