-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
WITH data AS (
  SELECT
    cp.id,
    CASE WHEN NULLIF(mode() WITHIN GROUP (ORDER BY c.charger_phases), 0) IS NULL THEN 'DC' ELSE 'AC' END AS kind,
    greatest(cp.charge_energy_added, cp.charge_energy_used) AS energy,
    cp.duration_min,
    cp.cost
  FROM charging_processes cp
  JOIN charges c ON cp.id = c.charging_process_id
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date) AND cp.charge_energy_added > 0.01
  GROUP BY cp.id
)
SELECT kind, count(*) AS charges, sum(energy) AS energy, sum(duration_min) AS duration_min, sum(cost) AS cost
FROM data
GROUP BY 1
ORDER BY 1
