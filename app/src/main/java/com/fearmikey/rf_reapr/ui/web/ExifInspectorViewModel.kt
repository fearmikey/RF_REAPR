package com.fearmikey.rf_reapr.ui.web

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.repository.ExifRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ExifInspectorUiState(
    val selectedUri: Uri? = null,
    val isLoading: Boolean = false,
    val metadata: ExifMetadata? = null,
    val isStripping: Boolean = false,
    val sanitizedFile: File? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ExifInspectorViewModel(
    private val exifRepository: ExifRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExifInspectorUiState())
    val uiState: StateFlow<ExifInspectorUiState> = _uiState.asStateFlow()

    fun selectImage(uri: Uri) {
        _uiState.update {
            it.copy(
                selectedUri = uri,
                isLoading = true,
                metadata = null,
                sanitizedFile = null,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            val result = exifRepository.extractMetadata(uri)
            result.onSuccess { meta ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        metadata = meta
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Failed to read image EXIF data"
                    )
                }
            }
        }
    }

    fun stripExif() {
        val uri = _uiState.value.selectedUri ?: return
        _uiState.update { it.copy(isStripping = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = exifRepository.stripMetadata(uri)
            result.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        isStripping = false,
                        sanitizedFile = file,
                        successMessage = "EXIF stripped successfully! File saved: ${file.name}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isStripping = false,
                        errorMessage = err.localizedMessage ?: "Failed to strip EXIF metadata"
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = ExifInspectorUiState()
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
