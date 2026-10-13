package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.navigation.BaritoneTasks;
import java.util.List;

final class BaritoneTasksRegression {
    static void run() {
        check(BaritoneTasks.goTo(-20, 64, 30, false).equals("goto -20 64 30"), "3D goal");
        check(BaritoneTasks.goTo(-20, 64, 30, true).equals("goto -20 30"), "XZ goal");
        check(BaritoneTasks.mine(List.of("minecraft:stone", "minecraft:stone"), 16).equals("mine 16 minecraft:stone"), "quantity before deduplicated blocks");
        check(BaritoneTasks.mine(List.of("minecraft:stone"), 0).equals("mine minecraft:stone"), "unlimited mining");
        check(BaritoneTasks.tunnel(3, 2, 16).equals("tunnel 3 2 16"), "backend tunnel argument order is height width depth");
        check(BaritoneTasks.follow(" Example_1 ").equals("follow player Example_1"), "follow name normalization");
        check(BaritoneTasks.farm(0).equals("farm"), "unrestricted farm");
        check(BaritoneTasks.explore(10, 20).equals("explore 10 20"), "exploration origin");
        check(BaritoneTasks.build("house.schematic", 0, 64, 0).equals("build house.schematic 0 64 0"), "schematic origin");
        check(BaritoneTasks.command(" #goto 1 2 ", "!").equals("goto 1 2"), "default prefix accepted");
        check(BaritoneTasks.command("!help", "!").equals("help"), "configured prefix accepted");
        rejects(() -> BaritoneTasks.goTo(Integer.MIN_VALUE, 0, 0, true));
        rejects(() -> BaritoneTasks.goTo(30_000_001, 0, 0, false));
        rejects(() -> BaritoneTasks.mine(List.of(), 0));
        rejects(() -> BaritoneTasks.mine(List.of("minecraft:stone help"), 0));
        rejects(() -> BaritoneTasks.mine(List.of("minecraft:stone"), -1));
        rejects(() -> BaritoneTasks.follow("name anothercommand"));
        rejects(() -> BaritoneTasks.tunnel(1, 2, 16));
        rejects(() -> BaritoneTasks.farm(-1));
        rejects(() -> BaritoneTasks.build("../house.schematic", 0, 64, 0));
        rejects(() -> BaritoneTasks.build("house file.schematic", 0, 64, 0));
        rejects(() -> BaritoneTasks.command("#", "#"));
        rejects(() -> BaritoneTasks.command("goto 1\nstop", "#"));
        rejects(() -> BaritoneTasks.command("x".repeat(2049), "#"));
        System.out.println("Baritone task validation regressions passed.");
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid task must not replace active work"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
