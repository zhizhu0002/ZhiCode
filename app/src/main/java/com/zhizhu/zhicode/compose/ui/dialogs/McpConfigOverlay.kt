package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * MCP 服务器配置窗口：列表页与编辑表单共用一个弹窗。
 *
 * ## 为什么这里值得做
 *
 * 引擎侧 `McpRuntime` 会真的把配置里的服务器拉起来，`McpTool` 也已经注册进
 * `ToolRegistry`——也就是说这里存下去的每一条配置**当场就能被 Agent 调用**。
 * 这不是占位界面。
 *
 * ## 字段是动态显隐的
 *
 * stdio 用 `command` + `args`，HTTP/SSE 用 `url`。把两者同时铺在表单里会让人填错，
 * 所以按 [McpServerDraft.type] 切换显示；`env` 只对 stdio 有意义（进程环境变量），
 * `headers` 只对远端有意义（HTTP 请求头）。
 */
@Composable
fun McpConfigOverlay(
    config: McpConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onDraftChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
) {
    OverlayDialog(
        show = config != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 640.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val current = config ?: return@OverlayDialog
        val form = current.form
        if (form == null) {
            McpServerList(
                config = current,
                onNew = onNew,
                onEdit = onEdit,
                onToggle = onToggle,
                onDelete = onDelete,
                onClose = onDismiss,
            )
        } else {
            McpServerForm(
                draft = form,
                onChange = onDraftChange,
                onSave = onSave,
                onCancel = onCancelForm,
            )
        }
    }
}

@Composable
private fun McpServerList(
    config: McpConfigState,
    onNew: () -> Unit,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "MCP 服务器",
        groupBody = true,
        actions = {
            SecondaryButton(text = "关闭", onClick = onClose)
            PrimaryButton(text = "添加", onClick = onNew, modifier = Modifier.padding(start = 8.dp))
        },
    ) {
        Text(
            text = "配置文件：${config.filePath}",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (config.servers.isEmpty()) {
            // 空态要把"怎么开始"说清楚，而不是只报一句"0 项"。
            Text(
                text = "当前没有配置 MCP 服务器。点「添加」填入服务器名称与启动命令（stdio）" +
                    "或服务器 URL（HTTP/SSE），保存后 Agent 即可调用该服务器提供的工具。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth(),
            )
            return@DialogShell
        }

        // 服务器可以有很多条，用懒列表 + 高度上限，避免把底部按钮顶出屏幕。
        // 这里不能用 weight()：DialogShell 的 body 是通用容器，不保证处在 ColumnScope 里。
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(config.servers, key = { it.name }) { server ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    cornerRadius = ZhiRadius.card,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (server.enabled) scheme.surfaceContainerHigh else scheme.surfaceContainer,
                        contentColor = scheme.onBackground,
                    ),
                    pressFeedbackType = PressFeedbackType.None,
                ) {
                    BasicComponent(
                        title = server.name,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = if (server.enabled) scheme.onBackground else scheme.onSurfaceVariantSummary,
                        ),
                        summary = if (server.enabled) server.summary else "已停用 · ${server.summary}",
                        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                        endActions = {
                            ZhiIconButton(
                                icon = ZhiIcons.edit,
                                description = "编辑",
                                onClick = { onEdit(server) },
                                iconSize = 15.dp,
                            )
                            ZhiIconButton(
                                icon = ZhiIcons.close,
                                description = "删除",
                                onClick = { onDelete(server) },
                                iconSize = 15.dp,
                            )
                        },
                        // 整行点击 = 启用/停用。原版是在行尾放一个独立按钮，
                        // 但 Compose 里两个图标按钮已经占了行尾，整行点击更省事且不易误触。
                        onClick = { onToggle(server) },
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerForm(
    draft: McpServerDraft,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = if (draft.isEditing) "编辑 MCP 服务器" else "添加 MCP 服务器",
        groupBody = true,
        actions = {
            SecondaryButton(text = "取消", onClick = onCancel)
            PrimaryButton(
                text = "保存",
                enabled = draft.saveable,
                onClick = onSave,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            TextField(
                value = draft.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = "服务器名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            FieldError(draft.nameError)

            OverlayDropdownPreference(
                items = McpType.entries.map { it.label },
                selectedIndex = McpType.entries.indexOf(draft.type).coerceAtLeast(0),
                title = "连接方式",
                summary = null,
                onSelectedIndexChange = { index ->
                    McpType.entries.getOrNull(index)?.let { type -> onChange { it.copy(type = type) } }
                },
            )

            if (draft.type.needsCommand) {
                TextField(
                    value = draft.command,
                    onValueChange = { v -> onChange { it.copy(command = v) } },
                    label = "启动命令",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                FieldError(draft.commandError)
                TextField(
                    value = draft.argsText,
                    onValueChange = { v -> onChange { it.copy(argsText = v) } },
                    label = "命令参数（每行一个）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = draft.envText,
                    onValueChange = { v -> onChange { it.copy(envText = v) } },
                    label = "环境变量 JSON（可选）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                FieldError(draft.envError)
            } else {
                TextField(
                    value = draft.url,
                    onValueChange = { v -> onChange { it.copy(url = v) } },
                    label = "服务器 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                FieldError(draft.urlError)
                TextField(
                    value = draft.headersText,
                    onValueChange = { v -> onChange { it.copy(headersText = v) } },
                    label = "请求头 JSON（可选）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                FieldError(draft.headersError)
            }

            OverlayDropdownPreference(
                items = McpScope.entries.map { it.label },
                selectedIndex = McpScope.entries.indexOf(draft.scope).coerceAtLeast(0),
                title = "作用范围",
                summary = null,
                onSelectedIndexChange = { index ->
                    McpScope.entries.getOrNull(index)?.let { scope -> onChange { it.copy(scope = scope) } }
                },
            )

            SwitchPreference(
                title = "启用",
                summary = "停用后配置仍然保留，但 Agent 不会调用该服务器。",
                checked = draft.enabled,
                onCheckedChange = { v -> onChange { it.copy(enabled = v) } },
            )

            Text(
                text = "「名称」用来在 Agent 调用时标识这台服务器，改动名称等于换了一台" +
                    "（原名称的记录会被覆盖）。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** 只在有错时占位，没错时不画——避免表单里到处是空行。 */
@Composable
private fun FieldError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = MiuixTheme.colorScheme.error,
        fontSize = ZhiTextScale.Footnote,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
