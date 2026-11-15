# MateView

A native Android app to browse the data logged by a self-hosted TeslaMate
installation. Read-only, in portrait and landscape, at home and away.

> This project is an unofficial community tool and is not affiliated with, endorsed by, or supported by the official TeslaMate project.

**Status: first test version (0.1.0).** It works against TeslaMate v4.4.0 on
the author's installation and has not been tried anywhere else yet. Expect
rough edges.

## What it shows

- **Home**: current state, battery, charging power and voltage, range,
  odometer, consumption, temperatures, charge level and state timeline. It
  refreshes by itself and follows a charge in progress.
- **Drives**: the list grouped by day, then each drive with its route on a
  map, figures and curves (speed, power, battery, range, elevation,
  temperatures).
- **Charges**: the list grouped by day, then each charge with its place,
  figures and curves.
- **Dashboards**: battery health, charge level, projected range, vampire
  drain, statistics, drive stats, charging stats, efficiency, mileage,
  temperatures, timeline, trip, states, updates, locations, visited places,
  database information.

Charts can be read by touch and zoomed by pinching. The last answer of each
screen is kept on the phone and shown, dated, when the server cannot be
reached. The interface is in English or French, following the system or a
choice in the settings.

Some panels of the Grafana dashboards are not carried over yet; see
[docs/design.md](docs/design.md) for the design and the roadmap.

## How it connects

MateView needs no extra server. It sends read-only SQL queries to the Grafana
instance that ships with TeslaMate, through one of two paths chosen in the
settings:

- **Home Assistant**: through the
  [hass_ingress](https://github.com/lovelylain/hass_ingress) proxy,
  authenticated with a Home Assistant long-lived access token. It works away
  from home through your Home Assistant remote address, without exposing
  Grafana to the Internet. The Home Assistant user must be allowed to see the
  ingress panel of Grafana (an administrator, if the panel requires it).
- **Direct Grafana**: the Grafana address and an optional service-account
  token, for a local network, a VPN or a reverse proxy.

Tokens are stored encrypted with a key held by the Android Keystore.

### Use a read-only database account

Anyone who can query Grafana can run SQL with the rights of its PostgreSQL
account, and TeslaMate's Grafana uses the database owner by default. Before
using this app, and all the more if Grafana accepts anonymous viewers, give
Grafana a role that can only read the `public` schema. MateView warns in its
settings when the account can write.

This path through Grafana is not an integration surface supported by
TeslaMate: a TeslaMate or Grafana update may break the app until it is
adapted.

## Install

Download the APK from the
[releases](https://github.com/adidoo/MateView/releases), allow your browser
or file manager to install apps, and open it. Android 8.0 or later.

Then open **Settings** in the app, choose the connection mode, enter the
address and the token, and press **Save and test**: each step of the
connection is checked and the one that fails is named.

## Build

Requirements: JDK 17 and the Android SDK (platform 37.2).

```
./gradlew assembleDebug
```

A signed release needs a line `release.signing=<path>` in `local.properties`,
pointing to a properties file with `storeFile`, `storePassword`, `keyAlias`
and `keyPassword`.

## License

MateView is free software, licensed under the
[GNU Affero General Public License v3.0 or later](LICENSE). See
[NOTICE](NOTICE).

Its SQL queries are derived from the Grafana dashboards of TeslaMate
(AGPL-3.0-or-later); TeslaMate's notice is reproduced in
[third_party/teslamate/NOTICE](third_party/teslamate/NOTICE).

Maps are drawn with [MapLibre Native](https://github.com/maplibre/maplibre-native)
on map data © [OpenStreetMap](https://www.openstreetmap.org/copyright)
contributors.

"Tesla" is a trademark of Tesla, Inc. MateView is not affiliated with Tesla, Inc.
