-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Battery Health" Grafana dashboard of TeslaMate v4.4.0.
-- One point per half month: the median capacity derived from the charges of that period.
WITH aux AS (
  SELECT car_id, COALESCE(derived_efficiency, car_efficiency) AS efficiency
  FROM (
    SELECT
      ROUND((charge_energy_added / NULLIF(end_rated_range_km - start_rated_range_km, 0))::numeric, 3) * 100 AS derived_efficiency,
      COUNT(*) AS count,
      cars.id AS car_id,
      cars.efficiency * 100 AS car_efficiency
    FROM cars
    LEFT JOIN charging_processes ON
      cars.id = charging_processes.car_id
      AND duration_min > 10
      AND end_battery_level <= 95
      AND start_rated_range_km IS NOT NULL
      AND end_rated_range_km IS NOT NULL
      AND charge_energy_added > 0
    WHERE cars.id = $car_id
    GROUP BY 1, 3, 4
    ORDER BY 2 DESC
    LIMIT 1
  ) AS efficiency
)
SELECT
  round(min(convert_km(p.odometer::numeric, '$length_unit')), 0) AS odometer,
  round(percentile_cont(0.5) WITHIN GROUP (
    ORDER BY c.rated_battery_range_km * aux.efficiency / c.usable_battery_level)::numeric, 1) AS capacity,
  min(cp.end_date) AS time
FROM charging_processes cp
JOIN (
  SELECT charging_process_id, MAX(date) AS date FROM charges WHERE usable_battery_level > 0 GROUP BY charging_process_id
) AS last_charges ON cp.id = last_charges.charging_process_id
INNER JOIN charges c ON c.charging_process_id = cp.id AND c.date = last_charges.date
INNER JOIN positions p ON p.id = cp.position_id
INNER JOIN aux ON aux.car_id = cp.car_id
WHERE cp.car_id = $car_id AND cp.end_date IS NOT NULL AND cp.charge_energy_added >= aux.efficiency
GROUP BY to_char(cp.end_date, 'YYYYMM') || CASE WHEN extract(day FROM cp.end_date) <= 15 THEN '1' ELSE '2' END
ORDER BY 1
