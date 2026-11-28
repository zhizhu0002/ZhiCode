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
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
                "*/*",
                home().usableSpace,
                "Ubuntu 24.04 容器根目录（可读写）",
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
            // 越界或不可解析的容器软链接只跳过单条，不影响其余目录浏览。
            runCatching {
                val resolved = DocumentTree.canonicalUnderRoot(home(), child)
                val docId = DocumentTree.documentIdFor(home(), child)
                cursor.include(resolved, docId, child.name)
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

    /** 写一行文档。 */
    private fun MatrixCursor.include(file: File, documentId: String, displayNameOverride: String? = null) {
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
                if (documentId == DocumentTree.ROOT_DOCUMENT_ID) "Debian /" else (displayNameOverride ?: file.name),
                if (directory) DocumentsContract.Document.MIME_TYPE_DIR else mimeOf(file),
                flags,
                if (file.isFile) file.length() else null,
                file.lastModified(),
                null,
            ),
        )
    }

    private fun MatrixCursor.emit(names: Array<String>, values: Array<Any?>) {
        val row = newRow()
        for (column in columnNames) {
            val index = names.indexOfFirst { it == column }
            row.add(if (index >= 0) values[index] else null)
        }
    }

    // -------------------------------------------------------------- 调用日志

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

    /** 追加一行。 */
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

    /** 共享根 = Debian 容器根目录 `/`。环境未初始化时也保证目录存在。 */
    private fun home(): File {
        val dir = ContainerPaths.rootfsDir()
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
