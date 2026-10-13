package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;

/** Boundaries shared by the HSV editor, theme palette and existing render colors. */
public final class ColorRegression {
    public static void run() {
        check(Color.fromHsv(0, 1, 1).equals(new Color(255, 0, 0)), "Hue zero is red");
        check(Color.fromHsv(360, 1, 1).equals(Color.fromHsv(0, 1, 1)), "Hue seam wraps to red");
        check(Color.fromHsv(-60, 1, 1).equals(Color.fromHsv(300, 1, 1)), "Negative hue wraps");
        check(Color.fromHsv(780, 1, 1).equals(new Color(255, 255, 0)), "Multiple hue turns wrap");
        check(Color.fromHsv(240, 0, 0.5).equals(new Color(128, 128, 128)), "Zero saturation is gray");
        check(Color.fromHsv(120, 1, 0).equals(new Color(0, 0, 0)), "Zero brightness is black");
        check(Color.fromHsv(120, 2, 2).equals(new Color(0, 255, 0)), "Saturation and brightness clamp");
        check(Color.fromHsv(120, -1, -1).equals(new Color(0, 0, 0)), "Negative components clamp");
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            rejects(invalid, 1, 1);
            rejects(0, invalid, 1);
            rejects(0, 1, invalid);
        }
        int[] channels = {0, 1, 27, 64, 128, 191, 254, 255};
        for (int r : channels) for (int g : channels) for (int b : channels) {
            Color original = new Color(r, g, b, 73);
            double[] hsv = original.toHsv();
            check(hsv[0] >= 0 && hsv[0] < 360 && hsv[1] >= 0 && hsv[1] <= 1
                && hsv[2] >= 0 && hsv[2] <= 1, "HSV values stay in the editor range");
            check(Color.fromHsv(hsv[0], hsv[1], hsv[2], original.a).equals(original),
                "RGB to HSV round-trip preserves saved colors and alpha");
        }
        SettingColor animated = new SettingColor(1, 2, 3, 73, true);
        animated.set(Color.fromHsv(350, 0.52, 0.92, animated.a));
        check(animated.a == 73 && animated.rainbow, "HSV editing preserves opacity and rainbow");
        check(Color.fromHsv(0, 1, 1, -1).a == 0 && Color.fromHsv(0, 1, 1, 300).a == 255,
            "Alpha remains bounded");
    }

    private static void rejects(double h, double s, double v) {
        try {
            Color.fromHsv(h, s, v);
            throw new AssertionError("Non-finite HSV must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
