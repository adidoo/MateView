-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drives" and "Drive Details" Grafana dashboards of TeslaMate v4.4.0.
SELECT
  speed_section AS speed,
  sum(seconds_elapsed) AS seconds
FROM (
  SELECT
    round(convert_km(p.speed::numeric, '$length_unit') / 10, 0) * 10 AS speed_section,
    extract(epoch FROM (lead(p.date) OVER (ORDER BY p.date) - p.date)) AS seconds_elapsed
  FROM drives d
  INNER JOIN positions p ON p.drive_id = d.id
  WHERE d.id = $drive_id AND d.car_id = $car_id
) AS drive_data
WHERE speed_section > 0 AND seconds_elapsed IS NOT NULL
GROUP BY 1
ORDER BY 1
