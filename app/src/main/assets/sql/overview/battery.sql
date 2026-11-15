-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
SELECT battery_level, convert_km(range_km::numeric, '$length_unit') AS range, date
FROM (
  (SELECT battery_level, ${preferred_range}_battery_range_km AS range_km, date
   FROM positions
   WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL
   ORDER BY date DESC LIMIT 1)
  UNION ALL
  (SELECT c.battery_level, c.${preferred_range}_battery_range_km AS range_km, c.date
   FROM charges c
   JOIN charging_processes p ON p.id = c.charging_process_id
   WHERE p.car_id = $car_id
   ORDER BY c.date DESC LIMIT 1)
) AS data
ORDER BY date DESC
LIMIT 1
