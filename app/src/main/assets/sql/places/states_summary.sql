-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "States" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  (SELECT state::text FROM states WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS state,
  (SELECT start_date FROM states WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS since,
  (SELECT 1 - sum(duration_min) / NULLIF(extract(epoch FROM (max(end_date) - min(start_date))) / 60, 0)
    FROM drives WHERE car_id = $car_id) AS parked
