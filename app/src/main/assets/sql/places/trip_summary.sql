-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Trip" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  (SELECT count(*) FROM drives d WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)) AS drives,
  (SELECT convert_km(sum(d.distance)::numeric, '$length_unit') FROM drives d
    WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)) AS distance,
  (SELECT sum(d.duration_min) FROM drives d WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)) AS drive_min,
  (SELECT sum((d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency) FROM drives d JOIN cars car ON car.id = d.car_id
    WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)) AS energy,
  (SELECT count(*) FROM charging_processes WHERE car_id = $car_id AND $__timeFilter(start_date)
    AND charge_energy_added > 0.01) AS charges,
  (SELECT sum(charge_energy_added) FROM charging_processes WHERE car_id = $car_id AND $__timeFilter(start_date)) AS energy_added,
  (SELECT sum(duration_min) FROM charging_processes WHERE car_id = $car_id AND $__timeFilter(start_date)) AS charge_min,
  (SELECT sum(cost) FROM charging_processes WHERE car_id = $car_id AND $__timeFilter(start_date)) AS cost
