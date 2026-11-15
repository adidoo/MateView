-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Vampire Drain" Grafana dashboard of TeslaMate v4.4.0.
-- Idle periods of at least one hour between drives and charges; the app filters longer ones itself.
WITH merge AS (
  SELECT
    c.start_date, c.end_date,
    c.start_${preferred_range}_range_km AS start_range_km, c.end_${preferred_range}_range_km AS end_range_km,
    c.start_battery_level, c.end_battery_level,
    p.usable_battery_level AS start_usable_battery_level, NULL::smallint AS end_usable_battery_level,
    p.odometer AS start_km, p.odometer AS end_km
  FROM charging_processes c
  JOIN positions p ON c.position_id = p.id
  WHERE c.car_id = $car_id AND $__timeFilter(c.start_date)
  UNION
  SELECT
    d.start_date, d.end_date,
    d.start_${preferred_range}_range_km, d.end_${preferred_range}_range_km,
    start_position.battery_level, end_position.battery_level,
    start_position.usable_battery_level, end_position.usable_battery_level,
    d.start_km, d.end_km
  FROM drives d
  JOIN positions start_position ON d.start_position_id = start_position.id
  JOIN positions end_position ON d.end_position_id = end_position.id
  WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)
),
v AS (
  SELECT
    lag(t.end_date) OVER w AS start_date,
    t.start_date AS end_date,
    lag(t.end_range_km) OVER w AS start_range,
    t.start_range_km AS end_range,
    lag(t.end_km) OVER w AS start_km,
    t.start_km AS end_km,
    EXTRACT(EPOCH FROM (t.start_date - lag(t.end_date) OVER w)) AS duration,
    lag(t.end_battery_level) OVER w AS start_battery_level,
    start_battery_level AS end_battery_level,
    start_battery_level > COALESCE(start_usable_battery_level, start_battery_level) AS has_reduced_range
  FROM merge t
  WINDOW w AS (ORDER BY t.start_date ASC)
)
SELECT
  v.start_date,
  v.end_date,
  v.duration,
  coalesce(s_standby.sleep, 0) / v.duration AS standby,
  v.start_battery_level,
  v.end_battery_level,
  has_reduced_range,
  convert_km(CASE WHEN has_reduced_range THEN NULL ELSE (v.start_range - v.end_range)::numeric END, '$length_unit') AS range_loss,
  CASE WHEN has_reduced_range THEN NULL ELSE (v.start_range - v.end_range) * c.efficiency END AS energy,
  CASE WHEN has_reduced_range THEN NULL
       ELSE ((v.start_range - v.end_range) * c.efficiency) / (v.duration / 3600) * 1000 END AS avg_power,
  convert_km(CASE WHEN has_reduced_range THEN NULL
       ELSE ((v.start_range - v.end_range) / (v.duration / 3600))::numeric END, '$length_unit') AS range_loss_per_hour
FROM v,
  LATERAL (
    SELECT EXTRACT(EPOCH FROM sum(LEAST(s.end_date, v.end_date) - GREATEST(s.start_date, v.start_date))) AS sleep
    FROM states s
    WHERE state IN ('asleep', 'offline')
      AND s.start_date < v.end_date
      AND (s.end_date IS NULL OR s.end_date > v.start_date)
      AND s.car_id = $car_id
  ) s_standby
JOIN cars c ON c.id = $car_id
WHERE v.duration > 3600
  AND v.start_range - v.end_range >= 0
  AND v.end_km - v.start_km < 1
ORDER BY v.start_date DESC
