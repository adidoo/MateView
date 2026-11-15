-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drive Stats" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  COALESCE(g.name, COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), ''))) AS name,
  count(*) AS visited
FROM drives t
INNER JOIN addresses a ON t.end_address_id = a.id
LEFT JOIN geofences g ON t.end_geofence_id = g.id
WHERE t.car_id = $car_id AND $__timeFilter(t.start_date)
GROUP BY 1
HAVING COALESCE(g.name, COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), ''))) IS NOT NULL
ORDER BY visited DESC
LIMIT 10
