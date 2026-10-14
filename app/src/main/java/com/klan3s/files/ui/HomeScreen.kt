package com.klan3s.files.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.klan3s.files.data.Volume
import com.klan3s.files.data.VolumeKind
import com.klan3s.files.data.formatSize
import com.klan3s.files.ui.theme.onColorFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, snack: SnackbarHostState, onOpenArchive: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Expressive Files") },
                subtitle = { Text("${vm.volumes.size} хранилищ" + if (vm.useRoot) " • root" else "") },
                actions = {
                    IconButton(onClick = onOpenArchive) { Icon(Icons.Rounded.FolderOpen, "Открыть архив") }
                    IconButton(onClick = vm::openSettings) { Icon(Icons.Rounded.Settings, "Настройки") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vm.volumes, key = { it.key }) { v -> VolumeCard(v) { vm.openVolume(v) } }
            if (!vm.useRoot) item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Полная файловая система", style = MaterialTheme.typography.titleMedium)
                            Text("Включите root в настройках, чтобы видеть /, /data, /system", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { vm.setRootEnabled(true) }) { Text("Root") }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeCard(v: Volume, onClick: () -> Unit) {
    val (icon, shape, bg, fg) = when (v.kind) {
        VolumeKind.INTERNAL -> listOf(Icons.Rounded.PhoneAndroid, MaterialShapes.Cookie4Sided.toShape(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        VolumeKind.SD -> listOf(Icons.Rounded.SdCard, MaterialShapes.Clover4Leaf.toShape(), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        VolumeKind.USB -> listOf(Icons.Rounded.Usb, MaterialShapes.Sunny.toShape(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        VolumeKind.ROOT -> listOf(Icons.Rounded.Tag, MaterialShapes.Cookie9Sided.toShape(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        VolumeKind.OTHER -> listOf(Icons.Rounded.Storage, MaterialShapes.Circle.toShape(), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val used = v.totalBytes - v.freeBytes
    val frac = if (v.totalBytes > 0) used.toFloat() / v.totalBytes else 0f
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(shape as androidx.compose.ui.graphics.Shape).background(bg as androidx.compose.ui.graphics.Color), contentAlignment = Alignment.Center) {
                    Icon(icon as androidx.compose.ui.graphics.vector.ImageVector, null, tint = onColorFor(bg as androidx.compose.ui.graphics.Color), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.label, style = MaterialTheme.typography.titleMedium)
                    Text(v.path.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (v.needsSaf) Text(
                        if (v.treeUri != null) "Доступ через системный диалог (SAF)" else "Нажмите, чтобы предоставить доступ",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (v.totalBytes > 0) {
                Spacer(Modifier.height(14.dp))
                LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("${formatSize(used)} из ${formatSize(v.totalBytes)} • свободно ${formatSize(v.freeBytes)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
