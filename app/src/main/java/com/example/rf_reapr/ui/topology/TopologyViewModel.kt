package com.example.rf_reapr.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.layout.TopologyLayoutEngine
import com.example.rf_reapr.domain.mapper.TopologyMapper
import com.example.rf_reapr.domain.model.MappedGraph
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.model.OpenPort
import com.example.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TopologyViewModel(
    private val discoveryRepository: NetworkDiscoveryRepository
) : ViewModel() {

    private val _mappedGraph = MutableStateFlow<MappedGraph?>(null)
    val mappedGraph: StateFlow<MappedGraph?> = _mappedGraph.asStateFlow()

    private val _discoveryState = MutableStateFlow<NetworkScanner.ScanResult<NetworkNode>>(NetworkScanner.ScanResult.Idle)
    val discoveryState: StateFlow<NetworkScanner.ScanResult<NetworkNode>> = _discoveryState.asStateFlow()

    /**
     * Starts a full network discovery and maps the topology.
     */
    fun startNetworkDiscovery() {
        viewModelScope.launch {
            val networkInfo = discoveryRepository.getLocalNetworkInfo()
            discoveryRepository.discoverDevices().collect { result ->
                _discoveryState.value = result
                if (result is NetworkScanner.ScanResult.Finished) {
                    val graph = TopologyMapper.mapDiscoveredNodesToGraph(
                        nodes = result.foundData,
                        gatewayIp = networkInfo.gatewayIp
                    )
                    val mapped = TopologyLayoutEngine.calculateRadialLayout(
                        graph = graph,
                        gatewayIp = networkInfo.gatewayIp
                    )
                    _mappedGraph.value = mapped
                }
            }
        }
    }

    /**
     * Updates the topology graph based on current port scan results.
     */
    fun updateTopology(scanResults: Map<String, List<OpenPort>>, gatewayIp: String? = null) {
        viewModelScope.launch {
            val graph = TopologyMapper.mapScanResultsToGraph(scanResults, gatewayIp)
            val mapped = TopologyLayoutEngine.calculateRadialLayout(graph, gatewayIp = gatewayIp)
            _mappedGraph.value = mapped
        }
    }
}
