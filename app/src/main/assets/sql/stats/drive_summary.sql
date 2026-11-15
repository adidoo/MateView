-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drive Stats" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  count(*) AS drives,
  convert_km(sum(d.distance)::numeric, '$length_unit') AS distance,
  sum((d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency) AS energy,
  convert_km((percentile_cont(0.5) WITHIN GROUP (ORDER BY d.distance))::numeric, '$length_unit') AS median_distance,
  convert_km(max(d.speed_max)::numeric, '$length_unit') AS speed_max,
  convert_km((max(d.end_km) - min(d.start_km))::numeric, '$length_unit') AS mileage,
  sum(d.duration_min) AS duration_min,
  min(d.start_date) AS first_drive
FROM drives d
JOIN cars car ON car.id = d.car_id
WHERE d.car_id = $car_id AND $__timeFilter(d.start_date) AND d.end_date IS NOT NULL
