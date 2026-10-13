package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.world.MiningMath;

final class MiningRegression {
    static void run() {
        check(!MiningMath.reaches(0, 0.6f, 0.7), "instant mining must respect configured threshold rather than old 0.5");
        check(MiningMath.reaches(0, 0.7f, 0.7), "float roundoff must not exclude exact threshold");
        check(!MiningMath.reaches(0, 0.8f, 0.9), "higher threshold limits one-click mining");
        check(MiningMath.reaches(0.4f, 0.3f, 0.7), "accumulated mining reaches same threshold");
        float finishing = MiningMath.finishDelta(0.4f, 0.3f, 0.7);
        check(0.4f + finishing >= 1, "promoted delta completes vanilla mining and reset in the same call");
        check(MiningMath.finishDelta(0.2f, 0.3f, 0.7) == 0.3f, "below threshold preserves ordinary progress");
        for (float invalid : new float[]{0, -1, Float.NaN, Float.NEGATIVE_INFINITY}) {
            check(!MiningMath.reaches(0.7f, invalid, 0.7), "unbreakable or invalid delta cannot finish a target");
            check(MiningMath.scale(invalid, 4) == 0, "boost cannot mine unbreakable or invalid states");
        }
        check(MiningMath.scale(Float.POSITIVE_INFINITY, 1.4) == Float.POSITIVE_INFINITY, "zero-hardness crops retain vanilla instant breaking");
        check(!MiningMath.reaches(Float.NaN, 0.8f, 0.7), "invalid accumulated progress cannot finish");
        check(!MiningMath.reaches(-0.1f, 0.8f, 0.7), "stale negative progress cannot finish");
        check(!MiningMath.reaches(1, 0.8f, 0.7), "already-finished progress is not accelerated again");
        check(close(MiningMath.scale(0.1f, 1.4), 0.14), "normal multiplier applies to actual target delta");
        check(close(MiningMath.scale(0.8f, 2), 0.9), "boost cannot select unsupported START-only instant-break path");
        check(MiningMath.scale(1.1f, 2) >= 1, "naturally instant blocks remain instant");
        check(MiningMath.scale(0.4f, 0) == 0, "zero multiplier deliberately pauses mining");
        check(Float.isFinite(MiningMath.scale(2, Double.MAX_VALUE)), "large settings cannot overflow float progress");
        check(close(MiningMath.hasteMultiplier(2, 0), 1.4), "requested Haste II equivalence");
        check(close(MiningMath.hasteMultiplier(2, 1), 1.4 / 1.2), "existing Haste is supplemented without stacking twice");
        check(MiningMath.hasteMultiplier(2, 3) == 1, "stronger real Haste or Conduit Power remains unchanged");
        System.out.println("Mining acceleration regressions passed.");
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-6; }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
