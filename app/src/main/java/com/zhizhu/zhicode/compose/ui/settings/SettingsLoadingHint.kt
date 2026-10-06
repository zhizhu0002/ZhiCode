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

/**
 * 二级设置页的「正在读取」占位。
 *
 * ## 为什么需要它
 *
 * 各个 `openXxx()` 入口被改成了**先同步推页、数据后读**（详见
 * `WorkspaceViewModel.openApiConfig` 的注释）。好处是点击那一帧就有东西可画，
 * 转场立刻开始；代价是页面存在一段"载荷还是空的"的窗口。
 *
 * 而「列表是空的」在这些页面上本来就是个**合法状态**（新项目就是没有技能、
 * 没有角色卡、没有 MCP 服务器）。所以如果什么都不画，用户会把「正在读」
 * 误读成「我的配置丢了」—— 这是纯粹的误导。
 *
 * 尺寸与那几句"还没有…"的说明文字保持一致（同样的 16/12 内边距、Footnote 字号），
 * 于是数据到位时是内容替换，不是整块跳一下。
 */
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
