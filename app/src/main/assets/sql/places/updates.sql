-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Updates" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  start_date AS time,
  end_date,
  split_part(version, ' ', 1) AS version,
  extract(epoch FROM end_date - start_date) AS duration_s
FROM updates
WHERE car_id = $car_id AND $__timeFilter(start_date)
ORDER BY start_date DESC
