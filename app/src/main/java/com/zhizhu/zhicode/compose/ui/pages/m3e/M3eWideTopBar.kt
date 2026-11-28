@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.ui.ZhiIcons

/**
 * 宽屏左栏顶栏。
 *
 * 只有宽屏会走这里（窄屏的顶栏在 [M3eWorkspacePage] 里），
 * 所以不需要处理菜单键与分段标签 —— 那些在各自的栏里。
 */
@Composable
internal fun M3eWideTopBar(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    onOpenSidebar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val brandTitle = LocalThemeSettings.current.resolvedTopBrand
    val busy = state.composerBusy

    Surface(modifier = modifier, color = scheme.surfaceContainerLow, tonalElevation = 2.dp) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = brandTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (busy) "工作中 · ${state.modelLabel}" else state.modelLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (busy) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.deviceStatus,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                FilledTonalIconButton(onClick = onSettings) {
                    Icon(painter = ZhiIcons.settings, contentDescription = "打开设置")
                }
            }
            if (busy) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                HorizontalDivider(color = scheme.outlineVariant)
            }
        }
    }
}
