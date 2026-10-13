/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.systems.modules.player;

import com.gaspoweredcake.client33.gui.GuiTheme;
import com.gaspoweredcake.client33.gui.widgets.WWidget;
import com.gaspoweredcake.client33.gui.widgets.containers.WTable;
import com.gaspoweredcake.client33.settings.BoolSetting;
import com.gaspoweredcake.client33.settings.DoubleSetting;
import com.gaspoweredcake.client33.settings.EnumSetting;
import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.SettingGroup;
import com.gaspoweredcake.client33.systems.modules.Categories;
import com.gaspoweredcake.client33.systems.modules.Module;
import com.gaspoweredcake.client33.utils.Utils;
import com.gaspoweredcake.client33.utils.misc.ReachRange;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Locale;

public class Reach extends Module {
    public enum Mode { Extra, Total }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgConditions = settings.createGroup("Conditions");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Extra adds to Minecraft's current range. Total sets the desired final range.")
        .defaultValue(Mode.Extra)
        .build());

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Customize block targeting and interaction distance.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> entities = sgGeneral.add(new BoolSetting.Builder()
        .name("entities")
        .description("Customize entity targeting and interaction distance.")
        .defaultValue(true)
        .build());

    // Keep the original keys and defaults for existing saved configurations.
    private final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-block-reach")
        .description("Distance added to the current block range; negative values reduce it.")
        .defaultValue(0)
        .range(-ReachRange.MAX_RANGE, ReachRange.MAX_RANGE)
        .sliderRange(0, 16)
        .visible(() -> blocks.get() && mode.get() == Mode.Extra)
        .build());

    private final Setting<Double> entityReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-entity-reach")
        .description("Distance added to the current entity range; negative values reduce it.")
        .defaultValue(0)
        .range(-ReachRange.MAX_RANGE, ReachRange.MAX_RANGE)
        .sliderRange(0, 8)
        .visible(() -> entities.get() && mode.get() == Mode.Extra)
        .build());

    private final Setting<Double> totalBlockReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("total-block-reach")
        .description("Desired final block range in Total mode, in blocks.")
        .defaultValue(6)
        .range(0, ReachRange.MAX_RANGE)
        .sliderRange(0, 16)
        .visible(() -> blocks.get() && mode.get() == Mode.Total)
        .build());

    private final Setting<Double> totalEntityReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("total-entity-reach")
        .description("Desired final entity range in Total mode, in blocks.")
        .defaultValue(4)
        .range(0, ReachRange.MAX_RANGE)
        .sliderRange(0, 8)
        .visible(() -> entities.get() && mode.get() == Mode.Total)
        .build());

    private final Setting<Boolean> onlySneaking = sgConditions.add(new BoolSetting.Builder()
        .name("only-while-sneaking")
        .description("Apply custom ranges only while sneaking.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> creativeOnly = sgConditions.add(new BoolSetting.Builder()
        .name("creative-only")
        .description("Apply custom ranges only in Creative mode.")
        .defaultValue(false)
        .build());

    public Reach() {
        super(Categories.Player, "reach", "Customizes your block and entity interaction ranges independently.");
    }

    private boolean available() {
        return isActive() && mc.player != null && mc.level != null && mc.player.isAlive()
            && (!onlySneaking.get() || mc.player.isShiftKeyDown())
            && (!creativeOnly.get() || mc.player.getAbilities().instabuild);
    }

    public double blockRange(double original) {
        return ReachRange.resolve(original, mode.get() == Mode.Total ? totalBlockReach.get() : blockReach.get(),
            mode.get() == Mode.Total, available() && blocks.get());
    }

    public double entityRange(double original) {
        return ReachRange.resolve(original, mode.get() == Mode.Total ? totalEntityReach.get() : entityReach.get(),
            mode.get() == Mode.Total, available() && entities.get());
    }

    /** Compatibility API: offset from the native attribute, without calling the modified range getter. */
    public double blockReach() {
        if (!available() || !blocks.get()) return 0;
        double original = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        return blockRange(original) - original;
    }

    public double entityReach() {
        if (!available() || !entities.get()) return 0;
        double original = mc.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return entityRange(original) - original;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        table.add(theme.label("Extra preserves Minecraft's native range modifiers; Total chooses the final distance. Ranges are bounded to 64 blocks.",
            Utils.getWindowWidth() / 3.0));
        table.row();
        table.add(theme.label("Servers validate reach independently. A longer client target range does not guarantee mining, placement or attacks will be accepted.",
            Utils.getWindowWidth() / 3.0));
        return table;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null || mc.level == null) return mode.get().toString();
        double block = blockRange(mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));
        double entity = entityRange(mc.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        return String.format(Locale.ROOT, "B %.1f / E %.1f", block, entity);
    }
}
