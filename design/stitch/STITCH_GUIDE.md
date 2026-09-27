# Google Stitch — UI Redesign Guide

The app's GUI needs a proper visual design pass. Stitch generates mockup screens you review before we code them in. **Start here.**

## How to use Stitch

1. Go to [Google Stitch](https://stitch.withgoogle.com) (or open Stitch in Google Labs)
2. Create a new project: **TrailMap GPS**
3. Open each prompt file below, copy the full text, paste into Stitch
4. Generate the screen
5. Tell me what you like / don't like — I'll update the Android app to match

**Order matters** — do the Map Home screen first. Everything else follows its style.

---

## Prompt files

| # | Screen | File |
|---|--------|------|
| 1 | Map Home (most important) | [01-map-home.md](01-map-home.md) |
| 2 | Import flow | [02-import-flow.md](02-import-flow.md) |
| 3 | Route detail sheet | [03-route-detail.md](03-route-detail.md) |
| 4 | Navigation + Battery Saver | [04-navigation-battery-saver.md](04-navigation-battery-saver.md) |
| 5 | Routes, Layers, Offline, Settings | [05-remaining-screens.md](05-remaining-screens.md) |

---

## Critical design requirements (all screens)

These fix the problems you reported:

### Metrics bar
- **Single thin horizontal strip** at the top — NOT full-height dividers
- Max height ~32dp
- Format: `5,240 ft Elev · 2.3 mph Speed · 247° Brg`
- Semi-transparent black background (`#000000` at 80% opacity)
- Does NOT block the map — floats over it with small rounded corners

### Compass rose
- Circle button, top-right below metrics bar
- **Small triangle arrow above the letter N** — arrow points to geographic north
- When north-up mode: arrow points to top of screen, N is green
- When heading-up mode: whole rose rotates, N is white

### Bottom navigation
- Must sit **above** the Android 3-button nav bar (48dp bottom padding)
- Black background, 4 icons: Routes, Record, Layers, Settings

### Map controls (right edge, vertical stack)
- My Location (target icon)
- Import (green)
- Draw route (pencil)
- Fullscreen (bottom-right, separate)

### Color palette
- Background/chrome: `#000000` true black
- Accent: `#4ADE80` trail green
- Text: white / `#9CA3AF` gray labels
- No white panels, no gradients, no blur

---

## After Stitch

Once you have mockups you like:
1. Export screenshots or share links
2. Tell me "implement Stitch design" 
3. I'll rebuild the Compose UI to match pixel-for-pixel

The functional code (GPS, import, navigation) stays — only the visual layer changes.
