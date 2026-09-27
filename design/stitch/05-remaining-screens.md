# Stitch Prompt: My Routes, Layer Picker, Offline Download, Settings

Design four screens/panels for a hiking GPS Android app. True black OLED theme throughout.

---

## Screen 1: My Routes Library

Full-screen list on true black (#000000) background.

### Top bar
- Title: "My Routes" (white)
- Search icon (right)

### Route list (scrollable)
Each row:
- Route name (white, bold): "Angels Landing Trail"
- Subtitle (gray #9CA3AF): "5.2 mi · 1,488 ft gain · Imported"
- Small badge pill: "GPX" (green outline) or "Recorded" (blue outline)
- Date: "Sep 1, 2026" (gray, right-aligned)
- Thin divider (#1F1F1F) between rows

Show 5 sample routes with varied names and sources.

### Empty state (show as small inset)
- Folder icon, "No routes yet"
- "Import a GPX file to get started" (gray)
- Green "Import" button

### Bottom nav bar
- Routes tab highlighted green (same as map home screen nav)

---

## Screen 2: Layer Picker

Bottom sheet panel on true black background, appearing over dimmed topo map.

### Content
- Title: "Map Layers" (white)
- **Base layer** section (radio buttons):
  - ● Topo (USGS) — selected, green dot
  - ○ Satellite (Esri)
  - ○ OpenStreetMap
- **Overlays** section (toggle switches):
  - Hillshade — OFF (gray toggle)
  - Contours on Satellite — OFF
- Preview thumbnail (small, 80x80) showing selected layer style
- "Done" button (green, full width)

---

## Screen 3: Offline Download

Full screen on true black, map visible in upper portion.

### Top bar
- Back arrow + "Download Offline Maps"

### Map area
- Route shown as green line
- Dashed rectangle (green border) showing download bounding box with padding around route
- Label: "Download area" (green text)

### Bottom panel (true black)
- "Angels Landing Trail" (white)
- Zoom range slider: "Zoom 10 — 15" with two handles
- Storage estimate: "Estimated size: 24 MB" (gray)
- Progress bar (for in-progress state): green fill, "Downloading... 67%"
- "Download" button (green, full width)
- Completed state: green checkmark + "Downloaded · 24 MB"

---

## Screen 4: Settings

Full-screen scrollable list on true black (#000000).

### Sections

**Units**
- Distance: mi / km (segmented control, mi selected)
- Elevation: ft / m (segmented control, ft selected)

**Coordinates**
- Format: Decimal / UTM (segmented control)

**Power Profile** (prominent section)
- Three-option segmented control:
  - Battery | **Balanced** | Accuracy
- Description text below (gray): "GPS every 3s · Screen stays on during navigation"

**Display**
- Keep screen on during navigation — toggle ON (green)
- Dark theme — always on, no toggle (grayed out, "Always on")

**About**
- "TrailMap GPS v1.0" (gray)
- "No account · No tracking · No ads" (gray, small)

## Global rules for all four screens
- True black backgrounds (#000000)
- No animations, no gradients, no glassmorphism
- Green accent (#4ADE80) for active/selected states
- Large touch targets (44dp)
- No login, no paywall, no premium badges
