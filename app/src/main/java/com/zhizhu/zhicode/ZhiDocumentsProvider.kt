package com.zhizhu.zhicode

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.termux.app.zhicode.core.DocumentTree
import com.termux.app.zhicode.core.ProviderLog
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/**
 * 把应用的 HOME 目录**发布**给系统文件管理器（DocumentsUI / 系统「文件」/「选择文件」）。
 *
 * ## 它解决的问题
 *
 * HOME 在应用私有目录里（`/data/user/0/<pkg>/files/home`），`0700` 加 SELinux 标签
 * —— 任何第三方文件管理器都进不去，不给 root 没有例外。而把 HOME 搬到共享存储的
 * 代价很大：那一片挂载是 `noexec`（`/proc/mounts`:
 * `/dev/fuse /storage/emulated fuse rw,nosuid,nodev,noexec`），
 * 搬过去之后 `./gradlew` 这类脚本就不能直跑了 —— 而它正是本应用最主要的用法之一。
 *
 * 本类让文件管理器通过 SAF 读我们，我们按自己的规则读写私有目录里的真实文件：
 * **HOME 一个字节都不搬**，可执行权限、符号链接、apt 全部照旧。
 *
 * ## 安全边界落在清单那一行
 *
 * `android:permission="android.permission.MANAGE_DOCUMENTS"` 是必须的，
 * 它同时是功能要求与安全边界：这一档是 `signature|privileged` 权限，
 * **只有系统进程持有** —— 于是只有 DocumentsUI 调得到，普通第三方应用调不到。
 * `exported="true"` 是 SAF 的硬性要求（系统要能发现这个 provider），
 * 真正的访问控制由那一行权限完成。
 *
 * ## 本类只做机械动作
 *
 * 拼 `MatrixCursor`、把调用转发给 [DocumentTree]、把它的 `IOException` 转成
 * `FileNotFoundException`（SAF 的约定）。会算错的那一半（ID ↔ 路径、名字校验、
 * 符号链接越界）在 [DocumentTree] 里 —— 纯逻辑、无 `android.*`、有单测。
 * 这么切是因为那几类错在真机上**没有报错可查**：只表现为文件管理器里某一项打不开。
 *
 * ## 与参考实现（IQ Code）的有意分歧
 *
 * 思路来自反编译 IQ Code 的 `IqDocumentsProvider`，但这几处我们**故意不同**：
 *
 * - **目录不按修改时间排序**：不声明 `FLAG_DIR_PREFERS_LAST_MODIFIED`。
 *   那是给相册/下载目录用的；源码目录按修改时间排会看起来像被打乱了。
 *   列表按名字排（见 [queryChildDocuments]）。
 * - **名字规则与 `FileOps` 共用一个实现**：参考实现自己写了一份 `safeName`，
 *   与它应用内的文件面板没有任何关系（它也没有那样的面板）。我们两处都有，
 *   口径不一致就会出现"一边能建、另一边打不开"，所以规则只留一处。
 * - **不做 `querySearchDocuments`，也不声明 `FLAG_SUPPORTS_SEARCH`**：
 *   声明了却不实现等于给用户一个点了没反应的搜索框。
 * - **失败时如实抛**：参考实现在 `queryChildDocuments` 里对单条越界静默跳过
 *   （我们保留这一点，因为它防止一条坏项废掉整个目录），但在新建/重命名上
 *   我们连**同名前缀**的情况也会明确报错。
 */
class ZhiDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    // ------------------------------------------------------------------ 根

    override fun queryRoots(projection: Array<out String>?): Cursor =
        traced("queryRoots", projection?.joinToString(",")) { rootsCursor(projection) }

    private fun rootsCursor(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )
        val cursor = MatrixCursor(columns)
        cursor.emit(
            ROOT_FIELDS,
            arrayOf(
                DocumentTree.ROOT_ID,
                DocumentTree.ROOT_DOCUMENT_ID,
                DocumentTree.ROOT_TITLE,
                // 可在根下新建 + 支持 isChildDocument 查询。
                // 刻意**不带** FLAG_SUPPORTS_SEARCH：没实现 querySearchDocuments。
                // 也不带 FLAG_LOCAL_ONLY：带上它只会让部分文件管理器
                // 多套一层"仅本地"的分组，观感更绕，而没有实际功能差异。
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
                "*/*",
                home().usableSpace,
                "应用的工作目录（可读写）",
                // 不给图标：让它用文件管理器自己的存储图标，比塞一个错的合适。
                null,
            ),
        )
        return cursor
    }

    // ------------------------------------------------------------------ 查询

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        traced("queryDocument", documentId) { documentCursor(documentId, projection) }

    private fun documentCursor(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_FIELDS)
        cursor.include(fileForId(documentId), documentId)
        return cursor
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = traced("queryChildDocuments", parentDocumentId, sortOrder) {
        childrenCursor(parentDocumentId, projection)
    }

    private fun childrenCursor(parentDocumentId: String, projection: Array<out String>?): Cursor {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("不是目录：$parentDocumentId")

        val cursor = MatrixCursor(projection ?: DOCUMENT_FIELDS)
        // 列不出来（权限不足）时给一张空表，**不抛**：抛出去整个目录在文件管理器里
        // 会变成"加载失败"，而空目录至少还能让用户往上走。
        val children = parent.listFiles() ?: return cursor

        // 按名字排（不按修改时间）：见类注释里与参考实现的分歧说明。
        children.sortedBy { it.name.lowercase(Locale.US) }.forEach { child ->
            if (DocumentTree.isPrivateName(child.name)) return@forEach
            // 单条越界不该让整个目录列不出来 —— 静默跳过这一条，列其余的。
            runCatching {
                cursor.include(DocumentTree.canonicalUnderRoot(home(), child), DocumentTree.documentIdFor(home(), child))
            }
        }
        return cursor
    }

    /** 声明了 `FLAG_SUPPORTS_IS_CHILD` 就得实现它，否则那一档是假的。 */
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        traced("isChildDocument", parentDocumentId, documentId) {
            runCatching { DocumentTree.isChildOf(fileForId(parentDocumentId), fileForId(documentId)) }
                .getOrDefault(false)
        }

    // ------------------------------------------------------------------ 读写

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor = traced("openDocument", documentId, mode) {
        openDescriptor(documentId, mode)
    }

    private fun openDescriptor(documentId: String, mode: String): ParcelFileDescriptor {
        val file = fileForId(documentId)
        if (!file.isFile) throw FileNotFoundException("不是文件：$documentId")
        val requested = mode.ifEmpty { "r" }

        // SAF 的 mode 是 POSIX 风格的小写串：r / w / rw，可选带 t（truncate）。
        val writable = !requested.startsWith("r") || requested.startsWith("rw")
        var flags = when {
            requested.startsWith("rw") -> ParcelFileDescriptor.MODE_READ_WRITE
            // MODE_CREATE：文件管理器"另存为"时会先给一个还不存在的名字。
            requested.startsWith("w") ->
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            requested.startsWith("r") -> ParcelFileDescriptor.MODE_READ_ONLY
            else -> throw FileNotFoundException("不支持的模式：$mode")
        }
        // "t" 只在可写时有意义；与只读组合是无效的，别传下去让内核猜。
        if (writable && requested.contains('t')) flags = flags or ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(file, flags)
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String =
        traced("createDocument", parentDocumentId, mimeType, displayName) {
            createEntry(parentDocumentId, mimeType, displayName)
        }

    private fun createEntry(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("不是目录：$parentDocumentId")

        val target = try {
            DocumentTree.canonicalUnderRoot(home(), File(parent, DocumentTree.safeName(displayName)))
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        if (target.exists()) throw FileNotFoundException("同名条目已存在：${target.name}")

        val created = try {
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile()
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        if (!created) throw FileNotFoundException("无法创建 ${target.name}")
        return try {
            DocumentTree.documentIdFor(home(), target)
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        traced("deleteDocument", documentId) { removeEntry(documentId) }
    }

    private fun removeEntry(documentId: String) {
        val target = fileForId(documentId)
        if (target == home()) throw FileNotFoundException("无法删除共享根目录")
        DocumentTree.deleteTree(target)?.let { throw FileNotFoundException("无法删除：$it") }
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String =
        traced("renameDocument", documentId, displayName) { renameEntry(documentId, displayName) }

    private fun renameEntry(documentId: String, displayName: String): String {
        val target = fileForId(documentId)
        val parent = target.parentFile ?: throw FileNotFoundException("无法重命名共享根目录")

        val renamed = try {
            DocumentTree.canonicalUnderRoot(home(), File(parent, DocumentTree.safeName(displayName)))
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        if (renamed == target) return documentId
        if (renamed.exists()) throw FileNotFoundException("同名条目已存在：${renamed.name}")
        if (!target.renameTo(renamed)) throw FileNotFoundException("无法重命名：${target.name}")
        return try {
            DocumentTree.documentIdFor(home(), renamed)
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
    }

    // ------------------------------------------------------------------ 行

    /**
     * 写一行文档。
     *
     * flags 分目录与文件两套 —— 混用会给出点了没反应的菜单项：
     * - 目录：重命名 / 删除 / **在里面新建**（对目录谈"写内容"没有意义）；
     * - 文件：重命名 / 删除 / **写**（编辑保存）。
     */
    private fun MatrixCursor.include(file: File, documentId: String) {
        val directory = file.isDirectory
        var flags = DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        flags = if (directory) {
            flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }

        emit(
            DOCUMENT_FIELDS_WITH_SUMMARY,
            arrayOf(
                documentId,
                if (documentId == DocumentTree.ROOT_DOCUMENT_ID) "HOME" else file.name,
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR else mimeOf(file),
                flags,
                if (file.isFile) file.length() else null,
                file.lastModified(),
                null,
            ),
        )
    }

    /**
     * 按 cursor **自己的列顺序**写一行。
     *
     * ⚠️ 不能改成"无条件 add 全部列名"：`MatrixCursor.RowBuilder.add(列名, 值)`
     * 对**不在投影里**的列名会抛 `IllegalArgumentException`。SAF 的调用方
     * （各厂商的文件管理器）会传各种子集投影，那样写会让这一项直接打不开 ——
     * 而且异常发生在 provider 里，界面上只会看到"无法加载"。
     */
    private fun MatrixCursor.emit(names: Array<String>, values: Array<Any?>) {
        val row = newRow()
        for (column in columnNames) {
            val index = names.indexOfFirst { it == column }
            row.add(if (index >= 0) values[index] else null)
        }
    }

    // -------------------------------------------------------------- 调用日志

    /**
     * 记一次 provider 调用（成功与失败都记），然后原样返回/抛出。
     *
     * 为什么每个入口都要过这一层：**框架会吞掉这里的异常** ——
     * `DocumentsProvider.call()` 把异常接住、写进 logcat、只回一个 `null`。
     * 于是"新建文件夹失败"在文件管理器那一侧只剩一句它自己的通用文案
     * （真机上见到的是 `Failed to create directory: 1`），看不到我们的原因；
     * 而真机上取 logcat 需要 adb 或 root，两样都没有。
     *
     * 所以原因必须由我们自己落到**用户打得到的地方**：`~/tmp/documents-provider.log`
     * —— 应用内的文件面板和文件管理器都读得到，诊断页也会把尾巴贴出来。
     *
     * 刻意不 `inline`：这几个入口每次最多一次文件追加，不值得为了省一层 lambda
     * 把实现细节铺进每个方法；可读性在这里更重要。
     */
    private fun <T> traced(method: String, vararg args: String?, body: () -> T): T = try {
        val result = body()
        // 查询类顺手记下行数：空列表与"查询失败"是两件事，日志里要能分开。
        val rows = (result as? Cursor)?.let { " [${it.count} 行]" }.orEmpty()
        log(method, args.toList(), "ok$rows")
        result
    } catch (t: Throwable) {
        log(method, args.toList(), "FAIL ${t.javaClass.simpleName}: ${t.message}")
        throw t
    }

    /**
     * 追加一行。**任何失败都吞掉**：日志把 provider 弄坏是最糟的结果
     * （它的存在意义只是让人能看见问题，它自己绝不能成为问题）。
     */
    private fun log(method: String, args: List<String?>, outcome: String) {
        runCatching {
            val dir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "tmp")
            if (!dir.isDirectory && !dir.mkdirs()) return
            val file = File(dir, ProviderLog.FILE_NAME)
            if (ProviderLog.shouldTrim(file.length())) {
                file.writeText(ProviderLog.trimTail(file.readText(), ProviderLog.KEEP_BYTES))
            }
            file.appendText(
                ProviderLog.format(
                    System.currentTimeMillis(),
                    method,
                    args.map { ProviderLog.arg(it) },
                    outcome,
                ) + "\n",
            )
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 共享根 = 应用的 HOME。环境还没装时也建出来：provider 可能在那之前就被查询。 */
    private fun home(): File {
        val dir = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }

    @Throws(FileNotFoundException::class)
    private fun fileForId(documentId: String): File = try {
        DocumentTree.fileForId(home(), documentId)
    } catch (e: IOException) {
        throw FileNotFoundException(e.message)
    }

    private fun mimeOf(file: File): String {
        val name = file.name.lowercase(Locale.US)
        val dot = name.lastIndexOf('.')
        val type = if (dot < 0) null else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1))
        }
        return type ?: "application/octet-stream"
    }

    private companion object {
        val DOCUMENT_FIELDS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        /** 与 [DOCUMENT_FIELDS] 同一批，外加 summary（我们确实会填它）。 */
        val DOCUMENT_FIELDS_WITH_SUMMARY = DOCUMENT_FIELDS + DocumentsContract.Document.COLUMN_SUMMARY

        val ROOT_FIELDS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_ICON,
        )
    }
}
