package com.fearmikey.rf_reapr.domain.model

data class ExifMetadata(
    val fileName: String? = null,
    val fileSizeFormatted: String? = null,
    val mimeType: String? = null,
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
    val rawTags: Map<String, String> = emptyMap()
) {
    val hasGps: Boolean
        get() = latitude != null && longitude != null

    val dimensionsFormatted: String?
        get() = if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
            "${imageWidth} × ${imageHeight} px"
        } else null
}
