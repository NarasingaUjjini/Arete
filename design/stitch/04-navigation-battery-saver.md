# Stitch Prompt: Navigation Mode + Battery Saver Mode

Design TWO states of the in-field navigation screen for a hiking GPS Android app.

---

## State A: Standard Navigation Mode

### Visual style
- USGS topo map fills screen
- Minimal UI overlay on true black (#000000) translucent bars
- Green route line (#4ADE80), blue GPS dot
- No animations

### Layout
- Top bar (true black, semi-transparent):
  - Left: "← Exit" text button (white)
  - Center: route name "Angels Landing" (white, truncated)
  - Right: Battery Saver toggle icon (battery with leaf, white — OFF state)
- Map: full screen with route line and position
- Bottom stats bar (true black background):
  - 3 large stat blocks in a row:
    - "0.8 mi" / "To next waypoint" (white value, gray label)
    - "247°" / "Bearing" 
    - "5,240 ft" / "Elevation"
  - Below stats: thin progress bar showing route completion (green fill on gray track)
  - "2.4 mi remaining · 1h 12m elapsed" (small gray text, centered)
- Off-route warning banner (show as alternate variant):
  - Red (#EF4444) background bar: "⚠ Off route — 45m from trail"

---

## State B: Battery Saver Navigation Mode

### Visual style
- **True black (#000000) fills entire screen** — NO map tiles rendered
- Only vector elements on black canvas
- Maximum battery savings

### Layout
- Entire screen is pure black
- Thin green (#4ADE80) route line drawn as simple vector path (no map underneath)
- Small blue dot for GPS position on the route line
- Top-right: Battery Saver icon (green, ON state)
- Center-bottom: 3 stats only, large white numbers on black:
  - "0.8 mi" — distance to next waypoint
  - "5,240 ft" — current elevation  
  - "1h 12m" — time elapsed
- Small gray text below: "Battery Saver · Tap to exit"
- No top bar, no progress bar, no route name — absolute minimum UI
- Screen appears dimmed/darker overall (suggest 30% brightness)

## Device frame
- Android phone, portrait
- Show State A and State B as two separate frames side by side if possible

## Do NOT include
- Turn-by-turn street directions
- 3D terrain or hillshade
- Animated compass
- Voice guidance UI
