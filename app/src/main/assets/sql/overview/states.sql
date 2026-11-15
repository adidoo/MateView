-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
-- One row per state change. 0 = end of a drive, charge or update, 1 = driving, 2 = charging,
-- 3 = offline, 4 = asleep, 5 = online, 6 = updating.
WITH states AS (
  SELECT
    unnest(ARRAY [start_date + interval '1 second', end_date]) AS date,
    unnest(ARRAY [2, 0]) AS state
  FROM charging_processes
  WHERE car_id = $car_id
    AND ($__timeFrom() :: timestamp - interval '30 day') < start_date
    AND (end_date < ($__timeTo() :: timestamp + interval '30 day') OR end_date IS NULL)
  UNION
  SELECT
    unnest(ARRAY [start_date + interval '1 second', end_date]) AS date,
    unnest(ARRAY [1, 0]) AS state
  FROM drives
  WHERE car_id = $car_id
    AND ($__timeFrom() :: timestamp - interval '30 day') < start_date
    AND (end_date < ($__timeTo() :: timestamp + interval '30 day') OR end_date IS NULL)
  UNION
  SELECT
    start_date AS date,
    CASE
      WHEN state = 'offline' THEN 3
      WHEN state = 'asleep' THEN 4
      WHEN state = 'online' THEN 5
    END AS state
  FROM states
  WHERE car_id = $car_id
    AND ($__timeFrom() :: timestamp - interval '30 day') < start_date
    AND (end_date < ($__timeTo() :: timestamp + interval '30 day') OR end_date IS NULL)
  UNION
  SELECT
    unnest(ARRAY [start_date + interval '1 second', end_date]) AS date,
    unnest(ARRAY [6, 0]) AS state
  FROM updates
  WHERE car_id = $car_id
    AND ($__timeFrom() :: timestamp - interval '30 day') < start_date
    AND (end_date < ($__timeTo() :: timestamp + interval '30 day') OR end_date IS NULL)
)
SELECT date AS "time", state
FROM states
WHERE date IS NOT NULL
  AND ($__timeFrom() :: timestamp - interval '30 day') < date
  AND date < ($__timeTo() :: timestamp + interval '30 day')
ORDER BY date ASC, state ASC
