package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.gui.renderer.RoundedRectGeometry;
import com.gaspoweredcake.client33.utils.render.TracerGeometry;
import com.gaspoweredcake.client33.utils.render.color.Color;
import org.joml.Vector3d;

public final class GuiRenderRegression {
    public static void run() {
        Vector3d start = new Vector3d(0, 1, 0), end = new Vector3d(0, 1, 100), sample = new Vector3d();
        check(TracerGeometry.point(start, end, 0, 1, sample).equals(start), "Ribbon starts at its origin");
        check(TracerGeometry.point(start, end, 1, 1, sample).equals(end), "Ribbon ends at its target");
        TracerGeometry.point(start, end, 0.5, 1, sample);
        check(sample.y == 4 && sample.z == 50, "Ribbon lift stays bounded at long distances");
        for (Vector3d camera : new Vector3d[] {new Vector3d(), new Vector3d(0, 1, 50)}) {
            Vector3d side = TracerGeometry.side(start, end, camera, new Vector3d(1, 0, 0), sample);
            check(Double.isFinite(side.x + side.y + side.z) && Math.abs(side.length() - 1) < 1e-9,
                "Camera-aligned ribbons must have finite unit-width vectors");
            check(Math.abs(side.z) < 1e-9, "Ribbon width is perpendicular to the path");
        }
        double[] a = Color.fromHsv(350, 1, 1).toHsv(), b = Color.fromHsv(10, 1, 1).toHsv();
        Color midpoint = TracerGeometry.color(a, b, 0.5, -1, 200, new Color());
        check(midpoint.r == 255 && midpoint.g < 10 && midpoint.b < 10, "HSV gradients take the short path across red");
        check(TracerGeometry.color(a, b, 0, 0.5, 200, midpoint).a == 0, "Ribbon origins fade away from the crosshair");
        for (int i = 0; i <= 48; i++) {
            TracerGeometry.color(a, b, i / 48.0, 0.5, 200, midpoint);
            check(midpoint.a >= 0 && midpoint.a <= 200, "Pulse opacity respects the configured limit");
        }
        check(TracerGeometry.phase(-0.25) == 0.75 && TracerGeometry.phase(Double.NaN) == 0,
            "Animation phase must wrap and remain finite");
        for (double[] dimensions : new double[][] {{100, 40, 6}, {400, 160, 24}, {8, 8, 20}, {1, 1, 0.5}, {100, 2, 1}}) {
            double width = dimensions[0], height = dimensions[1], radius = dimensions[2];
            double[] area = {0};
            boolean[] fringe = {false};
            RoundedRectGeometry.draw(10, 20, width, height, radius, (x0, y0, a0, x1, y1, a1, x2, y2, a2) -> {
                for (double[] point : new double[][] {{x0, y0}, {x1, y1}, {x2, y2}}) {
                    check(Double.isFinite(point[0] + point[1]), "Vertices must be finite");
                    check(point[0] >= 10 - 1e-9 && point[0] <= 10 + width + 1e-9
                        && point[1] >= 20 - 1e-9 && point[1] <= 20 + height + 1e-9,
                        "Coverage fringe must stay inside widget/scissor bounds");
                }
                double signed = ((x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)) / 2;
                check(signed <= 1e-9, "All nondegenerate triangles must share mesh winding");
                area[0] -= signed;
                fringe[0] |= a0 == 0 || a1 == 0 || a2 == 0;
            });
            double clampedRadius = Math.min(radius, Math.min(width, height) / 2);
            double polygonArea = width * height - (4 - 32 * Math.sin(Math.PI / 32)) * clampedRadius * clampedRadius;
            check(Math.abs(area[0] - polygonArea) < 1e-6, "Fill and fringe cover the rounded polygon without overlap");
            check(fringe[0], "Rounded surfaces must include transparent edge coverage");
        }
        for (double[] invalid : new double[][] {{0, 10, 2}, {-1, 10, 2}, {10, Double.NaN, 2}, {10, 10, Double.POSITIVE_INFINITY}}) {
            RoundedRectGeometry.draw(0, 0, invalid[0], invalid[1], invalid[2],
                (x0, y0, a0, x1, y1, a1, x2, y2, a2) -> { throw new AssertionError("Invalid shapes must emit no geometry"); });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
