# Nillkin trackpad on Windows host (2026-07-28)

## Root cause
- Under pointer capture, Nillkin reports `SOURCE_MOUSE_RELATIVE` with `TOOL_TYPE_FINGER`.
- `AndroidNativePointerCaptureProvider.eventHasRelativeMouseAxes()` previously required `TOOL_TYPE_MOUSE`, so the event missed relative handling.
- Composite keyboard sources include JOYSTICK without real joystick axes, allowing controller routing to swallow events.
- Unhandled relative deltas could reach the absolute view-position fallback and pin the Windows cursor near the top-left.
- The direct relative path skipped touchpad speed/remainder handling and zero deltas could fall through to the coordinate-delta adapter.

## Implemented pattern
- Accept relative mouse events except stylus/eraser in the capture provider.
- Classify external composite keyboard-touchpads by `KEYBOARD + MOUSE`, valid X/Y mouse ranges, `TOOL_TYPE_FINGER`, and TRACKBALL event class; reuse `ControllerHandler.isExternal()` for minSdk compatibility.
- Require `ControllerHandler.hasJoystickAxes(device)` before controller touchpad routing.
- Never use absolute fallback for `SOURCE_MOUSE_RELATIVE`.
- Use `TouchpadMouseDeltaAccumulator` for finger/touchpad deltas only; physical mouse/unknown tools retain the original unscaled direct-send path.
- Reset gesture/button/Alt/remainder state on focus loss, capture disable, and connection stop.

## Verification
- TDD RED/GREEN observed for classifier, accumulator, routing guards, composite tool filtering, and mouse-speed bypass.
- `testNonRootDebugUnitTest`: 11 tests, 0 failures/errors/skips.
- `lintNonRootDebug`: pass.
- `assembleNonRootDebug`: pass using Java 21 and `HOST_OS=windows` in context-mode sandbox.
- APK: `app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk`.
- Physical Nillkin + Windows-host acceptance remains pending and must cover movement, slow movement, gestures, focus/reconnect, external mouse/gamepad/stylus regressions, and a long stream.