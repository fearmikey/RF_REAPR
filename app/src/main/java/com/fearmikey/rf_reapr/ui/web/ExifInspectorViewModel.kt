package com.fearmikey.rf_reapr.ui.web

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.repository.ExifRepository
import com.fearmikey.rf_reapr.domain.repository.SanitizedImage
import com.fearmikey.rf_reapr.domain.service.ExifPrivacyAnalyzer
import com.fearmikey.rf_reapr.domain.service.ExifPrivacyFinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExifInspectorUiState(
    val selectedUri: Uri? = null,
    val isLoading: Boolean = false,
    val metadata: ExifMetadata? = null,
    val findings: List<ExifPrivacyFinding> = emptyList(),
    val editingIndicators: List<String> = emptyList(),
    val isStripping: Boolean = false,
    val sanitized: SanitizedImage? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ExifInspectorViewModel(
    private val exifRepository: ExifRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExifInspectorUiState())
    val uiState: StateFlow<ExifInspectorUiState> = _uiState.asStateFlow()

    fun selectImage(uri: Uri) {
        _uiState.value = ExifInspectorUiState(selectedUri = uri, isLoading = true)

        viewModelScope.launch {
            exifRepository.extractMetadata(uri)
                .onSuccess { meta ->
                    // Ignore a result for an image the user has already replaced.
                    _uiState.update { state ->
                        if (state.selectedUri != uri) state else state.copy(
                            isLoading = false,
                            metadata = meta,
                            findings = ExifPrivacyAnalyzer.analyze(meta),
                            editingIndicators = ExifPrivacyAnalyzer.editingIndicators(meta)
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        if (state.selectedUri != uri) state
                        else state.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Could not read the image's metadata")
                    }
                }
        }
    }

    fun stripExif() {
        val uri = _uiState.value.selectedUri ?: return
        if (_uiState.value.isStripping) return
        _uiState.update { it.copy(isStripping = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            exifRepository.stripMetadata(uri)
                .onSuccess { image ->
                    _uiState.update { state ->
                        if (state.selectedUri != uri) state.copy(isStripping = false) else state.copy(
                            isStripping = false,
                            sanitized = image,
                            successMessage = if (image.remainingTags.isEmpty()) {
                                "Clean copy created and verified: no location, device or time metadata remains."
                            } else null,
                            errorMessage = if (image.remainingTags.isNotEmpty()) {
                                "The copy still contains: ${image.remainingTags.joinToString(", ")}. Don't share it as sanitized."
                            } else null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isStripping = false, errorMessage = err.localizedMessage ?: "Could not strip the metadata") }
                }
        }
    }

    /** Writes the sanitized copy to a document the user created with the system file picker. */
    fun saveSanitizedTo(destination: Uri) {
        val image = _uiState.value.sanitized ?: return
        viewModelScope.launch {
            exifRepository.saveSanitized(image, destination)
                .onSuccess { _uiState.update { it.copy(successMessage = "Sanitized image saved.", errorMessage = null) } }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Could not save the image") } }
        }
    }

    fun report(): String? = _uiState.value.metadata?.let(ExifPrivacyAnalyzer::report)

    fun clear() {
        _uiState.value = ExifInspectorUiState()
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
