-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Trip" Grafana dashboard of TeslaMate v4.4.0.
-- The positions of every drive of the period, thinned out to about 1500 points.
WITH p AS (
  SELECT date, latitude, longitude, row_number() OVER (ORDER BY date) AS n, count(*) OVER () AS total
  FROM positions
  WHERE car_id = $car_id AND $__timeFilter(date) AND drive_id IS NOT NULL
)
SELECT date AS time, latitude, longitude
FROM p
WHERE n = 1 OR n = total OR n % greatest(1, total / 1500) = 0
ORDER BY date
