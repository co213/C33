package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.movement.SprintPolicy;

public final class SprintRegression {
    public static void run() {
        for (boolean strict : new boolean[]{true, false}) {
            for (boolean stationary : new boolean[]{true, false}) {
                check(!SprintPolicy.shouldSprint(strict, false, stationary, 1, 1, false), "Movement/stationary settings cannot override blocked eligibility");
                check(!SprintPolicy.shouldSprint(strict, true, stationary, Float.NaN, 1, false), "Invalid input cannot start sprinting");
            }
        }
        check(SprintPolicy.shouldSprint(true, true, false, 0.2f, 0, true), "Strict accepts native swim continuation rather than a land threshold");
        check(SprintPolicy.shouldSprint(false, true, false, -1, 0, false), "Rage permits backwards movement");
        check(SprintPolicy.shouldSprint(false, true, false, 0, 1, false), "Rage permits strafing");
        check(!SprintPolicy.shouldSprint(false, true, false, 0.5f, 0.5f, false), "Two slow diagonal axes do not accidentally exceed the sprint threshold");
        check(!SprintPolicy.shouldSprint(false, true, false, 0, 0, false), "Stationary sprint requires explicit opt-in");
        check(SprintPolicy.shouldSprint(false, true, true, 0, 0, false), "Stationary opt-in works when otherwise eligible");
        check(SprintPolicy.shouldSprint(false, true, false, 0.2f, 0, true), "Underwater movement uses its own threshold");
        check(!SprintPolicy.shouldSprint(false, true, false, 0, 0, true), "Water alone does not count as movement");
        System.out.println("Sprint eligibility, directional input and swimming policy regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
