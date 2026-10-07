# Arete

A free, import-first Android hiking and mountaineering GPS app. Bring routes via GPX, navigate offline on USGS topo and satellite layers — no accounts, no subscriptions, no ads.

## Features

- **Import routes** — GPX, KML, GeoJSON via share intent or file picker
- **Navigate offline** — follow imported routes with distance, bearing, elevation, and off-route alerts
- **Free map layers** — Arete Topo (USGS), USGS imagery, OpenTopoMap, Esri Satellite, OpenStreetMap
- **Battery Saver mode** — true-black navigation screen with no map tiles for long days
- **Record tracks** — GPS track recording with export to My Routes
- **Offline maps** — download topo (plus the active satellite/OSM layer) for a boxed area; those tiles render with no network
- **GPS assistance** — download ephemeris/A-GPS data before you leave service for a faster lock
- **Power profiles** — Battery / Balanced / Accuracy GPS intervals

## Workflow

1. Find a trail → export GPX
2. Share the file to Arete (or tap Import in the app)
3. Review route on map → Save → Download a Trip Pack
4. Airplane mode / no cell: the packed area, imported route, and GPS keep working. Unpacked areas stay blank.

## Stitch Design Prompts

UI design prompts for Google Stitch are in [`design/stitch/`](design/stitch/).

## Install from GitHub (no Play Store)

One-button page: **[Install Arête](https://narasingaujjini.github.io/NarasingaUjjini/install.html)**

Direct APK: [Arete.apk](https://github.com/NarasingaUjjini/Arete/releases/latest/download/Arete.apk)

1. Tap **Download for Android** (or the APK link above).
2. On the phone, allow install from the browser (unknown sources).
3. Open the APK and install. Android 8.0 or newer.

GitHub APKs are **debug-signed** and **do not contain NPS or Recreation.gov API keys**. Topo, GPS, import, trip packs, and NWS still work. Rec Info is empty on those builds.

Every push to **main** that touches `android/` rebuilds and publishes `Arete.apk` as the latest release. You can also push a tag `v1.0.1`, update the `sideload` tag, or run **Release APK** from the Actions tab.

## Build from source

### Prerequisites

- Android Studio Ladybug or newer
- Android SDK 35
- JDK 17

### Steps

1. Open the `android/` folder in Android Studio (not the parent folder)
2. Let Gradle sync complete
3. Connect your Android phone (USB debugging enabled) or use an emulator
4. Run the app (green play button)

Optional Rec Info keys (your own, never commit them): get free keys from [NPS Developer](https://www.nps.gov/subjects/developer/get-started.htm) and [RIDB / Recreation.gov](https://ridb.recreation.gov/), copy `android/local.properties.example` to `local.properties`, and add `NPS_API_KEY` / `RIDB_API_KEY`.

### Build from command line

```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

Install on a connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

- **Location** — GPS tracking and navigation
- **Internet** — only needed to download map tiles and GPS assistance; not needed on trail after that
- **Foreground service** — track recording in background

## Tech Stack

- Kotlin + Jetpack Compose
- MapLibre Native (Android)
- Room (SQLite) for local route storage
- DataStore for settings
- Google Play Services Location

## Map Data Sources

| Layer | Source | License |
|---|---|---|
| Arete Topo | USGS | Public domain |
| Satellite | Esri World Imagery | Free for personal non-revenue use |
| OSM | OpenStreetMap | ODbL (attribution shown) |

## Privacy

- No account required
- All data stored on device
- No analytics, no ads, no tracking

## License

Arete is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE). Personal / noncommercial use is allowed; commercial use is not.

## Secrets

NPS and Recreation.gov keys live only in gitignored `android/local.properties` on your machine. They are never committed, and GitHub Release APKs are built with `ARETE_PUBLIC_BUILD=1` so those fields are empty in the public binary.
