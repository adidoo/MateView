-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Battery Health" Grafana dashboard of TeslaMate v4.4.0.
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
),
current_capacity AS (
  SELECT AVG(capacity) AS capacity
  FROM (
    SELECT c.rated_battery_range_km * aux.efficiency / c.usable_battery_level AS capacity
    FROM charging_processes cp
    INNER JOIN charges c ON c.charging_process_id = cp.id
    INNER JOIN aux ON cp.car_id = aux.car_id
    WHERE cp.car_id = $car_id
      AND cp.end_date IS NOT NULL
      AND cp.charge_energy_added >= aux.efficiency
      AND c.usable_battery_level > 0
    ORDER BY cp.end_date DESC, c.date DESC
    LIMIT 100
  ) AS last_charges
),
max_capacity AS (
  SELECT MAX(c.rated_battery_range_km * aux.efficiency / c.usable_battery_level) AS capacity
  FROM charging_processes cp
  INNER JOIN (
    SELECT charging_process_id, MAX(date) AS date FROM charges WHERE usable_battery_level > 0 GROUP BY charging_process_id
  ) AS gcharges ON cp.id = gcharges.charging_process_id
  INNER JOIN charges c ON c.charging_process_id = cp.id AND c.date = gcharges.date
  INNER JOIN aux ON cp.car_id = aux.car_id
  WHERE cp.car_id = $car_id AND cp.end_date IS NOT NULL AND cp.charge_energy_added >= aux.efficiency
),
latest AS (
  SELECT date, range_km, usable_battery_level
  FROM (
    (SELECT date, ${preferred_range}_battery_range_km AS range_km, usable_battery_level
     FROM positions
     WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL AND usable_battery_level > 0
     ORDER BY date DESC LIMIT 1)
    UNION ALL
    (SELECT c.date, c.${preferred_range}_battery_range_km AS range_km, c.usable_battery_level
     FROM charges c
     INNER JOIN charging_processes p ON p.id = c.charging_process_id
     WHERE p.car_id = $car_id AND c.usable_battery_level > 0
     ORDER BY c.date DESC LIMIT 1)
  ) AS data
  ORDER BY date DESC
  LIMIT 1
),
max_range AS (
  SELECT
    CASE WHEN sum(usable_battery_level) = 0 THEN sum(range_km) * 100
         ELSE sum(range_km) / sum(usable_battery_level) * 100 END AS range_km
  FROM (
    SELECT c.usable_battery_level, c.date, c.${preferred_range}_battery_range_km AS range_km
    FROM charges c
    INNER JOIN charging_processes p ON p.id = c.charging_process_id
    WHERE p.car_id = $car_id AND c.usable_battery_level IS NOT NULL
  ) AS data
  GROUP BY floor(extract(epoch FROM date) / 86400)
  ORDER BY 1 DESC
  LIMIT 1
),
charging AS (
  SELECT
    count(*) AS charges,
    sum(charge_energy_added) AS energy_added,
    sum(greatest(charge_energy_added, charge_energy_used)) AS energy_used
  FROM charging_processes
  WHERE car_id = $car_id AND charge_energy_added > 0.01
),
driving AS (
  SELECT
    convert_km(sum(distance)::numeric, '$length_unit') AS logged,
    convert_km((max(end_km) - min(start_km))::numeric, '$length_unit') AS mileage,
    convert_km(max(end_km)::numeric, '$length_unit') AS odometer
  FROM drives
  WHERE car_id = $car_id
),
battery AS (
  SELECT lfp_battery
  FROM cars INNER JOIN car_settings ON cars.settings_id = car_settings.id
  WHERE cars.id = $car_id
)
SELECT
  max_capacity.capacity AS max_capacity,
  current_capacity.capacity AS current_capacity,
  convert_km(max_range.range_km::numeric, '$length_unit') AS max_range,
  convert_km((latest.range_km * 100.0 / latest.usable_battery_level)::numeric, '$length_unit') AS current_range,
  latest.usable_battery_level AS usable_battery_level,
  aux.efficiency * 10 / convert_km(1, '$length_unit') AS rated_efficiency,
  charging.charges,
  charging.energy_added,
  charging.energy_used,
  driving.logged,
  driving.mileage,
  driving.odometer,
  battery.lfp_battery
FROM (SELECT NULL) AS base
LEFT JOIN aux ON true
LEFT JOIN max_capacity ON true
LEFT JOIN current_capacity ON true
LEFT JOIN max_range ON true
LEFT JOIN latest ON true
LEFT JOIN charging ON true
LEFT JOIN driving ON true
LEFT JOIN battery ON true
