package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.render.CameraMotion;

public final class CameraRegression {
    public static void run() {
        check(CameraMotion.scroll(4, 100, 10) == 0, "Large inward scrolling cannot make camera distance negative");
        check(CameraMotion.scroll(4, -100, 10) == 64, "Large outward scrolling stays bounded");
        check(CameraMotion.scroll(0, -1, 1) > 0, "A zero-distance camera must recover by scrolling outward");
        double changed = CameraMotion.scroll(4, 1, 1);
        check(changed > 0 && changed < 4, "Inward scroll decreases distance");
        check(Math.abs(CameraMotion.scroll(changed, -1, 1) - 4) < 1e-12, "Reverse scrolling restores an unclamped target");
        check(CameraMotion.scroll(4, Double.NaN, 1) == 4, "Invalid wheel input must not poison camera state");
        check(CameraMotion.scroll(4, 1, Double.POSITIVE_INFINITY) == 4, "Invalid sensitivity must not poison camera state");
        check(CameraMotion.scroll(4, 1, 0) == 4, "Disabled sensitivity leaves the target unchanged");
        double at60 = 4, at144 = 4;
        for (int i = 0; i < 60; i++) at60 = CameraMotion.approach(at60, 10, 1.0 / 60, 8);
        for (int i = 0; i < 144; i++) at144 = CameraMotion.approach(at144, 10, 1.0 / 144, 8);
        check(Math.abs(at60 - at144) < 1e-10, "Camera easing should not depend on refresh rate");
        check(at60 > 4 && at60 <= 10, "Easing approaches its target without overshoot");
        check(CameraMotion.approach(4, 10, 0, 8) == 4, "Paused frame time must not advance easing");
        check(CameraMotion.approach(4, 10, Double.NaN, 8) == 4, "Invalid frame time must not advance easing");
        check(CameraMotion.approach(4, 10, 10, 8) == CameraMotion.approach(4, 10, 0.25, 8), "Long stalls must be bounded");
        check(CameraMotion.distance(-1) == 0 && CameraMotion.distance(1000) == 64,
            "Camera settings stay in the supported range");
        check(CameraMotion.distance(Double.NaN) == 4, "Invalid settings use a finite default");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
