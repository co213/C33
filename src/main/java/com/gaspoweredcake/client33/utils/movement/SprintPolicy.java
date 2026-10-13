package com.gaspoweredcake.client33.utils.movement;

/** Adds Rage movement choices without overriding Minecraft eligibility restrictions. */
public final class SprintPolicy {
    private SprintPolicy() {}

    public static boolean shouldSprint(boolean strict, boolean eligible, boolean stationary,
                                       float forward, float sideways, boolean underwater) {
        if (!eligible || !Float.isFinite(forward) || !Float.isFinite(sideways)) return false;
        if (strict) return true; // Native start/continuation rules already checked input.
        return stationary || Math.max(Math.abs(forward), Math.abs(sideways)) > (underwater ? 1.0E-5F : 0.8F);
    }
}
