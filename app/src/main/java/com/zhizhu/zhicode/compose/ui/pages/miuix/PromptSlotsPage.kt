package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.PromptSlot
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsFootnote
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PromptsPage(
    initPrompt: String,
    summaryPrompt: String,
    compactPromptHead: String,
    onDismiss: () -> Unit,
    onChange: (PromptSlot, String) -> Unit,
    onReset: (PromptSlot) -> Unit,
) {
    // 正在编辑哪个槽位。它是一个纯界面状态（不落盘），所以放在这一层 ——
    // 与 RoleCardsPage 的 `editor` 同一层级、同一写法。
    var editing by remember { mutableStateOf<PromptSlot?>(null) }
    // 退场动画期间 editing 已经是 null，编辑页还要把内容画完 —— 见 rememberLastNonNull。
    val shownEditing = rememberLastNonNull(editing)

    val custom = mapOf(
        PromptSlot.INIT to initPrompt,
        PromptSlot.COMPACT_SYSTEM to summaryPrompt,
        PromptSlot.COMPACT_HEAD to compactPromptHead,
    )

    SettingsPageStack(
        // 路径含最底下那一页：栈要靠整条路径算层级与方向。
        path = listOf(
            SettingsPageKey("prompts.list", 0),
            if (editing != null) SettingsPageKey("prompts.edit", 1) else null,
        ).filterNotNull(),
        // 编辑页只退一层（回列表），列表页才关整页。分层由页面栈负责，这里只接结果。
        onBack = if (editing == null) onDismiss else { { editing = null } },
    ) { key ->
        // ⚠️ 分支必须看**正在渲染的那一页**（key），不能看当前状态 ——
        // 否则退场动画期间那一页会渲染成空白。
        val open = if (key.id == "prompts.edit") shownEditing else null
        if (open == null) {
            SettingsSubPage(title = "提示词", onBack = onDismiss) {
                PromptSlotList(
                    custom = custom,
                    onEdit = { editing = it },
                )
            }
        } else {
            SettingsSubPage(title = open.title, onBack = { editing = null }) {
                PromptSlotEditor(
                    slot = open,
                    // 编辑框里展示的是**实际生效**的那一份：空串时展开成默认文案，
                    // 这样用户一进来就能看见自己在改什么，而不是面对一个空框。
                    value = open.resolve(custom[open]),
                    isCustom = open.resolveOrEmpty(custom[open]).isNotEmpty(),
                    onChange = { onChange(open, it) },
                    onReset = { onReset(open) },
                )
            }
        }
    }
}

/** 空串判定：用来决定那一行要不要显示「已自定义」标记。 */
private fun PromptSlot.resolveOrEmpty(value: String?): String = value?.trim().orEmpty()

@Composable
private fun PromptSlotList(
    custom: Map<PromptSlot, String>,
    onEdit: (PromptSlot) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsFootnote(
            "这三项都只影响某一件事的措辞，不参与权限与安全判断。" +
                "主系统提示不开放编辑——它规定了「不许声称做了没做的事」，那一条不能松。",
        )
        SettingsGroup("可自定义") {
            PromptSlot.entries.forEach { slot ->
                val isCustom = slot.resolveOrEmpty(custom[slot]).isNotEmpty()
                BasicComponent(
                    title = slot.title,
                    titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                    // 副标题是**压缩成一行**的预览：提示词里换行很多，
                    // 不压成一行的话，两行的样子会把行尾的「›」顶到上面去。
                    summary = PromptSlot.preview(slot.resolve(custom[slot])),
                    summaryColor = BasicComponentDefaults.summaryColor(
                        color = scheme.onSurfaceVariantSummary,
                    ),
                    endActions = {
                        Text(
                            text = if (isCustom) "已自定义" else "默认",
                            color = if (isCustom) scheme.primary else scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            fontWeight = if (isCustom) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    },
                    onClick = { onEdit(slot) },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PromptSlotEditor(
    slot: PromptSlot,
    value: String,
    isCustom: Boolean,
    onChange: (String) -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 同上：显式宽度，让卡片与输入框只有一个宽度来源。
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsFootnote(slot.detail)
        SettingsGroup("可用变量") {
            Text(
                text = slot.variables,
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        SettingsGroup("正文") {
            ZhiTextField(
                value = value,
                onValueChange = onChange,
                label = slot.title,
                useLabelAsPlaceholder = true,
                singleLine = false,
                minLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        SettingsGroup("操作") {
            BasicComponent(
                title = "恢复默认",
                titleColor = BasicComponentDefaults.titleColor(
                    // 没有自定义时它是**不可用**的：置灰而不是隐藏，
                    // 这样"这里本来有个按钮"这件事仍然看得见。
                    color = if (isCustom) scheme.onBackground else scheme.onSurfaceVariantSummary,
                ),
                summary = if (isCustom) {
                    "清空自定义内容，回到内置文案（${slot.defaultValue.length} 字）"
                } else {
                    "当前已经是内置文案，没有可恢复的内容"
                },
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                // 用 BasicComponent 而不是带箭头的 SettingsEntry：这里不是"进入下一页"，
                // 而是一个就地生效的动作。
                onClick = { if (isCustom) onReset() },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        SettingsFootnote("改动立刻生效并落盘，不需要保存。")
    }
}
