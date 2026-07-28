package com.limelight;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TouchpadMouseDeltaAccumulatorTest {
    @Test
    public void appliesSpeedToBothAxes() {
        TouchpadMouseDeltaAccumulator accumulator = new TouchpadMouseDeltaAccumulator();

        assertEquals(2, accumulator.addX(4.0f, 0.5f));
        assertEquals(-2, accumulator.addY(-4.0f, 0.5f));
    }

    @Test
    public void retainsSlowMovementUntilReset() {
        TouchpadMouseDeltaAccumulator accumulator = new TouchpadMouseDeltaAccumulator();

        assertEquals(0, accumulator.addX(0.2f, 1.0f));
        assertEquals(0, accumulator.addX(0.2f, 1.0f));
        assertEquals(1, accumulator.addX(0.2f, 1.0f));

        accumulator.addX(0.4f, 1.0f);
        accumulator.addY(0.4f, 1.0f);
        accumulator.reset();

        assertEquals(0, accumulator.addX(0.2f, 1.0f));
        assertEquals(0, accumulator.addY(0.2f, 1.0f));
    }
}
