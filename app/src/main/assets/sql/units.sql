-- SPDX-License-Identifier: AGPL-3.0-or-later
SELECT unit_of_length::text AS unit_of_length, unit_of_temperature::text AS unit_of_temperature,
       preferred_range::text AS preferred_range
FROM settings LIMIT 1
