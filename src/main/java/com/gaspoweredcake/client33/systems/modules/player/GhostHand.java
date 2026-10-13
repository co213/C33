/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */
package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.entity.player.DoItemUseEvent;
import com.gaspoweredcake.client33.events.render.Render3DEvent;
import com.gaspoweredcake.client33.renderer.ShapeMode;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.systems.modules.render.Freecam;
import com.gaspoweredcake.client33.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Requests normal server interactions at a selected nearby block, ignoring client occlusion. */
public class GhostHand extends Module {
    public enum Mode { Containers, Probe }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Containers targets known storage. Probe attempts one unconfirmed block behind the wall with an empty main hand.")
        .defaultValue(Mode.Containers).build());
    private final Setting<Double> depth = sgGeneral.add(new DoubleSetting.Builder()
        .name("probe-depth").description("Distance behind the visible wall surface to attempt. Preview shows the exact target.")
        .defaultValue(1.1).range(0.1, 4).sliderRange(0.1, 4).visible(() -> mode.get() == Mode.Probe).build());
    private final Setting<Boolean> preview = sgGeneral.add(new BoolSetting.Builder()
        .name("target-preview").description("Outline the reachable target: cyan for known storage, amber for an unconfirmed probe.")
        .defaultValue(true).build());
    private long lastAttempt;

    public GhostHand() { super(Categories.Player, "ghost-hand", "Interact with reachable containers behind walls, or probe a nearby disguised block."); }
    public boolean probing() { return isActive() && mode.get() == Mode.Probe; }
    @Override public void onActivate() { lastAttempt = 0; }

    private BlockHitResult target() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return null;
        var camera = mc.gameRenderer.mainCamera();
        Vec3 origin = camera.position(), look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        double range = Math.min(12, mc.player.blockInteractionRange());
        if (mode.get() == Mode.Probe) {
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
            BlockPos pos = BlockPos.containing(hit.getLocation().add(look.scale(depth.get())));
            return reachable(pos, range) ? hit(pos, look) : null;
        }
        BlockPos previous = null;
        for (double distance = 0; distance <= 12; distance += 0.1) {
            BlockPos pos = BlockPos.containing(origin.add(look.scale(distance)));
            if (pos.equals(previous)) continue;
            previous = pos;
            if (!reachable(pos, range)) continue;
            var state = mc.level.getBlockState(pos);
            if (state.hasBlockEntity() && (state.getMenuProvider(mc.level, pos) != null || state.is(Blocks.ENDER_CHEST))) return hit(pos, look);
        }
        return null;
    }

    private boolean reachable(BlockPos pos, double range) {
        return mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && mc.level.getWorldBorder().isWithinBounds(pos)
            && mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= range * range;
    }
    private BlockHitResult hit(BlockPos pos, Vec3 look) {
        Direction face = Direction.getApproximateNearest(look).getOpposite();
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(point, face, pos, false);
    }

    @EventHandler(priority = EventPriority.HIGH + 1)
    private void onUse(DoItemUseEvent event) {
        if (event.isCancelled() || mc.player == null || mc.player.isShiftKeyDown()) return;
        if (!probing() && Modules.get().get(Freecam.class).handlesContainerInteraction()) return;
        BlockHitResult target = target();
        if (target == null) return;
        event.cancel();
        long now = System.nanoTime();
        if (lastAttempt != 0 && now - lastAttempt < 250_000_000L) return;
        lastAttempt = now;
        if (mode.get() == Mode.Probe) {
            // An empty hand avoids placing or consuming held items when the target is really stone.
            if (!mc.player.getMainHandItem().isEmpty()) {
                warning("Select an empty hotbar slot for a hidden-block probe.");
                return;
            }
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, target);
            if (result instanceof InteractionResult.Success) { mc.player.swing(hand); return; }
            if (result instanceof InteractionResult.Fail) return;
        }
    }

    @EventHandler private void onRender(Render3DEvent event) {
        if (!preview.get()) return;
        BlockHitResult target = target();
        if (target != null) event.renderer.box(target.getBlockPos(), Color.CLEAR,
            mode.get() == Mode.Probe ? new Color(255, 180, 70) : new Color(80, 235, 210), ShapeMode.Lines, 0);
    }
}
