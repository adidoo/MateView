-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  date_bin('$bucket_s seconds'::interval, c.date, TIMESTAMP '2001-01-01') AS time,
  avg(c.charger_power) AS charger_power,
  max(c.charge_energy_added) AS charge_energy_added
FROM charges c
JOIN charging_processes p ON p.id = c.charging_process_id
WHERE $__timeFilter(c.date) AND p.car_id = $car_id
GROUP BY 1
ORDER BY 1
