-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
WITH charging_process AS (
  SELECT id, end_date
  FROM charging_processes
  WHERE car_id = $car_id
  ORDER BY start_date DESC
  LIMIT 1
)
SELECT
  c.date,
  (charging_process.end_date IS NULL) AS charging,
  c.charger_power,
  CASE WHEN c.fast_charger_present IS NOT TRUE THEN c.charger_voltage END AS charger_voltage
FROM charges c, charging_process
WHERE charging_process.id = c.charging_process_id
ORDER BY c.date DESC
LIMIT 1
