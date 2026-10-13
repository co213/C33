package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.navigation.*;
import java.util.*;

public final class NavigationRegression {
    public static void run() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        LogoutTracker<String> tracker = new LogoutTracker<>();
        tracker.update(Set.of(a), Map.of(a, "position-a"), 0, 2000, 60000, 2);
        tracker.update(Set.of(b), Map.of(b, "position-b"), 100, 2000, 60000, 2);
        check(tracker.spots().size() == 1 && tracker.spots().getFirst().snapshot().equals("position-a"), "Equal-count tab replacement records logout");
        tracker.update(Set.of(b), Map.of(), 200, 2000, 60000, 2);
        check(tracker.spots().size() == 1, "Entity unloading alone is not logout");
        tracker.update(Set.of(a, b), Map.of(a, "new-position"), 300, 2000, 60000, 2);
        check(tracker.spots().isEmpty(), "Rejoin clears previous logout");
        tracker.update(Set.of(c), Map.of(c, "position-c"), 400, 2000, 60000, 1);
        check(tracker.spots().size() == 1, "Retention is bounded");
        tracker.update(Set.of(), Map.of(), 5000, 2000, 60000, 2);
        check(tracker.spots().stream().noneMatch(spot -> spot.id().equals(c)), "Stale unloaded sightings are not logout locations");
        tracker.update(Set.of(), Map.of(), 70000, 2000, 60000, 2);
        check(tracker.spots().isEmpty(), "Old spots expire");
        tracker.update(Set.of(a), Map.of(a, "another-world"), 80000, 2000, 60000, 2);
        tracker.clear();
        tracker.update(Set.of(), Map.of(), 80001, 2000, 60000, 2);
        check(tracker.spots().isEmpty(), "World reset clears online and sighting history");

        ExplorationSpiral spiral = new ExplorationSpiral(16);
        Set<ExplorationSpiral.Point> visited = new HashSet<>();
        ExplorationSpiral.Point previous = new ExplorationSpiral.Point(0, 0);
        visited.add(previous);
        for (int i = 0; i < 1088; i++) {
            var point = spiral.next();
            check(visited.add(point), "Exploration must reach new grid locations");
            check(Math.abs(point.x() - previous.x()) + Math.abs(point.z() - previous.z()) == 16, "Each route is one bounded grid step");
            previous = point;
        }
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            check(visited.contains(new ExplorationSpiral.Point(x * 16, z * 16)), "Spiral covers the full interior without holes");
        }

        Backend backend = new Backend();
        RouteLease lease = new RouteLease(backend, true, () -> backend.goal = new Object());
        check(backend.mine && lease.ownsGoal(), "Route applies mining setting");
        lease.close(); lease.close();
        check(!backend.mine && backend.stops == 1, "Close restores settings and cancels exactly once");
        lease = new RouteLease(backend, true, () -> backend.goal = new Object());
        backend.goal = new Object();
        check(!lease.ownsGoal(), "Replacing goal revokes route ownership");
        lease.close();
        check(backend.stops == 1 && !backend.mine, "Closing old route preserves replacement goal");
        lease = new RouteLease(backend, true, () -> backend.goal = new Object());
        backend.mine = false;
        lease.close();
        check(!backend.mine, "Explicit user setting change survives route close");
        try {
            new RouteLease(backend, true, () -> { throw new IllegalStateException("failure"); });
            throw new AssertionError("Backend startup error hidden");
        } catch (IllegalStateException expected) { }
        check(!backend.mine, "Failed startup restores mining state");
        lease = new RouteLease(backend, true, () -> backend.goal = new Object());
        backend.failStop = true;
        try { lease.close(); throw new AssertionError("Expected stop error"); }
        catch (IllegalStateException expected) { }
        check(!backend.mine, "Failed cancellation still restores settings");
        System.out.println("Logout, exploration and navigation ownership regressions passed.");
    }

    private static final class Backend implements RouteLease.Backend {
        boolean mine, failStop;
        Object goal;
        int stops;
        public boolean allowBreak() { return mine; }
        public void allowBreak(boolean value) { mine = value; }
        public Object goal() { return goal; }
        public void stop() { stops++; if (failStop) throw new IllegalStateException(); goal = null; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
