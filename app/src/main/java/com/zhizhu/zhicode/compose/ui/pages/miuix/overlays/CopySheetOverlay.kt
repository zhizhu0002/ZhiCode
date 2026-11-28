package com.zhizhu.zhicode.compose.ui.pages.miuix.overlays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.CopySheetState
import com.zhizhu.zhicode.compose.ui.floatingNavSheetInset
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CopySheetOverlay(
    sheet: CopySheetState?,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
) {
    // 退场期间仍需要一份内容，否则抽屉会先空成一条再滑走。
    val shown = rememberLastNonNull(sheet) ?: return
    OverlayBottomSheet(
        show = sheet != null,
        title = shown.title,
        onDismissRequest = onDismiss,
        // 顶栏右侧就是唯一的动作：整条复制。抽屉本身不承担"提交"语义，
        // 所以不给左侧按钮 —— 拖拽与点遮罩都能关，多一个「取消」只是噪音。
        endAction = {
            TextButton(
                text = "复制全部",
                onClick = onCopyAll,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 为悬浮导航胶囊让位：抽屉在 popupHost 槽位，读不到内容区的
                // `LocalFloatingNavInset`（详见 `floatingNavSheetInset` 的说明）。
                .padding(bottom = 8.dp + floatingNavSheetInset()),
        ) {
            /* 原文装进一张 Miuix `Card`（底色走 [ZhiSheetCardColors]）。 */
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ZhiSheetCardColors(),
            ) {
                // 高度上限：长回复可以几千字，不设上限会把抽屉撑到顶住状态栏
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    // 原文照抄，不做 Markdown 渲染：这里的目的是**取字**
                    Text(
                        text = shown.text,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
