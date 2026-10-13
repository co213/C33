package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.schematic.SchematicBlueprint;
import com.gaspoweredcake.client33.utils.schematic.SchematicCodec;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Uses real Minecraft registries, data fixes and NBT; no graphical client is launched. */
public final class SchematicRegression {
    private static int current;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        current = SharedConstants.getCurrentVersion().dataVersion().version();
        for (int version : new int[] {1, 2, 3}) {
            SchematicBlueprint blueprint = SchematicCodec.decode(sponge(version));
            check(blueprint.width == 2 && blueprint.height == 1 && blueprint.length == 1, "Sponge dimensions");
            check(blueprint.stateAt(0, 0, 0).is(Blocks.STONE) && blueprint.stateAt(1, 0, 0).is(Blocks.AIR), "Sponge coordinate order");
            check(blueprint.includes(0, 0, 0, true) && !blueprint.includes(1, 0, 0, true) && blueprint.includes(1, 0, 0, false), "Air preservation and clearance");
            check(blueprint.materials().get("minecraft:stone") == 1, "Materials exclude air");
            var nativeParser = new baritone.utils.schematic.format.defaults.SpongeSchematic(blueprint.toTag());
            check(nativeParser.getDirect(0, 0, 0).is(Blocks.STONE) && nativeParser.getDirect(1, 0, 0).is(Blocks.AIR), "Converted output is readable by the actual Baritone Sponge parser");
            var nativeBuild = new com.gaspoweredcake.client33.pathing.BlueprintSchematic(blueprint, true);
            check(nativeBuild.widthX() == 2 && nativeBuild.heightY() == 1 && nativeBuild.lengthZ() == 1, "Native builder dimensions");
            check(nativeBuild.inSchematic(0, 0, 0, Blocks.AIR.defaultBlockState()) && !nativeBuild.inSchematic(1, 0, 0, Blocks.STONE.defaultBlockState()), "Native builder masks schematic air");
            check(!nativeBuild.inSchematic(-1, 0, 0, Blocks.AIR.defaultBlockState()) && !nativeBuild.inSchematic(0, 1, 0, Blocks.AIR.defaultBlockState()), "Native builder rejects positions outside the blueprint");
            check(nativeBuild.desiredState(0, 0, 0, Blocks.AIR.defaultBlockState(), List.of()).is(Blocks.STONE), "Native builder requests converted block state");
            SchematicBlueprint roundTrip = SchematicCodec.decode(blueprint.toTag());
            check(roundTrip.stateAt(0, 0, 0).equals(blueprint.stateAt(0, 0, 0)), "Converted Sponge reimport");
        }
        CompoundTag wrapped = new CompoundTag(); wrapped.put("Schematic", sponge(3));
        check(SchematicCodec.decode(wrapped).blockCount() == 1, "Sponge v3 wrapped root");
        CompoundTag legacy = new CompoundTag(); legacy.putShort("Width", (short) 2); legacy.putShort("Height", (short) 1); legacy.putShort("Length", (short) 1);
        legacy.putString("Materials", "Alpha"); legacy.putByteArray("Blocks", new byte[] {1, 35}); legacy.putByteArray("Data", new byte[] {0, 14});
        SchematicBlueprint old = SchematicCodec.decode(legacy);
        check(old.stateAt(0, 0, 0).is(Blocks.STONE) && old.stateAt(1, 0, 0).is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED)), "Pre-1.13 numeric IDs and metadata flatten correctly");
        CompoundTag renamed = sponge(2); renamed.putInt("DataVersion", 1631);
        CompoundTag names = new CompoundTag(); names.putInt("minecraft:grass_path", 0); names.putInt("minecraft:air", 1); renamed.put("Palette", names);
        check(SchematicCodec.decode(renamed).stateAt(0, 0, 0).is(Blocks.DIRT_PATH), "Minecraft data fixer renames historical blocks");
        CompoundTag collapsed = sponge(2); names = new CompoundTag(); names.putInt("minecraft:grass_path", 0); names.putInt("minecraft:dirt_path", 1);
        collapsed.put("Palette", names); collapsed.putInt("DataVersion", 1631);
        SchematicBlueprint duplicates = SchematicCodec.decode(collapsed);
        check(SchematicCodec.decode(duplicates.toTag()).blockCount() == 2, "Palette entries that converge during upgrade export with contiguous IDs");
        CompoundTag structure = structure(); SchematicBlueprint sparse = SchematicCodec.decode(structure);
        check(sparse.stateAt(0, 0, 0).is(Blocks.STONE) && !sparse.includes(1, 0, 0, false), "Unspecified structure positions stay outside the build even when clearing air");
        CompoundTag litematic = litematic(); SchematicBlueprint regions = SchematicCodec.decode(litematic);
        check(regions.width == 5 && regions.stateAt(0, 0, 0).is(Blocks.STONE) && regions.stateAt(4, 0, 0).is(Blocks.OAK_PLANKS), "Negative region sizes and multi-region origin normalization");
        var nativeSparse = new com.gaspoweredcake.client33.pathing.BlueprintSchematic(regions, false);
        check(!nativeSparse.inSchematic(2, 0, 0, Blocks.STONE.defaultBlockState()), "Native builder preserves multi-region gaps even in air-clear mode");
        check(!regions.includes(2, 0, 0, false), "Gaps between regions are preserved");
        int[] values = new int[30]; for (int i = 0; i < values.length; i++) values[i] = i % 5;
        long[] packed = pack(values, 3);
        check(Arrays.equals(SchematicCodec.decodePacked(packed, values.length, 5), values), "Litematica bit packing crosses 64-bit word boundaries");
        rejects(() -> SchematicCodec.decodePacked(new long[0], 30, 5));
        rejects(() -> SchematicCodec.decodePacked(new long[] {7}, 1, 5));
        rejects(() -> SchematicCodec.decodeVarInts(new byte[] {(byte) 128}, 1));
        rejects(() -> SchematicCodec.decodeVarInts(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255, 8}, 1));
        rejects(() -> SchematicCodec.decodeVarInts(new byte[] {0, 0}, 1));
        check(Arrays.equals(SchematicCodec.decodeVarInts(new byte[] {0, (byte) 128, 1}, 2), new int[] {0, 128}), "Multi-byte Sponge palette IDs");
        rejects(() -> SchematicCodec.volume(0, 1, 1)); rejects(() -> SchematicCodec.volume(512, 512, 512));
        rejects(() -> SchematicCodec.volume(Integer.MAX_VALUE, 2, 2));
        CompoundTag future = sponge(2); future.putInt("DataVersion", current + 1); rejects(() -> SchematicCodec.decode(future));
        CompoundTag missing = sponge(2); missing.getCompoundOrEmpty("Palette").putInt("missing_mod:building_block", 0); rejects(() -> SchematicCodec.decode(missing));
        CompoundTag invalidProp = sponge(2); names = new CompoundTag(); names.putInt("minecraft:stone[unknown=true]", 0); names.putInt("minecraft:air", 1); invalidProp.put("Palette", names); rejects(() -> SchematicCodec.decode(invalidProp));
        CompoundTag malformed = sponge(2); malformed.putByteArray("BlockData", new byte[] {3, 1}); rejects(() -> SchematicCodec.decode(malformed));
        CompoundTag modLegacy = legacy.copy(); modLegacy.putByteArray("AddBlocks", new byte[] {1}); rejects(() -> SchematicCodec.decode(modLegacy));
        CompoundTag unknownLegacy = legacy.copy(); unknownLegacy.putByteArray("Blocks", new byte[] {(byte) 254, 35}); rejects(() -> SchematicCodec.decode(unknownLegacy));
        CompoundTag overlap = litematic(); overlap.getCompoundOrEmpty("Regions").put("duplicate", region(0, -2, "minecraft:stone")); rejects(() -> SchematicCodec.decode(overlap));
        CompoundTag missingPos = litematic(); missingPos.getCompoundOrEmpty("Regions").getCompoundOrEmpty("negative").remove("Position");
        rejects(() -> SchematicCodec.decode(missingPos));
        CompoundTag partialPos = litematic(); partialPos.getCompoundOrEmpty("Regions").getCompoundOrEmpty("negative").getCompoundOrEmpty("Position").remove("z");
        rejects(() -> SchematicCodec.decode(partialPos));
        CompoundTag fractionalPos = litematic(); fractionalPos.getCompoundOrEmpty("Regions").getCompoundOrEmpty("negative").getCompoundOrEmpty("Position").putDouble("x", 0.5);
        rejects(() -> SchematicCodec.decode(fractionalPos));
        CompoundTag malformedStructure = structure(); malformedStructure.putString("blocks", "not a block list");
        rejects(() -> SchematicCodec.decode(malformedStructure));
        CompoundTag duplicateBlock = structure(); duplicateBlock.getListOrEmpty("blocks").add(duplicateBlock.getListOrEmpty("blocks").get(0).copy());
        rejects(() -> SchematicCodec.decode(duplicateBlock));
        CompoundTag malformedProps = structure(); malformedProps.getListOrEmpty("palette").getCompoundOrEmpty(0).putString("Properties", "bad");
        rejects(() -> SchematicCodec.decode(malformedProps));
        rejects(() -> SchematicCodec.decodeVarInts(new byte[0], Integer.MAX_VALUE));
        rejects(() -> SchematicCodec.decodeVarInts(new byte[0], -1));
        rejects(() -> SchematicCodec.decodePacked(new long[0], Integer.MAX_VALUE, 2));
        rejects(() -> SchematicCodec.decodePacked(new long[0], 1, 0));
        rejects(() -> SchematicCodec.decodePacked(new long[0], 1, 65537));
        Path dir = Files.createTempDirectory("client33-schematic-test-");
        try {
            Path original = dir.resolve("Old house.schematic"), library = dir.resolve("library");
            NbtIo.writeCompressed(legacy, original); byte[] untouched = Files.readAllBytes(original);
            var imported = SchematicCodec.importFile(original, library);
            check(Arrays.equals(untouched, Files.readAllBytes(original)), "Import preserves original bytes");
            check(imported.file().getParent().equals(library) && imported.file().getFileName().toString().matches("[A-Za-z0-9_.-]+"), "Conversion creates a safe filename in the selected library");
            check(SchematicCodec.readFile(imported.file()).stateAt(1, 0, 0).is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED)), "Compressed output round trip");
            var second = SchematicCodec.importFile(original, library); check(!second.file().equals(imported.file()), "Repeated imports never overwrite another conversion");
            Path plain = dir.resolve("structure.nbt"); NbtIo.write(structure, plain);
            check(SchematicCodec.readFile(plain).blockCount() == 1, "Uncompressed vanilla NBT import");
            Path bad = dir.resolve("bad.schem"); NbtIo.writeCompressed(future, bad);
            long before; try (var files = Files.list(library)) { before = files.count(); }
            try { SchematicCodec.importFile(bad, library); throw new AssertionError("Invalid conversion must fail"); } catch (IllegalArgumentException expected) { }
            try (var files = Files.list(library)) { check(files.count() == before, "Failed validation writes no partial or final output"); }
        } finally {
            try (var files = Files.walk(dir)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("Schematic format, Minecraft upgrade, sparse build, bounds and import persistence regressions passed.");
    }
    private static CompoundTag sponge(int version) {
        CompoundTag root = new CompoundTag(), states = new CompoundTag(), data = new CompoundTag();
        root.putInt("Version", version); root.putInt("DataVersion", current);
        root.putShort("Width", (short) 2); root.putShort("Height", (short) 1); root.putShort("Length", (short) 1);
        states.putInt("minecraft:stone", 0); states.putInt("minecraft:air", 1); data.put("Palette", states);
        data.putByteArray(version == 3 ? "Data" : "BlockData", new byte[] {0, 1});
        if (version == 3) root.put("Blocks", data); else { root.put("Palette", states); root.putByteArray("BlockData", new byte[] {0, 1}); }
        return root;
    }
    private static CompoundTag structure() {
        CompoundTag root = new CompoundTag(); root.putInt("DataVersion", current);
        ListTag size = ints(2, 1, 1); root.put("size", size);
        ListTag states = new ListTag(); states.add(state("minecraft:stone")); root.put("palette", states);
        ListTag blocks = new ListTag(); CompoundTag block = new CompoundTag(); block.put("pos", ints(0, 0, 0)); block.putInt("state", 0); blocks.add(block); root.put("blocks", blocks);
        return root;
    }
    private static CompoundTag litematic() {
        CompoundTag root = new CompoundTag(), regions = new CompoundTag(); root.putInt("Version", 6); root.putInt("MinecraftDataVersion", current);
        regions.put("negative", region(0, -2, "minecraft:stone")); regions.put("positive", region(3, 1, "minecraft:oak_planks")); root.put("Regions", regions);
        return root;
    }
    private static CompoundTag region(int position, int width, String block) {
        CompoundTag region = new CompoundTag(), pos = new CompoundTag(), size = new CompoundTag();
        pos.putInt("x", position); pos.putInt("y", 0); pos.putInt("z", 0); size.putInt("x", width); size.putInt("y", 1); size.putInt("z", 1);
        region.put("Position", pos); region.put("Size", size); ListTag palette = new ListTag(); palette.add(state(block)); region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", pack(new int[Math.abs(width)], 2)); return region;
    }
    private static CompoundTag state(String name) { CompoundTag tag = new CompoundTag(); tag.putString("Name", name); return tag; }
    private static ListTag ints(int... values) { ListTag list = new ListTag(); for (int value : values) list.add(IntTag.valueOf(value)); return list; }
    private static long[] pack(int[] values, int bits) {
        long[] data = new long[(values.length * bits + 63) / 64];
        for (int i = 0; i < values.length; i++) {
            int start = i * bits, word = start / 64, shift = start % 64; data[word] |= (long) values[i] << shift;
            if (shift + bits > 64) data[word + 1] |= (long) values[i] >>> (64 - shift);
        }
        return data;
    }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("Malformed schematic accepted"); } catch (IllegalArgumentException expected) { } }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
