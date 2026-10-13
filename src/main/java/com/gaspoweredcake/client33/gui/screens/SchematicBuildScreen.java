package com.gaspoweredcake.client33.gui.screens;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.WindowScreen;
import com.gaspoweredcake.client33.gui.widgets.WLabel;
import com.gaspoweredcake.client33.gui.widgets.containers.*;
import com.gaspoweredcake.client33.gui.widgets.input.WIntEdit;
import com.gaspoweredcake.client33.gui.widgets.input.WDropdown;
import com.gaspoweredcake.client33.gui.widgets.pressable.WCheckbox;
import com.gaspoweredcake.client33.pathing.IPathManager;
import com.gaspoweredcake.client33.pathing.Navigation;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.utils.navigation.BaritoneTasks;
import com.gaspoweredcake.client33.utils.schematic.SchematicCodec;
import net.minecraft.core.BlockPos;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.gaspoweredcake.client33.Client33.mc;

/** Import and review first; only the explicit Build button starts placing blocks. */
public final class SchematicBuildScreen extends WindowScreen {
    private static final ExecutorService IMPORTS = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().daemon().name("client33-schematic-import").factory());
    private final Path folder = mc.gameDirectory.toPath().resolve("schematics");
    private final IPathManager manager = PathManagers.get();
    private SchematicCodec.Imported imported;
    private boolean importing, keepAir = true;
    private int originX, originY, originZ;
    private WIntEdit x, y, z;
    private WLabel feedback, status;
    private WVerticalList preview;

    public SchematicBuildScreen(GuiTheme theme) {
        super(theme, "Building Studio");
        if (mc.player != null) {
            BlockPos pos = mc.player.blockPosition(); originX = pos.getX(); originY = pos.getY(); originZ = pos.getZ();
        }
    }

    @Override public void initWidgets() {
        add(theme.label("1. Import a building  /  2. Choose its origin  /  3. Build", 460)).expandX();
        add(theme.label("Browse or drop one .schematic, .schem, .litematic or structure .nbt file here.", 460)
            .color(theme.textSecondaryColor())).expandX();
        add(theme.button("Browse & convert building...")).expandX().widget().action = this::browse;
        addSaved();
        preview = add(theme.verticalList()).expandX().widget();
        updatePreview();
        WSection placement = add(theme.section("Building origin", true)).expandX().widget();
        placement.add(theme.label("The lowest X/Y/Z corner goes here. The building extends east, up and south.", 460));
        WTable table = placement.add(theme.table()).expandX().widget();
        table.add(theme.label("X")); x = table.add(theme.intEdit(originX, -30_000_000, 30_000_000, 0, 0, true)).expandX().widget(); table.row();
        table.add(theme.label("Y")); y = table.add(theme.intEdit(originY, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, true)).expandX().widget(); table.row();
        table.add(theme.label("Z")); z = table.add(theme.intEdit(originZ, -30_000_000, 30_000_000, 0, 0, true)).expandX().widget();
        x.action = () -> originX = x.get(); y.action = () -> originY = y.get(); z.action = () -> originZ = z.get();
        placement.add(theme.button("Use my position")).expandX().widget().action = () -> {
            if (mc.player == null) { feedback.set("Join a world first."); return; }
            BlockPos pos = mc.player.blockPosition();
            x.set(originX = pos.getX()); y.set(originY = pos.getY()); z.set(originZ = pos.getZ());
        };
        WHorizontalList air = placement.add(theme.horizontalList()).expandX().widget();
        WCheckbox keep = air.add(theme.checkbox(keepAir)).widget(); keep.action = () -> keepAir = keep.checked;
        air.add(theme.label("Keep existing blocks where the schematic is air", 400));
        keep.tooltip = "Enabled: place the building's solid blocks. Disabled: also clear air spaces inside its bounding box.";
        add(theme.button("Build building")).expandX().widget().action = this::build;
        WHorizontalList controls = add(theme.horizontalList()).expandX().widget();
        controls.add(theme.button("Pause")).expandX().widget().action = () -> control(0);
        controls.add(theme.button("Resume")).expandX().widget().action = () -> control(1);
        controls.add(theme.button("Stop")).expandX().widget().action = () -> control(2);
        status = add(theme.label("Builder: " + manager.getStatus(), 460)).expandX().widget();
        feedback = add(theme.label(importing ? "Converting in the background..." : imported == null ? "Import a building to begin." : "Ready: " + imported.file().getFileName(), 460)).expandX().widget();
        add(theme.label("Bring the required blocks. Baritone uses your inventory and normal placement; missing materials or unreachable blocks can stall a build.", 460)
            .color(theme.textSecondaryColor())).expandX();
        add(theme.button("Back")).expandX().widget().action = this::onClose;
    }

    private void browse() {
        if (importing) { feedback.set("Wait for the current conversion."); return; }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            for (String extension : List.of("*.schematic", "*.schem", "*.litematic", "*.nbt")) filters.put(stack.UTF8(extension));
            filters.flip();
            String file = TinyFileDialogs.tinyfd_openFileDialog("Import building", folder.toString() + "/", filters, "Minecraft buildings", false);
            if (file != null) importBuilding(Path.of(file), false);
        }
    }
    @Override public void onFilesDrop(List<Path> files) {
        if (files.size() != 1) { feedback.set("Drop one building file at a time."); return; }
        importBuilding(files.getFirst(), false);
    }
    private void addSaved() {
        try {
            if (!Files.isDirectory(folder)) return;
            List<String> names;
            try (var files = Files.list(folder)) {
                names = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.matches(".*-26_2-[a-f0-9]{8}\\.schem")).sorted().limit(100).toList();
            }
            if (names.isEmpty()) return;
            WHorizontalList row = add(theme.horizontalList()).expandX().widget();
            WDropdown<String> saved = row.add(theme.dropdown(names.toArray(String[]::new), names.getFirst())).expandX().widget();
            saved.tooltip = "Previously converted buildings in your schematics folder.";
            row.add(theme.button("Load saved")).widget().action = () -> importBuilding(folder.resolve(saved.get()), true);
        } catch (java.io.IOException ignored) { /* Browse remains available if the folder cannot be listed. */ }
    }
    private void importBuilding(Path source, boolean saved) {
        if (importing) { feedback.set("Wait for the current conversion."); return; }
        importing = true;
        // A failed replacement must never leave an older building armed for Build.
        imported = null;
        updatePreview();
        feedback.set("Reading and converting " + source.getFileName() + "...");
        CompletableFuture.supplyAsync(() -> {
            try { return saved ? new SchematicCodec.Imported(source, SchematicCodec.readFile(source)) : SchematicCodec.importFile(source, folder); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }, IMPORTS).whenComplete((result, error) -> mc.execute(() -> {
            importing = false;
            if (error != null) {
                Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                feedback.set("Import failed: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
                return;
            }
            imported = result;
            updatePreview();
            feedback.set("Ready. Choose the origin, then Build. Converted copy: " + result.file().getFileName());
        }));
    }
    private void updatePreview() {
        preview.clear();
        if (imported == null) return;
        var blueprint = imported.blueprint();
        preview.add(theme.label("Selected building: " + imported.file().getFileName(), 460));
        preview.add(theme.label(blueprint.format + "  /  " + blueprint.width + " × " + blueprint.height + " × " + blueprint.length
            + "  /  " + blueprint.blockCount() + " blocks", 460));
        preview.add(theme.label("Minecraft data version " + blueprint.sourceVersion + " to " + blueprint.version, 460).color(theme.textSecondaryColor()));
        for (String note : blueprint.notes) preview.add(theme.label(note, 460).color(theme.textSecondaryColor()));
        WSection materials = preview.add(theme.section("Materials — block counts", false)).expandX().widget();
        materials.add(theme.label("Counts are block cells, not exact item costs: doors, beds, crops and double slabs can differ.", 460));
        materials.add(theme.button("Copy complete material counts")).expandX().widget().action = () -> {
            StringBuilder text = new StringBuilder("Block counts (item costs may differ):\n");
            blueprint.materials().forEach((name, count) -> text.append(name).append(" × ").append(count).append('\n'));
            mc.keyboardHandler.setClipboard(text.toString());
            feedback.set("Complete material counts copied.");
        };
        if (blueprint.materials().size() > 100) materials.add(theme.label("Showing the largest 100 types. Copy for the complete list.", 460));
        blueprint.materials().entrySet().stream().sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()).limit(100)
            .forEach(entry -> materials.add(theme.label(entry.getKey() + "  × " + entry.getValue(), 460)));
    }
    private void build() {
        if (importing) { feedback.set("Wait for conversion to finish."); return; }
        if (imported == null) { feedback.set("Import a building first."); return; }
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { feedback.set("Join a world with a living player first."); return; }
        if (!manager.supportsSchematicBuild()) { feedback.set("Install Baritone for Minecraft 26.2 to enable building."); return; }
        try {
            var blueprint = imported.blueprint();
            int endX = Math.addExact(x.get(), blueprint.width - 1), endY = Math.addExact(y.get(), blueprint.height - 1), endZ = Math.addExact(z.get(), blueprint.length - 1);
            BaritoneTasks.goTo(x.get(), y.get(), z.get(), false); BaritoneTasks.goTo(endX, endY, endZ, false);
            BlockPos origin = new BlockPos(x.get(), y.get(), z.get());
            if (mc.level.isOutsideBuildHeight(y.get()) || mc.level.isOutsideBuildHeight(endY)) throw new IllegalArgumentException("The building extends outside this world's height limits.");
            for (int px : new int[] {x.get(), endX}) for (int pz : new int[] {z.get(), endZ})
                if (!mc.level.getWorldBorder().isWithinBounds(new BlockPos(px, y.get(), pz))) throw new IllegalArgumentException("The building extends outside the world border.");
            if (blueprint.blockCount() == 0) throw new IllegalArgumentException("This schematic has no solid building blocks.");
            Navigation.stop(); manager.stop(); manager.resume();
            manager.buildSchematic(imported.file().getFileName().toString(), blueprint, origin, keepAir);
            feedback.set("Building at " + origin.toShortString() + ". Close this screen to let Baritone move; reopen for Pause or Stop.");
        } catch (IllegalArgumentException | ArithmeticException error) { feedback.set(error.getMessage()); }
    }
    private void control(int action) {
        if (!manager.supportsSchematicBuild()) { feedback.set("Install Baritone for Minecraft 26.2 first."); return; }
        if (action == 0) { manager.pause(); feedback.set("Paused."); }
        else if (action == 1) { manager.resume(); feedback.set("Resumed."); }
        else { Navigation.stop(); manager.stop(); feedback.set("Stopped."); }
    }
    @Override public void tick() {
        super.tick();
        String value = "Builder: " + manager.getStatus();
        if (!value.equals(status.get())) status.set(value);
    }
}
