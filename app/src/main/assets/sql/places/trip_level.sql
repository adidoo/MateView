-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Trip" Grafana dashboard of TeslaMate v4.4.0.
-- Battery level and range, from the positions and, while charging, from the charge samples.
SELECT time, avg(battery_level) AS battery_level, convert_km(avg(range_km)::numeric, '$length_unit') AS range
FROM (
  SELECT date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
    battery_level, ${preferred_range}_battery_range_km AS range_km
  FROM positions
  WHERE car_id = $car_id AND $__timeFilter(date) AND ideal_battery_range_km IS NOT NULL
  UNION ALL
  SELECT date_bin('$bucket_s seconds'::interval, c.date, TIMESTAMP '2001-01-01'),
    c.battery_level, c.${preferred_range}_battery_range_km
  FROM charges c
  JOIN charging_processes cp ON cp.id = c.charging_process_id
  WHERE cp.car_id = $car_id AND $__timeFilter(c.date)
) samples
GROUP BY 1
ORDER BY 1
