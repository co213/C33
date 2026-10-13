package com.gaspoweredcake.client33.utils.storage;

import java.util.LinkedHashMap;
import java.util.List;

/** Bounded last-seen positions, deliberately separate from live container/content observations. */
public final class StorageLocations<K, V> {
    public record Seen<K, V>(K key, V value, long time) { }
    private final LinkedHashMap<K, Seen<K, V>> entries = new LinkedHashMap<>();
    private final int capacity;
    public StorageLocations(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public void observe(K key, V value, long now) {
        entries.remove(key);
        entries.put(key, new Seen<>(key, value, now));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    public void expire(long now, long ttl) { entries.values().removeIf(entry -> now - entry.time >= ttl); }
    public List<Seen<K, V>> entries() { return List.copyOf(entries.values()); }
    public void clear() { entries.clear(); }
}
