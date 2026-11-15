// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.overview

import adidoo.mateview.AppContainer
import adidoo.mateview.data.NotConnectedException
import adidoo.mateview.data.repo.Overview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** The periods offered on the home screen; the first one is TeslaMate's default. */
enum class OverviewPeriod(val durationMs: Long) {
    DAY(24 * 60 * 60 * 1000L),
    WEEK(7 * 24 * 60 * 60 * 1000L),
    MONTH(30 * 24 * 60 * 60 * 1000L),
}

data class OverviewUiState(
    val period: OverviewPeriod = OverviewPeriod.DAY,
    /** False until the connection has been tested successfully in the settings. */
    val connected: Boolean? = null,
    val carName: String? = null,
    val loading: Boolean = false,
    val overview: Overview? = null,
    val error: String? = null,
)

class OverviewViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    private var carId: Int? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.connection
                .map { Triple(it.verified, it.carId, it.carName) }
                .distinctUntilChanged()
                .collect { (verified, id, name) ->
                    carId = id.takeIf { verified }
                    _state.update {
                        it.copy(connected = verified && id != null, carName = name, overview = null, error = null)
                    }
                    load()
                }
        }
    }

    fun setPeriod(period: OverviewPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun refresh() = load(quiet = false)

    /** Periodic refresh while the screen is shown: no spinner, and a failure keeps what is displayed. */
    fun refreshQuietly() {
        if (loadJob?.isActive == true) return
        load(quiet = true)
    }

    private fun load(quiet: Boolean = false) {
        val id = carId ?: return
        val period = _state.value.period
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!quiet) _state.update { it.copy(loading = true, error = null) }
            try {
                val overview = container.overview.load(id, period.durationMs)
                _state.update { it.copy(loading = false, overview = overview, error = null) }
            } catch (e: NotConnectedException) {
                _state.update { it.copy(loading = false, connected = false) }
            } catch (e: IOException) {
                if (!quiet || _state.value.overview == null) {
                    _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
                }
            }
        }
    }
}
