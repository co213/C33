package com.gaspoweredcake.client33.utils.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Same stable order as sorting all candidates, retaining only the requested best entries. */
public final class TargetSelection {
    private TargetSelection() {}
    private record Ranked<T>(T value, int index) {}

    public static <T> List<T> select(List<T> candidates, int requested, Comparator<T> order, T preferred) {
        int limit = Math.min(Math.max(0, requested), candidates.size());
        if (limit == 0) return List.of();
        int preferredIndex = preferred == null ? -1 : candidates.indexOf(preferred);
        int slots = limit - (preferredIndex >= 0 ? 1 : 0);
        var result = new ArrayList<T>(limit);
        if (preferredIndex >= 0) result.add(candidates.get(preferredIndex));
        if (slots == 0) return result;
        Comparator<Ranked<T>> rank = (a, b) -> {
            int comparison = order.compare(a.value, b.value);
            return comparison != 0 ? comparison : Integer.compare(a.index, b.index);
        };
        var best = new PriorityQueue<Ranked<T>>(Math.min(slots, 16), rank.reversed());
        for (int i = 0; i < candidates.size(); i++) {
            if (i == preferredIndex) continue;
            var next = new Ranked<>(candidates.get(i), i);
            if (best.size() < slots) best.add(next);
            else if (rank.compare(next, best.peek()) < 0) { best.poll(); best.add(next); }
        }
        var sorted = new ArrayList<>(best);
        sorted.sort(rank);
        for (var next : sorted) result.add(next.value);
        return result;
    }
}
