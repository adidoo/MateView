-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Projected Range" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
  convert_km((sum(${preferred_range}_battery_range_km)
    / nullif(sum(coalesce(usable_battery_level, battery_level)), 0) * 100)::numeric, '$length_unit') AS projected_range,
  avg(battery_level) AS battery_level,
  convert_celsius(avg(outside_temp), '$temp_unit') AS outside_temp
FROM (
  SELECT battery_level, usable_battery_level, date, rated_battery_range_km, ideal_battery_range_km, outside_temp
  FROM positions
  WHERE car_id = $car_id AND $__timeFilter(date) AND ideal_battery_range_km IS NOT NULL
  UNION ALL
  SELECT c.battery_level, coalesce(c.usable_battery_level, c.battery_level), c.date,
         c.rated_battery_range_km, c.ideal_battery_range_km, c.outside_temp
  FROM charges c
  JOIN charging_processes p ON p.id = c.charging_process_id
  WHERE $__timeFilter(c.date) AND p.car_id = $car_id
) AS data
GROUP BY 1
ORDER BY 1
