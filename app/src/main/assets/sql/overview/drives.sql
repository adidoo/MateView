-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  convert_km(sum(distance)::numeric, '$length_unit') AS distance,
  sum((start_${preferred_range}_range_km - end_${preferred_range}_range_km) * car.efficiency * 1000)
    / NULLIF(convert_km(sum(distance)::numeric, '$length_unit'), 0) AS consumption_net
FROM drives
JOIN cars car ON car.id = car_id
WHERE $__timeFilter(start_date) AND car_id = $car_id
