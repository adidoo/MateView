-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  count(*) AS charges,
  sum(charge_energy_added) AS energy_added,
  sum(greatest(charge_energy_added, charge_energy_used)) AS energy_used,
  sum(cost) AS cost,
  sum(duration_min) AS duration_min,
  (SELECT convert_km(sum(distance)::numeric, '$length_unit') FROM drives
    WHERE car_id = $car_id AND $__timeFilter(start_date)) AS distance
FROM charging_processes
WHERE car_id = $car_id AND $__timeFilter(start_date) AND charge_energy_added > 0.01
