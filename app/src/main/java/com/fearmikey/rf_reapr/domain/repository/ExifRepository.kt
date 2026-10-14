package com.fearmikey.rf_reapr.domain.repository

import android.net.Uri
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import java.io.File

interface ExifRepository {
    suspend fun extractMetadata(uri: Uri): Result<ExifMetadata>
    suspend fun stripMetadata(uri: Uri): Result<File>
}
