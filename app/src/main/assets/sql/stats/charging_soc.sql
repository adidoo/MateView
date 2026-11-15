-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
SELECT start_date AS time, start_battery_level, end_battery_level
FROM charging_processes
WHERE car_id = $car_id AND $__timeFilter(start_date) AND end_date IS NOT NULL AND charge_energy_added > 0.01
ORDER BY start_date
