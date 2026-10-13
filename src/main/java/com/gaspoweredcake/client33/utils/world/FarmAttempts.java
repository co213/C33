package com.gaspoweredcake.client33.utils.world;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Bounded per-target backoff. An observed state change releases the old retry budget. */
public final class FarmAttempts<K, S> {
    private record Attempt<S>(S state, int count, long readyAt) { }
    private final LinkedHashMap<K, Attempt<S>> attempts = new LinkedHashMap<>();
    private final int capacity;

    public FarmAttempts(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public boolean ready(K key, S state, long now) {
        var previous = attempts.get(key);
        return previous == null || !Objects.equals(previous.state(), state) || now >= previous.readyAt();
    }

    public void record(K key, S state, long now) {
        var previous = attempts.remove(key);
        int count = previous == null || !Objects.equals(previous.state(), state) || previous.count() >= 3
            ? 1 : previous.count() + 1;
        attempts.put(key, new Attempt<>(state, count, now + (count >= 3 ? 5000 : 350)));
        while (attempts.size() > capacity) attempts.pollFirstEntry();
    }

    public void clear() { attempts.clear(); }
    public int size() { return attempts.size(); }
}
