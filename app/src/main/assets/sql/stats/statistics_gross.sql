-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Statistics" Grafana dashboard of TeslaMate v4.4.0.
-- Gross consumption per period: every loss of range between two events (driving, standing, but
-- not charging), over the distance driven.
-- Dates are stored in UTC; $tz_offset_s shifts them to the phone's time zone before grouping.
WITH events AS (
  SELECT start_date AS date, start_${preferred_range}_range_km AS range_km, start_km AS odometer,
    true AS drive_start, distance IS NULL AS is_incomplete
  FROM drives
  WHERE car_id = $car_id AND $__timeFilter(start_date)
  UNION ALL
  SELECT coalesce(end_date, start_date + interval '1 second'), end_${preferred_range}_range_km, end_km,
    false, distance IS NULL
  FROM drives
  WHERE car_id = $car_id AND $__timeFilter(start_date)
  UNION ALL
  SELECT cp.start_date, cp.start_${preferred_range}_range_km, p.odometer, false, cp.end_date IS NULL
  FROM charging_processes cp
  JOIN positions p ON p.id = cp.position_id
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date)
  UNION ALL
  SELECT coalesce(cp.end_date, cp.start_date + interval '1 second'), cp.end_${preferred_range}_range_km, p.odometer,
    false, cp.end_date IS NULL
  FROM charging_processes cp
  JOIN positions p ON p.id = cp.position_id
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date)
),
steps AS (
  SELECT
    date_trunc('$period', date + interval '$tz_offset_s seconds') AS p,
    CASE WHEN is_incomplete THEN 0 ELSE lead(odometer) OVER w - odometer END AS distance,
    CASE WHEN is_incomplete THEN 0
         WHEN drive_start THEN range_km - lead(range_km) OVER w
         ELSE greatest(range_km - lead(range_km) OVER w, 0) END AS range_loss
  FROM events
  WINDOW w AS (ORDER BY date)
)
SELECT
  s.p AS time,
  sum(s.range_loss) * c.efficiency AS energy_gross,
  sum(s.range_loss) * c.efficiency * 1000 / NULLIF(convert_km(sum(s.distance)::numeric, '$length_unit'), 0) AS consumption_gross
FROM steps s
JOIN cars c ON c.id = $car_id
GROUP BY s.p, c.efficiency
ORDER BY 1 DESC
