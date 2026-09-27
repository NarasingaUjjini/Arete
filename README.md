# TrailMap GPS

A free, import-first Android hiking and mountaineering GPS app. Bring routes from AllTrails via GPX, navigate offline on USGS topo and satellite layers — no accounts, no subscriptions, no ads.

## Features

- **Import routes** — GPX, KML, GeoJSON via share intent or file picker (AllTrails export workflow)
- **Navigate offline** — follow imported routes with distance, bearing, elevation, and off-route alerts
- **Free map layers** — OpenTopoMap (default), Esri Satellite, OpenStreetMap
- **Battery Saver mode** — true-black navigation screen with no map tiles for long days
- **Record tracks** — GPS track recording with export to My Routes
- **Offline maps** — download topo (plus the active satellite/OSM layer) for a boxed area; those tiles render with no network
- **GPS assistance** — download ephemeris/A-GPS data before you leave service for a faster lock
- **Power profiles** — Battery / Balanced / Accuracy GPS intervals

## Workflow

1. Find a trail on AllTrails → export GPX
2. Share the file to TrailMap GPS (or tap Import in the app)
3. Review route on map → Save → Download offline maps (packs the green-box area + GPS assistance)
4. Airplane mode / no cell: the packed area, imported route, and GPS keep working. Unpacked areas stay blank.

## Stitch Design Prompts

UI design prompts for Google Stitch are in [`design/stitch/`](design/stitch/):

1. Map home screen
2. Import flow
3. Route detail bottom sheet
4. Navigation + Battery Saver mode
5. My Routes, Layer picker, Offline download, Settings

## Build & Install

### Prerequisites

- Android Studio Ladybug or newer
- Android SDK 35
- JDK 17

### Steps

1. Open the `android/` folder in Android Studio
2. Let Gradle sync complete
3. Connect your Android phone (USB debugging enabled) or use an emulator
4. Run the app (green play button)

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
| Topo | OpenTopoMap | CC-BY-SA |
| Satellite | Esri World Imagery | Free for personal non-revenue use |
| OSM | OpenStreetMap | ODbL (attribution shown) |

## Privacy

- No account required
- All data stored on device
- No analytics, no ads, no tracking
