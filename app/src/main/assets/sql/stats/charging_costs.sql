-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
WITH typed AS (
  SELECT
    cp.id,
    cp.cost,
    greatest(cp.charge_energy_added, cp.charge_energy_used) AS energy,
    NULLIF(mode() WITHIN GROUP (ORDER BY c.charger_phases), 0) IS NULL AS is_dc,
    mode() WITHIN GROUP (ORDER BY c.fast_charger_brand) AS brand,
    cp.address_id,
    cp.geofence_id
  FROM charging_processes cp
  JOIN charges c ON c.charging_process_id = cp.id
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date) AND cp.charge_energy_added > 0.01
  GROUP BY cp.id
)
SELECT
  sum(t.cost) FILTER (WHERE t.is_dc AND (a.name ILIKE '%supercharger%' OR g.name ILIKE '%supercharger%' OR t.brand = 'Tesla')) AS supercharger_cost,
  sum(t.cost) FILTER (WHERE t.is_dc) / NULLIF(sum(t.energy) FILTER (WHERE t.is_dc AND t.cost IS NOT NULL), 0) AS cost_per_kwh_dc,
  sum(t.cost) FILTER (WHERE NOT t.is_dc) / NULLIF(sum(t.energy) FILTER (WHERE NOT t.is_dc AND t.cost IS NOT NULL), 0) AS cost_per_kwh_ac
FROM typed t
LEFT JOIN addresses a ON a.id = t.address_id
LEFT JOIN geofences g ON g.id = t.geofence_id
