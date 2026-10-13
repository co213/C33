/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.movement;

import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.utils.movement.StepAssist;
import com.gaspoweredcake.client33.utils.movement.StepHeightLease;
import com.gaspoweredcake.client33.utils.movement.StepRecovery;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.gaspoweredcake.client33.pathing.PathManagers;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;


public class Step extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Auto uses normal jumps in multiplayer and instant steps in singleplayer. Native needs the server to permit increased step height.")
        .defaultValue(Mode.Auto).build());

    public final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Maximum obstacle height. Normal-jump modes are limited to 1.25 blocks.")
        .defaultValue(1.25)
        .range(0, 4)
        .sliderRange(0.6, 2)
        .build()
    );

    private final Setting<ActiveWhen> activeWhen = sgGeneral.add(new EnumSetting.Builder<ActiveWhen>()
        .name("active-when")
        .description("Step is active when you meet these requirements.")
        .defaultValue(ActiveWhen.Always)
        .build()
    );

    private final Setting<Boolean> safeStep = sgGeneral.add(new BoolSetting.Builder()
        .name("safe-step")
        .description("Doesn't let you step out of a hole if you are low on health or there is a crystal nearby.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> stepHealth = sgGeneral.add(new IntSetting.Builder()
        .name("step-health")
        .description("The health you stop being able to step at.")
        .defaultValue(5)
        .range(1, 36)
        .sliderRange(1, 36)
        .visible(safeStep::get)
        .build()
    );

    private LocalPlayer owner;
    private StepHeightLease heightLease;
    private Setting<Boolean> pathStep;
    private boolean prevPathManagerStep, ownsPathStep;
    private final StepRecovery recovery = new StepRecovery();
    private volatile boolean nativeApplied;
    private boolean reportedFallback;

    private boolean nativeMode() {
        return !recovery.jumpFallback() && (mode.get() == Mode.Native || mode.get() == Mode.Auto && mc.isLocalServer());
    }

    public Step() {
        super(Categories.Movement, "step", "Climbs blocks using normal jumps on servers or instant native stepping when allowed.");
    }

    @Override
    public void onActivate() {
        recovery.reset(System.nanoTime());
        nativeApplied = reportedFallback = false;
        bindPlayer();
    }

    private void bindPlayer() {
        if (heightLease != null) heightLease.restore();
        restorePathStep();
        nativeApplied = false;
        owner = mc.player;
        var attribute = owner == null ? null : owner.getAttribute(Attributes.STEP_HEIGHT);
        heightLease = attribute == null ? null : new StepHeightLease(attribute::getBaseValue, attribute::setBaseValue);
    }

    private void restorePathStep() {
        if (ownsPathStep && pathStep != null && pathStep.get()) pathStep.set(prevPathManagerStep);
        ownsPathStep = false; pathStep = null;
    }

    private void enablePathStep() {
        Setting<Boolean> setting = PathManagers.get().getSettings().getStep();
        if (setting != pathStep) {
            restorePathStep(); pathStep = setting;
            prevPathManagerStep = setting.get(); ownsPathStep = true;
        }
        setting.set(true);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (owner != mc.player) bindPlayer();
        if (owner == null || mc.level == null || heightLease == null) return;
        if (recovery.jumpFallback() && !reportedFallback) {
            reportedFallback = true;
            info("Server moved the player during native stepping. Using normal jumps until Step is re-enabled.");
        }
        boolean work = activeWhen.get() == ActiveWhen.Always
            || activeWhen.get() == ActiveWhen.Sneaking && owner.isShiftKeyDown()
            || activeWhen.get() == ActiveWhen.NotSneaking && !owner.isShiftKeyDown();
        if (!work || !owner.isAlive() || !recovery.ready(System.nanoTime())) {
            nativeApplied = false;
            heightLease.restore(); restorePathStep(); return;
        }
        double maximum = getMaxSafeHeight();
        if (nativeMode() && maximum > 0) {
            heightLease.apply(maximum); enablePathStep(); nativeApplied = true;
        } else {
            nativeApplied = false;
            heightLease.restore(); restorePathStep();
            if (maximum > 0) tryJump(maximum);
        }
    }

    private void tryJump(double maximum) {
        if (mc.gui.screen() != null || !owner.onGround() || !owner.horizontalCollision || owner.isPassenger() || owner.isInWater()
            || owner.isInLava() || owner.onClimbable() || owner.getAbilities().flying || owner.isFallFlying()
            || owner.input.keyPresses.jump() || owner.isShiftKeyDown() && activeWhen.get() != ActiveWhen.Sneaking) return;
        Vec2 input = owner.input.getMoveVector();
        StepAssist.Direction direction = StepAssist.direction(owner.getYRot(), input.x, input.y);
        if (!direction.moving()) return;
        if (!mc.level.noCollision(owner, owner.getBoundingBox().move(0, StepAssist.JUMP_RISE, 0))) return;
        AABB ahead = owner.getBoundingBox().move(direction.x() * 0.4, 0, direction.z() * 0.4);
        double rise = StepAssist.jumpRise(maximum, owner.maxUpStep(), offset -> mc.level.noCollision(owner, ahead.move(0, offset, 0)));
        if (rise > 0) owner.jumpFromGround();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // Accept server teleports/corrections normally and let movement settle before retrying.
        if (event.packet instanceof ClientboundPlayerPositionPacket)
            recovery.corrected(System.nanoTime(), nativeApplied);
    }

    @Override
    public void onDeactivate() {
        if (heightLease != null) heightLease.restore();
        restorePathStep(); owner = null; heightLease = null; nativeApplied = false;
    }

    private float getHealth() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private double getExplosionDamage() {
        double maximum = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal && entity.isAlive() && entity.distanceToSqr(mc.player) <= 144)
                maximum = Math.max(maximum, DamageUtils.crystalDamage(mc.player, entity.position()));
        }
        return maximum;
    }

    private boolean isSaferThanWith(double currentDamage) {
        double damage = getExplosionDamage(), health = getHealth();
        return health > stepHealth.get() && health - damage > stepHealth.get() || damage <= currentDamage;
    }

    private double getMaxSafeHeight() {
        double max = Math.min(StepAssist.height(height.get()), nativeMode() ? 4 : StepAssist.JUMP_RISE);
        if (!safeStep.get()) return max;
        double h = 0;
        double currentDamage = getExplosionDamage();
        AABB initial = mc.player.getBoundingBox();

        // all of this is to avoid running into crystals which are behind
        // one block when holding a movement key because standing on the
        // near edge of that block is technically safe

        Vec2 input = mc.player.input.getMoveVector();
        StepAssist.Direction direction = StepAssist.direction(mc.player.getYRot(), input.x, input.y);
        Vec3 inputOffset = new Vec3(direction.x() * 1.2, 0, direction.z() * 1.2);

        try {
            for (int i = 1; i < max; i++) {
                mc.player.setBoundingBox(initial.move(0, i, 0));
                if (!isSaferThanWith(currentDamage)) return h;
                mc.player.setBoundingBox(mc.player.getBoundingBox().move(inputOffset));
                if (!isSaferThanWith(currentDamage)) return h;
                h += 1;
            }
            mc.player.setBoundingBox(initial.move(0, max, 0));
            if (!isSaferThanWith(currentDamage)) return h;
            mc.player.setBoundingBox(mc.player.getBoundingBox().move(inputOffset));
            return isSaferThanWith(currentDamage) ? max : h;
        } finally {
            mc.player.setBoundingBox(initial);
        }
    }

    @Override public String getInfoString() {
        return nativeMode() ? "Native" : recovery.jumpFallback() ? "Jump fallback" : "Jump";
    }

    public enum Mode { Auto, Native, Jump }

    public enum ActiveWhen {
        Always,
        Sneaking,
        NotSneaking
    }
}
