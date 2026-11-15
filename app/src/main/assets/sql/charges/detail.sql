-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charges" and "Charge Details" Grafana dashboards of TeslaMate v4.4.0.
SELECT
  cp.id,
  cp.start_date,
  cp.end_date,
  COALESCE(g.name, CONCAT_WS(', ',
    COALESCE(a.name, nullif(CONCAT_WS(' ', a.road, a.house_number), '')),
    COALESCE(a.city, a.neighbourhood))) AS address,
  CASE WHEN NULLIF(mode() WITHIN GROUP (ORDER BY c.charger_phases), 0) IS NULL THEN 'DC' ELSE 'AC' END AS charge_type,
  cp.duration_min,
  cp.cost,
  cp.charge_energy_added,
  greatest(cp.charge_energy_used, cp.charge_energy_added) AS charge_energy_used,
  cp.start_battery_level,
  cp.end_battery_level,
  convert_celsius(cp.outside_temp_avg, '$temp_unit') AS outside_temp,
  convert_km(cp.end_${preferred_range}_range_km - cp.start_${preferred_range}_range_km, '$length_unit') AS range_added,
  cp.charge_energy_added * 60 / NULLIF(cp.duration_min, 0) AS avg_power,
  convert_km(cp.start_${preferred_range}_range_km, '$length_unit') AS start_range,
  convert_km(cp.end_${preferred_range}_range_km, '$length_unit') AS end_range,
  CASE WHEN cp.charge_energy_used IS NULL THEN NULL
       ELSE LEAST(cp.charge_energy_added / NULLIF(cp.charge_energy_used, 0), 1.0) * 100 END AS efficiency,
  avg(c.charger_power) FILTER (WHERE c.charger_power > 0) AS power_avg,
  max(c.charger_power) AS power_max,
  min(p.latitude) AS latitude,
  min(p.longitude) AS longitude
FROM charging_processes cp
LEFT JOIN charges c ON c.charging_process_id = cp.id
LEFT JOIN addresses a ON a.id = cp.address_id
LEFT JOIN geofences g ON g.id = cp.geofence_id
LEFT JOIN positions p ON p.id = cp.position_id
WHERE cp.id = $charge_id AND cp.car_id = $car_id
GROUP BY cp.id, a.id, g.id
