-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Efficiency" Grafana dashboard of TeslaMate v4.4.0.
WITH t AS (
  SELECT
    CASE WHEN '$temp_unit' = 'C' THEN round(cast(d.outside_temp_avg AS numeric) / 5, 0) * 5
         ELSE round(cast(convert_celsius(d.outside_temp_avg, '$temp_unit') AS numeric) / 10, 0) * 10 END AS outside_temp,
    sum((d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km)) AS total_range,
    sum(d.distance) AS total_distance,
    sum(d.duration_min) AS duration,
    d.car_id
  FROM drives d
  WHERE d.distance IS NOT NULL
    AND d.car_id = $car_id
    AND convert_km(d.distance::numeric, '$length_unit') >= 1
    AND $__timeFilter(d.start_date)
    AND (d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) > 0.1
  GROUP BY 1, d.car_id
)
SELECT
  outside_temp,
  total_distance / total_range AS efficiency,
  total_range / convert_km(total_distance::numeric, '$length_unit') * c.efficiency * 1000 AS consumption,
  convert_km(total_distance::numeric, '$length_unit') AS distance,
  convert_km(total_distance::numeric, '$length_unit') / NULLIF(duration, 0) * 60 AS speed_avg
FROM t
JOIN cars c ON t.car_id = c.id
WHERE outside_temp IS NOT NULL
ORDER BY 1 DESC
