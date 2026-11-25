package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.repository.ExifRepository
import com.fearmikey.rf_reapr.domain.repository.SanitizedImage
import com.fearmikey.rf_reapr.domain.service.EvidenceHasher
import com.fearmikey.rf_reapr.domain.service.ExifPrivacyAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExifRepositoryImpl(
    private val context: Context
) : ExifRepository {

    override suspend fun extractMetadata(uri: Uri): Result<ExifMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var fileName: String? = null
            var fileSize: Long = -1L
            val mimeType = resolver.getType(uri)

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            var width: Int? = null
            var height: Int? = null
            resolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    width = options.outWidth
                    height = options.outHeight
                }
            }

            val sha256 = resolver.openInputStream(uri)?.use { EvidenceHasher.sha256(it) }
            val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: throw IllegalStateException("Could not open the image")

            if (width == null) width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
            if (height == null) height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }

            val rawTags = readTags(exif)
            val latLong = exif.latLong
            ExifMetadata(
                fileName = fileName ?: uri.lastPathSegment ?: "image",
                fileSizeFormatted = if (fileSize > 0) formatFileSize(fileSize) else null,
                mimeType = mimeType,
                sha256 = sha256,
                imageWidth = width,
                imageHeight = height,
                orientation = parseOrientation(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)),
                dateTimeOriginal = rawTags["DateTimeOriginal"] ?: rawTags["DateTime"],
                make = rawTags["Make"],
                model = rawTags["Model"],
                lensModel = rawTags["LensModel"],
                software = rawTags["Software"],
                focalLength = rawTags["FocalLength"]?.let { value ->
                    ExifPrivacyAnalyzer.parseRational(value)?.let { String.format(Locale.US, "%.1f mm", it) } ?: "$value mm"
                },
                fNumber = rawTags["FNumber"]?.let { "f/$it" },
                exposureTime = rawTags["ExposureTime"]?.let { value ->
                    val seconds = value.toDoubleOrNull()
                    if (seconds != null && seconds > 0 && seconds < 1.0) "1/${Math.round(1.0 / seconds)} s" else "$value s"
                },
                iso = rawTags["PhotographicSensitivity"] ?: rawTags["ISOSpeedRatings"],
                flash = rawTags["Flash"]?.toIntOrNull()?.let { if (it and 1 != 0) "Fired" else "Did not fire" },
                whiteBalance = when (rawTags["WhiteBalance"]?.toIntOrNull()) {
                    0 -> "Auto"
                    1 -> "Manual"
                    else -> rawTags["WhiteBalance"]
                },
                latitude = latLong?.get(0),
                longitude = latLong?.get(1),
                altitude = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() },
                hasThumbnail = exif.hasThumbnail(),
                hasXmp = exif.hasAttribute(XMP_TAG),
                rawTags = rawTags
            )
        }
    }

    /** Every populated tag from [TAGS], keyed by EXIF tag name. Binary blobs are summarised, not dumped. */
    private fun readTags(exif: ExifInterface): Map<String, String> = buildMap {
        for (tag in TAGS) {
            if (tag in BINARY_TAGS) {
                exif.getAttributeBytes(tag)?.let { put(tag, "present (${it.size} bytes)") }
            } else {
                exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { put(tag, it.take(MAX_TAG_LENGTH)) }
            }
        }
    }

    override suspend fun stripMetadata(uri: Uri): Result<SanitizedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("This file is not an image Android can decode")

            // Re-encoding needs the whole bitmap in memory; scale down rather than crash on huge photos.
            var sampleSize = 1
            val maxPixels = (Runtime.getRuntime().maxMemory() / 4 / BYTES_PER_PIXEL).coerceAtLeast(1_000_000L)
            while (bounds.outWidth.toLong() * bounds.outHeight / (sampleSize.toLong() * sampleSize) > maxPixels) sampleSize *= 2

            var decoded: Bitmap? = null
            while (decoded == null) {
                try {
                    decoded = resolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                    } ?: throw IllegalStateException("Unable to decode the image")
                } catch (_: OutOfMemoryError) {
                    if (sampleSize >= 16) throw IllegalStateException("The image is too large to process on this device")
                    sampleSize *= 2
                }
            }

            // The orientation tag is about to be dropped, so bake the rotation into the pixels.
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }
            val bitmap = if (matrix.isIdentity) decoded else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it != decoded) decoded.recycle()
                }
            }

            // PNG stays PNG (lossless, keeps transparency); everything else becomes a high-quality JPEG.
            val png = resolver.getType(uri) == "image/png"
            val directory = File(context.cacheDir, SANITIZED_DIR).apply { mkdirs() }
            // Earlier sanitized copies are no longer referenced; don't let them pile up in the cache.
            directory.listFiles()?.forEach { it.delete() }
            val output = File(directory, "sanitized_${System.currentTimeMillis()}.${if (png) "png" else "jpg"}")
            val width = bitmap.width
            val height = bitmap.height
            try {
                FileOutputStream(output).use { stream ->
                    val written = if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    if (!written) throw IllegalStateException("Could not encode the sanitized image")
                }
            } finally {
                bitmap.recycle()
            }

            // Verify instead of assuming: read the copy back and look for anything privacy-relevant.
            val check = ExifInterface(output.absolutePath)
            val remaining = PRIVACY_TAGS.filter { check.hasAttribute(it) } +
                listOfNotNull("embedded thumbnail".takeIf { check.hasThumbnail() }, "XMP block".takeIf { check.hasAttribute(XMP_TAG) })

            SanitizedImage(
                file = output,
                mimeType = if (png) "image/png" else "image/jpeg",
                width = width,
                height = height,
                sizeFormatted = formatFileSize(output.length()),
                remainingTags = remaining,
                downscaled = sampleSize > 1
            )
        }
    }

    override suspend fun saveSanitized(image: SanitizedImage, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!image.file.isFile) throw IllegalStateException("The sanitized copy is no longer in the cache; strip the image again")
            val out = context.contentResolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("Could not open the destination for writing")
            out.use { stream -> image.file.inputStream().use { it.copyTo(stream) } }
            Unit
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun parseOrientation(orientation: Int): String = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> "90° CW"
        ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "270° CW"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flipped horizontally"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flipped vertically"
        ExifInterface.ORIENTATION_TRANSPOSE -> "Transposed"
        ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
        else -> "Normal (0°)"
    }

    private companion object {
        const val SANITIZED_DIR = "sanitized_images"
        const val BYTES_PER_PIXEL = 4L
        const val MAX_TAG_LENGTH = 300
        const val XMP_TAG = "Xmp"

        /** Tags whose value is an opaque blob. */
        val BINARY_TAGS = setOf("MakerNote")

        /** Tags that tie an image to a place, time, device or person. */
        val PRIVACY_TAGS = listOf(
            "GPSLatitude", "GPSLongitude", "GPSAltitude", "GPSDateStamp", "GPSTimeStamp", "GPSProcessingMethod",
            "GPSAreaInformation", "GPSImgDirection", "GPSSpeed", "GPSDestLatitude", "GPSDestLongitude",
            "Make", "Model", "Software", "LensMake", "LensModel", "BodySerialNumber", "LensSerialNumber",
            "CameraOwnerName", "Artist", "Copyright", "ImageUniqueID", "ImageDescription", "UserComment", "MakerNote",
            "DateTime", "DateTimeOriginal", "DateTimeDigitized"
        )

        /** Everything shown under "All tags": the privacy tags plus the capture settings. */
        val TAGS = PRIVACY_TAGS + listOf(
            "GPSLatitudeRef", "GPSLongitudeRef", "GPSAltitudeRef", "GPSImgDirectionRef", "GPSSpeedRef",
            "GPSHPositioningError", "GPSVersionID",
            "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "SubSecTimeOriginal",
            "Orientation", "ImageWidth", "ImageLength", "PixelXDimension", "PixelYDimension", "ColorSpace",
            "ExposureTime", "FNumber", "PhotographicSensitivity", "ISOSpeedRatings", "ExposureProgram",
            "ExposureBiasValue", "MeteringMode", "Flash", "FocalLength", "FocalLengthIn35mmFilm", "WhiteBalance",
            "DigitalZoomRatio", "SceneCaptureType", "ExifVersion"
        )
    }
}
