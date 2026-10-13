package com.gaspoweredcake.client33.utils.combat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Hitscan aim math and configurable server/weapon matching, independent of a gun plugin. */
public final class GunAim {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");
    private GunAim() {}
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Solution(double yaw, double pitch, double distance) {}

    public static Solution solve(Point eye, Point target) {
        if (!eye.finite() || !target.finite()) return null;
        double x = target.x - eye.x, y = target.y - eye.y, z = target.z - eye.z;
        double horizontal = Math.hypot(x, z), distance = Math.hypot(horizontal, y);
        if (!Double.isFinite(distance) || distance < 1e-6) return null;
        return new Solution(Math.toDegrees(Math.atan2(z, x)) - 90,
            -Math.toDegrees(Math.atan2(y, horizontal)), distance);
    }

    public static Point predict(Point point, Point velocityPerTick, double ticks) {
        if (!point.finite() || !velocityPerTick.finite() || !Double.isFinite(ticks) || ticks <= 0) return point;
        double speed = Math.hypot(Math.hypot(velocityPerTick.x, velocityPerTick.z), velocityPerTick.y);
        // Discontinuous teleports are not usable velocity observations.
        if (speed > 1.5) return point;
        ticks = Math.clamp(ticks, 0, 4);
        double lead = speed * ticks;
        double factor = lead > 0.75 ? ticks * 0.75 / lead : ticks;
        return new Point(point.x + velocityPerTick.x * factor, point.y + velocityPerTick.y * factor, point.z + velocityPerTick.z * factor);
    }

    public static double angleError(double yaw, double currentYaw) {
        if (!Double.isFinite(yaw) || !Double.isFinite(currentYaw)) return 180;
        return Math.abs(((yaw - currentYaw) % 360 + 540) % 360 - 180);
    }

    public static boolean inCone(Solution solution, double yaw, double fov) {
        return solution != null && Double.isFinite(fov) && fov > 0 && angleError(solution.yaw, yaw) <= Math.min(360, fov) / 2;
    }

    public static boolean serverMatches(String address, List<String> configuredHosts) {
        String host = host(address);
        if (host.isEmpty()) return false;
        for (String value : configuredHosts) {
            String allowed = host(value);
            if (!allowed.isEmpty() && (host.equals(allowed) || host.endsWith("." + allowed))) return true;
        }
        return false;
    }
    private static String host(String value) {
        if (value == null) return "";
        String host = value.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host;
    }

    public static boolean weaponMatches(String displayName, List<String> aliases) {
        String name = " " + normalize(displayName) + " ";
        for (String alias : aliases) {
            String normalized = normalize(alias);
            if (!normalized.isEmpty() && name.contains(" " + normalized + " ")) return true;
        }
        return false;
    }
    private static String normalize(String value) {
        if (value == null) return "";
        value = FORMATTING.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        boolean space = true;
        for (int offset = 0; offset < value.length() && result.length() < 512;) {
            int cp = value.codePointAt(offset); offset += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) { result.appendCodePoint(cp); space = false; }
            else if (!space) { result.append(' '); space = true; }
        }
        return result.toString().trim();
    }
}
