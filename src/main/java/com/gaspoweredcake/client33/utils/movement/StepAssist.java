package com.gaspoweredcake.client33.utils.movement;

import java.util.function.DoublePredicate;

/** Bounded normal-jump obstacle probes and movement input rotation. */
public final class StepAssist {
    public static final double JUMP_RISE = 1.25;
    private StepAssist() {}
    public record Direction(double x, double z) {
        public boolean moving() { return x * x + z * z > 1e-8; }
    }
    public static Direction direction(double yaw, double strafe, double forward) {
        if (!Double.isFinite(yaw + strafe + forward)) return new Direction(0, 0);
        double length = Math.hypot(strafe, forward);
        if (length < 1e-8) return new Direction(0, 0);
        double angle = Math.toRadians(yaw), sin = Math.sin(angle), cos = Math.cos(angle);
        return new Direction((strafe * cos - forward * sin) / length, (forward * cos + strafe * sin) / length);
    }
    public static double height(double requested) {
        return Double.isFinite(requested) ? Math.clamp(requested, 0, 4) : 0;
    }
    /** Zero means no climbable obstacle. The full jump arc also needs headroom. */
    public static double jumpRise(double requested, double nativeHeight, DoublePredicate clearAtRise) {
        double maximum = Math.min(height(requested), JUMP_RISE);
        if (!Double.isFinite(nativeHeight) || maximum <= nativeHeight || clearAtRise.test(0) || clearAtRise.test(nativeHeight) || !clearAtRise.test(JUMP_RISE)) return 0;
        for (double rise = 1.0 / 16; rise < maximum; rise += 1.0 / 16) {
            if (rise > nativeHeight && clearAtRise.test(rise)) return rise;
        }
        return clearAtRise.test(maximum) ? maximum : 0;
    }
}
