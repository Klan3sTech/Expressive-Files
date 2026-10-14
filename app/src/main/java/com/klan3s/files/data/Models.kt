package com.klan3s.files.data

import java.io.File

data class FsEntry(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int = 0,
    val viaRoot: Boolean = false,
    val docUri: android.net.Uri? = null,   // не null — элемент доступен через SAF (DocumentFile)
) {
    val path: String get() = docUri?.toString() ?: file.absolutePath
    val isSaf: Boolean get() = docUri != null
    val isArchive: Boolean get() = !isDir && ArchiveService.isArchive(name)
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class ArchiveEntryInfo(
    val path: String,          // normalized, no leading slash, dirs end without slash
    val isDir: Boolean,
    val size: Long,
    val compressedSize: Long,
    val modified: Long,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = if (path.contains('/')) path.substringBeforeLast('/') else ""
}

enum class VolumeKind { INTERNAL, SD, USB, ROOT, OTHER }

data class Volume(
    val kind: VolumeKind,
    val label: String,
    val path: File,
    val totalBytes: Long,
    val freeBytes: Long,
    val removable: Boolean,
    val needsSaf: Boolean = false,          // обычный файловый доступ не работает — нужен SAF
    val treeUri: android.net.Uri? = null,   // сохранённое разрешение SAF
) {
    val key: String get() = path.absolutePath
}

enum class ArchiveFormat(val label: String, val ext: String, val hasLevel: Boolean) {
    ZIP("ZIP", ".zip", true),
    SEVEN_Z("7Z", ".7z", false),
    TAR_GZ("TAR.GZ", ".tar.gz", true),
    TAR_XZ("TAR.XZ", ".tar.xz", true),
    TAR_BZ2("TAR.BZ2", ".tar.bz2", true),
    TAR("TAR", ".tar", false),
}

enum class SevenZMethod(val label: String) { LZMA2("LZMA2"), LZMA("LZMA"), BZIP2("BZip2"), DEFLATE("Deflate"), COPY("Без сжатия") }

data class TaskProgress(
    val title: String,
    val detail: String = "",
    val fraction: Float? = null,   // null = indeterminate
    val done: Boolean = false,
    val error: String? = null,
)

fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}
