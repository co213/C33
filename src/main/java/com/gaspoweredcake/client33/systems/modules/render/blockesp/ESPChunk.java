/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

import static com.gaspoweredcake.client33.Client33.mc;
import static com.gaspoweredcake.client33.utils.Utils.getRenderDistance;

public class ESPChunk {

    private final int x, z;
    public Long2ObjectMap<ESPBlock> blocks;

    public ESPChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ESPBlock get(int x, int y, int z) {
        return blocks == null ? null : blocks.get(ESPBlock.getKey(x, y, z));
    }

    public void add(BlockPos blockPos, boolean update) {
        ESPBlock block = new ESPBlock(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        if (blocks == null) blocks = new Long2ObjectOpenHashMap<>(64);
        blocks.put(ESPBlock.getKey(blockPos), block);

        if (update) block.update();
    }

    public void add(BlockPos blockPos) {
        add(blockPos, true);
    }

    public void remove(BlockPos blockPos) {
        if (blocks != null) {
            ESPBlock block = blocks.remove(ESPBlock.getKey(blockPos));
            if (block != null && block.group != null) block.group.remove(block);
        }
    }

    public void update() {
        if (blocks != null) {
            for (ESPBlock block : blocks.values()) block.update();
        }
    }

    public void update(int x, int y, int z) {
        if (blocks != null) {
            ESPBlock block = blocks.get(ESPBlock.getKey(x, y, z));
            if (block != null) block.update();
        }
    }

    public int size() {
        return blocks == null ? 0 : blocks.size();
    }

    public boolean shouldBeDeleted() {
        int viewDist = getRenderDistance() + 1;
        int chunkX = SectionPos.posToSectionCoord(mc.player.blockPosition().getX());
        int chunkZ = SectionPos.posToSectionCoord(mc.player.blockPosition().getZ());

        return x > chunkX + viewDist || x < chunkX - viewDist || z > chunkZ + viewDist || z < chunkZ - viewDist;
    }

    public void render(Render3DEvent event) {
        if (blocks != null) {
            for (ESPBlock block : blocks.values()) block.render(event);
        }
    }


    /** Client-thread incremental scan; palette filtering skips sections with no selected blocks. */
    public static final class Search {
        public final ChunkAccess source;
        public final ESPChunk result;
        private final java.util.Set<Block> selected;
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private int section, offset;

        public Search(ChunkAccess chunk, List<Block> blocks) {
            source = chunk;
            result = new ESPChunk(chunk.getPos().x(), chunk.getPos().z());
            selected = java.util.Set.copyOf(blocks);
        }

        public boolean advance(int budget) {
            var sections = source.getSections();
            while (section < sections.length && budget-- > 0) {
                var palette = sections[section];
                if (offset == 0 && !palette.maybeHas(state -> selected.contains(state.getBlock()))) { section++; continue; }
                int x = offset & 15, z = (offset >> 4) & 15, y = offset >> 8;
                if (selected.contains(palette.getBlockState(x, y, z).getBlock())) {
                    position.set(source.getPos().getMinBlockX() + x, source.getMinY() + section * 16 + y, source.getPos().getMinBlockZ() + z);
                    result.add(position, false);
                }
                if (++offset == 4096) { offset = 0; section++; }
            }
            return section == sections.length;
        }
    }

    public static ESPChunk searchChunk(ChunkAccess chunk, List<Block> blocks) {
        Search search = new Search(chunk, blocks);
        search.advance(Integer.MAX_VALUE);
        return search.result;
    }
}
