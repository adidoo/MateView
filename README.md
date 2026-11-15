# MateView

A native Android app to browse the data logged by a self-hosted TeslaMate
installation: overview, drives, charges, battery, statistics, timeline and
places. Read-only, portrait and landscape, at home and away.

> This project is an unofficial community tool and is not affiliated with, endorsed by, or supported by the official TeslaMate project.

**Status: early development.** Nothing usable yet. See
[docs/design.md](docs/design.md) for the roadmap, the screens and the
architecture.

## How it connects

MateView needs no extra server. It sends read-only SQL queries to the Grafana
instance that ships with TeslaMate, through one of two paths chosen in the
settings:

- **Home Assistant**: through the `hass_ingress` proxy, authenticated with a
  Home Assistant long-lived token. Works away from home without exposing
  anything to the Internet.
- **Direct Grafana**: Grafana address and an optional service-account token,
  for a local network, a VPN or a reverse proxy.

Point Grafana at a **read-only** PostgreSQL account before using the app.

## Build

Requirements: JDK 17 and the Android SDK (platform 37.2).

```
./gradlew assembleDebug
```

## License

MateView is free software, licensed under the
[GNU Affero General Public License v3.0 or later](LICENSE). See
[NOTICE](NOTICE).

Its SQL queries are derived from the Grafana dashboards of TeslaMate
(AGPL-3.0-or-later); TeslaMate's notice is reproduced in
[third_party/teslamate/NOTICE](third_party/teslamate/NOTICE).

"Tesla" is a trademark of Tesla, Inc. MateView is not affiliated with Tesla, Inc.
