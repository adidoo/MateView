-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Trip" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date_bin('$bucket_s seconds'::interval, date, TIMESTAMP '2001-01-01') AS time,
  round(convert_m(avg(elevation), '$alt_length_unit')) AS elevation
FROM positions
WHERE car_id = $car_id AND $__timeFilter(date) AND elevation IS NOT NULL AND drive_id IS NOT NULL
GROUP BY 1
ORDER BY 1
