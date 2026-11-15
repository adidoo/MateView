-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Efficiency" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  (SELECT sum((d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency) / NULLIF(convert_km(sum(d.distance)::numeric, '$length_unit'), 0) * 1000
   FROM drives d JOIN cars car ON car.id = d.car_id
   WHERE d.distance IS NOT NULL AND (d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) >= 0.1
     AND $__timeFilter(d.start_date) AND d.car_id = $car_id) AS consumption_net,
  (SELECT convert_km(sum(distance)::numeric, '$length_unit') FROM drives
   WHERE car_id = $car_id AND $__timeFilter(start_date)) AS distance,
  (SELECT efficiency / convert_km(1, '$length_unit') * 1000 FROM cars WHERE id = $car_id) AS car_efficiency
