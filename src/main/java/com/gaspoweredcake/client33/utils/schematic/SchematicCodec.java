package com.gaspoweredcake.client33.utils.schematic;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Reads common building formats, with bounded allocations and Minecraft's own palette upgrades. */
public final class SchematicCodec {
    public static final int MAX_BLOCKS = 2_000_000;
    private static final long MAX_FILE = 64L * 1024 * 1024;
    private static final long NBT_QUOTA = 128L * 1024 * 1024;
    private SchematicCodec() {}
    public record Imported(Path file, SchematicBlueprint blueprint) {}
    private record Raw(String format, int version, int width, int height, int length, ListTag palette, int[] blocks) {}

    public static SchematicBlueprint readFile(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_FILE) throw invalid("Choose a schematic file smaller than 64 MiB.");
        CompoundTag root;
        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(source))) {
            stream.mark(2); int first = stream.read(), second = stream.read(); stream.reset();
            InputStream input = first == 31 && second == 139 ? new GZIPInputStream(stream) : stream;
            root = NbtIo.read(new DataInputStream(input), NbtAccounter.create(NBT_QUOTA));
        }
        return decode(root);
    }

    public static Imported importFile(Path source, Path folder) throws IOException {
        SchematicBlueprint blueprint = readFile(source);
        Files.createDirectories(folder);
        String name = source.getFileName().toString().replaceFirst("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9_-]", "_");
        if (name.isEmpty()) name = "building";
        name = name.substring(0, Math.min(60, name.length()));
        Path target = folder.resolve(name + "-26_2-" + UUID.randomUUID().toString().substring(0, 8) + ".schem");
        Path temporary = Files.createTempFile(folder, ".converting-", ".tmp");
        try {
            NbtIo.writeCompressed(blueprint.toTag(), temporary);
            Files.move(temporary, target); // Never overwrite an original or another import.
        } finally { Files.deleteIfExists(temporary); }
        return new Imported(target, blueprint);
    }

    public static SchematicBlueprint decode(CompoundTag input) {
        Raw raw;
        CompoundTag root = input.contains("Schematic") ? input.getCompoundOrEmpty("Schematic") : input;
        if (root.contains("Regions")) raw = litematic(root);
        else if (root.contains("Blocks") && root.get("Blocks") instanceof ByteArrayTag) raw = legacy(root);
        else if (root.contains("Palette") || root.get("Blocks") instanceof CompoundTag) raw = sponge(root);
        else if (root.contains("palette") && root.contains("blocks")) raw = structure(root);
        else throw invalid("Unrecognized format. Choose .schematic, .schem, .litematic or a structure .nbt file.");
        checkPalette(raw.palette);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        if (raw.version < 0) throw invalid("Invalid Minecraft data version.");
        if (raw.version > current) throw invalid("This file is from a newer Minecraft version. Downgrading is not supported.");
        CompoundTag upgrade = new CompoundTag();
        upgrade.put("palette", raw.palette.copy());
        upgrade.put("blocks", new ListTag()); upgrade.put("entities", new ListTag());
        ListTag size = new ListTag(); size.add(IntTag.valueOf(raw.width)); size.add(IntTag.valueOf(raw.height)); size.add(IntTag.valueOf(raw.length));
        upgrade.put("size", size);
        CompoundTag fixed = DataFixTypes.STRUCTURE.update(DataFixers.getDataFixer(), upgrade, raw.version, current);
        ListTag palette = fixed.getListOrEmpty("palette");
        if (palette.size() != raw.palette.size()) throw invalid("Minecraft changed palette indexing; this file cannot be safely converted.");
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) states.add(validateState(palette.getCompoundOrEmpty(i)));
        for (int index : raw.blocks) if (index < 0 || index >= states.size()) throw invalid("Block data references a missing palette entry.");
        List<String> notes = new ArrayList<>();
        notes.add("Blocks only: entities, chest contents and block-entity data are not built.");
        if (raw.version < current) notes.add("Block states upgraded with Minecraft's data fixer.");
        return new SchematicBlueprint(raw.format, raw.version, current, raw.width, raw.height, raw.length, states, raw.blocks, notes);
    }

    private static Raw sponge(CompoundTag root) {
        int format = root.getIntOr("Version", 0);
        if (format < 1 || format > 3) throw invalid("Only Sponge schematic versions 1–3 are supported.");
        int w = root.getIntOr("Width", 0), h = root.getIntOr("Height", 0), l = root.getIntOr("Length", 0), volume = volume(w, h, l);
        CompoundTag container = format == 3 ? root.getCompoundOrEmpty("Blocks") : root;
        CompoundTag sourcePalette = container.getCompoundOrEmpty("Palette");
        if (sourcePalette.isEmpty() || sourcePalette.size() > 65536) throw invalid("Invalid or oversized block palette.");
        int max = -1;
        for (String key : sourcePalette.keySet()) {
            int id = sourcePalette.getIntOr(key, -1);
            if (id < 0 || id >= 65536) throw invalid("Invalid palette index.");
            max = Math.max(max, id);
        }
        CompoundTag[] entries = new CompoundTag[max + 1];
        for (String key : sourcePalette.keySet()) {
            int id = sourcePalette.getIntOr(key, -1);
            if (entries[id] != null) throw invalid("Duplicate palette index.");
            entries[id] = parseState(key);
        }
        ListTag palette = new ListTag();
        for (CompoundTag entry : entries) { if (entry == null) throw invalid("Missing palette index."); palette.add(entry); }
        byte[] data = container.getByteArray(format == 3 ? "Data" : "BlockData").orElseThrow(() -> invalid("Missing block data."));
        return new Raw("Sponge v" + format, root.getIntOr("DataVersion", 1519), w, h, l, palette, decodeVarInts(data, volume));
    }

    private static Raw legacy(CompoundTag root) {
        if (!root.getStringOr("Materials", "Alpha").equals("Alpha")) throw invalid("Only Alpha/MCEdit legacy schematics are supported.");
        int w = root.getIntOr("Width", 0), h = root.getIntOr("Height", 0), l = root.getIntOr("Length", 0), volume = volume(w, h, l);
        byte[] ids = root.getByteArray("Blocks").orElseThrow(), metadata = root.getByteArray("Data").orElseThrow();
        byte[] extra = root.getByteArray("AddBlocks").orElse(new byte[0]);
        if (ids.length != volume || metadata.length != volume || (extra.length != 0 && extra.length != (volume + 1) / 2)) throw invalid("Legacy block arrays do not match the dimensions.");
        ListTag palette = new ListTag(); Map<Integer, Integer> indexes = new HashMap<>(); int[] blocks = new int[volume];
        for (int i = 0; i < volume; i++) {
            int id = ids[i] & 255, meta = metadata[i] & 255;
            if (meta > 15 || (extra.length != 0 && ((extra[i / 2] >> ((i & 1) * 4)) & 15) != 0)) throw invalid("Modded legacy numeric block IDs cannot be mapped reliably.");
            int value = id * 16 + meta;
            Integer index = indexes.get(value);
            if (index == null) {
                Dynamic<?> converted = BlockStateData.getTag(value);
                CompoundTag state = (CompoundTag) converted.convert(NbtOps.INSTANCE).getValue();
                if (id != 0 && state.getStringOr("Name", "minecraft:air").equals("minecraft:air")) throw invalid("Unknown legacy block ID " + id + ".");
                index = palette.size(); palette.add(state.copy()); indexes.put(value, index);
            }
            blocks[i] = index;
        }
        // getTag has already performed the pre-1.13 numeric flattening.
        return new Raw("Legacy MCEdit", 1519, w, h, l, palette, blocks);
    }

    private static Raw structure(CompoundTag root) {
        ListTag size = root.getListOrEmpty("size");
        if (size.size() != 3) throw invalid("Missing structure dimensions.");
        int w = size.getIntOr(0, 0), h = size.getIntOr(1, 0), l = size.getIntOr(2, 0), volume = volume(w, h, l);
        ListTag palette = root.getListOrEmpty("palette").copy(); checkPalette(palette);
        int ignored = palette.size(); palette.add(parseState("minecraft:structure_void"));
        int[] blocks = new int[volume]; Arrays.fill(blocks, ignored);
        if (!(root.get("blocks") instanceof ListTag)) throw invalid("Missing or malformed structure block list.");
        ListTag entries = root.getListOrEmpty("blocks");
        if (entries.size() > volume) throw invalid("Too many structure blocks.");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompoundOrEmpty(i); ListTag pos = entry.getListOrEmpty("pos");
            if (pos.size() != 3) throw invalid("Invalid structure block position.");
            int x = pos.getIntOr(0, -1), y = pos.getIntOr(1, -1), z = pos.getIntOr(2, -1);
            int index = index(x, y, z, w, h, l), state = entry.getIntOr("state", -1);
            if (state < 0 || state >= ignored || blocks[index] != ignored) throw invalid("Invalid or duplicate structure block.");
            blocks[index] = state;
        }
        return new Raw("Vanilla structure", root.getIntOr("DataVersion", 1519), w, h, l, palette, blocks);
    }

    private static Raw litematic(CompoundTag root) {
        int version = root.getIntOr("Version", 0);
        if (version < 4 || version > 7) throw invalid("Only Litematica versions 4–7 are supported.");
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.isEmpty() || regions.size() > 256) throw invalid("Invalid Litematica region count.");
        int minX = Integer.MAX_VALUE, minY = minX, minZ = minX, maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
        long total = 0;
        for (String key : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(key); int[] bounds = regionBounds(region);
            total += volume(bounds[3], bounds[4], bounds[5]); if (total > MAX_BLOCKS) throw invalid("Too many region blocks.");
            minX = Math.min(minX, bounds[0]); minY = Math.min(minY, bounds[1]); minZ = Math.min(minZ, bounds[2]);
            maxX = Math.max(maxX, Math.addExact(bounds[0], bounds[3])); maxY = Math.max(maxY, Math.addExact(bounds[1], bounds[4])); maxZ = Math.max(maxZ, Math.addExact(bounds[2], bounds[5]));
        }
        int w = Math.subtractExact(maxX, minX), h = Math.subtractExact(maxY, minY), l = Math.subtractExact(maxZ, minZ);
        int[] blocks = new int[volume(w, h, l)]; Arrays.fill(blocks, -1);
        ListTag palette = new ListTag();
        for (String key : new TreeSet<>(regions.keySet())) {
            CompoundTag region = regions.getCompoundOrEmpty(key); int[] bounds = regionBounds(region);
            ListTag states = region.getListOrEmpty("BlockStatePalette"); checkPalette(states);
            int base = palette.size(); if ((long) base + states.size() > 65535) throw invalid("Combined palette is too large.");
            for (Tag state : states) palette.add(state.copy());
            int[] data = decodePacked(region.getLongArray("BlockStates").orElseThrow(() -> invalid("Missing Litematica block data.")), volume(bounds[3], bounds[4], bounds[5]), states.size());
            for (int y = 0; y < bounds[4]; y++) for (int z = 0; z < bounds[5]; z++) for (int x = 0; x < bounds[3]; x++) {
                int dest = index(bounds[0] - minX + x, bounds[1] - minY + y, bounds[2] - minZ + z, w, h, l);
                if (blocks[dest] != -1) throw invalid("Overlapping Litematica regions must be merged before import.");
                blocks[dest] = base + data[(y * bounds[5] + z) * bounds[3] + x];
            }
        }
        int ignored = palette.size(); palette.add(parseState("minecraft:structure_void"));
        for (int i = 0; i < blocks.length; i++) if (blocks[i] == -1) blocks[i] = ignored;
        return new Raw("Litematica", root.getIntOr("MinecraftDataVersion", 1519), w, h, l, palette, blocks);
    }

    private static int[] regionBounds(CompoundTag region) {
        CompoundTag pos = region.getCompoundOrEmpty("Position"), size = region.getCompoundOrEmpty("Size");
        int[] result = new int[6]; String[] keys = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            if (!(size.get(keys[i]) instanceof IntTag) || !(pos.get(keys[i]) instanceof IntTag))
                throw invalid("Every Litematica region needs integer X/Y/Z position and size fields.");
            int extent = size.getIntOr(keys[i], 0), start = pos.getIntOr(keys[i], 0);
            if (extent == 0 || Math.abs((long) extent) > 512) throw invalid("Invalid region size.");
            result[i] = extent < 0 ? Math.addExact(start, extent + 1) : start; result[i + 3] = Math.abs(extent);
        }
        return result;
    }

    public static int volume(int w, int h, int l) {
        if (w < 1 || h < 1 || l < 1 || w > 512 || h > 512 || l > 512 || (long) w * h * l > MAX_BLOCKS)
            throw invalid("Building must fit within 512 blocks per side and 2 million total cells.");
        return w * h * l;
    }
    private static int index(int x, int y, int z, int w, int h, int l) {
        if (x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= l) throw invalid("Block position is outside the schematic.");
        return (y * l + z) * w + x;
    }
    public static int[] decodeVarInts(byte[] data, int count) {
        if (count < 1 || count > MAX_BLOCKS) throw invalid("Invalid block data size.");
        int[] result = new int[count]; int cursor = 0;
        for (int i = 0; i < count; i++) {
            int value = 0, shift = 0;
            while (true) {
                if (cursor >= data.length || shift >= 35) throw invalid("Truncated or oversized block index.");
                int next = data[cursor++] & 255;
                if (shift == 28 && (next & 0xF8) != 0) throw invalid("Block index overflow.");
                value |= (next & 127) << shift;
                if ((next & 128) == 0) break;
                shift += 7;
            }
            result[i] = value;
        }
        if (cursor != data.length) throw invalid("Unexpected trailing block data.");
        return result;
    }
    public static int[] decodePacked(long[] data, int count, int paletteSize) {
        if (count < 1 || count > MAX_BLOCKS || paletteSize < 1 || paletteSize > 65536) throw invalid("Invalid packed block data size or palette.");
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        if (data.length != ((long) count * bits + 63) / 64) throw invalid("Packed block data does not match the dimensions.");
        int[] result = new int[count]; long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            long start = (long) i * bits; int word = (int) (start / 64), shift = (int) (start % 64);
            long value = data[word] >>> shift;
            if (shift + bits > 64) value |= data[word + 1] << (64 - shift);
            result[i] = (int) (value & mask);
            if (result[i] >= paletteSize) throw invalid("Invalid packed palette index.");
        }
        return result;
    }
    private static void checkPalette(ListTag palette) {
        if (palette.isEmpty() || palette.size() > 65536 || palette.stream().anyMatch(tag -> !(tag instanceof CompoundTag))) throw invalid("Invalid block-state palette.");
        for (Tag entry : palette) {
            CompoundTag state = (CompoundTag) entry;
            if (!(state.get("Name") instanceof StringTag) || state.getStringOr("Name", "").isBlank()) throw invalid("Missing block name in palette.");
            if (state.contains("Properties")) {
                if (!(state.get("Properties") instanceof CompoundTag properties)
                    || properties.values().stream().anyMatch(value -> !(value instanceof StringTag))) throw invalid("Malformed block-state properties.");
            }
        }
    }
    private static CompoundTag parseState(String value) {
        CompoundTag state = new CompoundTag(), properties = new CompoundTag();
        int bracket = value.indexOf('['); String name = bracket < 0 ? value : value.substring(0, bracket);
        state.putString("Name", Identifier.parse(name).toString());
        if (bracket >= 0) {
            if (!value.endsWith("]")) throw invalid("Malformed block state: " + value);
            String content = value.substring(bracket + 1, value.length() - 1);
            if (!content.isEmpty()) for (String part : content.split(",", -1)) {
                String[] pair = part.split("=", -1);
                if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty() || properties.contains(pair[0])) throw invalid("Malformed block properties.");
                properties.putString(pair[0], pair[1]);
            }
        }
        if (!properties.isEmpty()) state.put("Properties", properties);
        return state;
    }
    private static BlockState validateState(CompoundTag tag) {
        Identifier id = Identifier.parse(tag.getStringOr("Name", ""));
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw invalid("Unavailable block after conversion: " + id + ". Install its compatible mod or replace it in the source.");
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        if (tag.contains("Properties") && !(tag.get("Properties") instanceof CompoundTag)) throw invalid("Malformed block properties on " + id + ".");
        CompoundTag properties = tag.getCompoundOrEmpty("Properties");
        for (String key : properties.keySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
            if (property == null || property.getValue(properties.getStringOr(key, "")).isEmpty()) throw invalid("Unsupported property " + key + " on " + id + ".");
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag);
    }
    public static String stateString(BlockState state) {
        CompoundTag tag = NbtUtils.writeBlockState(state), props = tag.getCompoundOrEmpty("Properties");
        String name = tag.getStringOr("Name", "");
        if (props.isEmpty()) return name;
        List<String> parts = new ArrayList<>();
        for (String key : new TreeSet<>(props.keySet())) parts.add(key + "=" + props.getStringOr(key, ""));
        return name + "[" + String.join(",", parts) + "]";
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
