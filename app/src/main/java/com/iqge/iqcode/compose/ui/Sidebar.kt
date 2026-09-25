package com.iqge.iqcode.compose.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.SessionSummary
import com.iqge.iqcode.compose.model.WorkspaceTab
import com.iqge.iqcode.compose.model.WorkspaceUiState
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
 * 侧栏，对应原 IQ Code 的 buildSidebar() / sessionRow()。
 * 宽屏时作为常驻栏，窄屏时放进 OverlayBottomSheet。
 */
@Composable
fun IqSidebar(
    state: WorkspaceUiState,
    onNewSession: () -> Unit,
    onProjectHistory: () -> Unit,
    onProjectPath: () -> Unit,
    onOpenSession: (SessionSummary) -> Unit,
    onSessionActions: (SessionSummary) -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
    onSelectTab: (WorkspaceTab) -> Unit,
    onRoleCard: () -> Unit,
    onSkills: () -> Unit,
    onSandbox: () -> Unit,
    onRuntime: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(modifier = modifier, color = IqColors.panelSurface()) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            // 每块功能分组包一个带描边的框，视觉上把「新建/项目」「会话列表」
            // 「工作区」「运行环境」四段分开；框内自带内边距与圆角。
            SidebarSection {
                SidebarAction(
                    label = "新会话",
                    icon = IqIcons.newSession,
                    primary = true,
                    onClick = onNewSession,
                )
                SidebarAction(label = "当前项目上下文", icon = IqIcons.projectHistory, onClick = onProjectHistory)
                SidebarAction(label = "手动添加 / 切换项目路径", icon = IqIcons.projectPath, onClick = onProjectPath)
            }

            SidebarSection(
                title = "项目历史 · ${state.projectName}",
                // 这一块要吃掉剩余高度，这样一屏能看到尽可能多的会话
                modifier = Modifier.weight(1f, fill = true),
            ) {
                // 会话区占满剩余高度（搜索框已按要求移除）。
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                    if (state.sessions.isEmpty()) {
                        item {
                            Text(
                                text = "暂无已保存会话",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = 11.sp,
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
                        )
                    }
                }
            }

            SidebarSection(title = "工作区") {
                SidebarAction(
                    label = "变更",
                    icon = IqIcons.changes,
                    active = state.tab == WorkspaceTab.CHANGES,
                    onClick = { onSelectTab(WorkspaceTab.CHANGES) },
                )
                SidebarAction(
                    label = "终端",
                    icon = IqIcons.terminal,
                    active = state.tab == WorkspaceTab.TERMINAL,
                    onClick = { onSelectTab(WorkspaceTab.TERMINAL) },
                )
                SidebarAction(
                    label = "文件",
                    icon = IqIcons.files,
                    active = state.tab == WorkspaceTab.FILES,
                    onClick = { onSelectTab(WorkspaceTab.FILES) },
                )
                SidebarAction(label = "技能", icon = IqIcons.skill, onClick = onSkills)
                SidebarAction(label = "自定义角色卡", icon = IqIcons.roleCard, onClick = onRoleCard)
                SidebarAction(label = "IQ 沙箱", icon = IqIcons.sandbox, onClick = onSandbox)
            }

            SidebarSection {
                SidebarAction(
                    label = if (state.runtimeReady) "运行环境就绪" else "准备内置 Termux 环境",
                    icon = IqIcons.runtime,
                    tint = if (state.runtimeReady) IqColors.green() else Color.Unspecified,
                    onClick = onRuntime,
                )
                SidebarAction(label = "设置", icon = IqIcons.settings, onClick = onSettings)
            }
        }
    }
}

/**
 * 侧栏里的一个功能分组：带描边的圆角容器 + 可选的小标题。
 *
 * 描边用 `BorderStroke`（Miuix `Surface` 原生参数），不再手写 `border`；
 * `title` 为空时连标题一起省掉，避免出现只有空行没有文字的头。
 */
@Composable
private fun SidebarSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        cornerRadius = IqRadius.card,
        insideMargin = PaddingValues(horizontal = 3.dp, vertical = 2.dp),
    ) {
        if (title != null) {
            // 分组标题走 Miuix SmallTitle，不再手写字号/字重
            IqSectionLabel(
                text = title,
                modifier = Modifier.padding(start = 6.dp, top = 1.dp, bottom = 2.dp),
            )
        }
        content()
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
            active -> IqColors.cardInnerSurface()
            else -> Color.Transparent
        },
        animationSpec = tween(IqMotion.MEDIUM, easing = FastOutSlowInEasing),
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
        // BasicComponent 内置 heightIn(min = 56.dp)，侧栏空间紧张，用外层
        // heightIn(max = …) 把行高压到 38dp（约束会被内层取 min 后收敛）。
        modifier = modifier.fillMaxWidth().heightIn(max = SidebarRowMaxHeight),
        cornerRadius = IqRadius.card,
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
                        modifier = Modifier.size(15.dp),
                    )
                }
            },
            // 点击由外层 Card 处理：这里再传一次 onClick 会形成嵌套可点区域
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
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
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        onLongPress = onActions,
        modifier = Modifier.fillMaxWidth().heightIn(max = SessionRowMaxHeight),
        cornerRadius = IqRadius.card,
        insideMargin = PaddingValues(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        colors = CardDefaults.defaultColors(
            color = if (active) IqColors.cardInnerSurface() else Color.Transparent,
            contentColor = if (session.busy) scheme.primary else scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 13.sp,
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
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "· ${session.messageCount} 条",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    if (session.note.isNotEmpty()) {
                        Text(
                            text = "· 备注",
                            color = scheme.primary,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            IqIconButton(
                icon = IqIcons.close,
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
private val SidebarRowMaxHeight = 38.dp

/** 会话行最大高度（标题 + 元信息两行）。 */
private val SessionRowMaxHeight = 54.dp
