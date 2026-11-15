-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Database Information" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  split_part(version(), ' ', 2) AS postgres_version,
  pg_database_size(current_database()) AS database_bytes,
  current_setting('TimeZone') AS timezone,
  (SELECT max(version) FROM schema_migrations) AS schema_version,
  (SELECT count(*) FROM drives WHERE car_id = $car_id) AS drives,
  (SELECT count(*) FROM charging_processes WHERE car_id = $car_id) AS charges,
  (SELECT count(*) FROM updates WHERE car_id = $car_id) AS updates,
  (SELECT count(*) FROM drives WHERE car_id = $car_id AND end_date IS NULL) AS open_drives,
  (SELECT count(*) FROM charging_processes WHERE car_id = $car_id AND end_date IS NULL) AS open_charges,
  (SELECT split_part(version, ' ', 1) FROM updates WHERE car_id = $car_id ORDER BY start_date DESC LIMIT 1) AS firmware
