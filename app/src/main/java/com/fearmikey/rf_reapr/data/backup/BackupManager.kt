package com.fearmikey.rf_reapr.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.fearmikey.rf_reapr.data.db.AppDatabase
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Encrypted, user-initiated backup and restore of all local engagement data: the Room
 * database (projects, evidence metadata, findings, checklists, scans, logs) plus files in
 * filesDir (evidence photos, PCAPs, HID assets). Written to a location the user picks via
 * the Storage Access Framework; nothing leaves the device otherwise.
 *
 * Deliberately NOT included: app settings and API keys (EncryptedSharedPreferences are
 * bound to this device's keystore, and keys shouldn't travel in a file).
 */
class BackupManager(private val context: Context) {

    data class Summary(val databaseBytes: Long, val fileCount: Int, val totalBytes: Long)

    /** Shared prefs that hold user data (not settings or secrets). */
    private val includedPrefs = listOf("wol_targets", ActiveProjectStore.PREFS_NAME)

    private val dbFile: File get() = context.getDatabasePath(AppDatabase.NAME)
    private val prefsDir: File get() = File(context.applicationInfo.dataDir, "shared_prefs")

    suspend fun export(destination: Uri, password: CharArray, appVersion: String): Result<Summary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.size >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters" }
                val staging = File(context.cacheDir, "backup_staging").apply { deleteRecursively(); mkdirs() }
                try {
                    val dbBytes = snapshotDatabase(staging)
                    val output = context.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("Can't open the selected file for writing")
                    var fileCount = 0
                    var total = dbBytes
                    ZipOutputStream(BackupCrypto.encryptingStream(output.buffered(), password)).use { zip ->
                        zip.putNextEntry(ZipEntry(MANIFEST))
                        zip.write(manifestJson(appVersion).toByteArray())
                        zip.closeEntry()

                        staging.listFiles()?.forEach { f -> zip.addFile(f, "$DB_DIR/${f.name}") }

                        context.filesDir.walkTopDown().filter { it.isFile }.forEach { f ->
                            zip.addFile(f, "$FILES_DIR/" + f.relativeTo(context.filesDir).invariantSeparatorsPath)
                            fileCount++
                            total += f.length()
                        }
                        includedPrefs.map { File(prefsDir, "$it.xml") }.filter { it.isFile }.forEach { f ->
                            zip.addFile(f, "$PREFS_DIR/${f.name}")
                        }
                    }
                    Summary(dbBytes, fileCount, total)
                } finally {
                    staging.deleteRecursively()
                }
            }
        }

    /**
     * Decrypts and validates [source], then replaces the database and restores files.
     * On success the caller MUST restart the process: the open database has been closed
     * and SharedPreferences caches are stale.
     */
    suspend fun restore(source: Uri, password: CharArray): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val work = File(context.cacheDir, "backup_restore").apply { deleteRecursively(); mkdirs() }
            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: throw IOException("Can't open the selected file")

                // 1. Decrypt + unzip everything into a staging dir first; nothing live is touched
                //    until the whole archive has authenticated.
                var manifest: JSONObject? = null
                ZipInputStream(BackupCrypto.decryptingStream(input.buffered(), password)).use { zip ->
                    generateSequence { zip.nextEntry }.forEach { entry ->
                        if (entry.isDirectory) return@forEach
                        if (entry.name == MANIFEST) {
                            manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        } else {
                            val out = safeResolve(work, entry.name)
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                val meta = manifest ?: throw IOException("Backup is missing its manifest")
                if (meta.optString("format") != FORMAT) throw IOException("Not an RF-REAPR backup")
                val backupDbVersion = meta.optInt("dbVersion", -1)
                if (backupDbVersion > AppDatabase.VERSION) {
                    throw IOException("Backup was made by a newer RF-REAPR version (database v$backupDbVersion). Update the app first.")
                }

                val stagedDb = File(work, "$DB_DIR/${AppDatabase.NAME}")
                if (!stagedDb.isFile) throw IOException("Backup doesn't contain a database")

                // 2. Validate the database and re-point absolute file paths at this install
                //    (paths differ between devices / debug and release builds).
                val oldFilesDir = meta.optString("filesDir")
                prepareRestoredDatabase(stagedDb, oldFilesDir, context.filesDir.absolutePath)

                // 3. Swap in the database.
                AppDatabase.closeInstance()
                listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
                dbFile.parentFile?.mkdirs()
                stagedDb.copyTo(dbFile, overwrite = true)

                // 4. Restore files (merged: same names overwritten, others kept).
                var count = 0
                var total = dbFile.length()
                File(work, FILES_DIR).takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.forEach { f ->
                    val target = safeResolve(context.filesDir, f.relativeTo(File(work, FILES_DIR)).invariantSeparatorsPath)
                    target.parentFile?.mkdirs()
                    f.copyTo(target, overwrite = true)
                    count++
                    total += f.length()
                }
                File(work, PREFS_DIR).takeIf { it.isDirectory }?.listFiles()
                    ?.filter { it.nameWithoutExtension in includedPrefs }
                    ?.forEach { f -> prefsDir.mkdirs(); f.copyTo(File(prefsDir, f.name), overwrite = true) }

                Summary(dbFile.length(), count, total)
            } finally {
                work.deleteRecursively()
            }
        }
    }

    /** Checkpoints WAL, then copies the db (+ any WAL written since) under a write transaction. */
    private fun snapshotDatabase(staging: File): Long {
        val db = AppDatabase.getDatabase(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        var bytes = 0L
        db.runInTransaction {
            listOf("", "-wal").map { File(dbFile.path + it) }.filter { it.isFile && it.length() > 0 }.forEach { f ->
                f.copyTo(File(staging, f.name), overwrite = true)
                bytes += f.length()
            }
        }
        return bytes
    }

    private fun prepareRestoredDatabase(file: File, oldFilesDir: String, newFilesDir: String) {
        // Include the WAL if the backup has one, so the copy is self-contained.
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA quick_check", null).use { c ->
                if (!c.moveToFirst() || c.getString(0) != "ok") throw IOException("Backup database is corrupted")
            }
            if (oldFilesDir.isNotBlank() && oldFilesDir != newFilesDir) {
                // Exact prefix compare (LIKE would treat the '_' in package names as a wildcard).
                val args = arrayOf<Any>(newFilesDir, oldFilesDir.length + 1, oldFilesDir.length, oldFilesDir)
                db.execSQL("UPDATE evidence SET filePath = ? || substr(filePath, ?) WHERE substr(filePath, 1, ?) = ?", args)
                db.execSQL("UPDATE hid_assets SET path = ? || substr(path, ?) WHERE substr(path, 1, ?) = ?", args)
            }
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close()
        } finally {
            db.close()
        }
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun manifestJson(appVersion: String) = JSONObject()
        .put("format", FORMAT)
        .put("formatVersion", 1)
        .put("dbVersion", AppDatabase.VERSION)
        .put("appVersion", appVersion)
        .put("createdAt", System.currentTimeMillis())
        .put("filesDir", context.filesDir.absolutePath)
        .toString(2)

    private fun ZipOutputStream.addFile(file: File, name: String) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val FILE_EXTENSION = "rfbackup"
        private const val FORMAT = "rf-reapr-backup"
        private const val MANIFEST = "manifest.json"
        private const val DB_DIR = "db"
        private const val FILES_DIR = "files"
        private const val PREFS_DIR = "prefs"

        /** Zip-slip protection: [name] must resolve inside [root]. */
        fun safeResolve(root: File, name: String): File {
            val target = File(root, name).canonicalFile
            val base = root.canonicalFile
            if (!target.path.startsWith(base.path + File.separator)) {
                throw IOException("Unsafe path in backup: $name")
            }
            return target
        }
    }
}
