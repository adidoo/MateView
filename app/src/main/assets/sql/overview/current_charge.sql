-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Written for MateView: the samples of the charge in progress, thinned out to about 600 points.
WITH s AS (
  SELECT c.date, c.charger_power, c.charge_energy_added, c.battery_level,
         row_number() OVER (ORDER BY c.date) AS n, count(*) OVER () AS total
  FROM charges c
  WHERE c.charging_process_id = (
    SELECT id FROM charging_processes
    WHERE car_id = $car_id AND end_date IS NULL
    ORDER BY start_date DESC LIMIT 1)
)
SELECT date AS time, charger_power, charge_energy_added, battery_level
FROM s
WHERE n = 1 OR n = total OR n % greatest(1, total / 600) = 0
ORDER BY date
