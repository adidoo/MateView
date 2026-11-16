-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Visited" Grafana dashboard of TeslaMate v4.4.0.
-- Dates are stored in UTC; $tz_offset_s shifts them to the phone's time zone before grouping.
WITH visits AS (
  SELECT v.address_id, v.ts
  FROM drives d
  CROSS JOIN LATERAL (VALUES (d.start_address_id, d.start_date), (d.end_address_id, d.end_date)) AS v(address_id, ts)
  WHERE d.car_id = $car_id AND $__timeFilter(d.start_date) AND d.end_date IS NOT NULL
  UNION ALL
  SELECT cp.address_id, cp.start_date
  FROM charging_processes cp
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date)
)
SELECT
  a.country,
  count(DISTINCT COALESCE(a.city, a.neighbourhood)) AS cities,
  count(DISTINCT (v.ts + interval '$tz_offset_s seconds')::date) AS days,
  min(v.ts) AS first_visit,
  max(v.ts) AS last_visit
FROM visits v
JOIN addresses a ON a.id = v.address_id
WHERE a.country IS NOT NULL AND COALESCE(a.city, a.neighbourhood) IS NOT NULL
GROUP BY 1
ORDER BY days DESC, last_visit DESC
