package com.gaspoweredcake.client33.gui.renderer;

/** Convex GUI geometry with a one-pixel coverage fringe inside the requested bounds. */
public final class RoundedRectGeometry {
    private static final int SEGMENTS = 16;
    private static final int POINTS = 4 * (SEGMENTS + 1);
    private static final double[] COS = new double[POINTS], SIN = new double[POINTS];
    static {
        for (int i = 0; i < POINTS; i++) {
            double angle = (i / (SEGMENTS + 1) + (i % (SEGMENTS + 1)) / (double) SEGMENTS) * Math.PI / 2;
            COS[i] = Math.cos(angle);
            SIN[i] = Math.sin(angle);
        }
    }

    @FunctionalInterface
    public interface TriangleSink {
        void triangle(double x0, double y0, int a0, double x1, double y1, int a1,
                      double x2, double y2, int a2);
    }

    private RoundedRectGeometry() {}

    public static void draw(double x, double y, double width, double height, double radius, TriangleSink sink) {
        if (!(width > 0 && height > 0) || !Double.isFinite(x + y + width + height + radius)) return;
        radius = Math.clamp(radius, 0, Math.min(width, height) / 2);
        double inset = Math.min(1, Math.min(width, height) / 2);
        double innerRadius = Math.max(0, radius - inset);
        double centerX = x + width / 2, centerY = y + height / 2;
        for (int i = 0; i < POINTS; i++) {
            int next = (i + 1) % POINTS;
            double ox0 = pointX(i, x, width, radius), oy0 = pointY(i, y, height, radius);
            double ox1 = pointX(next, x, width, radius), oy1 = pointY(next, y, height, radius);
            double ix0 = pointX(i, x + inset, width - inset * 2, innerRadius);
            double iy0 = pointY(i, y + inset, height - inset * 2, innerRadius);
            double ix1 = pointX(next, x + inset, width - inset * 2, innerRadius);
            double iy1 = pointY(next, y + inset, height - inset * 2, innerRadius);
            sink.triangle(centerX, centerY, 1, ix1, iy1, 1, ix0, iy0, 1);
            sink.triangle(ix0, iy0, 1, ix1, iy1, 1, ox1, oy1, 0);
            sink.triangle(ix0, iy0, 1, ox1, oy1, 0, ox0, oy0, 0);
        }
    }

    private static double pointX(int index, double x, double width, double radius) {
        int corner = index / (SEGMENTS + 1);
        return x + (corner == 0 || corner == 3 ? width - radius : radius) + COS[index] * radius;
    }

    private static double pointY(int index, double y, double height, double radius) {
        int corner = index / (SEGMENTS + 1);
        return y + (corner < 2 ? height - radius : radius) + SIN[index] * radius;
    }
}
