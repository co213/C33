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
    default boolean supportsCommands() { return false; }
    default String getCommandPrefix() { return ""; }
    default boolean executeCommand(String command) { throw new UnsupportedOperationException("This backend has no command interface."); }
    default List<CommandInfo> getCommands() { return List.of(); }

    record CommandInfo(String name, String description, List<String> help) {
        public CommandInfo { help = List.copyOf(help); }
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
