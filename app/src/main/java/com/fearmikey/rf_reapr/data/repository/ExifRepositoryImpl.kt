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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExifRepositoryImpl(
    private val context: Context
) : ExifRepository {

    @Suppress("DEPRECATION")
    override suspend fun extractMetadata(uri: Uri): Result<ExifMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            var fileName: String? = null
            var fileSize: Long = -1L
            val mimeType = context.contentResolver.getType(uri)

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            if (fileName == null) {
                fileName = uri.lastPathSegment ?: "image"
            }

            val fileSizeFormatted = if (fileSize > 0) formatFileSize(fileSize) else null

            var width: Int? = null
            var height: Int? = null

            // Read image dimensions via BitmapFactory bounds
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    width = options.outWidth
                    height = options.outHeight
                }
            }

            // Read EXIF tags using AndroidX ExifInterface
            val exif = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream)
            } ?: throw IllegalStateException("Could not open input stream for image")

            val exifWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val exifHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (width == null && exifWidth > 0) width = exifWidth
            if (height == null && exifHeight > 0) height = exifHeight

            val latLong = exif.latLong
            val latitude = latLong?.get(0)
            val longitude = latLong?.get(1)
            val altitude = exif.getAltitude(Double.NaN).let { if (it.isNaN()) null else it }

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

            val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { "$it mm" }
            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
            val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
                val sec = it.toDoubleOrNull()
                if (sec != null && sec > 0 && sec < 1.0) {
                    "1/${(1.0 / sec).toInt()}s"
                } else {
                    "${it}s"
                }
            }
            val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val flash = parseFlash(exif.getAttribute(ExifInterface.TAG_FLASH))
            val whiteBalance = parseWhiteBalance(exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE))
            val orientation = parseOrientation(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
            val lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)

            // Extract non-null raw tags
            val rawTags = mutableMapOf<String, String>()
            val standardTagNames = listOf(
                ExifInterface.TAG_MAKE to "Make",
                ExifInterface.TAG_MODEL to "Model",
                ExifInterface.TAG_SOFTWARE to "Software",
                ExifInterface.TAG_DATETIME to "DateTime",
                ExifInterface.TAG_DATETIME_ORIGINAL to "DateTimeOriginal",
                ExifInterface.TAG_DATETIME_DIGITIZED to "DateTimeDigitized",
                ExifInterface.TAG_ORIENTATION to "Orientation",
                ExifInterface.TAG_IMAGE_WIDTH to "ImageWidth",
                ExifInterface.TAG_IMAGE_LENGTH to "ImageLength",
                ExifInterface.TAG_EXPOSURE_TIME to "ExposureTime",
                ExifInterface.TAG_F_NUMBER to "FNumber",
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "ISO",
                ExifInterface.TAG_FLASH to "Flash",
                ExifInterface.TAG_FOCAL_LENGTH to "FocalLength",
                ExifInterface.TAG_WHITE_BALANCE to "WhiteBalance",
                ExifInterface.TAG_LENS_MAKE to "LensMake",
                ExifInterface.TAG_LENS_MODEL to "LensModel",
                ExifInterface.TAG_GPS_LATITUDE to "GPSLatitude",
                ExifInterface.TAG_GPS_LATITUDE_REF to "GPSLatitudeRef",
                ExifInterface.TAG_GPS_LONGITUDE to "GPSLongitude",
                ExifInterface.TAG_GPS_LONGITUDE_REF to "GPSLongitudeRef",
                ExifInterface.TAG_GPS_ALTITUDE to "GPSAltitude",
                ExifInterface.TAG_GPS_ALTITUDE_REF to "GPSAltitudeRef",
                ExifInterface.TAG_GPS_DATESTAMP to "GPSDateStamp",
                ExifInterface.TAG_GPS_TIMESTAMP to "GPSTimeStamp",
                ExifInterface.TAG_GPS_PROCESSING_METHOD to "GPSProcessingMethod"
            )

            for ((tagConstant, label) in standardTagNames) {
                val value = exif.getAttribute(tagConstant)
                if (!value.isNullOrBlank()) {
                    rawTags[label] = value
                }
            }

            ExifMetadata(
                fileName = fileName,
                fileSizeFormatted = fileSizeFormatted,
                mimeType = mimeType,
                imageWidth = width,
                imageHeight = height,
                orientation = orientation,
                dateTimeOriginal = dateTimeOriginal,
                make = make,
                model = model,
                lensModel = lensModel,
                software = software,
                focalLength = focalLength,
                fNumber = fNumber,
                exposureTime = exposureTime,
                iso = iso,
                flash = flash,
                whiteBalance = whiteBalance,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                rawTags = rawTags
            )
        }
    }

    override suspend fun stripMetadata(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Read orientation before decoding
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Unable to decode image bitmap")

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

            val finalBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                    if (it != originalBitmap) originalBitmap.recycle()
                }
            } else {
                originalBitmap
            }

            val sanitizedDir = File(context.cacheDir, "sanitized_images").apply { mkdirs() }
            val outputFile = File(sanitizedDir, "sanitized_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
            }
            finalBitmap.recycle()

            outputFile
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun parseFlash(flashValue: String?): String? {
        val code = flashValue?.toIntOrNull() ?: return null
        return if (code and 1 != 0) "Fired" else "Did not fire"
    }

    @Suppress("DEPRECATION")
    private fun parseWhiteBalance(wbValue: String?): String? {
        return when (wbValue?.toIntOrNull()) {
            ExifInterface.WHITEBALANCE_AUTO -> "Auto"
            ExifInterface.WHITEBALANCE_MANUAL -> "Manual"
            else -> wbValue
        }
    }

    private fun parseOrientation(orientation: Int): String {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> "90° CW"
            ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
            ExifInterface.ORIENTATION_ROTATE_270 -> "270° CW"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flipped Horizontal"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flipped Vertical"
            else -> "Normal (0°)"
        }
    }
}
