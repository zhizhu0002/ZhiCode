package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.RiskLevel

@Composable
internal fun M3ePermissionOverlay(
    request: PermissionRequest?,
    onDecision: (String, Boolean, Boolean) -> Unit,
) {
    if (request == null) return
    var alwaysAllow by remember(request.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDecision(request.id, false, false) },
        title = { Text("工具授权") },
        text = {
            Column {
                Text(request.tool, style = MaterialTheme.typography.titleMedium)
                Text(request.subtitle, style = MaterialTheme.typography.bodyMedium)
                if (request.riskLevel == RiskLevel.HIGH) {
                    Text("高风险", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    request.detail,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = alwaysAllow, onCheckedChange = { alwaysAllow = it })
                    Text("始终允许", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(request.id, true, alwaysAllow) }) { Text("允许") }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(request.id, false, false) }) { Text("拒绝") }
        },
    )
}
