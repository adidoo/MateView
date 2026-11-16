-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Temperatures" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
  convert_celsius(avg(inside_temp), '$temp_unit') AS inside_temp,
  convert_celsius(avg(outside_temp), '$temp_unit') AS outside_temp,
  convert_celsius(avg(driver_temp_setting), '$temp_unit') AS setpoint
FROM positions
WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL
  AND (inside_temp IS NOT NULL OR outside_temp IS NOT NULL) AND $__timeFilter(date)
GROUP BY 1
ORDER BY 1
