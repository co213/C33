package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.misc.ReachRange;

public final class ReachRegression {
    public static void run() {
        check(ReachRange.resolve(4.5, 4, false, true) == 8.5, "Extra mode preserves native creative/attribute range");
        check(ReachRange.resolve(6, 8, true, true) == 8, "Total mode chooses final range rather than adding twice");
        check(ReachRange.resolve(6, 8, true, false) == 6, "Disabled channel/condition preserves native modifiers");
        check(ReachRange.resolve(4.5, -10, false, true) == 0, "Negative saved offsets cannot create negative reach");
        check(ReachRange.resolve(4.5, 1e300, false, true) == 64, "Huge settings are bounded without overflow");
        check(ReachRange.resolve(Double.MAX_VALUE, 64, false, true) == 64, "Huge native attributes remain bounded when customizing");
        check(ReachRange.resolve(4.5, Double.NaN, false, true) == 4.5, "Invalid configuration keeps native reach");
        check(ReachRange.resolve(Double.NaN, 8, true, true) == 8, "Total mode produces finite reach with invalid native input");
        check(ReachRange.resolve(4.5, 0, true, true) == 0, "Zero total is honored");
        double nativeRange = 4.5;
        for (int i = 0; i < 100; i++) check(ReachRange.resolve(nativeRange, 2, false, true) == 6.5, "Queries do not accumulate offsets or mutate native range");
        check(ReachRange.resolve(4.5, 4, false, true) != ReachRange.resolve(3, 0, false, true), "Block and entity channels compose independently");
        System.out.println("Reach native modifiers, total/extra modes, disabled channels and finite bound regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
