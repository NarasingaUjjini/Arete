# Stitch Prompt: Import Flow Screen

Design a mobile app screen for importing a GPX hiking route file into an Android GPS app.

## Visual style
- True black OLED background (#000000) for all UI chrome
- Android Material 3, mountaineering aesthetic
- USGS topographic map visible in upper 60% of screen
- No animations, no gradients, no glassmorphism
- Accent color: trail green (#4ADE80)
- Large touch targets (44dp minimum)

## Layout — Import preview state (after file selected)

### Top bar (true black)
- Back arrow (left)
- Title: "Import Route" (white text)
- No other icons

### Map preview (upper portion)
- USGS topo map showing an imported route as a bright green (#4ADE80) line
- Route follows a mountain trail with switchbacks visible on topo contours
- Start point: green circle marker
- End point: checkered flag or square marker

### Bottom sheet (true black, slides up over map)
- Route name: "Angels Landing Trail" (large white text)
- Source badge: small pill label "Imported from GPX" in muted gray (#6B7280)
- Stats row (3 columns, white text):
  - "5.2 mi" / Distance
  - "1,488 ft" / Elevation gain
  - "3h 45m" / Est. time
- Elevation profile chart: simple line graph on black background, green line (#4ADE80), white axis labels, showing climb-then-descent shape
- Two action buttons (full width, stacked):
  - Primary: "Save Route" — green (#4ADE80) background, black text
  - Secondary: "Save & Download Offline Maps" — outlined green border, green text

## Alternate state — File picker landing
Show a simpler version with:
- Large upload icon centered
- Text: "Import a GPX, KML, or GeoJSON file"
- Subtext: "Share from AllTrails or tap to browse files"
- "Browse Files" button (green outlined)

## Device frame
- Android phone, portrait
- Dark status bar

## Do NOT include
- File format explanations or tutorials
- Account login
- Cloud sync options
- Route editing tools on this screen
