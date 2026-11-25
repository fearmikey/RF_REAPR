package com.fearmikey.rf_reapr.domain.repository

import android.net.Uri
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import java.io.File

/** A re-encoded copy of an image with its metadata removed, and the check that it worked. */
data class SanitizedImage(
    val file: File,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeFormatted: String,
    /** Privacy-relevant tags still present in the output; empty means the copy verified clean. */
    val remainingTags: List<String>,
    /** The original was too large to decode at full size on this device and was scaled down. */
    val downscaled: Boolean
)

interface ExifRepository {
    suspend fun extractMetadata(uri: Uri): Result<ExifMetadata>

    /** Writes a metadata-free copy into the app cache and re-reads it to verify nothing survived. */
    suspend fun stripMetadata(uri: Uri): Result<SanitizedImage>

    /** Copies a sanitized image to a location the user picked. */
    suspend fun saveSanitized(image: SanitizedImage, destination: Uri): Result<Unit>
}
