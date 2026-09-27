# Stitch Prompt: Map Home Screen (v2 — fixes from field testing)

Design a mobile app screen for an Android hiking GPS app. This is the primary launch screen.

## Visual style
- True black OLED background (#000000) for all UI chrome
- Android Material 3, mountaineering aesthetic — serious, functional, NOT tourist
- USGS/OpenTopoMap-style topographic map fills entire screen
- No animations, no gradients, no glassmorphism
- Accent: trail green (#4ADE80)
- Large touch targets (44dp minimum)

## Metrics bar (CRITICAL — top of screen)
- A **single thin horizontal strip** floating over the map, below the status bar
- Maximum height 32dp — must NOT extend down the screen
- Semi-transparent black pill (#000000 at 82% opacity), rounded corners 8dp
- Three stats in one horizontal row, separated by green dots (·):
  - `5,240 ft` Elev · `2.3 mph` Speed · `247°` Brg
- White values, tiny gray labels
- This bar must be compact — NOT vertical dividers, NOT full-screen overlays

## Compass rose (top right, below metrics bar)
- 44dp circle button, black semi-transparent background
- **Small triangle arrow (▲) directly above the letter N**
- Arrow points toward geographic north
- In north-up mode: N and arrow are green (#4ADE80), arrow points to top of screen
- Rose rotates when map is rotated so N always points to real north

## Map
- Full-screen topo map edge to edge
- Bright green (#4ADE80) route line
- Blue GPS position dot with accuracy ring

## Right-side controls (vertical stack, center-right)
1. My Location — target/crosshair icon, black circle background
2. Import — green filled pill button with upload icon (primary action)
3. Draw route — pencil icon, black circle background

## Bottom navigation bar
- Fixed to bottom with **48dp padding below it** (clears Android 3-button nav)
- True black background
- 4 icons + labels: Routes | Record | Layers | Settings
- Green when active, gray when inactive

## Bottom right
- Small fullscreen expand icon button (black circle)

## Top left (optional, below metrics)
- Tiny GPS accuracy badge: green dot + "±12m"

## Do NOT include
- Vertical divider lines anywhere
- Full-height UI panels
- White backgrounds
- Paywalls or login screens
