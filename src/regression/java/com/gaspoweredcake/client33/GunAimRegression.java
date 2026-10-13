package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.combat.GunAim;
import java.util.List;

/** Gun profile boundaries and hitscan coordinate conventions without a server plugin. */
public final class GunAimRegression {
    public static void run() {
        var eye = new GunAim.Point(0, 0, 0);
        var south = GunAim.solve(eye, new GunAim.Point(0, 0, 10));
        check(south.yaw() == 0 && south.pitch() == 0 && south.distance() == 10, "South is yaw zero");
        check(GunAim.solve(eye, new GunAim.Point(10, 0, 0)).yaw() == -90, "East is negative yaw");
        check(GunAim.solve(eye, new GunAim.Point(-10, 0, 0)).yaw() == 90, "West is positive yaw");
        check(GunAim.angleError(GunAim.solve(eye, new GunAim.Point(0, 0, -10)).yaw(), 180) == 0, "North wraps correctly");
        check(GunAim.solve(eye, new GunAim.Point(0, 10, 0)).pitch() == -90, "Up is negative pitch");
        check(GunAim.solve(eye, new GunAim.Point(0, -10, 0)).pitch() == 90, "Down is positive pitch");
        check(GunAim.solve(eye, eye) == null && GunAim.solve(eye, new GunAim.Point(Double.NaN, 0, 0)) == null, "Invalid/coincident points are rejected");
        check(GunAim.solve(new GunAim.Point(-Double.MAX_VALUE, 0, 0), new GunAim.Point(Double.MAX_VALUE, 0, 0)) == null, "Overflow cannot emit an invalid angle");
        check(GunAim.angleError(179, -179) == 2 && GunAim.angleError(-721, 359) == 0, "Yaw seam and multiple revolutions use the shortest arc");
        check(GunAim.inCone(south, 45, 90) && !GunAim.inCone(south, 45.01, 90), "Acquisition cone includes its exact boundary");
        check(GunAim.inCone(south, 180, 360) && !GunAim.inCone(south, 0, 0) && !GunAim.inCone(null, 0, 360), "Full cone and invalid inputs");
        var walking = new GunAim.Point(0.1, 0, 0);
        check(GunAim.predict(eye, walking, 0).equals(eye), "Default direct hitscan has no lead");
        check(Math.abs(GunAim.predict(eye, walking, 100).x() - 0.4) < 1e-12, "Prediction horizon is capped");
        var lead = GunAim.predict(eye, new GunAim.Point(0.5, 0.5, 0.5), 4);
        check(Math.abs(Math.hypot(Math.hypot(lead.x(), lead.z()), lead.y()) - 0.75) < 1e-12, "Three-dimensional lead is bounded");
        check(GunAim.predict(eye, new GunAim.Point(2, 0, 0), 1).equals(eye), "Teleport deltas cannot become prediction");
        check(GunAim.predict(eye, walking, Double.NaN).equals(eye), "Invalid horizons do not contaminate aim");
        var hosts = List.of("cherry.pizza");
        check(GunAim.serverMatches("MODRINTH.CHERRY.PIZZA.:25565", hosts), "Subdomains, casing, port and trailing dot are accepted");
        check(GunAim.serverMatches("cherry.pizza", hosts), "Exact host accepted");
        check(!GunAim.serverMatches("fakecherry.pizza", hosts) && !GunAim.serverMatches("cherry.pizza.fake.example", hosts), "Suffix lookalikes cannot enable the profile");
        check(!GunAim.serverMatches(null, hosts) && !GunAim.serverMatches("cherry.pizza", List.of("")), "Missing hosts cannot match");
        var guns = List.of("AK-47", "AWP", "Desert Eagle", "武器");
        check(GunAim.weaponMatches("§aStatTrak AK-47 | Forest (Factory New)", guns), "Formatted skins match a whole gun alias");
        check(GunAim.weaponMatches("desert EAGLE [skin]", guns) && GunAim.weaponMatches("★ 武器 ★", guns), "Multiword and Unicode aliases work");
        check(!GunAim.weaponMatches("AWPremium", guns) && !GunAim.weaponMatches("AK-470", guns), "Partial weapon names do not activate aim");
        check(!GunAim.weaponMatches(null, guns) && !GunAim.weaponMatches("Sword", List.of("", "§a")), "Empty names and formatting-only aliases cannot match");
        System.out.println("Gun hitscan angles, cone/prediction bounds and configurable host/weapon profile regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
