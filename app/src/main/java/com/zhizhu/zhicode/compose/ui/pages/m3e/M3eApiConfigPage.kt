package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.ApiProtocol
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.pi.PiCatalogAndPresets
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.PiProviderPreset
import com.zhizhu.zhicode.pi.PiThinkingLevel
import kotlinx.coroutines.launch

@Composable
internal fun M3eApiConfigPage(
    config: ApiConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
    onApplyPreset: (PiProviderPreset) -> Unit,
    onFetchDraftModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>>,
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit,
    onOpenPrompts: () -> Unit,
) {
    if (config == null) return
    val draft = config.form
    val back = {
        when {
            draft == null -> onDismiss()
            !draft.isEditing && !draft.pickingPreset -> onDraftChange { it.copy(pickingPreset = true) }
            config.standalone -> onDismiss()
            else -> onCancelForm()
        }
    }
    BackHandler(enabled = draft != null, onBack = back)
    val title = when {
        draft == null -> "供应商与模型管理"
        draft.pickingPreset -> "选择供应商模板"
        draft.isEditing -> "编辑供应商"
        else -> "新增供应商"
    }
    M3eSubPageScaffold(
        title = title,
        onBack = back,
        actions = {
            when {
                draft == null -> TextButton(onClick = onNew) { Text("新增") }
                draft.pickingPreset -> Unit
                else -> TextButton(onClick = onSave, enabled = draft.saveable) { Text("保存") }
            }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                draft == null -> ApiProfiles(
                    config = config,
                    onEdit = onEdit,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onUpdateContextAndPrompt = onUpdateContextAndPrompt,
                    onOpenPrompts = onOpenPrompts,
                )
                draft.pickingPreset -> Presets(
                    onPick = onApplyPreset,
                    onCustom = { onApplyPreset(PiCatalogAndPresets.presets.first { it.isCustom }) },
                )
                else -> ApiDraftForm(
                    draft = draft,
                    onChange = onDraftChange,
                    onFetchModels = onFetchDraftModels,
                    onChangePreset = { onDraftChange { it.copy(pickingPreset = true) } },
                )
            }
        }
    }
}

@Composable
private fun ApiProfiles(
    config: ApiConfigState,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit,
    onOpenPrompts: () -> Unit,
) {
    M3eSectionHeader("已配置供应商")
    when {
        config.loading -> Text("正在加载…", modifier = Modifier.padding(horizontal = 16.dp))
        config.profiles.isEmpty() -> Text("暂无供应商", modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (config.profiles.isNotEmpty()) M3eSegmentedColumn {
        config.profiles.forEachIndexed { index, profile ->
            M3eSegmentedItem(index, config.profiles.size, onClick = { onSelect(profile.id) }) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(profile.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (profile.id == config.activeId) Text("使用中", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { onEdit(profile) }) { Text("编辑") }
                        TextButton(onClick = { onDelete(profile.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    M3eSectionHeader("上下文与提示词")
    M3eSegmentedColumn {
        M3eSwitchRow(
            index = 0, count = if (config.autoCompact) 4 else 3, title = "上下文压缩",
            checked = config.autoCompact,
            onCheckedChange = { onUpdateContextAndPrompt(it, config.autoCompactPercent, config.customSystemPrompt) },
        )
        if (config.autoCompact) M3eSliderRow(
            index = 1, count = 4, title = "自动压缩上限", value = config.autoCompactPercent.toFloat(),
            valueText = "${config.autoCompactPercent}%",
            valueRange = COMPACT_PERCENT_MIN.toFloat()..COMPACT_PERCENT_MAX.toFloat(),
            steps = COMPACT_PERCENT_MAX - COMPACT_PERCENT_MIN - 1,
            onValueChange = { onUpdateContextAndPrompt(true, it.toInt(), config.customSystemPrompt) },
        )
        OutlinedTextField(
            value = config.customSystemPrompt,
            onValueChange = { onUpdateContextAndPrompt(config.autoCompact, config.autoCompactPercent, it) },
            label = { Text("自定义头部提示词") }, minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        M3eNavigationRow(
            index = if (config.autoCompact) 2 else 1, count = if (config.autoCompact) 4 else 3,
            title = "提示词模板", onClick = onOpenPrompts,
        )
    }
}

@Composable
private fun Presets(onPick: (PiProviderPreset) -> Unit, onCustom: () -> Unit) {
    M3eSectionHeader("自定义")
    M3eSegmentedColumn {
        M3eNavigationRow(index = 0, count = 1, title = "自定义供应商", summary = "手动填写接口与模型", onClick = onCustom)
    }
    val presets = remember { PiCatalogAndPresets.presets.filterNot { it.isCustom } }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(0) }
    val categories = listOf("全部", "官方", "云服务", "本地", "聚合 / 第三方")
    val visible = presets.filter { preset ->
        val matchesCategory = when (category) {
            1 -> preset.category == "official" || preset.category == "cn_official"
            2 -> preset.category == "cloud_provider"
            3 -> preset.category == "local"
            4 -> preset.category == "aggregator" || preset.category == "third_party"
            else -> true
        }
        val needle = query.trim()
        matchesCategory && (needle.isBlank() || listOf(preset.name, preset.providerKey, preset.baseUrl, preset.api.label).any { it.contains(needle, ignoreCase = true) })
    }
    OutlinedTextField(query, { query = it }, label = { Text("搜索供应商") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    M3eSegmentedColumn {
        M3eChoiceRow(0, 1, "分类", categories, category, onSelect = { category = it })
    }
    M3eSectionHeader("预设")
    M3eSegmentedColumn {
        visible.forEachIndexed { index, preset ->
            M3eNavigationRow(
                index = index, count = presets.size, title = preset.name,
                summary = "${preset.api.label} · ${preset.baseUrl.ifBlank { "无需 Base URL" }}",
                onClick = { onPick(preset) },
            )
        }
    }
}

@Composable
private fun ApiDraftForm(
    draft: ApiProfileDraft,
    onChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onFetchModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>>,
    onChangePreset: () -> Unit,
) {
    var showProtocols by remember { mutableStateOf(false) }
    var modelToAdd by remember { mutableStateOf("") }
    var fetchBusy by remember { mutableStateOf(false) }
    var fetchMessage by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf(emptyList<ModelOption>()) }
    val scope = rememberCoroutineScope()
    M3eSectionHeader("基本信息")
    val sourceCount = if (draft.isEditing) 1 else 2
    M3eSegmentedColumn {
        if (!draft.isEditing) M3eNavigationRow(index = 0, count = sourceCount, title = "供应商模板", valueText = draft.presetId, onClick = onChangePreset)
        M3eNavigationRow(index = if (draft.isEditing) 0 else 1, count = sourceCount, title = "接口协议", valueText = draft.protocol.label, onClick = { showProtocols = true })
    }
    ApiTextField("名称", draft.name, { onChange { d -> d.copy(name = it) } })
    ApiTextField("Provider ID", draft.providerKey, { onChange { d -> d.copy(providerKey = it) } })
    if (draft.protocol != ApiProtocol.BEDROCK_CONVERSE_STREAM && draft.protocol != ApiProtocol.DEBUG_SCRIPTED) {
        ApiTextField("Base URL", draft.baseUrl, { onChange { d -> d.copy(baseUrl = it) } })
    }
    ApiTextField("API Key", draft.apiKey, { onChange { d -> d.copy(apiKey = it) } }, secret = true)
    draft.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
    draft.baseUrlError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

    M3eSectionHeader("模型")
    if (draft.models.isNotEmpty()) M3eSegmentedColumn {
        draft.models.forEachIndexed { index, model ->
            ApiModelEditor(
                model = model,
                selected = draft.model == model.id,
                index = index,
                count = draft.models.size,
                onSelect = { onChange { it.copy(model = model.id) } },
                onChange = { updated -> onChange { current -> current.copy(models = current.models.mapIndexed { i, old -> if (i == index) updated else old }, model = if (current.model == model.id) updated.id else current.model) } },
                onRemove = { onChange { it.copy(models = it.models.filterIndexed { i, _ -> i != index }, model = if (it.model == model.id) it.models.getOrNull(index + 1)?.id ?: it.models.getOrNull(index - 1)?.id.orEmpty() else it.model) } },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(modelToAdd, { modelToAdd = it }, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val id = modelToAdd.trim()
            if (id.isNotEmpty() && draft.models.none { it.id == id }) {
                val model = PiModelConfig(id = id)
                onChange { it.copy(models = it.models + model, model = it.model.ifBlank { id }) }
                modelToAdd = ""
            }
        }) { Text("添加") }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = !fetchBusy, onClick = {
            fetchBusy = true
            fetchMessage = ""
            scope.launch {
                val result = onFetchModels(draft)
                fetchBusy = false
                fetchedModels = result.getOrDefault(emptyList())
                fetchMessage = result.exceptionOrNull()?.message ?: "获取到 ${fetchedModels.size} 个模型"
            }
        }) { Text(if (fetchBusy) "获取中…" else "从服务获取模型") }
        if (fetchMessage.isNotBlank()) Text(fetchMessage, style = MaterialTheme.typography.bodySmall)
    }
    fetchedModels.forEach { option ->
        TextButton(onClick = {
            if (draft.models.none { it.id == option.id }) {
                val model = PiModelConfig(id = option.id, name = option.displayName)
                onChange { it.copy(models = it.models + model, model = it.model.ifBlank { model.id }) }
            }
        }) { Text("添加 ${option.displayName}") }
    }

    M3eSectionHeader("高级选项")
    ApiTextField("自定义请求头（JSON）", draft.extraHeaders, { onChange { d -> d.copy(extraHeaders = it) } }, multiline = true)
    ApiTextField("兼容参数（JSON）", draft.compatJson, { onChange { d -> d.copy(compatJson = it) } }, multiline = true)

    if (showProtocols) AlertDialog(
        onDismissRequest = { showProtocols = false },
        title = { Text("接口协议") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ApiProtocol.entries.filter { !it.debugOnly || com.zhizhu.zhicode.compose.BuildConfig.DEBUG }.forEach { protocol ->
                    TextButton(onClick = { showProtocols = false; onChange { it.copy(protocol = protocol) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(protocol.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showProtocols = false }) { Text("关闭") } },
    )
}

@Composable
private fun ApiTextField(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = !multiline, minLines = if (multiline) 4 else 1,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ApiModelEditor(
    model: PiModelConfig,
    selected: Boolean,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
    onChange: (PiModelConfig) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(model.id) { mutableStateOf(false) }
    var contextWindow by remember(model.id, model.contextWindow) { mutableStateOf(model.contextWindow.toString()) }
    var maxTokens by remember(model.id, model.maxTokens) { mutableStateOf(model.maxTokens.toString()) }
    M3eSegmentedItem(index = index, count = count, containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(model.name.ifBlank { model.id }, style = MaterialTheme.typography.titleMedium)
                    Text(model.id, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "设置") }
                TextButton(onClick = onRemove) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
            if (expanded) {
                ApiTextField("模型 ID", model.id, { onChange(model.copy(id = it)) })
                ApiTextField("显示名称", model.name, { onChange(model.copy(name = it)) })
                ApiTextField("上下文窗口", contextWindow, { raw -> contextWindow = raw.filter(Char::isDigit); contextWindow.toIntOrNull()?.let { onChange(model.copy(contextWindow = it)) } })
                ApiTextField("最大输出 Token", maxTokens, { raw -> maxTokens = raw.filter(Char::isDigit); maxTokens.toIntOrNull()?.let { onChange(model.copy(maxTokens = it)) } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(model.reasoning, { onChange(model.copy(reasoning = it)) }); Text("推理")
                    Checkbox(model.supportsImage, { onChange(model.copy(supportsImage = it)) }); Text("图像")
                }
                M3eSectionHeader("Thinking 档位")
                PiThinkingLevel.entries.forEach { level ->
                    val configured = model.thinkingLevelMap[level]
                    val enabled = if (level in model.thinkingLevelMap) configured != null else level !in setOf(PiThinkingLevel.XHIGH, PiThinkingLevel.MAX)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(enabled, { checked ->
                            onChange(model.copy(thinkingLevelMap = model.thinkingLevelMap + (level to if (checked) level.id else null)))
                        })
                        Text(level.id)
                        if (enabled) OutlinedTextField(
                            value = configured ?: level.id,
                            onValueChange = { onChange(model.copy(thinkingLevelMap = model.thinkingLevelMap + (level to it))) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ApiTextField("模型兼容参数（JSON）", model.compatJson, { onChange(model.copy(compatJson = it)) }, multiline = true)
            }
        }
    }
}
