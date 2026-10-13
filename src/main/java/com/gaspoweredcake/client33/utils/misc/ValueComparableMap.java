/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.misc;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** A mutable map whose views iterate in value order, with keys breaking ties.
 * Ordering is captured when each iterator is created; lookup never relies on mutable values.
 */
public class ValueComparableMap<K extends Comparable<K>, V> extends AbstractMap<K, V> {
    private final Map<K, V> values = new HashMap<>();
    private final Comparator<? super V> comparator;
    private int version;

    public ValueComparableMap(Comparator<? super V> comparator) {
        this.comparator = comparator;
    }

    @Override
    public V put(K key, V value) {
        if (!values.containsKey(key)) version++;
        return values.put(key, value);
    }

    @Override
    public V get(Object key) { return values.get(key); }

    @Override
    public boolean containsKey(Object key) { return values.containsKey(key); }

    @Override
    public int size() { return values.size(); }

    @Override
    public V remove(Object key) {
        if (values.containsKey(key)) version++;
        return values.remove(key);
    }

    @Override
    public void clear() {
        if (!values.isEmpty()) version++;
        values.clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return values.size(); }
            @Override public void clear() { ValueComparableMap.this.clear(); }
            @Override public Iterator<Entry<K, V>> iterator() {
                var ordered = new ArrayList<>(values.entrySet());
                ordered.sort((left, right) -> {
                    int order = comparator.compare(left.getValue(), right.getValue());
                    return order != 0 ? order : left.getKey().compareTo(right.getKey());
                });
                var iterator = ordered.iterator();
                return new Iterator<>() {
                    private int expectedVersion = version;
                    private Entry<K, V> current;
                    private void checkVersion() {
                        if (version != expectedVersion) throw new ConcurrentModificationException();
                    }
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Entry<K, V> next() {
                        checkVersion();
                        return current = iterator.next();
                    }
                    @Override public void remove() {
                        checkVersion();
                        if (current == null) throw new IllegalStateException();
                        ValueComparableMap.this.remove(current.getKey());
                        expectedVersion = version;
                        current = null;
                    }
                };
            }
        };
    }
}
