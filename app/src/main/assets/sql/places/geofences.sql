-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Adapted for MateView from the "Locations" Grafana dashboard of TeslaMate v4.4.0.
SELECT name, radius
FROM geofences
ORDER BY name
