package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.ApiProtocol
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * API 配置窗口：列表页与编辑表单**共用一个弹窗**。
 *
 * [config] 的 `form` 非空即显示表单，否则显示列表 —— 对应原版的两张页面。
 *
 * 密钥处理：
 * - 已保存的密钥**永远不回填**到输入框（`ApiProfile.apiKey` 恒为空串）；
 * - 编辑时留空 = 沿用原密钥，输入内容 = 替换密钥。
 *   这个语义必须在界面上写清楚，否则用户会以为"留空等于清空密钥"而不敢动表单。
 */
@Composable
fun ApiConfigOverlay(
    config: ApiConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
) {
    val form = config?.form

    OverlayDialog(
        show = config != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 640.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val current = config ?: return@OverlayDialog
        if (form == null) {
            ApiProfileList(
                config = current,
                onNew = onNew,
                onEdit = onEdit,
                onSelect = onSelect,
                onDelete = onDelete,
                onClose = onDismiss,
            )
        } else {
            ApiProfileForm(
                draft = form,
                onChange = onDraftChange,
                onSave = onSave,
                onCancel = onCancelForm,
            )
        }
    }
}

@Composable
private fun ApiProfileList(
    config: ApiConfigState,
    onNew: () -> Unit,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "API 配置记录",
        groupBody = true,
        actions = {
            SecondaryButton(text = "关闭", onClick = onClose)
            PrimaryButton(text = "新增", onClick = onNew, modifier = Modifier.padding(start = 8.dp))
        },
    ) {
        if (config.profiles.isEmpty()) {
            // 空列表是正常的初始状态（应用不再自带任何厂商配置），
            // 但只显示一行"没有数据"会让人以为坏了。写清下一步做什么。
            Text(
                text = "还没有 API 配置。点右上角「新增」填写你自己服务的 Base URL、协议与模型名 —— " +
                    "应用不预置任何厂商地址，API Key 会存进系统加密存储。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        config.profiles.forEach { profile ->
            val active = profile.id == config.activeId
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = if (active) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                    contentColor = scheme.onBackground,
                ),
                pressFeedbackType = PressFeedbackType.None,
            ) {
                BasicComponent(
                    title = profile.name,
                    titleColor = BasicComponentDefaults.titleColor(
                        color = if (active) scheme.primary else scheme.onBackground,
                    ),
                    summary = profile.summary,
                    summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                    // 选中的那条用勾表示"当前生效"，未选中的给一个空位保持左对齐一致
                    startAction = {
                        if (active) {
                            Checkbox(
                                state = ToggleableState.On,
                                onClick = {},
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    endActions = {
                        ZhiIconButton(
                            icon = ZhiIcons.edit,
                            description = "编辑",
                            onClick = { onEdit(profile) },
                            iconSize = 15.dp,
                        )
                        // 每条都可以删——包括那条从旧版本留下来的厂商配置。
                        // 以前这里拦着不画按钮，理由是"引擎会拒绝删除官方记录"；
                        // 现在没有官方记录这个概念了，禁止删除只剩下一个后果：
                        // 用户清不掉一个自己不要的地址。
                        ZhiIconButton(
                            icon = ZhiIcons.close,
                            description = "删除",
                            onClick = { onDelete(profile.id) },
                            iconSize = 15.dp,
                        )
                    },
                    onClick = { if (!active) onSelect(profile.id) },
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        Text(
            text = "新增或编辑后，密钥会存进系统加密存储（AndroidKeyStore）。" +
                "编辑已有配置时密钥框留空表示沿用原密钥。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun ApiProfileForm(
    draft: ApiProfileDraft,
    onChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // 地址是否走明文 HTTP：部分自建网关只有 http。这个开关对应引擎里的
    // usesCleartextTraffic 场景，正常应保持关闭。
    var allowCleartext by remember(draft.id) { mutableStateOf(draft.baseUrl.startsWith("http://")) }
    var visionEnabled by remember(draft.id) { mutableStateOf(true) }

    // 明文开关与地址前缀强绑定：勾上就把 http:// 规范成 http://，
    // 不勾就把 http:// 改成 https://。避免出现"地址是 http 但开关是关的"这种自相矛盾状态。
    LaunchedEffect(allowCleartext) {
        val url = draft.baseUrl
        if (allowCleartext && url.startsWith("https://")) {
            onChange { it.copy(baseUrl = "http://" + url.removePrefix("https://")) }
        } else if (!allowCleartext && url.startsWith("http://")) {
            onChange { it.copy(baseUrl = "https://" + url.removePrefix("http://")) }
        }
    }

    DialogShell(
        title = if (draft.isEditing) "编辑 API 配置" else "新增 API 配置",
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
        TextField(
            value = draft.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = "名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        // 协议：用 Miuix 的 OverlayDropdownPreference（行尾显示当前值 + 下拉箭头）。
        // 不用 SuperArrow —— 那个 API 在 Miuix 0.9.4 里已经不存在（`extra` 包已移除）。
        OverlayDropdownPreference(
            items = ApiProtocol.entries.map { it.label },
            selectedIndex = ApiProtocol.entries.indexOf(draft.protocol).coerceAtLeast(0),
            title = "协议",
            summary = null,
            onSelectedIndexChange = { index ->
                ApiProtocol.entries.getOrNull(index)?.let { protocol ->
                    onChange { it.copy(protocol = protocol) }
                }
            },
        )

        TextField(
            value = draft.baseUrl,
            onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
            label = "Base URL",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        )

        SwitchPreference(
            title = "允许明文 HTTP",
            summary = if (allowCleartext) "当前地址走 http（不加密）" else "仅使用 https",
            checked = allowCleartext,
            onCheckedChange = { allowCleartext = it },
        )

        TextField(
            value = draft.apiKey,
            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
            label = if (draft.isEditing) "API Key（留空 = 沿用原密钥）" else "API Key",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        )

        TextField(
            value = draft.model,
            onValueChange = { value -> onChange { it.copy(model = value) } },
            label = "模型名",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        SwitchPreference(
            title = "发送图片（Vision）",
            summary = "关闭后不会把图片块发给模型，用于不支持视觉的模型",
            checked = visionEnabled,
            onCheckedChange = { visionEnabled = it },
        )

        // 表单级校验失败时直接说清是哪一项，而不是只把保存键置灰。
        val error = draft.nameError ?: draft.baseUrlError
        if (error != null) {
            Text(
                text = error,
                color = ZhiColors.red(),
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
