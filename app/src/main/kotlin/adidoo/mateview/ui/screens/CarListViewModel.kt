// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens

import adidoo.mateview.AppContainer
import adidoo.mateview.data.NotConnectedException
import adidoo.mateview.data.repo.ListPeriod
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
import adidoo.mateview.ui.components.errorText
import java.io.IOException

data class ListUiState<T>(
    val connected: Boolean? = null,
    val carId: Int? = null,
    val period: ListPeriod = ListPeriod.QUARTER,
    val loading: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    val selectedId: Int? = null,
)

/**
 * A list of the selected car's items (drives, charges...) over a period, with
 * one item selected for the detail page. Reloads when the car or period changes.
 */
abstract class CarListViewModel<T>(protected val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ListUiState<T>())
    val state: StateFlow<ListUiState<T>> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Called from this class's init: it must only use [container], not the subclass's own fields. */
    protected abstract suspend fun fetch(carId: Int, period: ListPeriod): T

    init {
        viewModelScope.launch {
            container.settings.connection
                .map { it.verified to it.carId }
                .distinctUntilChanged()
                .collect { (verified, carId) ->
                    _state.update {
                        it.copy(
                            connected = verified && carId != null,
                            carId = carId.takeIf { verified },
                            data = null,
                            error = null,
                            selectedId = null,
                        )
                    }
                    refresh()
                }
        }
    }

    fun setPeriod(period: ListPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period, data = null, selectedId = null) }
        refresh()
    }

    fun select(id: Int?) = _state.update { it.copy(selectedId = id) }

    fun refresh() {
        val carId = _state.value.carId ?: return
        val period = _state.value.period
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val data = fetch(carId, period)
                _state.update { it.copy(loading = false, data = data) }
            } catch (e: NotConnectedException) {
                _state.update { it.copy(loading = false, connected = false) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = errorText(e)) }
            }
        }
    }
}

data class DetailUiState<T>(val loading: Boolean = true, val data: T? = null, val error: String? = null, val missing: Boolean = false)

/** Loads one item for a detail page. */
class DetailViewModel<T>(private val fetch: suspend () -> T?) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState<T>())
    val state: StateFlow<DetailUiState<T>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val data = fetch()
                _state.update { it.copy(loading = false, data = data, missing = data == null) }
            } catch (e: IOException) {
                _state.update { it.copy(loading = false, error = errorText(e)) }
            }
        }
    }
}
