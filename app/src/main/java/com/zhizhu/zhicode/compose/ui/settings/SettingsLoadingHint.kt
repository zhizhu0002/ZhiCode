package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsLoadingHint(text: String = "正在读取…") {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // progress = null 走 Miuix 的不确定态分支（自带 rotate + sweep 无限动画）。
        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
        Text(
            text = text,
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
    }
}
