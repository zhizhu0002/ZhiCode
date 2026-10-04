package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 终端面板的外壳：工具栏、会话抽屉、两行扩展键，以及「环境未就绪 / PTY 起不来」两种占位。
 *
 * <h3>它替换掉了什么</h3>
 * 这些以前全是 Java 手拼 `LinearLayout`（`TermuxTerminalPane` 里六百多行的界面代码）。
 * 现在由 Compose 画，PTY 与 Termux 上游 `TerminalView` 仍留在
 * [TermuxTerminalPane]（它作为 `AndroidView` 被挂在本文件中间那一格）。
 *
 * <h3>配色</h3>
 * 终端**外壳**（工具栏 / 会话抽屉 / 扩展键 / 两个占位）的配色全部取自应用主题
 * （[chrome] 用的是 MiuixTheme + [ZhiColors]），所以外壳本来就跟随深浅色。
 *
 * ⚠️ 这里以前写着"刻意沿用原来那一套、直接用 Miuix 主题色会让深色档也跟着变" ——
 * 那句话说的是改动之前的计划，**与现在的实现已经不符**（[chrome] 早就整张走主题色了）。
 * 留着它的代价是下一个人会照着它去"恢复旧色表"。
 *
 * 真正不适配深浅色的**不是外壳，是终端正文**：ANSI 调色板原先写死深色档，
 * 所以浅色模式下终端里还是黑底白字。那一处在宿主 [TermuxTerminalPane]
 * （见它的 `applyTerminalPalette` / `setDarkTheme`），不在本文件。
 * 判定深浅统一用 [ZhiColors.isDark]，它读 `LocalZhiDark`，与全应用一致。
 */

/** 终端外壳的一套配色。取值来自原 `applyPaletteValues` 的深/浅两档。 */
private class Chrome(
    val bar: Color,
    val keyBg: Color,
    val drawer: Color,
    val divider: Color,
    val selected: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val error: Color,
)

@Composable
private fun chrome(): Chrome {
    val scheme = MiuixTheme.colorScheme
    return Chrome(
        bar = scheme.surface,
        keyBg = ZhiColors.cardSurface(),
        drawer = ZhiColors.panelSurface(),
        divider = scheme.dividerLine,
        selected = ZhiColors.cardInnerSurface(),
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariantSummary,
        accent = scheme.primary,
        error = ZhiColors.red(),
    )
}

/** 抽屉宽度。与原实现的 286dp 一致。 */
internal val TerminalDrawerWidth = 286.dp

/** 抽屉背后遮罩的不透明度。原实现是 `Color.argb(150, 0, 0, 0)`。 */
private const val ScrimAlpha = 150f / 255f

/**
 * 把一个 [TermuxTerminalPane] 的状态接成 Compose 可观察的值。
 *
 * 宿主是 Java，用的是「快照 + 回调」而不是 `StateFlow`（理由见宿主类注释）；
 * 这里把回调折成一个 [State]，这样调用点写起来和别的 Compose 状态没有区别。
 *
 * 进入组合时先**主动读一次**：宿主的合法状态变化都可能发生在登记回调之前
 * （例如构造时就起了第一个会话），不读这一次会显示一份空快照。
 */
@Composable
internal fun rememberTerminalState(host: TermuxTerminalPane): State<TermuxTerminalPane.State> {
    val holder: MutableState<TermuxTerminalPane.State> = remember(host) { mutableStateOf(host.state()) }
    DisposableEffect(host) {
        val observer = Runnable { holder.value = host.state() }
        host.addObserver(observer)
        holder.value = host.state()
        onDispose { host.removeObserver(observer) }
    }
    return holder
}

/** 与原来一样的短提示。 */
internal fun toast(context: Context, message: String?) {
    if (message.isNullOrEmpty()) return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * 终端面板头部右侧的三个动作：☰ 会话列表、⌨ 切键盘、⋮ 快捷动作。
 *
 * <h3>它替换掉了什么</h3>
 * 原来这里是一个**整条工具栏**（`TerminalToolbar`，硬编码 42dp 高，自带背景色），
 * 它被画在 [PaneHeader] 的**下面** —— 于是切到终端时顶部有两行标题
 * （「终端 · 项目名」+「☰ 会话标题 ⌨ ⋮」），而文件面板只有一行。
 * 三个面板的头部形态不一致，且那 42dp 是手写死的（工程里的统一行高是
 * [com.zhizhu.zhicode.compose.theme.ZhiRow.height]）。
 *
 * 现在只留一个 [PaneHeader]：面板标题与副标题（当前会话名）由它统一负责，
 * 这三个动作走它的 `actions` 槽。行高、分隔线、左右留白因此与另两个面板逐像素同款。
 */
@Composable
internal fun RowScope.TerminalHeaderActions(
    onMenu: () -> Unit,
    onKeyboard: () -> Unit,
    onMore: () -> Unit,
) {
    val palette = chrome()
    ZhiIconButton(
        icon = ZhiIcons.menu,
        description = "会话列表",
        onClick = onMenu,
        tint = palette.text,
        compact = 30.dp,
        iconSize = 16.dp,
    )
    GlyphButton(glyph = "⌨", color = palette.text, onClick = onKeyboard)
    ZhiIconButton(
        icon = ZhiIcons.moreVert,
        description = "更多操作",
        onClick = onMore,
        tint = palette.text,
        compact = 30.dp,
        iconSize = 16.dp,
    )
}

/**
 * 文字字形按钮（⌨ 这类 Miuix 图标库里没有的键）。
 *
 * 走 Miuix [IconButton] 而不是自己拼 `Box + clickable`：按压反馈由组件负责，
 * 与工具栏上另外两个图标按钮一致。字形本身就是它可被读到的文案
 * （`dump_ui` 里能直接看到 ⌨），不需要额外的 contentDescription。
 */
@Composable
private fun GlyphButton(
    glyph: String,
    color: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        backgroundColor = Color.Transparent,
        cornerRadius = 10.dp,
        minHeight = 34.dp,
        minWidth = 34.dp,
    ) {
        Text(
            text = glyph,
            color = color,
            fontSize = ZhiTextScale.Heading,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 两行扩展键。
 *
 * 高度按原实现随横竖屏切换（68/82dp）。键盘弹起时整块被系统 resize 推上去，
 * 不需要自己算偏移 —— 原实现那个 `setKeyboardOffset` 其实没有任何调用方。
 */
@Composable
internal fun TerminalExtraKeys(
    state: TermuxTerminalPane.State,
    onAction: (String) -> Unit,
) {
    if (state.extraKeys.isEmpty()) return
    val palette = chrome()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (landscape) 68.dp else 82.dp)
            .background(palette.bar)
            // V3：bar 与终端内容区同为纯黑贴在一起边界感弱，加一条顶部
            // 分隔线（与 drawer 同用的 divider 色）划清两块区域。
            .drawBehind {
                drawRect(
                    color = palette.divider,
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        state.extraKeys.forEach { keys ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                keys.forEach { key ->
                    val active = keyIsLatched(state, key.action)
                    // 修饰键锁定是「按下 → 变亮」，硬切会像闪一下。走令牌的 150ms 淡变。
                    val keyColor by animateColorAsState(
                        targetValue = if (active) palette.accent else palette.text,
                        animationSpec = ZhiMotion.colorSpec,
                        label = "extraKeyColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(ZhiRadius.inner))
                            .background(palette.keyBg)
                            .clickable { onAction(key.action) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key.display,
                            color = keyColor,
                            fontSize = ZhiTextScale.Footnote,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 这个扩展键是不是"已按下待用"的修饰键（CTRL/ALT/SHIFT/FN）。 */
private fun keyIsLatched(state: TermuxTerminalPane.State, action: String): Boolean =
    when (action.trim().uppercase()) {
        "CTRL" -> state.ctrl
        "ALT" -> state.alt
        "SHIFT" -> state.shift
        "FN" -> state.fn
        else -> false
    }

/**
 * 会话抽屉。
 *
 * 宽度、遮罩不透明度、行高、两端的三个动作都与原实现一致（`Termux sessions`
 * 标题、`＋  New session`、`⌨  Toggle keyboard`、`↻  Reload properties`）。
 *
 * 遮罩用 `detectTapGestures` 而不是 `clickable`：遮罩上出现涟漪会让人以为
 * 它是一块可交互内容，而它的作用只是"点空白处关掉抽屉"。
 */
@Composable
internal fun BoxScope.TerminalDrawer(
    open: Boolean,
    state: TermuxTerminalPane.State,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onRenameSession: (Int) -> Unit,
    onToggleKeyboard: () -> Unit,
    onReloadProperties: () -> Unit,
) {
    val palette = chrome()
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(ZhiMotion.fadeInSpec),
        exit = fadeOut(ZhiMotion.fadeOutSpec),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimAlpha))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(ZhiMotion.enterSpec) { -it },
        exit = slideOutHorizontally(ZhiMotion.exitSpec) { -it },
        modifier = Modifier.align(Alignment.CenterStart).width(TerminalDrawerWidth).fillMaxHeight(),
    ) {
        Surface(color = palette.drawer, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(
                    text = "终端会话",
                    color = palette.text,
                    fontSize = ZhiTextScale.Subheading,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 12.dp),
                )
                DrawerAction("＋  新建会话", palette) { onNewSession() }
                Box(modifier = Modifier.padding(vertical = 7.dp)) {
                    ZhiHorizontalDivider(color = palette.divider)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.sessions.forEachIndexed { index, session ->
                        SessionRow(
                            session = session,
                            palette = palette,
                            onSelect = { onSelect(index) },
                            onClose = { onCloseSession(index) },
                            onRename = { onRenameSession(index) },
                        )
                    }
                }
                DrawerAction("⌨  切换键盘", palette) { onToggleKeyboard() }
                DrawerAction("↻  重载 termux.properties", palette) { onReloadProperties() }
            }
        }
    }
}

/** 抽屉里的一行会话：● / ○ + 名字 + 运行中/已结束，右侧一个 ×；长按改名。 */
@Composable
private fun SessionRow(
    session: TermuxTerminalPane.SessionInfo,
    palette: Chrome,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onRename: () -> Unit,
) {
    // 选中行是「点一下整行变底 + 文字变亮」，同样走淡变：
    // 不淡的话切会话时两行会“啪”地交换，而这一行里同时变了底、文字色与圈符。
    val rowBg by animateColorAsState(
        targetValue = if (session.selected) palette.selected else Color.Transparent,
        animationSpec = ZhiMotion.colorSpec,
        label = "sessionRowBg",
    )
    val rowText by animateColorAsState(
        targetValue = if (session.selected) palette.text else palette.muted,
        animationSpec = ZhiMotion.colorSpec,
        label = "sessionRowText",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(rowBg)
            .pointerInput(session.name) { detectTapGestures(onTap = { onSelect() }, onLongPress = { onRename() }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (if (session.selected) "●  " else "○  ") + session.name +
                "\n    " + (if (session.running) "运行中" else "已结束"),
            color = rowText,
            fontSize = ZhiTextScale.BodySmall,
            fontWeight = if (session.selected) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 17.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = "×",
            color = palette.muted,
            fontSize = ZhiTextScale.TitleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(38.dp)
                .height(42.dp)
                .clickable { onClose() }
                .padding(top = 6.dp),
        )
    }
}

/** 抽屉里的一个整行动作（新建会话 / 切键盘 / 重载属性）。 */
@Composable
private fun DrawerAction(label: String, palette: Chrome, onClick: () -> Unit) {
    Text(
        text = label,
        color = palette.text,
        fontSize = ZhiTextScale.BodySmall,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
            .padding(start = 8.dp, top = 11.dp),
    )
}

/**
 * 环境未就绪的占位。
 *
 * 文案由宿主给出（见 `TermuxTerminalPane` 里的 `RUNTIME_NOTICE`）：刻意说明**为什么**
 * 不能输入、去哪里打开，而不是"正在准备中"——后者会让人干等一件没在发生的事。
 */
@Composable
internal fun TerminalRuntimeNotice(text: String) {
    val palette = chrome()
    Box(
        // ⚠️ 不能写死 Color.Black：浅色模式下终端正文是浅底（见 TermuxTerminalPane
        // 的浅色档 ANSI 表），占位却铺一块纯黑 —— 切进终端那一下会"闪一块黑"，
        // 也就是用户报的「终端对深浅色不适配」。
        // 用面板底色同一个令牌，它既跟随主题、又保证与终端正文底色一致。
        modifier = Modifier.fillMaxSize().background(ZhiColors.panelSurface()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.muted,
            fontSize = ZhiTextScale.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * PTY 起不来的占位：标题 + 等宽详情（可选中，方便复制去搜）+ 重试按钮。
 *
 * 关键是**不崩**：任何 JNI/attach 失败都落在这里，用户至少能把错误读出来。
 */
@Composable
internal fun TerminalFailure(detail: String, onRetry: () -> Unit) {
    val palette = chrome()
    Column(
        // 同上：不写死黑底，与终端正文/面板底色同源。
        modifier = Modifier.fillMaxSize().background(ZhiColors.panelSurface()).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = TermuxTerminalPane.FAILURE_TITLE,
            color = palette.error,
            fontSize = ZhiTextScale.Heading,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail + TermuxTerminalPane.FAILURE_FOOTNOTE,
            color = palette.muted,
            fontSize = ZhiTextScale.Caption,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(
            text = "↻  重试终端",
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
