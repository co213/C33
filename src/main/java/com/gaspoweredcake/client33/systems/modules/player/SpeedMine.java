/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.events.packets.PacketEvent;
import com.gaspoweredcake.client33.settings.*;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.systems.modules.Modules;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.ListMode;
import com.gaspoweredcake.client33.utils.world.MiningMath;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class SpeedMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Damage finishes early; Normal multiplies speed; Haste adds a mining-only boost.")
        .defaultValue(Mode.Damage)
        .onChanged(_ -> reset())
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Selected blocks.")
        .filter(block -> block.defaultDestroyTime() > 0)
        .visible(() -> mode.get() != Mode.Haste)
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("How to use the blocks setting.")
        .defaultValue(ListMode.Blacklist)
        .visible(() -> mode.get() != Mode.Haste)
        .build()
    );

    public final Setting<Double> modifier = sgGeneral.add(new DoubleSetting.Builder()
        .name("modifier")
        .description("Mining speed modifier. An additional value of 0.2 is equivalent to one haste level (1.2 = haste 1).")
        .defaultValue(1.4)
        .visible(() -> mode.get() == Mode.Normal)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Double> finishThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("finish-threshold")
        .description("Mining progress required before Damage finishes a block. Higher values are safer on strict servers.")
        .defaultValue(0.7)
        .range(0.7, 1.0)
        .sliderRange(0.7, 1.0)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    private final Setting<Integer> hasteAmplifier = sgGeneral.add(new IntSetting.Builder()
        .name("haste-amplifier")
        .description("Mining bonus equivalent to this Haste level. Preserves real effects and stronger boosts.")
        .defaultValue(2)
        .range(1, 255)
        .sliderRange(1, 5)
        .visible(() -> mode.get() == Mode.Haste)
        .build()
    );

    private final Setting<Boolean> instamine = sgGeneral.add(new BoolSetting.Builder()
        .name("instamine")
        .description("Finish in one click when per-tick progress reaches Finish Threshold. Honors Break Delay protection.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    private final Setting<Boolean> grimBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("grim-bypass")
        .description("Legacy extra-abort packet after an accelerated break. Server compatibility varies.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    public SpeedMine() {
        super(Categories.Player, "speed-mine", "Allows you to quickly mine blocks.");
    }

    private @Nullable BlockPos acceleratedPos;
    private @Nullable Object acceleratedWorld;

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        acceleratedPos = null;
        acceleratedWorld = null;
    }

    // Sent fires after STOP was submitted and excludes cancelled sends.
    @EventHandler
    private void onPacket(PacketEvent.Sent event) {
        if (!(event.packet instanceof ServerboundPlayerActionPacket packet)
            || packet.getAction() != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
            || !packet.getPos().equals(acceleratedPos)) return;

        boolean sendAbort = isActive() && mode.get() == Mode.Damage && grimBypass.get()
            && acceleratedWorld == mc.level && Utils.canUpdate() && mc.getConnection() != null
            && event.connection == mc.getConnection().getConnection();
        reset();
        if (sendAbort) event.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, packet.getPos().above(), packet.getDirection()));
    }

    public void markAccelerated(BlockPos pos) {
        acceleratedPos = pos.immutable();
        acceleratedWorld = mc.level;
    }

    /** Called for the actual local-player block, independent of crosshair position. */
    public float scaleProgress(BlockState state, float delta) {
        if (!isActive() || mc.player == null) return delta;
        if (mode.get() == Mode.Normal && filter(state.getBlock())) return MiningMath.scale(delta, modifier.get());
        if (mode.get() == Mode.Haste) {
            int existingLevel = MobEffectUtil.hasDigSpeed(mc.player) ? MobEffectUtil.getDigSpeedAmplification(mc.player) + 1 : 0;
            return MiningMath.scale(delta, MiningMath.hasteMultiplier(hasteAmplifier.get(), existingLevel));
        }
        return delta;
    }

    public float finishProgress(BlockPos pos, BlockState state, float progress, float delta) {
        if (!isActive() || mode.get() != Mode.Damage || !filter(state.getBlock())) return delta;
        float adjusted = MiningMath.finishDelta(progress, delta, finishThreshold.get());
        if (adjusted > delta) markAccelerated(pos);
        return adjusted;
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }

    public boolean filter(Block block) {
        return blocksFilter.get().allows(blocks.get().contains(block));
    }

    public boolean instamine() {
        return isActive() && mode.get() == Mode.Damage && instamine.get();
    }

    public boolean canInstantMine(BlockPos pos, BlockState state) {
        return instamine() && !Modules.get().get(BreakDelay.class).preventInstaBreak() && Utils.canUpdate() && !mc.player.isCreative() && !mc.player.isSpectator()
            && !state.isAir() && state.getDestroySpeed(mc.level, pos) >= 0 && filter(state.getBlock())
            && MiningMath.reaches(0, state.getDestroyProgress(mc.player, mc.level, pos), finishThreshold.get());
    }

    public enum Mode {
        Normal,
        Haste,
        Damage
    }

}
