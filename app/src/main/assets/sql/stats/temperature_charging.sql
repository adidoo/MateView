-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Temperatures" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  start_date AS time,
  convert_celsius(outside_temp_avg, '$temp_unit') AS outside_temp
FROM charging_processes
WHERE car_id = $car_id AND outside_temp_avg IS NOT NULL AND $__timeFilter(start_date)
ORDER BY 1
