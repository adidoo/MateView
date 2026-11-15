-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  COALESCE(g.name, CONCAT_WS(', ',
    COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), '')),
    COALESCE(a.city, a.neighbourhood))) AS location,
  count(*) AS charges,
  sum(cp.charge_energy_added) AS energy,
  sum(cp.cost) AS cost
FROM charging_processes cp
LEFT JOIN addresses a ON a.id = cp.address_id
LEFT JOIN geofences g ON g.id = cp.geofence_id
WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date) AND cp.charge_energy_added > 0.01
GROUP BY 1
ORDER BY energy DESC
LIMIT 10
