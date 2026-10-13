package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.storage.ContainerSnapshot;
import com.gaspoweredcake.client33.utils.storage.ContainerMemory;
import com.gaspoweredcake.client33.utils.storage.StorageLocations;
import com.gaspoweredcake.client33.utils.render.LabelLayout;
import java.util.*;

final class StorageRegression {
    static void run() {
        var diamond = new ContainerSnapshot.Stack("minecraft:diamond", "Diamond", 64, 64, false, -1);
        var tool = new ContainerSnapshot.Stack("minecraft:pickaxe", "§aPick\naxe", 1, 1, true, 25);
        var slots = new ArrayList<>(Arrays.asList(diamond, diamond, tool, null));
        var snapshot = ContainerSnapshot.summarize(100, slots);
        check(snapshot.serverObserved() && !ContainerSnapshot.summarize(100, slots, false).serverObserved(), "menu prediction distinguished from server packet");
        check(snapshot.slots() == 4 && snapshot.occupied() == 3 && snapshot.items() == 129, "slots/items exclude empty slots");
        check(snapshot.enchanted() == 1 && snapshot.lowestDurability() == 25, "equipment metadata retained");
        check(snapshot.contents().getFirst().count() == 128 && snapshot.contents().get(1).name().equals("Pick axe"), "aggregation and safe text");
        slots.clear(); check(snapshot.contents().size() == 2 && snapshot.occupancy() == 0.75, "immutable summary");
        check(!snapshot.expired(1099, 1000) && snapshot.expired(1100, 1000) && snapshot.expired(99, 1000), "exact expiry and regressed clock");
        try { snapshot.contents().clear(); throw new AssertionError("mutable snapshot"); } catch (UnsupportedOperationException expected) { }
        try { ContainerSnapshot.summarize(0, Collections.nCopies(109, diamond)); throw new AssertionError("unbounded snapshot"); } catch (IllegalArgumentException expected) { }
        check(ContainerSnapshot.summarize(0, List.of()).occupancy() == 0, "empty capacity is finite");
        var negative = new ContainerSnapshot.Stack("x", "x", -4, 64, false, -1);
        check(ContainerSnapshot.summarize(0, List.of(negative)).items() == 0, "reject negative counts");
        var huge = new ContainerSnapshot.Stack("x", "x", Integer.MAX_VALUE, 64, false, -1);
        check(ContainerSnapshot.summarize(0, List.of(huge)).items() == Integer.MAX_VALUE, "server overstack count is not silently truncated");
        check(ContainerSnapshot.summarize(0, Collections.nCopies(108, huge)).items() == 108L * Integer.MAX_VALUE, "overstack totals cannot overflow int");
        var memory = new ContainerMemory<String>(2); Object identity = new Object();
        check(memory.get("unknown", identity, 100, 1000) == null, "unseen must not be empty");
        memory.put("a", identity, snapshot); memory.put("b", identity, snapshot); memory.put("c", identity, snapshot);
        check(memory.size() == 2 && memory.get("a", identity, 100, 1000) == null, "bounded eviction");
        check(memory.get("b", new Object(), 100, 1000) == null, "replaced object cannot inherit inventory");
        memory.expire(1100, 1000); check(memory.size() == 0, "expiry removes retained summaries");
        memory.put("a", identity, snapshot); memory.clear(); check(memory.size() == 0, "world reset clears memory");
        var locations = new StorageLocations<String, String>(2);
        locations.observe("a", "Chest", 1); locations.observe("b", "Barrel", 2);
        locations.observe("a", "Chest", 3); locations.observe("c", "Dispenser", 4);
        check(locations.entries().size() == 2 && locations.entries().getFirst().key().equals("a"), "location eviction preserves most recently seen positions");
        var remembered = locations.entries();
        locations.expire(13, 10);
        check(locations.entries().size() == 1 && remembered.size() == 2, "expiry and immutable snapshot iteration");
        locations.clear(); check(locations.entries().isEmpty(), "world change clears location memory");
        var layout = new LabelLayout(800, 600);
        var first = layout.place(400, 400, 120, 45); var second = layout.place(400, 400, 120, 45);
        check(first != null && second != null && !first.overlaps(second), "cluster labels shift without overlapping");
        check(layout.place(Double.NaN, 200, 100, 30) == null && layout.place(20, 200, 900, 30) == null, "invalid geometry rejected");
        check(layout.place(900, 200, 100, 30) == null, "offscreen targets do not pile onto edges");
        var tiny = new LabelLayout(300, 200); tiny.reserve(new LabelLayout.Rect(0, 0, 300, 200));
        check(tiny.place(150, 190, 120, 30) == null, "inspector exclusion respected");
        System.out.println("Storage observation and label regressions passed.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
