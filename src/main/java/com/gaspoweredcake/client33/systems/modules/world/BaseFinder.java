package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.pathing.Navigation;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Freecam;
import com.gaspoweredcake.client33.systems.waypoints.Waypoint;
import com.gaspoweredcake.client33.systems.waypoints.Waypoints;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.navigation.ExplorationSpiral;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.*;

/** Explores loaded terrain until it discovers a configured storage cluster. */
public final class BaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> doubleChests = sgGeneral.add(new BoolSetting.Builder()
        .name("double-chests").description("Look for double chests; each pair counts once.").defaultValue(true).build());
    private final Setting<Boolean> dispensers = sgGeneral.add(new BoolSetting.Builder()
        .name("dispensers").description("Look for dispensers and droppers.").defaultValue(true).build());
    private final Setting<Boolean> otherStorage = sgGeneral.add(new BoolSetting.Builder()
        .name("other-storage").description("Also detect single/ender chests, barrels, shulkers, hoppers and furnaces.")
        .defaultValue(true).build());
    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-containers").description("Containers within the cluster radius needed to mark a storage candidate.")
        .defaultValue(2).range(1, 64).sliderMax(16).build());
    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-radius").description("Maximum distance between the candidate and matching storage blocks.")
        .defaultValue(16).range(1, 64).sliderMax(32).build());
    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("exploration-spacing").description("Distance between exploration grid points. Keep below your loaded chunk diameter.")
        .defaultValue(64).range(16, 256).sliderMax(128).build());
    private final Setting<Boolean> mine = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-mining").description("Allow mining through obstacles while exploring and approaching storage.")
        .defaultValue(false).build());

    private final LinkedHashMap<BlockPos, BlockEntity> containers = new LinkedHashMap<>();
    private Iterator<ChunkAccess> chunks = Collections.emptyIterator();
    private Iterator<BlockPos> blocks = Collections.emptyIterator();
    private ChunkAccess scanChunk;
    private List<BlockPos> candidates = List.of();
    private int candidateIndex, ticks, failures;
    private ClientLevel world;
    private BlockPos origin, found;
    private ExplorationSpiral spiral;
    private Navigation.Route route;

    public BaseFinder() {
        super(Categories.World, "base-finder", "Walks an expanding search until a storage cluster is found. Requires Baritone; scans loaded chunks only.");
    }

    @Override public void onActivate() {
        clear();
        if (mc.level == null || mc.player == null || !PathManagers.get().supportsNavigation()) {
            error("Join a world and install Baritone for Minecraft 26.2 to use Base Finder.");
            toggle();
            return;
        }
        if (!doubleChests.get() && !dispensers.get() && !otherStorage.get()) {
            error("Enable at least one storage type."); toggle(); return;
        }
        Freecam freecam = Modules.get().get(Freecam.class);
        if (freecam.isActive()) freecam.toggle();
        world = mc.level;
        origin = mc.player.blockPosition();
        spiral = new ExplorationSpiral(spacing.get());
        info("Searching loaded terrain for storage. Disable Base Finder to stop.");
    }

    @Override public void onDeactivate() { clear(); }
    @EventHandler private void onLeave(GameLeftEvent event) { if (isActive()) toggle(); }
    private void clear() {
        if (route != null) route.cancel();
        route = null; world = null; found = null; scanChunk = null;
        containers.clear(); chunks = Collections.emptyIterator(); blocks = Collections.emptyIterator();
        candidates = List.of(); candidateIndex = ticks = failures = 0;
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.level != world || mc.player == null || !mc.player.isAlive()) { toggle(); return; }
        if (Modules.get().isActive(Freecam.class)) {
            warning("Search stopped because Freecam was enabled."); toggle(); return;
        }
        if (route != null && route.state() == Navigation.State.Cancelled) {
            info("Search stopped: navigation was cancelled or replaced."); toggle(); return;
        }
        if (found == null) scan();
        if (!isActive()) return;
        if (route != null && route.state() == Navigation.State.Running) return;
        if (found != null) {
            if (route != null && route.state() == Navigation.State.Arrived) info("Arrived at the storage candidate: %s.", found.toShortString());
            else warning("Unable to reach storage. Its waypoint has been saved.");
            toggle(); return;
        }
        if (route != null && route.state() == Navigation.State.Stuck) {
            if (++failures >= 8) { warning("Search stopped after eight blocked routes. Try enabling mining or moving to open terrain."); toggle(); return; }
        } else failures = 0;
        nextRoute();
    }

    private void nextRoute() {
        for (int attempt = 0; attempt < 32; attempt++) {
            ExplorationSpiral.Point point = spiral.next();
            long x = origin.getX() + point.x(), z = origin.getZ() + point.z();
            if (Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984) continue;
            BlockPos target = new BlockPos((int) x, origin.getY(), (int) z);
            if (!world.getWorldBorder().isWithinBounds(target)) continue;
            startRoute(target, true);
            return;
        }
        warning("Search reached the world border. Move inward and restart to search another area."); toggle();
    }

    private void startRoute(BlockPos target, boolean ignoreY) {
        try { route = Navigation.start(target, ignoreY, mine.get()); }
        catch (IllegalStateException | IllegalArgumentException failure) { error(failure.getMessage()); toggle(); }
    }

    private boolean matches(BlockEntity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (entity instanceof ChestBlockEntity) {
            var state = entity.getBlockState();
            if (!state.hasProperty(ChestBlock.TYPE)) return otherStorage.get();
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) return doubleChests.get() && type == ChestType.LEFT;
            return otherStorage.get();
        }
        if (entity instanceof DispenserBlockEntity) return dispensers.get();
        return otherStorage.get() && (entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity
            || entity instanceof EnderChestBlockEntity || entity instanceof HopperBlockEntity || entity instanceof AbstractFurnaceBlockEntity
            || entity instanceof CrafterBlockEntity || entity instanceof BrewingStandBlockEntity);
    }

    private boolean live(BlockPos pos, BlockEntity entity) {
        return world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && world.getBlockEntity(pos) == entity && matches(entity);
    }

    private void scan() {
        if (++ticks % 20 == 0) containers.entrySet().removeIf(entry -> !live(entry.getKey(), entry.getValue()));
        // Cap both chunk transitions and block entities, even in densely built chunks.
        int visitedChunks = 0;
        for (int count = 0; count < 256; count++) {
            if (scanChunk != null && (!world.hasChunk(scanChunk.getPos().x(), scanChunk.getPos().z())
                || world.getChunk(scanChunk.getPos().x(), scanChunk.getPos().z()) != scanChunk)) blocks = Collections.emptyIterator();
            if (!blocks.hasNext()) {
                if (++visitedChunks > 4) break;
                if (!chunks.hasNext()) { chunks = Utils.chunks().iterator(); break; }
                scanChunk = chunks.next();
                blocks = List.copyOf(scanChunk.getBlockEntitiesPos()).iterator();
                continue;
            }
            BlockPos pos = blocks.next();
            BlockEntity entity = world.getBlockEntity(pos);
            if (!matches(entity)) { containers.remove(pos); continue; }
            if (containers.size() >= 8192 && !containers.containsKey(pos)) containers.remove(containers.keySet().iterator().next());
            containers.put(pos.immutable(), entity);
        }
        if (candidateIndex >= candidates.size()) { candidates = List.copyOf(containers.keySet()); candidateIndex = 0; }
        if (candidates.isEmpty()) return;
        BlockPos candidate = candidates.get(candidateIndex++);
        BlockEntity entity = containers.get(candidate);
        if (entity == null || !live(candidate, entity)) return;
        int count = 0;
        for (var entry : containers.entrySet()) {
            if (candidate.distSqr(entry.getKey()) <= (double) radius.get() * radius.get() && live(entry.getKey(), entry.getValue())) count++;
            if (count >= minimum.get()) {
                found = candidate;
                Waypoints.get().add(new Waypoint.Builder().name("Storage candidate " + candidate.toShortString())
                    .pos(candidate).dimension(PlayerUtils.getDimension()).build());
                info("Found %d storage containers near %s. Approaching saved waypoint.", count, candidate.toShortString());
                startRoute(candidate, false);
                return;
            }
        }
    }

    @Override public String getInfoString() { return found == null ? "Searching: " + containers.size() : "Approaching storage"; }
}
