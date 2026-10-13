package com.gaspoweredcake.client33.pathing;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.utils.navigation.RouteLease;
import com.gaspoweredcake.client33.utils.PreInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import static com.gaspoweredcake.client33.Client33.mc;

/** Owns a requested route and restores temporary pathfinder settings on every exit. */
public final class Navigation {
    public enum State { Running, Arrived, Cancelled, Stuck }
    private static Route active;
    private Navigation() {}

    @PreInit(dependencies = PathManagers.class)
    public static void init() { Client33.EVENT_BUS.subscribe(Navigation.class); }

    public static Route start(BlockPos target, boolean ignoreY, boolean mine) {
        if (mc.player == null || mc.level == null) throw new IllegalStateException("Join a world before starting a route.");
        IPathManager manager = PathManagers.get();
        if (!manager.supportsNavigation()) throw new IllegalStateException("Install Baritone for Minecraft 26.2 to enable automatic navigation.");
        if (!mc.level.getWorldBorder().isWithinBounds(target)) throw new IllegalArgumentException("Target is outside the world border.");
        if (active != null) active.finish(State.Cancelled);
        Route route = new Route(manager, target, ignoreY, mine);
        active = route;
        return route;
    }

    public static void stop() { if (active != null) active.cancel(); }
    public static String status() { return active == null ? "Idle" : "Walking to " + active.target.toShortString(); }

    @EventHandler
    private static void tick(TickEvent.Post event) {
        Route route = active;
        if (route == null) return;
        if (mc.level != route.world || mc.player != route.player || !route.player.isAlive()) {
            route.finish(State.Cancelled);
            return;
        }
        if (route.manager.isPaused()) return;
        Vec3 pos = route.player.position();
        double dx = pos.x - (route.target.getX() + 0.5), dz = pos.z - (route.target.getZ() + 0.5);
        double dy = route.ignoreY ? 0 : pos.y - route.target.getY();
        if (dx * dx + dy * dy + dz * dz <= 9) {
            route.finish(State.Arrived);
            return;
        }
        if (!route.lease.ownsGoal()) {
            route.finish(State.Cancelled);
            return;
        }
        if (pos.distanceToSqr(route.lastProgress) >= 1) {
            route.lastProgress = pos;
            route.idleTicks = 0;
        } else if (++route.idleTicks >= 600) route.finish(State.Stuck);
    }

    @EventHandler
    private static void onLeave(GameLeftEvent event) {
        if (active != null) active.finish(State.Cancelled);
    }

    public static final class Route {
        private final IPathManager manager;
        private final ClientLevel world = mc.level;
        private final LocalPlayer player = mc.player;
        public final BlockPos target;
        private final boolean ignoreY;
        private final RouteLease lease;
        private State state = State.Running;
        private Vec3 lastProgress = mc.player.position();
        private int idleTicks;

        private Route(IPathManager manager, BlockPos target, boolean ignoreY, boolean mine) {
            this.manager = manager;
            this.target = target.immutable();
            this.ignoreY = ignoreY;
            lease = new RouteLease(new RouteLease.Backend() {
                public boolean allowBreak() { return manager.getAllowBreak(); }
                public void allowBreak(boolean value) { manager.setAllowBreak(value); }
                public Object goal() { return manager.getGoalToken(); }
                public void stop() { manager.stop(); }
            }, mine, () -> { manager.resume(); manager.moveTo(target, ignoreY); });
        }

        public State state() { return state; }
        public void cancel() { if (active == this) finish(State.Cancelled); }
        private void finish(State state) {
            if (this.state != State.Running) return;
            this.state = state;
            if (active == this) active = null;
            try { lease.close(); }
            catch (RuntimeException failure) { Client33.LOG.error("Failed to release navigation route.", failure); }
        }
    }
}
