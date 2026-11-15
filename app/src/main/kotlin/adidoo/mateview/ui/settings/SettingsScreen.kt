// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.settings

import adidoo.mateview.BuildConfig
import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.data.ConnectionReport
import adidoo.mateview.data.ConnectionTester
import adidoo.mateview.data.StepStatus
import adidoo.mateview.data.net.Stage
import adidoo.mateview.data.settings.ConnectionMode
import adidoo.mateview.ui.AppLanguage
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

private const val TWO_PANE_MIN_WIDTH_DP = 600

@Composable
fun SettingsScreen() {
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    val form: @Composable () -> Unit = { ConnectionForm(state, viewModel) }
    val results: @Composable () -> Unit = {
        TestResults(state, viewModel)
        LanguageChoice()
        About()
    }

    if (LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP) {
        // Wide window: the form on the left, the test and the about section on the right.
        Row(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Pane(Modifier.weight(1f), form)
            Pane(Modifier.weight(1f), results)
        }
    } else {
        Pane(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            form()
            results()
        }
    }
}

@Composable
private fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun ConnectionForm(state: SettingsUiState, viewModel: SettingsViewModel) {
    Text(stringResource(R.string.settings_connection), style = MaterialTheme.typography.titleLarge)

    val modes = listOf(
        ConnectionMode.HOME_ASSISTANT to R.string.settings_mode_ha,
        ConnectionMode.DIRECT to R.string.settings_mode_direct,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { viewModel.setMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                enabled = !state.testing,
            ) { Text(stringResource(label)) }
        }
    }

    val urlError = when (state.fieldError) {
        FieldError.EMPTY_URL -> R.string.settings_error_empty_url
        FieldError.INVALID_URL -> R.string.settings_error_invalid_url
        FieldError.INSECURE_URL -> R.string.settings_error_insecure_url
        else -> null
    }

    if (state.mode == ConnectionMode.HOME_ASSISTANT) {
        Text(stringResource(R.string.settings_mode_ha_help), style = MaterialTheme.typography.bodyMedium)
        UrlField(state.haUrl, viewModel::setHaUrl, R.string.settings_ha_url, urlError, !state.testing)
        TokenField(
            value = state.haToken,
            onChange = viewModel::setHaToken,
            label = R.string.settings_ha_token,
            stored = state.hasHaToken,
            error = if (state.fieldError == FieldError.MISSING_TOKEN) R.string.settings_error_missing_token else null,
            enabled = !state.testing,
        )
        OutlinedTextField(
            value = state.haPanel,
            onValueChange = viewModel::setHaPanel,
            label = { Text(stringResource(R.string.settings_ha_panel)) },
            supportingText = { Text(stringResource(R.string.settings_ha_panel_help)) },
            singleLine = true,
            enabled = !state.testing,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(stringResource(R.string.settings_mode_direct_help), style = MaterialTheme.typography.bodyMedium)
        UrlField(state.grafanaUrl, viewModel::setGrafanaUrl, R.string.settings_grafana_url, urlError, !state.testing)
        TokenField(
            value = state.grafanaToken,
            onChange = viewModel::setGrafanaToken,
            label = R.string.settings_grafana_token,
            stored = state.hasGrafanaToken,
            error = null,
            enabled = !state.testing,
        )
    }

    Button(onClick = viewModel::saveAndTest, enabled = !state.testing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_save_and_test))
    }
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit, label: Int, error: Int?, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TokenField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    stored: Boolean,
    error: Int?,
    enabled: Boolean,
) {
    val help = error ?: if (stored) R.string.settings_token_stored else null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = help?.let { { Text(stringResource(it)) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestResults(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.steps.isEmpty()) return
    Text(stringResource(R.string.settings_test_title), style = MaterialTheme.typography.titleMedium)
    state.steps.forEach { StepRow(it) }

    val report = state.report ?: return
    ReportNotes(report)
    if (report.cars.size > 1) {
        Text(stringResource(R.string.settings_car), style = MaterialTheme.typography.titleMedium)
        report.cars.forEach { car ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = car.id == state.selectedCarId,
                        role = Role.RadioButton,
                        onClick = { viewModel.selectCar(car) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = car.id == state.selectedCarId, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(listOfNotNull(car.name, car.model?.let { "Model $it" }).joinToString(" · "))
            }
        }
    }
}

@Composable
private fun StepRow(step: StepUi) {
    val label = stringResource(
        when (step.stage) {
            Stage.REACH_HA -> R.string.step_reach_ha
            Stage.AUTH_HA -> R.string.step_auth_ha
            Stage.FIND_PANEL -> R.string.step_find_panel
            Stage.OPEN_SESSION -> R.string.step_open_session
            Stage.GRAFANA -> R.string.step_grafana
            Stage.DATASOURCE -> R.string.step_datasource
            Stage.DATABASE -> R.string.step_database
        },
    )
    Row(verticalAlignment = Alignment.Top) {
        when (step.status) {
            StepStatus.Running -> CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
            is StepStatus.Done ->
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            is StepStatus.Failed ->
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            StepStatus.Pending ->
                Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val detail = when (val status = step.status) {
                is StepStatus.Done -> status.detail
                is StepStatus.Failed -> status.message
                else -> null
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.status is StepStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Summary of a successful test, with the warnings the user should act on. */
@Composable
private fun ReportNotes(report: ConnectionReport) {
    val cars = report.cars.joinToString { it.name }
    Text(
        stringResource(R.string.settings_report_ok, cars, report.databaseUser.orEmpty()),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (report.canWrite) Warning(stringResource(R.string.settings_warning_can_write, report.databaseUser.orEmpty()))
    val schema = report.schemaVersion
    if (schema != null && schema != ConnectionTester.SUPPORTED_SCHEMA_VERSION) {
        Warning(
            stringResource(
                if (schema < ConnectionTester.SUPPORTED_SCHEMA_VERSION) {
                    R.string.settings_warning_schema_older
                } else {
                    R.string.settings_warning_schema_newer
                },
            ),
        )
    }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** System language by default, or a forced one. Changing it recreates the activity. */
@Composable
private fun LanguageChoice() {
    val activity = LocalContext.current as? Activity ?: return
    val current = AppLanguage.current(activity)
    val choices = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
        "en" to "English",
        "fr" to "Français",
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = { if (current != tag) AppLanguage.set(activity, tag) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun About() {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
    // Required verbatim, in English, by the TeslaMate trademark policy.
    Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium)
}
