-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
-- Averaged into $bucket_s-second buckets: positions are logged every few seconds while driving.
SELECT
  date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
  avg(battery_level) AS soc
FROM (
  SELECT battery_level, date
  FROM positions
  WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL AND $__timeFilter(date)
  UNION ALL
  SELECT c.battery_level, c.date
  FROM charges c
  JOIN charging_processes p ON p.id = c.charging_process_id
  WHERE $__timeFilter(c.date) AND p.car_id = $car_id
) AS data
GROUP BY 1
ORDER BY 1
