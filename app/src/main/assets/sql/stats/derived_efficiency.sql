-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Efficiency" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  round((charge_energy_added / NULLIF(end_${preferred_range}_range_km - start_${preferred_range}_range_km, 0))::numeric
    / convert_km(1, '$length_unit'), 3) * 1000 AS efficiency,
  count(*) AS count
FROM charging_processes
WHERE car_id = $car_id
  AND duration_min > 10
  AND end_battery_level <= 95
  AND start_${preferred_range}_range_km IS NOT NULL
  AND end_${preferred_range}_range_km IS NOT NULL
  AND charge_energy_added > 0
GROUP BY 1
ORDER BY 2 DESC
LIMIT 3
