package com.fearmikey.rf_reapr.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.fearmikey.rf_reapr.data.db.dao.UsbDriveDao
import com.fearmikey.rf_reapr.data.db.entity.UsbDriveEntity
import com.fearmikey.rf_reapr.domain.model.UsbDriveInfo
import com.fearmikey.rf_reapr.domain.repository.UsbDriveRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Detects removable USB storage volumes (flash drives attached via an OTG adapter)
 * using the public [StorageManager] APIs, and writes DuckyScript payloads to them
 * via the Storage Access Framework once the user grants access to the drive's root.
 *
 * This intentionally avoids talking to the USB mass-storage protocol directly: any
 * drive Android has already mounted as a public volume can be targeted this way,
 * without requiring root or a bundled mass-storage-host implementation.
 */
class UsbDriveRepositoryImpl(
    private val context: Context,
    private val dao: UsbDriveDao
) : UsbDriveRepository {

    private val storageManager: StorageManager
        get() = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    private fun StorageVolume.stableId(): String =
        uuid?.let { "uuid:$it" } ?: "vol:${getDescription(context)}:${System.identityHashCode(this)}"

    private fun attachedVolumes(): List<StorageVolume> =
        runCatching { storageManager.storageVolumes.filter { it.isRemovable } }.getOrDefault(emptyList())

    /** Emits an updated volume list whenever removable media is mounted/unmounted. */
    private fun observeAttachedVolumes(): Flow<List<StorageVolume>> = callbackFlow {
        fun push() {
            trySend(attachedVolumes())
        }
        push()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = push()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        awaitClose { context.unregisterReceiver(receiver) }
    }

    override fun observeDrives(): Flow<List<UsbDriveInfo>> {
        return combine(observeAttachedVolumes(), dao.getAllDrives()) { attached, saved ->
            val savedById = saved.associateBy { it.id }
            val attachedIds = attached.map { it.stableId() }.toHashSet()

            val attachedInfos = attached.map { volume ->
                val id = volume.stableId()
                val savedEntry = savedById[id]
                UsbDriveInfo(
                    id = id,
                    name = volume.getDescription(context) ?: "USB Drive",
                    isAttached = true,
                    isAuthorized = savedEntry != null,
                    treeUri = savedEntry?.treeUri,
                    lastUsed = savedEntry?.lastUsed ?: 0L
                )
            }

            val disconnectedSaved = saved
                .filter { it.id !in attachedIds }
                .map { entity ->
                    UsbDriveInfo(
                        id = entity.id,
                        name = entity.name,
                        isAttached = false,
                        isAuthorized = true,
                        treeUri = entity.treeUri,
                        lastUsed = entity.lastUsed
                    )
                }

            (attachedInfos + disconnectedSaved).sortedWith(
                compareByDescending<UsbDriveInfo> { it.isAttached }.thenByDescending { it.lastUsed }
            )
        }
    }

    override fun createAccessIntent(drive: UsbDriveInfo): Intent? {
        val volume = attachedVolumes().firstOrNull { it.stableId() == drive.id } ?: return null
        // StorageVolume#createOpenDocumentTreeIntent() pre-selects the volume's root in the
        // system picker, but only exists on API 29+. Older devices fall back to a generic
        // document-tree picker where the user selects the drive manually.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            volume.createOpenDocumentTreeIntent()
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
    }

    override suspend fun onAccessGranted(driveId: String, driveName: String, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        dao.upsertDrive(
            UsbDriveEntity(
                id = driveId,
                name = driveName,
                treeUri = treeUri.toString(),
                lastUsed = System.currentTimeMillis()
            )
        )
    }

    override suspend fun forgetDrive(driveId: String) {
        dao.getDriveById(driveId)?.let { entity ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    entity.treeUri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        dao.deleteDrive(driveId)
    }

    override suspend fun writeToDrive(drive: UsbDriveInfo, fileName: String, content: String): Result<Unit> {
        val treeUriString = drive.treeUri
            ?: return Result.failure(IllegalStateException("Drive '${drive.name}' has not been authorized yet"))

        return runCatching {
            val treeUri = treeUriString.toUri()
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("Could not access '${drive.name}'")

            // Overwrite any existing payload with the same name.
            rootDoc.findFile(fileName)?.delete()

            val newFile = rootDoc.createFile("text/plain", fileName)
                ?: throw IllegalStateException("Could not create '$fileName' on '${drive.name}'")

            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(content.toByteArray())
            } ?: throw IllegalStateException("Could not open output stream for '${drive.name}'")

            dao.getDriveById(drive.id)?.let { entity ->
                dao.upsertDrive(entity.copy(lastUsed = System.currentTimeMillis()))
            }
        }
    }
}
