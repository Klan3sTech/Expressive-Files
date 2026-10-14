package com.klan3s.files.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ThemeMode(val label: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }
enum class ViewMode(val label: String) { LIST("Список"), COMPACT("Компактный"), GRID("Сетка") }
enum class IconStyle(val label: String) { SHAPES("Фигуры M3"), ROUNDED("Скруглённые"), CIRCLE("Круги") }

data class SeedColor(val name: String, val argb: Long)

val seedColors = listOf(
    SeedColor("Dynamic", 0L),
    SeedColor("Фиолетовый", 0xFF6750A4),
    SeedColor("Индиго", 0xFF3F51B5),
    SeedColor("Синий", 0xFF1E88E5),
    SeedColor("Бирюзовый", 0xFF00897B),
    SeedColor("Зелёный", 0xFF43A047),
    SeedColor("Лайм", 0xFF7CB342),
    SeedColor("Янтарный", 0xFFFFB300),
    SeedColor("Оранжевый", 0xFFF4511E),
    SeedColor("Красный", 0xFFE53935),
    SeedColor("Розовый", 0xFFD81B60),
    SeedColor("Графит", 0xFF546E7A),
)

/** Настройки приложения — наблюдаемое состояние поверх SharedPreferences. */
class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(ThemeMode.entries[p.getInt("themeMode", 0)]); private set
    var seedArgb by mutableStateOf(p.getLong("seed", 0L)); private set           // 0 = dynamic
    var amoled by mutableStateOf(p.getBoolean("amoled", false)); private set
    var viewMode by mutableStateOf(ViewMode.entries[p.getInt("viewMode", 0)]); private set
    var gridColumns by mutableStateOf(p.getInt("gridColumns", 3)); private set
    var iconStyle by mutableStateOf(IconStyle.entries[p.getInt("iconStyle", 0)]); private set
    var showHidden by mutableStateOf(p.getBoolean("showHidden", false)); private set
    var foldersFirst by mutableStateOf(p.getBoolean("foldersFirst", true)); private set
    var sort by mutableStateOf(SortMode.entries[p.getInt("sort", 0)]); private set
    var showDetails by mutableStateOf(p.getBoolean("showDetails", true)); private set
    var showThumbnails by mutableStateOf(p.getBoolean("thumbs", true)); private set
    var useRoot by mutableStateOf(p.getBoolean("useRoot", false)); private set
    var confirmDelete by mutableStateOf(p.getBoolean("confirmDelete", true)); private set
    var defaultFormat by mutableStateOf(ArchiveFormat.entries[p.getInt("defFormat", 0)]); private set
    var defaultLevel by mutableStateOf(p.getInt("defLevel", 6)); private set

    fun updateThemeMode(v: ThemeMode) { themeMode = v; p.edit { putInt("themeMode", v.ordinal) } }
    fun updateSeed(v: Long) { seedArgb = v; p.edit { putLong("seed", v) } }
    fun updateAmoled(v: Boolean) { amoled = v; p.edit { putBoolean("amoled", v) } }
    fun updateViewMode(v: ViewMode) { viewMode = v; p.edit { putInt("viewMode", v.ordinal) } }
    fun updateGridColumns(v: Int) { gridColumns = v; p.edit { putInt("gridColumns", v) } }
    fun updateIconStyle(v: IconStyle) { iconStyle = v; p.edit { putInt("iconStyle", v.ordinal) } }
    fun updateShowHidden(v: Boolean) { showHidden = v; p.edit { putBoolean("showHidden", v) } }
    fun updateFoldersFirst(v: Boolean) { foldersFirst = v; p.edit { putBoolean("foldersFirst", v) } }
    fun updateSort(v: SortMode) { sort = v; p.edit { putInt("sort", v.ordinal) } }
    fun updateShowDetails(v: Boolean) { showDetails = v; p.edit { putBoolean("showDetails", v) } }
    fun updateShowThumbnails(v: Boolean) { showThumbnails = v; p.edit { putBoolean("thumbs", v) } }
    fun updateUseRoot(v: Boolean) { useRoot = v; p.edit { putBoolean("useRoot", v) } }
    fun updateConfirmDelete(v: Boolean) { confirmDelete = v; p.edit { putBoolean("confirmDelete", v) } }
    fun updateDefaultFormat(v: ArchiveFormat) { defaultFormat = v; p.edit { putInt("defFormat", v.ordinal) } }
    fun updateDefaultLevel(v: Int) { defaultLevel = v; p.edit { putInt("defLevel", v) } }
}
