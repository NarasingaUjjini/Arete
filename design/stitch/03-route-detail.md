# Stitch Prompt: Route Detail Bottom Sheet

Design a bottom sheet overlay on a hiking GPS map screen showing details of an imported route before navigation.

## Visual style
- True black OLED (#000000) for sheet and UI chrome
- Map visible behind/above the sheet (USGS topo, dimmed slightly)
- Accent: trail green (#4ADE80)
- No animations, no gradients
- Material 3 bottom sheet with drag handle (small gray pill at top)

## Layout

### Map area (top 40%, still visible behind sheet)
- Green route line on topo map
- Blue GPS position dot

### Bottom sheet content (true black background)
- Drag handle (gray pill, centered)
- Route name: "Half Dome Trail" (white, 24sp bold)
- Source badge: "Imported from AllTrails" (gray pill, #6B7280 background)
- Stats row (4 items, evenly spaced):
  - 14.2 mi | Distance
  - 4,800 ft | Elev. gain
  - 10h 30m | Est. time
  - 8,842 ft | Max elev.
  - Labels in gray (#9CA3AF), values in white
- Elevation profile: full-width line chart
  - Black background
  - Green (#4ADE80) filled area under the line
  - White elevation labels on Y axis (ft)
  - Distance labels on X axis (mi)
  - Small vertical indicator dot showing current position on profile
- Action buttons (horizontal row, equal width):
  - **Navigate** — green filled button, play/arrow icon, black text. Primary.
  - **Download** — outlined green, download icon
  - **Delete** — outlined red (#EF4444), trash icon

## Device frame
- Android phone, portrait
- Sheet covers ~65% of screen height

## Do NOT include
- Reviews, ratings, or photos
- Social share buttons
- Edit route button (routes are imported as-is)
- Premium/upgrade prompts
