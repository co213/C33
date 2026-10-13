package com.gaspoweredcake.client33.utils.movement;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Owns only the base value we wrote; external/server attribute changes remain intact. */
public final class StepHeightLease {
    private final DoubleSupplier read;
    private final DoubleConsumer write;
    private double baseline, applied;
    private boolean owned;

    public StepHeightLease(DoubleSupplier read, DoubleConsumer write) {
        this.read = read; this.write = write; baseline = read.getAsDouble();
    }
    public void apply(double value) {
        double current = read.getAsDouble();
        if (!owned || Double.compare(current, applied) != 0) baseline = current;
        if (Double.compare(current, value) != 0) write.accept(value);
        applied = value; owned = true;
    }
    public void restore() {
        double current = read.getAsDouble();
        if (owned && Double.compare(current, applied) == 0) write.accept(baseline);
        else baseline = current;
        owned = false;
    }
}
