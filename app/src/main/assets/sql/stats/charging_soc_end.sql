-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charging Stats" Grafana dashboard of TeslaMate v4.4.0.
-- Battery level at the end of charging, by 5 % bands. Charges that follow one another count once.
WITH activity AS (
  SELECT cp.start_battery_level, cp.end_battery_level, cp.end_date, true AS is_charge
  FROM charging_processes cp
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date) AND cp.end_date IS NOT NULL
  UNION ALL
  SELECT sp.battery_level, ep.battery_level, d.end_date, false
  FROM drives d
  JOIN positions sp ON sp.id = d.start_position_id
  JOIN positions ep ON ep.id = d.end_position_id
  WHERE d.car_id = $car_id AND $__timeFilter(d.start_date) AND d.end_date IS NOT NULL
),
flagged AS (
  SELECT *, lead(is_charge) OVER (ORDER BY end_date) AS next_is_charge FROM activity
)
SELECT round(end_battery_level / 5.0) * 5 AS soc, count(*) AS n
FROM flagged
WHERE is_charge AND next_is_charge IS NOT TRUE AND end_battery_level IS NOT NULL
GROUP BY 1
ORDER BY 1
