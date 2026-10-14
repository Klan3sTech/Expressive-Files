package com.klan3s.files.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Работа с томами через Storage Access Framework (флешки/SD без прямого доступа). */
class SafOps(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    /** Быстрый листинг через ContentResolver (DocumentFile.listFiles слишком медленный). */
    fun list(treeUri: Uri, showHidden: Boolean): List<FsEntry> {
        val docId = runCatching { DocumentsContract.getDocumentId(treeUri) }.getOrElse { DocumentsContract.getTreeDocumentId(treeUri) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val out = ArrayList<FsEntry>()
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        cr.query(childrenUri, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val name = c.getString(1) ?: continue
                val mime = c.getString(2); val size = if (c.isNull(3)) 0L else c.getLong(3); val mod = if (c.isNull(4)) 0L else c.getLong(4)
                if (!showHidden && name.startsWith(".")) continue
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                out += FsEntry(File(name), name, isDir, if (isDir) 0 else size, mod, 0, docUri = uri)
            }
        }
        return out
    }

    fun parentOf(treeUri: Uri): Uri? {
        val id = runCatching { DocumentsContract.getDocumentId(treeUri) }.getOrNull() ?: return null
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        if (id == rootId) return null
        val parentId = id.substringBeforeLast('/', "")
        if (parentId.isEmpty() || parentId.length < rootId.length) return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
    }

    fun displayPath(uri: Uri): String {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: DocumentsContract.getTreeDocumentId(uri)
        return id.substringAfter(':', "")
    }

    fun mkdir(parent: Uri, name: String): Boolean =
        runCatching { DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name) != null }.getOrDefault(false)

    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)

    fun rename(uri: Uri, newName: String): Boolean = runCatching { DocumentsContract.renameDocument(cr, uri, newName) != null }.getOrDefault(false)

    fun createFile(parent: Uri, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(cr, parent, FileOps.mime(name).let { if (it == "*/*") "application/octet-stream" else it }, name) }.getOrNull()

    fun copyToCache(uri: Uri, dest: File): Boolean = runCatching {
        dest.parentFile?.mkdirs()
        cr.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        true
    }.getOrDefault(false)

    /** Рекурсивно копирует SAF-документ в локальную папку (в кэш). */
    fun downloadTree(uri: Uri, name: String, isDir: Boolean, destDir: File, onFile: (String) -> Unit) {
        val target = File(destDir, name)
        if (isDir) {
            target.mkdirs()
            list(uri, true).forEach { downloadTree(it.docUri!!, it.name, it.isDir, target, onFile) }
        } else { onFile(name); copyToCache(uri, target) }
    }

    /** Рекурсивно загружает локальный файл/папку в SAF-каталог. */
    fun uploadTree(src: File, parent: Uri, onFile: (String) -> Unit) {
        if (src.isDirectory) {
            val existing = list(parent, true).firstOrNull { it.name == src.name && it.isDir }?.docUri
            val dirUri = existing ?: DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, src.name) ?: error("Не удалось создать папку ${src.name}")
            src.listFiles()?.forEach { uploadTree(it, dirUri, onFile) }
        } else {
            onFile(src.name)
            val out = createFile(parent, src.name) ?: error("Не удалось создать ${src.name}")
            cr.openOutputStream(out)!!.use { o -> src.inputStream().use { it.copyTo(o, 1 shl 16) } }
        }
    }

    fun uploadContents(fromDir: File, parent: Uri, onFile: (String) -> Unit) {
        fromDir.listFiles()?.forEach { uploadTree(it, parent, onFile) }
    }
}
