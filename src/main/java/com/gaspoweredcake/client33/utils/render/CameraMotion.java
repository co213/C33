package com.gaspoweredcake.client33.utils.render;

/** Bounded scroll targets and frame-rate independent third-person camera easing. */
public final class CameraMotion {
    public static final double MAX_DISTANCE = 64;
    private CameraMotion() {}

    public static double distance(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0, MAX_DISTANCE) : 4;
    }

    public static double scroll(double current, double wheel, double sensitivity) {
        current = distance(current);
        if (!Double.isFinite(wheel) || !Double.isFinite(sensitivity) || sensitivity <= 0) return current;
        double exponent = Math.clamp(-wheel * 0.25 * sensitivity, -20, 20);
        // The offset allows scrolling away from zero without a stuck camera.
        return distance((current + 0.5) * Math.exp(exponent) - 0.5);
    }

    public static double approach(double current, double target, double seconds, double speed) {
        current = distance(current); target = distance(target);
        if (!Double.isFinite(seconds) || seconds <= 0 || !Double.isFinite(speed) || speed <= 0) return current;
        double factor = -Math.expm1(-Math.min(seconds, 0.25) * speed);
        double value = current + (target - current) * factor;
        return Math.abs(value - target) < 0.0001 ? target : value;
    }
}
