package com.klan3s.files.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.klan3s.files.data.ArchiveFormat
import com.klan3s.files.data.FileOps
import com.klan3s.files.data.SevenZMethod
import com.klan3s.files.data.SortMode
import com.klan3s.files.data.ViewMode
import com.klan3s.files.data.VolumeKind
import com.klan3s.files.data.formatSize
import java.io.File

// ------------------------------------------------------------------ root

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilesApp(vm: MainViewModel) {
    var granted by remember { mutableStateOf(hasStoragePermission()) }
    if (!granted) {
        PermissionScreen { granted = hasStoragePermission(); if (granted) vm.refreshVolumes() }
        return
    }

    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    BackHandler(enabled = true) { if (!vm.goUp()) (ctx as? android.app.Activity)?.finish() }
    val openDocHome = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::openUri) }

    AnimatedContent(targetState = vm.screen, transitionSpec = { (fadeIn() + slideInVertically { it / 16 }).togetherWith(fadeOut()) }, label = "screen") { sc ->
        when (sc) {
            Screen.HOME -> HomeScreen(vm, snack) { openDocHome.launch(arrayOf("*/*")) }
            Screen.SETTINGS -> SettingsScreen(vm, snack)
            Screen.BROWSER -> BrowserScreen(vm, snack)
        }
    }
    vm.task?.let { TaskDialog(it, onDismiss = vm::dismissTask) }
    vm.safRequest?.let { v -> SafRequestDialog(vm, v) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowserScreen(vm: MainViewModel, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val settings = vm.settings
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val archive = vm.archive
    val selectionMode = vm.selectionMode

    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<com.klan3s.files.data.FsEntry?>(null) }
    var showCompress by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::openUri) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scroll,
                title = {
                    Text(
                        when {
                            selectionMode -> "Выбрано: ${vm.selection.size}"
                            archive != null -> archive.dir.substringAfterLast('/').ifEmpty { archive.displayName }
                            vm.isSaf -> vm.safCrumbs().lastOrNull()?.first ?: (vm.currentVolume?.label ?: "USB")
                            vm.currentDir.absolutePath == vm.volumeRoot.absolutePath -> vm.currentVolume?.label ?: "Память устройства"
                            vm.currentDir.absolutePath == "/" -> "/"
                            else -> vm.currentDir.name
                        }, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                subtitle = {
                    Text(
                        when {
                            selectionMode -> "Нажмите для действий"
                            archive != null -> "${archive.fileCount} файлов • ${formatSize(archive.totalSize)} • ${formatSize(archive.file.length())} в архиве"
                            else -> "${vm.entries.count { it.isDir }} папок, ${vm.entries.count { !it.isDir }} файлов" + (if (vm.entries.any { it.viaRoot }) " • root" else "") + (if (vm.isSaf) " • SAF" else "")
                        }, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { vm.goUp() }) {
                        Icon(if (selectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Rounded.SelectAll, "Выбрать всё") }
                    } else {
                        if (archive == null) {
                            IconButton(onClick = { showSearch = !showSearch; if (!showSearch) vm.query = "" }) { Icon(Icons.Rounded.Search, "Поиск") }
                            IconButton(onClick = { openDoc.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FileOpen, "Открыть архив") }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Сортировка") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text((if (settings.sort == s) "✓  " else "    ") + s.label) },
                                        onClick = { vm.changeSort(s); sortMenu = false })
                                }
                                HorizontalDivider()
                                ViewMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text((if (settings.viewMode == m) "✓  " else "    ") + m.label) },
                                        onClick = { settings.updateViewMode(m); sortMenu = false })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (settings.showHidden) "Скрыть скрытые" else "Показать скрытые") },
                                    leadingIcon = { Icon(if (settings.showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) },
                                    onClick = { vm.toggleHidden(); sortMenu = false })
                                DropdownMenuItem(
                                    text = { Text("Настройки") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = { vm.openSettings(); sortMenu = false })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // --- FAB / меню (без выделения)
            AnimatedVisibility(!selectionMode && archive == null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButtonMenu(
                    expanded = fabExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabExpanded,
                            onCheckedChange = { fabExpanded = !fabExpanded },
                            modifier = Modifier.animateFloatingActionButton(visible = true, alignment = Alignment.BottomEnd),
                        ) {
                            val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                            Icon(rememberVectorPainter(icon), null, modifier = Modifier.animateIcon({ checkedProgress }))
                        }
                    },
                ) {
                    FloatingActionButtonMenuItem(onClick = { fabExpanded = false; showNewFolder = true },
                        text = { Text("Новая папка") }, icon = { Icon(Icons.Rounded.CreateNewFolder, null) })
                    FloatingActionButtonMenuItem(onClick = { fabExpanded = false; openDoc.launch(arrayOf("*/*")) },
                        text = { Text("Открыть архив…") }, icon = { Icon(Icons.Rounded.FolderZip, null) })
                    if (vm.clipboard != null) {
                        FloatingActionButtonMenuItem(onClick = { fabExpanded = false; vm.paste() },
                            text = { Text("Вставить (${vm.clipboard!!.items.size})") }, icon = { Icon(Icons.Rounded.ContentPaste, null) })
                    }
                }
            }
            AnimatedVisibility(!selectionMode && archive != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { vm.extract(onlySelected = false) },
                    icon = { Icon(Icons.Rounded.Unarchive, null) },
                    text = { Text("Распаковать всё") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                Breadcrumbs(vm)
                AnimatedVisibility(showSearch && archive == null) {
                    OutlinedTextField(
                        value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                        placeholder = { Text("Поиск в папке") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                AnimatedContent(
                    targetState = archive?.file?.path + "/" + (archive?.dir ?: "") + "|" + vm.currentDir.path,
                    transitionSpec = { (fadeIn() + slideInVertically { it / 12 }).togetherWith(fadeOut()) }, label = "list",
                ) { _ ->
                    if (archive != null) ArchiveList(vm) else FileList(vm, onOpenFile = { e -> openExternal(ctx, e) })
                }
            }

            // --- плавающая панель действий при выделении
            AnimatedVisibility(
                selectionMode,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            ) {
                if (archive == null) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                        floatingActionButton = {
                            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { showCompress = true }) {
                                Icon(Icons.Rounded.Archive, "Сжать")
                            }
                        },
                    ) {
                        IconButton(onClick = { vm.copySelected(move = false) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                        IconButton(onClick = { vm.copySelected(move = true) }) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, "Переместить") }
                        IconButton(enabled = vm.selection.size == 1, onClick = { renameTarget = vm.entries.firstOrNull { it.path == vm.selection.first() } }) {
                            Icon(Icons.Rounded.DriveFileRenameOutline, "Переименовать")
                        }
                        IconButton(onClick = { if (settings.confirmDelete) confirmDelete = true else vm.deleteSelected() }) { Icon(Icons.Rounded.Delete, "Удалить") }
                    }
                } else {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                        floatingActionButton = {
                            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { vm.extract(onlySelected = true) }) {
                                Icon(Icons.Rounded.Unarchive, "Распаковать выбранное")
                            }
                        },
                    ) {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Rounded.SelectAll, "Выбрать всё") }
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, "Снять выделение") }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- dialogs
    if (showNewFolder) NameDialog("Новая папка", "", "Создать", { showNewFolder = false }) { vm.createFolder(it); showNewFolder = false }
    renameTarget?.let { f -> NameDialog("Переименовать", f.name, "Сохранить", { renameTarget = null }) { vm.rename(f, it); renameTarget = null } }
    vm.passwordRequest?.let { f -> PasswordDialog(f.name, onDismiss = vm::dismissPassword) { vm.openArchive(f, it) } }
    if (showCompress) CompressSheet(vm.selection.size, defaultName = vm.selection.firstOrNull()?.let { File(it).nameWithoutExtension } ?: "archive",
        defaultFormat = settings.defaultFormat, defaultLevel = settings.defaultLevel,
        onDismiss = { showCompress = false }) { fmt, name, lvl, m -> showCompress = false; vm.compress(fmt, name, lvl, m) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Удалить ${vm.selection.size} элем.?") },
        text = { Text("Это действие нельзя отменить.") },
        confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteSelected() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

// ------------------------------------------------------------------ lists

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileList(vm: MainViewModel, onOpenFile: (com.klan3s.files.data.FsEntry) -> Unit) {
    val s = vm.settings
    val list = vm.filteredEntries()
    if (vm.loading && list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    if (list.isEmpty()) { EmptyState(if (vm.query.isNotBlank()) "Ничего не найдено" else if (!vm.currentDir.canRead() && !vm.useRoot) "Нет доступа — нужен root" else "Папка пуста"); return }

    fun subtitle(e: com.klan3s.files.data.FsEntry): String = if (!s.showDetails) "" else
        if (e.isDir) (if (e.viaRoot) formatDate(e.modified) else "${e.childCount} элем. • ${formatDate(e.modified)}") else "${formatSize(e.size)} • ${formatDate(e.modified)}"
    fun click(e: com.klan3s.files.data.FsEntry) {
        when {
            vm.selectionMode -> vm.toggleSelect(e.path)
            e.isDir -> vm.openEntry(e)
            e.isArchive -> vm.openArchive(e)
            else -> onOpenFile(e)
        }
    }

    if (s.viewMode == ViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.gridColumns), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.path }) { e ->
                val kind = kindFor(e.name, e.isDir)
                val thumb = rememberThumbnail(if (kind == FileKind.IMAGE && !e.viaRoot && !e.isSaf) e.file else null, s.showThumbnails)
                FileTile(e.name, kind, if (e.isDir) "" else formatSize(e.size), e.path in vm.selection, s.iconStyle, thumb,
                    onClick = { click(e) }, onLongClick = { vm.toggleSelect(e.path) })
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.path }) { e ->
                val kind = kindFor(e.name, e.isDir)
                val thumb = rememberThumbnail(if (kind == FileKind.IMAGE && !e.viaRoot && !e.isSaf) e.file else null, s.showThumbnails)
                FileRow(
                    name = e.name, kind = kind, subtitle = subtitle(e),
                    selected = e.path in vm.selection, selectionMode = vm.selectionMode,
                    iconStyle = s.iconStyle, compact = s.viewMode == ViewMode.COMPACT, thumb = thumb,
                    badge = if (e.viaRoot) "root" else null,
                    onClick = { click(e) }, onLongClick = { vm.toggleSelect(e.path) },
                )
            }
        }
    }
}

@Composable
private fun ArchiveList(vm: MainViewModel) {
    val a = vm.archive ?: return
    val list = a.visible
    if (list.isEmpty()) { EmptyState("Пусто"); return }
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
        items(list, key = { it.path }) { e ->
            val selected = e.path in vm.selection
            val sub = buildString {
                if (e.isDir) append("${a.entries.count { it.parent == e.path }} элем.")
                else {
                    append(formatSize(e.size))
                    if (e.compressedSize >= 0 && e.size > 0) append(" → ${formatSize(e.compressedSize)} (${(100 - e.compressedSize * 100 / e.size).coerceIn(0, 100)}%)")
                }
                if (e.modified > 0) append(" • ${formatDate(e.modified)}")
            }
            FileRow(
                name = e.name, kind = kindFor(e.name, e.isDir), subtitle = sub,
                selected = selected, selectionMode = vm.selectionMode,
                iconStyle = vm.settings.iconStyle, compact = vm.settings.viewMode == ViewMode.COMPACT, thumb = null,
                onClick = { if (vm.selectionMode) vm.toggleSelect(e.path) else if (e.isDir) vm.enterArchiveDir(e.path) else vm.toggleSelect(e.path) },
                onLongClick = { vm.toggleSelect(e.path) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(120.dp).clip(MaterialShapes.SoftBurst.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FolderOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Breadcrumbs(vm: MainViewModel) {
    val a = vm.archive
    val crumbs: List<Pair<String, () -> Unit>> = if (a == null && vm.isSaf) {
        vm.safCrumbs().map { (label, uri) -> label to { vm.openSaf(uri) } }
    } else if (a != null) {
        val parts = a.dir.split('/').filter { it.isNotEmpty() }
        listOf(a.displayName to { vm.enterArchiveDir("") }) + parts.indices.map { i ->
            parts[i] to { vm.enterArchiveDir(parts.take(i + 1).joinToString("/")) }
        }
    } else {
        val root = vm.volumeRoot
        val rel = vm.currentDir.absolutePath.removePrefix(root.absolutePath).split('/').filter { it.isNotEmpty() }
        listOf((vm.currentVolume?.let { shortLabel(it.kind) } ?: "Память") to { vm.open(root) }) + rel.indices.map { i ->
            rel[i] to { vm.open(File(root, rel.take(i + 1).joinToString("/"))) }
        }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(crumbs) { i, (label, action) ->
            val last = i == crumbs.lastIndex
            AssistChip(
                onClick = action,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (i == 0) ({ Icon(if (a != null) Icons.Rounded.FolderZip else volumeIcon(vm.currentVolume?.kind), null, Modifier.size(18.dp)) }) else null,
                shape = MaterialTheme.shapes.large,
                colors = if (last) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
                border = null,
            )
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Имя") }, shape = MaterialTheme.shapes.large) },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun PasswordDialog(name: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Архив защищён") },
        text = {
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(), shape = MaterialTheme.shapes.large)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Открыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskDialog(t: com.klan3s.files.data.TaskProgress, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (t.done) onDismiss() },
        title = { Text(if (t.error != null) "Ошибка" else t.title) },
        text = {
            Column {
                if (t.error != null) Text(t.error, color = MaterialTheme.colorScheme.error)
                else {
                    Text(t.detail.ifEmpty { "Подготовка…" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    if (t.fraction == null) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth())
                    if (t.fraction != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("${(t.fraction * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = { if (t.done) TextButton(onClick = onDismiss) { Text("OK") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompressSheet(count: Int, defaultName: String, defaultFormat: ArchiveFormat, defaultLevel: Int, onDismiss: () -> Unit, onCreate: (ArchiveFormat, String, Int, SevenZMethod) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var format by remember { mutableStateOf(defaultFormat) }
    var level by remember { mutableStateOf(defaultLevel.toFloat()) }
    var method by remember { mutableStateOf(SevenZMethod.LZMA2) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Создать архив", style = MaterialTheme.typography.headlineSmall)
            Text("$count элем. будет упаковано в текущую папку", color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Имя архива") },
                suffix = { Text(format.ext) }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
            )

            Text("Формат", style = MaterialTheme.typography.labelLarge)
            val formats = ArchiveFormat.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        row.forEachIndexed { i, f ->
                            ToggleButton(
                                checked = format == f, onCheckedChange = { format = f },
                                modifier = Modifier.weight(1f),
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            ) { Text(f.label, maxLines = 1) }
                        }
                    }
                }
            }

            if (format == ArchiveFormat.SEVEN_Z) {
                Text("Метод сжатия", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SevenZMethod.entries) { m ->
                        ToggleButton(checked = method == m, onCheckedChange = { method = m }) { Text(m.label) }
                    }
                }
            } else if (format.hasLevel) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Степень сжатия", style = MaterialTheme.typography.labelLarge)
                    Text(
                        when (level.toInt()) { 0 -> "без сжатия"; in 1..3 -> "быстро (${level.toInt()})"; in 4..6 -> "обычно (${level.toInt()})"; else -> "максимум (${level.toInt()})" },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(value = level, onValueChange = { level = it }, valueRange = 0f..9f, steps = 8)
            } else {
                Text("TAR без сжатия — просто объединяет файлы", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                Button(
                    onClick = { onCreate(format, name, level.toInt(), method) },
                    modifier = Modifier.weight(1f).height(48.dp), enabled = name.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Icon(Icons.Rounded.Archive, null); Spacer(Modifier.width(8.dp)); Text("Создать") }
            }
        }
    }
}

// ------------------------------------------------------------------ permission

private fun hasStoragePermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(140.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FolderZip, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(28.dp))
            Text("Доступ к файлам", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Чтобы просматривать папки, открывать и создавать архивы, приложению нужен доступ ко всем файлам.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        runCatching { settings.launch(i) }.onFailure { settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    } else legacy.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                },
                modifier = Modifier.height(56.dp), shapes = ButtonDefaults.shapes(),
            ) { Text("Разрешить доступ", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun shortLabel(k: VolumeKind) = when (k) {
    VolumeKind.INTERNAL -> "Память"; VolumeKind.SD -> "SD"; VolumeKind.USB -> "USB"; VolumeKind.ROOT -> "/"; VolumeKind.OTHER -> "Диск"
}

private fun volumeIcon(k: VolumeKind?) = when (k) {
    VolumeKind.SD -> Icons.Rounded.SdCard; VolumeKind.USB -> Icons.Rounded.Usb; VolumeKind.ROOT -> Icons.Rounded.Tag; else -> Icons.Rounded.Home
}

private fun openExternal(ctx: android.content.Context, e: com.klan3s.files.data.FsEntry) {
    runCatching {
        val uri = e.docUri ?: FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", e.file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, FileOps.mime(e.name)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}

@Composable
fun SafRequestDialog(vm: MainViewModel, v: com.klan3s.files.data.Volume) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> vm.onSafTreeResult(r.data?.data) }
    AlertDialog(
        onDismissRequest = vm::dismissSafRequest,
        icon = { Icon(Icons.Rounded.Usb, null) },
        title = { Text("Доступ к накопителю") },
        text = { Text("Android не даёт приложениям прямой доступ к «${v.label}». Нажмите «Разрешить» и в системном окне выберите корень накопителя → «Использовать эту папку». Разрешение сохранится.") },
        confirmButton = { Button(onClick = { runCatching { launcher.launch(vm.safTreeIntent(v)) }.onFailure { vm.message = "Не удалось открыть системный диалог" } }) { Text("Разрешить") } },
        dismissButton = { TextButton(onClick = vm::dismissSafRequest) { Text("Отмена") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
