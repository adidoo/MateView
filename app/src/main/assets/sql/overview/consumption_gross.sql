-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
WITH d AS (
  SELECT
    c.car_id,
    lag(end_${preferred_range}_range_km) OVER (ORDER BY start_date) - start_${preferred_range}_range_km AS range_loss,
    p.odometer - lag(p.odometer) OVER (ORDER BY start_date) AS distance
  FROM charging_processes c
  LEFT JOIN positions p ON p.id = c.position_id
  WHERE end_date IS NOT NULL AND c.car_id = $car_id AND $__timeFilter(start_date)
  ORDER BY start_date
),
range_loss_between_charges AS (
  SELECT sum(range_loss) AS range_loss
  FROM d
  WHERE distance >= 0 AND range_loss >= 0
  GROUP BY car_id
),
charge_dates AS (
  SELECT min(start_date) AS first_charge, max(end_date) AS last_charge
  FROM charging_processes
  WHERE end_date IS NOT NULL AND car_id = $car_id AND $__timeFilter(start_date)
),
range_loss_before_first_charge AS (
  SELECT max(${preferred_range}_battery_range_km) - min(${preferred_range}_battery_range_km) AS range_loss
  FROM positions, charge_dates
  WHERE car_id = $car_id AND $__timeFilter(date)
    AND ((SELECT first_charge FROM charge_dates) IS NULL OR date < (SELECT first_charge FROM charge_dates))
),
range_loss_after_last_charge AS (
  SELECT max(${preferred_range}_battery_range_km) - min(${preferred_range}_battery_range_km) AS range_loss
  FROM positions, charge_dates
  WHERE car_id = $car_id AND $__timeFilter(date) AND date > (SELECT last_charge FROM charge_dates)
),
total_range_loss AS (
  SELECT sum(range_loss) AS range_loss
  FROM (
    SELECT range_loss FROM range_loss_between_charges
    UNION ALL
    SELECT range_loss FROM range_loss_before_first_charge
    UNION ALL
    SELECT range_loss FROM range_loss_after_last_charge
  ) r
),
distance AS (
  SELECT max(odometer) - min(odometer) AS distance
  FROM positions
  WHERE car_id = $car_id AND $__timeFilter(date)
)
SELECT
  NULLIF(range_loss, 0) * (c.efficiency * 1000) / convert_km(NULLIF(distance::numeric, 0), '$length_unit') AS consumption_gross
FROM total_range_loss, distance
LEFT JOIN cars c ON c.id = $car_id
