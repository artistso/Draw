# SoCreate — Professional Animation Studio

**For Samsung Galaxy Tab S10+ and all Android tablets (API 31+).**

## Quick Start
```bash
cd SoCreate
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## What's Inside (855 lines, single file)

### Drawing
- 7 tools: Brush, Pencil, Ink, Airbrush, Eraser, Fill, Liquify
- Adaptive stroke stabilizer with velocity tracking
- Custom pressure curve remapping
- Size, hardness, opacity, scatter controls
- Hard/soft edge rendering

### Animation
- 24-frame timeline with spiral layout
- Playback at 8/12/24/30/60 FPS
- Color-coded onion skinning (prev blue, next red)
- Frame add/remove/duplicate/clear
- Multi-frame selection (Shift+click)

### Algorithmic Tweening
- 9 easing curves: Smoothstep, Linear, Ease In/Out, Ease In-Out, Bounce, Elastic, Back In, Back Out
- Stroke interpolation with point resampling and color blending
- Configurable frame count

### Character Rigging
- Auto-rig: 16 joints, 13 bones (full humanoid skeleton)
- CCD Inverse Kinematics solver
- Rig overlay with joint selection
- IK chain targets

### Selection & Transform
- Select all, delete selection
- Move/scale selection with bounding box + 8 handles
- Visual selection indicators

### 3D Perspective Box
- Trapezoidal foreground/background/side walls
- Horizon line + vanishing point
- Ground plane

### Modifiers
- Dedicated Shift / Ctrl / Alt toggle bar
- 200-step Undo/Redo with full stroke snapshots
- Stylus-only mode
- Grid + Perspective toggles

### Layers
- 5 layers: Background, Line Art, Color, Effects, Overlay
- Per-layer per-frame stroke storage

### Projects
- Save/Load with full data fidelity
- Gallery panel (socreate)
- PNG frame sequence export

## Architecture
- 2 Kotlin files: MainActivity.kt (27 lines) + StudioApp.kt (855 lines)
- Single ViewModel: StudioState
- Compose Material 3
- Zero API calls. Zero telemetry. All local.
