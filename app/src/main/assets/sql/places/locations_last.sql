-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Locations" Grafana dashboard of TeslaMate v4.4.0.
WITH locations AS (
  SELECT address_id, geofence_id, start_date AS end_date FROM charging_processes
  WHERE car_id = $car_id AND $__timeFilter(start_date)
  UNION
  SELECT end_address_id, end_geofence_id, end_date FROM drives
  WHERE car_id = $car_id AND $__timeFilter(end_date)
)
SELECT
  max(l.end_date) AS time,
  COALESCE(g.name, array_to_string((string_to_array(a.display_name, ', ', ''))[1:2], ', ')) AS address,
  COALESCE(a.city, a.neighbourhood) AS city,
  count(*) AS visits
FROM locations l
INNER JOIN addresses a ON l.address_id = a.id
LEFT JOIN geofences g ON l.geofence_id = g.id
GROUP BY 2, 3
ORDER BY 1 DESC
LIMIT 50
