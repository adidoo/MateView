-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date,
  convert_celsius(driver_temp_setting, '$temp_unit') AS driver_temp,
  convert_celsius(inside_temp, '$temp_unit') AS inside_temp,
  convert_celsius(outside_temp, '$temp_unit') AS outside_temp
FROM positions
WHERE car_id = $car_id
  AND ideal_battery_range_km IS NOT NULL
  AND (inside_temp IS NOT NULL OR outside_temp IS NOT NULL)
  AND date >= (TIMEZONE('UTC', NOW()) - INTERVAL '60m')
ORDER BY date DESC
LIMIT 1
