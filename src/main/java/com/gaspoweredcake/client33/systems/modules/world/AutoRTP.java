package com.gaspoweredcake.client33.systems.modules.world;

import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.waypoints.Waypoint;
import com.gaspoweredcake.client33.systems.waypoints.Waypoints;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.world.RtpSearchCycle;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Repeats a server RTP command after checking the current area's loaded chests. */
public final class AutoRTP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command").description("Server teleport command, with or without the leading slash.")
        .defaultValue("rtp").build());
    private final Setting<Integer> minimum = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-chests").description("Nearby chest containers needed to stop. A double chest counts once; contents are not inspected.")
        .defaultValue(8).range(1, 128).sliderRange(1, 32).build());
    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius").description("Distance from the player to count loaded chest containers, in blocks.")
        .defaultValue(64).range(8, 128).sliderRange(16, 128).build());
    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("command-interval").description("Minimum real seconds between RTP commands. Match your server's cooldown.")
        .defaultValue(30).range(5, 3600).sliderRange(5, 300).build());
    private final Setting<Integer> loadWait = sgGeneral.add(new IntSetting.Builder()
        .name("load-wait").description("Seconds to let chunks arrive after activation, a command or a teleport.")
        .defaultValue(10).range(1, 120).sliderRange(1, 30).build());
    private final Setting<Boolean> waypoint = sgGeneral.add(new BoolSetting.Builder()
        .name("save-waypoint").description("Save a waypoint at a detected chest cluster before stopping.")
        .defaultValue(true).build());

    private final RtpSearchCycle cycle = new RtpSearchCycle();
    private final Set<BlockPos> chests = new LinkedHashSet<>();
    private Iterator<BlockPos> positions = Collections.emptyIterator();
    private ClientLevel world;
    private ChunkAccess scanChunk;
    private BlockPos scanOrigin, teleportOrigin;
    private int scanRadius, minChunkX, minChunkZ, chunkWidth, chunkHeight, chunkIndex, attempts;

    public AutoRTP() {
        super(Categories.World, "auto-rtp", "Repeats /rtp until enough loaded chests are nearby. Pauses commands while a screen is open; no Baritone needed.");
    }

    @Override public void onActivate() {
        clear();
        if (mc.level == null || mc.player == null || mc.isLocalServer()) {
            error("Join a multiplayer server that supports /rtp first.");
            toggle();
            return;
        }
        world = mc.level;
        teleportOrigin = mc.player.blockPosition();
        cycle.reset(System.nanoTime(), waitNanos());
        startScan();
        info("Searching for %d nearby chest containers. RTP cooldown: %d seconds.", minimum.get(), interval.get());
    }

    @Override public void onDeactivate() {
        clear();
        // Availability shutdown can unsubscribe before GameLeftEvent reaches this module.
        // Discard enabled intent too, so reconnecting cannot resume command automation.
        if (isActive()) disable();
    }
    @EventHandler private void onLeave(GameLeftEvent event) { if (isActive()) toggle(); }

    private void clear() {
        world = null; scanChunk = null; scanOrigin = teleportOrigin = null;
        positions = Collections.emptyIterator(); chests.clear(); attempts = 0;
    }

    private long waitNanos() { return TimeUnit.SECONDS.toNanos(loadWait.get()); }

    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { toggle(); return; }
        long now = System.nanoTime();
        BlockPos playerPos = mc.player.blockPosition();
        if (world != mc.level || teleportOrigin == null || teleportOrigin.distSqr(playerPos) > 32 * 32) {
            world = mc.level;
            teleportOrigin = playerPos;
            cycle.locationChanged(now, waitNanos());
            startScan();
        } else if (scanRadius != radius.get() || !playerPos.equals(scanOrigin)) startScan();
        if (!cycle.settled(now)) return;
        boolean complete = scan();
        if (!isActive()) return;
        if (cycle.ready(now, complete, mc.gui.screen() != null)) {
            String text = command.get().trim();
            if (text.startsWith("/")) text = text.substring(1);
            if (text.isBlank() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.startsWith("/")) {
                error("Set a single valid RTP command."); toggle(); return;
            }
            mc.player.connection.sendCommand(text);
            attempts++;
            cycle.sent(now, TimeUnit.SECONDS.toNanos(interval.get()), waitNanos());
            teleportOrigin = playerPos;
            startScan();
        } else if (complete) startScan();
    }

    private void startScan() {
        chests.clear(); positions = Collections.emptyIterator(); scanChunk = null;
        scanOrigin = mc.player.blockPosition(); scanRadius = radius.get(); chunkIndex = 0;
        minChunkX = (scanOrigin.getX() - scanRadius) >> 4;
        minChunkZ = (scanOrigin.getZ() - scanRadius) >> 4;
        chunkWidth = ((scanOrigin.getX() + scanRadius) >> 4) - minChunkX + 1;
        chunkHeight = ((scanOrigin.getZ() + scanRadius) >> 4) - minChunkZ + 1;
    }

    private boolean liveChest(BlockPos pos) {
        if (pos.distSqr(mc.player.blockPosition()) > (double) scanRadius * scanRadius
            || !world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest) || chest.isRemoved()) return false;
        var state = chest.getBlockState();
        // Count one half of normal/trapped double chests; ender chests are personal storage.
        return !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) != ChestType.RIGHT;
    }

    private boolean scan() {
        if (scanChunk != null && (!world.hasChunk(scanChunk.getPos().x(), scanChunk.getPos().z())
            || world.getChunk(scanChunk.getPos().x(), scanChunk.getPos().z()) != scanChunk)) positions = Collections.emptyIterator();
        int visitedChunks = 0, visitedBlocks = 0;
        while (visitedBlocks < 256) {
            if (!positions.hasNext()) {
                if (chunkIndex >= chunkWidth * chunkHeight) return true;
                if (visitedChunks++ >= 4) return false;
                int x = minChunkX + chunkIndex % chunkWidth, z = minChunkZ + chunkIndex / chunkWidth;
                chunkIndex++;
                if (!world.hasChunk(x, z)) continue;
                scanChunk = world.getChunk(x, z);
                positions = List.copyOf(scanChunk.getBlockEntitiesPos()).iterator();
                continue;
            }
            visitedBlocks++;
            BlockPos pos = positions.next();
            if (!liveChest(pos)) continue;
            chests.add(pos.immutable());
            if (chests.size() < minimum.get()) continue;
            chests.removeIf(candidate -> !liveChest(candidate));
            if (chests.size() < minimum.get()) continue;
            BlockPos found = chests.iterator().next();
            if (waypoint.get()) Waypoints.get().add(new Waypoint.Builder()
                .name("RTP storage " + found.toShortString()).pos(found).dimension(PlayerUtils.getDimension()).build());
            info("Found %d chest containers near %s. Auto RTP stopped.", chests.size(), found.toShortString());
            toggle();
            return false;
        }
        return false;
    }

    @Override public String getInfoString() { return attempts + " RTP / " + chests.size() + " chests"; }
}
