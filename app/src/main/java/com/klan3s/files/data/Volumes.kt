package com.klan3s.files.data

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.core.content.edit
import java.io.File

/** Обнаружение внутренней памяти, SD-карт и USB-накопителей. */
object Volumes {

    private const val PREFS = "saf_trees"

    private val uuidRe = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")

    fun list(ctx: Context, includeRoot: Boolean): List<Volume> {
        val result = LinkedHashMap<String, Volume>()
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val usbAttached = runCatching { (ctx.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.isNotEmpty() }.getOrDefault(false)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val persisted = ctx.contentResolver.persistedUriPermissions.filter { it.isReadPermission && it.isWritePermission }.map { it.uri }

        fun add(path: File, label: String?, removable: Boolean, primary: Boolean, hintUsb: Boolean = false, sv: StorageVolume? = null) {
            val canon = runCatching { path.canonicalFile }.getOrDefault(path)
            if (result.containsKey(canon.absolutePath)) return
            // /mnt/media_rw/XXXX дублирует /storage/XXXX
            val storageTwin = File("/storage/${canon.name}")
            if (canon.absolutePath.startsWith("/mnt/media_rw/") && result.containsKey(storageTwin.absolutePath)) return
            val desc = (label ?: "").lowercase()
            val kind = when {
                primary -> VolumeKind.INTERNAL
                hintUsb || desc.contains("usb") || desc.contains("otg") || desc.contains("флеш") || desc.contains("накопит") -> VolumeKind.USB
                desc.contains("sd") || desc.contains("карт") || desc.contains("card") -> VolumeKind.SD
                uuidRe.matches(canon.name) -> if (usbAttached) VolumeKind.USB else VolumeKind.SD
                else -> VolumeKind.OTHER
            }
            val readable = canon.canRead() && canon.listFiles() != null
            val uuid = sv?.uuid ?: canon.name
            val tree = prefs.getString(uuid, null)?.let(Uri::parse)?.takeIf { it in persisted }
                ?: persisted.firstOrNull { it.toString().contains(Uri.encode("$uuid:")) || it.toString().contains("$uuid%3A") }
            val (total, free) = space(canon)
            result[canon.absolutePath] = Volume(
                kind = kind,
                label = when (kind) {
                    VolumeKind.INTERNAL -> "Внутренняя память"
                    VolumeKind.SD -> label ?: "SD-карта"
                    VolumeKind.USB -> label ?: "USB-накопитель"
                    else -> label ?: canon.name
                },
                path = canon, totalBytes = total, freeBytes = free, removable = removable,
                needsSaf = !readable, treeUri = tree,
            )
        }

        add(Environment.getExternalStorageDirectory(), "Внутренняя память", false, true)

        // 1. StorageManager
        runCatching {
            for (v in sm.storageVolumes) {
                if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                val dir: File? = if (Build.VERSION.SDK_INT >= 30) v.directory else runCatching {
                    v.javaClass.getMethod("getPath").invoke(v) as? String
                }.getOrNull()?.let(::File)
                if (dir != null) add(dir, v.getDescription(ctx), v.isRemovable, v.isPrimary, sv = v)
            }
        }
        // 2. Прямой скан /storage — ловит тома, которые StorageManager не отдал (часто USB OTG)
        runCatching {
            File("/storage").listFiles()?.filter { it.isDirectory && it.name !in setOf("emulated", "self", "enc_emulated", "knox-emulated") }
                ?.forEach { add(it, null, true, false, hintUsb = it.name.lowercase().let { n -> n.contains("usb") || n.contains("otg") }) }
        }
        // 3. app-specific каталоги на внешних томах
        runCatching {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { f ->
                val idx = f.absolutePath.indexOf("/Android/")
                if (idx > 0) add(File(f.absolutePath.substring(0, idx)), null, true, false)
            }
        }
        // 4. /mnt/media_rw и /mnt/usb* (root / некоторые прошивки)
        runCatching {
            File("/mnt/media_rw").listFiles()?.filter { it.isDirectory }?.forEach { add(it, null, true, false, hintUsb = usbAttached) }
            File("/mnt").listFiles()?.filter { it.isDirectory && it.name.lowercase().startsWith("usb") }?.forEach { add(it, "USB ${it.name}", true, false, hintUsb = true) }
        }
        if (includeRoot) {
            val (t, f) = space(File("/data"))
            result["/"] = Volume(VolumeKind.ROOT, "Корень системы (root)", File("/"), t, f, false)
        }
        return result.values.toList()
    }

    /** Интент для запроса SAF-доступа к конкретному тому. */
    fun treeIntent(ctx: Context, v: Volume): Intent {
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val sv = runCatching { sm.getStorageVolume(v.path) }.getOrNull()
        val intent = if (Build.VERSION.SDK_INT >= 29 && sv != null) sv.createOpenDocumentTreeIntent()
        else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= 26) putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri("com.android.externalstorage.documents", v.path.name))
        }
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun saveTree(ctx: Context, v: Volume, uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val uuid = runCatching { sm.getStorageVolume(v.path)?.uuid }.getOrNull() ?: v.path.name
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(uuid, uri.toString()) }
    }

    private fun space(f: File): Pair<Long, Long> = runCatching {
        val s = StatFs(f.absolutePath); s.totalBytes to s.availableBytes
    }.getOrDefault(0L to 0L)
}
