package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf

/**
 * 发送框里**文字**的所有权。
 *
 * ## 为什么要有这个类
 *
 * 原来输入框的文字存在 `WorkspaceUiState.composerText` 里，每次按键都走
 *
 *     _state.update { it.copy(composerText = text) }
 *
 * 而 `WorkspaceUiState` 是一个装着**整份 transcript** 的 `data class`。
 * 后果不只是"复制一次很贵"，更要紧的是：`copy()` 产出的新实例会让
 * `ChatList(state = state)` 这个参数变化 —— 于是**用户每敲一个字符，
 * 整个对话流就重组一次**（含所有可见消息卡的 Markdown）。
 *
 * 这不是推理题：`ZhiFrameTrace` 的重组计数里，按键期间 `ChatList` 与 `Composer`
 * 同频跳，而 `Composer` 的停止键/附件条根本没变。
 *
 * 参考实现（rikkahub `ChatInputState.kt`）把文字放在 `TextFieldState` 里由界面持有，
 * 状态层完全不参与打字 —— 这里采用同样的划分。
 *
 * ## 所有权边界（重要，别越界）
 *
 * | 东西 | 归谁 |
 * | --- | --- |
 * | 正在输入的文字 | **本类**（`field`）。按键只失效输入框那一层 |
 * | 每个会话的草稿 | **本类**（`drafts`）。切会话时存/取 |
 * | 附件 | 状态层（`WorkspaceUiState.attachments`）。附件不是按键产物 |
 * | 斜杠命令的匹配结果 | 状态层。它需要命令表，且会弹面板 |
 * | 生成中/可停止 | 状态层 |
 *
 * ## 与状态层如何同步
 *
 * 只有三处需要跨过边界，都是**低频**动作：
 *
 *  1. **发送**：读 `field.text.toString()` 交给 `viewModel.send(...)`，然后 `clear()`；
 *  2. **斜杠查询**：文字以 `/` 开头且还没打空格时，把查询串交给状态层算匹配列表；
 *  3. **切会话**：`saveDraft(旧)` + `restoreDraft(新)`。
 *
 * 另外状态层可以**单向**往这里写（"编辑后发送"、"恢复草稿"这类外部动作），
 * 走 [setText]。
 */
@Stable
class ComposerTextState {

    /** 输入框自己的文字状态。`TextFieldState` 内部的变更**不**触发外部重组。 */
    val field: TextFieldState = TextFieldState()

    /**
     * 每个会话的草稿。
     *
     * 用 `mutableStateMapOf` 而不是普通 `MutableMap`：`drafts` 的读写只发生在切会话时，
     * 但它是 `@Stable` 类里的可变字段，用快照容器才能让"当前有没有草稿"这类
     * 读取（如果有的话）行为正确。容量上界 = 用户实际切换过的会话数，
     * 每个值是一个字符串 —— 没有无界增长的风险。
     */
    private val drafts = mutableStateMapOf<String, String>()

    /** 当前文字（只读快照；不要在组合期每帧读它来驱动布局）。 */
    fun text(): String = field.text.toString()

    fun isEmpty(): Boolean = field.text.isBlank()

    /**
     * 从外部写入文字，并把光标放到末尾。
     *
     * ⚠️ 只在**外部动作**里调用（编辑后发送、恢复草稿、填入模板）。
     * 不要在 `onValueChange` 里回调它 —— 那会把用户的输入再写一遍，
     * 光标会被踢到末尾。
     */
    fun setText(value: String) {
        if (field.text.toString() != value) {
            field.setTextAndPlaceCursorAtEnd(value)
        }
    }

    /** 追加一段文字（斜杠命令补全、文件引用补全都用它）。 */
    fun append(value: String) {
        field.setTextAndPlaceCursorAtEnd(field.text.toString() + value)
    }

    /** 发送之后清空输入框，但**不**动草稿（草稿由切会话负责）。 */
    fun clear() {
        field.setTextAndPlaceCursorAtEnd("")
    }

    /** 把这个会话当前的文字存成草稿。切走之前调用。 */
    fun saveDraft(sessionId: String) {
        if (sessionId.isEmpty()) return
        val current = field.text.toString()
        if (current.isBlank()) drafts.remove(sessionId) else drafts[sessionId] = current
    }

    /** 切到这个会话时，把它的草稿取回输入框（没有草稿就是空的）。 */
    fun restoreDraft(sessionId: String) {
        val draft = if (sessionId.isEmpty()) "" else drafts[sessionId].orEmpty()
        field.setTextAndPlaceCursorAtEnd(draft)
    }

    /** 丢弃某个会话的草稿（会话被删除时调用，避免泄漏一条字符串）。 */
    fun dropDraft(sessionId: String) {
        drafts.remove(sessionId)
    }

    /**
     * 把「最后一个 `@` 记号」替换成 [replacement]（`@` 补全选中时用）。
     *
     * 规则：从末尾往前找最后一个 `@`，把从它到末尾的内容整段换掉，
     * 并在后面补一个空格 —— 否则用户接着输入会粘在路径后面。
     *
     * 找不到 `@` 时原样追加：那说明调用时机不对（文字里已经没有触发记号了），
     * 此时"什么也不做"会让用户以为补全坏了，而追加至少不会丢内容。
     */
    fun replaceLastAtToken(replacement: String) {
        val text = field.text.toString()
        val at = text.lastIndexOf('@')
        val next = if (at < 0) {
            text + replacement + " "
        } else {
            text.substring(0, at) + replacement + " "
        }
        field.setTextAndPlaceCursorAtEnd(next)
    }
}
