package com.gaspoweredcake.client33.utils.schematic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Validated, current-version blocks in X-fastest, then Z, then Y order. */
public final class SchematicBlueprint {
    public final String format;
    public final int sourceVersion, version, width, height, length;
    public final List<String> notes;
    private final List<BlockState> palette;
    private final int[] blocks;
    private final Map<String, Integer> materials;
    private final int blockCount;

    SchematicBlueprint(String format, int sourceVersion, int version, int width, int height, int length,
                       List<BlockState> palette, int[] blocks, List<String> notes) {
        this.format = format; this.sourceVersion = sourceVersion; this.version = version;
        this.width = width; this.height = height; this.length = length;
        this.palette = List.copyOf(palette); this.blocks = blocks.clone(); this.notes = List.copyOf(notes);
        int[] counts = new int[palette.size()];
        for (int index : blocks) counts[index]++;
        Map<String, Integer> totals = new TreeMap<>();
        for (int i = 0; i < counts.length; i++) {
            BlockState state = palette.get(i);
            if (counts[i] != 0 && !state.isAir() && !state.is(Blocks.STRUCTURE_VOID))
                totals.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), counts[i], Integer::sum);
        }
        materials = java.util.Collections.unmodifiableMap(totals);
        blockCount = totals.values().stream().mapToInt(Integer::intValue).sum();
    }

    public BlockState stateAt(int x, int y, int z) { return palette.get(blocks[(y * length + z) * width + x]); }
    public boolean includes(int x, int y, int z, boolean keepAir) {
        BlockState state = stateAt(x, y, z);
        return !state.is(Blocks.STRUCTURE_VOID) && !(keepAir && state.isAir());
    }
    public Map<String, Integer> materials() { return materials; }
    public int blockCount() { return blockCount; }

    /** Sponge v2 output understood by the installed Baritone parser. Original files stay untouched. */
    public CompoundTag toTag() {
        CompoundTag root = new CompoundTag(), states = new CompoundTag();
        root.putInt("Version", 2); root.putInt("DataVersion", version);
        root.putShort("Width", (short) width); root.putShort("Height", (short) height); root.putShort("Length", (short) length);
        root.putIntArray("Offset", new int[] {0, 0, 0});
        int[] remap = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            String key = SchematicCodec.stateString(palette.get(i));
            if (!states.contains(key)) states.putInt(key, states.size());
            remap[i] = states.getIntOr(key, 0);
        }
        root.put("Palette", states); root.putInt("PaletteMax", states.size());
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int index : blocks) {
            int value = remap[index];
            do { data.write((value & 127) | (value > 127 ? 128 : 0)); value >>>= 7; } while (value != 0);
        }
        root.putByteArray("BlockData", data.toByteArray());
        return root;
    }
}
