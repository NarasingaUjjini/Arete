# TrailMap GPS — Complete reference

This document describes the Android app as it is built, not as it was planned. A reader who has never installed it should be able to reconstruct the screens, the data, the map behavior, the offline rules, and the places where the on-screen wording does not match the code.

App name on the phone: TrailMap GPS. Package: `com.trailmap.gps`. Version shown in settings: v1.0. Version in the build file: 1.0.0 (versionCode 1). Minimum Android: API 26 (Android 8.0). Target and compile SDK: 35. Orientation is locked to portrait. There is no account, no login, no cloud sync, no analytics, and no ads.

The product is a personal hiking navigator. Trail discovery stays in AllTrails (or any other app that can export a file). TrailMap GPS imports that file, draws it, stores it on the phone, can download map tiles for a boxed area, and can navigate or record using the phone’s GPS.

---

## 1. What the app is and is not

It does:

- Import GPX, KML, and GeoJSON from the system share sheet or a file picker.
- Store routes in a local SQLite database.
- Draw the route as a green line on a map.
- Show three free raster map layers: OpenTopoMap (called Topo), Esri satellite with a faint OpenTopoMap overlay, and OpenStreetMap.
- Optionally draw a hillshade raster under the topo layer.
- Let the user pan a green box over the map and download the tiles inside that box.
- Serve those tiles from disk when the phone has no network, and show a blank tile when a tile was never downloaded.
- Navigate a saved route: distance to the next route vertex, bearing, elevation, remaining distance, progress, and an off-route banner.
- Record a GPS track into a new route.
- Draw a route by tapping the map.
- Change units, coordinate format, GPS update rate, and whether the screen stays on.

It does not:

- Search for trails, show AllTrails photos or reviews, or browse nearby OpenStreetMap trails.
- Re-route or snap a line to a trail network.
- Export GPX. A FileProvider is declared for `cache/exports` and `files/routes`, but nothing writes those paths.
- Show named GPX waypoints on the map. They are parsed and stored, then ignored by every screen.
- Edit route notes. The database column `notes` exists and is always empty.
- Delete downloaded tiles when a route is deleted. Tiles live in a shared cache, not inside the route.
- Turn the screen off or dim it by power profile. The settings copy that mentions dimming is not implemented.
- Enforce the “25.0 MB” or “10,000 waypoints” limits printed on the import screen.

---

## 2. How a person moves through the app

There is one activity, `MainActivity`. It hosts a single Compose tree, `TrailMapAppContent`. Screens are not Android Navigation routes. They are a sealed class held in memory:

- Map (the launch screen)
- Import picker
- Import preview
- Routes library
- Route detail
- Offline download
- Navigation
- Recording
- Settings

A layer picker is not its own screen. It is a modal bottom sheet opened from the map’s Layers tab.

The back stack is not a real stack. Each screen’s back button assigns the next screen directly. Leaving route detail goes to the library. Leaving the library, settings, recording, import, or download goes to the map. Leaving navigation stops navigation and returns to the map. There is no system-back handling beyond whatever the activity does by default, so the Android back button can exit the app from the map.

### Launch

On create, the activity initializes MapLibre, turns on edge-to-edge drawing, asks for fine and coarse location if they are not granted, and handles a VIEW or SEND intent if the app was opened by sharing a file. It then watches the “keep screen on” setting. That setting defaults to on, and when it is on the activity sets `FLAG_KEEP_SCREEN_ON` for the whole app, not only during navigation. The settings label says “Keep screen on during navigation.” The code keeps the screen on everywhere.

The application class, `TrailMapApp`, opens the database, the settings store, and the offline tile manager. On startup it initializes MapLibre and installs a tile URL rewriter so map requests can be answered from disk.

### Map home

The map fills the screen. On top of it, from top to bottom and left to right:

- A pill in the upper left, only when a GPS fix exists: a green dot and the accuracy as `±Nm`, where N is the fix’s accuracy in meters, truncated to an integer. If there is no fix, that corner is an empty 44 dp spacer so the compass stays on the right.
- A compass button, upper right. It shows a triangle above the letter N. The whole mark rotates opposite the map’s bearing, so the N still points to geographic north when the map is rotated. If the north-up setting is on, the mark is trail green. If the map is in heading-up mode, the mark is the normal light text color. Tapping it flips north-up versus heading-up and animates the camera bearing. North-up sets bearing to 0. Heading-up sets bearing to the latest GPS course. If there is no course yet, heading-up also uses 0.
- A metrics capsule under those controls: elevation, speed, and bearing from the latest GPS fix. Without a fix each value is an em dash. Elevation and speed follow the unit settings. Bearing is the GPS course rounded to a whole degree with a degree sign, not a compass bearing to a destination.
- Three round buttons on the right edge. Top: crosshair, “Center on my location.” It animates the camera to the latest fix at zoom 15 or the current zoom, whichever is larger, and applies the current north-up or heading-up bearing. If there is no fix, the trigger increments but the camera does not move. Middle, hidden while drawing: a green download-shaped button whose action is Import, not map download. Bottom: a pencil. It starts or cancels freehand drawing. While drawing, the icon is a red close mark.
- A fullscreen button, lower left, a circle with an expand icon. Fullscreen hides the accuracy pill, compass, right-edge buttons, draw banner, save chip, and bottom bar. The metrics capsule stays. The fullscreen button remains, now just above the system navigation bar, and its icon becomes “exit fullscreen.”
- A bottom bar, black, 56 dp tall, plus padding for the system navigation bar so it sits above three-button nav and gesture nav. Four items: ROUTES, RECORD (or REC in red while a recording service is running), LAYERS, SETTINGS. None of them is a selected tab on the map itself; they navigate away or open the sheet. The map is not one of the four tabs.

If a route is selected, or an import preview is on screen, or navigation was showing a route, the map draws that route. On the home map the priority is: the route currently being navigated, otherwise the selected saved route, otherwise the unsaved import preview, otherwise no line. The home map auto-fits the route when the point list identity changes, unless the user is drawing. It does not follow the user. The user has to tap the crosshair.

### Routes library

Title: ROUTES LIBRARY. A search field filters the in-memory list by whether the route name contains the query, ignoring case. The database has a SQL search query, but the screen does not use it.

Empty state: “NO ROUTES YET”, “Import a GPX file to get started”, and an IMPORT button that opens the file picker screen.

Each card shows:

- An 80 dp placeholder, not a map thumbnail. A small badge in the corner reads GPX, REC, or Drawn.
- The route name, one line, ellipsized.
- A source word: IMPORTED, RECORDED, or DRAWN.
- The date the route was saved, formatted like “SEP 3, 2026”. The field is named `importedAt` even for recorded and drawn routes. It is the insert time.
- DISTANCE and ELEVATION. Distance uses the unit setting. Elevation here is elevation gain, not the current altitude.
- A 4 dp bar along the bottom. If the route has been marked offline-downloaded, the left 15 percent of that bar is green. The 15 percent is a fixed decoration. It is not a measure of how much of the route is covered.

Routes are ordered newest first. Tapping a card selects it, fits it on the map state, and opens route detail.

### Import

Two ways in.

Share or open: the manifest accepts VIEW and SEND for GPX XML, generic XML, KML, JSON, and `*/*`. The activity reads the URI from the intent data or `EXTRA_STREAM`, opens an input stream, and uses the last path segment as the file name, or `imported_route.gpx` if there is none. A parse error is swallowed. The preview screen is requested, but if parsing failed the preview is null and the UI immediately sends the user back to the map. There is no error message.

Browse: the import screen says “Import Route Data” and “Select a GPX, KML, or GeoJSON file. Share directly from AllTrails or tap to browse.” The button is BROWSE FILES. Under it, three rows claim Supported Formats GPX, KML, GEOJSON; Max File Size 25.0 MB; Waypoint Limit 10,000. Those last two numbers are not checked anywhere.

The system document picker is opened with the same MIME list as the share intent, including `*/*`.

After a successful parse, Import Route shows the line on a map with a 20 percent black veil, a badge that always says “Imported from GPX” even for KML or GeoJSON, the name in capitals, and three cards: Distance, Elevation (gain), and Est. Time. An elevation profile follows. Buttons: Save Route, and Save & Download Maps. Save writes the route, selects it, asks the map to fit it, and returns home. Save & Download does the same write and opens the offline screen for the new id. Back clears the preview and returns home. Nothing is stored until Save.

### Route detail

A map of the route fills the background, dimmed 15 percent. A back arrow returns to the library. A bottom sheet covers about 65 percent of the screen. It is not draggable despite a drag-handle graphic; the handle is decoration.

Inside the sheet:

- The route name, up to two lines.
- A source badge: “Imported from GPX”, “Recorded track”, or “Drawn route”.
- If `offlineDownloaded` is true and GPS assistance was refreshed within 24 hours: a green check and OFFLINE READY. If maps were downloaded but GPS assistance is missing or older than 24 hours: MAPS SAVED. The check is the same in both cases.
- Four stat tiles. The numbers respect unit settings, but the captions under them are hardcoded: “mi”, “ft gain”, “hrs”, “max ft”. In kilometers and meters the number is converted and the caption is still miles and feet. “hrs” is the estimated-time string’s first token, so a value like “2h” is what shows, and the caption still says hrs.
- A 120 dp elevation profile. The x-axis is point index, not distance. The y-axis is elevation from the lowest point on the route to the highest, with a minimum range of 1 meter so a flat line still draws. No axis numbers are printed.
- Offline status text. If never downloaded: “No offline map pack — download this area before you lose signal.” If downloaded: “Offline maps · N MB” (or KB or B) using the byte count stored on the route, then a GPS assistance line. Never refreshed: “GPS assistance not downloaded — use Settings or re-download this area.” Older than 24 hours: “GPS assistance is stale — refresh before you leave signal.” Fresh: “GPS assistance · Just now” or “Nm ago”, “Nh ago”, “Yesterday”, or “Nd ago”.

The footer has Navigate, a download icon, and a red delete icon. Delete removes the database row immediately, with no confirmation, and returns to the library. It does not delete map tiles. Navigate stores the route, starts the navigation flag, starts an elapsed-time clock, and opens the navigation screen.

### Offline download

Title: OFFLINE MAPS. The top 45 percent is a live map of the route. A green rounded rectangle is drawn inset from the edges of that map. A slider labeled Area Size moves the inset. Tight is a large inset (a small box). Wide is a small inset (a box that nearly fills the map). The labels are “Small download box”, “Medium download box”, and “Large download box” at the one-third and two-thirds marks of the slider. The slider does not add a geographic buffer. It only changes how much of the screen the box covers. The user pans and zooms the map so the terrain inside the box is the terrain they want. When the camera goes idle, the app converts the box’s screen corners into a longitude/latitude rectangle: minimum longitude, minimum latitude, maximum longitude, maximum latitude.

Copy under the route name: “Pan and zoom the map so the green box covers the area you want offline.” A green line names what will be packed, for example “Packs Topo, then refreshes GPS assistance.” or “Packs Topo · Satellite · Hillshade, then refreshes GPS assistance.”

Detail Level is a slider from zoom 12 to zoom 16 in whole steps, default 15. The label reads “Zoom 10 — N” because every download also fetches zoom 10 up through the chosen maximum. Zooms below 10 are never fetched. Zooms above the slider value are never fetched.

An estimate card shows “EST. SIZE” as roughly `tileCount * 18 / 1024` megabytes, at least 1, prefixed with a tilde. The 18 is an assumed kilobyte-ish constant used as `tiles * 18 bytes / 1024`, so the number is a rough stand-in, not a measured size. The line under it is “N tiles · Topo” (plus Satellite, OpenStreetMap, and/or Hillshade when those are active). GPS assistance status is repeated here.

Download is disabled while a download or a GPS refresh is running, while the tile estimate is zero, or before the box has reported bounds. The button says Download, or Re-download if the route was already marked offline. Cancel returns to the map and does not abort an in-progress download; the coroutine keeps running.

During download a green progress bar shows percent complete, counting every tile job, including ones that fail.

When the byte count comes back greater than zero, the route is marked offline-downloaded and `offlineSizeBytes` is set to that count. The count is the sum of the lengths of tile files touched, including files that were already on disk. It is not “new bytes this session” and it is not the size of the whole cache. If every tile fails, the count is zero and the route is not marked offline.

After the tile loop, the same action refreshes GPS assistance.

What gets downloaded depends on the current layer and the hillshade switch, not on a checklist on this screen:

- Topo: OpenTopoMap only.
- Satellite: OpenTopoMap and Esri World Imagery.
- OpenStreetMap: OpenTopoMap and OSM raster.
- Hillshade on: the above, plus hillshade tiles.

OpenTopoMap is always included so contours exist on every pack.

### Navigation

The screen is either the full map or Battery Saver.

Full map: the camera follows the user. Zoom will not drop below 14 while following. Bearing follows the north-up setting. The top bar is a translucent black strip: “← EXIT”, the route name in capitals (one line), and a battery icon. EXIT stops navigation and returns home. The battery icon toggles Battery Saver.

If the nearest route vertex is more than 45 meters from the GPS fix, a banner appears under the top bar: “OFF ROUTE — MORE THAN 45M FROM TRAIL”. The test is distance to the nearest stored point, not distance to the line segment between points. A sparse GPX can warn while the user is still on the trail, and a dense GPX can stay quiet while the user has left the corridor but is near some vertex.

The bottom panel shows three stats and a progress bar:

- TO NEXT WAYPOINT. This is not a named waypoint. The app finds the nearest vertex, then uses the next vertex after it (or the last vertex if the user is on the end). The distance is straight-line haversine from the GPS fix to that next vertex.
- BEARING. Initial bearing from the fix to that same next vertex, 0–360 degrees, not the GPS course.
- ELEVATION. The GPS altitude, not the elevation stored on the route.

Under the bar: “X remaining · Y elapsed”. Remaining distance is the sum of haversine segments from the nearest vertex to the end. It does not subtract the partial segment between the user and that vertex, and it does not add the distance from the user back to the line. Elapsed time is wall-clock time since Navigate was tapped, updated once a second. It is not moving time.

Progress is `nearestIndex / (pointCount - 1)`. It is how far along the vertex list the nearest point is, not how far along the distance the user is.

Battery Saver: the same map is still rendered underneath, including tiles and the follow-camera. A 50 percent black veil covers it. Large type at the bottom shows distance to the next vertex, elevation, and elapsed time. A green battery icon sits at the top right. The caption is “BATTERY SAVER · TAP TO EXIT”. Tapping the veil, the stats, or the icon turns the veil off and returns to the full navigation chrome. It does not exit the hike. There is no EXIT control on this view; the user must leave Battery Saver first. The original design goal of drawing no tiles is not what the code does. The map is still running, which still costs GPU work. The black veil only hides it.

### Recording

RECORD on the home bar opens a black screen, not the map. Before a session it says READY TO RECORD, a large duration (see the timer bug below), distance 0, elev. gain 0, Start Recording, and Back to Map.

Start Recording clears any previous in-memory track, starts a foreground service, and sets the same elapsed-time origin used by navigation. The service notification is “Recording track” on a low-importance channel named “GPS Tracking”. The service reads the power profile once at start and requests GPS updates at that interval. Each fix is stored as a track point (latitude, longitude, GPS altitude, timestamp) and also published as the current location. The in-memory list lives on the service, not in the database, until save.

Stop Recording stops the foreground service. The points remain in memory so they can be saved. The screen still says READY TO RECORD after stop, because the recording flag is false. There is no separate “stopped, unsaved” state and no Save button on this screen. `saveRecordedTrack` exists and the screen has an `onSave` callback, but the recording UI never calls it. A finished recording is discarded when the track is reset on the next Start, or when the process dies. This is a functional gap: the user can record and stop, and cannot name and keep the track from the recording screen.

The elapsed clock on this screen only advances while `isNavigating` is true. Starting a recording sets the clock origin but does not set navigating, so the big timer stays at 0m unless the user is also in a navigation session. Distance and elevation gain on the screen are computed from the recorded points and do update.

The bottom-bar record icon turns red and reads REC while the service reports recording, even if the user has navigated back to the map. The service is sticky. Stopping it is the Stop button. There is no notification action to stop.

Saving, if it were invoked, requires at least two points, computes the same stats as import, stores source RECORDED, and clears the in-memory track.

### Drawing a route

The pencil on the map sets drawing mode and clears any previous draw line. While drawing, map taps add a point. The elevation stored on each tapped point is the current GPS altitude, or 0 if there is no fix. A yellow line connects the taps. The camera fits the drawn line once there are at least two points. A banner says “TAP MAP TO ADD WAYPOINTS”. These are draw vertices, not GPX waypoints.

With two or more points, a chip appears above the bottom bar: “N points” and SAVE ROUTE. That opens a dialog whose field defaults to “Drawn Route”. Save requires two points, computes stats, stores source DRAWN, clears drawing, selects the new route, and fits it. Cancel on the dialog only closes the dialog. The pencil’s close icon cancels drawing and throws the points away. There is no undo for a single point.

### Layers

The sheet title is MAP LAYERS, subtitle “Configure active data sources”. Base Layer is a radio list:

- Topo (OpenTopoMap)
- Satellite (Esri)
- OpenStreetMap

Overlays: a Hillshade switch. DONE closes the sheet. Changing either value writes DataStore immediately and the map reloads its style. Hillshade only changes the topo style. On satellite and OSM the switch is still saved, and a later offline pack will include hillshade tiles, but those styles do not draw the hillshade layer.

### Settings

Title: GLOBAL SETTINGS.

Units. Distance: mi or km. Elevation: ft or m. Each control is on its own card so they do not overlap.

Coordinates. Format: Decimal or UTM. Nothing on the map home or navigation screen displays coordinates. The formatter exists. Decimal is five digits after the point, “lat, lon”. UTM is a simplified conversion that always prints a northing as if the point were in the northern hemisphere (`zoneN eastingE northingm`). Southern-hemisphere false northing is not applied. No screen currently calls this formatter.

Power Profile. Battery, Balanced, or Accuracy.

- Battery: GPS hardware updates about every 10 seconds. The caption says “GPS every 10s · Screen dims after 30s idle”. The interval is real. The dimming is not.
- Balanced: about every 3 seconds. Caption: “GPS every 3s · Screen stays on during navigation”. The interval is real. Screen-on is the separate Display switch, default on, and it applies to the whole app.
- Accuracy: about every 1 second, same caption pattern, “Maximum accuracy”. The GPS request uses the same high-accuracy provider as Balanced. The difference is the interval, not a different chip mode. Minimum update interval equals the interval, so the system is asked not to deliver faster than that.

Display. “Keep screen on during navigation”, default on. As noted, it is not limited to navigation. Under it, a non-interactive line: “Dark theme — Always on”. There is no light theme in the app. The Material theme is a dark scheme only.

Prepare for offline. Explains that map areas are downloaded from a route and that GPS assistance speeds up a lock with no cell service. Status is “GPS data · Never downloaded”, “GPS data · Stale (… )”, or “GPS data · …” using the same relative-time words as route detail. Stale means older than 24 hours, or never. A result message from the last refresh attempt appears underneath when one exists. The button reads “Download GPS data”, or “Refreshing GPS…” while it runs.

Footer: the launcher artwork, “TrailMap GPS v1.0”, “No account · No tracking · No ads”. “No tracking” means no analytics. The app does track GPS position while it is open, and while recording.

---

## 3. What a route contains

SQLite database file: `trailmap.db`. One table, `routes`. Room schema version 1, no migrations.

Columns:

- `id`, autoincrement primary key.
- `name`, string.
- `source`, stored as the enum name IMPORTED, RECORDED, or DRAWN.
- `distanceMeters`, `elevationGainMeters`, `elevationLossMeters`, `maxElevationMeters`, doubles, computed at save time and never updated.
- `estimatedTimeSeconds`, long, computed at save time.
- `pointsJson`, a JSON array of objects `{lat, lon, elevation, time?}`. Time is omitted when null. On read, time is kept only if the number is greater than 0.
- `waypointsJson`, default `[]`. GPX waypoints become `{name, lat, lon, elevation}`. KML and GeoJSON always store an empty list. Drawn and recorded routes also store an empty list.
- `importedAt`, epoch milliseconds at insert.
- `offlineDownloaded`, boolean, default false.
- `offlineSizeBytes`, long, default 0.
- `notes`, string, default empty, never written by the UI.

Deleting a route deletes the row only.

### How stats are computed

For any list of points, if there are fewer than two points the stats are all zero.

Otherwise the app walks consecutive pairs:

- Distance is the haversine distance on a sphere of radius 6,371,000 meters. It is not Vincenty and it does not use the elevation difference, so it is a horizontal distance.
- Elevation gain is the sum of positive elevation deltas. Elevation loss is the sum of the absolute values of negative deltas. There is no noise threshold. A recorded GPS track whose altitude jitters by a meter each fix will accumulate that jitter as gain and loss.
- Max elevation is the largest elevation value in the list, including zeros. A GPX with missing elevations stored as 0 can report a max of 0 if every point is 0, or a misleading max if only some points have elevation.
- Estimated time assumes a flat hiking speed of 3 kilometers per hour. `hours = (distanceMeters / 1000) / 3`, then seconds. Climb, descent, and stops are ignored. A 3 km route is always estimated at 1 hour.

### GPX parse

The whole file is read as text. If the text contains `<gpx`, case-insensitive, it is treated as GPX. The parser is a pull parser, not a schema validator.

- The default name is the file name without its extension. The first `<name>` that is not inside a waypoint replaces it. Later names also replace it, because the parser does not stop at the metadata name. A track name later in the file wins over the document name.
- `<trkpt>` and `<rtept>` both append to the same point list, in document order. A file with a route and a track will concatenate them into one line.
- Latitude and longitude are attributes. Missing or unparsable values become 0, which is a real coordinate in the Gulf of Guinea, not a skipped point.
- `<ele>` sets a single elevation variable. The next point uses whatever elevation was last seen. If a point has no elevation element, it inherits the previous point’s elevation.
- `<wpt>` stores a waypoint. The name is unreliable: the end-tag handler reads `parser.text` after the name element has already been consumed, so the stored name is often the default “Waypoint”.
- If no track or route points were found, parse throws “No track points found in GPX file”. The UI turns that into a silent return to the map.

### KML parse

Triggered when the text contains `<kml`. Every `<name>` overwrites the route name, so the last name in the file wins. Every `<coordinates>` text node is split on whitespace. Each token is `longitude,latitude` or `longitude,latitude,elevation`. KML order is longitude first, the opposite of GPX attributes. Tokens that do not have two numbers are skipped. Empty coordinate lists throw “No coordinates found in KML file”. Waypoints are not read. Folders, styles, and multiple geometries become one concatenated line.

### GeoJSON parse

Triggered when the trimmed text starts with `{`. A `"name"` string is taken from the first regex match, otherwise the file name. Every JSON-like coordinate pair `[lon, lat]` or `[lon, lat, ele]` in the whole document is taken, including coordinates that are not part of the intended line, because the regex scans the raw text. GeoJSON order is longitude first. An empty match throws “No coordinates found in GeoJSON file”.

Anything else throws “Unsupported file format”.

---

## 4. The map

MapLibre Native (Android) version 11.7.1 draws the map inside a `MapView` embedded in Compose. The view is created once. Style reloads when the layer, hillshade flag, or battery-saver flag changes.

The map UI chrome from MapLibre is reduced: the logo is off, the compass widget is off because the app draws its own, rotation gestures are on, attribution is on except in battery-saver style. Prefetch zoom delta is 3, so the renderer asks for tiles three zooms out as well.

There are three route lines, each a GeoJSON LineString:

- Saved or imported route: trail green `#4ADE80`, width 5.
- Draw line: amber `#FBBF24`, width 4.
- A track source and blue line `#60A5FA`, width 3, exist in the map code but no screen passes recorded points into that layer while recording. The live recording is not drawn on the home map.

Elevations are included as a third coordinate when they are not exactly 0. The line is still drawn in two dimensions; the third value is just stored on the geometry.

A blue location puck is MapLibre’s location component in compass render mode, using the default location engine (so Play Services can also move it) and, separately, `forceLocationUpdate` whenever the app’s own GPS fix changes. The puck can therefore be driven by two sources.

Tapping the map does nothing unless drawing mode is on. In drawing mode the tap becomes a vertex and the map consumes the click.

Camera behavior:

- Fit route: animate over 600 ms to the bounds of the points with 120 px padding, or 80 px for a drawn line. If the map is not laid out yet, fall back to the average coordinate at zoom 12.
- Recenter: animate over 400 ms to the fix, zoom at least 15.
- North-up toggle: animate bearing over 350 ms, keeping the current target and zoom.
- Follow, used only on the navigation screen: ease over 800 ms to the fix, zoom at least 14, whenever the fix or the north-up flag changes.

### Styles

The app does not load a remote style document. Each layer is a MapLibre style JSON string compiled into the app. The background of every map style is `#0b1210`, a near-black green, so missing tiles look dark instead of white. Battery-saver style is an empty style with a pure black background, but the navigation Battery Saver screen does not use that flag. It uses the normal style and covers it with a scrim.

Topo, hillshade off. One raster source, OpenTopoMap, tile size 256, max zoom 17, three hosts:

- `https://a.tile.opentopomap.org/{z}/{x}/{y}.png`
- `https://b.tile.opentopomap.org/{z}/{x}/{y}.png`
- `https://c.tile.opentopomap.org/{z}/{x}/{y}.png`

Attribution string in the style: OpenTopoMap.

Topo, hillshade on. The same OpenTopoMap source, plus `https://tiles.wmflabs.org/hillshading/{z}/{x}/{y}.png`, max zoom 15, drawn at 35 percent opacity underneath the topo tiles. Attribution: Wikimedia.

Satellite. Esri World Imagery:

`https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`

Note the order: zoom, then Y, then X. That is Esri’s order, not the XYZ order used by OpenTopoMap and OSM. Max zoom 19. On top, OpenTopoMap from hosts a and b at 40 percent opacity so contours sit on the imagery. Attributions: Esri and OpenTopoMap.

OpenStreetMap. `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, max zoom 19. Attribution: OpenStreetMap.

These URLs are what MapLibre requests. They are not what the phone necessarily hits. The tile manager rewrites them first.

OpenTopoMap is OSM data rendered with contour lines. It is not a USGS scan. The README’s older “USGS Topo” wording is leftover. The layer picker and the code both say OpenTopoMap.

---

## 5. Offline tiles

Directory: the app’s private files folder, `offline_tiles/`. Layout:

`offline_tiles/{source}/{z}/{x}/{y}.png`

Source folder names: `opentopo`, `esri`, `osm`, `hillshade`. Tiles are shared across routes. Downloading two nearby routes reuses files that already exist.

A 1×1 transparent PNG, `empty_tile.png`, sits next to that folder. It is written once at startup.

On startup the app registers a MapLibre resource transform. Every tile URL is inspected:

- Host contains `tile.opentopomap.org/` → opentopo, path `z/x/y.png`.
- Path contains `World_Imagery/MapServer/tile/` → esri, path `z/y/x`, stored on disk as `z/x/y` after swapping Y and X back.
- Host contains `tile.openstreetmap.org/` → osm, `z/x/y.png`.
- Path contains `hillshading/` → hillshade, `z/x/y.png`.

If the matching file exists and is non-empty, the transform returns a `file://` URI and MapLibre reads the disk. If it does not exist and the device has a network that claims internet capability, the original HTTPS URL is returned and the tile is fetched live. That live fetch is not copied into the cache. Only the explicit Download action writes files. Panning around online does not fill the offline pack. If the file is missing and there is no network, the transform returns the empty PNG so the map stays dark instead of waiting on a dead connection.

“Has a network” means `ConnectivityManager` reports an active network with `NET_CAPABILITY_INTERNET`. It does not prove the server is reachable. Airplane mode fails the check. A captive portal can pass the check and then fail the download.

### Which tiles a download enumerates

Bounds are `[minLon, minLat, maxLon, maxLat]`. For each zoom from 10 through the chosen max:

- Convert the southwest and northeast corners to XYZ tile indices. Latitude is clamped to ±85 degrees before the Web Mercator formula. Tile indices are clamped into the legal range for that zoom.
- The rectangle of tiles between those indices is added.
- If that rectangle is more than 5,000 tiles, the entire zoom level is skipped with no error. A huge box can silently omit coarse zooms, or omit the detailed zooms, and still download the levels that fit under the cap.

Hillshade tiles are not requested above zoom 15 even if the detail slider is 16. Esri and OSM allow up to 19 but the slider never asks past 16. OpenTopoMap’s own max is 17; the slider never asks for 17.

Downloads run on the IO dispatcher, one tile after another, not in parallel. Each request times out at 15 seconds connect and 15 seconds read. The User-Agent is `TrailMapGPS/1.0 (personal hiking app)`. HTTP statuses outside 200–299 produce a zero-byte contribution and the loop continues. Exceptions are swallowed the same way. A partial pack can be marked successful if at least one tile file had a non-zero length.

If the file already exists and is non-empty, it is not downloaded again. Its length is still added to the reported byte count.

Files are written to `y.png.tmp` and renamed into place.

Invalid bounds (max ≤ min) set the error “Invalid download area” and return 0. An empty tile list sets “No tiles in download area”. The download screen does not display `error`; it only shows the progress bar. Those errors update the state object and then sit unseen unless a later UI reads them. Nothing in the current screens reads `downloadState.error`.

### What “offline ready” guarantees

Inside the downloaded zoom range and inside the downloaded rectangle, topo tiles that succeeded will draw with the radios off. Satellite or OSM pixels appear offline only if that layer was the selected layer during the download. Hillshade appears offline only if the switch was on during the download and the zoom is 15 or below. Outside the box, or at a zoom that was skipped or never requested, the user sees the dark background and empty tiles. The route line, the database, and the GPS fixes do not need those tiles. A hike can be navigated on a blank map.

Leaving the packed area does not warn. The off-route banner is about the trail line, not about the tile rectangle.

---

## 6. GPS

Two listeners can run at once.

The view model, for as long as the process lives, collects fixes for the map puck and the metrics. It restarts the stream when the power profile changes.

The recording service collects its own stream only while a recording is in progress, using whatever profile was current when recording started. It does not notice a profile change mid-recording.

Both prefer `LocationManager.GPS_PROVIDER` when the user has location enabled. The listener is the platform GPS provider, interval from the power profile, minimum distance 0. The last known GPS fix, if any, is emitted immediately. If GPS is switched off, the view-model path falls back to Google Play Services fused location at high accuracy and the same interval. The recording service uses the same `LocationTracker`, so it has the same fallback.

A fix becomes:

- latitude, longitude
- elevation = altitude in meters (0 if the chip has not produced one; the Android `Location` altitude defaults to 0)
- accuracy in meters
- bearing in degrees, 0 if the chip has no course
- speed in meters per second
- timestamp from the location object

The app does not filter by accuracy, does not reject a 0,0 fix, and does not smooth speed or altitude.

### GPS assistance

“Download GPS data” and the end of a map download call `LocationManager.sendExtraCommand` on `GPS_PROVIDER` with `force_xtra_injection` and `force_time_injection`. XTRA is the assistance file many Android GPS stacks use so the chip does not have to wait for satellites to broadcast a full ephemeris. The commands are best-effort. Some phones ignore them. If the provider is disabled, the function returns failure and the message “Turn on location / GPS, then try again”.

After the commands, the app waits up to 20 seconds for one GPS fix. Outcomes:

- A fix arrived: success, “GPS assistance saved”. The current time is stored in DataStore.
- No fix, but at least one extra command returned true: success, “Assistance data requested — keep GPS on until you get a lock”. The timestamp is still stored, so the UI will say the data is fresh even if the chip never confirmed a lock.
- Neither: failure, “Could not refresh GPS data. Go outside with a clear sky and try again.” The old timestamp is left unchanged.

The stored key is `gnss_assistance_updated_at` in the preferences DataStore named `settings`. Fresh means newer than 24 hours. The assistance data itself is not a file the app manages. The app only stores the time it last asked the system to refresh.

Navigation and the map do not wait for this refresh. GPS works without it. The refresh only exists to shorten the time to first fix after the phone has been off the network.

---

## 7. Settings storage

DataStore preferences file `settings`:

- `distance_unit`: MILES (default) or KILOMETERS.
- `elevation_unit`: FEET (default) or METERS.
- `coordinate_format`: DECIMAL (default) or UTM. Unused by screens.
- `power_profile`: BALANCED (default), BATTERY, or ACCURACY.
- `map_layer`: TOPO (default), SATELLITE, or OSM.
- `hillshade`: false by default.
- `keep_screen_on`: true by default.
- `north_up`: true by default.
- `gnss_assistance_updated_at`: 0 until a successful assistance call.

### Display conversions

- Miles: meters / 1609.344, one decimal, suffix `mi`.
- Kilometers: meters / 1000, one decimal, suffix `km`.
- Feet: meters × 3.28084, rounded to an integer with thousands separators, suffix `ft`.
- Meters: rounded integer, suffix `m`.
- Speed: meters per second × 2.23694 for mph, × 3.6 for km/h, one decimal.
- Duration: `Hh Mm` if at least one hour, otherwise `Mm`. Seconds are dropped. Zero is `0m`.
- File size: decimal MB at 1,000,000 bytes and above, else KB at 1,000, else raw bytes. Not kibibytes.

---

## 8. Visual system

The UI is a dark “Alpine Tactical” theme taken from a Stitch mock. There is one accent, trail green `#4ADE80`. Text is `#DCE3F0`. Secondary text is `#BCCABB`. Surfaces are not pure gray; the cards are blue-black, from `#080F17` through `#151C25`, `#192029`, `#232A34`, to `#2E353F`. Borders are `#3D4A3E`. The alert color used for the off-route banner and the delete icon is `#FFB4AB`, a pale red, with white banner text. Recording red is the platform `Color.Red`.

Touch targets are 44 dp. Screen edge margin is 16 dp. Gaps inside groups are 12 dp. Section gaps are 24 dp. Primary buttons are green fill, black capital letters, 4 dp corners. Outline buttons are a green stroke. The bottom navigation is black with a hairline top border. Map overlays use black at 80 to 90 percent opacity so the map remains slightly visible.

The launcher icon is a foreground image the project treats as the app logo. The notification small icon is a separate drawable, `ic_notification`.

---

## 9. Permissions and process

Manifest permissions:

- INTERNET, for tiles and for GPS assistance downloads performed by the system.
- ACCESS_NETWORK_STATE, for the online/offline tile decision.
- ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION. Requested together at launch. Denial is ignored; the next location request simply fails and the map shows no puck and em dashes.
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION, for the recording service.
- WAKE_LOCK is declared. The code that is actually used to keep the CPU/screen situation under control is the activity window flag, not an explicit wake lock in the service.

The recording service is not exported. Its foreground type is location. The notification opens `MainActivity` and is ongoing. There is no POST_NOTIFICATIONS request. On Android 13 and newer the notification may be hidden until the user allows notifications, while the service can still run.

The activity is exported because it is the launcher and because it receives shared files. It does not declare `android:grantUriPermissions` on itself; the stream is opened through the content resolver for the URI the system handed over.

Backup is allowed (`allowBackup=true`). Routes and tiles live in app-private storage, so a device backup may include the database and the tile cache depending on the phone’s backup rules. There is no custom backup rule file.

---

## 10. Worked example

A person finds a trail in AllTrails, exports GPX, and shares it to TrailMap GPS.

The activity opens the stream. The parser sees `<gpx`, reads track points in order, inherits elevation forward when a point omits it, computes horizontal distance, noisy gain and loss, max elevation, and a time at 3 km/h. The preview map fits the line. They tap Save & Download Maps. A row is inserted. The offline screen fits the route once. They zoom until the canyon is inside the green box and set detail to 15. The app lists every OpenTopoMap tile from zoom 10 through 15 inside that rectangle, skipping any zoom whose rectangle exceeds 5,000 tiles. Each missing PNG is fetched and stored under `offline_tiles/opentopo/...`. The route is flagged offline and the byte total is saved. The app then asks Android to inject XTRA and time, waits up to 20 seconds for a GPS fix, and stores the timestamp.

Later, in airplane mode, they open the route and tap Navigate. The map style is local. Tile requests hit the transform, which returns the files. The puck follows. The bottom line shows distance and bearing to the next vertex, GPS altitude, remaining distance from the nearest vertex to the end, and a progress fraction along the vertex list. If they wander more than 45 meters from every stored vertex, the pale-red banner appears. If they pan into a drainage they did not pack, that part of the map is dark and the green line continues.

---

## 11. Limits a reader should not paper over

- Online panning does not cache tiles. Only Download does.
- The import screen’s file-size and waypoint limits are text, not code.
- Named waypoints are stored and never drawn.
- Recorded tracks cannot be saved from the recording screen, and the recording timer does not run.
- The recording line is not shown on the map.
- Battery Saver still renders the full map under a scrim and has no exit-hike control.
- “Next waypoint” means the next vertex. Off route means 45 meters from the nearest vertex.
- Estimated time ignores terrain. Elevation gain has no smoothing.
- Route-detail unit captions stay on miles and feet when settings change.
- Keep-screen-on is global. Power-profile dimming does not exist.
- UTM and the coordinate setting are unused on screen, and the UTM formula is northern-hemisphere only.
- Deleting a route does not free tile disk space.
- A download that skips oversized zoom levels does so silently.
- The offline size on a route is the sum of file lengths touched by that download, not a measured folder size.
- GPS assistance success can be recorded when the phone only accepted the extra command and never got a fix.
- Hillshade’s tile server is a third-party URL (`tiles.wmflabs.org`) and is not part of OpenTopoMap. If that host is down, those tiles fail and the rest of the pack continues.
- Esri imagery is used as a personal, non-commercial basemap. The style carries an Esri attribution string.
- OpenStreetMap and OpenTopoMap require attribution; the style JSON includes attribution and MapLibre’s attribution control is enabled on the normal map.
- The app will not discover a trail, will not turn a user around along a graph, and will not replace AllTrails. It will follow the line it was given, on the tiles it was given, with the GPS the phone already has.
