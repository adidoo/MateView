-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Database Information" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  relname AS table_name,
  indexrelname AS index_name,
  idx_scan AS scans,
  pg_relation_size(indexrelid) AS bytes
FROM pg_stat_all_indexes
WHERE schemaname = 'public' AND indexrelname IS NOT NULL
ORDER BY 3 DESC
