-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Drives" and "Drive Details" Grafana dashboards of TeslaMate v4.4.0.
SELECT
  d.id,
  d.start_date,
  d.end_date,
  COALESCE(sg.name, CONCAT_WS(', ',
    COALESCE(sa.name, nullif(CONCAT_WS(' ', sa.road, sa.house_number), '')),
    COALESCE(sa.city, sa.neighbourhood))) AS start_address,
  COALESCE(eg.name, CONCAT_WS(', ',
    COALESCE(ea.name, nullif(CONCAT_WS(' ', ea.road, ea.house_number), '')),
    COALESCE(ea.city, ea.neighbourhood))) AS end_address,
  d.duration_min,
  convert_km(d.distance::numeric, '$length_unit') AS distance,
  sp.battery_level AS start_battery_level,
  ep.battery_level AS end_battery_level,
  convert_celsius(d.outside_temp_avg, '$temp_unit') AS outside_temp,
  convert_km(d.speed_max::numeric, '$length_unit') AS speed_max,
  d.power_max,
  (d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency AS energy,
  (d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency * 1000
    / NULLIF(convert_km(d.distance::numeric, '$length_unit'), 0) AS consumption
FROM drives d
JOIN cars car ON car.id = d.car_id
LEFT JOIN addresses sa ON sa.id = d.start_address_id
LEFT JOIN addresses ea ON ea.id = d.end_address_id
LEFT JOIN geofences sg ON sg.id = d.start_geofence_id
LEFT JOIN geofences eg ON eg.id = d.end_geofence_id
LEFT JOIN positions sp ON sp.id = d.start_position_id
LEFT JOIN positions ep ON ep.id = d.end_position_id
WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)
ORDER BY d.start_date DESC
