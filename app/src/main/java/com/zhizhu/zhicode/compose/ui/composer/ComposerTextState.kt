package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf

@Stable
class ComposerTextState {

    /** 输入框自己的文字状态。`TextFieldState` 内部的变更**不**触发外部重组。 */
    val field: TextFieldState = TextFieldState()

    /** 每个会话的草稿。 */
    private val drafts = mutableStateMapOf<String, String>()

    /** 当前文字（只读快照；不要在组合期每帧读它来驱动布局）。 */
    fun text(): String = field.text.toString()

    fun isEmpty(): Boolean = field.text.isBlank()

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

    /** 把「最后一个 `@` 记号」替换成 [replacement]（`@` 补全选中时用）。 */
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
