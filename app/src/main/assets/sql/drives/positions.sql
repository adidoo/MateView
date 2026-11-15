-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drives" and "Drive Details" Grafana dashboards of TeslaMate v4.4.0.
-- Thinned out to about 1200 points: a long drive logs one position per second.
WITH p AS (
  SELECT
    date, latitude, longitude, speed, power, battery_level,
    ${preferred_range}_battery_range_km AS range_km, elevation, outside_temp, inside_temp,
    row_number() OVER (ORDER BY date) AS n,
    count(*) OVER () AS total
  FROM positions
  WHERE drive_id = $drive_id AND car_id = $car_id
)
SELECT
  date AS time,
  latitude,
  longitude,
  convert_km(speed::numeric, '$length_unit') AS speed,
  power,
  battery_level,
  convert_km(range_km, '$length_unit') AS range,
  round(convert_m(elevation, '$alt_length_unit')) AS elevation,
  convert_celsius(outside_temp, '$temp_unit') AS outside_temp,
  convert_celsius(inside_temp, '$temp_unit') AS inside_temp
FROM p
WHERE n = 1 OR n = total OR n % greatest(1, total / 1200) = 0
ORDER BY date
