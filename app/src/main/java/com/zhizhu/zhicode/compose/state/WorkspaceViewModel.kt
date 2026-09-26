package com.zhizhu.zhicode.compose.state

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhizhu.zhicode.compose.data.ApiConfigStore
import com.zhizhu.zhicode.compose.data.AttachmentReader
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.data.FileBrowser
import com.zhizhu.zhicode.compose.data.GitChanges
import com.zhizhu.zhicode.compose.data.McpStore
import com.zhizhu.zhicode.compose.data.MockWorkspaceRepository
import com.zhizhu.zhicode.compose.data.ModelCatalogStore
import com.zhizhu.zhicode.compose.data.SessionReader
import com.zhizhu.zhicode.compose.data.RoleCardStore
import com.zhizhu.zhicode.compose.data.MemoryStore
import com.zhizhu.zhicode.compose.data.SkillStore
import com.zhizhu.zhicode.compose.data.WorkspacePaths
import com.zhizhu.zhicode.compose.data.WorkspaceRepository
import com.zhizhu.zhicode.compose.engine.EngineEvents
import com.zhizhu.zhicode.compose.engine.EngineOverrides
import com.zhizhu.zhicode.compose.engine.EnginePlanApproval
import com.zhizhu.zhicode.compose.engine.ZhiEngineController
import com.zhizhu.zhicode.compose.engine.ToolText
import com.zhizhu.zhicode.compose.engine.toEngineEffort
import com.zhizhu.zhicode.compose.engine.toEngineMode
import com.zhizhu.zhicode.compose.engine.toUiPlanApproval
import com.zhizhu.zhicode.compose.runtime.EnvDoctor
import com.zhizhu.zhicode.RuntimeInstaller
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.Attachment
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.ChoiceIntent
import com.zhizhu.zhicode.compose.model.ChoiceOption
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.DiffState
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.model.MemoryEditor
import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.ModelPickerState
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.PermissionRequest
import com.zhizhu.zhicode.compose.model.QueuedPrompt
import com.zhizhu.zhicode.compose.model.PlanApproval
import com.zhizhu.zhicode.compose.model.RiskLevel
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.SLASH_COMMANDS
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SettingsCategory
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.SlashCommand
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.model.WebSearchProvider
import com.termux.app.zhicode.core.PlanApprovalGate
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面状态机。
 *
 * 数据来自 [WorkspaceRepository]（现在只剩终端占位横幅项）；对话流、工具执行、会话文件
 * 后续换成真实引擎实现即可，UI 层不用改。
 *
 * 继承 [AndroidViewModel] 而不是 `ViewModel`，是为了拿到 `Application`：
 * 内置 Termux 环境的安装与自检都需要 Context。
 * 这样做还有一个实际好处 —— `viewModel()` 的默认工厂本来就支持 `AndroidViewModel`，
 * 所以 `ZhiCodeApp()` 里那行默认参数不用改、也不用自己写 Factory。
 */
class WorkspaceViewModel(
    application: Application,
    private val repo: WorkspaceRepository = MockWorkspaceRepository(),
) : AndroidViewModel(application), EngineEvents {

    /** 内置 Termux 环境安装器（第一次使用前会解压 32MB 的 bootstrap）。 */
    private val installer = RuntimeInstaller(application)

    /** 真实 IQ Code 引擎。懒创建，第一次发消息时才初始化。 */
    private val engine = ZhiEngineController(application, this)

    // ------------------------------------------------------------------
    // 运行期状态（属于"这一轮正在发生什么"，不是界面状态，因此不进 StateFlow）
    // ------------------------------------------------------------------

    /** 当前正在流式输出的助手气泡 id。为 null 表示还没有任何 delta。 */
    private var streamingAssistantId: String? = null

    /** 当前回合的工具分组 id。一个工具批次 = 一张分组卡。 */
    private var currentGroupId: String? = null

    /** 引擎给的工具 id → 它落在哪个分组卡里。用于把结果/进度写回正确的位置。 */
    private data class LiveTool(val groupId: String, val name: String, val command: String)

    private val liveTools = mutableMapOf<String, LiveTool>()

    /**
     * 工具实时输出的合并缓冲。
     *
     * `onToolProgress` 的粒度是"每次进程写出一点"，Bash 跑一条 `apt install` 能到几千次。
     * 每次都改 StateFlow 会把界面拖垮，所以按 [PROGRESS_FLUSH_MS] 合并后再落盘。
     */
    private val progressBuffers = mutableMapOf<String, StringBuilder>()
    private val progressLastFlush = mutableMapOf<String, Long>()

    /** 正在等待用户回执的权限请求 id。 */
    private var pendingPermissionId: String? = null

    /**
     * 当前这一轮属于哪个会话（`activeSessionId` 的快照）。
     *
     * 用途只有一个：错误迟到时判断它该不该落到**现在**这个会话里。
     * 用户在等待回复时切走会话，这一轮会被 `engine.cancel()` 打断，但错误回调
     * 仍可能之后到达；没有这个标记就会把错误卡写进新会话（详见 [finishTurnWithError]）。
     *
     * 为 null 表示"当前没有归属待判定的回合"，此时不拦截。
     */
    private var turnSessionId: String? = null

    /**
     * 正在进行的「提问」流程。
     *
     * 引擎的 `AskUserQuestion` 可以一次带多个问题，界面是**分步**展示的，
     * 所以必须把请求 id、问题列表、当前步骤、已收集的答案都留着 ——
     * 只存"当前这一步"的话，走到第二步就丢了前面的答案。
     */
    private class PendingQuestions(
        val requestId: String,
        val questions: JSONArray,
        var index: Int = 0,
        val answers: JSONObject = JSONObject(),
    )

    private var pendingQuestions: PendingQuestions? = null




    /**
     * 会话操作的目标。
     *
     * `showSessionActions(session)` 只负责弹选择器，真正的动作在选择器回调里执行；
     * 那时只能拿到选项文案，拿不到是哪条会话。所以必须把目标记住 ——
     * 否则会出现"想删 A 结果删了当前会话"这类错删。
     */
    private var pendingSessionAction: SessionSummary? = null
    private var pendingMessageAction: ChatItem? = null

    private var modelCatalogJob: Job? = null

    /**
     * 会话级模型覆盖。
     *
     * 为什么不能只写配置记录：官方 API 记录在引擎层是**不可变的**——
     * `ApiSettingsStore.saveProfile` 对官方记录强制回写 `name/baseUrl/defaultModel`
     * （`OFFICIAL_PROFILE_ID` 分支），所以往官方记录写模型会静默失效。
     * 原版的处理方式是同时把模型写进**当前会话配置**并 `engine.configure()`；
     * 这里用同一口径：持久化照写（自定义配置有效），即时生效靠这个覆盖值。
     *
     * 切换 API 配置时清空——换了配置就该用那份配置自己的模型。
     */
    private var modelOverride: String? = null

    /**
     * 会话级角色卡覆盖。
     *
     * 与 [modelOverride] 同理：磁盘上的卡片清单与"实际生效内容"是两份数据，
     * 引擎的 SessionConfig 只认后者。空串 ≠ null —— 空串表示"确实停用了"（要覆盖掉
     * 配置里的旧值），null 表示"本轮不覆盖"（会退回旧值，于是停用看起来没生效）。
     */
    private var roleCardOverride: String? = null


    private val _state = MutableStateFlow(
        WorkspaceUiState(
            // 项目路径是真实工作区（$HOME/workspace），不再是开发本应用时用的那个路径。
            // 它在运行环境装好后由 [ensureWorkspace] 创建，见那里的说明。
            projectName = WorkspacePaths.nameOf(WorkspacePaths.defaultProject()),
            projectPath = WorkspacePaths.defaultProject(),
            // 会话列表 / 对话历史 / 任务清单都来自磁盘，这里先给空值，
            // 由 [initSessionState] 在 IO 线程读好后填上。
            // 不在构造器里同步读盘：会话 JSONL 可能有几千行，会拖慢启动。
            sessions = emptyList(),
            activeSessionId = "",
            transcript = emptyList(),
            tasks = emptyList(),
            contextTokens = 0,
            contextWindow = 200_000,
            deviceStatus = clockLabel(),
            // 变更面板首屏为空；git 是要真实执行命令的，等用户点「刷新」再读。
            diff = DiffState(),
            terminalLines = repo.terminalBanner(WorkspacePaths.defaultProject()),
            // 文件面板走真实文件系统。只列**一层**（不递归）：内置 Termux 环境装好后
            // home 下可能有几千个文件，递归会拖慢启动。
            filePath = WorkspacePaths.defaultProject(),
            fileEntries = emptyList(),
            busySessionIds = emptySet(),
            composerBusy = false,
            workingStatus = null,
            sidebarOpen = false,
            // 真实探测，不再是写死的 true
            runtimeReady = installer.isInstalled(),
        )
    )

    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        initSessionState()
    }

    /**
     * 首次载入：列出磁盘上的真实会话，有历史就把最近一条打开。
     *
     * 「自动打开最近一条」是刻意保留的行为：否则用户每次启动都看到空对话流，
     * 会以为历史丢了（历史其实在侧栏里，但要自己去点）。
     */
    private fun initSessionState() {
        viewModelScope.launch(Dispatchers.IO) {
            // 顶栏的模型名/密钥状态要反映**真实生效**的配置，
            // 否则用户会看到一个跟实际请求无关的模型名。
            syncActiveProfile()
            syncRoleCardFromStore()
            ensureWorkspace()
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { it.copy(sessions = sessions) }
            sessions.firstOrNull()?.let { openSession(it) }
        }
    }

    /**
     * 确保当前项目目录存在。
     *
     * 两个时机都需要：运行环境刚装好（home 此时才出现）、以及每次启动
     * （环境是上次装的）。**必须在列会话之前调用** —— 会话按项目目录分键存储，
     * 目录不存在时会话会落在一个"幽灵项目"键下，之后换了路径就找不回来了。
     */
    private fun ensureWorkspace() {
        val path = _state.value.projectPath
        // 目录已存在：只需按真实文件系统算出列表与说明。
        if (WorkspacePaths.exists(path)) {
            reloadFiles()
            return
        }
        // 目录不存在，且运行环境还没装（home 目录都还没出现）：
        // 此时**不能**建目录（父目录不存在），但也**不能什么都不说** ——
        // 否则文件面板会显示"0 项"却没有任何解释。
        if (!installer.isInstalled()) {
            _state.update {
                it.copy(
                    fileEntries = emptyList(),
                    fileNote = "工作区还不存在：$path\n初始化内置 Termux 环境后会自动建立。",
                )
            }
            return
        }
        WorkspacePaths.ensure(path)
        reloadFiles()
    }

    /** 刷新文件面板当前目录的列表，并同步 [WorkspaceUiState.fileNote]。 */
    private fun reloadFiles() {
        val path = _state.value.filePath
        val entries = FileBrowser.children(path)
        val note = when {
            entries.isNotEmpty() -> ""
            !java.io.File(path).isDirectory -> if (java.io.File(path).exists()) "不是目录：$path" else "目录不存在：$path"
            else -> ""
        }
        _state.update { it.copy(fileEntries = entries, fileNote = note) }
    }

    private var idCounter = 0L

    private fun nextId(prefix: String): String = "$prefix-${++idCounter}"

    // ---------- 输入器 ----------

    // ---------- 任务清单 ----------

    /** 点悬浮任务卡：打开任务详情窗口，查看**全部**任务（卡片只显示当前窗口）。 */
    fun openTaskList() = _state.update { it.copy(taskListOpen = true) }

    fun closeTaskList() = _state.update { it.copy(taskListOpen = false) }

    fun onComposerChange(text: String) {        val query = if (text.startsWith("/") && !text.contains(' ') && !text.contains('\n')) text else null
        _state.update {
            it.copy(
                composerText = text,
                slashQuery = query,
                slashMatches = if (query == null) emptyList()
                else SLASH_COMMANDS.filter { c -> c.name.startsWith(query.lowercase(Locale.US)) },
            )
        }
    }

    fun pickSlashCommand(command: SlashCommand) {
        _state.update {
            it.copy(composerText = command.name + " ", slashQuery = null, slashMatches = emptyList())
        }
    }

    fun dismissSlashPalette() {
        _state.update { it.copy(slashQuery = null, slashMatches = emptyList()) }
    }

    /**
     * 附件载荷。
     *
     * **故意不放进 `WorkspaceUiState`**：图片是几 MB 的字节数组，塞进不可变状态里会让
     * 每次 `copy()` 都拖着它，而且 Compose 判断状态是否变化时要对它做 `equals`——
     * 每帧比较几 MB 是纯粹的浪费。界面只需要名字和大小，真正的字节留在这里，
     * 发送时再取。
     */
    private class AttachmentPayload(val bytes: ByteArray?, val mimeType: String)

    private val attachmentPayloads = mutableMapOf<String, AttachmentPayload>()

    /**
     * 附加一张图片（来自系统选择器）。
     *
     * 读盘与大小校验都在 IO 线程：10 MB 的图片读进来是实打实的耗时。
     */
    fun attachImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { AttachmentReader.readImage(getApplication(), uri) }
            result.fold(
                onSuccess = { image ->
                    val id = nextId("att")
                    attachmentPayloads[id] = AttachmentPayload(image.bytes, image.mimeType)
                    _state.update { s ->
                        s.copy(
                            attachments = s.attachments + Attachment(
                                id = id,
                                label = image.name,
                                // 「PNG · 2.4 MB」两个信息都来自真实读数，不是写死的文案。
                                detail = "${image.formatLabel} · ${image.sizeLabel}",
                                isImage = true,
                            ),
                            message = "已添加图片：${image.name}",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(message = "图片读取失败：${error.message ?: "未知原因"}") }
                },
            )
        }
    }

    /**
     * 把待发图片编成引擎要的内容块。
     *
     * 结构对齐原版 `buildImageBlocks()`：`{type:image, source:{type:base64, media_type, data}, name}`。
     * 是否真的发给模型由引擎按 `visionEnabled` 决定（`VisionMessageFilter`），
     * 这里不重复判断——两处都判会出现"界面拦了但引擎其实允许"这类不一致。
     */
    private fun buildImageBlocks(): JSONArray {
        val blocks = JSONArray()
        _state.value.attachments.filter { it.isImage }.forEach { attachment ->
            val payload = attachmentPayloads[attachment.id] ?: return@forEach
            val bytes = payload.bytes ?: return@forEach
            if (bytes.isEmpty()) return@forEach
            runCatching {
                blocks.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", payload.mimeType)
                                .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)),
                        )
                        .put("name", attachment.label),
                )
            }
        }
        return blocks
    }

    /** 清空附件：界面条目与载荷必须一起走，否则载荷留在 map 里没人回收。 */
    private fun clearAttachments() {
        attachmentPayloads.clear()
        _state.update { it.copy(attachments = emptyList()) }
    }

    /**
     * 把待发的**文本**附件拼进提示词。
     *
     * 结构照抄原版 `buildPromptWithAttachments`：包成 `<attached_context>` 块，
     * 每个附件一个 `<attachment name="...">`。用 XML 风格的包裹是有意的——
     * 模型对"这段是附带资料、不是用户指令"的分界需要明确标记，
     * 直接把技能内容贴在提示词后面会让它读成用户要求。
     *
     * 只有存在文本附件时才加包裹层；一个都没有就原样返回，
     * 避免每条消息都拖着空标签。
     */
    private fun buildPromptWithTextAttachments(prompt: String): String {
        val texts = _state.value.attachments.filter { !it.isImage && it.textBody != null }
        if (texts.isEmpty()) return prompt
        return buildString {
            append(prompt).append("\n\n<attached_context>")
            texts.forEach { attachment ->
                append("\n<attachment name=\"").append(attachment.label.replace("\"", "'")).append("\">\n")
                val body = attachment.textBody.orEmpty()
                append(if (body.length > TEXT_ATTACHMENT_LIMIT) body.take(TEXT_ATTACHMENT_LIMIT) + "\n[…attachment truncated…]" else body)
                append("\n</attachment>")
            }
            append("\n</attached_context>")
        }
    }

    // ---------- Skill ----------

    fun openSkills() {
        _state.update { it.copy(skills = SkillsState(skills = SkillStore.list(it.projectPath))) }
    }

    fun closeSkills() = _state.update { it.copy(skills = null) }

    fun newSkill() = _state.update {
        it.copy(skills = it.skills?.copy(createForm = SkillCreateDraft()))
    }

    fun updateSkillCreateDraft(transform: (SkillCreateDraft) -> SkillCreateDraft) = _state.update {
        val skills = it.skills ?: return@update it
        val form = skills.createForm ?: return@update it
        it.copy(skills = skills.copy(createForm = transform(form)))
    }

    fun cancelSkillCreate() = _state.update {
        it.copy(skills = it.skills?.copy(createForm = null))
    }

    /** 新建：建目录 + 写模板，然后**直接进入编辑器**（用户下一步必然是要写内容）。 */
    fun createSkill() {
        val s = _state.value
        val form = s.skills?.createForm ?: return
        if (!form.saveable) return
        val name = form.name.trim()
        val result = SkillStore.create(s.projectPath, form.scope, name)
        result.fold(
            onSuccess = {
                val read = SkillStore.read(s.projectPath, form.scope, name)
                val body = read.getOrElse { "" }
                _state.update { state ->
                    state.copy(
                        skills = SkillsState(
                            skills = SkillStore.list(state.projectPath),
                            editing = SkillEditTarget(
                                name = name,
                                scope = form.scope,
                                path = SkillStore.fileOf(state.projectPath, form.scope, name).absolutePath,
                                body = body,
                            ),
                        ),
                        message = if (it) "已创建技能 $name" else "技能 $name 已存在，直接打开编辑",
                    )
                }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "创建失败：${error.message ?: "未知原因"}") }
            },
        )
    }

    fun editSkill(entry: SkillEntry) {
        val s = _state.value
        val body = SkillStore.read(s.projectPath, entry.scope, entry.name).getOrElse { error ->
            _state.update { it.copy(message = "打开失败：${error.message ?: "未知原因"}") }
            return
        }
        _state.update {
            it.copy(
                skills = it.skills?.copy(
                    createForm = null,
                    editing = SkillEditTarget(entry.name, entry.scope, entry.path, body),
                ),
            )
        }
    }

    fun updateSkillBody(body: String) = _state.update {
        val skills = it.skills ?: return@update it
        val editing = skills.editing ?: return@update it
        it.copy(skills = skills.copy(editing = editing.copy(body = body)))
    }

    fun cancelSkillEdit() = _state.update {
        it.copy(skills = it.skills?.copy(editing = null))
    }

    fun saveSkill() {
        val s = _state.value
        val editing = s.skills?.editing ?: return
        val result = SkillStore.save(s.projectPath, editing.scope, editing.name, editing.body)
        _state.update {
            it.copy(
                skills = SkillsState(skills = SkillStore.list(it.projectPath)),
                message = if (result.isSuccess) "技能 ${editing.name} 已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    fun deleteSkill(entry: SkillEntry) {
        val result = SkillStore.delete(_state.value.projectPath, entry.scope, entry.name)
        _state.update {
            it.copy(
                skills = SkillsState(skills = SkillStore.list(it.projectPath)),
                message = if (result.isSuccess) "已删除技能 ${entry.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * 把技能附加到下一步任务。
     *
     * 走**文本附件**路径而不是直接塞进输入框：输入框是用户的编辑区，
     * 往里面灌几百行技能内容会让人没法继续写自己的话。
     * 附加后内容由 [buildPromptWithTextAttachments] 在发送时包进 `<attached_context>`。
     */
    fun attachSkill(entry: SkillEntry) {
        val body = SkillStore.read(_state.value.projectPath, entry.scope, entry.name).getOrElse { error ->
            _state.update { it.copy(message = "附加失败：${error.message ?: "未知原因"}") }
            return
        }
        val id = nextId("skill")
        _state.update { s ->
            // 同名技能只留一条：连着点两次「附加」应该还是那一份，而不是叠两份进提示词。
            val kept = s.attachments.filterNot { it.label == "skill:${entry.name}" }
            s.copy(
                attachments = kept + Attachment(
                    id = id,
                    label = "skill:${entry.name}",
                    detail = "${entry.scope.label} · ${entry.sizeLabel}",
                    isImage = false,
                    textBody = body,
                ),
                message = "已附加技能：${entry.name}",
            )
        }
    }

    // ---------- 角色卡 ----------

    /**
     * 打开角色卡管理器。
     *
     * 角色卡与技能的区别值得说清楚：技能是"按需加载的资料"（Agent 主动调用
     * `Skill` 工具，或用户手动附加一次），角色卡是**长期生效的人设指令**——
     * 引擎把启用中的内容作为 `<role_card>` 块拼进每一次系统提示词。
     */
    fun openRoleCards() {
        val context = getApplication<android.app.Application>()
        _state.update {
            it.copy(
                roleCards = RoleCardsState(
                    cards = RoleCardStore.list(context),
                    activeId = RoleCardStore.activeId(context),
                ),
                settingsDraft = null,
            )
        }
    }

    fun closeRoleCards() = _state.update { it.copy(roleCards = null) }

    fun newRoleCard() = _state.update {
        it.copy(roleCards = it.roleCards?.copy(editor = RoleCardEditor()))
    }

    fun editRoleCard(card: RoleCard) = _state.update {
        it.copy(
            roleCards = it.roleCards?.copy(
                editor = RoleCardEditor(id = card.id, name = card.name, content = card.content),
            ),
        )
    }

    fun updateRoleCardDraft(transform: (RoleCardEditor) -> RoleCardEditor) = _state.update {
        val state = it.roleCards ?: return@update it
        val editor = state.editor ?: return@update it
        it.copy(roleCards = state.copy(editor = transform(editor)))
    }

    fun cancelRoleCardEditor() = _state.update {
        it.copy(roleCards = it.roleCards?.copy(editor = null))
    }

    /**
     * 保存角色卡并**立即启用**。
     *
     * 与原版一致：保存一张卡就意味着"我现在要用它"，所以同时写清单、写 activeId、
     * 并把内容灌进引擎配置。分三步做是必须的——只写清单不写 activeId 的话，
     * 用户会看到卡保存成功却没有任何效果。
     */
    fun saveRoleCard() {
        val editor = _state.value.roleCards?.editor ?: return
        if (!editor.saveable) return
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.upsert(
            context,
            RoleCard(id = editor.id, name = editor.name.trim(), content = editor.content),
        )
        result.fold(
            onSuccess = { saved ->
                roleCardOverride = saved.content
                refreshRoleCards(message = "角色卡「${saved.name}」已保存并启用")
                runCatching { engine.configure(engineOverrides()) }
            },
            onFailure = { error ->
                _state.update { it.copy(message = "保存失败：${error.message ?: "未知原因"}") }
            },
        )
    }

    /** 启用一张已存在的卡。 */
    fun selectRoleCard(card: RoleCard) {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), card.id)
        if (result.isFailure) {
            _state.update { it.copy(message = "启用失败：${result.exceptionOrNull()?.message ?: "未知原因"}") }
            return
        }
        roleCardOverride = card.content
        refreshRoleCards(message = "已启用角色卡：${card.name}")
        runCatching { engine.configure(engineOverrides()) }
    }

    fun disableRoleCard() {
        val context = getApplication<android.app.Application>()
        val result = RoleCardStore.save(context, RoleCardStore.list(context), "")
        if (result.isFailure) {
            _state.update { it.copy(message = "停用失败：${result.exceptionOrNull()?.message ?: "未知原因"}") }
            return
        }
        // 覆盖值置成空串（而不是 null）：null 表示"不覆盖"，那会退回配置里的旧值，
        // 于是"停用"看起来没生效——角色卡还挂在系统提示词里。
        roleCardOverride = ""
        refreshRoleCards(message = "角色卡已停用")
        runCatching { engine.configure(engineOverrides()) }
    }

    fun deleteRoleCard(card: RoleCard) {
        val context = getApplication<android.app.Application>()
        val wasActive = _state.value.roleCards?.activeId == card.id
        val result = RoleCardStore.delete(context, card.id)
        if (result.isFailure) {
            _state.update { it.copy(message = "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}") }
            return
        }
        if (wasActive) {
            roleCardOverride = ""
            runCatching { engine.configure(engineOverrides()) }
        }
        refreshRoleCards(message = "已删除角色卡：${card.name}")
    }

    private fun refreshRoleCards(message: String) {
        val context = getApplication<android.app.Application>()
        _state.update {
            it.copy(
                roleCards = RoleCardsState(
                    cards = RoleCardStore.list(context),
                    activeId = RoleCardStore.activeId(context),
                ),
                message = message,
            )
        }
    }

    /**
     * 把磁盘上启用中的角色卡内容读进会话覆盖值。
     *
     * 启动时调用一次：`SessionConfig.load()` 不带 roleCard（引擎只按 `applyProfile`
     * 填协议/地址/模型），所以不主动读的话，重启后角色卡在列表里显示"已启用"
     * 却不会被注入请求——正是那种"看起来配好了其实没生效"的状态。
     */
    private fun syncRoleCardFromStore() {
        val content = runCatching { RoleCardStore.activeContent(getApplication()) }.getOrDefault("")
        // 空串表示"确实没有启用的卡"，也要覆盖掉配置里的旧值，所以这里不能判空就跳过。
        roleCardOverride = content
    }

    // ---------- 记忆文件（IQ.md） ----------

    fun openMemory() {
        _state.update { it.copy(memory = MemoryState(files = MemoryStore.list(it.projectPath))) }
    }

    fun closeMemory() = _state.update { it.copy(memory = null) }

    /**
     * 打开 IQ 沙箱管理界面（`SandboxDashboardActivity`）。
     *
     * 与「环境弹窗」不同，这里**不能**只改本进程的 UI 状态：沙箱引擎整个跑在
     * `:iqsandbox` 进程里，管理界面通过 `${applicationId}.sandbox.control` 这个同 UID
     * 私有 provider 与它通信（见 SandboxControlProvider）。所以这里必须真的
     * 启动那个 Activity，而不是弹一个本地的 Compose 面板 —— 后者会得到一个
     * "永远连不上引擎"的空壳界面。
     *
     * ViewModel 手里只有 Application 上下文，从 Application 启动 Activity
     * 必须带 FLAG_ACTIVITY_NEW_TASK，否则直接抛 AndroidRuntimeException。
     * 失败时如实上报，不静默吞掉（例如清单里少声明了该 Activity）。
     */
    fun openSandbox() {
        val context = getApplication<android.app.Application>()
        val started = runCatching {
            context.startActivity(
                android.content.Intent(context, com.zhizhu.zhicode.sandbox.SandboxDashboardActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        started.onFailure { error ->
            appendInfo(
                "无法打开 IQ 沙箱",
                "启动沙箱管理界面失败：${error.javaClass.simpleName}: ${error.message ?: "未知原因"}\n\n" +
                    "沙箱引擎运行在 :iqsandbox 进程，界面必须由系统拉起而无法在本进程内绘制。",
            )
        }
    }


    /** 打开某个记忆文件编辑。文件不存在时给一份空编辑器（即"新建"）。 */
    fun editMemory(file: MemoryFile) {
        val path = _state.value.projectPath
        val body = MemoryStore.read(path, file.scope).getOrElse { error ->
            _state.update { it.copy(message = "打开失败：${error.message ?: "未知原因"}") }
            return
        }
        _state.update {
            it.copy(
                memory = it.memory?.copy(
                    editing = MemoryEditor(scope = file.scope, path = file.path, body = body, exists = file.exists),
                ),
            )
        }
    }

    fun updateMemoryBody(body: String) = _state.update {
        val memory = it.memory ?: return@update it
        val editing = memory.editing ?: return@update it
        it.copy(memory = memory.copy(editing = editing.copy(body = body)))
    }

    fun cancelMemoryEdit() = _state.update {
        it.copy(memory = it.memory?.copy(editing = null))
    }

    fun saveMemory() {
        val state = _state.value
        val editing = state.memory?.editing ?: return
        val result = MemoryStore.save(state.projectPath, editing.scope, editing.body)
        _state.update {
            it.copy(
                memory = MemoryState(files = MemoryStore.list(it.projectPath)),
                message = if (result.isSuccess) "${editing.scope.label} 已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * `/init`：让 Agent 直接创建或完善项目 IQ.md。
     *
     * 指令照抄原版：明确要求**不要**进计划模式、不建任务、不起子 Agent、不做大范围调研，
     * 只读现有的 IQ.md / CLAUDE.md / README 与主构建清单，然后直接写 IQ.md。
     *
     * 两个细节都保留：
     * - 先退出计划模式。计划模式下引擎只产出计划不落盘，`/init` 会变成"只给我一份计划"，
     *   而那显然不是用户想要的。
     * - 对话流里放一条可见的 `/init` 用户气泡：这条请求是应用自己发的，
     *   不显示出来的话用户会看到一条自己没写过的消息引发的回复。
     */
    /** 界面入口：记忆面板里的「让 IQ 完善」按钮。 */
    fun runInitFromUi() = runInitPrompt()

    private fun runInitPrompt() {
        if (engine.isBusy()) {
            appendInfo("初始化项目说明", "IQ 正在执行任务，请先等待或停止当前任务。")
            return
        }
        runCatching { engine.cancelPlanModeFromUi("/init 使用快速直接模式") }

        _state.update {
            it.copy(
                transcript = it.transcript + ChatItem(id = nextId("u"), kind = ChatKind.USER, title = "你", body = "/init"),
                composerBusy = true,
                workingStatus = "正在整理项目说明…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
        startTurn(INIT_INSTRUCTION)
    }

    fun removeAttachment(id: String) {
        attachmentPayloads.remove(id)
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == id }) }
    }

    // ---------- 发送 / 停止 ----------

    fun send() {
        val text = _state.value.composerText.trim()
        if (text.isEmpty()) return
        if (text.startsWith("/")) {
            _state.update { it.copy(composerText = "", slashQuery = null, slashMatches = emptyList()) }
            runSlashCommand(text)
            return
        }
        if (engine.isBusy()) {
            // 预输入，复刻原版 sendPrompt() 的 steering 分支（MainActivity.java:1425-1450）：
            //  1) 用户气泡**立刻**进对话流（原版同样 addChatView(userItem)），这就是可见的确认；
            //  2) 清空输入框与附件；
            //  3) 工作状态文案改成「已预输入，等待当前回复完成…」（原版 showWorkingIndicator）；
            //  4) 文案按当时在执行什么分三种（原版的 wasTool / wasModel / 其它）。
            // ⚠️ 追加到**队尾**（不是覆盖）：连发多条都要保留。
            val userItem = ChatItem(
                id = nextId("u"),
                kind = ChatKind.USER,
                title = "你",
                body = text,
            )
            // 顺序很重要：先编好要发的内容块与拼好提示词，再把附件清掉。
            // 反过来的话队列里这条预输入就永远发不出那张图、也丢掉附加的技能了。
            val imageBlocks = buildImageBlocks()
            val promptWithTextAttachments = buildPromptWithTextAttachments(text)
            clearAttachments()
            _state.update { s ->
                val toolsRunning = s.transcript.lastOrNull()
                    ?.tools?.any { !it.completed } == true
                val queuedNote = when {
                    s.permissionRequest != null || toolsRunning -> "已预输入：当前工具完成后加载"
                    else -> "已预输入：当前回复完成后加载"
                }
                val queue = s.pendingInputs + QueuedPrompt(userItem.id, text)
                s.copy(
                    composerText = "",
                    slashQuery = null,
                    slashMatches = emptyList(),
                    transcript = s.transcript + userItem,
                    pendingInputs = queue,
                    workingStatus = queuedStatus(queue.size),
                    message = queuedNote,
                )
            }
            // 真正的排队交给引擎：它会在「当前工具执行完 / 当前模型回复结束」这个
            // 协议安全点把预输入并入对话，并回调 onQueuedPromptApplied。
            // 界面这边不再自己维护"跑完一轮再发下一条"的递归链（那会与引擎重复排队）。
            val steered = engine.steer(promptWithTextAttachments, userItem.id, imageBlocks)
            if (!steered) {
                // 极端竞态：这里判定为忙，但真正入队时回合已经结束。
                // 不静默丢弃 —— 把文字放回输入框，用户能直接重发。
                _state.update { s ->
                    s.copy(
                        pendingInputs = s.pendingInputs.filterNot { q -> q.chatItemId == userItem.id },
                        composerText = text,
                        message = "上一回合刚好结束，消息未能入队，已放回输入框",
                    )
                }
            }
            return
        }
        val userItem = ChatItem(
            id = nextId("u"),
            kind = ChatKind.USER,
            title = "你",
            body = text,
        )
        // 同上：先编内容块再清附件。
        val imageBlocks = buildImageBlocks()
        clearAttachments()
        _state.update {
            it.copy(
                composerText = "",
                slashQuery = null,
                slashMatches = emptyList(),
                transcript = it.transcript + userItem,
                contextTokens = it.contextTokens + 1_100,
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
        startTurn(buildPromptWithTextAttachments(text), imageBlocks)
    }

    /**
     * 把一条提示词交给真实引擎。
     *
     * 会话配置**每一轮都重新下发**：用户在设置里改了权限模式/推理强度/上下文窗口后，
     * 不需要重启应用就能在下一轮生效。
     */
    private fun startTurn(prompt: String, extraContent: JSONArray? = null) {
        // 记下这一轮属于哪个会话。放在这里而不是各调用点：`startTurn` 是**唯一漏斗**
        // （普通消息与 /init 都走它），只需维护一处；错误迟到时靠它判断该不该落地。
        turnSessionId = _state.value.activeSessionId
        val configured = runCatching { engine.configure(engineOverrides()) }
        if (configured.isFailure) {
            // 配置阶段就失败（例如 SharedPreferences 损坏）时不要静默：
            // 否则界面会一直停在"正在思考…"，用户只能干等。
            finishTurnWithError("引擎配置失败：" + (configured.exceptionOrNull()?.message ?: "未知原因"))
            return
        }
        runCatching { engine.sendPrompt(prompt, extraContent) }.onFailure { error ->
            finishTurnWithError(ToolText.friendlyError(error.message, error))
        }
    }

    /**
     * 把当前界面状态翻译成引擎覆盖项。
     *
     * `model` **故意留空**：界面上的 `modelLabel` 目前还是演示标签，真正生效的模型
     * 来自 API 配置记录（`ApiSettingsStore`）。在这里覆盖会用一个不存在的模型名把请求打挂；
     * 等设置页接线（Phase 5）之后再把模型选择器接上。
     */
    private fun engineOverrides(): EngineOverrides {
        val s = _state.value
        return EngineOverrides(
            permissionMode = s.permissionMode.toEngineMode(),
            effort = s.effort.toEngineEffort(),
            contextWindow = s.contextWindow,
            projectDirectory = s.projectPath,
            customSystemPrompt = s.settings.customSystemPrompt,
            visionEnabled = s.settings.visionEnabled,
            autoCompact = s.settings.autoCompact,
            autoCompactPercent = s.settings.autoCompactPercent,
            webSearchEnabled = s.settings.webSearchEnabled,
            webSearchProvider = when (s.settings.webSearchProvider) {
                WebSearchProvider.AUTO -> "auto"
                WebSearchProvider.DUCKDUCKGO -> "duckduckgo"
                WebSearchProvider.BING -> "bing"
            },
            webSearchMaxResults = s.settings.webSearchMaxResults,
            webTimeoutSec = s.settings.webSearchTimeoutSec,
            rootExecutionEnabled = s.settings.rootExecutionEnabled,
            sandboxAgentFullAccess = s.settings.sandboxAgentFullAccess,
            forcedKeepAliveEnabled = s.settings.forcedKeepAliveEnabled,
            model = modelOverride,
            roleCard = roleCardOverride,
        )
    }

    fun stop() {
        // 引擎的 cancel() 会一并取消权限/提问/计划三个门控，
        // 所以这里不需要再单独回执一次权限，只要把界面状态收干净。
        engine.cancel()
        pendingPermissionId = null
        // 在途的流式残片与工具进度缓冲全部丢弃：它们属于已经作废的这一轮。
        progressBuffers.clear()
        progressLastFlush.clear()
        liveTools.clear()
        currentGroupId = null
        _state.update {
            // 停止时把还在排队的预输入**还原回输入框**（拼成多行逐条显示），
            // 而不是直接丢弃 —— 否则连发多条再按停止就会漏消息。
            val restored = it.pendingInputs.joinToString("\n") { q -> q.text }
            it.copy(
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = it.busySessionIds - it.activeSessionId,
                composerText = if (restored.isNotEmpty()) restored else it.composerText,
                pendingInputs = emptyList(),
                message = if (it.pendingInputs.isNotEmpty()) {
                    "已停止当前任务，${it.pendingInputs.size} 条预输入已放回输入框"
                } else {
                    "已停止当前任务"
                },
            )
        }
    }

    // ==================================================================
    // 引擎事件逐条落地
    //
    // 线程约定：这些方法**全部在主线程被调用**（ZhiEngineController 负责搬运），
    // 所以可以直接改 StateFlow、不需要再加 post/Handler。
    // 顺序约定：凡是"文本之后紧跟工具/结束"的地方，控制器都已先 flush 过文本缓冲，
    // 否则工具卡会插到还没显示的正文前面。
    // ==================================================================

    override fun onEngineSessionStarted(model: String) {
        streamingAssistantId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        _state.update {
            it.copy(
                composerBusy = true,
                workingStatus = "正在思考…",
                busySessionIds = it.busySessionIds + it.activeSessionId,
            )
        }
    }

    override fun onEngineText(delta: String) {
        val existing = streamingAssistantId
        if (existing == null) {
            // 首个 delta 才创建气泡。原版是回合开始时就插一个空气泡、
            // 结束时再丢弃空的；Compose 里那样会先闪一个空白块，观感更差，
            // 所以改成延迟创建 —— 语义等价（空回复同样不会留下气泡）。
            val id = nextId("a")
            streamingAssistantId = id
            _state.update { s ->
                s.copy(
                    transcript = s.transcript + ChatItem(
                        id = id,
                        kind = ChatKind.ASSISTANT,
                        title = "IQ",
                        body = delta,
                        streaming = true,
                        processSteps = listOf("开始分析请求"),
                    ),
                    workingStatus = "正在回复…",
                )
            }
            return
        }
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id == existing) it.copy(body = it.body + delta) else it
                },
                workingStatus = "正在回复…",
            )
        }
    }

    override fun onEngineThinking(delta: String) {
        val id = streamingAssistantId ?: return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map {
                    if (it.id != id) it
                    // 思考预览按 1200 字符上限保留尾部（原版 liveThinking 的做法），
                    // 否则长思考会把内存和渲染都拖住。
                    else {
                        val merged = foldWhitespace(it.thinking + delta)
                        it.copy(thinking = if (merged.length > THINKING_LIMIT) merged.takeLast(THINKING_LIMIT) else merged)
                    }
                },
            )
        }
    }

    override fun onEngineToolBatchStarted(toolIds: List<String>) {
        // 一个批次 = 一张新的工具分组卡。批次边界由引擎给，界面不猜。
        val groupId = nextId("g")
        currentGroupId = groupId
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = groupId,
                    kind = ChatKind.TOOL_GROUP,
                    groupLabel = "",
                    tools = emptyList(),
                ),
            )
        }
    }

    override fun onEngineToolUse(id: String, name: String, input: JSONObject?) {
        // 工作流类工具（任务清单/计划模式）不产生工具卡，只更新进度与步骤，
        // 与原版一致：它们的效果体现在任务面板和计划窗口上。
        if (ToolText.isWorkflowTool(name)) {
            _state.update { it.copy(workingStatus = "正在执行 $name…") }
            appendProcessStep("调用工具：$name")
            return
        }

        val groupId = currentGroupId ?: nextId("g").also { currentGroupId = it }
        val summary = ToolText.summary(name, input) ?: ""
        val command = input?.optString("command", "") ?: ""
        val (added, deleted) = ToolText.delta(name, input)
        val activity = ToolActivity(
            id = id,
            toolName = name,
            displayName = ToolText.userFacingName(name),
            summary = summary,
            additions = added,
            deletions = deleted,
            kind = kindOf(name),
        )
        liveTools[id] = LiveTool(groupId, name, command)

        _state.update { s ->
            val text = if (s.transcript.any { it.id == groupId }) s.transcript
            else s.transcript + ChatItem(id = groupId, kind = ChatKind.TOOL_GROUP)
            s.copy(
                transcript = text.map { item ->
                    if (item.id != groupId) item
                    else {
                        val tools = item.tools + activity
                        item.copy(tools = tools, groupLabel = groupLabel(tools))
                    }
                },
                workingStatus = "正在执行 $name…",
            )
        }
        appendProcessStep("调用工具：$name")
    }

    override fun onEngineToolProgress(id: String, chunk: String, stderr: Boolean, elapsedMs: Long) {
        if (liveTools[id] == null) return
        val buffer = progressBuffers.getOrPut(id) { StringBuilder() }
        if (stderr) buffer.append("[stderr]\n")
        buffer.append(chunk)
        // 单条工具的实时输出上限，超出丢头部（原版 liveOutput 是 40000）。
        if (buffer.length > LIVE_OUTPUT_LIMIT) buffer.delete(0, buffer.length - LIVE_OUTPUT_LIMIT)

        val now = System.currentTimeMillis()
        val last = progressLastFlush[id] ?: 0L
        if (now - last < PROGRESS_FLUSH_MS) return
        progressLastFlush[id] = now
        val text = buffer.toString()
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map {
                            if (it.id == id) it.copy(output = text, elapsedMs = maxOf(it.elapsedMs, elapsedMs)) else it
                        },
                    )
                },
            )
        }
    }

    override fun onEngineToolResult(
        id: String,
        name: String,
        isError: Boolean,
        exitCode: Int,
        content: String,
        diff: String,
        addedLines: Int,
        deletedLines: Int,
        command: String,
    ) {
        val live = liveTools.remove(id)
        progressBuffers.remove(id)
        progressLastFlush.remove(id)

        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { tool ->
                            if (tool.id != id) tool
                            else tool.copy(
                                completed = true,
                                failed = isError,
                                exitCode = exitCode,
                                output = content,
                                elapsedMs = maxOf(tool.elapsedMs, 0L),
                                additions = if (addedLines > 0) addedLines else tool.additions,
                                deletions = if (deletedLines > 0) deletedLines else tool.deletions,
                                // 包管理器命令失败时默认展开：报错信息通常很长，
                                // 折叠着用户只能看到"退出码 1"，等于没说。
                                expanded = tool.expanded ||
                                    (isError && ToolText.isPackageManagerTool(command)),
                            )
                        },
                    )
                },
                workingStatus = "正在继续处理…",
            )
        }
        appendProcessStep("工具完成：$name")

        // 编辑类工具可能改动工作区，顺手刷新变更面板。
        if (live != null && kindOf(name) == ToolKind.EDIT) refreshDiff()
    }

    override fun onEngineToolBatchCompleted(toolIds: List<String>) {
        if (toolIds.isEmpty()) return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else {
                        val touched = item.tools.any { it.id in toolIds }
                        if (!touched) item
                        else item.copy(
                            groupCompleted = true,
                            // 批结束时仍没拿到结果的成员，原版会标记成失败并补一句话。
                            // 保留这个行为：静默留一个"永远转圈"的卡更难排查。
                            tools = item.tools.map { tool ->
                                if (tool.id in toolIds && !tool.completed) {
                                    tool.copy(
                                        completed = true,
                                        failed = true,
                                        output = tool.output.ifEmpty { "工具在返回结果前结束。" },
                                    )
                                } else tool
                            },
                        )
                    }
                },
            )
        }
        currentGroupId = null
    }

    override fun onEnginePermissionRequest(requestId: String, tool: String, summary: String, highRisk: Boolean) {
        pendingPermissionId = requestId
        _state.update {
            it.copy(
                permissionRequest = PermissionRequest(
                    id = requestId,
                    tool = tool,
                    subtitle = subtitleFor(tool),
                    detail = summary,
                    riskLevel = if (highRisk) RiskLevel.HIGH else RiskLevel.NORMAL,
                ),
            )
        }
    }

    override fun onEngineQuestionRequest(requestId: String, questionsJson: String) {
        val questions = runCatching { JSONArray(questionsJson) }.getOrDefault(JSONArray())
        if (questions.length() == 0) {
            // 空提问没有可问的内容，直接回执让引擎继续，别把用户晾在一个空窗口前。
            engine.respondQuestion(requestId, JSONObject())
            return
        }
        pendingQuestions = PendingQuestions(requestId = requestId, questions = questions)
        showQuestionStep()
    }

    /**
     * 展示提问流程的第 [step] 步。
     *
     * 引擎的 `AskUserQuestion` 有两种形态：单问题（直接提交）与多问题（分步，
     * 每步一个窗口，按钮是「下一步」，最后一步才是「提交」）。
     * 答案按**问题原文**为键累积（与引擎的约定一致）。
     */
    private fun showQuestionStep() {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        if (question == null) {
            // 该步不是合法对象：跳过它而不是整个流程失败。
            advanceQuestion(JSONObject())
            return
        }
        val options = question.optJSONArray("options")
        val choices = buildList {
            if (options != null) {
                for (i in 0 until options.length()) {
                    val option = options.optJSONObject(i) ?: continue
                    add(ChoiceOption(label = option.optString("label", ""), detail = option.optString("description", "")))
                }
            }
        }
        val isLast = flow.index >= flow.questions.length() - 1
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = question.optString("header", "问题"),
                    intent = ChoiceIntent.QUESTION,
                    prompt = question.optString("question", "IQ 应该怎么做？"),
                    options = choices,
                    allowFreeForm = true,
                    freeFormHint = "其他回答…",
                    multiSelect = question.optBoolean("multiSelect", false),
                    // 多问题时分步：中间步骤是「下一步」，最后一步才是「提交」。
                    submitLabel = if (isLast) "提交" else "下一步",
                ),
            )
        }
    }

    /** 记录这一步的答案并前进；已是最后一步则回执引擎。 */
    private fun advanceQuestion(answer: Any?) {
        val flow = pendingQuestions ?: return
        val question = flow.questions.optJSONObject(flow.index)
        val key = question?.optString("question", "") ?: ""
        if (key.isNotEmpty() && answer != null) {
            runCatching { flow.answers.put(key, answer) }
        }
        val next = flow.index + 1
        if (next >= flow.questions.length()) {
            pendingQuestions = null
            _state.update { it.copy(choicePicker = null) }
            engine.respondQuestion(flow.requestId, flow.answers)
            return
        }
        flow.index = next
        showQuestionStep()
    }

    /** 取消整个提问流程：回执空答案，引擎会按「未选择」继续。 */
    private fun cancelQuestions() {
        val flow = pendingQuestions ?: return
        pendingQuestions = null
        _state.update { it.copy(choicePicker = null) }
        engine.respondQuestion(flow.requestId, JSONObject())
        appendInfo("已跳过提问", "IQ 的提问被跳过，它会按「未选择」继续。")
    }

    override fun onEnginePlanApprovalRequest(request: EnginePlanApproval) {
        _state.update { it.copy(planApproval = request.toUiPlanApproval()) }
    }

    override fun onEngineTasksChanged(tasks: List<AgentTask>) {
        _state.update { it.copy(tasks = tasks) }
    }

    override fun onEngineProjectDirectoryChanged(directory: String) {
        _state.update {
            it.copy(
                projectPath = directory,
                projectName = directory.trimEnd('/').substringAfterLast('/').ifEmpty { directory },
                filePath = directory,
            )
        }
    }

    override fun onEngineQueuedPromptApplied() {
        // 引擎已经把队首预输入并入了对话。界面这边做的是：
        // 出队一条（FIFO），并把还在流的上一段回复收尾。
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            val rest = s.pendingInputs.drop(1)
            s.copy(
                pendingInputs = rest,
                workingStatus = "预输入已加载，正在继续…",
                message = if (rest.isEmpty()) "预输入已加载，正在继续" else "预输入已加载，还剩 ${rest.size} 条",
            )
        }
    }

    override fun onEngineResponseRetry() {
        // 重试会重发整段回复：把上一次失败尝试留下的半截气泡丢掉，
        // 否则界面上会出现"半句话 + 完整回复"。
        discardStreamingAssistant()
        _state.update { it.copy(workingStatus = "连接中断，正在重试模型请求…", composerBusy = true) }
    }

    override fun onEngineInterruptedBySteering() {
        discardStreamingAssistant()
        _state.update {
            it.copy(
                workingStatus = "旧响应已打断，正在按最新纠正重新规划…",
                composerBusy = true,
            )
        }
    }

    override fun onEngineUsage(inputTokens: Long, outputTokens: Long) {
        val used = maxOf(0L, inputTokens) + maxOf(0L, outputTokens)
        _state.update { s ->
            // 有效窗口下限 16000：配置里若是 0/异常值，也不能让上下文条除出 Inf。
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            val shown = if (used > 0) used.toInt() else engine.estimateContextTokens()
            s.copy(contextTokens = shown, contextWindow = window)
        }
    }

    override fun onEngineStatus(status: String) {
        val text = ToolText.displayStatus(status, "thinking", true)
        _state.update { it.copy(workingStatus = text) }
        appendProcessStep(text)
    }

    override fun onEngineTurnComplete(stopReason: String) {
        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                composerBusy = false,
                workingStatus = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
                contextTokens = engine.estimateContextTokens().takeIf { it > 0 } ?: s.contextTokens,
                sessions = s.sessions.map { session ->
                    if (session.id == s.activeSessionId) session.copy(busy = false, updatedAtLabel = "刚刚") else session
                },
            )
        }
        // 第一轮结束后会话文件才真正落盘（引擎在 sendPrompt 里创建 SessionStore），
        // 所以这里必须重读列表，否则新会话在侧栏里不存在。
        syncActiveSessionFromDisk()
    }

    /**
     * 回合结束后把当前会话对齐到磁盘状态。
     *
     * 做两件事：
     * 1. 重读会话列表 —— 让刚创建的会话出现、让标题/消息数/时间刷新；
     * 2. 如果当前还没有 activeSessionId（刚 /new 过），把引擎刚写出的文件认领为当前会话。
     */
    private fun syncActiveSessionFromDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            val sessionFile = engine.sessionFile()
            _state.update { s ->
                val activeId = when {
                    s.activeSessionId.isNotEmpty() -> s.activeSessionId
                    sessionFile != null -> sessionFile.absolutePath
                    else -> ""
                }
                s.copy(sessions = sessions, activeSessionId = activeId)
            }
        }
    }

    override fun onEngineError(message: String, error: Throwable?) {
        finishTurnWithError(ToolText.friendlyError(message, error))
    }

    // ---------- 引擎事件用到的内部工具 ----------

    /**
     * 一轮以错误收尾：收尾气泡、落一条错误卡、把忙碌态清干净。
     *
     * ## 为什么开头要校验会话归属
     *
     * 报错可能是**迟到**的：用户在等待回复时切到了别的会话（或点了新会话），
     * 这一轮随即被 `engine.cancel()` 打断，而错误回调仍可能在之后到达。
     * `ZhiEngineController` 的代际校验能挡掉一部分，但 `startTurn` 这条路径是
     * 同步调用的、不经过代际门，而且 `_state` 此刻已经是**新会话**的 ——
     * 于是错误卡会落到新会话里，看起来像"串台"。
     *
     * 所以这里再按会话 id 兜一道：本轮所属的会话已经不是当前会话时，直接丢弃，
     * 不去污染新会话。（用户的预期是"错误应当出现在出问题的那个会话里"，
     * 但它已经不在前台、内存里也没有它的 transcript，落不进去；
     * 丢掉比串台正确 —— 那个会话下次打开时会从自己的文件重建。）
     */
    private fun finishTurnWithError(text: String) {
        val owner = turnSessionId
        turnSessionId = null
        if (owner != null && owner != _state.value.activeSessionId) return

        finalizeStreaming(keepIfEmpty = false)
        _state.update { s ->
            s.copy(
                transcript = s.transcript + ChatItem(
                    id = nextId("e"),
                    kind = ChatKind.ERROR,
                    title = "错误",
                    body = text,
                ),
                composerBusy = false,
                workingStatus = null,
                permissionRequest = null,
                busySessionIds = s.busySessionIds - s.activeSessionId,
            )
        }
        pendingPermissionId = null
    }

    /** 结束流式状态。[keepIfEmpty] 为 false 时，空白气泡会被丢弃（原版 discardEmptyStreamingMessage）。 */
    private fun finalizeStreaming(keepIfEmpty: Boolean) {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s ->
            s.copy(
                transcript = s.transcript.mapNotNull { item ->
                    if (item.id != id) item
                    else if (!keepIfEmpty && item.body.isBlank() && item.thinking.isBlank()) null
                    else item.copy(streaming = false)
                },
            )
        }
    }

    /** 整条丢弃当前流式气泡（重试 / 被纠偏打断时用）。 */
    private fun discardStreamingAssistant() {
        val id = streamingAssistantId ?: return
        streamingAssistantId = null
        _state.update { s -> s.copy(transcript = s.transcript.filterNot { it.id == id }) }
    }

    /** 给当前助手气泡记一条过程步骤（最多 12 条，相邻重复去重，与原版一致）。 */
    private fun appendProcessStep(step: String) {
        val id = streamingAssistantId ?: return
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id) item
                    else {
                        val merged = if (item.processSteps.lastOrNull() == step) item.processSteps
                        else (item.processSteps + step).takeLast(PROCESS_STEP_LIMIT)
                        item.copy(processSteps = merged)
                    }
                },
            )
        }
    }

    /** 思考文本的空白折叠：连续空白合成一个空格（原版 appendCollapsedWhitespace）。 */
    private fun foldWhitespace(text: String): String {
        val out = StringBuilder(text.length)
        var lastSpace = false
        for (ch in text) {
            val isSpace = ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r'
            if (isSpace) {
                if (!lastSpace) out.append(' ')
                lastSpace = true
            } else {
                out.append(ch)
                lastSpace = false
            }
        }
        return out.toString()
    }


    private fun queuedStatus(count: Int): String =
        if (count <= 1) "已预输入，等待当前回复完成…" else "已预输入 $count 条，等待当前回复完成…"

    /**
     * 回执一次权限请求。
     *
     * 回执对象是**引擎**而不是界面上的临时对象：引擎的工具线程正阻塞在
     * `PermissionGate.require()` 的闩锁上，只有真正 respond 过去它才会继续。
     * 若一直不回执，引擎会等满 30 分钟。
     */
    fun resolvePermission(allow: Boolean, alwaysAllow: Boolean = false) {
        val requestId = pendingPermissionId
        pendingPermissionId = null
        engine.respondPermission(requestId ?: "", allow)
        _state.update {
            it.copy(
                permissionRequest = null,
                permissionMode = if (alwaysAllow) PermissionMode.ACCEPT_EDITS else it.permissionMode,
                message = if (alwaysAllow) {
                    if (allow) "已允许本次调用，并切换为自动编辑" else "已拒绝本次调用，并切换为自动编辑"
                } else {
                    if (allow) "已允许本次调用" else "已拒绝本次调用"
                },
            )
        }
        // 切到自动编辑后要立刻下发，否则下一条工具调用还会弹窗。
        if (alwaysAllow) runCatching { engine.configure(engineOverrides()) }
    }

    private fun kindOf(toolName: String): ToolKind = when (toolName) {
        "Grep", "Glob", "Search" -> ToolKind.SEARCH
        "Read", "ReadMany", "LS", "List", "Tree", "Stat" -> ToolKind.READ
        "Edit", "MultiEdit", "Write", "Move", "Delete", "Mkdir", "Copy" -> ToolKind.EDIT
        "Bash", "Root", "BashTool" -> ToolKind.COMMAND
        else -> ToolKind.OTHER
    }

    private fun groupLabel(tools: List<ToolActivity>): String {
        val parts = mutableListOf<String>()
        val searches = tools.count { it.kind == ToolKind.SEARCH }
        val reads = tools.count { it.kind == ToolKind.READ }
        val edits = tools.count { it.kind == ToolKind.EDIT }
        val commands = tools.count { it.kind == ToolKind.COMMAND }
        if (searches > 0) parts += "搜索 $searches 个模式"
        if (reads > 0) parts += "读取 $reads 个文件"
        if (edits > 0) parts += "修改 $edits 处代码"
        if (commands > 0) parts += "执行 $commands 条命令"
        if (parts.isEmpty()) parts += "调用 ${tools.size} 个工具"
        return parts.joinToString("、")
    }

    private fun subtitleFor(tool: String): String = when (tool) {
        "Root" -> "高风险：蜘蛛请求以 Android uid 0 执行系统命令"
        "Bash" -> "蜘蛛请求在本地 Termux 环境中执行以下命令"
        "AndroidIntent" -> "蜘蛛请求通过 Android 应用进程打开手机 App、网页或系统页面"
        "Write", "Edit", "MultiEdit" -> "蜘蛛请求修改项目中的文件"
        "Delete" -> "蜘蛛请求删除项目中的文件或目录"
        else -> "蜘蛛请求调用工具 $tool"
    }

    // ---------- 斜杠命令 ----------

    /**
     * 斜杠命令路由。
     *
     * ## 两条原则
     *
     * 1. **能接真功能的就接真功能**，不要保留"看起来执行了"的假回执。
     * 2. **没实现的就直说没实现**。原来兜底分支会打印"已执行 `xxx`（Mock）"，
     *    这是最糟的一种反馈：用户以为命令生效了，于是去做下一件事。
     *    现在的兜底是明确列出"这条还没移植"以及可用的替代指令。
     */
    private fun runSlashCommand(raw: String) {
        val command = raw.substringBefore(' ').lowercase(Locale.US)
        val arg = raw.substringAfter(' ', "").trim()
        when (command) {
            "/clear", "/new" -> newSession()
            "/terminal" -> selectTab(WorkspaceTab.TERMINAL)
            "/changes", "/diff" -> selectTab(WorkspaceTab.CHANGES)
            "/files" -> selectTab(WorkspaceTab.FILES)
            "/plan" -> enterPlanMode(arg)
            "/permissions" -> showPermissionPicker()
            "/effort" -> showEffortPicker()
            "/model" -> showModelPicker()
            "/cancel" -> stop()
            "/config" -> openSettings()
            "/mcp" -> openMcpConfig()
            "/copy" -> copyLatestAssistantReply()
            "/compact" -> compactContext(arg)

            // 环境类：都落到真实的 Termux 环境弹窗上（含安装/修复入口），
            // 不再是把命令当普通消息发出去换一句假回复。
            "/doctor", "/runtime" -> openEnvironment()
            "/repair" -> {
                openEnvironment()
                repairRuntime()
            }

            "/tasks" -> openTaskList()
            "/skills" -> openSkills()
            "/agents" -> openRoleCards()
            "/memory" -> openMemory()
            "/init" -> runInitPrompt()

            // 沙箱是**跨进程**的：引擎在 :iqsandbox，界面必须交给系统启动。
            // 以前这条命令没有路由，会掉进下面的"指令尚未移植"兜底卡片。
            "/sandbox" -> openSandbox()

            "/web" -> handleWebSlash(arg)

            "/context" -> {
                if (arg.isEmpty()) {
                    showContextInfo()
                } else {
                    val tokens = parseTokenCount(arg)
                    if (tokens == null) {
                        _state.update { it.copy(message = "请输入 128k、200k、1m、1.5m 这类格式") }
                    } else {
                        _state.update { it.copy(contextWindow = tokens, message = "上下文窗口已设置为 ${formatTokens(tokens)}") }
                    }
                }
            }

            "/resume" -> {
                openSidebar()
                _state.update { it.copy(message = "在侧栏选择要恢复的会话") }
            }

            "/usage", "/stats", "/status" -> showContextInfo()
            "/help" -> appendInfo(
                "全部指令",
                SLASH_COMMANDS.joinToString("\n") { "` ${it.name} ` — ${it.hint}" },
            )

            else -> appendInfo(
                "指令尚未移植",
                buildString {
                    append("`").append(command).append("` 在当前 Compose 版本里还没有对应实现，")
                    append("本机也没有执行任何操作。\n\n")
                    append("可用的相关指令：\n")
                    append("` /doctor ` `/runtime` `/repair` — 内置 Termux 环境\n")
                    append("` /compact ` — 真正调用模型压缩上下文\n")
                    append("` /context ` — 查看或设置上下文窗口\n")
                    append("` /copy ` — 复制最近一条回复\n")
                    append("` /tasks ` — 查看 Agent 任务列表\n")
                    append("` /resume ` — 从侧栏恢复历史会话\n")
                    append("` /mcp ` — MCP 服务器配置\n")
                    append("` /model ` — 切换模型")
                },
            )
        }
    }

    /**
     * 重新估算上下文用量。
     *
     * 压缩之后必须重算：`contextTokens` 平时是**累加**出来的（每轮加上 API 上报的用量），
     * 压缩会真的丢掉一部分历史，累加值只会越走越大，不重算就会显示一个虚高的占用。
     */
    private fun syncContextUsage() {
        _state.update { s ->
            val window = maxOf(16_000, engine.contextWindowTokens().takeIf { it > 0 } ?: s.contextWindow)
            s.copy(contextTokens = engine.estimateContextTokens(), contextWindow = window)
        }
    }

    /**
     * `/context 1m` 这类参数。
     *
     * 返回 null 表示格式不合法（而不是抛异常）——调用方要的是"给一句提示"，
     * 不是错误处理。边界与原版一致：16k–2m，超出范围会让引擎按无效窗口处理。
     */
    private fun parseTokenCount(raw: String): Int? {
        var s = raw.trim().lowercase(Locale.US).replace(",", "").replace("_", "")
        if (s.isEmpty()) return null
        var multiplier = 1.0
        when {
            s.endsWith("k") -> { multiplier = 1_000.0; s = s.dropLast(1) }
            s.endsWith("m") -> { multiplier = 1_000_000.0; s = s.dropLast(1) }
        }
        val value = s.toDoubleOrNull()?.times(multiplier) ?: return null
        if (!value.isFinite() || value < 16_000.0 || value > 2_000_000.0) return null
        return value.toInt()
    }

    /**
     * 「重试上一问」：把最近一条用户消息重新发一次。
     *
     * 注意重发**不会**撤回上一轮的结果，所以它会作为新一轮追加在对话末尾
     * （与原版行为一致：原版也是重发，而不是回滚历史）。
     */
    private fun retryLastUserPrompt() {
        val lastUser = _state.value.transcript.lastOrNull {
            it.kind == ChatKind.USER && it.body.isNotBlank()
        }
        if (lastUser == null) {
            _state.update { it.copy(message = "没有可重试的问题") }
            return
        }
        _state.update { it.copy(composerText = lastUser.body) }
        send()
    }

    /**
     * `/web`：联网搜索。
     *
     * 四种形态（与原版 `handleWebSlash` 一致）：
     * - 无参数 → 打开联网设置
     * - `on` / `off` → 开关，**真的写进设置并重配引擎**（否则下一轮请求还按旧值走）
     * - `fetch <url>` → 直接读网页
     * - 其它 → 当作搜索词直接搜
     *
     * 后两种走 [ZhiEngineController.manualWebSearch]，**不经过模型**：
     * 用户输入后立刻拿到结果，省掉一次模型往返与相应的上下文消耗。
     */
    private fun handleWebSlash(arg: String) {
        when {
            arg.isEmpty() -> {
                openSettings()
                _state.update { it.copy(settingsDraft = it.settingsDraft?.copy(category = SettingsCategory.NETWORK)) }
            }
            arg.equals("on", ignoreCase = true) || arg == "开启" -> setWebSearch(true)
            arg.equals("off", ignoreCase = true) || arg == "关闭" -> setWebSearch(false)
            arg.startsWith("fetch ", ignoreCase = true) -> {
                val url = arg.substring(6).trim()
                if (url.isEmpty()) appendInfo("网页读取", "用法：`/web fetch https://example.com`")
                else runWebAction("网页读取", url) { engine.manualWebFetch(url) }
            }
            else -> runWebAction("联网搜索", arg) { engine.manualWebSearch(arg) }
        }
    }

    private fun setWebSearch(enabled: Boolean) {
        _state.update { s ->
            s.copy(
                settings = s.settings.copy(webSearchEnabled = enabled),
                settingsDraft = s.settingsDraft?.copy(webSearchEnabled = enabled),
                message = if (enabled) "联网搜索已开启" else "联网搜索已关闭",
            )
        }
        // 立刻下发：这一步容易漏，漏了就会"开关看起来变了，但下一轮请求还用旧值"。
        runCatching { engine.configure(engineOverrides()) }
    }

    /**
     * 执行一次手动联网动作，并把文本结果落成一张助手卡片。
     *
     * 先放一张"进行中"的卡片：网络请求可能要几秒，没有反馈用户会以为没反应。
     * 结果出来后**原地替换**那张卡片，而不是追加一条——否则对话流里会留下
     * 一个永远停在"正在搜索…"的死卡片。
     */
    private fun runWebAction(title: String, subject: String, action: suspend () -> Result<String>) {
        val cardId = nextId("web")
        _state.update {
            it.copy(
                transcript = it.transcript + ChatItem(
                    id = cardId,
                    kind = ChatKind.ASSISTANT,
                    title = title,
                    body = "正在处理：`$subject` …",
                ),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            _state.update { s ->
                val body = result.getOrElse { error -> (error.message ?: "未知原因") }
                s.copy(
                    transcript = s.transcript.map { item ->
                        if (item.id != cardId) item else item.copy(body = body, streaming = false)
                    },
                )
            }
        }
    }

    /** `/context`（无参数）与 `/usage`、`/stats`、`/status` 共用。 */    private fun showContextInfo() {
        val s = _state.value
        val measured = engine.hasMeasuredContextUsage()
        appendInfo(
            "会话状态",
            buildString {
                append("模型：").append(s.modelLabel).append('\n')
                append("API 配置：").append(s.profileName)
                append(if (s.apiKeyConfigured) "（已配置密钥）" else "（未配置密钥）").append('\n')
                append("项目：").append(s.projectPath).append('\n')
                append("上下文：").append(formatTokens(s.contextTokens))
                append(" / ").append(formatTokens(s.contextWindow))
                append(if (measured) "（API 实测用量）" else "（本地估算，尚未产生 API 用量）").append('\n')
                append("权限：").append(s.permissionMode.label).append('\n')
                append("推理强度：").append(s.effort.label).append('\n')
                append("运行环境：").append(if (s.runtimeReady) "已就绪" else "未初始化")
            },
        )
    }

    /**
     * `/compact`：真正调用模型做语义压缩。
     *
     * 引擎侧是同步阻塞的（会走网络），所以放 IO 线程；期间给一条进行中提示，
     * 因为大上下文压缩可能要几十秒，没有反馈用户会以为卡死了。
     *
     * ⚠️ 引擎**不保证**真的调用了模型：`compactContextSemantic` 在算不出可压缩区间时
     * 会直接返回 [ENGINE_NOTHING_TO_COMPACT] 那句话（见其 `cut <= 0` 分支），
     * 引擎自己的自动压缩也用同一个前缀判断。所以这里必须分流——
     * 不加区分地宣称"已用模型生成摘要"就是在编造一次并不存在的模型调用。
     */
    private fun compactContext(instructions: String) {
        if (engine.isBusy()) {
            appendInfo("上下文压缩", "任务正在运行，完成后再压缩上下文。")
            return
        }
        // 用中性的进行时措辞：此时还不知道引擎会不会真的调模型。
        appendInfo("上下文压缩", "正在整理上下文…")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.compactContext(instructions) }
            result.fold(
                onSuccess = { summary ->
                    syncContextUsage()
                    val usage = "现在上下文：${formatTokens(_state.value.contextTokens)} / ${formatTokens(_state.value.contextWindow)}"
                    if (summary.startsWith(ENGINE_NOTHING_TO_COMPACT)) {
                        appendInfo(
                            "无需压缩",
                            "引擎判断当前没有可压缩的历史，因此**没有**调用模型。\n$usage",
                        )
                        return@fold
                    }
                    appendInfo(
                        "上下文压缩完成",
                        buildString {
                            append("已用模型生成语义摘要，后续请求改用摘要后的上下文。\n")
                            append(usage).append('\n')
                            if (summary.isNotBlank()) {
                                append("\n摘要要点：\n").append(summary.take(COMPACT_SUMMARY_LIMIT))
                                if (summary.length > COMPACT_SUMMARY_LIMIT) append("…")
                            }
                        },
                    )
                },
                onFailure = { error -> appendInfo("上下文压缩失败", error.message ?: "未知原因") },
            )
        }
    }

    /**
     * `/plan` 进入计划模式。
     *
     * 参考原版流程：先弹「计划目标」窗口问清楚要做哪件事，再产出计划。
     * 直接把计划推给用户（旧行为）会跳过"目标澄清"这一步。
     */
    private fun enterPlanMode(arg: String) {
        if (arg == "off" || arg == "exit") {
            runCatching { engine.cancelPlanModeFromUi("") }
            _state.update { it.copy(planApproval = null, message = "已退出计划模式") }
            return
        }
        if (engine.isBusy()) {
            _state.update { it.copy(message = "当前回合还在进行，等它结束后再进入计划模式") }
            return
        }
        val result = runCatching { engine.enterPlanModeFromUi() }
        val entered = result.getOrNull()?.isError == false
        if (!entered) {
            // 计划模式进不去时必须说出来。默默什么都不做的话，用户会以为
            // 自己已经在只读模式里了 —— 而实际上下一条消息会照常执行。
            finishTurnWithError("进入计划模式失败：" + (result.exceptionOrNull()?.message ?: "引擎拒绝了该请求"))
            return
        }
        _state.update { it.copy(permissionMode = PermissionMode.PLAN, message = "已进入计划模式（只读）") }
        // 带了目标就当成一条普通提示词发出去：计划模式下引擎只会产出计划，不会执行。
        // 「问目标」这一步由引擎自己用「选择」窗口发起（等提问门控接线后生效）。
        if (arg.isNotEmpty()) {
            _state.update { it.copy(composerText = arg) }
            send()
        }
    }

    /**
     * 批准 / 退回计划。
     *
     * 回执必须发给引擎：`onPlanApprovalRequest` 对应的工具线程正阻塞在审批闩锁上，
     * 只有 `respondPlanApproval` 才会让它继续。原版这个闩锁**故意没有超时**——
     * 无人值守的请求永远不能变成"批准执行"。
     */
    fun resolvePlan(approved: Boolean) {
        val plan = _state.value.planApproval ?: return
        engine.respondPlanApproval(
            plan.id,
            if (approved) PlanApprovalGate.Decision.APPROVE else PlanApprovalGate.Decision.KEEP_PLANNING,
            "",
        )
        appendInfo(
            if (approved) "计划已批准" else "计划已退回",
            if (approved) "开始执行：${plan.title}" else "等待修改后的计划。",
        )
        _state.update { it.copy(planApproval = null) }
    }


    /** 「继续规划」并带上用户填写的反馈。 */
    fun resolvePlanWithFeedback(feedback: String) {
        val plan = _state.value.planApproval ?: return
        val trimmed = feedback.trim()
        // 空反馈等价于"退回"：把 KEEP_PLANNING 与空 feedback 一起发过去，
        // 引擎会重新进入规划并等待下一版计划。
        engine.respondPlanApproval(plan.id, PlanApprovalGate.Decision.KEEP_PLANNING, trimmed)
        appendInfo(
            if (trimmed.isEmpty()) "计划已退回" else "已提交反馈",
            if (trimmed.isEmpty()) "等待修改后的计划。" else trimmed,
        )
        _state.update { it.copy(planApproval = null) }
    }

    // ---------- API 配置 ----------

    /**
     * 打开 API 配置（列表页）。
     *
     * 顺带刷新 [WorkspaceUiState.profileName] / [WorkspaceUiState.modelLabel]：
     * 这两个字段原来写死成演示值，现在它们是**真实生效的配置摘要**，
     * 否则顶栏会显示一个跟实际请求无关的模型名。
     */
    fun openApiConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(apiConfig = state, settingsDraft = null)
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun closeApiConfig() = _state.update { it.copy(apiConfig = null) }

    /** 新增：给一张空表单。 */
    fun newApiProfile() = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = ApiProfileDraft.blank()))
    }

    /** 编辑：**故意不回填密钥**（见 ApiConfigStore 的说明）。 */
    fun editApiProfile(profile: ApiProfile) = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = ApiProfileDraft.from(profile)))
    }

    fun updateApiProfileDraft(transform: (ApiProfileDraft) -> ApiProfileDraft) = _state.update {
        val config = it.apiConfig ?: return@update it
        val form = config.form ?: return@update it
        it.copy(apiConfig = config.copy(form = transform(form)))
    }

    fun cancelApiProfileForm() = _state.update {
        it.copy(apiConfig = it.apiConfig?.copy(form = null))
    }

    /**
     * 保存一条 API 配置。
     *
     * 密钥写入 AndroidKeyStore 加密存储；编辑已有记录时若密钥留空，
     * 传 `replaceKey=false` 沿用原密钥，而不是把它清掉。
     */
    fun saveApiProfile() {
        val form = _state.value.apiConfig?.form ?: return
        if (!form.saveable) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.save(getApplication(), form)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _state.update { it.copy(message = "保存失败：" + (error?.message ?: "未知原因")) }
                return@launch
            }
            val state = ApiConfigStore.read(getApplication())
            _state.update { it.copy(apiConfig = state, message = "API 配置已保存") }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun selectApiProfile(profileId: String) {
        // 换配置就用那份配置自己的模型，别把上一条配置的会话级覆盖带过去。
        modelOverride = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.select(getApplication(), profileId)
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(
                    apiConfig = state,
                    message = if (result.isSuccess) "已切换 API 配置" else "切换失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    fun deleteApiProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApiConfigStore.delete(getApplication(), profileId)
            val state = ApiConfigStore.read(getApplication())
            _state.update {
                it.copy(
                    apiConfig = state,
                    message = if (result.isSuccess) "配置已删除" else "无法删除：${result.exceptionOrNull()?.message ?: "未知原因"}",
                )
            }
            syncActiveProfile()
            syncRoleCardFromStore()
        }
    }

    // ---------- MCP 配置 ----------

    /**
     * 打开 MCP 服务器配置（列表页）。
     *
     * 与 API 配置一样共用"列表页 / 表单页"两态，`form` 非空即表单。
     * 这里的读写是**同步**的：`McpConfigStore` 只读一个本地小 JSON，
     * 没有网络也没有密钥解密，放 IO 线程反而让状态更新顺序更难推理。
     */
    fun openMcpConfig() {
        _state.update { it.copy(mcpConfig = McpStore.read(), settingsDraft = null) }
    }

    fun closeMcpConfig() = _state.update { it.copy(mcpConfig = null) }

    fun newMcpServer() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(form = McpServerDraft(originalName = null)))
    }

    /**
     * 编辑已有服务器：需要回读 `args` / `env` / `headers` 的原文，
     * 因为列表模型只带了参数个数（这些内容可能很大，不该进列表状态）。
     */
    fun editMcpServer(server: McpServer) {
        val raw = McpStore.rawOf(server.name)
        _state.update {
            val config = it.mcpConfig ?: return@update it
            val draft = if (raw != null) McpStore.draftOf(server, raw.first, raw.second, raw.third)
            else McpStore.draftOf(server, emptyList(), "", "")
            it.copy(mcpConfig = config.copy(form = draft))
        }
    }

    fun updateMcpDraft(transform: (McpServerDraft) -> McpServerDraft) = _state.update {
        val config = it.mcpConfig ?: return@update it
        val form = config.form ?: return@update it
        it.copy(mcpConfig = config.copy(form = transform(form)))
    }

    fun cancelMcpForm() = _state.update {
        it.copy(mcpConfig = it.mcpConfig?.copy(form = null))
    }

    fun saveMcpServer() {
        val form = _state.value.mcpConfig?.form ?: return
        if (!form.saveable) return
        val result = McpStore.save(form)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = if (result.isSuccess) "MCP 配置已保存"
                else "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /** 启用 / 停用：整行点击触发，等价于原版行尾的「启用/停用」按钮。 */
    fun toggleMcpServer(server: McpServer) {
        val result = McpStore.setEnabled(server.name, !server.enabled)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = when {
                    result.isFailure -> "保存失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
                    server.enabled -> "已停用：${server.name}"
                    else -> "已启用：${server.name}"
                },
            )
        }
    }

    fun deleteMcpServer(server: McpServer) {
        val result = McpStore.delete(server.name)
        _state.update {
            it.copy(
                mcpConfig = McpStore.read(),
                message = if (result.isSuccess) "已删除 MCP 服务器：${server.name}"
                else "删除失败：${result.exceptionOrNull()?.message ?: "未知原因"}",
            )
        }
    }

    /**
     * 把当前生效的 API 配置同步到界面摘要，并让引擎重新读取。
     *     * 必须让引擎重读：它内部持有配置副本，不重新下发的话
     * 用户"保存了密钥却仍然报 API key is not configured"。
     */
    private fun syncActiveProfile() {
        val active = ApiConfigStore.active(getApplication())
        _state.update {
            it.copy(
                profileName = active?.name ?: "未配置",
                modelLabel = modelOverride ?: active?.model ?: "未设置",
                apiKeyConfigured = active?.hasKey == true,
            )
        }
        runCatching { engine.configure(engineOverrides()) }
    }

    private fun appendInfo(title: String, body: String) {
        _state.update {
            it.copy(transcript = it.transcript + ChatItem(id = nextId("i"), kind = ChatKind.INFO, title = title, body = body))
        }
    }

    /**
     * 把 **Compose 树之外**发生的事件写进对话流。
     *
     * 目前唯一的来源是 `MainActivity.onResume`：`AndroidIntentBridge.resumePendingApkInstall`
     * 在用户从「安装未知应用」权限页返回后会继续 APK 安装，并可能返回错误
     * （例如 APK 已损坏、系统安装器不可用）。这类结果必须落进对话流，
     * 因为一次性的 Toast 很容易被漏掉，而失败恰恰是最不能漏的。
     */
    fun reportExternalEvent(title: String, body: String) = appendInfo(title, body)

    // ---------- 选择器 ----------

    fun showPermissionPicker() {
        val values = PermissionMode.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "权限模式",
                    intent = ChoiceIntent.PERMISSION_MODE,
                    options = values.map { mode ->
                        ChoiceOption(
                            label = mode.label,
                            detail = mode.detail,
                            checked = mode == it.permissionMode,
                        )
                    },
                )
            )
        }
    }

    fun showEffortPicker() {
        val values = EffortLevel.entries
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "推理强度",
                    intent = ChoiceIntent.EFFORT,
                    options = values.map { level -> ChoiceOption(level.label, checked = level == it.effort) },
                )
            )
        }
    }

    /**
     * 打开模型选择面板。
     *
     * 面板先立起来（显示"正在获取"），目录拉取在 IO 线程后台跑；回来后再填列表。
     * 原版就是这个顺序，否则网络慢时点按钮像没反应。
     *
     * 会话正在跑的时候换模型不会打断当前轮：模型写在配置记录里，下一轮 `configure`
     * 自然带上（原版的"下一完整轮生效"）。
     */
    fun showModelPicker() {
        _state.update { s ->
            val active = s.apiConfig?.let { config -> config.profiles.firstOrNull { it.id == config.activeId } }
            s.copy(
                modelPicker = ModelPickerState(
                    profileName = active?.name ?: s.profileName,
                    currentModel = active?.model?.takeIf { it.isNotBlank() } ?: s.modelLabel,
                ),
            )
        }
        fetchModelCatalog()
    }

    fun closeModelPicker() {
        modelCatalogJob?.cancel()
        modelCatalogJob = null
        _state.update { it.copy(modelPicker = null) }
    }

    fun setModelQuery(text: String) {
        _state.update { s -> s.copy(modelPicker = s.modelPicker?.copy(query = text)) }
    }

    private fun fetchModelCatalog() {
        modelCatalogJob?.cancel()
        modelCatalogJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ModelCatalogStore.fetch(getApplication()) }
            val fetched = result.getOrNull()
            _state.update { s ->
                // 面板可能已经被关掉了：这时不要把状态又塞回去。
                val picker = s.modelPicker ?: return@update s
                s.copy(
                    modelPicker = if (fetched == null) {
                        picker.copy(
                            loading = false,
                            status = ModelCatalogStore.friendlyError(result.exceptionOrNull()),
                            models = emptyList(),
                        )
                    } else {
                        picker.copy(
                            loading = false,
                            status = if (fetched.isEmpty()) "API 未返回可用模型，可手动输入"
                            else "已获取 ${fetched.size} 个模型",
                            models = fetched,
                        )
                    },
                )
            }
        }
    }

    /**
     * 应用选定的模型：写进当前配置记录并重跑 `configure`，让下一次请求就用上新模型。
     *
     * 这里**不**走 `engineOverrides().model`：模型属于配置记录的持久化内容，
     * 只做一次内存覆盖会让"底栏显示的模型"和"实际请求的模型"在下一次启动后分叉。
     */
    fun applySelectedModel(model: String) {
        val target = model.trim()
        if (target.isEmpty()) return
        closeModelPicker()
        modelOverride = target
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ApiConfigStore.setDefaultModel(getApplication(), target) }
            // 覆盖值先生效，所以即使写配置失败模型也已经切换了；把这一点如实说出来，
            // 而不是报一个"保存失败"让人以为模型没换。
            syncActiveProfile()
            syncRoleCardFromStore()
            _state.update {
                it.copy(
                    message = if (result.isSuccess) "模型：$target"
                    else "模型：$target（本次会话已生效，未能写入配置：${result.exceptionOrNull()?.message ?: "未知原因"}）",
                )
            }
        }
    }

    fun showMessageActions(item: ChatItem) {
        val options = when (item.kind) {
            ChatKind.USER -> listOf("复制", "再次发送", "编辑后发送")
            ChatKind.ASSISTANT, ChatKind.ERROR -> listOf("复制", "重试上一问")
            else -> listOf("复制", if (item.groupCompleted) "已全部完成" else "执行中")
        }
        // 与 showSessionActions 同理：动作在**选择器回调**里执行，那时拿到的只有选项文案，
        // 不记住来源就会"复制了别的消息"。
        pendingMessageAction = item
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "消息操作",
                    intent = ChoiceIntent.MESSAGE_ACTION,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    // ---------- 剪贴板 ----------

    /**
     * 复制一条消息的可见文本。
     *
     * 复制的是用户**看得到的内容**：助手消息取正文（没有正文时退回思考内容），
     * 工具组则是把各工具的标题拼起来——复制一个空串对用户毫无用处。
     */
    fun copyMessage(item: ChatItem) {
        val text = when (item.kind) {
            ChatKind.ASSISTANT, ChatKind.ERROR -> item.body.ifBlank { item.thinking }
            // 工具组没有正文，把各工具的展示名与摘要拼起来——复制空串对用户没用。
            ChatKind.TOOL_GROUP -> item.tools.joinToString("\n") { tool ->
                listOfNotNull(tool.displayName.takeIf { it.isNotBlank() }, tool.summary.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            }
            else -> item.body
        }
        copyText(text, "蜘蛛消息")
    }

    /** `/copy`：复制最近一条助手回复。 */
    fun copyLatestAssistantReply() {
        val latest = _state.value.transcript.lastOrNull {
            it.kind == ChatKind.ASSISTANT && it.body.isNotBlank()
        }
        if (latest == null) {
            _state.update { it.copy(message = "还没有可复制的回复") }
            return
        }
        copyText(latest.body, "蜘蛛回复")
    }

    private fun copyText(text: String, label: String) {
        val ok = Clipboard.copy(getApplication(), label, text)
        _state.update {
            it.copy(
                message = when {
                    !ok -> "复制失败：内容为空或剪贴板不可用"
                    // Android 13 起系统自己会弹「已复制」，应用再弹一次就是重复打扰。
                    Clipboard.systemShowsCopyToast() -> ""
                    else -> "已复制到剪贴板"
                },
            )
        }
    }

    fun showSessionActions(session: SessionSummary) {
        // 记住操作对象：动作是在**选择器回调**里执行的，那时只能拿到选项文案，
        // 拿不到是哪条会话 —— 不记住就会出现"删掉了当前会话"这种错删。
        pendingSessionAction = session
        val options = if (session.note.isEmpty()) listOf("编辑备注", "恢复会话", "删除会话")
        else listOf("编辑备注", "清除备注", "恢复会话", "删除会话")
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = session.title,
                    intent = ChoiceIntent.SESSION_ACTION,
                    options = options.map { ChoiceOption(it) },
                )
            )
        }
    }

    /** 打开备注编辑：没有选项，只靠自由输入提交。 */
    private fun editSessionNote(session: SessionSummary) {
        _state.update {
            it.copy(
                choicePicker = ChoicePickerState(
                    title = "会话备注",
                    intent = ChoiceIntent.SESSION_NOTE,
                    prompt = "给这条会话写一句备注，方便以后在侧栏里认出来。",
                    options = emptyList(),
                    allowFreeForm = true,
                    freeFormHint = if (session.note.isEmpty()) "例如：修好了相册崩溃" else session.note,
                ),
            )
        }
    }

    /** 写入备注到磁盘。空字符串表示清除。 */
    private fun saveSessionNote(session: SessionSummary, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(session.id)
            // titleOverride 传空串 = 不覆盖标题，只改备注。
            val ok = SessionReader.updateMetadata(file, note, "")
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update {
                it.copy(
                    sessions = sessions,
                    message = if (ok) {
                        if (note.isEmpty()) "已清除备注" else "备注已保存"
                    } else {
                        "备注保存失败"
                    },
                )
            }
        }
    }

    fun dismissChoicePicker() {
        // 提问流程的「取消」必须把空答案回执给引擎，否则引擎会一直等
        // （`QuestionGate.ask` 的闩锁要等 30 分钟才超时）。
        if (_state.value.choicePicker?.intent == ChoiceIntent.QUESTION) {
            cancelQuestions()
            return
        }
        _state.update { it.copy(choicePicker = null) }
    }

    /**
     * 提交选择器里选中的项。
     *
     * [indices] 是选中的下标列表：单选选择器只会有 0 或 1 个，
     * 多选的提问流程会有多个。统一用列表是为了让两条路径共用一套 UI 回调。
     */
    fun onSubmitSelection(indices: List<Int>) {
        val picker = _state.value.choicePicker ?: return
        // 提问流程走自己的分步状态机，答案要按问题原文累积。
        if (picker.intent == ChoiceIntent.QUESTION) {
            val labels = indices.mapNotNull { picker.options.getOrNull(it)?.label }
            val answer: Any? = when {
                labels.isEmpty() -> null
                picker.multiSelect -> JSONArray(labels)
                else -> labels.first()
            }
            advanceQuestion(answer)
            return
        }
        // 单选选择器语义不变：等价于点第一项。
        onChoiceSelected(indices.firstOrNull() ?: -1)
    }

    fun onChoiceSelected(index: Int) {
        val picker = _state.value.choicePicker ?: return
        val option = picker.options.getOrNull(index) ?: return
        when (picker.intent) {
            ChoiceIntent.PERMISSION_MODE -> {
                val mode = PermissionMode.entries.getOrNull(index)
                if (mode != null) _state.update {
                    it.copy(choicePicker = null, permissionMode = mode, message = "权限模式：${mode.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.EFFORT -> {
                val level = EffortLevel.entries.getOrNull(index)
                if (level != null) _state.update {
                    it.copy(choicePicker = null, effort = level, message = "推理强度：${level.label}")
                } else _state.update { it.copy(choicePicker = null) }
            }
            // MODEL 不再走选择器：模型面板要异步拉目录并写回配置记录，
            // 用 5 个固定选项的通用选择器表达不了，已换成 ui/dialogs/ModelPickerOverlay。
            ChoiceIntent.MESSAGE_ACTION -> {
                val target = pendingMessageAction
                pendingMessageAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到来源就不猜：宁可无反应，也不能复制到别的消息。
                    _state.update { it.copy(message = "找不到目标消息，操作已取消") }
                } else when (option.label) {
                    "复制" -> copyMessage(target)
                    "再次发送" -> {
                        // 复用用户消息的原文重发；工具组这类没有正文的目标不支持。
                        if (target.body.isBlank()) _state.update { it.copy(message = "这条消息没有可重发的内容") }
                        else {
                            _state.update { it.copy(composerText = target.body) }
                            send()
                        }
                    }
                    "编辑后发送" -> _state.update { it.copy(composerText = target.body, message = "已放回输入框，可编辑后发送") }
                    "重试上一问" -> retryLastUserPrompt()
                    // 这两种是状态展示项，点了不做任何事。
                    "已全部完成", "执行中" -> Unit
                    else -> _state.update { it.copy(message = "不支持的操作：${option.label}") }
                }
            }
            ChoiceIntent.SESSION_ACTION -> {
                val target = pendingSessionAction
                pendingSessionAction = null
                _state.update { it.copy(choicePicker = null) }
                if (target == null) {
                    // 拿不到操作对象就什么都不做。宁可无反应，也不能猜一条会话删掉。
                    _state.update { it.copy(message = "找不到目标会话，操作已取消") }
                } else when (option.label) {
                    "删除会话" -> deleteSession(target.id)
                    "恢复会话" -> openSession(target)
                    "编辑备注" -> editSessionNote(target)
                    "清除备注" -> saveSessionNote(target, "")
                    else -> _state.update { it.copy(message = "${option.label}（未接线）") }
                }
            }
            ChoiceIntent.SESSION_NOTE -> {
                // 这个分支正常不会走到（备注靠自由输入提交），留作兜底。
                _state.update { it.copy(choicePicker = null) }
            }
            ChoiceIntent.QUESTION -> {
                // 提问流程正常走 onSubmitSelection / dismissChoicePicker；
                // 单选回调真落到这里时按「跳过本步」处理，不要静默什么都不做。
                advanceQuestion(null)
            }
            // 通用选择器：没有任何附加语义，选中只意味着"用户做了选择"。
            // 这里**不能**再打印"已执行 xxx（Mock）"——那会让人以为某个动作发生了。
            // 附件也不再走这里：它已经有真正的系统图片选择器（attachImage）。
            ChoiceIntent.GENERIC -> _state.update {
                it.copy(choicePicker = null, message = "已选择：${option.label}")
            }
            // 计划模式下的「目标澄清」：用户选定的目标本身就是一条提示词。
            // 计划正文由引擎产出（不再由界面拼），所以这里只是把它发出去。
            ChoiceIntent.PLAN_GOAL -> {
                val goal = if (option.detail.isNotEmpty()) "${option.label} —— ${option.detail}" else option.label
                _state.update { it.copy(choicePicker = null, composerText = goal) }
                send()
            }
        }
    }

    /**
     * 选择窗口里的「其他回答…」提交：不走选项，直接把这段自由文本当作输入。
     * 在计划模式下它等价于"自定义目标"，会作为提示词发给引擎；
     * 在会话备注中它是备注正文。
     */
    fun onSubmitFreeForm(text: String) {
        val trimmed = text.trim()
        val picker = _state.value.choicePicker
        if (picker == null) return
        // 备注可以提交空字符串（= 清除），所以不能和"空输入就关闭"混在一起。
        if (picker.intent == ChoiceIntent.SESSION_NOTE) {
            val target = pendingSessionAction
            pendingSessionAction = null
            _state.update { it.copy(choicePicker = null) }
            if (target == null) {
                _state.update { it.copy(message = "找不到目标会话，备注未保存") }
            } else {
                saveSessionNote(target, trimmed)
            }
            return
        }
        if (trimmed.isEmpty()) {
            dismissChoicePicker()
            return
        }
        if (picker.intent == ChoiceIntent.PLAN_GOAL) {
            _state.update { it.copy(choicePicker = null, composerText = trimmed) }
            send()
            return
        }
        _state.update {
            it.copy(
                choicePicker = null,
                composerText = trimmed,
                slashQuery = null,
                slashMatches = emptyList(),
                message = null,
            )
        }
    }

    // ---------- 会话 ----------

    /**
     * 新建会话。
     *
     * 这里只重置引擎的对话上下文（新 workflowId），**不**立即建文件：
     * 引擎的 `SessionStore` 是在第一轮发消息时才创建的，提前建会留下
     * 一堆"0 条消息"的空会话文件，侧栏会很快被垃圾填满。
     * 所以界面上先插入一个占位条目，等第一轮结束后 [refreshSessions] 会用真实文件替换它。
     */
    fun newSession() {
        engine.resetConversation()
        discardStreamingAssistant()
        // 上一轮即使还在跑也已经不属于任何会话了，清掉归属标记避免误拦。
        turnSessionId = null
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null
        _state.update {
            it.copy(
                // 占位条目的 id 用空串而不是伪造 id：它不是真实文件，
                // 若被当成路径去打开会失败，空串能让"打不开"这件事显式化。
                activeSessionId = "",
                transcript = emptyList(),
                tasks = emptyList(),
                contextTokens = 0,
                composerBusy = false,
                workingStatus = null,
                tab = WorkspaceTab.CHAT,
                sidebarOpen = false,
                message = "已开始新会话，第一条消息会创建会话文件",
            )
        }
    }

    /**
     * 打开一条历史会话。
     *
     * 两件事都要做，缺一不可：
     * 1. `engine.resumeConversation(file)` —— 让**引擎**把历史装回 provider 上下文，
     *    否则界面上看得到历史、模型却不知道，追问会答非所问；
     * 2. 从同一个 JSONL 重建界面 transcript。
     *
     * 文件读取放 IO 线程：一个长会话的 JSONL 可能有几千行，在主线程读会卡住界面。
     */
    fun openSession(session: SessionSummary) {
        val file = File(session.id)
        if (!file.isFile) {
            _state.update { it.copy(message = "会话文件不存在：${session.id}") }
            refreshSessions()
            return
        }
        // 切会话必须先把引擎这一轮停掉：否则旧会话的回调还会继续往新的
        // transcript 里写（引擎的 cancel 会连带取消三个门控）。
        engine.cancel()
        discardStreamingAssistant()
        // 切走会话：上一轮的迟到事件一律不应再落到新会话里。
        // 注意这里**不要**把 turnSessionId 置 null —— 置空等于关掉拦截。
        // 保留了旧值，[finishTurnWithError] 才能看出"归属 ≠ 当前"并丢弃。
        currentGroupId = null
        liveTools.clear()
        progressBuffers.clear()
        progressLastFlush.clear()
        pendingPermissionId = null

        _state.update { it.copy(sidebarOpen = false, tab = WorkspaceTab.CHAT, workingStatus = "正在载入会话…") }
        viewModelScope.launch(Dispatchers.IO) {
            val transcript = SessionReader.transcript(file)
            val tasks = SessionReader.tasks(file)
            // 引擎必须先配置好才有 config 可用，否则 resumeConversation 不会恢复项目/工作流。
            val resumed = runCatching {
                engine.configure(engineOverrides())
                engine.resumeConversation(file)
            }
            _state.update {
                it.copy(
                    activeSessionId = session.id,
                    transcript = transcript,
                    tasks = tasks,
                    contextTokens = engine.estimateContextTokens(),
                    contextWindow = engine.contextWindowTokens().takeIf { w -> w > 0 } ?: it.contextWindow,
                    composerBusy = false,
                    workingStatus = null,
                    message = resumed.exceptionOrNull()?.let { error ->
                        "会话历史已显示，但引擎恢复失败：" + (error.message ?: "未知原因")
                    },
                )
            }
        }
    }

    /**
     * 删除会话。
     *
     * 删除的是**磁盘上的文件**，不是列表里的一项 —— 只从列表移除的话，
     * 下次启动它又会回来，用户会以为删除没生效。
     */
    fun deleteSession(id: String) {
        if (id.isEmpty()) {
            // 尚未落盘的占位会话：直接清掉当前视图即可。
            _state.update { it.copy(activeSessionId = "", transcript = emptyList(), tasks = emptyList(), message = "会话已清空") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val removed = SessionReader.delete(File(id))
            val wasActive = _state.value.activeSessionId == id
            if (wasActive) {
                engine.resetConversation()
                discardStreamingAssistant()
            }
            val sessions = SessionReader.list(_state.value.projectPath)
            val next = sessions.firstOrNull()
            _state.update {
                it.copy(
                    sessions = sessions,
                    activeSessionId = if (wasActive) "" else it.activeSessionId,
                    transcript = if (wasActive) emptyList() else it.transcript,
                    tasks = if (wasActive) emptyList() else it.tasks,
                    message = if (removed) "会话已删除" else "删除失败：文件可能已被移除",
                )
            }
            // 删除后仍留历史时，顺手把最近一条打开，避免出现"删完一片空白"。
            if (wasActive && next != null) openSession(next)
        }
    }

    /** 从磁盘重新读取会话列表（新建/发消息/删除之后调用）。 */
    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = SessionReader.list(_state.value.projectPath)
            _state.update { state ->
                // 保留"尚未落盘的新会话"这一状态：此刻 activeSessionId 为空且
                // 列表里还没有对应文件，不能被列表刷新覆盖掉。
                state.copy(sessions = sessions)
            }
        }
    }



    fun openSidebar() {
        // 每次拉开侧栏都重读一次文件列表：新会话是在**第一轮消息之后**才落盘的，
        // 不刷新的话"刚聊完的会话"在侧栏里看不到。
        refreshSessions()
        _state.update { it.copy(sidebarOpen = true) }
    }
    fun closeSidebar() = _state.update { it.copy(sidebarOpen = false) }

    // ---------- 工作区 ----------

    fun selectTab(tab: WorkspaceTab) {
        _state.update { it.copy(tab = tab) }
        // 进入「变更」页时才去读 git。切换 Tab 是明确的用户动作，
        // 每次切过去读一次是合理的；若挂在回合结束自动读，大仓库会明显拖慢对话。
        if (tab == WorkspaceTab.CHANGES) refreshDiff()
    }

    /**
     * 重新拉一次变更列表（变更面板的「刷新」）。
     *
     * 对应原版 `refreshChanges()`（重跑 `git status --short` + `git diff`）。
     * 这里数据来自 [repo]，所以"刷新"等价于重新读一次。
     */
    /**
     * 刷新 git 变更。
     *
     * 三条 git 命令（rev-parse / status / diff）+ 未跟踪文件的补丁，必须放 IO 线程，
     * 而且这是**唯一**会去读 git 的入口 —— 不在回合结束时自动跑：
     * 一个大仓库的 `git diff` 可能几秒，挂在每个回合结尾会让对话明显变卡。
     */
    fun refreshDiff() {
        val path = _state.value.projectPath
        _state.update { it.copy(diff = it.diff.copy(loading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { GitChanges.read(getApplication(), path) }
                .getOrElse { error ->
                    DiffState(note = "读取 git 变更失败：" + (error.message ?: "未知原因"))
                }
            _state.update {
                it.copy(
                    diff = result,
                    message = when {
                        result.note.isNotEmpty() -> result.note
                        result.files.isEmpty() -> "工作区没有未提交的变更"
                        else -> "已刷新变更列表（${result.files.size} 个文件）"
                    },
                )
            }
        }
    }

    fun cycleThemeMode() = _state.update {
        val next = when (it.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        it.copy(themeMode = next, message = "主题：${next.label}")
    }

    // ---------- 内置 Termux 运行环境 ----------

    /**
     * 生成自检报告。
     *
     * 刻意**绝不抛异常**：这是排障入口，它自己崩掉或什么都不显示，就等于把唯一的诊断手段也弄没了。
     * 探测失败时把失败原因当作报告正文显示出来，至少还能看到是哪一项炸的。
     */
    private fun buildEnvironmentReport(): String = runCatching {
        EnvDoctor.report(getApplication())
    }.getOrElse { error ->
        "# 蜘蛛 · 环境自检\n\n" +
            "自检本身失败了：\n" +
            "${error.javaClass.name}: ${error.message}\n\n" +
            "堆栈：\n" +
            error.stackTraceToString() +
            "\n— 报告结束，可整段复制"
    }

    /** 打开「环境自检」：现场跑一遍探测并生成可复制的报告。 */
    fun openEnvironment() {
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentOpen = true,
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    fun closeEnvironment() = _state.update { it.copy(environmentOpen = false) }

    /** 重新生成自检报告（安装前后各跑一次，便于对比）。 */
    fun refreshEnvironmentReport() {
        val reportText = buildEnvironmentReport()
        _state.update {
            it.copy(
                environmentReport = reportText,
                runtimeReady = runCatching { installer.isInstalled() }.getOrDefault(false),
            )
        }
    }

    /**
     * 安装（或重新初始化）内置 Termux 环境。
     *
     * 解压 32MB、落 3473 个文件、建 1177 个符号链接，必须放到 IO 线程，
     * 并且把进度实时回灌给 UI —— 整个过程在真机上要十几秒，不给反馈会被当成卡死。
     */
    fun installRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update {
            it.copy(runtimeInstalling = true, runtimeProgress = 0, runtimeMessage = "正在准备…")
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                installer.install { message, percent ->
                    _state.update { it.copy(runtimeMessage = message, runtimeProgress = percent) }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        runtimeInstalling = false,
                        runtimeMessage = "初始化失败：${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }.isSuccess

            if (ok) {
                val reportText = buildEnvironmentReport()
                _state.update {
                    it.copy(
                        runtimeInstalling = false,
                        runtimeProgress = 100,
                        runtimeMessage = "内置 Termux 环境已就绪",
                        runtimeReady = installer.isInstalled(),
                        environmentReport = reportText,
                    )
                }
                // home 目录是随运行环境一起出现的，所以工作区只能在这之后创建。
                // 不创建的话：变更面板永远显示"项目目录不存在"，
                // 终端的工作目录会回退到 home，会话也会落在一个不存在的项目键下。
                ensureWorkspace()
            }
        }
    }

    /** 幂等修复：清半成品目录、补齐 apt/dpkg 兼容层。适用于 apt 升级过或上次中断。 */
    fun repairRuntime() {
        if (_state.value.runtimeInstalling) return
        _state.update { it.copy(runtimeInstalling = true, runtimeMessage = "正在修复…") }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { installer.repairIfInstalled() }.isSuccess
            val reportText = buildEnvironmentReport()
            _state.update {
                it.copy(
                    runtimeInstalling = false,
                    runtimeMessage = if (ok) "已修复" else "修复失败",
                    runtimeReady = installer.isInstalled(),
                    environmentReport = reportText,
                )
            }
        }
    }

    /** 复制自检报告到剪贴板，便于把真机现状整段发出来定位问题。 */
    fun copyEnvironmentReport(): Boolean {
        val app = getApplication<Application>()
        val text = _state.value.environmentReport.ifBlank { buildEnvironmentReport() }
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("蜘蛛环境自检", text))
        _state.update { it.copy(message = "环境自检报告已复制") }
        return true
    }

    // ---------- 设置 ----------

    /**
     * 打开设置弹窗：从当前 state 快照一份 draft。
     *
     * 之后的编辑全部落在 draft 上，「取消」丢弃 draft 即可，不需要回滚任何东西。
     */
    fun openSettings() = _state.update {
        it.copy(settingsDraft = SettingsDraft.from(it))
    }

    fun closeSettings() = _state.update { it.copy(settingsDraft = null) }

    /** 弹窗内任意一项改动都走这里，保证 draft 只有一份写入路径。 */
    fun updateSettingsDraft(transform: (SettingsDraft) -> SettingsDraft) = _state.update {
        val draft = it.settingsDraft ?: return@update it
        it.copy(settingsDraft = transform(draft))
    }

    /** 设置弹窗直接回传整份 draft 时的入口（等价于 [updateSettingsDraft] 忽略旧值）。 */
    fun setSettingsDraft(draft: SettingsDraft) = _state.update { it.copy(settingsDraft = draft) }

    /** 「保存」：唯一把 draft 写回 state 的入口，并关闭弹窗。 */
    fun saveSettings() = _state.update {
        val draft = it.settingsDraft ?: return@update it
        draft.applyTo(it).copy(message = "设置已保存")
    }

    /**
     * 分类页里的「入口类」条目：先关掉设置弹窗，再把对应目标打开。
     *
     * 这里只接**真的有实现**的目标。原来还给 `canvas` / `other` 留了分支，
     * 但它们分别指向"未移植的运行时画布"和"自定义头部提示词的第二个入口"，
     * 现在画布入口已撤、提示词并入「上下文与项目」，两条分支一并删掉——
     * 留着只会让后来的人以为这里有功能。
     */
    fun navigateFromSettings(target: String) {
        _state.update { it.copy(settingsDraft = null) }
        when (target) {
            "apiProfiles" -> openApiConfig()
            "mcp" -> openMcpConfig()
            else -> Unit
        }
    }

    // ---------- 对话流交互 ----------

    fun toggleToolExpanded(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(
                        tools = item.tools.map { if (it.id == id) it.copy(expanded = !it.expanded) else it },
                    )
                },
            )
        }
    }

    /**
     * 批量设置工具组里各成员的展开状态。
     *
     * [collapseIds] 是**要收起的成员 id 集合**：集合内的成员收起，其余展开。
     * 因此"全部展开"传空集，"全部收起"传全部成员 id。
     */
    fun toggleGroupExpanded(id: String, collapseIds: Set<String>) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { item ->
                    if (item.id != id || item.kind != ChatKind.TOOL_GROUP) item
                    else item.copy(tools = item.tools.map { it.copy(expanded = it.id !in collapseIds) })
                },
            )
        }
    }

    fun toggleThinking(id: String) {
        _state.update { s ->
            s.copy(
                transcript = s.transcript.map { if (it.id == id) it.copy(thinkingExpanded = !it.thinkingExpanded) else it },
            )
        }
    }

    // ---------- 文件 ----------

    fun openDirectory(entry: FileEntry) {
        navigateTo(entry.path)
    }

    /** 刷新文件面板。Agent 改动文件后用它重新列目录。 */
    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadFiles()
            _state.update { it.copy(message = "已刷新文件列表") }
        }
    }

    fun navigateTo(path: String) {
        // 列目录要读盘，放 IO 线程：大目录（例如 node_modules）在主线程列会卡住界面。
        _state.update { it.copy(filePath = path, openFile = null) }
        viewModelScope.launch(Dispatchers.IO) { reloadFiles() }
    }

    fun navigateUp() {
        val current = _state.value.filePath
        val root = rootPath()
        if (current == root) return
        val parent = current.substringBeforeLast('/', "")
        if (parent.isEmpty() || parent.length < root.length) {
            // 已经到根（或再往上会越过根）：回到根，而不是继续往外走。
            navigateTo(root)
            return
        }
        navigateTo(parent)
    }

    fun openFile(entry: FileEntry) {
        if (entry.directory) {
            openDirectory(entry)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = FileBrowser.read(entry.path)
            _state.update { it.copy(openFile = file, message = "已打开 ${entry.name}（只读）") }
        }
    }

    /** 文件面板的根路径（内置 Termux home 不存在时回退到应用私有目录）。 */
    /** 文件面板的根 = 当前项目路径（不是 Termux home），这样面包屑与「上一级」都以项目为界。 */
    private fun rootPath(): String = _state.value.projectPath

    fun closeFile() = _state.update { it.copy(openFile = null) }

    // ---------- 操作反馈 ----------
    //
    // 原先这里由 Snackbar 消费 message，但浮层会遮挡底部输入器，已移除。
    // state.message 继续记录最后一次操作结果（「已切换 API 配置」「模型：xxx」等），
    // 留作将来接内联提示的缓冲；不接也不会渲染出任何东西。

    companion object {
        fun clockLabel(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        fun formatTokens(value: Int): String = when {
            value >= 1_000_000 -> trimZero(value / 1_000_000f) + "m"
            value >= 1_000 -> trimZero(value / 1_000f) + "k"
            else -> "$value"
        }

        /** 200.0k → 200k，省出顶栏 chip 的横向空间。 */
        private fun trimZero(value: Float): String =
            if (value >= 100f || value % 1f == 0f) String.format(Locale.US, "%.0f", value)
            else String.format(Locale.US, "%.1f", value)

        /** 思考预览保留的尾部字符数（原版 `liveThinking` 上限 1200）。 */
        private const val THINKING_LIMIT = 1200

        /** 单条工具实时输出的上限（原版 `liveOutput` 上限 40000）。 */
        private const val LIVE_OUTPUT_LIMIT = 40_000

        /** 工具实时输出落盘间隔：每条进程输出都改一次 StateFlow 会把界面拖垮。 */
        private const val PROGRESS_FLUSH_MS = 200L

        /** 助手气泡上保留的过程步骤条数上限（原版 12）。 */
        private const val PROCESS_STEP_LIMIT = 12

        /** 压缩摘要回显上限：摘要是给人看要点的，整段贴进对话会把记录淹掉。 */
        private const val COMPACT_SUMMARY_LIMIT = 600

        /**
         * 引擎"没什么可压缩"时返回的前缀。
         *
         * 这不是我发明的约定：`ZhiCodeEngine.compactContextSemantic` 用这句话作为
         * 未调用模型的信号，引擎自己的自动压缩分支也用 `startsWith` 判断它。
         * 引擎没有给出结构化返回值，所以只能照它的契约来。
         */
        private const val ENGINE_NOTHING_TO_COMPACT = "上下文已经足够精简"

        /** 文本附件（技能）拼进提示词的长度上限，与原版一致。 */
        private const val TEXT_ATTACHMENT_LIMIT = 60_000

        /**
         * `/init` 的指令，逐字取自原版。
         *
         * 全英文是刻意的（原版如此）：这段是要模型执行的指令，
         * 而它明确要求限制调研范围、禁止起子 Agent，措辞改动会改变实际行为，
         * 所以不翻译、不改写。
         */
        private const val INIT_INSTRUCTION =
            "This is the built-in fast /init maintenance command. Do not enter plan mode, create tasks, " +
                "launch subagents, or perform broad repository research. Read only the existing " +
                "IQ.md/CLAUDE.md/README and primary build manifest or script when present, then directly " +
                "create or improve IQ.md with concise build, test, architecture, conventions, and " +
                "repository-specific instructions. Preserve useful existing instructions and run at most " +
                "one small verification command."
    }
}
