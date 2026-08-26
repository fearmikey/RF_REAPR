package com.fearmikey.rf_reapr.domain.repository

import android.content.Intent
import android.net.Uri
import com.fearmikey.rf_reapr.domain.model.UsbDriveInfo
import kotlinx.coroutines.flow.Flow

interface UsbDriveRepository {
    /** Emits the merged set of currently attached and previously authorized USB drives. */
    fun observeDrives(): Flow<List<UsbDriveInfo>>

    /** Builds the system intent used to request write access to the root of [drive], or null if it is no longer attached. */
    fun createAccessIntent(drive: UsbDriveInfo): Intent?

    /** Persists the access grant returned after launching [createAccessIntent] for later reuse. */
    suspend fun onAccessGranted(driveId: String, driveName: String, treeUri: Uri)

    /** Removes a previously authorized drive from the saved list and releases the permission grant. */
    suspend fun forgetDrive(driveId: String)

    /** Writes [content] to a file named [fileName] at the root of the authorized [drive]. */
    suspend fun writeToDrive(drive: UsbDriveInfo, fileName: String, content: String): Result<Unit>
}
