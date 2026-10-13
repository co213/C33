package com.gaspoweredcake.client33.utils.world;

/** Monotonic command cooldown independent of game ticks and server TPS. */
public final class RtpSearchCycle {
    private long nextCommand, settled;

    public void reset(long now, long settleNanos) {
        nextCommand = now;
        settled = now + settleNanos;
    }

    public void locationChanged(long now, long settleNanos) {
        settled = now + settleNanos;
    }

    public boolean settled(long now) { return now - settled >= 0; }

    public boolean ready(long now, boolean scanComplete, boolean screenOpen) {
        return scanComplete && !screenOpen && settled(now) && now - nextCommand >= 0;
    }

    public void sent(long now, long intervalNanos, long settleNanos) {
        nextCommand = now + intervalNanos;
        settled = now + settleNanos;
    }
}
