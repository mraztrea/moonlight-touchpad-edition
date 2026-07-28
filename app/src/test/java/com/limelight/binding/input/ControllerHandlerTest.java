package com.limelight.binding.input;

import android.view.InputDevice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControllerHandlerTest {
    @Test
    public void compositeKeyboardWithoutJoystickAxesIsNotAController() {
        int sources = InputDevice.SOURCE_KEYBOARD |
                InputDevice.SOURCE_MOUSE |
                InputDevice.SOURCE_JOYSTICK;

        assertFalse(ControllerHandler.hasJoystickAxes(sources, false, false));
        assertTrue(ControllerHandler.hasJoystickAxes(sources, true, true));
    }
}
