package com.gaspoweredcake.client33.utils.movement;

/** Server corrections pause assistance; rejected native movement falls back for the session. */
public final class StepRecovery {
    private volatile long blockedUntil;
    private volatile boolean jumpFallback;

    public void reset(long now) { blockedUntil = now; jumpFallback = false; }
    public void corrected(long now, boolean nativeApplied) {
        if (nativeApplied) jumpFallback = true;
        blockedUntil = now + 2_000_000_000L;
    }
    public boolean ready(long now) { return now - blockedUntil >= 0; }
    public boolean jumpFallback() { return jumpFallback; }
}
