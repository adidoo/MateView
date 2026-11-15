-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Temperatures" Grafana dashboard of TeslaMate v4.4.0.
-- Dates are stored in UTC; $tz_offset_s shifts them to the phone's time zone before grouping.
SELECT
  date_trunc('day', date + interval '$tz_offset_s seconds') - interval '$tz_offset_s seconds' AS time,
  convert_celsius(min(outside_temp), '$temp_unit') AS outside_min,
  convert_celsius(max(outside_temp), '$temp_unit') AS outside_max
FROM positions
WHERE car_id = $car_id AND outside_temp IS NOT NULL AND $__timeFilter(date)
GROUP BY 1
ORDER BY 1
