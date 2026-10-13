/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.SettingsUtil;
import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import java.util.List;
import java.util.Comparator;

import static com.gaspoweredcake.client33.Client33.mc;

public class BaritonePathManager implements IPathManager {
    private final BaritoneSettings settings;

    private GoalDirection directionGoal;
    private boolean pathingPaused;

    public BaritonePathManager() {
        // Subscribe to event bus
        Client33.EVENT_BUS.subscribe(this);

        // Create settings
        settings = new BaritoneSettings();

        // Baritone pathing control
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritoneProcess());
    }

    @Override
    public String getName() {
        return "Baritone";
    }

    @Override
    public boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    @Override public boolean isPaused() { return pathingPaused; }
    @Override public boolean supportsCommands() { return true; }
    @Override public String getCommandPrefix() { return BaritoneAPI.getSettings().prefix.value; }

    @Override
    public boolean executeCommand(String command) {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
    }

    @Override
    public List<CommandInfo> getCommands() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().stream()
            .filter(command -> !command.hiddenFromHelp() && !command.getNames().isEmpty())
            .map(command -> new CommandInfo(command.getNames().getFirst(), command.getShortDesc(), command.getLongDesc()))
            .sorted(Comparator.comparing(CommandInfo::name)).toList();
    }

    @Override
    public String getStatus() {
        if (pathingPaused) return "Paused";
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        return baritone.getPathingControlManager().mostRecentInControl().map(IBaritoneProcess::displayName)
            .orElseGet(() -> baritone.getPathingBehavior().getInProgress().isPresent() ? "Calculating path" : "Idle");
    }

    @Override
    public Object getGoalToken() {
        var process = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
        return process.isActive() ? process.getGoal() : null;
    }

    @Override
    public boolean supportsNavigation() { return true; }

    @Override
    public boolean getAllowBreak() { return BaritoneAPI.getSettings().allowBreak.value; }

    @Override
    public void setAllowBreak(boolean value) { BaritoneAPI.getSettings().allowBreak.value = value; }

    @Override
    public void pause() {
        pathingPaused = true;
    }

    @Override
    public void resume() {
        pathingPaused = false;
    }

    @Override
    public void stop() {
        pathingPaused = false;
        directionGoal = null;
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {
        if (ignoreY) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(pos.getX(), pos.getZ()));
            return;
        }

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
    }

    @Override
    public void moveInDirection(float yaw) {
        directionGoal = new GoalDirection(yaw);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(directionGoal);
    }

    @Override
    public void mine(Block... blocks) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(blocks);
    }

    @Override
    public void follow(Predicate<Entity> entity) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().follow(entity);
    }

    @Override
    public float getTargetYaw() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerRotations().getYaw();
    }

    @Override
    public float getTargetPitch() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerRotations().getPitch();
    }

    @Override
    public ISettings getSettings() {
        return settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (directionGoal == null) return;

        if (directionGoal != BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal()) {
            directionGoal = null;
            return;
        }

        directionGoal.tick();
    }

    private static class GoalDirection implements Goal {
        private static final double SQRT_2 = Math.sqrt(2);

        private final float yaw;
        private int x;
        private int z;

        private int timer;

        public GoalDirection(float yaw) {
            this.yaw = yaw;
            tick();
        }

        public static double calculate(double xDiff, double zDiff) {
            double x = Math.abs(xDiff);
            double z = Math.abs(zDiff);
            double straight;
            double diagonal;
            if (x < z) {
                straight = z - x;
                diagonal = x;
            } else {
                straight = x - z;
                diagonal = z;
            }

            diagonal *= SQRT_2;
            return (diagonal + straight) * BaritoneAPI.getSettings().costHeuristic.value;
        }

        public void tick() {
            if (timer <= 0) {
                timer = 20;

                Vec3 pos = mc.player.position();
                float theta = (float) Math.toRadians(yaw);

                x = (int) Math.floor(pos.x - (double) Mth.sin(theta) * 100);
                z = (int) Math.floor(pos.z + (double) Mth.cos(theta) * 100);
            }

            timer--;
        }

        public boolean isInGoal(int x, int y, int z) {
            return x == this.x && z == this.z;
        }

        public double heuristic(int x, int y, int z) {
            int xDiff = x - this.x;
            int zDiff = z - this.z;
            return calculate(xDiff, zDiff);
        }

        public String toString() {
            return String.format("GoalXZ{x=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.z));
        }

        public int getX() {
            return this.x;
        }

        public int getZ() {
            return this.z;
        }
    }

    private class BaritoneProcess implements IBaritoneProcess {
        @Override
        public boolean isActive() {
            return pathingPaused;
        }

        @Override
        public PathingCommand onTick(boolean b, boolean b1) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return 0d;
        }

        @Override
        public String displayName0() {
            return "33";
        }
    }
}
