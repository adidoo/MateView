-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
-- Median power at each battery level, over every DC charge of the period.
SELECT
  c.battery_level AS soc,
  percentile_cont(0.5) WITHIN GROUP (ORDER BY c.charger_power) AS power,
  count(DISTINCT c.charging_process_id) AS charges
FROM charges c
JOIN charging_processes cp ON cp.id = c.charging_process_id
WHERE cp.car_id = $car_id AND $__timeFilter(c.date) AND c.charger_power > 0 AND c.fast_charger_present
GROUP BY c.battery_level
ORDER BY 1
