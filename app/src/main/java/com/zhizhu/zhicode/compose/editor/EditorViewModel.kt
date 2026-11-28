package com.zhizhu.zhicode.compose.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorScreenState(
    val path: String = "",
    val fileName: String = "",
    /** 正在读盘。期间页面显示加载态。 */
    val loading: Boolean = true,
    /** 与编辑器共享的文本模型；为 null 表示还没读到（或读失败）。 */
    val content: Content? = null,
    /** 这份文件真正用的编码，保存时按它写回。 */
    val charsetName: String = "UTF-8",
    val writable: Boolean = false,
    /** 用户改过且尚未保存。 */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** 非空即「未保存修改」的退出确认框正在显示。 */
    val exitConfirm: Boolean = false,
    /** 读盘失败：整页显示错误（文件不存在 / 二进制 / 过大 / 权限）。 */
    val error: String? = null,
    /** 保存失败：弹窗说明原因，正文与页面都留在原地。 */
    val saveError: String? = null,
    /** 顶栏副标题里的短提示（例如「已保存」），非空时覆盖「只读」。 */
    val notice: String? = null,
)

class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorScreenState())
    val state: StateFlow<EditorScreenState> = _state.asStateFlow()

    /** 进行中的保存任务（「取消」要能真的取消）。 */
    private var saveJob: Job? = null

    /** 正文的修改计数。 */
    private var editVersion = 0

    /** 至少成功保存过一次。Activity 用它决定 `finish()` 时回给主界面 RESULT_OK。 */
    var savedAtLeastOnce: Boolean = false
        private set

    fun hasDirty(): Boolean = _state.value.dirty

    /** 读盘并建立文本模型。切文件、或换一个编码重读都走它。 */
    fun load(path: String, forceCharset: String? = null) {
        val name = runCatching { File(path).name }.getOrDefault(path)
        _state.update {
            it.copy(
                path = path,
                fileName = name,
                loading = true,
                error = null,
                saveError = null,
                notice = null,
                exitConfirm = false,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { EditorFileIo.load(path, forceCharset) }
            }
            _state.update { current ->
                // 读盘期间可能又换了一次文件：过期的结果不能覆盖新页面。
                if (current.path != path) return@update current
                result.fold(
                    onSuccess = { loaded ->
                        editVersion = 0
                        current.copy(
                            loading = false,
                            content = loaded.content,
                            charsetName = loaded.charsetName,
                            writable = loaded.writable,
                            dirty = false,
                            error = null,
                        )
                    },
                    onFailure = { failure ->
                        current.copy(
                            loading = false,
                            content = null,
                            error = failure.message ?: "打开失败",
                        )
                    },
                )
            }
        }
    }

    /** 换一个编码重新读盘。正文会被换成一个新的 `Content` 实例。 */
    fun reload(charsetName: String?) {
        val path = _state.value.path
        if (path.isEmpty() || charsetName == null) return
        if (charsetName == _state.value.charsetName) return
        // 用户指定的编码要传下去：不传的话 load 会重新自动识别，菜单点了等于没点。
        load(path, forceCharset = charsetName)
    }

    /** 正文被用户改动。只置标志位 —— 文本本身已经是共享的那个实例。 */
    fun markDirty() {
        editVersion++
        _state.update { if (it.dirty) it else it.copy(dirty = true, notice = null) }
    }

    /** 写回磁盘。 */
    fun save(onDone: (Boolean) -> Unit = {}) {
        val current = _state.value
        if (current.saving) return
        val content = current.content ?: return onDone(false)
        val path = current.path
        val charsetName = current.charsetName
        // 导出一次字符串就够：真正写盘在 IO 线程，编辑器那边继续可写。
        val payload = content.toString()
        val version = editVersion

        _state.update { it.copy(saving = true, saveError = null, notice = null) }
        saveJob = viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { EditorFileIo.save(path, payload, charsetName) }
            // 保存期间用户可能已经把文件关了/换了一个：过期的结果只用来关掉 saving。
            _state.update { state ->
                if (state.path != path) {
                    state.copy(saving = false)
                } else {
                    state.copy(
                        saving = false,
                        // 只有"这次保存覆盖的就是当前正文"时才清脏。
                        dirty = if (error == null && version == editVersion) false else state.dirty,
                        saveError = error,
                        notice = if (error == null) "已保存" else null,
                    )
                }
            }
            if (error == null) savedAtLeastOnce = true
            onDone(error == null)
        }
    }

    /** 取消进行中的保存（写入本身不可中断，这里只保证界面与状态不再等它）。 */
    fun cancelSave() {
        saveJob?.cancel()
        saveJob = null
        _state.update { it.copy(saving = false, notice = null) }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    fun requestExitConfirm() = _state.update { it.copy(exitConfirm = true) }

    fun cancelExitConfirm() = _state.update { it.copy(exitConfirm = false) }

    /** 用户选了「不保存，退出」：确认框关掉即可，关页面由 Activity 负责。 */
    fun discardAndExit() = _state.update { it.copy(exitConfirm = false) }
}
