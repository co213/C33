package com.gaspoweredcake.client33.utils.navigation;

/** Temporary backend settings and cancellation belong only to the goal that acquired them. */
public final class RouteLease {
    public interface Backend {
        boolean allowBreak();
        void allowBreak(boolean value);
        Object goal();
        void stop();
    }

    private final Backend backend;
    private final boolean previous, requested;
    private Object token;
    private boolean closed;

    public RouteLease(Backend backend, boolean mine, Runnable start) {
        this.backend = backend;
        previous = backend.allowBreak();
        requested = mine;
        try {
            backend.allowBreak(mine);
            start.run();
            token = backend.goal();
            if (token == null) throw new IllegalStateException("Pathfinder did not accept the route.");
        } catch (RuntimeException failure) {
            try { backend.stop(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { restore(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            closed = true;
            throw failure;
        }
    }

    public boolean ownsGoal() { return !closed && token == backend.goal(); }

    public void close() {
        if (closed) return;
        boolean stop = ownsGoal();
        closed = true;
        try { if (stop) backend.stop(); }
        finally { restore(); }
    }

    private void restore() {
        if (backend.allowBreak() == requested) backend.allowBreak(previous);
    }
}
