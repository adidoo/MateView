-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Written for MateView: one row per charge started in the period, for the daily energy bars.
SELECT start_date AS time, charge_energy_added
FROM charging_processes
WHERE car_id = $car_id AND $__timeFilter(start_date) AND charge_energy_added > 0
ORDER BY start_date
