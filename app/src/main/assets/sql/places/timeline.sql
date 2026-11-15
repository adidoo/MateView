-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Timeline" Grafana dashboard of TeslaMate v4.4.0.
-- Drives, charges and software updates; the app derives the parking periods from the gaps.
SELECT * FROM (
  SELECT
    'drive' AS kind, d.id, d.start_date, d.end_date,
    COALESCE(sg.name, CONCAT_WS(', ', COALESCE(sa.name, nullif(CONCAT_WS(' ', sa.road, sa.house_number), '')), COALESCE(sa.city, sa.neighbourhood))) AS start_place,
    COALESCE(eg.name, CONCAT_WS(', ', COALESCE(ea.name, nullif(CONCAT_WS(' ', ea.road, ea.house_number), '')), COALESCE(ea.city, ea.neighbourhood))) AS end_place,
    convert_km(d.distance::numeric, '$length_unit') AS distance,
    (d.start_${preferred_range}_range_km - d.end_${preferred_range}_range_km) * car.efficiency AS energy,
    sp.battery_level AS start_soc, ep.battery_level AS end_soc
  FROM drives d
  JOIN cars car ON car.id = d.car_id
  LEFT JOIN addresses sa ON sa.id = d.start_address_id
  LEFT JOIN addresses ea ON ea.id = d.end_address_id
  LEFT JOIN geofences sg ON sg.id = d.start_geofence_id
  LEFT JOIN geofences eg ON eg.id = d.end_geofence_id
  LEFT JOIN positions sp ON sp.id = d.start_position_id
  LEFT JOIN positions ep ON ep.id = d.end_position_id
  WHERE d.car_id = $car_id AND $__timeFilter(d.start_date)
  UNION ALL
  SELECT
    'charge', cp.id, cp.start_date, cp.end_date,
    COALESCE(g.name, CONCAT_WS(', ', COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), '')), COALESCE(a.city, a.neighbourhood))), NULL,
    NULL, cp.charge_energy_added, cp.start_battery_level, cp.end_battery_level
  FROM charging_processes cp
  LEFT JOIN addresses a ON a.id = cp.address_id
  LEFT JOIN geofences g ON g.id = cp.geofence_id
  WHERE cp.car_id = $car_id AND $__timeFilter(cp.start_date)
  UNION ALL
  SELECT 'update', u.id, u.start_date, u.end_date, split_part(u.version, ' ', 1), NULL, NULL, NULL, NULL, NULL
  FROM updates u
  WHERE u.car_id = $car_id AND $__timeFilter(u.start_date)
) AS events
ORDER BY start_date DESC
