package com.revcore.android.core.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared, user-visible storage root: Internal Storage/Revcore.
 *
 * The app never silently falls back to app-private storage. When the platform
 * permission is missing the caller must show [Status.NeedsPermission] together
 * with the exact reason and a way to open Android settings.
 */
object RevCoreStorage {

    const val ROOT_NAME = "Revcore"

    enum class Folder(val directoryName: String) {
        LOGS("Logs"),
        BACKUP("Backup"),
        MAPS("Maps"),
        EXPORTS("Exports"),
        DATABASE("Database"),
        CONFIG("Config")
    }

    sealed class Status {
        data class Ready(val root: File) : Status()
        data class NeedsPermission(val reason: String) : Status()
        data class Failed(val reason: String) : Status()

        val isReady: Boolean get() = this is Ready
        fun pathOrNull(): String? = (this as? Ready)?.root?.absolutePath
    }

    private val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val compactStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun root(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    fun folder(folder: Folder): File = File(root(), folder.directoryName)

    /** API 26-29 use the legacy runtime write permission. */
    fun requiresLegacyRuntimePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    /** API 30+ require the special "all files access" permission for a public root. */
    fun requiresAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun hasAccess(context: Context): Boolean = when {
        requiresAllFilesAccess() -> Environment.isExternalStorageManager()
        else -> context.checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Creates the root and every subfolder, then verifies each one is a writable
     * directory on shared storage. No directory is reported as created before the
     * filesystem confirms it.
     */
    fun ensure(context: Context): Status {
        if (!hasAccess(context)) {
            return Status.NeedsPermission(
                if (requiresAllFilesAccess()) {
                    "Akses penyimpanan belum diberikan. Aktifkan \"Akses semua file\" untuk RevCore di pengaturan Android."
                } else {
                    "Izin penyimpanan belum diberikan. Izinkan RevCore mengakses penyimpanan."
                }
            )
        }
        return try {
            val root = root()
            if (!root.exists() && !root.mkdirs()) {
                return Status.Failed("Folder ${root.absolutePath} tidak dapat dibuat.")
            }
            if (!root.isDirectory) {
                return Status.Failed("${root.absolutePath} ada tetapi bukan folder.")
            }
            val missing = mutableListOf<String>()
            Folder.entries.forEach { folder ->
                val dir = File(root, folder.directoryName)
                if (!dir.exists() && !dir.mkdirs()) {
                    missing += folder.directoryName
                } else if (!dir.isDirectory) {
                    missing += folder.directoryName
                }
            }
            if (missing.isNotEmpty()) {
                return Status.Failed(
                    "Subfolder gagal dibuat: ${missing.joinToString()}. Periksa ruang penyimpanan dan izin."
                )
            }
            if (!root.canWrite()) {
                return Status.Failed("Folder ${root.absolutePath} tidak dapat ditulis.")
            }
            Status.Ready(root)
        } catch (e: Exception) {
            Status.Failed(e.message ?: "Penyimpanan bersama tidak dapat diakses.")
        }
    }

    /** Directs the user to the exact Android screen for this permission. */
    fun permissionIntent(context: Context): Intent? {
        if (!requiresAllFilesAccess()) return null
        val appUri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri)
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /** Opens the requested folder in the system file manager when one is available. */
    fun openFolderIntent(folder: Folder): Intent? {
        val relativePath = "$ROOT_NAME/${folder.directoryName}"
        val uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$relativePath"
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun sanitizeName(raw: String, fallback: String = "ECU"): String {
        val cleaned = raw
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.ifEmpty { fallback }
    }

    fun buildLogFileName(date: Date = Date()): String = "RevCore_Log_${dateStamp.format(date)}.txt"

    fun buildBackupFileName(vehicle: String, ecmId: String?, date: Date = Date()): String {
        val vehiclePart = sanitizeName(vehicle)
        val idPart = ecmId?.let { sanitizeName(it, "") }?.takeIf { it.isNotBlank() }
        val middle = if (idPart != null) "${vehiclePart}_$idPart" else vehiclePart
        return "${middle}_Backup_${dateStamp.format(date)}.bin"
    }

    fun buildMapFileName(vehicle: String, date: Date = Date()): String =
        "${sanitizeName(vehicle)}_MAP_${dateStamp.format(date)}.map"

    fun buildExportFileName(base: String, date: Date = Date()): String {
        val stem = base.substringBeforeLast('.', base)
        val ext = base.substringAfterLast('.', "bin")
        return "${sanitizeName(stem, "RevCore")}_Export_${compactStamp.format(date)}.$ext"
    }

    /**
     * Returns a file in [folder] whose name does not exist yet. Existing files are
     * never overwritten silently; an incrementing suffix is added instead.
     */
    fun uniqueFile(folder: Folder, fileName: String): File {
        val dir = folder(folder)
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isBlank()) "${stem}_$counter" else "${stem}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun existingFile(folder: Folder, fileName: String): File? =
        File(folder(folder), fileName).takeIf { it.exists() }
}
