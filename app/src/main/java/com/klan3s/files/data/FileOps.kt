package com.klan3s.files.data

import android.os.Environment
import java.io.File

enum class SortMode(val label: String) { NAME("Имя"), SIZE("Размер"), DATE("Дата"), TYPE("Тип") }

object FileOps {
    val root: File get() = Environment.getExternalStorageDirectory()

    /** Нужен ли root для доступа к этому пути. */
    fun needsRoot(f: File): Boolean = !f.canRead() || (f.isDirectory && f.listFiles() == null)
    fun needsRootWrite(dir: File): Boolean = !dir.canWrite()

    fun list(dir: File, showHidden: Boolean, sort: SortMode, foldersFirst: Boolean, useRoot: Boolean): List<FsEntry> {
        val files = dir.listFiles()
        val entries: List<FsEntry> = if (files == null) {
            if (useRoot && RootShell.check()) RootShell.list(dir, showHidden) else emptyList()
        } else files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                val isDir = f.isDirectory
                FsEntry(f, f.name, isDir, if (isDir) 0 else f.length(), f.lastModified(),
                    if (isDir) (f.list()?.size ?: 0) else 0)
            }.toList()
        val cmp: Comparator<FsEntry> = when (sort) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareByDescending<FsEntry> { it.size }.thenBy { it.name.lowercase() }
            SortMode.DATE -> compareByDescending<FsEntry> { it.modified }.thenBy { it.name.lowercase() }
            SortMode.TYPE -> compareBy<FsEntry> { it.extension }.thenBy { it.name.lowercase() }
        }
        return if (foldersFirst) entries.sortedWith(compareByDescending<FsEntry> { it.isDir }.then(cmp)) else entries.sortedWith(cmp)
    }

    fun uniqueChild(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    fun delete(f: File, useRoot: Boolean): Boolean =
        f.deleteRecursively() || (useRoot && RootShell.delete(f))

    fun mkdir(f: File, useRoot: Boolean): Boolean = f.mkdirs() || (useRoot && RootShell.mkdir(f))

    fun rename(a: File, b: File, useRoot: Boolean): Boolean = a.renameTo(b) || (useRoot && RootShell.rename(a, b))

    fun copyInto(src: File, destDir: File, move: Boolean, useRoot: Boolean, onFile: (String) -> Unit) {
        if (useRoot && (needsRoot(src) || needsRootWrite(destDir))) {
            onFile(src.name)
            if (!RootShell.copyTreeInto(src, destDir, move)) error("Не удалось скопировать ${src.name} (root)")
            return
        }
        val target = uniqueChild(destDir, src.name)
        if (move && src.renameTo(target)) { onFile(src.name); return }
        if (src.isDirectory) {
            target.mkdirs()
            src.listFiles()?.forEach { copyInto(it, target, false, useRoot, onFile) }
        } else {
            onFile(src.name)
            src.copyTo(target, overwrite = true)
        }
        if (move) src.deleteRecursively()
    }

    fun countFiles(f: File): Int = if (f.isDirectory) (f.listFiles()?.sumOf { countFiles(it) } ?: 1) else 1

    fun mime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
