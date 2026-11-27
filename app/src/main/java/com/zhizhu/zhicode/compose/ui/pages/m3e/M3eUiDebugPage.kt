package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel

@Composable
internal fun M3eUiDebugPage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    onBack: () -> Unit,
    onExportSession: (String) -> Unit,
    onImportSession: () -> Unit,
) {
    var checked by remember(state.uiDebugResetToken) { mutableStateOf(true) }
    var text by remember(state.uiDebugResetToken) { mutableStateOf("") }
    var slider by remember(state.uiDebugResetToken) { mutableStateOf(0.5f) }
    val log = remember(state.uiDebugResetToken) { mutableStateListOf<String>() }
    M3eSubPageScaffold(
        title = "UI 调试",
        onBack = onBack,
        actions = { TextButton(onClick = { viewModel.requestUiDebugReset(); log.clear() }) { Text("重置") } },
    ) { padding, _ ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M3eSectionHeader("Expressive")
            M3eSegmentedColumn {
                M3eSegmentedItem(0, 1) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("标题", style = MaterialTheme.typography.headlineSmall)
                        Text("正文、辅助信息与标签", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { log.add(0, "按钮") }) { Text("主要操作") }
                            OutlinedButton(onClick = { log.add(0, "次要按钮") }) { Text("次要操作") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked, { checked = it; log.add(0, "开关 $it") })
                            Text("开关")
                            Checkbox(checked, { checked = it; log.add(0, "复选框 $it") })
                            Text("复选框")
                        }
                        Slider(slider, { slider = it; log.add(0, "滑块 ${it.toInt()}") })
                        OutlinedTextField(text, { text = it }, label = { Text("输入框") }, modifier = Modifier.fillMaxWidth())
                        LinearProgressIndicator(progress = { slider }, modifier = Modifier.fillMaxWidth())
                        CircularProgressIndicator()
                    }
                }
            }
            M3eSectionHeader("会话")
            M3eSegmentedColumn {
                M3eNavigationRow(
                    index = 0, count = 2, title = "导出当前会话",
                    summary = state.activeSession?.title.orEmpty(),
                    onClick = {
                        val id = state.activeSessionId
                        if (id.isNotBlank()) onExportSession("session-${id.take(8)}.jsonl.gz")
                    },
                )
                M3eNavigationRow(index = 1, count = 2, title = "导入会话", onClick = onImportSession)
            }
            M3eSectionHeader("组件状态")
            M3eSegmentedColumn {
                M3eSegmentedItem(0, 1) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("状态：${state.message?.ifBlank { "就绪" } ?: "就绪"}")
                        Text("运行环境：${if (state.runtimeReady) "已就绪" else "未安装"}")
                        Text("当前模型：${state.modelLabel}")
                        Text("上下文：${state.contextTokens} / ${state.contextWindow}")
                    }
                }
            }
            if (log.isNotEmpty()) {
                M3eSectionHeader("交互日志")
                M3eSegmentedColumn {
                    log.take(12).forEachIndexed { index, entry ->
                        M3eSegmentedItem(index, log.take(12).size) { Text(entry, modifier = Modifier.padding(14.dp)) }
                    }
                }
            }
        }
    }
}
