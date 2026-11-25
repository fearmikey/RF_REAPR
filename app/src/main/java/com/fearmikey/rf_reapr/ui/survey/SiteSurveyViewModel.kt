package com.fearmikey.rf_reapr.ui.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.MeasureResult
import com.fearmikey.rf_reapr.domain.repository.SiteSurveyInfo
import com.fearmikey.rf_reapr.domain.repository.SiteSurveyRepository
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.HeatPoint
import com.fearmikey.rf_reapr.domain.service.SignalHeatmap
import com.fearmikey.rf_reapr.domain.service.SiteSample
import com.fearmikey.rf_reapr.domain.service.SurveyNetwork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which network the heatmap shows: an SSID (all its access points) or one BSSID of it. */
data class NetworkSelection(val ssid: String, val bssid: String? = null)

/** Everything the canvas needs for the selected network. */
data class HeatmapView(
    val selection: NetworkSelection? = null,
    /** One reading per measured position, including positions where the network wasn't heard. */
    val points: List<HeatPoint> = emptyList(),
    /** The individual access points (BSSIDs) of the selected SSID. */
    val accessPoints: List<SurveyNetwork> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class SiteSurveyViewModel(private val repository: SiteSurveyRepository) : ViewModel() {

    val surveys: StateFlow<List<SiteSurveyInfo>> = repository.observeSurveys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectName: StateFlow<String?> = ActiveEngagement.current.map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _current = MutableStateFlow<SiteSurveyInfo?>(null)
    val current: StateFlow<SiteSurveyInfo?> = _current.asStateFlow()

    val samples: StateFlow<List<SiteSample>> = _current
        .flatMapLatest { survey -> survey?.let { repository.observeSamples(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Networks heard anywhere in this survey, strongest first. */
    val networks: StateFlow<List<SurveyNetwork>> = samples.map { SignalHeatmap.networks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selection = MutableStateFlow<NetworkSelection?>(null)

    val heatmap: StateFlow<HeatmapView> = combine(samples, networks, _selection) { all, nets, picked ->
        // Default to the strongest network; drop a selection the samples no longer contain.
        val selection = picked?.takeIf { p -> nets.any { it.ssid == p.ssid } } ?: nets.firstOrNull()?.let { NetworkSelection(it.ssid) }
        if (selection == null) HeatmapView()
        else HeatmapView(
            selection = selection,
            points = SignalHeatmap.pointsFor(all, selection.ssid, selection.bssid),
            accessPoints = SignalHeatmap.accessPoints(all, selection.ssid)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeatmapView())

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun open(survey: SiteSurveyInfo) {
        _selection.value = null
        _current.value = survey
    }

    fun close() {
        _current.value = null
    }

    fun create(name: String, floorPlanUri: String?) = viewModelScope.launch {
        val id = repository.create(name, floorPlanUri)
        repository.get(id)?.let { survey ->
            if (floorPlanUri != null && survey.floorPlanPath == null) _message.value = "The floor plan couldn't be read; using a blank grid"
            open(survey)
        }
    }

    fun delete(survey: SiteSurveyInfo) = viewModelScope.launch {
        if (_current.value?.id == survey.id) close()
        repository.delete(survey.id)
    }

    fun select(selection: NetworkSelection) {
        _selection.value = selection
    }

    /** Measures at a normalized position on the plan. Ignored while a measurement is in flight. */
    fun measure(x: Float, y: Float) {
        val survey = _current.value ?: return
        if (_measuring.value) return
        _measuring.value = true
        viewModelScope.launch {
            try {
                _message.value = when (val result = repository.measure(survey.id, x, y)) {
                    is MeasureResult.Recorded ->
                        "Recorded ${result.accessPoints} access point${if (result.accessPoints == 1) "" else "s"}" +
                            if (result.fresh) "" else " (from a scan a few seconds old)"
                    is MeasureResult.Failed -> result.reason
                }
            } finally {
                _measuring.value = false
            }
        }
    }

    fun deletePoint(pointId: Long) = viewModelScope.launch {
        _current.value?.let { repository.deletePoint(it.id, pointId) }
    }

    /** Removes the most recently measured point. */
    fun undoLastPoint() {
        samples.value.maxOfOrNull { it.pointId }?.let(::deletePoint)
    }

    fun consumeMessage() {
        _message.value = null
    }
}
