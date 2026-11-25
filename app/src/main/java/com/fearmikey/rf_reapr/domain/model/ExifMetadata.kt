package com.fearmikey.rf_reapr.domain.model

data class ExifMetadata(
    val fileName: String? = null,
    val fileSizeFormatted: String? = null,
    val mimeType: String? = null,
    /** SHA-256 of the file as selected, so the inspected image can be tied to an evidence record. */
    val sha256: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: String? = null,
    val dateTimeOriginal: String? = null,
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val software: String? = null,
    val focalLength: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    /** An embedded preview can still show the original scene after the main image was cropped or blurred. */
    val hasThumbnail: Boolean = false,
    val hasXmp: Boolean = false,
    /** Every populated tag, keyed by its EXIF tag name ("BodySerialNumber", "GPSDateStamp", …). */
    val rawTags: Map<String, String> = emptyMap()
) {
    val hasGps: Boolean
        get() = latitude != null && longitude != null

    val dimensionsFormatted: String?
        get() = if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
            "${imageWidth} × ${imageHeight} px"
        } else null
}
