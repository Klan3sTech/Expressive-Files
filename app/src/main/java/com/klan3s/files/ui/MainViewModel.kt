package com.klan3s.files.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klan3s.files.data.ArchiveEntryInfo
import com.klan3s.files.data.ArchiveFormat
import com.klan3s.files.data.ArchiveService
import com.klan3s.files.data.FileOps
import com.klan3s.files.data.FsEntry
import com.klan3s.files.data.RootShell
import com.klan3s.files.data.SafOps
import com.klan3s.files.data.Settings
import com.klan3s.files.data.SevenZMethod
import com.klan3s.files.data.SortMode
import com.klan3s.files.data.TaskProgress
import com.klan3s.files.data.Volume
import com.klan3s.files.data.Volumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, BROWSER, SETTINGS }

data class ArchiveView(
    val file: File,
    val entries: List<ArchiveEntryInfo>,
    val dir: String = "",
    val password: String? = null,
    val displayName: String = file.name,
    val targetDir: File = file.parentFile ?: FileOps.root,
    val targetSaf: Uri? = null,
) {
    val visible: List<ArchiveEntryInfo>
        get() = entries.filter { it.parent == dir }
            .sortedWith(compareByDescending<ArchiveEntryInfo> { it.isDir }.thenBy { it.name.lowercase() })
    val totalSize: Long get() = entries.filter { !it.isDir }.sumOf { it.size.coerceAtLeast(0) }
    val fileCount: Int get() = entries.count { !it.isDir }
}

data class Clipboard(val items: List<FsEntry>, val move: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val saf = SafOps(app)

    var screen by mutableStateOf(Screen.HOME); private set
    var volumes by mutableStateOf<List<Volume>>(emptyList()); private set
    var currentVolume by mutableStateOf<Volume?>(null); private set
    var rootAvailable by mutableStateOf<Boolean?>(null); private set

    /** Запрос SAF-доступа к тому — UI должен запустить системный диалог. */
    var safRequest by mutableStateOf<Volume?>(null); private set

    var currentDir by mutableStateOf(FileOps.root); private set
    /** Текущий SAF-каталог (если не null — проводник работает через DocumentsContract). */
    var safDir by mutableStateOf<Uri?>(null); private set
    var entries by mutableStateOf<List<FsEntry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var query by mutableStateOf("")

    var archive by mutableStateOf<ArchiveView?>(null); private set
    var selection by mutableStateOf<Set<String>>(emptySet()); private set
    var clipboard by mutableStateOf<Clipboard?>(null); private set
    var task by mutableStateOf<TaskProgress?>(null); private set
    var message by mutableStateOf<String?>(null)
    var passwordRequest by mutableStateOf<File?>(null); private set

    val selectionMode get() = selection.isNotEmpty()
    /** Root включён в настройках и не было явного отказа su (на старте — оптимистично, проверка идёт в фоне). */
    val useRoot get() = settings.useRoot && rootAvailable != false
    val isSaf get() = safDir != null
    private val cacheDir get() = File(getApplication<Application>().cacheDir, "work").also { it.mkdirs() }

    init {
        if (settings.useRoot) {
            // Восстанавливаем root-сессию после холодного старта: тихо проверяем su в фоне
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { RootShell.check(force = true) }
                rootAvailable = ok
                if (!ok) message = "Root-доступ не подтверждён (su отклонил запрос) — режим root временно отключён"
                refreshVolumes()
                if (screen == Screen.BROWSER) refresh()
            }
        }
        refreshVolumes()
    }

    // ---------------- volumes / root / saf

    fun refreshVolumes() {
        viewModelScope.launch {
            volumes = withContext(Dispatchers.IO) { Volumes.list(getApplication(), useRoot) }
        }
    }

    fun setRootEnabled(enabled: Boolean) {
        if (!enabled) { settings.updateUseRoot(false); rootAvailable = null; refreshVolumes(); return }
        task = TaskProgress("Запрос root-доступа", "Подтвердите запрос в Magisk / KernelSU")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RootShell.check(force = true) }
            task = null
            rootAvailable = ok
            settings.updateUseRoot(ok)
            message = if (ok) "Root-доступ получен" else "Root недоступен: su не найден или запрос отклонён"
            refreshVolumes()
        }
    }

    fun openVolume(v: Volume) {
        currentVolume = v
        if (v.needsSaf && !useRoot) {
            if (v.treeUri != null) { screen = Screen.BROWSER; openSaf(v.treeUri) }
            else safRequest = v
            return
        }
        screen = Screen.BROWSER; open(v.path)
    }

    /** Результат системного диалога выбора папки. */
    fun onSafTreeResult(uri: Uri?) {
        val v = safRequest ?: return
        safRequest = null
        if (uri == null) { message = "Доступ к накопителю не предоставлен"; return }
        Volumes.saveTree(getApplication(), v, uri)
        currentVolume = v.copy(treeUri = uri)
        screen = Screen.BROWSER
        openSaf(uri)
        refreshVolumes()
    }

    fun dismissSafRequest() { safRequest = null }
    fun safTreeIntent(v: Volume) = Volumes.treeIntent(getApplication(), v)

    fun openSettings() { screen = Screen.SETTINGS }
    fun goHome() { archive = null; selection = emptySet(); safDir = null; screen = Screen.HOME; refreshVolumes() }

    val volumeRoot: File get() = currentVolume?.path ?: FileOps.root

    /** Хлебные крошки для SAF-тома. */
    fun safCrumbs(): List<Pair<String, Uri>> {
        val sd = safDir ?: return emptyList()
        val tree = currentVolume?.treeUri ?: sd
        val treeId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val volId = treeId.substringBefore(':'); val rootPath = treeId.substringAfter(':', "")
        val curId = runCatching { android.provider.DocumentsContract.getDocumentId(sd) }.getOrDefault(treeId)
        val rel = curId.substringAfter(':', "").removePrefix(rootPath).split('/').filter { it.isNotEmpty() }
        val first = (currentVolume?.label ?: "USB") to android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        return listOf(first) + rel.indices.map { i ->
            val id = volId + ":" + (listOf(rootPath).filter { it.isNotEmpty() } + rel.take(i + 1)).joinToString("/")
            rel[i] to android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id)
        }
    }

    /** Путь внутри SAF-тома для заголовка/крошек. */
    val safPathParts: List<String> get() = safDir?.let { saf.displayPath(it).split('/').filter { p -> p.isNotEmpty() } } ?: emptyList()

    // ---------------- browser

    fun refresh() {
        val dir = currentDir
        val sd = safDir
        loading = true
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) {
                if (sd != null) sortEntries(saf.list(sd, settings.showHidden))
                else FileOps.list(dir, settings.showHidden, settings.sort, settings.foldersFirst, useRoot)
            }
            entries = l; loading = false
            if (sd == null && l.isEmpty() && dir.listFiles() == null && !dir.canRead()) {
                message = if (!settings.useRoot) "Нет доступа. Включите root в настройках"
                else if (rootAvailable == false) "Нет доступа: su отклонил запрос. Проверьте разрешение в Magisk/KernelSU"
                else "Не удалось прочитать папку через root"
            }
        }
    }

    private fun sortEntries(list: List<FsEntry>): List<FsEntry> {
        val cmp: Comparator<FsEntry> = when (settings.sort) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareByDescending<FsEntry> { it.size }.thenBy { it.name.lowercase() }
            SortMode.DATE -> compareByDescending<FsEntry> { it.modified }.thenBy { it.name.lowercase() }
            SortMode.TYPE -> compareBy<FsEntry> { it.extension }.thenBy { it.name.lowercase() }
        }
        return if (settings.foldersFirst) list.sortedWith(compareByDescending<FsEntry> { it.isDir }.then(cmp)) else list.sortedWith(cmp)
    }

    fun open(dir: File) { safDir = null; currentDir = dir; selection = emptySet(); query = ""; refresh() }
    fun openSaf(uri: Uri) { safDir = uri; selection = emptySet(); query = ""; refresh() }
    fun openEntry(e: FsEntry) { if (e.isSaf) openSaf(e.docUri!!) else open(e.file) }

    fun goUp(): Boolean {
        if (screen == Screen.SETTINGS) { screen = if (currentVolume == null) Screen.HOME else Screen.BROWSER; return true }
        archive?.let { a ->
            if (a.dir.isNotEmpty()) { archive = a.copy(dir = a.dir.substringBeforeLast('/', "")); selection = emptySet(); return true }
            archive = null; selection = emptySet(); return true
        }
        if (selection.isNotEmpty()) { selection = emptySet(); return true }
        if (screen == Screen.HOME) return false
        safDir?.let { sd ->
            val parent = saf.parentOf(sd)
            if (parent == null) goHome() else openSaf(parent)
            return true
        }
        val parent = currentDir.parentFile
        if (currentDir.absolutePath == volumeRoot.absolutePath || parent == null) { goHome(); return true }
        open(parent); return true
    }

    fun changeSort(s: SortMode) { settings.updateSort(s); refresh() }
    fun toggleHidden() { settings.updateShowHidden(!settings.showHidden); refresh() }

    fun toggleSelect(key: String) { selection = if (key in selection) selection - key else selection + key }
    fun clearSelection() { selection = emptySet() }
    fun selectAll() {
        selection = archive?.visible?.map { it.path }?.toSet() ?: filteredEntries().map { it.path }.toSet()
    }
    private fun selectedEntries(): List<FsEntry> = entries.filter { it.path in selection }

    fun filteredEntries(): List<FsEntry> =
        if (query.isBlank()) entries else entries.filter { it.name.contains(query, ignoreCase = true) }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        val sd = safDir
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (sd != null) saf.mkdir(sd, name.trim()) else FileOps.mkdir(File(currentDir, name.trim()), useRoot)
            }
            if (ok) refresh() else message = "Не удалось создать папку"
        }
    }

    fun rename(e: FsEntry, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (e.isSaf) saf.rename(e.docUri!!, newName.trim())
                else { val t = File(e.file.parentFile, newName.trim()); if (t.exists()) false else FileOps.rename(e.file, t, useRoot) }
            }
            if (ok) { selection = emptySet(); refresh() } else message = "Не удалось переименовать"
        }
    }

    fun deleteSelected() {
        val items = selectedEntries()
        val root = useRoot
        runSimpleTask("Удаление", items.size) { report ->
            items.forEachIndexed { i, e ->
                report(i, e.name)
                val ok = if (e.isSaf) saf.delete(e.docUri!!) else FileOps.delete(e.file, root)
                if (!ok) error("Не удалось удалить ${e.name}")
            }
        }
    }

    fun copySelected(move: Boolean) {
        clipboard = Clipboard(selectedEntries(), move)
        selection = emptySet()
        message = if (move) "Выберите папку и нажмите «Вставить»" else "Скопировано в буфер"
    }

    fun paste() {
        val cb = clipboard ?: return
        val destDir = currentDir
        val destSaf = safDir
        val root = useRoot
        clipboard = null
        val total = cb.items.sumOf { if (it.isSaf) 1 else FileOps.countFiles(it.file) }
        runSimpleTask(if (cb.move) "Перемещение" else "Копирование", total) { report ->
            var n = 0
            val stage = File(cacheDir, "paste_${System.currentTimeMillis()}").also { it.mkdirs() }
            try {
                for (e in cb.items) {
                    when {
                        // local -> local
                        !e.isSaf && destSaf == null -> FileOps.copyInto(e.file, destDir, cb.move, root) { report(n++, it) }
                        // local -> SAF
                        !e.isSaf && destSaf != null -> { saf.uploadTree(e.file, destSaf) { report(n++, it) }; if (cb.move) FileOps.delete(e.file, root) }
                        // SAF -> local
                        e.isSaf && destSaf == null -> {
                            saf.downloadTree(e.docUri!!, e.name, e.isDir, stage) { report(n++, it) }
                            FileOps.copyInto(File(stage, e.name), destDir, true, root) { }
                            if (cb.move) saf.delete(e.docUri)
                        }
                        // SAF -> SAF
                        else -> {
                            saf.downloadTree(e.docUri!!, e.name, e.isDir, stage) { report(n++, it) }
                            saf.uploadTree(File(stage, e.name), destSaf!!) { }
                            if (cb.move) saf.delete(e.docUri)
                        }
                    }
                }
            } finally { stage.deleteRecursively() }
        }
    }

    // ---------------- archives

    fun openArchive(e: FsEntry, password: String? = null) {
        if (e.isSaf) {
            task = TaskProgress("Открытие архива", e.name)
            viewModelScope.launch {
                val tmp = File(cacheDir, "saf_${e.name}")
                val ok = withContext(Dispatchers.IO) { saf.copyToCache(e.docUri!!, tmp) }
                if (!ok) { task = null; message = "Не удалось прочитать файл"; return@launch }
                openArchiveFile(tmp, password, displayName = e.name, targetSaf = safDir)
            }
        } else openArchiveFile(e.file, password)
    }

    fun openArchive(file: File, password: String? = null) = openArchiveFile(file, password)

    private fun openArchiveFile(file: File, password: String?, displayName: String = file.name, targetSaf: Uri? = null) {
        task = TaskProgress("Открытие архива", displayName)
        val root = useRoot
        viewModelScope.launch {
            try {
                val (readable, list) = withContext(Dispatchers.IO) {
                    var src = file
                    if (!file.canRead()) {
                        if (!root) error("Нет доступа к файлу (нужен root)")
                        val tmp = File(cacheDir, "root_${file.name}")
                        if (!RootShell.copyToCache(file, tmp)) error("Не удалось прочитать файл через root")
                        src = tmp
                    }
                    src to ArchiveService.list(src, password)
                }
                archive = ArchiveView(readable, list, password = password, displayName = displayName,
                    targetDir = if (targetSaf != null) cacheDir else (file.parentFile ?: currentDir), targetSaf = targetSaf)
                selection = emptySet(); passwordRequest = null
                task = null
            } catch (e: Throwable) {
                task = null
                if (isPasswordError(e)) passwordRequest = file
                else message = "Не удалось открыть: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "archive.zip"
                    val tmp = File(ctx.cacheDir, "open/${System.currentTimeMillis()}_$name").also { it.parentFile?.mkdirs() }
                    ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    tmp
                }.getOrNull()
            }
            if (f != null && ArchiveService.isArchive(f.name)) { if (screen == Screen.HOME) { screen = Screen.BROWSER; open(FileOps.root) }; openArchiveFile(f, null) }
            else message = "Не удалось открыть файл"
        }
    }

    private fun isPasswordError(e: Throwable): Boolean {
        val m = (e.message ?: "").lowercase()
        return e is org.apache.commons.compress.PasswordRequiredException || m.contains("password") || m.contains("encrypted") || m.contains("crypt")
    }

    fun dismissPassword() { passwordRequest = null }
    fun enterArchiveDir(path: String) { archive = archive?.copy(dir = path); selection = emptySet() }

    fun extract(onlySelected: Boolean) {
        val a = archive ?: return
        val sel = if (onlySelected) selection else emptySet()
        val root = useRoot
        val baseName = ArchiveService.baseName(a.displayName)
        val filter: ((String) -> Boolean)? = if (onlySelected) { p -> sel.any { s -> p == s || p.startsWith("$s/") } } else null
        val strip = if (onlySelected) a.dir else ""
        selection = emptySet()

        // --- в SAF-том (флешка)
        a.targetSaf?.let { tsaf ->
            val stage = File(cacheDir, "extract_${System.currentTimeMillis()}")
            runTask("Распаковка", 1, after = { openSaf(tsaf) }) { report ->
                try {
                    ArchiveService.extract(a.file, stage, filter, strip, a.password) { d, t, n -> report(d, n, t) }
                    report(0, "Запись на накопитель…", 0)
                    val dirUri = android.provider.DocumentsContract.createDocument(getApplication<Application>().contentResolver, tsaf,
                        android.provider.DocumentsContract.Document.MIME_TYPE_DIR, baseName) ?: error("Не удалось создать папку на накопителе")
                    saf.uploadContents(stage, dirUri) { n -> report(0, n, 0) }
                } finally { stage.deleteRecursively() }
            }
            return
        }

        val finalDest = FileOps.uniqueChild(a.targetDir, baseName)
        val viaRoot = FileOps.needsRootWrite(a.targetDir)
        if (viaRoot && !root) { message = "Папка недоступна для записи (нужен root)"; return }
        val dest = if (viaRoot) File(cacheDir, "extract_${System.currentTimeMillis()}") else finalDest
        runTask("Распаковка", 1, after = { open(finalDest) }) { report ->
            ArchiveService.extract(a.file, dest, filter, strip, a.password) { d, t, n -> report(d, n, t) }
            if (viaRoot) {
                report(0, "Копирование через root…", 0)
                if (!RootShell.copyContents(dest, finalDest)) error("Не удалось записать через root")
                dest.deleteRecursively()
            }
        }
    }

    fun compress(format: ArchiveFormat, name: String, level: Int, method: SevenZMethod) {
        val items = selectedEntries()
        if (items.isEmpty()) return
        val root = useRoot
        val dir = currentDir
        val destSaf = safDir
        val outName = name.trim().ifBlank { "archive" } + format.ext
        selection = emptySet()

        val viaRoot = destSaf == null && (FileOps.needsRootWrite(dir) || items.any { FileOps.needsRoot(it.file) })
        if (viaRoot && !root) { message = "Нужен root для этой папки"; return }
        val needStage = viaRoot || destSaf != null || items.any { it.isSaf }

        runTask("Создание ${format.label}", 1) { report ->
            val stage = if (needStage) File(cacheDir, "stage_${System.currentTimeMillis()}").also { it.mkdirs() } else null
            var out = if (stage != null) File(stage, outName) else FileOps.uniqueChild(dir, outName)
            try {
                val src: List<File> = items.map { e ->
                    when {
                        e.isSaf -> { report(0, "Чтение ${e.name}…", 0); saf.downloadTree(e.docUri!!, e.name, e.isDir, stage!!) { }; File(stage, e.name) }
                        FileOps.needsRoot(e.file) -> { RootShell.copyTreeInto(e.file, stage!!, false); File(stage, e.name) }
                        else -> e.file
                    }
                }
                ArchiveService.create(format, src, out, level, method) { d, t, n -> report(d, n, t) }
                when {
                    destSaf != null -> { report(0, "Запись на накопитель…", 0); saf.uploadTree(out, destSaf) { } }
                    viaRoot -> if (!RootShell.copyTreeInto(out, dir, true)) error("Не удалось записать через root")
                    stage != null -> out.copyTo(FileOps.uniqueChild(dir, outName), overwrite = false)
                }
            } catch (e: Throwable) { out.delete(); throw e } finally { stage?.deleteRecursively() }
        }
    }

    // ---------------- task runner

    private fun runTask(
        title: String, total: Int, after: (() -> Unit)? = null,
        block: suspend ((done: Int, name: String, total: Int) -> Unit) -> Unit,
    ) {
        task = TaskProgress(title)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    block { d, n, t ->
                        val tt = if (t > 0) t else total
                        task = TaskProgress(title, n, if (tt > 0) d.toFloat() / tt else null)
                    }
                }
                task = TaskProgress(title, "Готово", 1f, done = true)
                kotlinx.coroutines.delay(600)
                task = null
                if (after != null) after() else refresh()
            } catch (e: Throwable) {
                task = TaskProgress(title, "", null, done = true, error = e.message ?: e.javaClass.simpleName)
                refresh()
            }
        }
    }

    private fun runSimpleTask(title: String, total: Int, block: suspend ((Int, String) -> Unit) -> Unit) =
        runTask(title, total, null) { r -> block { d, n -> r(d, n, total) } }

    fun dismissTask() { task = null }
}
