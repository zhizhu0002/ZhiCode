package com.zhizhu.zhicode.compose.ui.panes

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.TermuxTerminalPane
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import androidx.compose.ui.unit.IntOffset
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme


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

/** 把一个 [TermuxTerminalPane] 的状态接成 Compose 可观察的值。 */
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

/** 终端面板头部右侧的三个动作：☰ 会话列表、⌨ 切键盘、⋮ 快捷动作。 */
@Composable
internal fun RowScope.TerminalHeaderActions(
    onKeyboard: () -> Unit,
    onMore: () -> Unit,
) {
    val palette = chrome()
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

/** 文字字形按钮（⌨ 这类 Miuix 图标库里没有的键）。 */
@Composable
private fun GlyphButton(
    glyph: String,
    color: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        backgroundColor = Color.Transparent,
        // 方角：终端的工具栏用的是上游 Termux 的语言（平面、直角），
        // 圆角按钮属于设置页那一套。
        cornerRadius = 0.dp,
        minHeight = 34.dp,
        minWidth = 34.dp,
    ) {
        Text(
            text = glyph,
            color = color,
            fontSize = MiuixTheme.textStyles.title3.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TerminalExtraKeys(
    state: TermuxTerminalPane.State,
    onAction: (String) -> Unit,
) {
    if (state.extraKeys.isEmpty()) return
    val palette = chrome()
    val imeMotion = LocalImeMotion.current
    val navInset = if (imeMotion.imeVisible) {
        0.dp
    } else {
        com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset.current
    }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val navBarProgressState = com.zhizhu.zhicode.compose.ui.LocalNavBarProgress.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = navBarProgressState.value
                if (progress > 0f) translationY = navInset.toPx() * progress
            }
            .height((if (landscape) 68.dp else 82.dp) + navInset)
            .background(palette.bar)
            // V3：bar 与终端内容区同为纯黑贴在一起边界感弱，加一条顶部
            // 分隔线（与 drawer 同用的 divider 色）划清两块区域。
            .drawBehind {
                drawRect(
                    color = palette.divider,
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            // 底部那段让位必须落在**内边距**上：两行键是 weight(1f) 平分的，
            // 要是只加高度不加内边距，让出来的空间会被两行键直接吃掉（键变得巨高）。
            .padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 2.dp + navInset),
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
                            .background(palette.keyBg)
                            .clickable { onAction(key.action) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key.display,
                            color = keyColor,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
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
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            fontWeight = if (session.selected) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 17.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = "×",
            color = palette.muted,
            fontSize = MiuixTheme.textStyles.title2.fontSize,
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
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() }
            .padding(start = 8.dp, top = 11.dp),
    )
}

@Composable
internal fun TerminalRuntimeNotice(text: String) {
    val palette = chrome()
    Box(
        modifier = Modifier.fillMaxSize().background(ZhiColors.panelSurface()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.muted,
            fontSize = MiuixTheme.textStyles.main.fontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/** PTY 起不来的占位：标题 + 等宽详情（可选中，方便复制去搜）+ 重试按钮。 */
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
            fontSize = MiuixTheme.textStyles.title3.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail + TermuxTerminalPane.FAILURE_FOOTNOTE,
            color = palette.muted,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
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
