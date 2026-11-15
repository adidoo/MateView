// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.settings

import adidoo.mateview.AppContainer
import adidoo.mateview.data.Car
import adidoo.mateview.data.ConnectionReport
import adidoo.mateview.data.ResolvedConnection
import adidoo.mateview.data.StepStatus
import adidoo.mateview.data.net.Addresses
import adidoo.mateview.data.net.Stage
import adidoo.mateview.data.settings.ConnectionMode
import adidoo.mateview.data.settings.ConnectionSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FieldError { EMPTY_URL, INVALID_URL, INSECURE_URL, MISSING_TOKEN }

data class StepUi(val stage: Stage, val status: StepStatus)

data class SettingsUiState(
    val loaded: Boolean = false,
    val mode: ConnectionMode = ConnectionMode.HOME_ASSISTANT,
    val haUrl: String = "",
    val haPanel: String = ConnectionSettings.DEFAULT_PANEL,
    /** What the user is typing; empty means "keep the stored token". */
    val haToken: String = "",
    val hasHaToken: Boolean = false,
    val grafanaUrl: String = "",
    val grafanaToken: String = "",
    val hasGrafanaToken: Boolean = false,
    val fieldError: FieldError? = null,
    val testing: Boolean = false,
    val steps: List<StepUi> = emptyList(),
    val report: ConnectionReport? = null,
    val selectedCarId: Int? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.settings.connection.first()
            _state.update {
                it.copy(
                    loaded = true,
                    mode = saved.mode,
                    haUrl = saved.haUrl,
                    haPanel = saved.haPanel,
                    hasHaToken = saved.hasHaToken,
                    grafanaUrl = saved.grafanaUrl,
                    hasGrafanaToken = saved.hasGrafanaToken,
                    selectedCarId = saved.carId,
                )
            }
        }
    }

    fun setMode(mode: ConnectionMode) = edit { it.copy(mode = mode) }
    fun setHaUrl(value: String) = edit { it.copy(haUrl = value) }
    fun setHaPanel(value: String) = edit { it.copy(haPanel = value) }
    fun setHaToken(value: String) = edit { it.copy(haToken = value) }
    fun setGrafanaUrl(value: String) = edit { it.copy(grafanaUrl = value) }
    fun setGrafanaToken(value: String) = edit { it.copy(grafanaToken = value) }

    private fun edit(change: (SettingsUiState) -> SettingsUiState) {
        _state.update { change(it).copy(fieldError = null) }
    }

    fun selectCar(car: Car) {
        _state.update { it.copy(selectedCarId = car.id) }
        viewModelScope.launch { container.settings.selectCar(car.id, car.name) }
    }

    /** Saves the form, then runs the connection test stage by stage. */
    fun saveAndTest() {
        val form = _state.value
        if (form.testing) return
        val isHa = form.mode == ConnectionMode.HOME_ASSISTANT
        val url = when (val parsed = Addresses.parse(if (isHa) form.haUrl else form.grafanaUrl)) {
            is Addresses.Result.Ok -> parsed.url
            Addresses.Result.Empty -> return fail(FieldError.EMPTY_URL)
            Addresses.Result.Invalid -> return fail(FieldError.INVALID_URL)
            Addresses.Result.InsecureRemote -> return fail(FieldError.INSECURE_URL)
        }
        if (isHa && form.haToken.isBlank() && !form.hasHaToken) return fail(FieldError.MISSING_TOKEN)

        viewModelScope.launch {
            val settings = container.settings
            settings.saveConnection(
                mode = form.mode,
                haUrl = form.haUrl,
                haPanel = form.haPanel,
                haToken = form.haToken.ifBlank { null },
                grafanaUrl = form.grafanaUrl,
                grafanaToken = form.grafanaToken.ifBlank { null },
            )
            val saved = settings.connection.first()
            val connection = if (isHa) {
                ResolvedConnection.HomeAssistant(url, settings.haToken().orEmpty(), saved.haPanel)
            } else {
                ResolvedConnection.Direct(url, settings.grafanaToken())
            }
            _state.update {
                it.copy(
                    // The typed tokens are now stored encrypted: drop them from memory.
                    haToken = "",
                    grafanaToken = "",
                    hasHaToken = saved.hasHaToken,
                    hasGrafanaToken = saved.hasGrafanaToken,
                    haPanel = saved.haPanel,
                    testing = true,
                    report = null,
                    steps = connection.stages.map { stage -> StepUi(stage, StepStatus.Pending) },
                )
            }

            val report = container.connectionTester.run(connection) { stage, status ->
                _state.update { current ->
                    current.copy(steps = current.steps.map { if (it.stage == stage) it.copy(status = status) else it })
                }
            }

            if (report != null) {
                settings.setVerified(true)
                val car = report.cars.firstOrNull { it.id == saved.carId } ?: report.cars.first()
                settings.selectCar(car.id, car.name)
                _state.update { it.copy(testing = false, report = report, selectedCarId = car.id) }
            } else {
                _state.update { it.copy(testing = false) }
            }
        }
    }

    private fun fail(error: FieldError) {
        _state.update { it.copy(fieldError = error) }
    }
}
