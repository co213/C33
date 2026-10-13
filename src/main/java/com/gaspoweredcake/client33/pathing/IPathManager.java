/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.pathing;

import com.gaspoweredcake.client33.settings.Setting;
import com.gaspoweredcake.client33.settings.Settings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

import java.util.function.Predicate;
import java.util.List;

public interface IPathManager {
    String getName();

    boolean isPathing();

    default boolean isPaused() { return false; }
    default String getStatus() { return isPaused() ? "Paused" : isPathing() ? "Pathing" : "Idle"; }
    /** Bounded read-only data for overlays; implementations without path access return an empty view. */
    default VisualPath getVisualPath(int maxNodes) { return VisualPath.empty(); }

    record VisualPath(List<BlockPos> nodes, BlockPos goal, String status, double progress,
                      double remainingTicks, boolean active, boolean paused) {
        public VisualPath { nodes = List.copyOf(nodes); }
        public static VisualPath empty() { return new VisualPath(List.of(), null, "Idle", 0, -1, false, false); }
    }
    default boolean supportsCommands() { return false; }
    default String getCommandPrefix() { return ""; }
    default boolean executeCommand(String command) { throw new UnsupportedOperationException("This backend has no command interface."); }
    default List<CommandInfo> getCommands() { return List.of(); }

    record CommandInfo(String name, String description, List<String> help) {
        public CommandInfo { help = List.copyOf(help); }
    }

    default boolean supportsSchematicBuild() { return false; }
    default void buildSchematic(String name, com.gaspoweredcake.client33.utils.schematic.SchematicBlueprint blueprint,
                                BlockPos origin, boolean keepAir) {
        throw new UnsupportedOperationException("Install Baritone for Minecraft 26.2 to build schematics.");
    }

    /** Identity of the current goal, when the backend exposes it. */
    default Object getGoalToken() { return null; }

    /** Navigation requires live backend settings and goal ownership support. */
    default boolean supportsNavigation() { return false; }
    default boolean getAllowBreak() { throw new UnsupportedOperationException(); }
    default void setAllowBreak(boolean value) { throw new UnsupportedOperationException(); }

    void pause();

    void resume();

    void stop();

    default void moveTo(BlockPos pos) {
        moveTo(pos, false);
    }

    void moveTo(BlockPos pos, boolean ignoreY);

    void moveInDirection(float yaw);

    void mine(Block... blocks);

    void follow(Predicate<Entity> entity);

    float getTargetYaw();

    float getTargetPitch();

    ISettings getSettings();

    interface ISettings {
        Settings get();

        Setting<Boolean> getWalkOnWater();

        Setting<Boolean> getWalkOnLava();

        Setting<Boolean> getStep();

        Setting<Boolean> getNoFall();

        void save();
    }
}
