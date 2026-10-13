/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.BlockUpdateEvent;
import com.gaspoweredcake.client33.events.world.ChunkDataEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.render.color.RainbowColors;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

public class BlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to search for.")
        .onChanged(_ -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .build()
    );

    private final Setting<ESPBlockData> defaultBlockConfig = sgGeneral.add(new GenericSetting.Builder<ESPBlockData>()
        .name("default-block-config")
        .description("Default block config.")
        .defaultValue(
            new ESPBlockData(
                ShapeMode.Lines,
                new SettingColor(0, 255, 200),
                new SettingColor(0, 255, 200, 25),
                true,
                new SettingColor(0, 255, 200, 125)
            )
        )
        .build()
    );

    private final Setting<Map<Block, ESPBlockData>> blockConfigs = sgGeneral.add(new BlockDataSetting.Builder<ESPBlockData>()
        .name("block-configs")
        .description("Config for each block.")
        .defaultData(defaultBlockConfig)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Render tracer lines.")
        .defaultValue(false)
        .build()
    );

    private final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

    private final Long2ObjectMap<ESPChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final Set<ESPGroup> groups = new ReferenceOpenHashSet<>();
    private final LinkedHashMap<Long, ChunkAccess> pending = new LinkedHashMap<>();
    private ESPChunk.Search search;
    private ClientLevel scanWorld;

    public BlockESP() {
        super(Categories.Render, "block-esp", "Renders specified blocks through walls.", "search");

        RainbowColors.register(this::onTickRainbow);
    }

    @Override
    public void onActivate() {
        scanWorld = mc.level;
        synchronized (chunks) {
            chunks.clear();
            groups.clear();
            pending.clear();
            search = null;
        }

        for (ChunkAccess chunk : Utils.chunks()) {
            searchChunk(chunk);
        }

    }

    @Override
    public void onDeactivate() {
        synchronized (chunks) {
            chunks.clear();
            groups.clear();
            pending.clear();
            search = null;
        }
    }

    private void onTickRainbow() {
        if (!isActive()) return;

        defaultBlockConfig.get().tickRainbow();
        for (ESPBlockData blockData : blockConfigs.get().values()) blockData.tickRainbow();
    }

    ESPBlockData getBlockData(Block block) {
        ESPBlockData blockData = blockConfigs.get().get(block);
        return blockData == null ? defaultBlockConfig.get() : blockData;
    }

    private void updateChunk(int x, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.pack(x, z));
        if (chunk != null) chunk.update();
    }

    private void updateBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
        if (chunk != null) chunk.update(x, y, z);
    }

    public ESPBlock getBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
        return chunk == null ? null : chunk.get(x, y, z);
    }

    public ESPGroup newGroup(Block block) {
        synchronized (chunks) {
            ESPGroup group = new ESPGroup(block);
            groups.add(group);
            return group;
        }
    }

    public void removeGroup(ESPGroup group) {
        synchronized (chunks) {
            groups.remove(group);
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        searchChunk(event.chunk());
    }

    private void searchChunk(ChunkAccess chunk) {
        if (scanWorld != mc.level) return;
        pending.put(chunk.getPos().pack(), chunk);
        if (search != null && search.source.getPos().equals(chunk.getPos())) search = null;
    }

    private boolean live(ChunkAccess chunk) {
        return scanWorld == mc.level && mc.level.hasChunk(chunk.getPos().x(), chunk.getPos().z())
            && mc.level.getChunk(chunk.getPos().x(), chunk.getPos().z()) == chunk;
    }

    private void release(ESPChunk chunk) {
        if (chunk == null || chunk.blocks == null) return;
        for (ESPBlock block : chunk.blocks.values()) {
            if (block.group != null) block.group.remove(block, false);
            block.loaded = false;
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (scanWorld != mc.level) return;
        int bx = event.pos.getX(), by = event.pos.getY(), bz = event.pos.getZ();
        int chunkX = bx >> 4, chunkZ = bz >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        boolean selected = blocks.get().contains(event.newState.getBlock());
        boolean wasSelected = blocks.get().contains(event.oldState.getBlock());
        if (!selected && !wasSelected) return;
        // Restart a partial scan so changes to already visited positions cannot be lost.
        if (search != null && search.source.getPos().pack() == key) searchChunk(search.source);
        ESPChunk chunk = chunks.get(key);
        if (chunk == null) {
            if (!selected) return;
            chunk = new ESPChunk(chunkX, chunkZ);
            if (chunk.shouldBeDeleted()) return;
            chunks.put(key, chunk);
        }
        blockPos.set(bx, by, bz);
        // Remove the old group before replacing even one selected block with another type.
        chunk.remove(blockPos);
        if (selected) chunk.add(blockPos);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) {
            if (x != 0 || y != 0 || z != 0) updateBlock(bx + x, by + y, bz + z);
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (scanWorld != mc.level) onActivate();
        if (search == null && !pending.isEmpty()) {
            var entry = pending.entrySet().iterator();
            ChunkAccess chunk = entry.next().getValue();
            entry.remove();
            if (live(chunk)) search = new ESPChunk.Search(chunk, blocks.get());
        }
        if (search == null) return;
        if (!live(search.source)) { search = null; return; }
        if (!search.advance(8192)) return;
        int x = search.source.getPos().x(), z = search.source.getPos().z();
        ESPChunk result = search.result;
        search = null;
        release(chunks.remove(ChunkPos.pack(x, z)));
        if (result.size() > 0) { chunks.put(ChunkPos.pack(x, z), result); result.update(); }
        updateChunk(x - 1, z); updateChunk(x + 1, z); updateChunk(x, z - 1); updateChunk(x, z + 1);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (chunks) {
            for (Iterator<ESPChunk> it = chunks.values().iterator(); it.hasNext(); ) {
                ESPChunk chunk = it.next();

                if (chunk.shouldBeDeleted()) {
                    release(chunk);

                    it.remove();
                } else chunk.render(event);
            }

            if (tracers.get()) {
                for (ESPGroup group : groups) {
                    group.render(event);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return "%s groups".formatted(groups.size());
    }
}
