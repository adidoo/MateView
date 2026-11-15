-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Locations" Grafana dashboard of TeslaMate v4.4.0.
SELECT city, count(*) AS addresses
FROM addresses
WHERE city IS NOT NULL AND id IN (
  SELECT start_address_id FROM drives WHERE car_id = $car_id AND $__timeFilter(start_date)
  UNION
  SELECT end_address_id FROM drives WHERE car_id = $car_id AND $__timeFilter(end_date)
  UNION
  SELECT address_id FROM charging_processes WHERE car_id = $car_id AND $__timeFilter(start_date)
)
GROUP BY 1
ORDER BY 2 DESC
LIMIT 10
