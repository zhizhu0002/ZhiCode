package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    if (config == null) return
    val form = config.form
    // 用「最后一次非空」而不是直接读 form：退出动画期间离场页还在画，
    // 那时 form 已经是 null 了。见 rememberLastNonNull 的说明。
    val shownForm = rememberLastNonNull(form)
    SettingsPageStack(
        current = if (form == null) SettingsPageKey("mcp.list", 0) else SettingsPageKey("mcp.form", 1),
        onBack = if (form == null) onDismiss else onCancelForm,
    ) { key ->
        // ⚠️ 分支必须看**正在渲染的那一页**（key），不能看当前状态：
        // 退出动画期间 key 还是 mcp.form 而 form 已经变 null，
        // 按状态分支会让离场页画成列表，动画就废了。
        val draft = if (key.id == "mcp.form") shownForm else null
        if (draft == null) {
            SettingsSubPage(
                title = "MCP 服务器",
                onBack = onDismiss,
                action = "添加" to onNew,
            ) {
                McpServerList(
                    config = config,
                    onEdit = onEdit,
                    onToggle = onToggle,
                    onDelete = onDelete,
                )
            }
        } else {
            SettingsSubPage(
                title = if (draft.isEditing) "编辑 MCP 服务器" else "添加 MCP 服务器",
                onBack = onCancelForm,
                action = "保存" to (if (draft.saveable) onSave else null),
            ) {
                McpServerForm(
                    draft = draft,
                    onChange = onDraftChange,
                )
            }
        }
    }
}

@Composable
private fun McpServerList(
    config: McpConfigState,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            text = "配置文件：${config.filePath}",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // 与设置主页同形态：一张分组卡里若干行，每行不再各套一张卡。
        // （整页模式下列表不再限高：外层 SettingsSubPage 的 LazyColumn 负责滚动。）
        SettingsGroup("服务器") {
            if (config.servers.isEmpty()) {
                // 空态要把"怎么开始"说清楚，而不是只报一句"0 项"。
                Text(
                    text = "当前没有配置 MCP 服务器。点「添加」填入服务器名称与启动命令（stdio）" +
                        "或服务器 URL（HTTP/SSE），保存后 Agent 即可调用该服务器提供的工具。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                config.servers.forEach { server ->
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
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
) {
    // 表单滚动由外层 SettingsSubPage 的 LazyColumn 负责，这里不套 verticalScroll
    // （嵌套滚动容器会拿到无限高度约束而崩溃）。
    val scheme = MiuixTheme.colorScheme
    Column {
        // 与设置主页同形态：表单按「服务器 / 连接 / 作用范围」三张分组卡排列。
        SettingsGroup("服务器") {
            ZhiTextField(
                value = draft.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = "服务器名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
        }

        SettingsGroup(if (draft.type.needsCommand) "启动参数" else "HTTP 连接") {
            if (draft.type.needsCommand) {
                ZhiTextField(
                    value = draft.command,
                    onValueChange = { v -> onChange { it.copy(command = v) } },
                    label = "启动命令",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                FieldError(draft.commandError)
                ZhiTextField(
                    value = draft.argsText,
                    onValueChange = { v -> onChange { it.copy(argsText = v) } },
                    label = "命令参数（每行一个）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                ZhiTextField(
                    value = draft.envText,
                    onValueChange = { v -> onChange { it.copy(envText = v) } },
                    label = "环境变量 JSON（可选）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                FieldError(draft.envError)
            } else {
                ZhiTextField(
                    value = draft.url,
                    onValueChange = { v -> onChange { it.copy(url = v) } },
                    label = "服务器 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                FieldError(draft.urlError)
                ZhiTextField(
                    value = draft.headersText,
                    onValueChange = { v -> onChange { it.copy(headersText = v) } },
                    label = "请求头 JSON（可选）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
        }

        Text(
            text = "「名称」用来在 Agent 调用时标识这台服务器，改动名称等于换了一台" +
                "（原名称的记录会被覆盖）。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
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
