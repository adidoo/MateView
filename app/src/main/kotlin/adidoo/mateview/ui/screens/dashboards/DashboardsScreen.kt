// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.screens.dashboards

import adidoo.mateview.MateViewApplication
import adidoo.mateview.R
import adidoo.mateview.ui.components.NotConnectedMessage
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class DashboardGroup(@StringRes val label: Int) {
    BATTERY(R.string.group_battery),
    STATISTICS(R.string.group_statistics),
    PLACES(R.string.group_places),
    SYSTEM(R.string.group_system),
}

/** The dashboards of TeslaMate beyond the three main tabs; [built] ones can be opened. */
enum class Dashboard(val group: DashboardGroup, @StringRes val label: Int, val icon: ImageVector, val built: Boolean = false) {
    BATTERY_HEALTH(DashboardGroup.BATTERY, R.string.dash_battery_health, Icons.Outlined.BatteryFull, true),
    CHARGE_LEVEL(DashboardGroup.BATTERY, R.string.dash_charge_level, Icons.Outlined.BatteryChargingFull, true),
    PROJECTED_RANGE(DashboardGroup.BATTERY, R.string.dash_projected_range, Icons.Outlined.ShowChart, true),
    VAMPIRE_DRAIN(DashboardGroup.BATTERY, R.string.dash_vampire_drain, Icons.Outlined.Bolt, true),
    STATISTICS(DashboardGroup.STATISTICS, R.string.dash_statistics, Icons.Outlined.BarChart, true),
    DRIVE_STATS(DashboardGroup.STATISTICS, R.string.dash_drive_stats, Icons.Outlined.DirectionsCar, true),
    CHARGING_STATS(DashboardGroup.STATISTICS, R.string.dash_charging_stats, Icons.Outlined.EvStation, true),
    EFFICIENCY(DashboardGroup.STATISTICS, R.string.dash_efficiency, Icons.Outlined.Speed, true),
    MILEAGE(DashboardGroup.STATISTICS, R.string.dash_mileage, Icons.Outlined.ShowChart, true),
    TEMPERATURES(DashboardGroup.STATISTICS, R.string.dash_temperatures, Icons.Outlined.DeviceThermostat, true),
    TIMELINE(DashboardGroup.PLACES, R.string.dash_timeline, Icons.Outlined.Timeline),
    TRIP(DashboardGroup.PLACES, R.string.dash_trip, Icons.Outlined.Map),
    STATES(DashboardGroup.PLACES, R.string.dash_states, Icons.Outlined.Settings),
    UPDATES(DashboardGroup.PLACES, R.string.dash_updates, Icons.Outlined.Update),
    LOCATIONS(DashboardGroup.PLACES, R.string.dash_locations, Icons.Outlined.Place),
    VISITED(DashboardGroup.PLACES, R.string.dash_visited, Icons.Outlined.Public),
    DATABASE(DashboardGroup.SYSTEM, R.string.dash_database, Icons.Outlined.Storage),
}

@Composable
fun DashboardsScreen(onOpenSettings: () -> Unit) {
    val container = (LocalContext.current.applicationContext as MateViewApplication).container
    val connection by container.settings.connection.collectAsStateWithLifecycle(initialValue = null)
    var open by rememberSaveable { mutableStateOf<Dashboard?>(null) }

    val settings = connection ?: return
    val carId = settings.carId
    if (!settings.verified || carId == null) {
        return Box(Modifier.safeDrawingPadding()) { NotConnectedMessage(onOpenSettings) }
    }

    val current = open
    if (current == null) {
        DashboardIndex(onOpen = { open = it })
    } else {
        BackHandler { open = null }
        val back = { open = null }
        when (current) {
            Dashboard.BATTERY_HEALTH -> BatteryHealthScreen(container, carId, back)
            Dashboard.CHARGE_LEVEL -> ChargeLevelScreen(container, carId, back)
            Dashboard.PROJECTED_RANGE -> ProjectedRangeScreen(container, carId, back)
            Dashboard.VAMPIRE_DRAIN -> VampireDrainScreen(container, carId, back)
            Dashboard.STATISTICS -> StatisticsScreen(container, carId, back)
            Dashboard.DRIVE_STATS -> DriveStatsScreen(container, carId, back)
            Dashboard.CHARGING_STATS -> ChargingStatsScreen(container, carId, back)
            Dashboard.EFFICIENCY -> EfficiencyScreen(container, carId, back)
            Dashboard.MILEAGE -> MileageScreen(container, carId, back)
            Dashboard.TEMPERATURES -> TemperaturesScreen(container, carId, back)
            else -> open = null
        }
    }
}

@Composable
private fun DashboardIndex(onOpen: (Dashboard) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.nav_dashboards), style = MaterialTheme.typography.headlineSmall)
        }
        DashboardGroup.entries.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(group.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(Dashboard.entries.filter { it.group == group }, key = { it.name }) { dashboard ->
                DashboardCard(dashboard) { onOpen(dashboard) }
            }
        }
    }
}

@Composable
private fun DashboardCard(dashboard: Dashboard, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(
        onClick = onClick,
        enabled = dashboard.built,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(dashboard.icon, contentDescription = null, tint = if (dashboard.built) MaterialTheme.colorScheme.primary else muted)
            Column {
                Text(
                    stringResource(dashboard.label),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dashboard.built) {
                    Text(stringResource(R.string.dash_coming), style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}
