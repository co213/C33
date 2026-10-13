package com.gaspoweredcake.client33.utils.navigation;

/** Visits an expanding square grid, one bounded travel segment at a time. */
public final class ExplorationSpiral {
    public record Point(long x, long z) { }
    private long x, z, legLength = 1, remaining = 1;
    private int direction, legs;
    private final int spacing;

    public ExplorationSpiral(int spacing) {
        if (spacing < 1) throw new IllegalArgumentException("Spacing must be positive");
        this.spacing = spacing;
    }

    public Point next() {
        switch (direction) {
            case 0 -> x += spacing;
            case 1 -> z += spacing;
            case 2 -> x -= spacing;
            case 3 -> z -= spacing;
        }
        if (--remaining == 0) {
            direction = (direction + 1) % 4;
            if (++legs % 2 == 0) legLength++;
            remaining = legLength;
        }
        return new Point(x, z);
    }
}
