package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.world.FarmAttempts;

final class FarmRegression {
    static void run() {
        var attempts = new FarmAttempts<String, String>(2);
        check(attempts.ready("crop", "ripe", 0), "new crop is eligible");
        attempts.record("crop", "ripe", 0);
        check(!attempts.ready("crop", "ripe", 349), "server response gets time before retry");
        check(attempts.ready("crop", "air", 1), "harvest confirmation releases old state immediately");
        attempts.record("crop", "ripe", 350);
        attempts.record("crop", "ripe", 700);
        check(!attempts.ready("crop", "ripe", 5699), "three rejected actions back off for five seconds");
        check(attempts.ready("crop", "ripe", 5700), "temporary server rejection can recover");
        attempts.record("crop", "ripe", 5700);
        check(attempts.ready("crop", "ripe", 6050), "new retry burst resets attempt counter");
        attempts.record("soil", "empty", 6000);
        attempts.record("third", "empty", 6000);
        check(attempts.size() == 2 && attempts.ready("crop", "ripe", 6000), "moving through farms keeps memory bounded");
        attempts.clear();
        check(attempts.size() == 0 && attempts.ready("soil", "empty", 6001), "mode and world reset release pending attempts");
        System.out.println("Farm retry regressions passed.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
