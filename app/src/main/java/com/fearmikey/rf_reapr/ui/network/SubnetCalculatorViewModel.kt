package com.fearmikey.rf_reapr.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Only needed to fetch the device's current IPv4 network; all maths is in SubnetCalculator. */
class SubnetCalculatorViewModel(
    private val discoveryRepository: NetworkDiscoveryRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadCurrentNetwork(onLoaded: (ip: String, prefix: Int) -> Unit) {
        viewModelScope.launch {
            runCatching { discoveryRepository.getLocalNetworkInfo() }
                .onSuccess {
                    _message.value = null
                    onLoaded(it.localIp, it.prefixLength)
                }
                .onFailure { _message.value = "No IPv4 network connection found" }
        }
    }
}
