package com.limelight.binding.input.capture;

import android.view.InputDevice;
import android.view.MotionEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndroidNativePointerCaptureProviderTest {
    @Test
    public void disablesNativePointerCaptureForNillkinTouchpad() {
        assertTrue(AndroidNativePointerCaptureProvider.isPointerCaptureBlockedDevice(
                0x21CE, 0xB907));
        assertFalse(AndroidNativePointerCaptureProvider.isPointerCaptureBlockedDevice(
                0x21CE, 0xB908));
    }

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
}
