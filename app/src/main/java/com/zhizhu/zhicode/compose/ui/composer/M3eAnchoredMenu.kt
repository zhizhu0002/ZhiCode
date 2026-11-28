@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.ui.ZhiIcons

/** 自带展开状态的锚定菜单：给它一个触发控件，它负责弹菜单。 */
@Composable
internal fun M3eAnchorMenu(
    groups: List<M3eMenuGroup>,
    modifier: Modifier = Modifier,
    trigger: @Composable (toggle: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        trigger { expanded = !expanded }
        M3eAnchoredMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = groups,
        )
    }
}

/** 输入器底排用的药丸高度。 */
internal val M3eChipHeight = 36.dp

/** 输入器底排的透明药丸（可点、可高亮）。 */
@Composable
internal fun M3ePill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    trailingChevron: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (highlighted) scheme.primary else scheme.onSurfaceVariant
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.height(M3eChipHeight),
        shape = CircleShape,
        color = if (highlighted) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailingChevron) {
                Icon(
                    painter = ZhiIcons.expand,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 输入器底排的「标签 + 下拉」药丸。 */
@Composable
internal fun M3eTextMenuChip(
    label: String,
    groups: List<M3eMenuGroup>,
    modifier: Modifier = Modifier,
) {
    M3eAnchorMenu(groups = groups, modifier = modifier) { toggle ->
        M3ePill(
            label = label,
            onClick = toggle,
            modifier = Modifier.fillMaxWidth(),
            trailingChevron = true,
        )
    }
}
/** 下拉菜单里的一项。 */
internal data class M3eMenuItem(
    val text: String,
    val summary: String? = null,
    val selected: Boolean = false,
    val icon: Painter? = null,
    val onClick: () -> Unit,
)

/** 下拉菜单里的一组（组与组之间画一条分隔线）。 */
internal data class M3eMenuGroup(val items: List<M3eMenuItem>)

/** 锚定在芯片上的 M3E 下拉菜单。 */
@Composable
internal fun M3eAnchoredMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<M3eMenuGroup>,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 240.dp, max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .heightIn(min = 1.dp),
                    )
                }
                group.items.forEach { item ->
                    M3eMenuRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun M3eMenuRow(item: M3eMenuItem) {
    val scheme = MaterialTheme.colorScheme
    val container = if (item.selected) scheme.secondaryContainer else scheme.surfaceContainer
    val content = if (item.selected) scheme.onSecondaryContainer else scheme.onSurface
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val icon = item.icon
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.selected) {
                    Icon(
                        painter = ZhiIcons.done,
                        contentDescription = "已选中",
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            val summary = item.summary
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.selected) content.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
