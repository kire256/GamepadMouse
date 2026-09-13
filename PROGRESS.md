# GamepadMouse Progress

## Current milestone

Phase 2 input customization and keyboard-mode iteration.

## Implemented

- System-wide gamepad mouse mode through an AccessibilityService.
- Cursor movement, click/long-press, right-stick scrolling, and configurable bindings.
- Cursor style, size, color, and optional inactivity timeout.
- Audio cue packs and custom sound selection.
- Per-device profile backend and automatic controller detection.
- Keyboard mode MVP with an on-screen QWERTY/numeric/symbol overlay.
- `Keyboard Mode` is available in the binding picker's **General** category.
- The Status screen displays the installed version name and version code beneath the app title.
- Keyboard overlay width and height are adjustable (defaults: 80% × 45%) and bottom-centered for tablets/foldables.
- Keyboard mode retains the focusable controller capture surface, fixing non-responsive controls.
- Bindings support single buttons or combinations, mouse/gamepad/both mode targeting, and 0–2000 ms hold delays.
- Existing legacy bindings migrate to mouse-mode single-button bindings.

## Keyboard mode controls

- D-pad: move selection
- A: type selected key
- B: backspace
- X: space
- Y: shift
- LB/RB: change layout
- Start: enter
- Select/Back: return to mouse mode

## Verification

- Debug APK builds successfully with JDK 21.
- Debug APK installs on Samsung Galaxy Z Fold 6 (`RFGL23YPJLR`).
- Installed package: `com.droidforge.gamepadmouse.debug`.
- Current debug version: `0.5.2-cursor-assets-debug (9)`.
- The focusable capture view now forwards controller key events to the service, fixing D-pad keyboard navigation.
- A keyboard-mode binding toggles the keyboard off when pressed again.
- Binding recording shows a prominent “Listening for controller input…” card while active.
- Keyboard navigation supports both D-pad key events and controllers that expose the D-pad as hat axes.
- Keyboard options include a number row, system-key row, Shift, Caps Lock, Hide, top/bottom movement, and color presets.
- Settings includes an editable keyboard-input test field.
- Keyboard binding state resets across mode changes, preventing the toggle from becoming permanently latched.
- Optional automatic keyboard display opens on focused editable fields, with suppression after manual hide.
- Keyboard colors now include cyan, purple, pink, and slate presets.
- Navigation now separates Mouse, Keyboard, Bindings, and General sections.
- Keyboard-mode defaults are B = press selected key and A = hide; keyboard press/move/hide are bindable actions.
- Keyboard overlay is a bounded touchable window, allowing finger taps on keys without blocking touches outside it.
- Binding list includes a visible scroll hint panel.
- Version 7 is installed on the Fold; Settings are separated into Mouse, Keyboard, Bindings, and General navigation tabs.
- Keyboard defaults use B to press and A to hide, with bindable keyboard press/move/hide actions and a dedicated Keyboard binding mode.
- The bounded keyboard overlay accepts finger taps while leaving the rest of the screen touchable.
- Keyboard Mode bindings toggle the keyboard even when their configured mode is Mouse or Gamepad.
- New binding dialogs clear previous recording state and wait 250 ms before accepting controller input.
- Existing installations automatically gain keyboard defaults: B = press selected key, X = backspace, A = hide.
- Added three transparent image cursor styles from `C:/AI/Apps/arrows`: Blue Arrow, Blue Target, and 3D Blue Pointer.

## Next testing

1. Adjust keyboard width/height on the Fold and confirm readability in tablet and phone postures.
2. Test D-pad/A/B/X/Y/LB/RB/Start/Select in keyboard mode.
3. Test mouse-only, gamepad-only, and both-mode bindings.
4. Test two-button combinations with 0 ms and non-zero hold delays.
5. Validate character insertion, backspace, space, and enter in real editable fields.

## Known limitations

- Keyboard mode is an MVP and real text-field insertion still needs device testing across apps.
- Manual finger-tap cursor hiding was removed because touchable overlays blocked normal touch input and Samsung did not reliably emit the required accessibility touch events.
