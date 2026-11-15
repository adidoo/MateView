// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui

import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.data.settings.ConnectionSettings
import adidoo.mateview.ui.settings.SettingsScreen
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Top-level destinations: a bottom bar on narrow windows, a side rail on wide ones. */
enum class TopDestination(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Outlined.Home),
    Drives(R.string.nav_drives, Icons.Outlined.DirectionsCar),
    Charges(R.string.nav_charges, Icons.Outlined.EvStation),
    Dashboards(R.string.nav_dashboards, Icons.Outlined.Dashboard),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

private const val RAIL_MIN_WIDTH_DP = 600

@Composable
fun MateViewApp() {
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val connection by container.settings.connection.collectAsStateWithLifecycle(initialValue = null)

    // The default keeps a bottom bar on a phone in landscape, where height is the
    // scarce dimension: switch to the side rail as soon as the window is wide.
    val layoutType = if (LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopDestination.entries.forEach { destination ->
                item(
                    selected = destination == current,
                    onClick = { current = destination },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        when (current) {
            TopDestination.Settings -> SettingsScreen()
            else -> PlaceholderScreen(
                title = stringResource(current.label),
                connection = connection,
                onOpenSettings = { current = TopDestination.Settings },
            )
        }
    }
}

/** Stands in for the screens that are not built yet, and leads to the settings until a connection works. */
@Composable
private fun PlaceholderScreen(title: String, connection: ConnectionSettings?, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        when {
            connection == null -> Unit
            !connection.verified -> {
                Text(stringResource(R.string.home_not_connected), textAlign = TextAlign.Center)
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.home_open_settings)) }
            }
            else -> {
                Text(stringResource(R.string.home_connected, connection.carName.orEmpty()))
                Text(stringResource(R.string.placeholder_not_built), textAlign = TextAlign.Center)
            }
        }
    }
}
