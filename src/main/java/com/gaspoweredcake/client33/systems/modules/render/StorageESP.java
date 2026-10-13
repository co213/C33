/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.render;

import com.gaspoweredcake.client33.events.entity.player.InteractBlockEvent;
import com.gaspoweredcake.client33.events.entity.player.InteractEntityEvent;
import com.gaspoweredcake.client33.events.packets.InventoryEvent;
import com.gaspoweredcake.client33.events.packets.ContainerSlotUpdateEvent;
import com.gaspoweredcake.client33.events.render.Render2DEvent;
import com.gaspoweredcake.client33.renderer.Renderer2D;
import com.gaspoweredcake.client33.renderer.text.TextRenderer;
import com.gaspoweredcake.client33.utils.combat.CombatTelemetry;
import com.gaspoweredcake.client33.utils.storage.ContainerObservations;
import com.gaspoweredcake.client33.utils.storage.ContainerSnapshot;
import com.gaspoweredcake.client33.utils.storage.StorageLocations;
import com.gaspoweredcake.client33.events.world.ChunkDataEvent;
import com.gaspoweredcake.client33.events.world.BlockUpdateEvent;
import java.util.LinkedHashSet;
import com.gaspoweredcake.client33.utils.render.NametagUtils;
import com.gaspoweredcake.client33.utils.render.LabelLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Nameable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.Comparator;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.LinkedHashMap;
import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WVerticalList;
import com.gaspoweredcake.client33.gui.widgets.pressable.WButton;
import com.gaspoweredcake.client33.renderer.MeshBuilder;
import com.gaspoweredcake.client33.renderer.MeshRenderer;
import com.gaspoweredcake.client33.renderer.Client33RenderPipelines;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.player.PlayerUtils;
import com.gaspoweredcake.client33.utils.render.MeshBuilderVertexConsumerProvider;
import com.gaspoweredcake.client33.utils.render.RenderUtils;
import com.gaspoweredcake.client33.utils.render.SimpleBlockRenderer;
import com.gaspoweredcake.client33.utils.render.color.Color;
import com.gaspoweredcake.client33.utils.render.color.SettingColor;
import com.gaspoweredcake.client33.utils.render.postprocess.PostProcessShaders;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.*;

import java.util.List;

public class StorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgData = settings.createGroup("Container Intelligence");
    private final SettingGroup sgVisual = settings.createGroup("Signature Visuals");
    private final ContainerObservations observations = new ContainerObservations();
    private record Overlay(Object source, AABB bounds, Color color, double distance) {}
    private final List<Overlay> overlays = new ArrayList<>();
    private record Remembered(BlockPos pos, String name, long seenAt) {}
    private final StorageLocations<BlockPos, String> locations = new StorageLocations<>(8192);
    private final LinkedHashSet<BlockPos> dirtyPositions = new LinkedHashSet<>();
    private final List<BlockEntity> ordered = new ArrayList<>();
    private Object focused;
    private final Setting<Boolean> labels = sgData.add(new BoolSetting.Builder().name("labels").description("Decluttered storage type, distance and observed occupancy labels.").defaultValue(true).build());
    private final Setting<Boolean> inspector = sgData.add(new BoolSetting.Builder().name("inspector").description("Point at storage for coordinates, block properties and remembered content summaries.").defaultValue(true).build());
    private final Setting<Boolean> rememberContents = sgData.add(new BoolSetting.Builder().name("remember-contents").description("Remember server-sent contents after you open a container. Session only; never opens containers for you.").defaultValue(true).onChanged(value -> { if (!value) observations.clear(); }).build());
    private final Setting<Integer> memorySeconds = sgData.add(new IntSetting.Builder().name("memory-seconds").description("Expire remembered contents; other players may change them sooner.").defaultValue(300).range(10, 1800).sliderRange(10, 600).build());
    private final Setting<Integer> labelBudget = sgData.add(new IntSetting.Builder().name("label-budget").description("Maximum nonoverlapping labels per frame; closest containers have priority.").defaultValue(20).range(1, 64).sliderRange(1, 64).build());
    private final Setting<Integer> labelRange = sgData.add(new IntSetting.Builder().name("label-range").description("Maximum label distance; 0 removes the distance limit. Label budget still applies.").defaultValue(128).range(0, 1024).sliderRange(0, 256).build());
    private final Setting<Boolean> coordinates = sgData.add(new BoolSetting.Builder().name("label-coordinates").description("Add XYZ coordinates to each label; always shown in the inspector.").defaultValue(false).build());
    private final Setting<Integer> contentLines = sgData.add(new IntSetting.Builder().name("content-lines").description("Most numerous item types to list in the inspector.").defaultValue(5).range(1, 12).sliderRange(1, 12).build());
    private final Setting<Double> labelScale = sgVisual.add(new DoubleSetting.Builder().name("label-scale").description("Scale of container labels and inspector text.").defaultValue(0.7).range(0.4, 1.2).sliderRange(0.4, 1.2).build());
    private final Setting<Boolean> animate = sgVisual.add(new BoolSetting.Builder().name("animate").description("Animate hologram scan bands and locator rings.").defaultValue(true).build());
    private final Setting<SettingColor> accent = sgVisual.add(new ColorSetting.Builder().name("scan-accent").description("Hologram scan and focused-container accent.").defaultValue(new SettingColor(106, 232, 217, 220)).build());
    private final Setting<Boolean> rememberLocations = sgData.add(new BoolSetting.Builder()
        .name("remember-locations").description("Keep last-seen storage outlines when unloaded or disguised. Amber markers are unconfirmed, not live chests.")
        .defaultValue(true).onChanged(value -> { if (!value) locations.clear(); }).build());
    private final Setting<Integer> locationMemory = sgData.add(new IntSetting.Builder()
        .name("location-memory-seconds").description("Expire unconfirmed storage locations after this many seconds. Cleared on world change.")
        .defaultValue(600).range(10, 3600).sliderRange(30, 1800).build());
    private final SettingGroup sgOpened = settings.createGroup("Opened Rendering");

    private final LinkedHashMap<BlockPos, BlockEntity> containers = new LinkedHashMap<>();
    private final LinkedHashMap<Long, ChunkAccess> scanQueue = new LinkedHashMap<>();
    private Object scanWorld;
    private int scanTick;
    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder().name("chunks-per-tick")
        .description("Incremental discovery budget for loaded 26.2 chunk block entities.").defaultValue(4).range(1, 32).sliderRange(1, 32).build());
    private final Setting<Boolean> vehicles = sgGeneral.add(new BoolSetting.Builder().name("container-vehicles")
        .description("Include loaded container entities such as chest boats and chest/hopper minecarts.").defaultValue(true).build());
    private final Setting<Integer> renderBudget = sgGeneral.add(new IntSetting.Builder().name("render-budget")
        .description("Maximum container overlays per frame.").defaultValue(512).range(16, 4096).sliderRange(16, 1024).build());

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rendering mode.")
        .defaultValue(Mode.Box)
        .build()
    );

    private final Setting<BoxStyle> boxStyle = sgGeneral.add(new EnumSetting.Builder<BoxStyle>()
        .name("box-style")
        .description("Full edges, corner brackets, or an animated hologram with a locator ring.")
        .defaultValue(BoxStyle.Hologram)
        .visible(() -> mode.get() == Mode.Box)
        .build()
    );

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to display.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracers to storage blocks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines)
        .defaultValue(28)
        .range(0, 255)
        .sliderMax(255)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("The width of the shader outline.")
        .visible(() -> mode.get() == Mode.Shader)
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect")
        .visible(() -> mode.get() == Mode.Shader)
        .decimalPlaces(3)
        .defaultValue(0.7)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<SettingColor> chest = sgGeneral.add(new ColorSetting.Builder()
        .name("chest")
        .description("The color of chests.")
        .defaultValue(new SettingColor(226, 188, 139, 220))
        .build()
    );

    private final Setting<SettingColor> trappedChest = sgGeneral.add(new ColorSetting.Builder()
        .name("trapped-chest")
        .description("The color of trapped chests.")
        .defaultValue(new SettingColor(231, 141, 148, 220))
        .build()
    );

    private final Setting<SettingColor> barrel = sgGeneral.add(new ColorSetting.Builder()
        .name("barrel")
        .description("The color of barrels.")
        .defaultValue(new SettingColor(140, 204, 176, 220))
        .build()
    );

    private final Setting<SettingColor> shulker = sgGeneral.add(new ColorSetting.Builder()
        .name("shulker")
        .description("The color of Shulker Boxes.")
        .defaultValue(new SettingColor(160, 183, 225, 220))
        .build()
    );

    private final Setting<SettingColor> enderChest = sgGeneral.add(new ColorSetting.Builder()
        .name("ender-chest")
        .description("The color of Ender Chests.")
        .defaultValue(new SettingColor(185, 158, 224, 220))
        .build()
    );

    private final Setting<SettingColor> other = sgGeneral.add(new ColorSetting.Builder()
        .name("other")
        .description("The color of furnaces, dispensers, droppers and hoppers.")
        .defaultValue(new SettingColor(173, 193, 198, 210))
        .build()
    );

    private final Setting<Double> fadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("Distance from the camera over which nearby storage fades in.")
        .defaultValue(6)
        .min(0)
        .sliderMax(12)
        .build()
    );

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum camera distance. 0 shows all received storage within the render budget, plus enabled location memory.")
        .defaultValue(0)
        .min(0)
        .sliderMax(128)
        .build()
    );

    private final Setting<Integer> farFadeDistance = sgGeneral.add(new IntSetting.Builder()
        .name("far-fade-distance")
        .description("Distance over which storage gently fades out near the maximum range.")
        .defaultValue(12)
        .min(0)
        .sliderMax(32)
        .visible(() -> maxDistance.get() > 0)
        .build()
    );

    private final Setting<Boolean> hideOpened = sgOpened.add(new BoolSetting.Builder()
        .name("hide-opened")
        .description("Hides containers with an unexpired server-observed contents snapshot.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> openedColor = sgOpened.add(new ColorSetting.Builder()
        .name("opened-color")
        .description("Optional setting to change colors of opened chests, as opposed to not rendering. Disabled at zero opacity.")
        .defaultValue(new SettingColor(154, 171, 181, 0)) // TRANSPARENT BY DEFAULT.
        .build()
    );


    private final Color lineColor = new Color(0, 0, 0, 0);
    private final Color sideColor = new Color(0, 0, 0, 0);
    private boolean render;
    private int count;

    private final MeshBuilder mesh;
    private final MeshBuilderVertexConsumerProvider vertexConsumerProvider;

    public StorageESP() {
        super(Categories.Render, "storage-esp", "Incrementally indexes loaded 26.2 containers and vehicle storage, with bounded discovery and rendering.");

        mesh = new MeshBuilder(Client33RenderPipelines.WORLD_COLORED);
        vertexConsumerProvider = new MeshBuilderVertexConsumerProvider(mesh);
    }

    private void getBlockEntityColor(BlockEntity blockEntity) {
        render = false;

        if (!storageBlocks.get().contains(blockEntity.getType())) return;

        if (blockEntity instanceof TrappedChestBlockEntity)
            lineColor.set(trappedChest.get()); // Must come before ChestBlockEntity as it is the superclass of TrappedChestBlockEntity
        else if (blockEntity instanceof ChestBlockEntity) lineColor.set(chest.get());
        else if (blockEntity instanceof BarrelBlockEntity) lineColor.set(barrel.get());
        else if (blockEntity instanceof ShulkerBoxBlockEntity) lineColor.set(shulker.get());
        else if (blockEntity instanceof EnderChestBlockEntity) lineColor.set(enderChest.get());
        else if (blockEntity instanceof AbstractFurnaceBlockEntity || blockEntity instanceof BrewingStandBlockEntity || blockEntity instanceof ChiseledBookShelfBlockEntity || blockEntity instanceof CrafterBlockEntity || blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DecoratedPotBlockEntity || blockEntity instanceof HopperBlockEntity)
            lineColor.set(other.get());
        else return;

        render = true;

        if (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both) {
            sideColor.set(lineColor);
            sideColor.a = lineColor.a * fillOpacity.get() / 255;
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        list.add(theme.label("Only server-sent containers can be detected. Amber memory is unconfirmed."));
        list.add(theme.button("Use maximum loaded range")).widget().action = () -> {
            maxDistance.set(0); fadeDistance.set(0.0); labelRange.set(0); renderBudget.set(4096);
        };
        // Button to Clear Interacted Blocks
        WButton clear = list.add(theme.button("Clear Rendering Cache")).expandX().widget();

        clear.action = this::clearCache;

        return list;
    }

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!event.isCancelled() && mc.level != null) observations.attempt(mc.level.getBlockEntity(event.result.getBlockPos()));
    }

    @EventHandler private void onEntityInteract(InteractEntityEvent event) {
        if (!event.isCancelled()) observations.attempt(event.entity);
    }
    @EventHandler private void onInventory(InventoryEvent event) {
        if (rememberContents.get()) observations.full(event.packet);
    }
    @EventHandler private void onSlot(ContainerSlotUpdateEvent event) {
        if (rememberContents.get()) observations.slot(event.packet);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        count = 0; overlays.clear();
        if (mc.level == null || mc.player == null) return;
        Mode renderMode = mode.get();

        if (scanWorld != mc.level) clearCache();
        for (BlockEntity blockEntity : ordered) {
            if (count >= renderBudget.get()) break;
            BlockPos livePos = blockEntity.getBlockPos();
            if (!mc.level.hasChunk(livePos.getX() >> 4, livePos.getZ() >> 4)
                || mc.level.getBlockEntity(livePos) != blockEntity) continue;
            if (renderMode == Mode.Box && isSecondHalfOfDoubleChest(blockEntity)) continue;
            // Check if the block has been interacted with (opened)
            var snapshot = observations.snapshot(blockEntity, memorySeconds.get() * 1000L);
            boolean interacted = snapshot != null;
            if (interacted && hideOpened.get()) continue;  // Skip rendering if "hideOpened" is true

            getBlockEntityColor(blockEntity);
            if (!render) continue;

            // Set the color to openedColor if its alpha is greater than 0
            if (interacted && openedColor.get().a > 0) {
                // openedColor takes precedence.
                lineColor.set(openedColor.get());
                sideColor.set(openedColor.get());
                sideColor.a = lineColor.a * fillOpacity.get() / 255;
            }

            if (render) {
                double a = opacityAt(blockEntity.getBlockPos());
                if (a < 0.075) continue;

                // Only start a mesh when there's something to render
                if (count == 0 && mode.get() == Mode.Shader) {
                    mesh.begin();
                }

                int prevLineA = lineColor.a;
                int prevSideA = sideColor.a;

                lineColor.a *= a;
                sideColor.a *= a;

                if (tracers.get()) {
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 0.5, blockEntity.getBlockPos().getZ() + 0.5, lineColor);
                }

                if (focused == ContainerObservations.canonical(blockEntity)) lineColor.set(accent.get()).a((int) (accent.get().a * a));
                if (!isSecondHalfOfDoubleChest(blockEntity)) overlays.add(new Overlay(ContainerObservations.canonical(blockEntity), bounds(blockEntity), new Color(lineColor),
                    Math.sqrt(PlayerUtils.squaredDistanceToCamera(livePos.getX() + 0.5, livePos.getY() + 0.5, livePos.getZ() + 0.5))));

                if (mode.get() == Mode.Box) {
                    renderBox(event, blockEntity);
                }

                if (mode.get() == Mode.Shader) {
                    renderShader(event, blockEntity);
                }

                lineColor.a = prevLineA;
                sideColor.a = prevSideA;

                count++;
            }
        }

        if (mode.get() == Mode.Shader && count > 0) {
            MeshRenderer.begin()
                .attachments(PostProcessShaders.STORAGE_OUTLINE.framebuffer)
                .clearColor(Color.CLEAR)
                .pipeline(Client33RenderPipelines.WORLD_COLORED)
                .mesh(mesh, event.matrices)
                .end();

            PostProcessShaders.STORAGE_OUTLINE.render();
        }
        if (vehicles.get()) for (var entity : mc.level.entitiesForRendering()) {
            if (count >= renderBudget.get()) break;
            if (!(entity instanceof ContainerEntity) || !entity.isAlive()) continue;
            double opacity = opacityAt(entity.blockPosition());
            if (opacity < 0.075) continue;
            Color line = new Color(other.get()).a((int) (other.get().a * opacity));
            Color fill = new Color(line).a(line.a * fillOpacity.get() / 255);
            var bounds = entity.getBoundingBox().move(Mth.lerp(event.tickDelta, entity.xOld, entity.getX()) - entity.getX(),
                Mth.lerp(event.tickDelta, entity.yOld, entity.getY()) - entity.getY(), Mth.lerp(event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ());
            if (hideOpened.get() && observations.snapshot(entity, memorySeconds.get() * 1000L) != null) continue;
            if (openedColor.get().a > 0 && observations.snapshot(entity, memorySeconds.get() * 1000L) != null) {
                line.set(openedColor.get()).a((int) (openedColor.get().a * opacity)); fill.set(line).a(line.a * fillOpacity.get() / 255);
            }
            if (focused == entity) line.set(accent.get());
            event.renderer.box(bounds, fill, line, shapeMode.get(), 0);
            if (boxStyle.get() == BoxStyle.Hologram) hologram(event, bounds, line);
            if (tracers.get()) event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, bounds.getCenter().x, bounds.getCenter().y, bounds.getCenter().z, line);
            overlays.add(new Overlay(entity, bounds, line, Math.sqrt(PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()))));
            count++;
        }
    }

    @EventHandler(priority = meteordevelopment.orbit.EventPriority.LOW)
    private void onRememberedRender(Render3DEvent event) {
        if (!rememberLocations.get() || scanWorld != mc.level || mc.player == null) return;
        int budget = Math.min(256, Math.max(0, renderBudget.get() - count));
        for (var entry : locations.entries()) {
            if (budget <= 0) break;
            BlockPos pos = entry.key();
            if (mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                BlockEntity live = mc.level.getBlockEntity(pos);
                if (live != null && storageBlocks.get().contains(live.getType())) continue;
            }
            double opacity = opacityAt(pos);
            if (opacity < 0.075) continue;
            Color color = new Color(255, 180, 70, (int) (160 * opacity));
            AABB bounds = new AABB(pos);
            event.renderer.box(pos, new Color(0, 0, 0, 0), color, ShapeMode.Lines, 0);
            overlays.add(new Overlay(new Remembered(pos, entry.value(), entry.time()), bounds, color,
                Math.sqrt(PlayerUtils.squaredDistanceToCamera(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))));
            budget--;
        }
    }

    private void clearCache() {
        containers.clear(); ordered.clear(); overlays.clear(); observations.clear(); locations.clear(); dirtyPositions.clear(); focused = null; scanQueue.clear(); scanTick = 0; scanWorld = mc.level;
    }
    @Override public void onActivate() { clearCache(); }
    @Override public void onDeactivate() { clearCache(); }
    @EventHandler private void onLeave(GameLeftEvent event) { clearCache(); }
    @EventHandler private void onChunkData(ChunkDataEvent event) {
        if (scanWorld != mc.level) clearCache();
        scanQueue.put(event.chunk().getPos().pack(), event.chunk());
    }
    @EventHandler private void onBlockUpdate(BlockUpdateEvent event) {
        if (dirtyPositions.size() >= 2048) dirtyPositions.remove(dirtyPositions.iterator().next());
        dirtyPositions.add(event.pos.immutable());
    }
    private void observe(BlockPos pos) {
        if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        BlockEntity entity = mc.level.getBlockEntity(pos);
        if (entity == null || !storageBlocks.get().contains(entity.getType())) { containers.remove(pos); return; }
        if (containers.size() >= 8192 && !containers.containsKey(pos)) containers.remove(containers.keySet().iterator().next());
        containers.put(pos.immutable(), entity);
        if (rememberLocations.get()) locations.observe(pos.immutable(), name(entity), CombatTelemetry.now());
    }
    @EventHandler private void onTick(TickEvent.Post event) {
        if (scanWorld != mc.level) clearCache();
        observations.tick(memorySeconds.get() * 1000L);
        locations.expire(CombatTelemetry.now(), locationMemory.get() * 1000L);
        if (++scanTick % 20 == 0) containers.entrySet().removeIf(entry -> !mc.level.hasChunk(entry.getKey().getX() >> 4, entry.getKey().getZ() >> 4)
            || mc.level.getBlockEntity(entry.getKey()) != entry.getValue() || !storageBlocks.get().contains(entry.getValue().getType()));
        var dirty = dirtyPositions.iterator();
        for (int i = 0; i < 128 && dirty.hasNext(); i++) { BlockPos pos = dirty.next(); dirty.remove(); observe(pos); }
        if (scanQueue.isEmpty()) for (ChunkAccess chunk : Utils.chunks()) {
            double x = chunk.getPos().x() * 16.0 + 8, z = chunk.getPos().z() * 16.0 + 8;
            if (maxDistance.get() == 0 || PlayerUtils.squaredDistanceToCamera(x, mc.player.getY(), z) <= Math.pow(maxDistance.get() + 24, 2))
                scanQueue.put(chunk.getPos().pack(), chunk);
        }
        for (int i = 0; i < chunksPerTick.get() && !scanQueue.isEmpty(); i++) {
            var iterator = scanQueue.values().iterator();
            ChunkAccess chunk = iterator.next(); iterator.remove();
            if (!mc.level.hasChunk(chunk.getPos().x(), chunk.getPos().z()) || mc.level.getChunk(chunk.getPos().x(), chunk.getPos().z()) != chunk) continue;
            for (BlockPos pos : chunk.getBlockEntitiesPos()) observe(pos);
        }
        ordered.clear(); ordered.addAll(containers.values());
        ordered.sort(Comparator.comparingDouble(be -> PlayerUtils.squaredDistanceToCamera(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5)));
    }


    private double opacityAt(BlockPos pos) {
        double distanceSquared = PlayerUtils.squaredDistanceToCamera(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        int limit = maxDistance.get();
        if (limit > 0 && distanceSquared >= (double) limit * limit) return 0;

        double distance = Math.sqrt(distanceSquared);
        double opacity = 1;
        double nearFade = fadeDistance.get();
        if (nearFade > 0) {
            double t = Mth.clamp(distance / nearFade, 0, 1);
            opacity *= t * t * (3 - 2 * t);
        }

        int farFade = farFadeDistance.get();
        if (limit > 0 && farFade > 0) {
            double t = Mth.clamp((limit - distance) / Math.min(farFade, limit), 0, 1);
            opacity *= t * t * (3 - 2 * t);
        }
        return opacity;
    }

    private boolean isSecondHalfOfDoubleChest(BlockEntity blockEntity) {
        return ContainerObservations.canonical(blockEntity) != blockEntity;
    }

    private AABB bounds(BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = x1 + 1;
        double y2 = y1 + 1;
        double z2 = z1 + 1;

        BlockEntity otherHalf = ContainerObservations.partner(blockEntity);
        if (otherHalf != null) {
            BlockPos otherPos = otherHalf.getBlockPos();
            x1 = Math.min(x1, otherPos.getX()); z1 = Math.min(z1, otherPos.getZ());
            x2 = Math.max(x2, otherPos.getX() + 1); z2 = Math.max(z2, otherPos.getZ() + 1);
        }

        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            double inset = 1.0 / 16.0;
            x1 += inset;
            z1 += inset;
            x2 -= inset;
            y2 -= inset * 2;
            z2 -= inset;
        }

        return new AABB(x1, y1, z1, x2, y2, z2);
    }

    private void renderBox(Render3DEvent event, BlockEntity blockEntity) {
        AABB box = bounds(blockEntity);
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        if (boxStyle.get() == BoxStyle.Hologram) hologram(event, box, lineColor);
        if (boxStyle.get() == BoxStyle.Full) {
            event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode.get(), 0);
        } else {
            if (shapeMode.get().sides()) event.renderer.boxSides(x1, y1, z1, x2, y2, z2, sideColor, 0);
            if (shapeMode.get().lines()) renderCorners(event, x1, y1, z1, x2, y2, z2);
        }
    }

    private void renderCorners(Render3DEvent event, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = Math.min(0.25, (x2 - x1) * 0.28);
        double dy = Math.min(0.25, (y2 - y1) * 0.28);
        double dz = Math.min(0.25, (z2 - z1) * 0.28);
        for (int ix = 0; ix < 2; ix++) {
            double x = ix == 0 ? x1 : x2;
            double nextX = x + (ix == 0 ? dx : -dx);
            for (int iy = 0; iy < 2; iy++) {
                double y = iy == 0 ? y1 : y2;
                double nextY = y + (iy == 0 ? dy : -dy);
                for (int iz = 0; iz < 2; iz++) {
                    double z = iz == 0 ? z1 : z2;
                    double nextZ = z + (iz == 0 ? dz : -dz);
                    event.renderer.line(x, y, z, nextX, y, z, lineColor);
                    event.renderer.line(x, y, z, x, nextY, z, lineColor);
                    event.renderer.line(x, y, z, x, y, nextZ, lineColor);
                }
            }
        }
    }

    private void hologram(Render3DEvent event, AABB box, Color color) {
        if (!shapeMode.get().lines()) return;
        double phase = animate.get() ? (mc.level.getGameTime() + event.tickDelta) * 0.045 : 0;
        double y = box.minY + box.getYsize() * (0.5 + Math.sin(phase) * 0.45);
        Color scan = new Color(accent.get()).a(Math.min(color.a, accent.get().a));
        event.renderer.line(box.minX, y, box.minZ, box.maxX, y, box.minZ, scan);
        event.renderer.line(box.maxX, y, box.minZ, box.maxX, y, box.maxZ, scan);
        event.renderer.line(box.maxX, y, box.maxZ, box.minX, y, box.maxZ, scan);
        event.renderer.line(box.minX, y, box.maxZ, box.minX, y, box.minZ, scan);
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double radius = Math.max(box.getXsize(), box.getZsize()) * 0.65;
        for (int i = 0; i < 24; i++) {
            if (i % 6 == 5) continue;
            double a = phase * 0.4 + i * Math.PI / 12, b = a + Math.PI / 12;
            event.renderer.line(cx + Math.cos(a) * radius, box.minY + 0.02, cz + Math.sin(a) * radius,
                cx + Math.cos(b) * radius, box.minY + 0.02, cz + Math.sin(b) * radius, color);
        }
    }

    private String name(Object source) {
        if (source instanceof Remembered remembered) return remembered.name + " (last seen)";
        if (source instanceof Entity entity) return ContainerSnapshot.clean(entity.getDisplayName().getString(), 34);
        BlockEntity block = (BlockEntity) source;
        if (block instanceof Nameable named && named.hasCustomName()) return ContainerSnapshot.clean(named.getDisplayName().getString(), 34);
        return (ContainerObservations.partner(block) == null ? "" : "Double ") + ContainerSnapshot.clean(block.getBlockState().getBlock().getName().getString(), 26);
    }
    private BlockPos position(Object source) { if (source instanceof Remembered remembered) return remembered.pos; return source instanceof Entity entity ? entity.blockPosition() : ((BlockEntity) source).getBlockPos(); }
    private String location(Object source) { BlockPos p = position(source); return p.getX() + " / " + p.getY() + " / " + p.getZ(); }
    private ContainerSnapshot snapshot(Object source) { return rememberContents.get() ? observations.snapshot(source, memorySeconds.get() * 1000L) : null; }
    private String summary(Object source, ContainerSnapshot data) {
        if (source instanceof Remembered) return "Unconfirmed location; may be removed or disguised";
        if (data != null) return data.occupied() + "/" + data.slots() + " slots  ·  " + data.items() + (data.items() == 1 ? " item" : " items");
        int capacity = ContainerObservations.capacity(source);
        return (capacity == 0 ? "" : capacity + " slots  ·  ") + "Contents unknown";
    }
    private Object hitSource() {
        if (mc.hitResult instanceof BlockHitResult hit) {
            BlockEntity block = mc.level.getBlockEntity(hit.getBlockPos());
            return block == null ? null : ContainerObservations.canonical(block);
        }
        if (mc.hitResult instanceof EntityHitResult hit) return hit.getEntity();
        return null;
    }

    @EventHandler private void onLabels(Render2DEvent event) {
        if (mc.player == null || scanWorld != mc.level || mc.gui.screen() != null || (!labels.get() && !inspector.get())) { focused = null; return; }
        double width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
        overlays.sort(Comparator.comparingDouble(Overlay::distance));
        Object hit = hitSource(); Overlay selection = null;
        double nearest = 75 * 75;
        for (Overlay overlay : overlays) {
            if (labelRange.get() > 0 && overlay.distance > labelRange.get()) continue;
            if (overlay.source == hit) { selection = overlay; break; }
            var center = overlay.bounds.getCenter();
            Vector3d projected = new Vector3d(center.x, center.y, center.z);
            if (!NametagUtils.to2D(projected, 1, false) || projected.z < 0 || projected.z > 1) continue;
            double dx = projected.x - width / 2, dy = projected.y - height / 2;
            double distance = dx * dx + dy * dy;
            if (distance < nearest) { nearest = distance; selection = overlay; }
        }
        focused = selection == null ? null : selection.source;
        Renderer2D.COLOR.begin();
        TextRenderer text = TextRenderer.get(); text.begin(event.graphics, labelScale.get());
        LabelLayout layout = new LabelLayout(width, height);
        if (inspector.get() && selection != null) drawInspector(text, selection, width, height, layout);
        int drawn = 0;
        if (labels.get()) for (Overlay overlay : overlays) {
            if (drawn >= labelBudget.get()) break;
            if (labelRange.get() > 0 && overlay.distance > labelRange.get()) continue;
            AABB box = overlay.bounds;
            Vector3d point = new Vector3d((box.minX + box.maxX) / 2, box.maxY + 0.28, (box.minZ + box.maxZ) / 2);
            if (!NametagUtils.to2D(point, 1, false) || point.z < 0 || point.z > 1) continue;
            var data = snapshot(overlay.source);
            String title = name(overlay.source) + "  /  " + Math.round(overlay.distance) + " m";
            String detail = summary(overlay.source, data);
            String state = overlay.source instanceof Remembered remembered ? "LAST SEEN " + Math.max(0, (CombatTelemetry.now() - remembered.seenAt) / 1000) + "s AGO" : data == null ? "NOT OBSERVED" : (observations.live(overlay.source) ? "RECEIVING" : (data.serverObserved() ? "RECEIVED " : "MENU VIEW ") + Math.max(0, (CombatTelemetry.now() - data.observedAt()) / 1000) + "s AGO");
            List<String> lines = new ArrayList<>(List.of(title, detail, state));
            if (coordinates.get()) lines.add(location(overlay.source));
            double w = lines.stream().mapToDouble(text::getWidth).max().orElse(0) + 20;
            double h = lines.size() * (text.getHeight() + 3) + 15;
            var rect = layout.place(point.x, point.y, w, h);
            if (rect == null) continue;
            panel(rect, overlay.source == focused ? accent.get() : overlay.color);
            for (int i = 0; i < lines.size(); i++) text.render(lines.get(i), rect.x() + 9, rect.y() + 6 + i * (text.getHeight() + 3), i == 0 ? Color.WHITE : new Color(171, 188, 202), false);
            if (data != null) {
                Renderer2D.COLOR.quad(rect.x() + 8, rect.y() + h - 6, w - 16, 2, new Color(49, 58, 68));
                Renderer2D.COLOR.quad(rect.x() + 8, rect.y() + h - 6, (w - 16) * data.occupancy(), 2, overlay.color);
            }
            Renderer2D.COLOR.line(point.x, point.y + 8, rect.x() + w / 2, rect.y() + h, overlay.color);
            drawn++;
        }
        Renderer2D.COLOR.render(); text.end();
    }

    private void panel(LabelLayout.Rect rect, Color color) {
        Renderer2D.COLOR.quad(rect.x(), rect.y(), rect.width(), rect.height(), new Color(12, 16, 23, 218));
        Renderer2D.COLOR.boxLines(rect.x(), rect.y(), rect.width(), rect.height(), new Color(57, 72, 86, 210));
        Renderer2D.COLOR.quad(rect.x(), rect.y(), 2, rect.height(), color);
        Renderer2D.COLOR.quad(rect.x(), rect.y(), Math.min(36, rect.width()), 2, color);
    }

    private void drawInspector(TextRenderer text, Overlay overlay, double screenWidth, double screenHeight, LabelLayout layout) {
        List<String> lines = new ArrayList<>();
        Object source = overlay.source;
        var data = snapshot(source);
        BlockPos pos = position(source);
        lines.add("STORAGE INTEL  /  " + name(source));
        lines.add("XYZ  " + location(source) + "  ·  " + Math.round(overlay.distance) + " m");
        lines.add("Chunk " + (pos.getX() >> 4) + ", " + (pos.getZ() >> 4) + "  /  " + mc.level.dimension().identifier().getPath());
        if (source instanceof BlockEntity block) {
            lines.add(BuiltInRegistries.BLOCK.getKey(block.getBlockState().getBlock()).toString());
            StringBuilder properties = new StringBuilder();
            for (var entry : block.getBlockState().getValues().sorted(Comparator.comparing(e -> e.property().getName())).toList()) {
                String value = entry.property().getName() + "=" + entry.valueName();
                if (properties.length() + value.length() > 44) { lines.add(properties.toString()); properties.setLength(0); }
                if (!properties.isEmpty()) properties.append("  "); properties.append(value);
            }
            if (!properties.isEmpty()) lines.add(properties.toString());
            if (block instanceof ChestBlockEntity) {
                BlockEntity partner = ContainerObservations.partner(block);
                boolean obstructed = mc.level.getBlockState(pos.above()).isRedstoneConductor(mc.level, pos.above())
                    || (partner != null && mc.level.getBlockState(partner.getBlockPos().above()).isRedstoneConductor(mc.level, partner.getBlockPos().above()));
                lines.add("Block above: " + (obstructed ? "obstructed" : "clear") + "  ·  " + (partner == null ? "single" : "paired"));
            }
        } else if (source instanceof Entity entity) {
            lines.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            lines.add("Entity #" + entity.getId() + "  ·  " + (entity.isAlive() ? "loaded" : "removed"));
        }
        lines.add(summary(source, data));
        if (source instanceof Remembered) lines.add("This is a remembered location, not current server data.");
        else if (data == null) lines.add(rememberContents.get() ? "Open container to observe its contents." : "Content memory disabled.");
        else {
            lines.add((data.serverObserved() ? "Server snapshot " : "Client menu view ") + Math.max(0, (CombatTelemetry.now() - data.observedAt()) / 1000) + "s ago  ·  may have changed");
            if (data.enchanted() > 0 || data.lowestDurability() >= 0)
                lines.add(data.enchanted() + (data.enchanted() == 1 ? " enchanted stack" : " enchanted stacks") + (data.lowestDurability() < 0 ? "" : "  ·  min durability " + data.lowestDurability() + "%"));
            int shown = 0;
            for (var item : data.contents()) { if (shown++ >= contentLines.get()) break; lines.add(item.count() + " × " + item.name()); }
            if (data.contents().size() > contentLines.get()) lines.add("+ " + (data.contents().size() - contentLines.get()) + " more item types");
            if (data.occupied() == 0) lines.add("Empty when observed");
        }
        double lineHeight = text.getHeight() + 5;
        int fit = Math.max(1, (int) ((screenHeight - 130) / lineHeight));
        if (lines.size() > fit) lines = new ArrayList<>(lines.subList(0, fit));
        double w = Math.min(screenWidth - 24, Math.max(250, Math.min(440, lines.stream().mapToDouble(text::getWidth).max().orElse(0) + 24)));
        double h = lines.size() * lineHeight + 20;
        var rect = new LabelLayout.Rect(Math.max(12, screenWidth - w - 18), Math.max(12, Math.min(90, screenHeight - h - 12)), w, h);
        layout.reserve(rect); panel(rect, accent.get());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            while (line.length() > 1 && text.getWidth(line) > w - 24) line = line.substring(0, line.length() - 2) + "…";
            text.render(line, rect.x() + 12, rect.y() + 9 + i * lineHeight, i == 0 ? accent.get() : i < 3 ? Color.WHITE : new Color(181, 197, 209), false);
        }
    }

    private void renderShader(Render3DEvent event, BlockEntity blockEntity) {
        vertexConsumerProvider.setColor(lineColor);
        SimpleBlockRenderer.renderWithBlockEntity(blockEntity, event.tickDelta, vertexConsumerProvider);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && mode.get() == Mode.Shader;
    }

    public enum Mode {
        Box,
        Shader
    }

    public enum BoxStyle {
        Corners,
        Full,
        Hologram
    }
}
