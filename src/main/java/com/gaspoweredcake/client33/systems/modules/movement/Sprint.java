/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.events.entity.player.SendMovementPacketsEvent;
import com.gaspoweredcake.client33.mixin.LocalPlayerAccessor;
import com.gaspoweredcake.client33.utils.movement.SprintPolicy;
import com.gaspoweredcake.client33.settings.BoolSetting;
import com.gaspoweredcake.client33.settings.EnumSetting;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.client.player.LocalPlayer;

public class Sprint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        Strict,
        Rage
    }

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("sprint-mode")
        .description("Strict follows Minecraft sprint rules. Rage also allows sideways and backwards sprinting.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Boolean> keepSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-sprint")
        .description("Whether to keep sprinting after attacking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> unsprintOnHit = sgGeneral.add(new BoolSetting.Builder()
        .name("unsprint-on-hit")
        .description("Stops sprinting before attacks. Critical and sweep attacks still require their normal Minecraft conditions.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> unsprintInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("unsprint-in-water")
        .description("Stops Rage sprinting in water. Strict follows Minecraft swimming rules.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    private final Setting<Boolean> permaSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-while-stationary")
        .description("Sprint even when not moving.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Rage)
        .build()
    );

    public Sprint() {
        super(Categories.Movement, "sprint", "Automatically sprints.");
    }

    private LocalPlayer owner;

    @Override
    public void onDeactivate() {
        // Release automatic sprint without overriding a held manual sprint key.
        if (owner != null && owner == mc.player && !mc.options.keySprint.isDown()) owner.setSprinting(false);
        owner = null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMovement(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        owner = mc.player;
        // Input and vanilla movement have updated; sendPosition synchronizes this state once.
        mc.player.setSprinting(shouldSprint());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled() || !unsprintOnHit.get() || !(event.packet instanceof ServerboundAttackPacket)) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (event.connection != mc.getConnection().getConnection()) return;

        owner = mc.player;
        mc.player.setSprinting(false);
        // Synchronize using vanilla's last-sent state, even if local sprint already stopped.
        // No immediate restart: the local attack must finish with sprint disabled.
        ((LocalPlayerAccessor) mc.player).client33$sendIsSprintingIfNeeded();
    }

    public boolean shouldSprint() {
        LocalPlayer player = mc.player;
        if (!isActive() || player == null || mc.level == null || !player.isAlive() || player.isSpectator()) return false;
        if (mc.gui.screen() != null) {
            GUIMove guiMove = Modules.get().get(GUIMove.class);
            if (!guiMove.isActive() || !guiMove.sprint.get() || guiMove.skip()) return false;
        }
        if (unsprintInWater() && player.isInWater()) return false;

        LocalPlayerAccessor vanilla = (LocalPlayerAccessor) player;
        boolean strict = mode.get() == Mode.Strict;
        boolean eligible;
        if (strict) {
            // Preserve the game's different start, run and swim continuation rules.
            eligible = player.isSprinting()
                ? !(player.isSwimming() ? vanilla.client33$shouldStopSwimSprinting() : vanilla.client33$shouldStopRunSprinting())
                : vanilla.client33$canStartSprinting();
        } else {
            eligible = vanilla.client33$isSprintingPossible(player.getAbilities().flying || player.isSwimming())
                && (!player.horizontalCollision || player.minorHorizontalCollision)
                && !vanilla.client33$isSlowDueToUsingItem()
                && (!player.isFallFlying() || player.isUnderWater())
                && (!player.isMovingSlowly() || player.isUnderWater());
        }
        return SprintPolicy.shouldSprint(strict, eligible, permaSprint.get(), player.zza, player.xxa, player.isUnderWater());
    }

    public boolean rageSprint() {
        return isActive() && mode.get() == Mode.Rage;
    }

    public boolean unsprintInWater() {
        return rageSprint() && unsprintInWater.get();
    }

    public boolean stopSprinting() {
        return !isActive() || !keepSprint.get() || unsprintOnHit.get();
    }
}
