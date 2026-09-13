# GamepadMouse Progress

## Current milestone

Phase 2 input customization and keyboard-mode MVP.

## Implemented

- System-wide gamepad mouse mode through an AccessibilityService.
- Cursor movement, click/long-press, right-stick scrolling, and configurable bindings.
- Cursor style, size, color, and optional inactivity timeout.
- Audio cue packs and custom sound selection.
- Per-device profile backend and automatic controller detection.
- Keyboard mode MVP with an on-screen QWERTY/numeric/symbol overlay.
- `Keyboard Mode` is available in the binding picker's **General** category.
- The Status screen displays the installed version name and version code beneath the app title.

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
- Current debug version: `0.2.0-keyboard-mvp-debug (2)`.

## Next testing

1. Confirm `Keyboard Mode` appears at the bottom of the General binding-action list.
2. Bind it to an unused controller button.
3. Test entering/exiting keyboard mode.
4. Validate character insertion, backspace, space, and enter in real editable fields.
5. Fix any app-specific accessibility text-entry limitations found during device testing.

## Known limitations

- Keyboard mode is an MVP and real text-field insertion still needs device testing across apps.
- Manual finger-tap cursor hiding was removed because touchable overlays blocked normal touch input and Samsung did not reliably emit the required accessibility touch events.
