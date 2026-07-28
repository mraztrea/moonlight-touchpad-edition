package com.limelight;

final class TouchpadMouseDeltaAccumulator {
    private float remainderX;
    private float remainderY;

    int addX(float delta, float speedFactor) {
        remainderX += delta * speedFactor;
        int result = Math.round(remainderX);
        remainderX -= result;
        return result;
    }

    int addY(float delta, float speedFactor) {
        remainderY += delta * speedFactor;
        int result = Math.round(remainderY);
        remainderY -= result;
        return result;
    }

    void reset() {
        remainderX = 0;
        remainderY = 0;
    }
}
