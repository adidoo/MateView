-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Temperatures" Grafana dashboard of TeslaMate v4.4.0.
-- Dates are stored in UTC; $tz_offset_s shifts them to the phone's time zone before grouping.
-- Quarter-hour averages first, so that a long drive does not outweigh the rest of the day.
WITH binned AS (
  SELECT
    date_bin('15 minutes'::interval, date, TIMESTAMP '2001-01-01') AS bin,
    avg(outside_temp) AS outside_temp
  FROM positions
  WHERE car_id = $car_id AND outside_temp IS NOT NULL AND $__timeFilter(date)
  GROUP BY 1
)
SELECT
  date_trunc('day', bin + interval '$tz_offset_s seconds') - interval '$tz_offset_s seconds' AS time,
  convert_celsius(min(outside_temp), '$temp_unit') AS outside_min,
  convert_celsius(max(outside_temp), '$temp_unit') AS outside_max,
  convert_celsius(percentile_cont(0.15) WITHIN GROUP (ORDER BY outside_temp::double precision)::numeric, '$temp_unit') AS outside_p15,
  convert_celsius(percentile_cont(0.85) WITHIN GROUP (ORDER BY outside_temp::double precision)::numeric, '$temp_unit') AS outside_p85
FROM binned
GROUP BY 1
ORDER BY 1
