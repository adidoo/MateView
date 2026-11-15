-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drive Stats" Grafana dashboard of TeslaMate v4.4.0.
WITH drive_data AS (
  SELECT
    round(convert_km(p.speed::numeric, '$length_unit') / 10, 0) * 10 AS speed_section,
    extract(epoch FROM (lead(p.date) OVER (PARTITION BY p.drive_id ORDER BY p.date) - p.date)) AS seconds_elapsed
  FROM positions p
  WHERE p.car_id = $car_id AND $__timeFilter(p.date) AND p.drive_id IS NOT NULL
)
SELECT speed_section AS speed, sum(seconds_elapsed) AS seconds
FROM drive_data
WHERE speed_section > 0 AND seconds_elapsed IS NOT NULL
GROUP BY 1
ORDER BY 1
