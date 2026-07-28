package com.limelight;

import android.view.InputDevice;
import android.view.MotionEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameInputRoutingTest {
    @Test
    public void relativeAxesNeverFallThroughToTouchpadCoordinateDeltas() {
        assertFalse(Game.shouldTryTouchpadRelativeMove(true));
        assertTrue(Game.shouldTryTouchpadRelativeMove(false));
    }

    @Test
    public void relativeMouseNeverUsesAbsoluteFallback() {
        assertFalse(Game.shouldUseAbsoluteMouseFallback(InputDevice.SOURCE_MOUSE_RELATIVE));
        assertTrue(Game.shouldUseAbsoluteMouseFallback(InputDevice.SOURCE_MOUSE));
    }

    @Test
    public void compositeKeyboardMouseIsATouchpadUnderPointerCapture() {
        int sources = InputDevice.SOURCE_KEYBOARD |
                InputDevice.SOURCE_MOUSE |
                InputDevice.SOURCE_JOYSTICK;

        assertTrue(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                sources, MotionEvent.TOOL_TYPE_FINGER, true, true, true));
    }

    @Test
    public void standaloneMouseIsNotATouchpad() {
        assertFalse(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE, true, true, true));
    }

    @Test
    public void gamepadAxesAreNotTouchpadAxes() {
        int sources = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_JOYSTICK;

        assertFalse(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                sources, MotionEvent.TOOL_TYPE_FINGER, true, false, false));
    }

    @Test
    public void compositeMouseAndPenToolsAreNotTouchpads() {
        int sources = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_MOUSE;

        assertFalse(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                sources, MotionEvent.TOOL_TYPE_MOUSE, true, true, true));
        assertFalse(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                sources, MotionEvent.TOOL_TYPE_STYLUS, true, true, true));
        assertFalse(Game.isTouchpadEvent(InputDevice.SOURCE_MOUSE_RELATIVE,
                sources, MotionEvent.TOOL_TYPE_ERASER, true, true, true));
    }

    @Test
    public void physicalMouseBypassesTouchpadSpeedScaling() {
        assertTrue(Game.shouldApplyTouchpadMouseSpeed(MotionEvent.TOOL_TYPE_FINGER));
        assertFalse(Game.shouldApplyTouchpadMouseSpeed(MotionEvent.TOOL_TYPE_MOUSE));
        assertFalse(Game.shouldApplyTouchpadMouseSpeed(MotionEvent.TOOL_TYPE_STYLUS));
        assertFalse(Game.shouldApplyTouchpadMouseSpeed(MotionEvent.TOOL_TYPE_ERASER));
        assertFalse(Game.shouldApplyTouchpadMouseSpeed(MotionEvent.TOOL_TYPE_UNKNOWN));
    }
}
