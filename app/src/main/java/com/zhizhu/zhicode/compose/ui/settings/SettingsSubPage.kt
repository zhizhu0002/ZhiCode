package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 设置的二级整页（API 配置 / MCP / 技能 / 角色卡 / 记忆），与设置主页同款骨架：
 * Miuix 原生 Scaffold + SmallTopAppBar（返回键 = MiuixIcons.Back）+ LazyColumn 滚动。
 *
 * 以前这些页面是 OverlayDialog 弹窗壳（DialogShell + 底部按钮排），和整页设置
 * 并存时观感割裂 —— 弹窗宽度封顶、滚动高度受限、按钮位置和整页不一致。
 * 收敛到一个壳之后，二级页和主页只有标题与动作差异。
 *
 * [action]（如「新增 / 保存」）放顶栏 TextButton，与设置主页的「保存」同位。
 */
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    action: Pair<String, (() -> Unit)?>? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                scrollBehavior = topAppBarScrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = scheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (action != null && action.second != null) {
                        TextButton(text = action.first, onClick = action.second!!)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "subPageBody") { content() }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }
}
