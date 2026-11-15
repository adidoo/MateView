-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Battery Health" Grafana dashboard of TeslaMate v4.4.0.
WITH data AS (
  SELECT
    cp.id,
    cp.charge_energy_added,
    CASE WHEN NULLIF(mode() WITHIN GROUP (ORDER BY charger_phases), 0) IS NULL THEN 'DC' ELSE 'AC' END AS current,
    cp.charge_energy_used
  FROM charging_processes cp
  RIGHT JOIN charges ON cp.id = charges.charging_process_id
  WHERE cp.car_id = $car_id AND cp.charge_energy_added > 0.01
  GROUP BY 1, 2
)
SELECT current AS kind, sum(greatest(charge_energy_added, charge_energy_used)) AS energy
FROM data
GROUP BY 1
ORDER BY 1
