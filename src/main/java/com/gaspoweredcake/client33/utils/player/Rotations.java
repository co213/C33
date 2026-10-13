/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.gaspoweredcake.client33.utils.player;

import com.gaspoweredcake.client33.Client33;
import com.gaspoweredcake.client33.events.entity.player.SendMovementPacketsEvent;
import com.gaspoweredcake.client33.events.world.TickEvent;
import com.gaspoweredcake.client33.systems.config.Config;
import com.gaspoweredcake.client33.utils.PreInit;
import com.gaspoweredcake.client33.utils.entity.Target;
import com.gaspoweredcake.client33.events.game.GameLeftEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.gaspoweredcake.client33.Client33.mc;

public class Rotations {
    private static final List<Rotation> rotations = new ArrayList<>();
    private static List<Rotation> inFlight = List.of();
    private static LocalPlayer prePlayer;
    public static float serverYaw;
    public static float serverPitch;
    public static int rotationTimer;
    private static float preYaw, prePitch;
    private static int i = 0;

    private static Rotation lastRotation;
    private static int lastRotationTimer;
    public static boolean rotating = false;

    private Rotations() {
    }

    @PreInit
    public static void init() {
        Client33.EVENT_BUS.subscribe(Rotations.class);
    }

    public static void rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
        if (mc.player == null || mc.level == null || !Double.isFinite(yaw) || !Double.isFinite(pitch)) return;
        Rotation rotation = new Rotation();
        rotation.set(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90, 90), priority, clientSide, callback);

        int i = 0;
        for (; i < rotations.size(); i++) {
            if (priority > rotations.get(i).priority) break;
        }

        rotations.add(i, rotation);
    }

    public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
        rotate(yaw, pitch, priority, false, callback);
    }

    public static void rotate(double yaw, double pitch, Runnable callback) {
        rotate(yaw, pitch, 0, callback);
    }

    public static void rotate(double yaw, double pitch, int priority) {
        rotate(yaw, pitch, priority, null);
    }

    public static void rotate(double yaw, double pitch) {
        rotate(yaw, pitch, 0, null);
    }

    private static void resetLastRotation() {
        if (lastRotation != null) {
            lastRotation = null;
            lastRotationTimer = 0;
        }
    }

    @EventHandler
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        resetPreRotation();
        rotations.removeIf(rotation -> !rotation.valid());
        inFlight = List.copyOf(rotations);
        rotations.clear();
        i = 0;
        if (mc.player == null || mc.getCameraEntity() != mc.player) return;

        if (!inFlight.isEmpty()) {
            rotating = true;
            resetLastRotation();
            setupMovementPacketRotation(inFlight.getFirst());
            i = 1;
        } else if (lastRotation != null) {
            if (!lastRotation.valid() || lastRotationTimer >= Config.get().rotationHoldTicks.get()) {
                resetLastRotation();
                rotating = false;
            } else {
                setupMovementPacketRotation(lastRotation);
                lastRotationTimer++;
            }
        }
    }

    private static void setupMovementPacketRotation(Rotation rotation) {
        setClientRotation(rotation);
        setCamRotation(rotation.yaw, rotation.pitch);
    }

    private static void setClientRotation(Rotation rotation) {
        prePlayer = mc.player;
        preYaw = mc.player.getYRot();
        prePitch = mc.player.getXRot();

        mc.player.setYRot((float) rotation.yaw);
        mc.player.setXRot((float) rotation.pitch);
    }

    @EventHandler
    private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        List<Rotation> batch = inFlight;
        inFlight = List.of();
        try {
            if (i == 1 && !batch.isEmpty()) {
                Rotation first = batch.getFirst();
                try { first.runCallback(); } finally { resetPreRotation(); }
                if (first.valid()) lastRotation = first;
            }
            for (; i < batch.size(); i++) {
                Rotation rotation = batch.get(i);
                if (!rotation.valid()) continue;
                setCamRotation(rotation.yaw, rotation.pitch);
                if (rotation.clientSide) setClientRotation(rotation);
                try { rotation.sendPacket(); } finally { resetPreRotation(); }
                if (rotation.valid()) lastRotation = rotation;
            }
        } finally {
            resetPreRotation();
            i = 0;
        }
    }

    private static void resetPreRotation() {
        if (prePlayer == null) return;
        prePlayer.setYRot(preYaw);
        prePlayer.setXRot(prePitch);
        prePlayer = null;
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        resetPreRotation();
        rotations.clear();
        inFlight = List.of();
        resetLastRotation();
        i = 0;
        rotating = false;
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        rotationTimer++;
    }

    public static double getYaw(Entity entity) {
        return mc.player.getYRot() + Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(entity.getZ() - mc.player.getZ(), entity.getX() - mc.player.getX())) - 90f - mc.player.getYRot());
    }

    public static double getYaw(Vec3 pos) {
        return mc.player.getYRot() + Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.z() - mc.player.getZ(), pos.x() - mc.player.getX())) - 90f - mc.player.getYRot());
    }

    public static double getPitch(Vec3 pos) {
        double diffX = pos.x() - mc.player.getX();
        double diffY = pos.y() - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.z() - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.getXRot() + Mth.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getXRot());
    }

    public static double getPitch(Entity entity, Target target) {
        double y = switch (target) {
            case Head -> entity.getEyeY();
            case Body -> entity.getY() + entity.getBbHeight() / 2;
            case Feet -> entity.getY();
        };

        double diffX = entity.getX() - mc.player.getX();
        double diffY = y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = entity.getZ() - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.getXRot() + Mth.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getXRot());
    }

    public static double getPitch(Entity entity) {
        return getPitch(entity, Target.Body);
    }

    public static double getYaw(BlockPos pos) {
        return mc.player.getYRot() + Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.getZ() + 0.5 - mc.player.getZ(), pos.getX() + 0.5 - mc.player.getX())) - 90f - mc.player.getYRot());
    }

    public static double getPitch(BlockPos pos) {
        double diffX = pos.getX() + 0.5 - mc.player.getX();
        double diffY = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.getZ() + 0.5 - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.getXRot() + Mth.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getXRot());
    }

    public static void setCamRotation(double yaw, double pitch) {
        serverYaw = (float) yaw;
        serverPitch = (float) pitch;
        rotationTimer = 0;
    }

    private static class Rotation {
        public double yaw, pitch;
        public int priority;
        public boolean clientSide;
        public Runnable callback;
        private LocalPlayer player;
        private ClientLevel world;

        public void set(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.clientSide = clientSide;
            this.callback = callback;
            player = mc.player;
            world = mc.level;
        }

        private boolean valid() {
            return player != null && player == mc.player && world == mc.level;
        }

        public void sendPacket() {
            if (!valid() || mc.getConnection() == null) return;
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot((float) yaw, (float) pitch, mc.player.onGround(), mc.player.horizontalCollision));
            runCallback();
        }

        public void runCallback() {
            Runnable action = callback;
            callback = null;
            if (action == null || !valid()) return;
            try {
                action.run();
            } catch (RuntimeException failure) {
                Client33.LOG.error("Deferred rotation action failed.", failure);
            }
        }
    }
}
