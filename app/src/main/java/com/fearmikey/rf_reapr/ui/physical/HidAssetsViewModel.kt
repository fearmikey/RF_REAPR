package com.fearmikey.rf_reapr.ui.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.HidAsset
import com.fearmikey.rf_reapr.domain.repository.HidAssetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HidAssetsViewModel(
    private val repository: HidAssetRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<HidAsset>>(emptyList())
    val assets: StateFlow<List<HidAsset>> = _assets.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllAssets().collect {
                _assets.value = it
            }
        }
    }

    fun deleteAsset(asset: HidAsset) {
        viewModelScope.launch {
            repository.deleteAsset(asset)
        }
    }
}
