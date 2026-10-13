/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.utils.world;

/** Mining acceleration without changing vanilla's instant-break packet path. */
public final class MiningMath {
    private MiningMath() { }

    public static float scale(float delta, double multiplier) {
        // Zero-hardness crops and decorations produce positive infinity in vanilla.
        if (delta == Float.POSITIVE_INFINITY) return delta;
        if (!Float.isFinite(delta) || delta <= 0) return 0;
        if (!Double.isFinite(multiplier) || multiplier < 0) return delta;
        double scaled = delta * multiplier;
        // A START packet alone cannot finish a block that the server considers non-instant.
        if (delta < 1 && scaled >= 1) return 0.9f;
        return (float) Math.min(scaled, Float.MAX_VALUE);
    }

    public static boolean reaches(float progress, float delta, double threshold) {
        return Float.isFinite(progress) && progress >= 0 && progress < 1
            && Float.isFinite(delta) && delta > 0
            && Double.isFinite(threshold) && threshold > 0 && threshold <= 1
            && (double) progress + delta + 1e-7 >= threshold;
    }

    public static float finishDelta(float progress, float delta, double threshold) {
        return reaches(progress, delta, threshold) ? Math.max(delta, Math.nextUp(1f - progress)) : delta;
    }

    /** Vanilla has already applied the stronger of Haste and Conduit Power. */
    public static double hasteMultiplier(int requestedLevel, int existingLevel) {
        double actual = 1 + Math.max(0, existingLevel) * 0.2;
        double desired = 1 + Math.max(0, requestedLevel) * 0.2;
        return Math.max(1, desired / actual);
    }
}
