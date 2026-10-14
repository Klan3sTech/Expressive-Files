package com.klan3s.files.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.klan3s.files.data.IconStyle
import com.klan3s.files.ui.theme.onColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileKind { FOLDER, ARCHIVE, IMAGE, VIDEO, AUDIO, PDF, APK, TEXT, CODE, OTHER }

fun kindFor(name: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.FOLDER
    val n = name.lowercase()
    val ext = n.substringAfterLast('.', "")
    return when {
        n.endsWith(".apk") || n.endsWith(".xapk") -> FileKind.APK
        com.klan3s.files.data.ArchiveService.isArchive(n) -> FileKind.ARCHIVE
        ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg") -> FileKind.IMAGE
        ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") -> FileKind.VIDEO
        ext in setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus") -> FileKind.AUDIO
        ext == "pdf" -> FileKind.PDF
        ext in setOf("txt", "md", "log", "csv", "doc", "docx", "rtf") -> FileKind.TEXT
        ext in setOf("kt", "java", "js", "ts", "py", "json", "xml", "html", "css", "c", "cpp", "h", "sh", "yaml", "yml", "prop", "rc", "conf", "ini") -> FileKind.CODE
        else -> FileKind.OTHER
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileKind.shape(style: IconStyle): Shape = when (style) {
    IconStyle.CIRCLE -> CircleShape
    IconStyle.ROUNDED -> RoundedCornerShape(14.dp)
    IconStyle.SHAPES -> when (this) {
        FileKind.FOLDER -> MaterialShapes.Cookie4Sided.toShape()
        FileKind.ARCHIVE -> MaterialShapes.Cookie9Sided.toShape()
        FileKind.IMAGE -> MaterialShapes.Clover4Leaf.toShape()
        FileKind.VIDEO -> MaterialShapes.Pill.toShape()
        FileKind.AUDIO -> MaterialShapes.Sunny.toShape()
        FileKind.PDF -> MaterialShapes.Arch.toShape()
        FileKind.APK -> MaterialShapes.Ghostish.toShape()
        FileKind.TEXT, FileKind.CODE -> MaterialShapes.Square.toShape()
        FileKind.OTHER -> MaterialShapes.Circle.toShape()
    }
}

val FileKind.icon: ImageVector
    get() = when (this) {
        FileKind.FOLDER -> Icons.Rounded.Folder
        FileKind.ARCHIVE -> Icons.Rounded.FolderZip
        FileKind.IMAGE -> Icons.Rounded.Image
        FileKind.VIDEO -> Icons.Rounded.Movie
        FileKind.AUDIO -> Icons.Rounded.MusicNote
        FileKind.PDF -> Icons.Rounded.PictureAsPdf
        FileKind.APK -> Icons.Rounded.Android
        FileKind.TEXT -> Icons.AutoMirrored.Rounded.Article
        FileKind.CODE -> Icons.Rounded.Code
        FileKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

@Composable
fun FileKind.colors(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (this) {
        FileKind.FOLDER -> c.primaryContainer to c.onPrimaryContainer
        FileKind.ARCHIVE -> c.tertiaryContainer to c.onTertiaryContainer
        FileKind.IMAGE, FileKind.VIDEO -> c.secondaryContainer to c.onSecondaryContainer
        FileKind.APK -> c.primary to c.onPrimary
        else -> c.surfaceContainerHighest to c.onSurfaceVariant
    }
}

private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
fun formatDate(ms: Long): String = if (ms <= 0) "" else dateFmt.format(Date(ms))

/** Миниатюра изображения (без сторонних библиотек). */
@Composable
fun rememberThumbnail(file: File?, enabled: Boolean, sizePx: Int = 160): Bitmap? {
    val bmp by produceState<Bitmap?>(null, file?.absolutePath, enabled) {
        value = if (file == null || !enabled) null else withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, o)
                var sample = 1
                while (o.outWidth / sample > sizePx * 2 || o.outHeight / sample > sizePx * 2) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    return bmp
}

@Composable
fun FileIcon(kind: FileKind, style: IconStyle, selected: Boolean, size: androidx.compose.ui.unit.Dp, thumb: Bitmap?, modifier: Modifier = Modifier) {
    val (iconBg, iconFg) = kind.colors()
    val shape = if (selected) MaterialTheme.shapes.extraLarge else kind.shape(style)
    Box(
        modifier.size(size).clip(shape).background(if (selected) MaterialTheme.colorScheme.primary else iconBg),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, null, tint = onColorFor(MaterialTheme.colorScheme.primary))
            thumb != null -> Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(kind.icon, null, tint = onColorFor(iconBg), modifier = Modifier.size(size / 2))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    name: String, kind: FileKind, subtitle: String, selected: Boolean, selectionMode: Boolean,
    iconStyle: IconStyle, compact: Boolean, thumb: Bitmap?, badge: String? = null,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "bg")
    val corner by animateDpAsState(if (selected) 24.dp else 12.dp, label = "corner")
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 0.dp else 2.dp)
            .clip(RoundedCornerShape(corner)).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(kind, iconStyle, selected, if (compact) 36.dp else 48.dp, thumb)
        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                if (!compact) Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Tag, badge, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
        if (kind == FileKind.FOLDER && !selectionMode) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTile(
    name: String, kind: FileKind, subtitle: String, selected: Boolean, iconStyle: IconStyle, thumb: Bitmap?,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "bg")
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (thumb != null && !selected) {
            Image(thumb.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                FileIcon(kind, iconStyle, selected, 64.dp, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
