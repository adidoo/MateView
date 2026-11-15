-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Charges" and "Charge Details" Grafana dashboards of TeslaMate v4.4.0.
SELECT
  c.date AS time,
  c.battery_level,
  c.charger_power,
  convert_km(c.${preferred_range}_battery_range_km, '$length_unit') AS range,
  CASE WHEN c.fast_charger_present IS NOT TRUE THEN c.charger_voltage END AS charger_voltage,
  CASE WHEN c.fast_charger_present IS NOT TRUE THEN c.charger_actual_current END AS charger_current,
  convert_celsius(c.outside_temp, '$temp_unit') AS outside_temp
FROM charges c
JOIN charging_processes cp ON cp.id = c.charging_process_id
WHERE c.charging_process_id = $charge_id AND cp.car_id = $car_id
ORDER BY c.date
