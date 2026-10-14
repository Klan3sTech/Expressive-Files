package com.klan3s.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.klan3s.files.data.ArchiveFormat
import com.klan3s.files.data.IconStyle
import com.klan3s.files.data.SortMode
import com.klan3s.files.data.ThemeMode
import com.klan3s.files.data.ViewMode
import com.klan3s.files.data.seedColors
import com.klan3s.files.ui.theme.schemeFromSeed
import com.klan3s.files.ui.theme.onColorFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, snack: SnackbarHostState) {
    val s = vm.settings
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Настройки") },
                subtitle = { Text("Внешний вид, отображение, root") },
                navigationIcon = { IconButton(onClick = { vm.goUp() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            // ---------------- Тема
            item { Section("Тема") }
            item {
                Segmented(ThemeMode.entries.map { it.label }, s.themeMode.ordinal) { s.updateThemeMode(ThemeMode.entries[it]) }
            }
            item {
                Text("Цвет", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seedColors) { c ->
                        val selected = s.seedArgb == c.argb
                        val dark = com.klan3s.files.ui.theme.isDark(s)
                        val preview = if (c.argb == 0L) MaterialTheme.colorScheme.primary else schemeFromSeed(Color(c.argb), dark).primary
                        val container = if (c.argb == 0L) MaterialTheme.colorScheme.tertiary else schemeFromSeed(Color(c.argb), dark).tertiaryContainer
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                            Box(
                                Modifier.size(52.dp)
                                    .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                                    .background(preview)
                                    .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable { s.updateSeed(c.argb) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(container).align(Alignment.BottomEnd).padding(0.dp))
                                if (selected) Icon(Icons.Rounded.Check, null, tint = onColorFor(preview))
                                else if (c.argb == 0L) Icon(Icons.Rounded.AutoAwesome, null, tint = onColorFor(preview), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { SwitchRow("AMOLED-чёрный фон", "Чистый чёрный в тёмной теме", s.amoled) { s.updateAmoled(it) } }

            // ---------------- Отображение
            item { Section("Отображение") }
            item { Segmented(ViewMode.entries.map { it.label }, s.viewMode.ordinal) { s.updateViewMode(ViewMode.entries[it]) } }
            if (s.viewMode == ViewMode.GRID) item {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Колонок в сетке: ${s.gridColumns}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(170.dp))
                    Slider(value = s.gridColumns.toFloat(), onValueChange = { s.updateGridColumns(it.toInt()) }, valueRange = 2f..6f, steps = 3)
                }
            }
            item {
                Text("Стиль иконок", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Segmented(IconStyle.entries.map { it.label }, s.iconStyle.ordinal) { s.updateIconStyle(IconStyle.entries[it]) }
            }
            item { SwitchRow("Миниатюры изображений", "Показывать превью фото вместо иконки", s.showThumbnails) { s.updateShowThumbnails(it) } }
            item { SwitchRow("Подробности", "Размер и дата под именем файла", s.showDetails) { s.updateShowDetails(it) } }
            item { SwitchRow("Папки сверху", null, s.foldersFirst) { s.updateFoldersFirst(it); vm.refresh() } }
            item { SwitchRow("Скрытые файлы", "Показывать файлы, начинающиеся с точки", s.showHidden) { s.updateShowHidden(it); vm.refresh() } }
            item {
                Text("Сортировка по умолчанию", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Segmented(SortMode.entries.map { it.label }, s.sort.ordinal) { vm.changeSort(SortMode.entries[it]) }
            }

            // ---------------- Архивы
            item { Section("Архивы") }
            item {
                Text("Формат по умолчанию", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Segmented(ArchiveFormat.entries.take(3).map { it.label }, s.defaultFormat.ordinal.coerceIn(0, 2)) { s.updateDefaultFormat(ArchiveFormat.entries[it]) }
            }
            item {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Степень сжатия: ${s.defaultLevel}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(170.dp))
                    Slider(value = s.defaultLevel.toFloat(), onValueChange = { s.updateDefaultLevel(it.toInt()) }, valueRange = 0f..9f, steps = 8)
                }
            }

            // ---------------- Root
            item { Section("Root-доступ") }
            item {
                SwitchRow(
                    "Использовать root",
                    when {
                        vm.rootAvailable == true -> "Доступ получен — видна вся файловая система"
                        vm.rootAvailable == false -> "su недоступен или запрос отклонён — нажмите, чтобы запросить снова"
                        s.useRoot -> "Включено — проверка su…"
                        else -> "Для просмотра /, /data, /system и записи в системные папки"
                    },
                    vm.useRoot,
                ) { vm.setRootEnabled(it) }
            }
            item { SwitchRow("Подтверждать удаление", null, s.confirmDelete) { s.updateConfirmDelete(it) } }

            item {
                Spacer(Modifier.height(24.dp))
                Text("Expressive Files 1.1 • Material 3 Expressive", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Connected button group (M3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Segmented(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { i, label ->
            ToggleButton(
                checked = selected == i, onCheckedChange = { onSelect(i) }, modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
