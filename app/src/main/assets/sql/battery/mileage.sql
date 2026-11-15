-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Projected Range" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
  convert_km(avg(odometer)::numeric, '$length_unit') AS odometer
FROM positions
WHERE $__timeFilter(date) AND car_id = $car_id AND ideal_battery_range_km IS NOT NULL
GROUP BY 1
ORDER BY 1
