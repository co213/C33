package com.gaspoweredcake.client33.utils.navigation;

import java.util.*;

/** Detects tab-list removals from recent immutable sightings, including equal-count replacements. */
public final class LogoutTracker<T> {
    public record Spot<T>(UUID id, T snapshot, long loggedOutAt) { }
    private record Seen<T>(T snapshot, long time) { }
    private Set<UUID> online = Set.of();
    private final Map<UUID, Seen<T>> seen = new HashMap<>();
    private final LinkedHashMap<UUID, Spot<T>> spots = new LinkedHashMap<>();

    public void update(Set<UUID> current, Map<UUID, T> visible, long now, long recentMillis, long expiryMillis, int limit) {
        // Sightings are supplied before removals are processed so the last visible position wins.
        visible.forEach((id, value) -> seen.put(id, new Seen<>(value, now)));
        for (UUID id : online) {
            if (current.contains(id)) continue;
            Seen<T> last = seen.remove(id);
            if (last != null && now - last.time <= recentMillis) {
                spots.remove(id);
                spots.put(id, new Spot<>(id, last.snapshot, now));
            }
        }
        current.forEach(spots::remove);
        seen.entrySet().removeIf(entry -> !current.contains(entry.getKey()) || now - entry.getValue().time > recentMillis);
        spots.values().removeIf(spot -> now - spot.loggedOutAt >= expiryMillis);
        while (spots.size() > Math.max(0, limit)) spots.remove(spots.keySet().iterator().next());
        online = Set.copyOf(current);
    }

    public List<Spot<T>> spots() { return List.copyOf(spots.values()); }
    public void clearSpots() { spots.clear(); }
    public void clear() { online = Set.of(); seen.clear(); spots.clear(); }
}
