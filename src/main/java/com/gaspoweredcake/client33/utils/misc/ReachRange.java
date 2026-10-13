package com.gaspoweredcake.client33.utils.misc;

/** Bounded range composition that preserves native attributes when a channel is disabled. */
public final class ReachRange {
    public static final double MAX_RANGE = 64;
    private ReachRange() {}

    public static double resolve(double original, double configured, boolean total, boolean enabled) {
        double base = Double.isFinite(original) ? Math.max(0, original) : 0;
        if (!enabled) return base;
        if (!Double.isFinite(configured)) return base;
        double requested = Math.clamp(configured, -MAX_RANGE, MAX_RANGE);
        return Math.clamp(total ? requested : base + requested, 0, MAX_RANGE);
    }
}
