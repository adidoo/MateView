# MateView — plan, screens and architecture

Design document written on 2026-11-15, before any code.
Reference analysed: the 22 Grafana dashboards shipped with TeslaMate v4.4.0
(20 dashboards plus 2 detail pages).

## 1. Goal and scope

A native Android app (Kotlin, Jetpack Compose) that shows the data of a
self-hosted TeslaMate installation, with the same categories as its Grafana
dashboards, usable in portrait and landscape, at home and away.

- **Read-only.** No command is sent to the car and nothing is written to the
  database.
- **No extra server.** The app queries the Grafana instance that already ships
  with TeslaMate.
- **Out of scope for v1**: live data over MQTT, home-screen widgets,
  notifications, Wear OS, editing geofences or charge costs.

## 2. Roadmap

Each step ends with an install on a real phone and a check in both
orientations.

| Step | Content | Visible result |
|---|---|---|
| 0. Foundations | Gradle project, theme, adaptive navigation, license, README | Empty app that launches |
| 1. Connection | Settings, both connection modes, step-by-step test, car selection, cache | "Connected" over mobile data |
| 2. Home | Overview plus shared components: tiles, gauges, charts, period picker | First useful screen |
| 3. Drives and charges | Lists, filters, drive and charge details (map, charts) | The two main use cases |
| 4. Battery | Battery health, charge level, projected range, vampire drain | |
| 5. Statistics | Statistics, drive stats, charging stats, efficiency, mileage, temperatures | |
| 6. Timeline and places | Timeline, trip, states, updates, locations, visited, database info | All 20 categories covered |
| 7. Polish | English and French, icon, offline mode, signed release, tests | Version 1.0 |

## 3. Shared interface principles

### 3.1 Navigation

- **Portrait (compact width)**: bottom bar with 5 entries — Home, Drives,
  Charges, Dashboards, Settings.
- **Landscape and tablets**: the bar becomes a vertical rail on the left; the
  content uses two or three columns.
- **Dashboards**: a grid of every category, grouped (see 4.1), with the same
  names as in Grafana.
- The layout depends on the **available width** (window size classes), not on
  the orientation, so it also works in split-screen.

### 3.2 Elements present on every screen

- **Car**: selector in the top bar, shown only when there are several cars.
- **Period**: chip in the top bar. The default comes from each Grafana
  dashboard (24 h, 7 d, 30 d, 90 d, 3 months, 6 months, 1 year, all), plus a
  custom range.
- **Filters**: bottom sheet with the filters of the matching Grafana dashboard.
- **Units**: the ones set in TeslaMate (km or miles, °C or °F, "ideal" or
  "rated" range).
- **Refresh**: pull down; the data timestamp is shown when reading the cache.
- **States**: loading (skeletons), empty ("no data for this period"), error
  (cause, Retry button, link to settings).

### 3.3 From a Grafana panel to a phone screen

| Grafana panel | Portrait | Landscape |
|---|---|---|
| Stat | Tile grid, 2 columns | 4 to 6 columns |
| Gauge | Arc gauge inside a tile | same |
| Time series | Full-width chart, pinch to zoom, value on touch | Taller, next to the tiles |
| Table (up to 18 columns) | Card list with 4 to 6 key values, the rest in the detail | Real table, horizontal scroll, first column pinned |
| Map | Map on top, collapsible | Map on the left, data on the right |
| Pie, bars, histogram | Chart with legend below | Legend on the right |
| State timeline | Horizontal coloured band, scrollable | same, wider |
| Heatmap, scatter plot, candlesticks | Dedicated chart | same |

## 4. Screens

### 4.1 Grouping

| Group | Screens (Grafana name) |
|---|---|
| Home | Overview |
| Drives | Drives → Drive Details |
| Charges | Charges → Charge Details |
| Battery | Battery Health, Charge Level, Projected Range, Vampire Drain |
| Statistics | Statistics, Drive Stats, Charging Stats, Efficiency, Mileage, Temperatures |
| Timeline and places | Timeline, Trip, States, Updates, Locations, Visited |
| System | Database Information |

### 4.2 Home — Overview (24 h)

- Gauges: battery level (colour zones depend on LFP or not), AC input voltage,
  charging power.
- Tiles: range, odometer, firmware version, net and gross consumption, distance
  logged over the period.
- Temperature gauges: driver setpoint, inside, outside (last hour only,
  otherwise empty).
- Charts: charge level; charging details (power, current, AC voltage, energy
  added, battery heater).
- State timeline: driving, charging, offline, asleep, online, updating.
- App-specific addition: a "current state" card on top (state, since when,
  last known position).

### 4.3 Drives (3 months)

- Period summary: energy consumed, total duration, distance, average
  consumption.
- Drive list. Portrait card: date, start → destination, distance, duration,
  consumption, start % → end %. Extra columns in landscape: temperature,
  average and max speed, max power, efficiency, energy, reduced-range (cold)
  marker.
- Filters: geofence, location (text), minimum distance, minimum speed,
  efficiency calculation mode.
- Collapsed section "incomplete drives".
- Tapping a drive opens its details.

### 4.4 Drive Details

- Map of the route.
- Tiles: distance, duration, odometer from → to, average speed, energy consumed
  and recovered, consumption, ascent and descent.
- Charts: speed, power, battery level, range; elevation; temperatures (outside,
  inside, setpoints, climate); pressure of the four tyres.
- Speed histogram.
- Landscape: map on the left, charts on the right, shared cursor between the
  map and the charts.

### 4.5 Charges (3 months)

- Summary: energy added, energy used, total cost, average duration.
- List. Portrait card: date, location, AC or DC, energy added, start % → end %,
  duration, cost. Extra columns in landscape: energy used, efficiency, cost per
  kWh, average power, range gained, charge rate, temperature, odometer.
- Filters: geofence, location, AC or DC, minimum cost, minimum duration.
- Collapsed section "incomplete charges".

### 4.6 Charge Details

- Tiles: cost, duration, energy added / used / efficiency, battery level start
  / added / end, range start / added / end, average power, outside temperature.
- Map of the charging location.
- Charts: power, battery level, range, AC voltage and current, pilot current,
  phases, temperature, battery heater.
- Charging curve: power against battery level.

### 4.7 Battery

**Battery Health**
- Usable capacity new / now / difference; max range new / now / lost; estimated
  degradation (gauge) and health in %.
- Current level and stored energy; efficiency.
- Charging stats: number of charges, cycles, energy added and used, efficiency;
  AC / DC split.
- Drive stats: logged distance, mileage, data lost.
- Scatter plot: battery capacity against mileage.
- Screen settings: custom "when new" capacity and range.

**Charge Level (6 months)** — battery level and usable battery level, 20 % –
80 % reference band (or 100 % for LFP), optional moving average and
percentiles.

**Projected Range (6 months)** — projected range at 100 %, overlaid with
mileage, battery level or outside temperature; adjustable time resolution.

**Vampire Drain (90 days)** — list of idle periods: start, end, duration,
standby share, SOC loss, range loss, energy drained, average power, loss per
hour. Filter: minimum idle time.

### 4.8 Statistics

**Statistics** — table per day, week, month, year or total: driving duration
and distance, average temperature, number of drives, efficiency, net and gross
consumption, energy used and added, number and cost of charges. Portrait: one
card per period; landscape: full table.

**Drive Stats (1 year)** — number of drives, distance, energy; median drive
distance; average distance and energy per day; max speeds; extrapolated monthly
and annual mileage; speed histogram; top 10 destinations. Filter: locations to
exclude.

**Charging Stats (all time)**
- Tiles: number of charges, energy added, Supercharger cost, total cost, cost
  per 100 km, cost per kWh (overall, AC, DC).
- Charts: charge heatmap (day × hour), charge delta (start → end SOC), AC / DC
  split by energy and by duration, DC charging curve.
- Map of charging locations weighted by kWh.
- Rankings: stations by energy and by cost; distribution of end-of-charge and
  start-of-charge levels.

**Efficiency (all time)** — net and gross consumption, logged distance;
efficiency by temperature band (consumption, distance, average speed); current
and derived efficiencies. Filter: minimum distance per drive.

**Mileage (6 months)** — odometer curve.

**Temperatures (30 days)** — min / average / max outside and inside with
coverage; inside, outside and setpoint curve; outside temperature while
charging; daily candlesticks (min, max, 15th and 85th percentiles).

### 4.9 Timeline and places

**Timeline (7 days)** — chronological list: driving, charging, parking,
updating, missing data. Per row: start, end, duration, addresses, odometer,
distance, energy, range difference, battery level, temperature. Filters: action
type, address. A drive or a charge opens its details.

**Trip (24 h)** — summary of a trip over the chosen range: route map; distance;
time spent (driving, charging, breaks); average speed excluding breaks and
including charging; net and gross consumption; charging cost and cost per
distance; energy added; timeline band; lists of drives and charges; battery +
range and elevation charts.

**States (2 days)** — current state, last change, share of time parked, state
timeline.

**Updates (all time)** — number of updates, median interval, list: date,
version, install duration, time since the previous one, number of charges and
average range on that version.

**Locations (1 year)** — number of addresses, cities, states, countries; city
and state rankings; last visited places; addresses; geofences. Filter: address.

**Visited (90 days)** — map of visited places; distance, charging cost and
energy; countries table (first and last visit, cities, days); cities table;
days spent in the selected city (drives, charges, places).

### 4.10 System — Database Information

Mileage and general stats, firmware version, incomplete data, PostgreSQL
version, database and table sizes, indexes, time zone. The "SQL execution
statistics" part (pg_stat_statements) is not carried over.

### 4.11 Settings

- **Connection** (see 5.2): mode, addresses, token, a "Test connection" button
  that shows each step and the one that fails.
- Default **car**.
- **Display**: theme (system, light, dark), language (system, English, French),
  units (TeslaMate's or forced).
- **Data**: cache lifetime, clear cache.
- **About**: version, AGPL-3.0-or-later license, NOTICE file, "unofficial"
  disclaimer, link to the source code, library licenses.

## 5. Architecture

### 5.1 Technical choices

| Topic | Choice | Reason |
|---|---|---|
| Language, UI | Kotlin, Jetpack Compose, Material 3 | Current Android standard, native adaptive layout |
| Minimum version | Android 8.0 (API 26), target API 36 | Covers almost every phone |
| Structure | One activity, one Gradle module, MVVM (ViewModel + StateFlow) | Simple for a read-only app |
| Injection | Hand-written container | No code generator to maintain |
| Network | OkHttp (HTTP and WebSocket), kotlinx.serialization | The Home Assistant mode needs both |
| Settings | DataStore; tokens encrypted with the Android Keystore | A token must never be stored in clear text |
| Cache | JSON files on disk, keyed by query | Offline display without a local database |
| Charts | Vico for lines, bars and candlesticks; Compose drawing for pie, heatmap, state band, scatter | Vico does not cover every type |
| Maps | MapLibre with a key-less OpenStreetMap basemap | Free software, no Google account |

Exact versions are pinned in `gradle/libs.versions.toml`.

### 5.2 Data access

TeslaMate has no API. The app therefore sends SQL queries to the API of its
Grafana (`POST /api/ds/query`), which runs them against the database with a
**read-only** PostgreSQL account.

```
Screen → ViewModel → Repository → SQL catalog → Grafana client → Transport → Grafana → PostgreSQL
                         ↓
                       Cache
```

**Two transports, chosen in the settings:**

1. **Home Assistant (recommended away from home).** The app connects to the
   Home Assistant address (for example the Nabu Casa one) with a long-lived
   token, then goes through the `hass_ingress` proxy to Grafana:
   1. HA WebSocket, authenticated with the token;
   2. `get_panels` → token of the Grafana panel;
   3. `ha-ingress/session` → 5-minute session, extended by each request;
   4. HTTP requests carrying both cookies; on refusal, start again at 1.

   Nothing has to be exposed to the Internet. Limits: the HA account must be
   allowed to see the panel (administrator if the panel requires it), and the
   mechanism is internal to `hass_ingress`, so it must be re-checked after its
   updates.
2. **Direct Grafana.** Grafana address plus an optional service-account token.
   For the local network, a VPN or a reverse proxy.

Both implement the same `Transport` interface: the rest of the app does not
know which path the requests take.

**SQL queries.** They ship with the app (`assets/sql/<screen>/<panel>.sql`),
derived from the TeslaMate v4.4.0 dashboards and adapted to what each screen
needs. The Grafana server expands the time macros (`$__timeFilter`,
`$__timeFrom`…); the app substitutes the dashboard variables itself (`$car_id`,
`$length_unit`, `$preferred_range`…), always with controlled values (numbers or
closed lists), never unescaped free text.

**Compatibility.** On start the app reads the latest database migration
(`schema_migrations`) and warns when the TeslaMate version is older or newer
than the one the queries were written for.

### 5.3 Code layout

```
adidoo.mateview
├─ app/            activity, dependency container, navigation graph
├─ data/
│  ├─ net/         Transport, direct transport, Home Assistant transport, Grafana client, response parsing
│  ├─ sql/         query catalog, variable substitution
│  ├─ repo/        one repository per domain: cars, drives, charges, battery, statistics, places, system
│  ├─ cache/       disk cache
│  └─ settings/    settings, token vault
├─ domain/         models (car, drive, charge…), units, periods
└─ ui/
   ├─ theme/
   ├─ components/  tiles, gauges, charts, table, map, period picker, filter sheet, adaptive scaffold
   ├─ screens/     one folder per screen (screen + ViewModel)
   ├─ settings/
   └─ about/
```

### 5.4 Rotation and screen sizes

- Each screen's state lives in its ViewModel: it survives rotation without a
  new request.
- Scroll position, open filter, selected tab: kept with `rememberSaveable`.
- Three layouts by width: compact (one column), medium (two columns), expanded
  (list and detail side by side).

### 5.5 Security

- Tokens encrypted with the Android Keystore, never written to logs or to
  Android backups.
- HTTPS required outside the local network; HTTP accepted only for a local
  address, with a warning.
- Grafana should use a read-only PostgreSQL account without access to the
  `private` schema (Tesla API tokens).

### 5.6 Tests

- Unit tests: Grafana response parsing, variable substitution, unit
  conversions, on recorded responses.
- Optional integration test against a real installation (address and token
  from environment variables, never in the repository).
- Manual check on a phone at each step, portrait and landscape.

## 6. License and trademark

- **License: AGPL-3.0-or-later**, the same as TeslaMate, because the SQL
  queries are derived from it. TeslaMate's NOTICE file is kept in full under
  `third_party/teslamate/`.
- **Name**: MateView, application id `adidoo.mateview`. Neither the name nor
  the logo of TeslaMate is used to designate the app.
- **Mandatory disclaimer**, reproduced verbatim in the README, the About page
  and any listing:
  "This project is an unofficial community tool and is not affiliated with,
  endorsed by, or supported by the official TeslaMate project."
