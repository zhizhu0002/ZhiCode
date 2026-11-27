package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown

@Composable
internal fun M3ePlanApprovalOverlay(
    plan: PlanApproval?,
    onApprove: () -> Unit,
    onRevise: () -> Unit,
    onReviseWithFeedback: (String) -> Unit,
) {
    if (plan == null) return
    var feedback by remember(plan.id, plan.revision) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onRevise,
        title = { Text("${plan.title} · revision ${plan.revision}") },
        text = {
            Column {
                if (plan.path.isNotEmpty()) Text(plan.path, fontFamily = FontFamily.Monospace)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    ZhiMarkdown(source = plan.body)
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("继续规划时填写反馈（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (plan.permissionNote.isNotEmpty()) Text(plan.permissionNote)
            }
        },
        confirmButton = { TextButton(onClick = onApprove) { Text("批准并开始") } },
        dismissButton = { TextButton(onClick = { onReviseWithFeedback(feedback) }) { Text("继续规划") } },
    )
}
