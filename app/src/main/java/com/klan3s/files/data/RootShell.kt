package com.klan3s.files.data

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Работа с файловой системой через `su`. Используется только там, где обычный
 * доступ невозможен (/, /data, /system и т.д.).
 */
object RootShell {

    @Volatile var available: Boolean? = null; private set

    /** Проверяет наличие root (вызывает запрос у Magisk/KernelSU/SuperSU). */
    fun check(force: Boolean = false): Boolean {
        if (!force) available?.let { return it }
        val ok = runCatching { run("id").getOrThrow().contains("uid=0") }.getOrDefault(false)
        available = ok
        return ok
    }

    fun run(cmd: String, timeoutSec: Long = 60): Result<String> = runCatching {
        val proc = ProcessBuilder("su", "-c", cmd).redirectErrorStream(false).start()
        val out = StringBuilder()
        val err = StringBuilder()
        val t1 = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
        val t2 = Thread { proc.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
        t1.start(); t2.start()
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) { proc.destroy(); error("Команда не завершилась: $cmd") }
        t1.join(); t2.join()
        if (proc.exitValue() != 0) error(err.toString().trim().ifEmpty { "exit ${proc.exitValue()}" })
        out.toString()
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private val lsLine = Regex("""^([bcdlps-])[rwxsStT-]{9}[+.]?\s+\d+\s+\S+\s+\S+\s+(\d+)\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+(.+)$""")
    private val lsLineNoSize = Regex("""^([bcdlps-])[rwxsStT-]{9}[+.]?\s+\d+\s+\S+\s+\S+\s+(?:\d+,\s*\d+\s+)?(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+(.+)$""")
    private val dateFmt get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

    /** Список каталога через `ls -la`. */
    fun list(dir: File, showHidden: Boolean): List<FsEntry> {
        val out = run("ls -la ${q(dir.absolutePath)}").getOrElse { return emptyList() }
        val res = ArrayList<FsEntry>()
        for (line in out.lineSequence()) {
            var type: Char; var size = 0L; var date: String; var name: String
            val m = lsLine.find(line)
            if (m != null) { type = m.groupValues[1][0]; size = m.groupValues[2].toLong(); date = m.groupValues[3]; name = m.groupValues[4] }
            else { val m2 = lsLineNoSize.find(line) ?: continue; type = m2.groupValues[1][0]; date = m2.groupValues[2]; name = m2.groupValues[3] }
            var isDir = type == 'd'
            if (type == 'l') {
                val target = name.substringAfter(" -> ", "")
                name = name.substringBefore(" -> ")
                if (target.isNotEmpty()) {
                    val tf = if (target.startsWith("/")) File(target) else File(dir, target)
                    isDir = tf.isDirectory || run("test -d ${q(tf.absolutePath)} && echo d").getOrNull()?.trim() == "d"
                }
            }
            if (name == "." || name == "..") continue
            if (!showHidden && name.startsWith(".")) continue
            val modified = runCatching { dateFmt.parse(date.take(16))?.time ?: 0L }.getOrDefault(0L)
            res += FsEntry(File(dir, name), name, isDir, if (isDir) 0 else size, modified, 0, viaRoot = true)
        }
        return res
    }

    fun copyToCache(src: File, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        return run("cp -f ${q(src.absolutePath)} ${q(dest.absolutePath)} && chmod 644 ${q(dest.absolutePath)}").isSuccess
    }

    fun copyTreeInto(src: File, destDir: File, move: Boolean): Boolean =
        run("mkdir -p ${q(destDir.absolutePath)} && ${if (move) "mv -f" else "cp -rf"} ${q(src.absolutePath)} ${q(destDir.absolutePath)}/").isSuccess

    /** Копирует содержимое каталога (извлечённый архив) в целевой каталог. */
    fun copyContents(fromDir: File, toDir: File): Boolean =
        run("mkdir -p ${q(toDir.absolutePath)} && cp -rf ${q(fromDir.absolutePath)}/. ${q(toDir.absolutePath)}/").isSuccess

    fun delete(f: File) = run("rm -rf ${q(f.absolutePath)}").isSuccess
    fun mkdir(f: File) = run("mkdir -p ${q(f.absolutePath)}").isSuccess
    fun rename(a: File, b: File) = run("mv ${q(a.absolutePath)} ${q(b.absolutePath)}").isSuccess
    fun exists(f: File) = run("test -e ${q(f.absolutePath)} && echo y").getOrNull()?.trim() == "y"
}
