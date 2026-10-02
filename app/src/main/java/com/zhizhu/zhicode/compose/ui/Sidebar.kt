package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 侧栏**真正读到的**四项。
 *
 * <p>理由与 [TopBarState] 完全相同：侧栏只用 `projectName` / `sessions` /
 * `activeSessionId` / `runtimeReady`，而整份 `WorkspaceUiState` 里带着
 * 对话流与全部工具输出 —— 流式期间每 32ms 换一次实例，侧栏就要为一次
 * 无关的正文增量重组一遍（它一个字都不会变），判等还要逐个走会话列表。
 *
 * <p>⚠️ **不能用 `@Immutable` 标这个类**：`sessions` 是 `List`，而 Kotlin 的
 * `List` 只是个只读视图，背后完全可能是可变的 `ArrayList`。标成不可变等于向
 * 编译器保证一件我核实不了的事，一旦哪里原地 add 一下，界面就会静默停更。
 * 它保持 unstable，跳过判断由 Compose 用 `equals()` 做 —— 会话列表通常很短
 * （几十条以内），这个代价是可接受的，而"传整份 UiState"的代价不是。
 */
data class SidebarState(
    val projectName: String,
    val sessions: List<SessionSummary>,
    val activeSessionId: String,
    val runtimeReady: Boolean,
) {
    companion object {
        fun from(state: WorkspaceUiState) = SidebarState(
            projectName = state.projectName,
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            runtimeReady = state.runtimeReady,
        )
    }
}

/**
 * 侧栏（UI 重设计 R2）。
 *
 * 结构改为 HyperOS 设置页同构：**分节标题（SmallTitle）+ 直接行列表**，
 * 删除原来的「Card 容器套 BasicComponent」双层结构 —— 那是「卡片套卡片」
 * 观感的主要来源：纯黑面板上每节再画一个 #2A2A2A 圆角大卡，层级噪音大、
 * 每节多出两层内边距，行密度上不去。
 *
 * 行本体直接用 Miuix [BasicComponent]（自带按压反馈与 56dp 最小高，
 * 由外部 heightIn(max) 收敛到紧凑值），选中/主按钮态用 background 修饰符表达。
 */
@Composable
fun ZhiSidebar(
    state: SidebarState,
    onNewSession: () -> Unit,
    onOpenSession: (SessionSummary) -> Unit,
    onSessionActions: (SessionSummary) -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
    /**
     * 长按动作菜单的宿主插槽。在**每一条会话自己的布局里**调用，菜单就会贴那一条
     * 弹出（见 `ZhiAnchoredActionMenu`）。
     *
     * 第一个参数是会话 id（只有被长按那一条会认领）；第二个是**手指位置**
     * （相对该条），非空时菜单从那一点长出来。
     */
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
    onSandbox: () -> Unit,
    onRuntime: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(modifier = modifier, color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            SidebarRow(
                label = "新会话",
                icon = ZhiIcons.newSession,
                emphasized = true,
                onClick = onNewSession,
            )
            // rikkahub 式语义化入口：直接开页面/动作，不走命令行通道。
            //
            // 「项目路径与会话」（旧 = 往输入器塞 `/status`）和「当前项目上下文」
            // （旧 = `/resume`）都已删除。它们都把**一次会话动作**伪装成导航行：
            // 前者发一条斜杠命令、后者要模型来选会话，点下去都不会立刻看到结果，
            // 而正下方就是项目历史列表 —— 同一个目标两条路径，用户得猜该点哪个。
            // 恢复会话直接点历史里的会话；项目信息在顶栏与设置里都能看到。
            // （用户已确认：这两行删掉，之后按新的做法重做。）

            ZhiSectionLabel(
                text = "项目历史 · ${state.projectName}",
                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
            )
            // 会话多时吃掉剩余高度让一屏看到更多；空/少时按内容自适应。
            LazyColumn(
                modifier = if (state.sessions.isEmpty()) Modifier.fillMaxWidth()
                else Modifier.weight(1f, fill = true),
            ) {
                if (state.sessions.isEmpty()) {
                    item {
                        Text(
                            text = "暂无已保存会话",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = ZhiTextScale.Caption,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        active = session.id == state.activeSessionId,
                        onOpen = { onOpenSession(session) },
                        onActions = { onSessionActions(session) },
                        onDelete = { onDeleteSession(session) },
                        anchoredMenu = anchoredMenu,
                    )
                }
            }

            // 「技能」「自定义角色卡」原本在这里，已删：它们是**设置里的配置对象**，
            // 侧栏是"去哪一屏"的导航，把两处配置项摆在导航栏里会让同一目标出现两条
            // 路径（侧栏一条、设置页「扩展」组一条），且侧栏会被越堆越长。
            // 配置类入口统一收到设置页（见 SettingsDialog 的 ExtensionsPage）。
            ZhiSectionLabel(
                text = "更多",
                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
            )
            SidebarRow(label = "ZhiCode 沙箱", icon = ZhiIcons.sandbox, onClick = onSandbox)

            SidebarRow(
                label = if (state.runtimeReady) "运行环境就绪" else "准备内置 Termux 环境",
                icon = ZhiIcons.runtime,
                tint = if (state.runtimeReady) ZhiColors.green() else Color.Unspecified,
                onClick = onRuntime,
            )
            SidebarRow(label = "设置", icon = ZhiIcons.settings, onClick = onSettings)
        }
    }
}

/**
 * 侧栏功能行：直接 [BasicComponent]，无外层 Card。
 * [emphasized] 为 true 时整行主色填充（「新会话」）。
 */
@Composable
private fun SidebarRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    val background = if (emphasized) scheme.primary else Color.Transparent
    val foreground = when {
        emphasized -> scheme.onPrimary
        tint != Color.Unspecified -> tint
        else -> scheme.onSurface
    }
    BasicComponent(
        onClick = onClick,
        title = label,
        titleColor = BasicComponentDefaults.titleColor(color = foreground),
        startAction = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = SidebarRowMaxHeight)
            .clip(RoundedCornerShape(ZhiRadius.inner))
            .background(background),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onDelete: () -> Unit,
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
) {
    // 只读观察者：不会抢走卡片自己的「点击打开会话」与长按（也不影响无障碍语义）。
    val finger = rememberFingerTracker()
    // 长按那一刻定格手指位置：菜单显示期间手指已经抬起，必须留住这个值。
    var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
    // 外面这层 Box 只为托住长按菜单与手指追踪：菜单作为它的子项就会用**相对这一条**
    // 的偏移定位（与观察者同一个坐标系）。它不参与布局，行依旧 fillMaxWidth。
    Box(modifier = finger.modifier) {
        SessionRowInner(
            session = session,
            active = active,
            onOpen = onOpen,
            onActions = {
                fingerOffset = finger.offset()
                onActions()
            },
            onDelete = onDelete,
        )
        anchoredMenu(session.id, fingerOffset)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowInner(
    session: SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 选中态背景淡入，切换会话时不会硬跳
    val background by animateColorAsState(
        targetValue = if (active) ZhiColors.cardInnerSurface() else Color.Transparent,
        animationSpec = ZhiMotion.colorSpec,
        label = "sessionRowBackground",
    )
    Card(
        onClick = onOpen,
        onLongPress = onActions,
        modifier = Modifier.fillMaxWidth().heightIn(max = SessionRowMaxHeight),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        colors = CardDefaults.defaultColors(
            color = background,
            contentColor = if (session.busy) scheme.primary else scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = ZhiTextScale.Body,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = session.updatedAtLabel,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        maxLines = 1,
                    )
                    Text(
                        text = "· ${session.messageCount} 条",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        maxLines = 1,
                    )
                    if (session.note.isNotEmpty()) {
                        Text(
                            text = "· 备注",
                            color = scheme.primary,
                            fontSize = ZhiTextScale.Micro,
                            maxLines = 1,
                        )
                    }
                }
            }
            ZhiIconButton(
                icon = ZhiIcons.close,
                description = "删除会话",
                onClick = onDelete,
                iconSize = 14.dp,
            )
        }
    }
}

// ---------------------------------------------------------------- 紧凑尺寸

/**
 * 侧栏行最大高度。
 *
 * Miuix `BasicComponent` 内部写死 `heightIn(min = 56.dp)`（从 0.9.4 字节码读出：
 * `bipush 56` → `heightIn`），而侧栏要在有限高度里塞下尽可能多的会话，
 * 所以从外面再套一层 `heightIn(max = …)`：内层的 min 会被外层 max 收敛下来。
 */
private val SidebarRowMaxHeight = 46.dp

/**
 * 会话行最大高度（标题 + 元信息两行）。
 *
 * 原为 48dp，用户反馈「项目历史列表太狭窄了，可以加高一点点」——
 * 加高到 58dp。两行的呼吸空间够了之后，标题与元信息不再贴在一起，
 * 点起来也更好按（48dp 已接近 44dp 的可点下限，长标题更容易误触隔壁行）。
 */
private val SessionRowMaxHeight = 58.dp
