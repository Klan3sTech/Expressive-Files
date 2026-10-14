package com.klan3s.files.data

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod as CcSevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Чтение: zip/jar/apk, 7z, rar (RAR4), tar, tar.gz/tgz, tar.bz2/tbz2, tar.xz/txz, gz, bz2, xz
 * Запись: zip, 7z (LZMA2/LZMA/BZip2/Deflate/Copy), tar, tar.gz, tar.xz, tar.bz2
 */
object ArchiveService {

    private val kinds = linkedMapOf(
        ".tar.gz" to "tar.gz", ".tgz" to "tar.gz",
        ".tar.bz2" to "tar.bz2", ".tbz2" to "tar.bz2", ".tbz" to "tar.bz2",
        ".tar.xz" to "tar.xz", ".txz" to "tar.xz",
        ".tar" to "tar",
        ".zip" to "zip", ".jar" to "zip", ".apk" to "zip", ".aar" to "zip", ".xapk" to "zip",
        ".7z" to "7z",
        ".rar" to "rar",
        ".gz" to "gz", ".bz2" to "bz2", ".xz" to "xz",
    )

    fun kindOf(name: String): String? {
        val n = name.lowercase()
        return kinds.entries.firstOrNull { n.endsWith(it.key) }?.value
    }

    fun isArchive(name: String) = kindOf(name) != null

    fun baseName(name: String): String {
        val n = name.lowercase()
        val ext = kinds.keys.firstOrNull { n.endsWith(it) } ?: return name
        return name.dropLast(ext.length)
    }

    // ---------------------------------------------------------------- LIST

    fun list(file: File, password: String? = null): List<ArchiveEntryInfo> {
        val out = ArrayList<ArchiveEntryInfo>()
        when (val kind = kindOf(file.name) ?: error("Неизвестный формат")) {
            "zip" -> ZipFile.builder().setFile(file).get().use { zf ->
                val en = zf.entries
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    norm(e.name)?.let { out += ArchiveEntryInfo(it, e.isDirectory, e.size, e.compressedSize, e.time) }
                }
            }
            "7z" -> openSevenZ(file, password).use { sz ->
                for (e in sz.entries) {
                    norm(e.name)?.let {
                        out += ArchiveEntryInfo(it, e.isDirectory, e.size, -1,
                            if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0)
                    }
                }
            }
            "rar" -> Archive(file, password).use { ar ->
                for (h in ar.fileHeaders) {
                    norm(h.fileName.replace('\\', '/'))?.let {
                        out += ArchiveEntryInfo(it, h.isDirectory, h.fullUnpackSize, h.fullPackSize, h.mTime?.time ?: 0)
                    }
                }
            }
            "tar", "tar.gz", "tar.bz2", "tar.xz" -> openTar(file, kind).use { tin ->
                var e: TarArchiveEntry? = tin.nextEntry
                while (e != null) {
                    norm(e.name)?.let { out += ArchiveEntryInfo(it, e.isDirectory, e.size, -1, e.modTime?.time ?: 0) }
                    e = tin.nextEntry
                }
            }
            "gz", "bz2", "xz" -> out += ArchiveEntryInfo(baseName(file.name), false, -1, file.length(), file.lastModified())
        }
        return synthesizeDirs(out)
    }

    /** Добавляет записи для каталогов, которых нет явно в архиве. */
    private fun synthesizeDirs(entries: List<ArchiveEntryInfo>): List<ArchiveEntryInfo> {
        val map = LinkedHashMap<String, ArchiveEntryInfo>()
        for (e in entries) {
            var p = e.parent
            while (p.isNotEmpty() && !map.containsKey(p)) {
                map[p] = ArchiveEntryInfo(p, true, 0, 0, 0)
                p = if (p.contains('/')) p.substringBeforeLast('/') else ""
            }
            map[e.path] = e
        }
        return map.values.toList()
    }

    private fun norm(raw: String): String? {
        val p = raw.replace('\\', '/').trim('/').replace(Regex("/+"), "/")
        if (p.isEmpty()) return null
        if (p.split('/').any { it == ".." }) return null // zip-slip
        return p
    }

    private fun openSevenZ(file: File, password: String?): SevenZFile {
        val b = SevenZFile.builder().setFile(file)
        if (!password.isNullOrEmpty()) b.setPassword(password.toCharArray())
        return b.get()
    }

    private fun openTar(file: File, kind: String): TarArchiveInputStream {
        val raw: InputStream = BufferedInputStream(FileInputStream(file), 1 shl 16)
        val s = when (kind) {
            "tar.gz" -> GzipCompressorInputStream(raw, true)
            "tar.bz2" -> BZip2CompressorInputStream(raw, true)
            "tar.xz" -> XZCompressorInputStream(raw, true)
            else -> raw
        }
        return TarArchiveInputStream(s)
    }

    // ---------------------------------------------------------------- EXTRACT

    /**
     * @param filter принимает нормализованный путь записи; null = всё
     * @param strip  префикс, который нужно убрать из путей при записи (для извлечения подпапки)
     */
    fun extract(
        file: File, dest: File, filter: ((String) -> Boolean)? = null, strip: String = "",
        password: String? = null, onProgress: (done: Int, total: Int, name: String) -> Unit,
    ) {
        dest.mkdirs()
        val total = list(file, password).count { !it.isDir && (filter?.invoke(it.path) ?: true) }.coerceAtLeast(1)
        var done = 0
        val accept: (String) -> Boolean = { filter?.invoke(it) ?: true }
        fun target(p: String): File {
            val rel = if (strip.isNotEmpty() && p.startsWith("$strip/")) p.removePrefix("$strip/") else p
            val f = File(dest, rel)
            if (!f.canonicalPath.startsWith(dest.canonicalPath)) error("Небезопасный путь: $p")
            return f
        }
        fun write(p: String, isDir: Boolean, src: InputStream?) {
            val t = target(p)
            if (isDir) { t.mkdirs(); return }
            t.parentFile?.mkdirs()
            onProgress(done, total, t.name)
            FileOutputStream(t).use { o -> src?.copyTo(o, 1 shl 16) }
            done++
            onProgress(done, total, t.name)
        }

        when (val kind = kindOf(file.name) ?: error("Неизвестный формат")) {
            "zip" -> ZipFile.builder().setFile(file).get().use { zf ->
                val en = zf.entries
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    val p = norm(e.name) ?: continue
                    if (!accept(p)) continue
                    if (e.isDirectory) write(p, true, null) else zf.getInputStream(e).use { write(p, false, it) }
                }
            }
            "7z" -> openSevenZ(file, password).use { sz ->
                var e = sz.nextEntry
                while (e != null) {
                    val p = norm(e.name)
                    if (p != null && accept(p)) {
                        if (e.isDirectory) write(p, true, null) else write(p, false, sz.getInputStream(e))
                    }
                    e = sz.nextEntry
                }
            }
            "rar" -> Archive(file, password).use { ar ->
                for (h in ar.fileHeaders) {
                    val p = norm(h.fileName.replace('\\', '/')) ?: continue
                    if (!accept(p)) continue
                    if (h.isDirectory) write(p, true, null)
                    else {
                        val t = target(p); t.parentFile?.mkdirs()
                        onProgress(done, total, t.name)
                        FileOutputStream(t).use { o -> ar.extractFile(h, o) }
                        done++; onProgress(done, total, t.name)
                    }
                }
            }
            "tar", "tar.gz", "tar.bz2", "tar.xz" -> openTar(file, kind).use { tin ->
                var e: TarArchiveEntry? = tin.nextEntry
                while (e != null) {
                    val p = norm(e.name)
                    if (p != null && accept(p)) {
                        if (e.isDirectory) write(p, true, null) else write(p, false, tin)
                    }
                    e = tin.nextEntry
                }
            }
            "gz", "bz2", "xz" -> {
                val raw = BufferedInputStream(FileInputStream(file), 1 shl 16)
                val s = when (kind) {
                    "gz" -> GzipCompressorInputStream(raw, true)
                    "bz2" -> BZip2CompressorInputStream(raw, true)
                    else -> XZCompressorInputStream(raw, true)
                }
                s.use { write(baseName(file.name), false, it) }
            }
        }
    }

    // ---------------------------------------------------------------- CREATE

    fun create(
        format: ArchiveFormat, sources: List<File>, out: File, level: Int, method: SevenZMethod,
        onProgress: (done: Int, total: Int, name: String) -> Unit,
    ) {
        // собираем список (относительный путь -> файл)
        val items = ArrayList<Pair<String, File>>()
        fun walk(f: File, rel: String) {
            items += rel to f
            if (f.isDirectory) f.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { walk(it, "$rel/${it.name}") }
        }
        sources.forEach { walk(it, it.name) }
        val total = items.count { !it.second.isDirectory }.coerceAtLeast(1)
        var done = 0
        val lvl = level.coerceIn(0, 9)

        when (format) {
            ArchiveFormat.ZIP -> ZipArchiveOutputStream(out).use { z ->
                z.setLevel(lvl)
                z.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
                for ((rel, f) in items) {
                    if (f.isDirectory) {
                        z.putArchiveEntry(ZipArchiveEntry("$rel/").also { it.time = f.lastModified() }); z.closeArchiveEntry()
                    } else {
                        onProgress(done, total, f.name)
                        z.putArchiveEntry(ZipArchiveEntry(f, rel))
                        FileInputStream(f).use { it.copyTo(z, 1 shl 16) }
                        z.closeArchiveEntry()
                        done++; onProgress(done, total, f.name)
                    }
                }
            }
            ArchiveFormat.SEVEN_Z -> SevenZOutputFile(out).use { sz ->
                sz.setContentCompression(
                    when (method) {
                        SevenZMethod.LZMA2 -> CcSevenZMethod.LZMA2
                        SevenZMethod.LZMA -> CcSevenZMethod.LZMA
                        SevenZMethod.BZIP2 -> CcSevenZMethod.BZIP2
                        SevenZMethod.DEFLATE -> CcSevenZMethod.DEFLATE
                        SevenZMethod.COPY -> CcSevenZMethod.COPY
                    }
                )
                for ((rel, f) in items) {
                    val e = sz.createArchiveEntry(f, rel)
                    sz.putArchiveEntry(e)
                    if (!f.isDirectory) {
                        onProgress(done, total, f.name)
                        FileInputStream(f).use { ins ->
                            val buf = ByteArray(1 shl 16)
                            var n: Int
                            while (ins.read(buf).also { n = it } > 0) sz.write(buf, 0, n)
                        }
                        done++; onProgress(done, total, f.name)
                    }
                    sz.closeArchiveEntry()
                }
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2 -> {
                val raw: OutputStream = BufferedOutputStream(FileOutputStream(out), 1 shl 16)
                val comp: OutputStream = when (format) {
                    ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(raw, GzipParameters().apply { compressionLevel = lvl })
                    ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(raw, lvl)
                    ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(raw, lvl.coerceIn(1, 9))
                    else -> raw
                }
                TarArchiveOutputStream(comp).use { t ->
                    t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for ((rel, f) in items) {
                        val e: TarArchiveEntry = t.createArchiveEntry(f, if (f.isDirectory) "$rel/" else rel)
                        t.putArchiveEntry(e)
                        if (!f.isDirectory) {
                            onProgress(done, total, f.name)
                            FileInputStream(f).use { it.copyTo(t, 1 shl 16) }
                            done++; onProgress(done, total, f.name)
                        }
                        t.closeArchiveEntry()
                    }
                }
            }
        }
    }
}
