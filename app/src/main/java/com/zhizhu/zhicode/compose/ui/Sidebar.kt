package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 侧栏，对应原 蜘蛛 的 buildSidebar() / sessionRow()。
 * 宽屏时作为常驻栏，窄屏时放进 OverlayBottomSheet。
 */
@Composable
fun ZhiSidebar(
    state: WorkspaceUiState,
    onNewSession: () -> Unit,
    onProjectHistory: () -> Unit,
    onProjectPath: () -> Unit,
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
    onRoleCard: () -> Unit,
    onSkills: () -> Unit,
    onSandbox: () -> Unit,
    onRuntime: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(modifier = modifier, color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            // 每块功能分组 = 框上方的分组标题 + 一个带描边的框，
            // 把「新建/项目」「会话列表」「工作区」「运行环境」四段分开。
            // 标题原先写在框**内**，看起来像框里的一个条目，也把框撑高了；
            // 改到框外后与 Miuix / HyperOS 设置页的做法一致。
            SidebarSection {
                SidebarAction(
                    label = "新会话",
                    icon = ZhiIcons.newSession,
                    primary = true,
                    onClick = onNewSession,
                )
                SidebarAction(label = "当前项目上下文", icon = ZhiIcons.projectHistory, onClick = onProjectHistory)
                SidebarAction(label = "手动添加 / 切换项目路径", icon = ZhiIcons.projectPath, onClick = onProjectPath)
            }

            SidebarSection(
                title = "项目历史 · ${state.projectName}",
                // 会话多时吃掉剩余高度让一屏看到更多；空/少时 weight(fill=false)
                // 只给上限不强制撑满，不再出现巨型空卡（上限仍由 weight 约束，
                // 会话多时 LazyColumn 照样占满并内部滚动）。
                modifier = Modifier.weight(1f, fill = state.sessions.isNotEmpty()),
                fillHeight = state.sessions.isNotEmpty(),
            ) {
                // 会话区占满剩余高度（搜索框已按要求移除）。
                LazyColumn(
                    modifier = if (state.sessions.isEmpty()) Modifier.fillMaxWidth()
                    else Modifier.fillMaxWidth().weight(1f, fill = true),
                ) {
                    if (state.sessions.isEmpty()) {
                        item {
                            Text(
                                text = "暂无已保存会话",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Caption,
                                modifier = Modifier.padding(start = 10.dp, top = 12.dp),
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
            }

            // 工作区里**不再重复**放「变更 / 终端 / 文件」——
            // 这三个本来就是工作区标签页，顶栏那排按键组（窄屏）与右侧副栏（宽屏）
            // 已经能切了，侧栏再列一遍既是重复入口，也把这张卡撑得很长。
            SidebarSection(title = "工作区") {
                SidebarAction(label = "技能", icon = ZhiIcons.skill, onClick = onSkills)
                SidebarAction(label = "自定义角色卡", icon = ZhiIcons.roleCard, onClick = onRoleCard)
                SidebarAction(label = "ZhiCode 沙箱", icon = ZhiIcons.sandbox, onClick = onSandbox)
            }

            SidebarSection {
                SidebarAction(
                    label = if (state.runtimeReady) "运行环境就绪" else "准备内置 Termux 环境",
                    icon = ZhiIcons.runtime,
                    tint = if (state.runtimeReady) ZhiColors.green() else Color.Unspecified,
                    onClick = onRuntime,
                )
                SidebarAction(label = "设置", icon = ZhiIcons.settings, onClick = onSettings)
            }
        }
    }
}

/**
 * 侧栏里的一个功能分组：**框上方的分组标题** + 一个圆角容器。
 *
 * `title` 为空时连标题一起省掉（第 1、4 段就是这样，它们没有分组名），
 * 不会出现只有空行没有文字的头。
 *
 * `fillHeight` 用于「会话列表」那段：它要吃掉剩余高度，好让一屏能多列几个会话。
 */
@Composable
private fun SidebarSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        if (title != null) {
            // 分组标题走 Miuix SmallTitle，不再手写字号/字重
            ZhiSectionLabel(
                text = title,
                modifier = Modifier.padding(start = 9.dp, top = 3.dp, bottom = 1.dp),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // 需要吃掉剩余高度的分组：外层已用 weight 约束高度，这里让框撑满
                .then(if (fillHeight) Modifier.weight(1f, fill = true) else Modifier),
            cornerRadius = ZhiRadius.card,
            insideMargin = PaddingValues(horizontal = 2.dp, vertical = 1.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SidebarAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    // 选中态背景淡入，切换工作区时不会硬跳
    val background by animateColorAsState(
        targetValue = when {
            primary -> scheme.primary
            active -> ZhiColors.cardInnerSurface()
            else -> Color.Transparent
        },
        animationSpec = tween(ZhiMotion.MEDIUM, easing = FastOutSlowInEasing),
        label = "sidebarActionBackground",
    )
    val foreground = when {
        primary -> scheme.onPrimary
        tint != Color.Unspecified -> tint
        active -> scheme.onSurface
        else -> scheme.onSurfaceVariantSummary
    }
    // 行本体走 Miuix BasicComponent（标题/图标/内边距/按压态由组件负责，
    // 不再自己拼 Row + Icon + Text）；外面套一层 Card 是因为 BasicComponent
    // 没有背景色参数，而选中态需要一个随主题变化的底色。
    Card(
        onClick = onClick,
        // BasicComponent 内置 heightIn(min = 56.dp)，侧栏不需要那么高，
        // 用外层 heightIn(max = …) 收敛。
        //
        // ⚠️ 别把这两个值再压小：`PressFeedbackType.Sink` 的反馈是**内容下沉**，
        // 行高太扁 + 内边距为 0 时整个动画几乎看不出来，看起来像"没有按压反馈"。
        // 之前压到 34dp / vertical = 0dp 就是这个结果。
        modifier = modifier.fillMaxWidth().heightIn(max = SidebarRowMaxHeight),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = background, contentColor = foreground),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        BasicComponent(
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
            // 点击由外层 Card 处理：这里再传一次 onClick 会形成嵌套可点区域。
            // vertical 不能为 0：要给 Sink 的下沉动画留出空间。
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        )
    }
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
    // 的偏移定位（与观察者同一个坐标系）。它不参与布局，Card 依旧 fillMaxWidth。
    Box(modifier = finger.modifier) {
        SessionRowCard(
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
private fun SessionRowCard(
    session: SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        onLongPress = onActions,
        modifier = Modifier.fillMaxWidth().heightIn(max = SessionRowMaxHeight),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        colors = CardDefaults.defaultColors(
            color = if (active) ZhiColors.cardInnerSurface() else Color.Transparent,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = session.updatedAtLabel,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        maxLines = 1,
                    )
                    Text(
                        text = "· ${session.messageCount} 条",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        maxLines = 1,
                    )
                    if (session.note.isNotEmpty()) {
                        Text(
                            text = "· 备注",
                            color = scheme.primary,
                            fontSize = ZhiTextScale.Footnote,
                            maxLines = 1,
                        )
                    }
                }
            }
            ZhiIconButton(
                icon = ZhiIcons.close,
                description = "删除会话",
                onClick = onDelete,
                iconSize = 15.dp,
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
private val SidebarRowMaxHeight = 42.dp

/** 会话行最大高度（标题 + 元信息两行）。 */
private val SessionRowMaxHeight = 58.dp
