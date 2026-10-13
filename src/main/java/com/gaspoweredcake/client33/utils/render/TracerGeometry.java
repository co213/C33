package com.gaspoweredcake.client33.utils.render;

import com.gaspoweredcake.client33.utils.render.color.Color;
import org.joml.Vector3d;

/** Allocation-free sampling and camera-facing ribbon math. */
public final class TracerGeometry {
    private TracerGeometry() {}

    public static Vector3d point(Vector3d start, Vector3d end, double t, double arc, Vector3d out) {
        t = Math.clamp(t, 0, 1);
        double lift = Math.min(3, start.distance(end) * 0.06) * arc;
        return out.set(start).lerp(end, t).add(0, 4 * t * (1 - t) * lift, 0);
    }

    public static Vector3d side(Vector3d a, Vector3d b, Vector3d camera, Vector3d fallback, Vector3d out) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double vx = (a.x + b.x) / 2 - camera.x;
        double vy = (a.y + b.y) / 2 - camera.y;
        double vz = (a.z + b.z) / 2 - camera.z;
        out.set(dy * vz - dz * vy, dz * vx - dx * vz, dx * vy - dy * vx);
        if (out.lengthSquared() < 1e-12) out.set(fallback);
        if (out.lengthSquared() < 1e-12) out.set(1, 0, 0);
        return out.normalize();
    }

    public static double phase(double time) { return Double.isFinite(time) ? time - Math.floor(time) : 0; }

    public static Color color(double[] from, double[] to, double t, double pulse, int alpha, Color out) {
        t = Math.clamp(t, 0, 1);
        double hueDelta = ((to[0] - from[0] + 540) % 360) - 180;
        double h = phase((from[0] + hueDelta * t) / 360);
        double d = t - pulse;
        double light = pulse < 0 ? 0 : Math.exp(-d * d / 0.0016);
        double s = (from[1] + (to[1] - from[1]) * t) * (1 - light * 0.18);
        double v = Math.min(1, from[2] + (to[2] - from[2]) * t + light * 0.16);
        int packed = java.awt.Color.HSBtoRGB((float) h, (float) s, (float) v);
        return out.set(Color.toRGBAR(packed), Color.toRGBAG(packed), Color.toRGBAB(packed),
            (int) Math.round(alpha * Math.min(1, t * 6) * (0.72 + light * 0.28)));
    }
}
