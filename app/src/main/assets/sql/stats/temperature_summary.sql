-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Temperatures" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  convert_celsius(min(outside_temp), '$temp_unit') AS outside_min,
  convert_celsius(avg(outside_temp), '$temp_unit') AS outside_avg,
  convert_celsius(max(outside_temp), '$temp_unit') AS outside_max,
  convert_celsius(min(inside_temp), '$temp_unit') AS inside_min,
  convert_celsius(avg(inside_temp), '$temp_unit') AS inside_avg,
  convert_celsius(max(inside_temp), '$temp_unit') AS inside_max
FROM positions
WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL AND $__timeFilter(date)
