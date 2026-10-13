package com.gaspoweredcake.client33.utils.render;

/** Bounded path windows and honest progress within the current calculated segment. */
public final class PathViewMath {
    private PathViewMath() {}
    public static int start(int position, int size) {
        return size <= 0 ? 0 : (int) Math.clamp((long) position - 1, 0, size - 1);
    }
    public static int end(int start, int size, int budget) {
        return (int) Math.min(Math.max(0, size), (long) Math.max(0, start) + Math.clamp(budget, 2, 256));
    }
    public static double progress(int position, int size) {
        return size < 2 ? 0 : Math.clamp(position / (double) (size - 1), 0, 1);
    }
    public static double angle(double current, double target, double seconds, double speed) {
        if (!Double.isFinite(current)) current = 0;
        if (!Double.isFinite(target) || !Double.isFinite(seconds) || seconds <= 0 || !Double.isFinite(speed) || speed <= 0) return current;
        double delta = wrap(target - current);
        return wrap(current + delta * -Math.expm1(-Math.min(seconds, 0.25) * speed));
    }
    public static double pitch(double current, double target, double seconds, double speed) {
        current = Double.isFinite(current) ? Math.clamp(current, -90, 90) : 0;
        if (!Double.isFinite(target) || !Double.isFinite(seconds) || seconds <= 0 || !Double.isFinite(speed) || speed <= 0) return current;
        target = Math.clamp(target, -90, 90);
        return current + (target - current) * -Math.expm1(-Math.min(seconds, 0.25) * speed);
    }
    public static double wrap(double angle) {
        return Double.isFinite(angle) ? ((angle % 360 + 540) % 360) - 180 : 0;
    }
    public static double pulse(double distance, double time) {
        if (!Double.isFinite(distance + time)) return 0;
        double phase = (distance - time * 4) / 12;
        double wave = Math.sin(phase * Math.PI * 2);
        return Math.pow(Math.max(0, wave), 6);
    }
}
