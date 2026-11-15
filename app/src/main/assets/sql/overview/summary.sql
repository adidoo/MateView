-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Overview" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  (SELECT lfp_battery FROM cars INNER JOIN car_settings ON cars.settings_id = car_settings.id
    WHERE cars.id = $car_id) AS lfp_battery,
  (SELECT convert_km(odometer::numeric, '$length_unit') FROM positions
    WHERE car_id = $car_id AND ideal_battery_range_km IS NOT NULL ORDER BY date DESC LIMIT 1) AS odometer,
  (SELECT split_part(version, ' ', 1) FROM updates
    WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS firmware,
  (SELECT state::text FROM states WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS state,
  (SELECT start_date FROM states WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS state_since,
  (SELECT start_date FROM drives WHERE car_id = $car_id AND end_date IS NULL
    ORDER BY start_date DESC LIMIT 1) AS driving_since,
  (SELECT start_date FROM updates WHERE car_id = $car_id AND end_date IS NULL
    ORDER BY start_date DESC LIMIT 1) AS updating_since,
  (SELECT COALESCE(g.name, CONCAT_WS(', ',
            COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), '')),
            COALESCE(a.city, a.neighbourhood)))
    FROM drives d
    LEFT JOIN addresses a ON a.id = d.end_address_id
    LEFT JOIN geofences g ON g.id = d.end_geofence_id
    WHERE d.car_id = $car_id AND d.end_date IS NOT NULL
    ORDER BY d.end_date DESC LIMIT 1) AS place
