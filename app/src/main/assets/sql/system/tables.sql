-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Database Information" Grafana dashboard of TeslaMate v4.4.0.
SELECT
  c.relname AS name,
  pg_total_relation_size(c.oid) AS bytes,
  c.reltuples AS rows
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
ORDER BY 2 DESC
