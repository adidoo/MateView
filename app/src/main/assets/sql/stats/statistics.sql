-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Statistics" Grafana dashboard of TeslaMate v4.4.0.
-- Dates are stored in UTC; $tz_offset_s shifts them to the phone's time zone before grouping.
WITH d AS (
  SELECT
    date_trunc('$period', d.start_date + interval '$tz_offset_s seconds') AS p,
    count(*) AS drives,
    sum(d.duration_min) AS duration_min,
    sum(d.distance) AS distance_km,
    avg(d.outside_temp_avg) AS outside_temp,
    sum((d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency) AS energy
  FROM drives d
  JOIN cars car ON car.id = d.car_id
  WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)
  GROUP BY 1
),
c AS (
  SELECT
    date_trunc('$period', start_date + interval '$tz_offset_s seconds') AS p,
    count(*) AS charges,
    sum(charge_energy_added) AS energy_added,
    sum(greatest(charge_energy_added, charge_energy_used)) AS energy_used,
    sum(cost) AS cost
  FROM charging_processes
  WHERE car_id = $car_id AND $__timeFilter(start_date) AND (charge_energy_added IS NULL OR charge_energy_added > 0)
  GROUP BY 1
)
SELECT
  coalesce(d.p, c.p) AS time,
  d.drives,
  d.duration_min,
  convert_km(d.distance_km::numeric, '$length_unit') AS distance,
  convert_celsius(d.outside_temp, '$temp_unit') AS outside_temp,
  d.energy,
  d.energy * 1000 / NULLIF(convert_km(d.distance_km::numeric, '$length_unit'), 0) AS consumption,
  c.charges,
  c.energy_added,
  c.energy_used,
  c.cost
FROM d
FULL JOIN c ON d.p = c.p
ORDER BY 1 DESC
