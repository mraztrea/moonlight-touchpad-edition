# Nillkin Trackpad Windows Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route Nillkin `SOURCE_MOUSE_RELATIVE` finger events to Moonlight's relative mouse path without controller swallowing, absolute-position fallback, lost slow movement, or stale gesture state.

**Architecture:** Keep the existing `Game` input pipeline. Relax only the native pointer-capture classifier, recognize external keyboard-touchpads by mouse-axis capabilities, add capability-based controller routing, funnel both delta paths through one allocation-free accumulator, and reset existing gesture fields at lifecycle boundaries.

**Tech Stack:** Android Java 11, Android SDK 34, JUnit 4, Gradle Android plugin 8.5.1.

## Global Constraints

- Preserve unscaled physical-mouse movement plus stylus, gamepad, touchscreen, and existing Xiaomi behavior.
- Reject stylus and eraser tools from the relative-mouse classifier.
- Never interpret `SOURCE_MOUSE_RELATIVE` deltas as absolute view coordinates.
- Do not add device-name or vendor/product fingerprints.
- Do not add a new runtime dependency.
- Physical Nillkin + Windows-host acceptance remains a hardware validation step.

---

### Task 1: Relative mouse classifier

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java`
- Test: `app/src/test/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProviderTest.java`

**Interfaces:**
- Produces: `static boolean isRelativeMouseEvent(int eventSource, int toolType)`

- [x] **Step 1: Add JUnit 4 test support**

```groovy
testImplementation 'junit:junit:4.13.2'
```

- [x] **Step 2: Write the failing classifier test**

```java
@Test
public void acceptsFingerRelativeMouseButRejectsPenTools() {
    assertTrue(AndroidNativePointerCaptureProvider.isRelativeMouseEvent(
            InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_FINGER));
    assertTrue(AndroidNativePointerCaptureProvider.isRelativeMouseEvent(
            InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_MOUSE));
    assertFalse(AndroidNativePointerCaptureProvider.isRelativeMouseEvent(
            InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_STYLUS));
    assertFalse(AndroidNativePointerCaptureProvider.isRelativeMouseEvent(
            InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.TOOL_TYPE_ERASER));
}
```

- [x] **Step 3: Run RED**

Run: `.\gradlew.bat testNonRootDebugUnitTest --tests com.limelight.binding.input.capture.AndroidNativePointerCaptureProviderTest`

Expected: compilation fails because `isRelativeMouseEvent` does not exist.

- [x] **Step 4: Implement the minimal classifier and use it**

```java
static boolean isRelativeMouseEvent(int eventSource, int toolType) {
    return eventSource == InputDevice.SOURCE_MOUSE_RELATIVE &&
            toolType != MotionEvent.TOOL_TYPE_STYLUS &&
            toolType != MotionEvent.TOOL_TYPE_ERASER;
}
```

`eventHasRelativeMouseAxes()` calls this helper, retaining the existing captured `SOURCE_TOUCHPAD` branch.

- [x] **Step 5: Run GREEN**

Run the focused test again and expect PASS.

### Task 2: Shared scaled delta accumulator

**Files:**
- Create: `app/src/main/java/com/limelight/TouchpadMouseDeltaAccumulator.java`
- Modify: `app/src/main/java/com/limelight/Game.java`
- Test: `app/src/test/java/com/limelight/TouchpadMouseDeltaAccumulatorTest.java`

**Interfaces:**
- Produces: `int addX(float delta, float speedFactor)`, `int addY(float delta, float speedFactor)`, `void reset()`
- Consumes: `Game.prefConfig.touchpadMouseSpeed`, existing Moonlight mouse send methods

- [x] **Step 1: Write failing tests for speed and retained subpixel movement**

```java
@Test
public void appliesSpeedAndRetainsSlowMovement() {
    TouchpadMouseDeltaAccumulator accumulator = new TouchpadMouseDeltaAccumulator();
    assertEquals(2, accumulator.addX(4.0f, 0.5f));
    accumulator.reset();
    assertEquals(0, accumulator.addX(0.2f, 1.0f));
    assertEquals(0, accumulator.addX(0.2f, 1.0f));
    assertEquals(1, accumulator.addX(0.2f, 1.0f));
}
```

- [x] **Step 2: Run RED**

Run: `.\gradlew.bat testNonRootDebugUnitTest --tests com.limelight.TouchpadMouseDeltaAccumulatorTest`

Expected: compilation fails because the accumulator does not exist.

- [x] **Step 3: Implement the allocation-free accumulator**

Each axis adds `delta * speedFactor`, rounds the accumulated value, subtracts the emitted integer, and returns it. `reset()` clears both remainders.

- [x] **Step 4: Route both delta paths through one Game helper**

Add `sendTouchpadMouseMove(float deltaX, float deltaY)`, which:

1. Applies `touchpadMouseSpeed / 100.0f` through the accumulator.
2. Sends non-zero clamped deltas through the existing absolute/relative Moonlight methods.
3. Returns whether a non-zero packet was sent.

The finger relative-axes branch is considered handled even for zero deltas, preventing fallthrough into `trySendTouchpadRelativeMove()`. The adapter path calls the same helper after computing its raw coordinate difference. `TOOL_TYPE_MOUSE` keeps Moonlight's original unscaled direct-send path.

- [x] **Step 5: Run GREEN**

Run the accumulator test and expect PASS.

### Task 3: Controller and absolute fallback guards

**Files:**
- Modify: `app/src/main/java/com/limelight/binding/input/ControllerHandler.java`
- Modify: `app/src/main/java/com/limelight/Game.java`
- Test: `app/src/test/java/com/limelight/binding/input/ControllerHandlerTest.java`
- Test: `app/src/test/java/com/limelight/GameInputRoutingTest.java`

**Interfaces:**
- Produces: `public static boolean hasJoystickAxes(InputDevice device)`
- Produces: `static boolean hasJoystickAxes(int sources, boolean hasXAxis, boolean hasYAxis)`
- Produces: `static boolean isTouchpadEvent(int eventSource, int deviceSources, int toolType, boolean isExternal, boolean hasMouseXRange, boolean hasMouseYRange)`
- Produces: `static boolean shouldApplyTouchpadMouseSpeed(int toolType)`
- Produces: `static boolean shouldUseAbsoluteMouseFallback(int eventSource)`

- [x] **Step 1: Write failing touchpad capability and routing tests**

```java
@Test
public void compositeKeyboardWithoutJoystickAxesIsNotAController() {
    int sources = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_MOUSE |
            InputDevice.SOURCE_JOYSTICK;
    assertFalse(ControllerHandler.hasJoystickAxes(sources, false, false));
    assertTrue(ControllerHandler.hasJoystickAxes(sources, true, true));
}

@Test
public void relativeMouseNeverUsesAbsoluteFallback() {
    assertFalse(Game.shouldUseAbsoluteMouseFallback(InputDevice.SOURCE_MOUSE_RELATIVE));
    assertTrue(Game.shouldUseAbsoluteMouseFallback(InputDevice.SOURCE_MOUSE));
}

@Test
public void compositeKeyboardMouseIsATouchpadUnderPointerCapture() {
    int sources = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_MOUSE |
            InputDevice.SOURCE_JOYSTICK;
    assertTrue(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
            sources, MotionEvent.TOOL_TYPE_FINGER, true, true, true));
}
```

- [x] **Step 2: Run RED**

Run the two focused test classes. Expected: compilation fails because the helpers do not exist.

- [x] **Step 3: Implement the touchpad and controller capability guards**

Classify an external `KEYBOARD + MOUSE` device as a touchpad only for `TOOL_TYPE_FINGER` when both X/Y mouse motion ranges are valid, and accept the `SOURCE_CLASS_TRACKBALL` class used by pointer capture. Expose the existing device-based `hasJoystickAxes()` and delegate its boolean expression to the pure overload. In `Game`, call `tryHandleTouchpadEvent()` only when `ControllerHandler.hasJoystickAxes(event.getDevice())` is true. Apply touchpad speed only to finger tools so physical mice retain their original direct-send speed.

- [x] **Step 4: Implement the absolute fallback guard**

```java
static boolean shouldUseAbsoluteMouseFallback(int eventSource) {
    return eventSource != InputDevice.SOURCE_MOUSE_RELATIVE;
}
```

Use it on the final `updateMousePosition()` fallback.

- [x] **Step 5: Run GREEN**

Run the two focused test classes and expect PASS.

### Task 4: Gesture lifecycle reset

**Files:**
- Modify: `app/src/main/java/com/limelight/Game.java`

**Interfaces:**
- Produces: `private void resetTouchpadGestureState()`
- Consumes: existing `cancelTouchpadTapDrag()`, `releaseTouchpadAltTab()`, mouse button-up send method, and delta accumulator

- [x] **Step 1: Implement one reset method**

Cancel the delayed tap-drag callback, release held left mouse and Alt states, clear all touchpad active/candidate flags, clear timing/coordinate/distance fields, reset scroll/pinch direction and remainders, and reset the shared delta accumulator.

- [x] **Step 2: Call it at lifecycle boundaries**

Call on `onWindowFocusChanged(false)`, before disabling input capture, and before stopping an active connection.

- [x] **Step 3: Run focused regression tests**

Run: `.\gradlew.bat testNonRootDebugUnitTest`

Expected: PASS.

### Task 5: Full verification

**Files:**
- Verify all files above.

- [x] **Step 1: Run Android lint**

Run: `.\gradlew.bat lintNonRootDebug`

Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Build debug APK**

Run: `.\gradlew.bat assembleNonRootDebug`

Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Inspect final diff**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; only the scoped source/test/build-plan files plus the user's pre-existing untracked documentation and Serena metadata.

- [ ] **Step 4: Hardware acceptance**

Install the non-root debug APK and validate the checklist from both bug reports on a Nillkin keyboard against a Windows host. This step cannot be proven by JVM/build verification alone.
